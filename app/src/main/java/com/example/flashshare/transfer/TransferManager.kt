package com.example.flashshare.transfer

import android.content.Context
import android.net.Uri
import com.example.flashshare.data.SettingsRepository
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.model.TransferDirection
import com.example.flashshare.model.TransferItem
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.storage.FileManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

class TransferManager(
  private val context: Context,
  private val fileManager: FileManager,
  private val history: TransferHistory,
  private val settingsRepository: SettingsRepository,
) {

  private class TransferControl {
    @Volatile var paused: Boolean = false
    @Volatile var cancelled: Boolean = false
    var job: Job? = null
  }

  private data class SendJobContext(
    val file: ShareableFile,
    val peerIp: String,
    val peerPort: Int,
    val peerToken: String,
    val peerName: String,
    val sessionId: String,
    val remoteFileId: String,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val concurrencySemaphore = Semaphore(2)
  private val controls = ConcurrentHashMap<String, TransferControl>()
  private val sendContexts = ConcurrentHashMap<String, SendJobContext>()

  private val _activeTransfers = MutableStateFlow<List<TransferItem>>(emptyList())
  val transfers: StateFlow<List<TransferItem>> = _activeTransfers.asStateFlow()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun clearError() {
    _lastError.value = null
  }

  fun upsertLiveTransfer(item: TransferItem) {
    val current = _activeTransfers.value.toMutableList()
    val idx = current.indexOfFirst { it.id == item.id }
    if (idx >= 0) {
      current[idx] = item
    } else {
      current.add(0, item)
    }
    _activeTransfers.value = current
  }

  fun registerControlForIncoming(id: String): () -> Pair<Boolean, Boolean> {
    val ctrl = controls.getOrPut(id) { TransferControl() }
    return { ctrl.paused to ctrl.cancelled }
  }

  fun completeIncomingTransfer(item: TransferItem) {
    controls.remove(item.id)
    upsertLiveTransfer(item)
    scope.launch { history.recordOrUpdate(item) }
  }

  fun sendFilesToPeer(
    files: List<ShareableFile>,
    peerIp: String,
    peerPort: Int,
    peerToken: String,
    peerName: String,
  ) {
    if (files.isEmpty()) return
    TransferService.start(context)

    scope.launch {
      val settings = settingsRepository.settings.value
      val remoteFilesJson = JSONArray()
      val fileIdMap = LinkedHashMap<String, ShareableFile>()

      files.forEach { f ->
        val remoteId = UUID.randomUUID().toString()
        fileIdMap[remoteId] = f
        remoteFilesJson.put(
          JSONObject().apply {
            put("id", remoteId)
            put("name", f.name)
            put("relativePath", f.relativePath)
            put("size", f.sizeBytes)
            put("mimeType", f.mimeType)
          },
        )
      }

      val offerBody = JSONObject().apply {
        put("senderName", settings.deviceName)
        put("files", remoteFilesJson)
      }.toString()

      val offerResp = runCatching {
        postJson(peerIp, peerPort, "/api/p2p/offer?token=${enc(peerToken)}", offerBody)
      }.getOrElse { err ->
        val msg = "Failed to connect to $peerIp:$peerPort — ${err.localizedMessage ?: "Connection refused"}"
        _lastError.value = msg
        files.forEach { f ->
          val failed = TransferItem(
            fileName = f.name,
            relativePath = f.relativePath,
            fileSize = f.sizeBytes,
            status = TransferStatus.FAILED,
            direction = TransferDirection.SEND,
            mode = ConnectionMode.PHONE_TO_PHONE,
            peerName = peerName,
            peerAddress = "$peerIp:$peerPort",
            errorMessage = msg,
            sourceUri = f.uri.toString(),
            mimeType = f.mimeType,
          )
          upsertLiveTransfer(failed)
          history.recordOrUpdate(failed)
        }
        return@launch
      }

      val sessionId = offerResp.optString("sessionId")
      if (sessionId.isBlank()) {
        _lastError.value = offerResp.optString("error", "Peer rejected pairing token or request.")
        return@launch
      }

      // Create pending transfer items
      val transferIds = LinkedHashMap<String, String>()
      fileIdMap.forEach { (remoteFileId, f) ->
        val tid = UUID.randomUUID().toString()
        transferIds[remoteFileId] = tid
        sendContexts[tid] = SendJobContext(f, peerIp, peerPort, peerToken, peerName, sessionId, remoteFileId)
        val item = TransferItem(
          id = tid,
          fileName = f.name,
          relativePath = f.relativePath,
          fileSize = f.sizeBytes,
          status = TransferStatus.PENDING,
          direction = TransferDirection.SEND,
          mode = ConnectionMode.PHONE_TO_PHONE,
          peerName = peerName,
          peerAddress = "$peerIp:$peerPort",
          sourceUri = f.uri.toString(),
          mimeType = f.mimeType,
        )
        upsertLiveTransfer(item)
      }

      // Wait for Receiver Acceptance (up to 90s)
      var accepted = false
      val deadline = System.currentTimeMillis() + 90_000L
      while (isActive && System.currentTimeMillis() < deadline) {
        val statusResp = runCatching {
          getJson(peerIp, peerPort, "/api/p2p/offer/$sessionId?token=${enc(peerToken)}")
        }.getOrNull()
        val state = statusResp?.optString("status") ?: "PENDING"
        if (state == "ACCEPTED") {
          accepted = true
          break
        } else if (state == "REJECTED") {
          transferIds.values.forEach { tid ->
            updateItem(tid) {
              it.copy(status = TransferStatus.REJECTED, errorMessage = "Declined by $peerName")
            }
          }
          _lastError.value = "Transfer declined by $peerName."
          return@launch
        }
        delay(600)
      }

      if (!accepted) {
        transferIds.values.forEach { tid ->
          updateItem(tid) {
            it.copy(status = TransferStatus.FAILED, errorMessage = "Timed out waiting for receiver acceptance")
          }
        }
        _lastError.value = "Timed out waiting for $peerName to accept."
        return@launch
      }

      // Stream files with controlled concurrency
      transferIds.values.forEach { tid ->
        launchSendTask(tid, offset = 0L)
      }
    }
  }

  private fun launchSendTask(transferId: String, offset: Long) {
    val ctx = sendContexts[transferId] ?: return
    val ctrl = controls.getOrPut(transferId) { TransferControl() }.apply {
      paused = false
      cancelled = false
    }
    ctrl.job?.cancel()
    ctrl.job = scope.launch {
      concurrencySemaphore.withPermit {
        streamFileToPeer(transferId, ctx, offset, ctrl)
      }
    }
  }

  private suspend fun streamFileToPeer(
    transferId: String,
    ctx: SendJobContext,
    startOffset: Long,
    ctrl: TransferControl,
  ) {
    val settings = settingsRepository.settings.value
    val bufSize = settings.bufferSizeKb * 1024
    val file = ctx.file

    updateItem(transferId) {
      it.copy(
        status = TransferStatus.IN_PROGRESS,
        bytesTransferred = startOffset,
        errorMessage = null,
      )
    }

    val sha256Local = if (settings.autoVerifySha256) {
      fileManager.computeSha256(file.uri, bufSize)
    } else null

    val result = runCatching {
      val contentLen = (file.sizeBytes - startOffset).coerceAtLeast(0L)
      Socket().use { socket ->
        socket.soTimeout = 15_000
        socket.sendBufferSize = bufSize
        socket.connect(InetSocketAddress(ctx.peerIp, ctx.peerPort), 6000)

        val out = BufferedOutputStream(socket.getOutputStream(), bufSize)
        val path = "/api/p2p/stream?token=${enc(ctx.peerToken)}&sessionId=${enc(ctx.sessionId)}&fileId=${enc(ctx.remoteFileId)}&offset=$startOffset"
        val headers = buildString {
          append("POST $path HTTP/1.1\r\n")
          append("Host: ${ctx.peerIp}:${ctx.peerPort}\r\n")
          append("Content-Type: application/octet-stream\r\n")
          append("Content-Length: $contentLen\r\n")
          append("X-File-Name: ${enc(file.name)}\r\n")
          append("X-File-Size: ${file.sizeBytes}\r\n")
          if (sha256Local != null) append("X-File-SHA256: $sha256Local\r\n")
          append("Connection: close\r\n\r\n")
        }
        out.write(headers.toByteArray(Charsets.UTF_8))

        val input = fileManager.openInputStream(file.uri)
          ?: throw IllegalStateException("Cannot read ${file.name} (storage permission revoked)")

        var totalSent = startOffset
        input.use { stream ->
          var remainingToSkip = startOffset
          val skipBuf = ByteArray(minOf(65536, bufSize))
          while (remainingToSkip > 0) {
            val s = stream.read(skipBuf, 0, minOf(skipBuf.size.toLong(), remainingToSkip).toInt())
            if (s <= 0) break
            remainingToSkip -= s
          }

          val buffer = ByteArray(bufSize)
          var lastUiTime = System.currentTimeMillis()
          var bytesSinceUi = 0L

          while (true) {
            if (ctrl.cancelled) throw InterruptedException("CANCELLED")
            if (ctrl.paused) throw InterruptedException("PAUSED")

            val read = stream.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
            totalSent += read
            bytesSinceUi += read

            val now = System.currentTimeMillis()
            val dt = now - lastUiTime
            if (dt >= 250L) {
              val speed = (bytesSinceUi * 1000L) / dt.coerceAtLeast(1L)
              val remaining = (file.sizeBytes - totalSent).coerceAtLeast(0L)
              val eta = if (speed > 0L) remaining / speed else 0L
              updateItem(transferId) {
                it.copy(
                  status = TransferStatus.IN_PROGRESS,
                  bytesTransferred = totalSent,
                  speedBytesPerSec = speed,
                  etaSeconds = eta,
                )
              }
              lastUiTime = now
              bytesSinceUi = 0L
            }
          }
        }
        out.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val statusLine = reader.readLine() ?: ""
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          if (line.isNullOrEmpty()) break
        }
        val body = reader.readText()
        if (!statusLine.contains("200")) {
          throw IllegalStateException("Peer returned $statusLine: $body")
        }
        val json = runCatching { JSONObject(body) }.getOrNull()
        val remoteSha = json?.optString("sha256")?.takeIf { it.isNotBlank() }
        val verified = sha256Local == null || remoteSha == null || sha256Local.equals(remoteSha, ignoreCase = true)

        updateItem(transferId) {
          val done = it.copy(
            status = if (verified) TransferStatus.COMPLETED else TransferStatus.FAILED,
            bytesTransferred = file.sizeBytes,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            sha256Local = sha256Local,
            sha256Remote = remoteSha,
            verified = verified,
            errorMessage = if (verified) null else "SHA-256 checksum mismatch after transfer",
          )
          scope.launch { history.recordOrUpdate(done) }
          done
        }
      }
    }

    result.onFailure { err ->
      when (err.message) {
        "PAUSED" -> updateItem(transferId) {
          it.copy(status = TransferStatus.PAUSED, speedBytesPerSec = 0L, etaSeconds = 0L)
        }
        "CANCELLED" -> updateItem(transferId) {
          val cancelled = it.copy(status = TransferStatus.CANCELLED, speedBytesPerSec = 0L, etaSeconds = 0L)
          scope.launch { history.recordOrUpdate(cancelled) }
          cancelled
        }
        else -> updateItem(transferId) {
          val failed = it.copy(
            status = TransferStatus.FAILED,
            speedBytesPerSec = 0L,
            etaSeconds = 0L,
            errorMessage = err.localizedMessage ?: "Transfer interrupted",
          )
          scope.launch { history.recordOrUpdate(failed) }
          failed
        }
      }
    }
  }

  fun pauseTransfer(id: String) {
    controls[id]?.paused = true
    updateItem(id) {
      if (it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.PENDING) {
        it.copy(status = TransferStatus.PAUSED, speedBytesPerSec = 0L)
      } else it
    }
  }

  fun resumeTransfer(id: String) {
    val ctx = sendContexts[id] ?: return
    val current = _activeTransfers.value.firstOrNull { it.id == id } ?: return
    scope.launch {
      val statusResp = runCatching {
        getJson(ctx.peerIp, ctx.peerPort, "/api/p2p/offer/${ctx.sessionId}?token=${enc(ctx.peerToken)}")
      }.getOrNull()
      val offsets = statusResp?.optJSONObject("receivedOffsets")
      val remoteOffset = offsets?.optLong(ctx.remoteFileId, current.bytesTransferred) ?: current.bytesTransferred
      launchSendTask(id, remoteOffset.coerceIn(0L, ctx.file.sizeBytes))
    }
  }

  fun cancelTransfer(id: String) {
    controls[id]?.cancelled = true
    controls[id]?.job?.cancel()
    updateItem(id) {
      val cancelled = it.copy(status = TransferStatus.CANCELLED, speedBytesPerSec = 0L, etaSeconds = 0L)
      scope.launch { history.recordOrUpdate(cancelled) }
      cancelled
    }
  }

  fun retryTransfer(id: String) {
    val ctx = sendContexts[id]
    if (ctx != null) {
      resumeTransfer(id)
    } else {
      val existing = _activeTransfers.value.firstOrNull { it.id == id } ?: return
      val uriStr = existing.sourceUri ?: return
      scope.launch {
        val file = fileManager.inspectUri(Uri.parse(uriStr), existing.relativePath) ?: return@launch
        val parts = existing.peerAddress.split(':')
        val ip = parts.getOrNull(0) ?: return@launch
        val port = parts.getOrNull(1)?.toIntOrNull() ?: 8080
        sendFilesToPeer(listOf(file), ip, port, "", existing.peerName)
      }
    }
  }

  fun clearCompletedLive() {
    _activeTransfers.value = _activeTransfers.value.filter {
      it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.PENDING || it.status == TransferStatus.PAUSED
    }
  }

  private fun updateItem(id: String, transform: (TransferItem) -> TransferItem) {
    val list = _activeTransfers.value.toMutableList()
    val idx = list.indexOfFirst { it.id == id }
    if (idx >= 0) {
      list[idx] = transform(list[idx])
      _activeTransfers.value = list
    }
  }

  private fun postJson(ip: String, port: Int, path: String, jsonBody: String): JSONObject {
    val bytes = jsonBody.toByteArray(Charsets.UTF_8)
    Socket().use { socket ->
      socket.soTimeout = 6000
      socket.connect(InetSocketAddress(ip, port), 5000)
      val out = BufferedOutputStream(socket.getOutputStream())
      val req = buildString {
        append("POST $path HTTP/1.1\r\n")
        append("Host: $ip:$port\r\n")
        append("Content-Type: application/json; charset=utf-8\r\n")
        append("Content-Length: ${bytes.size}\r\n")
        append("Connection: close\r\n\r\n")
      }
      out.write(req.toByteArray(Charsets.UTF_8))
      out.write(bytes)
      out.flush()
      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        if (line.isNullOrEmpty()) break
      }
      val resp = reader.readText()
      return runCatching { JSONObject(resp) }.getOrElse { JSONObject() }
    }
  }

  private fun getJson(ip: String, port: Int, path: String): JSONObject {
    Socket().use { socket ->
      socket.soTimeout = 5000
      socket.connect(InetSocketAddress(ip, port), 4000)
      val out = BufferedOutputStream(socket.getOutputStream())
      val req = "GET $path HTTP/1.1\r\nHost: $ip:$port\r\nConnection: close\r\n\r\n"
      out.write(req.toByteArray(Charsets.UTF_8))
      out.flush()
      val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
      var line: String?
      while (reader.readLine().also { line = it } != null) {
        if (line.isNullOrEmpty()) break
      }
      val resp = reader.readText()
      return runCatching { JSONObject(resp) }.getOrElse { JSONObject() }
    }
  }

  private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
