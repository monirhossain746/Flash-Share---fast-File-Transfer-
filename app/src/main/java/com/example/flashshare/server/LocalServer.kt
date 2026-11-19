package com.example.flashshare.server

import android.content.Context
import com.example.flashshare.data.SettingsRepository
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.IncomingTransferRequest
import com.example.flashshare.model.RemoteFileMeta
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.model.TransferDirection
import com.example.flashshare.model.TransferItem
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.network.DeviceDiscovery
import com.example.flashshare.network.NetworkManager
import com.example.flashshare.storage.FileManager
import com.example.flashshare.transfer.TransferManager
import com.example.flashshare.transfer.TransferService
import com.example.flashshare.util.Formatters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LocalServer(
  private val context: Context,
  private val networkManager: NetworkManager,
  private val deviceDiscovery: DeviceDiscovery,
  private val fileManager: FileManager,
  private val transferManager: TransferManager,
  private val settingsRepository: SettingsRepository,
) {

  private data class OfferSession(
    val request: IncomingTransferRequest,
    var status: String = "PENDING", // PENDING, ACCEPTED, REJECTED
    var duplicateResolution: DuplicateAction = DuplicateAction.RENAME,
  )

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var serverSocket: ServerSocket? = null
  private var acceptJob: Job? = null

  private val _isRunning = MutableStateFlow(false)
  val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

  private val _activePort = MutableStateFlow(8080)
  val activePort: StateFlow<Int> = _activePort.asStateFlow()

  private val _sessionToken = MutableStateFlow(generateToken())
  val sessionToken: StateFlow<String> = _sessionToken.asStateFlow()

  private val _sharedFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
  val sharedFiles: StateFlow<List<ShareableFile>> = _sharedFiles.asStateFlow()

  private val _pendingIncomingOffer = MutableStateFlow<IncomingTransferRequest?>(null)
  val pendingIncomingOffer: StateFlow<IncomingTransferRequest?> = _pendingIncomingOffer.asStateFlow()

  private val _serverError = MutableStateFlow<String?>(null)
  val serverError: StateFlow<String?> = _serverError.asStateFlow()

  private val offerSessions = ConcurrentHashMap<String, OfferSession>()
  private val partTransferIds = ConcurrentHashMap<String, String>()

  fun updateSharedFiles(files: List<ShareableFile>) {
    _sharedFiles.value = files
  }

  fun regenerateToken() {
    _sessionToken.value = generateToken()
  }

  fun clearError() {
    _serverError.value = null
  }

  fun start(portOverride: Int? = null) {
    if (_isRunning.value) return
    val settings = settingsRepository.settings.value
    val desiredPort = (portOverride ?: settings.defaultPort).coerceIn(1024, 65535)
    _serverError.value = null

    acceptJob = scope.launch {
      val boundSocket = bindAvailablePort(desiredPort)
      if (boundSocket == null) {
        _serverError.value = "Unable to bind local server on port $desiredPort or nearby ports."
        _isRunning.value = false
        return@launch
      }

      serverSocket = boundSocket
      _activePort.value = boundSocket.localPort
      _isRunning.value = true
      TransferService.start(context)
      deviceDiscovery.startAdvertising(settings.deviceName, boundSocket.localPort, _sessionToken.value)

      while (isActive && !boundSocket.isClosed) {
        val client = runCatching { boundSocket.accept() }.getOrNull() ?: break
        launch { handleClient(client) }
      }
      _isRunning.value = false
    }
  }

  fun stop() {
    runCatching { serverSocket?.close() }
    serverSocket = null
    acceptJob?.cancel()
    acceptJob = null
    _isRunning.value = false
    deviceDiscovery.stopAdvertising()
    TransferService.stop(context)
  }

  fun respondToIncomingOffer(sessionId: String, accept: Boolean, duplicateAction: DuplicateAction = DuplicateAction.RENAME) {
    val session = offerSessions[sessionId] ?: return
    session.status = if (accept) "ACCEPTED" else "REJECTED"
    session.duplicateResolution = duplicateAction
    if (_pendingIncomingOffer.value?.sessionId == sessionId) {
      _pendingIncomingOffer.value = null
    }
    if (!accept) {
      session.request.files.forEach { f ->
        transferManager.completeIncomingTransfer(
          TransferItem(
            fileName = f.name,
            relativePath = f.relativePath,
            fileSize = f.size,
            status = TransferStatus.REJECTED,
            direction = TransferDirection.RECEIVE,
            mode = ConnectionMode.PHONE_TO_PHONE,
            peerName = session.request.senderName,
            peerAddress = session.request.senderIp,
            errorMessage = "Declined by receiver",
            mimeType = f.mimeType,
          ),
        )
      }
    }
  }

  private fun bindAvailablePort(startPort: Int): ServerSocket? {
    for (p in startPort..(startPort + 10).coerceAtMost(65535)) {
      val sock = runCatching {
        ServerSocket().apply {
          reuseAddress = true
          bind(InetSocketAddress("0.0.0.0", p))
        }
      }.getOrNull()
      if (sock != null) return sock
    }
    return null
  }

  private suspend fun handleClient(socket: Socket) {
    socket.use { client ->
      client.soTimeout = 30_000
      val remoteAddr = client.inetAddress
      if (remoteAddr == null || !Formatters.isLocalPrivateIp(remoteAddr)) {
        writeError(client.getOutputStream(), 403, "Forbidden: Local network access only.")
        return
      }

      val input = BufferedInputStream(client.getInputStream(), 65536)
      val output = BufferedOutputStream(client.getOutputStream(), 65536)

      val requestHead = readHttpHeaders(input) ?: return
      val requestLine = requestHead.firstOrNull() ?: return
      val parts = requestLine.split(" ")
      if (parts.size < 2) return
      val method = parts[0].uppercase()
      val fullTarget = parts[1]
      val path = fullTarget.substringBefore('?')
      val queryParams = parseQueryParams(fullTarget.substringAfter('?', ""))

      val headers = LinkedHashMap<String, String>()
      for (i in 1 until requestHead.size) {
        val line = requestHead[i]
        val colon = line.indexOf(':')
        if (colon > 0) {
          headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
      }

      val clientIp = remoteAddr.hostAddress ?: "Peer"

      // Public local ping
      if (method == "GET" && path == "/api/ping") {
        val settings = settingsRepository.settings.value
        val payload = JSONObject().apply {
          put("app", "FlashShare")
          put("deviceName", settings.deviceName)
          put("port", _activePort.value)
          put("status", "ready")
        }.toString()
        writeJson(output, 200, payload)
        return
      }

      // Root Web Portal (validates token if provided, or allows direct local LAN browser access on "/")
      if (method == "GET" && path == "/") {
        val settings = settingsRepository.settings.value
        val ip = networkManager.networkState.value.primaryIp
        val html = WebPortalHtml.render(
          deviceName = settings.deviceName,
          token = _sessionToken.value,
          localAddress = "http://$ip:${_activePort.value}",
        )
        writeHtml(output, html)
        return
      }

      // Token verification for API endpoints (allow pairing if token matches OR if client has active token)
      val providedToken = queryParams["token"] ?: headers["x-flashshare-token"] ?: ""
      val tokenRequired = path != "/api/ping"
      if (tokenRequired && providedToken.isNotBlank() && providedToken != _sessionToken.value) {
        // Allow retry/reconnect only if token matches current session
        writeError(output, 401, "Invalid FlashShare session token.")
        return
      }

      when {
        method == "GET" && path == "/api/files" -> {
          val arr = JSONArray()
          _sharedFiles.value.forEach { f ->
            arr.put(
              JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("size", f.sizeBytes)
                put("ext", f.extension)
                put("mimeType", f.mimeType)
              },
            )
          }
          writeJson(output, 200, arr.toString())
        }

        method == "GET" && path.startsWith("/api/download/") -> {
          val fileId = URLDecoder.decode(path.removePrefix("/api/download/"), "UTF-8")
          val target = _sharedFiles.value.firstOrNull { it.id == fileId }
          if (target == null) {
            writeError(output, 404, "Shared file not found.")
          } else {
            serveFileDownload(target, headers["range"], output, clientIp)
          }
        }

        method == "POST" && path == "/api/upload" -> {
          handleBrowserUpload(input, output, headers, clientIp)
        }

        method == "POST" && path == "/api/p2p/offer" -> {
          val contentLen = headers["content-length"]?.toIntOrNull() ?: 0
          val bodyBytes = readFixedBytes(input, contentLen)
          val json = runCatching { JSONObject(String(bodyBytes, Charsets.UTF_8)) }.getOrNull()
          if (json == null) {
            writeError(output, 400, "Invalid JSON offer.")
            return
          }
          val senderName = json.optString("senderName", "FlashShare Sender")
          val arr = json.optJSONArray("files") ?: JSONArray()
          val remoteFiles = mutableListOf<RemoteFileMeta>()
          for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            remoteFiles += RemoteFileMeta(
              id = obj.getString("id"),
              name = Formatters.sanitizeFilename(obj.getString("name")),
              relativePath = Formatters.sanitizeRelativePath(obj.optString("relativePath", obj.getString("name"))),
              size = obj.optLong("size", 0L),
              mimeType = obj.optString("mimeType", "application/octet-stream"),
            )
          }
          val totalBytes = remoteFiles.sumOf { it.size }
          val settings = settingsRepository.settings.value
          if (!fileManager.hasEnoughStorage(totalBytes)) {
            writeJson(output, 507, JSONObject().put("error", "Insufficient storage on receiver device.").toString())
            return
          }
          val duplicates = fileManager.findDuplicateNames(settings.downloadTreeUri, remoteFiles.map { it.name })
          val sessionId = UUID.randomUUID().toString()
          val req = IncomingTransferRequest(
            sessionId = sessionId,
            senderName = senderName,
            senderIp = clientIp,
            token = providedToken,
            files = remoteFiles,
            totalBytes = totalBytes,
            duplicateNames = duplicates,
          )
          offerSessions[sessionId] = OfferSession(req)
          _pendingIncomingOffer.value = req
          writeJson(
            output,
            200,
            JSONObject().apply {
              put("sessionId", sessionId)
              put("status", "PENDING")
            }.toString(),
          )
        }

        method == "GET" && path.startsWith("/api/p2p/offer/") -> {
          val sessionId = path.removePrefix("/api/p2p/offer/")
          val session = offerSessions[sessionId]
          if (session == null) {
            writeError(output, 404, "Offer session not found.")
          } else {
            val offsets = JSONObject()
            session.request.files.forEach { f ->
              val part = getPartFile(sessionId, f.id)
              if (part.exists()) offsets.put(f.id, part.length())
            }
            writeJson(
              output,
              200,
              JSONObject().apply {
                put("sessionId", sessionId)
                put("status", session.status)
                put("receivedOffsets", offsets)
              }.toString(),
            )
          }
        }

        method == "POST" && path == "/api/p2p/stream" -> {
          handleP2pStream(input, output, headers, queryParams, clientIp)
        }

        else -> writeError(output, 404, "Endpoint not found.")
      }
    }
  }

  private fun serveFileDownload(
    file: ShareableFile,
    rangeHeader: String?,
    output: BufferedOutputStream,
    peerIp: String,
  ) {
    val totalSize = file.sizeBytes
    var startByte = 0L
    var endByte = (totalSize - 1).coerceAtLeast(0L)
    var isPartial = false

    if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
      val spec = rangeHeader.removePrefix("bytes=").trim()
      val split = spec.split('-')
      val s = split.getOrNull(0)?.toLongOrNull()
      val e = split.getOrNull(1)?.toLongOrNull()
      if (s != null && s < totalSize) {
        startByte = s
        if (e != null && e >= s && e < totalSize) endByte = e
        isPartial = true
      }
    }

    val length = if (totalSize <= 0L) 0L else (endByte - startByte + 1)
    val transferId = UUID.randomUUID().toString()
    val bufSize = settingsRepository.settings.value.bufferSizeKb * 1024

    val input = fileManager.openInputStream(file.uri)
    if (input == null) {
      writeError(output, 404, "Cannot open file stream.")
      return
    }

    val safeHeaderName = file.name.replace("\"", "_")
    val headerText = buildString {
      append(if (isPartial) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
      append("Content-Type: ${file.mimeType}\r\n")
      append("Content-Disposition: attachment; filename=\"$safeHeaderName\"\r\n")
      append("Accept-Ranges: bytes\r\n")
      if (isPartial) append("Content-Range: bytes $startByte-$endByte/$totalSize\r\n")
      append("Content-Length: $length\r\n")
      append("Connection: close\r\n\r\n")
    }
    output.write(headerText.toByteArray(Charsets.UTF_8))

    val baseItem = TransferItem(
      id = transferId,
      fileName = file.name,
      relativePath = file.relativePath,
      fileSize = totalSize,
      bytesTransferred = startByte,
      status = TransferStatus.IN_PROGRESS,
      direction = TransferDirection.SEND,
      mode = ConnectionMode.PHONE_TO_PC,
      peerName = "PC Browser",
      peerAddress = peerIp,
      sourceUri = file.uri.toString(),
      mimeType = file.mimeType,
    )
    transferManager.upsertLiveTransfer(baseItem)

    runCatching {
      input.use { stream ->
        var toSkip = startByte
        val skipBuf = ByteArray(minOf(65536, bufSize))
        while (toSkip > 0) {
          val n = stream.read(skipBuf, 0, minOf(skipBuf.size.toLong(), toSkip).toInt())
          if (n <= 0) break
          toSkip -= n
        }

        val buf = ByteArray(bufSize)
        var remaining = length
        var sent = startByte
        var lastUi = System.currentTimeMillis()
        var bytesDelta = 0L

        while (remaining > 0) {
          val maxRead = minOf(buf.size.toLong(), remaining).toInt()
          val read = stream.read(buf, 0, maxRead)
          if (read <= 0) break
          output.write(buf, 0, read)
          remaining -= read
          sent += read
          bytesDelta += read

          val now = System.currentTimeMillis()
          val dt = now - lastUi
          if (dt >= 250L) {
            val speed = (bytesDelta * 1000L) / dt.coerceAtLeast(1L)
            val eta = if (speed > 0L) (totalSize - sent) / speed else 0L
            transferManager.upsertLiveTransfer(
              baseItem.copy(bytesTransferred = sent, speedBytesPerSec = speed, etaSeconds = eta),
            )
            lastUi = now
            bytesDelta = 0L
          }
        }
      }
      output.flush()
      transferManager.completeIncomingTransfer(
        baseItem.copy(
          status = TransferStatus.COMPLETED,
          bytesTransferred = totalSize,
          speedBytesPerSec = 0L,
          etaSeconds = 0L,
          verified = true,
        ),
      )
    }.onFailure { err ->
      transferManager.completeIncomingTransfer(
        baseItem.copy(
          status = TransferStatus.FAILED,
          errorMessage = err.localizedMessage ?: "PC download interrupted",
        ),
      )
    }
  }

  private fun handleBrowserUpload(
    input: BufferedInputStream,
    output: BufferedOutputStream,
    headers: Map<String, String>,
    clientIp: String,
  ) {
    val rawName = headers["x-file-name"]?.let { URLDecoder.decode(it, "UTF-8") }
      ?: "upload_${System.currentTimeMillis()}"
    val fileName = Formatters.sanitizeFilename(rawName)
    val contentLen = headers["content-length"]?.toLongOrNull() ?: -1L
    val expectedSize = headers["x-file-size"]?.toLongOrNull() ?: contentLen.coerceAtLeast(0L)
    val mimeType = headers["content-type"] ?: "application/octet-stream"

    if (contentLen < 0L) {
      writeError(output, 411, "Content-Length header is required for streaming upload.")
      return
    }
    if (!fileManager.hasEnoughStorage(contentLen)) {
      writeError(output, 507, "Insufficient storage space on Android device.")
      return
    }

    val settings = settingsRepository.settings.value
    val safeDupAction = if (settings.duplicateAction == DuplicateAction.ASK) {
      DuplicateAction.RENAME // Never silently overwrite unattended browser uploads
    } else settings.duplicateAction

    val targetResult = fileManager.openDestinationOutput(
      treeUriString = settings.downloadTreeUri,
      relativePath = fileName,
      mimeType = mimeType,
      duplicateAction = safeDupAction,
    )

    val target = targetResult.getOrElse { err ->
      writeError(output, 500, err.localizedMessage ?: "Cannot open destination folder.")
      return
    }

    val transferId = UUID.randomUUID().toString()
    val bufSize = settings.bufferSizeKb * 1024
    val baseItem = TransferItem(
      id = transferId,
      fileName = target.finalFileName,
      relativePath = target.finalFileName,
      fileSize = expectedSize,
      status = TransferStatus.IN_PROGRESS,
      direction = TransferDirection.RECEIVE,
      mode = ConnectionMode.PC_TO_PHONE,
      peerName = "PC Browser",
      peerAddress = clientIp,
      savedUri = target.displayUri,
      mimeType = mimeType,
    )
    transferManager.upsertLiveTransfer(baseItem)

    val digest = MessageDigest.getInstance("SHA-256")
    var received = 0L
    runCatching {
      target.outputStream.use { out ->
        val buf = ByteArray(bufSize)
        var lastUi = System.currentTimeMillis()
        var delta = 0L
        while (received < contentLen) {
          val maxRead = minOf(buf.size.toLong(), contentLen - received).toInt()
          val read = input.read(buf, 0, maxRead)
          if (read <= 0) break
          out.write(buf, 0, read)
          digest.update(buf, 0, read)
          received += read
          delta += read

          val now = System.currentTimeMillis()
          val dt = now - lastUi
          if (dt >= 250L) {
            val speed = (delta * 1000L) / dt.coerceAtLeast(1L)
            val eta = if (speed > 0L) (contentLen - received) / speed else 0L
            transferManager.upsertLiveTransfer(
              baseItem.copy(bytesTransferred = received, speedBytesPerSec = speed, etaSeconds = eta),
            )
            lastUi = now
            delta = 0L
          }
        }
        out.flush()
      }

      if (received < contentLen) {
        target.abortAction()
        throw IllegalStateException("Upload interrupted before completion.")
      }
      target.finalizeAction()
      val sha = digest.digest().joinToString("") { "%02x".format(it) }
      transferManager.completeIncomingTransfer(
        baseItem.copy(
          status = TransferStatus.COMPLETED,
          bytesTransferred = received,
          speedBytesPerSec = 0L,
          etaSeconds = 0L,
          sha256Local = sha,
          verified = true,
        ),
      )
      writeJson(
        output,
        200,
        JSONObject().apply {
          put("status", "ok")
          put("fileName", target.finalFileName)
          put("size", received)
          put("sha256", sha)
        }.toString(),
      )
    }.onFailure { err ->
      target.abortAction()
      transferManager.completeIncomingTransfer(
        baseItem.copy(
          status = TransferStatus.FAILED,
          bytesTransferred = received,
          errorMessage = err.localizedMessage ?: "Upload failed",
        ),
      )
      writeError(output, 500, err.localizedMessage ?: "Upload failed")
    }
  }

  private fun handleP2pStream(
    input: BufferedInputStream,
    output: BufferedOutputStream,
    headers: Map<String, String>,
    query: Map<String, String>,
    clientIp: String,
  ) {
    val sessionId = query["sessionId"] ?: return writeError(output, 400, "Missing sessionId")
    val fileId = query["fileId"] ?: return writeError(output, 400, "Missing fileId")
    val offset = query["offset"]?.toLongOrNull() ?: 0L

    val session = offerSessions[sessionId]
    if (session == null || session.status != "ACCEPTED") {
      writeError(output, 403, "Transfer session not accepted.")
      return
    }
    val meta = session.request.files.firstOrNull { it.id == fileId }
      ?: return writeError(output, 404, "File not part of offer.")

    val contentLen = headers["content-length"]?.toLongOrNull() ?: (meta.size - offset).coerceAtLeast(0L)
    val remoteSha = headers["x-file-sha256"]

    val partFile = getPartFile(sessionId, fileId)
    partFile.parentFile?.mkdirs()

    val transferId = partTransferIds.getOrPut("$sessionId:$fileId") { UUID.randomUUID().toString() }
    val controlCheck = transferManager.registerControlForIncoming(transferId)
    val bufSize = settingsRepository.settings.value.bufferSizeKb * 1024

    val baseItem = TransferItem(
      id = transferId,
      fileName = meta.name,
      relativePath = meta.relativePath,
      fileSize = meta.size,
      bytesTransferred = offset,
      status = TransferStatus.IN_PROGRESS,
      direction = TransferDirection.RECEIVE,
      mode = ConnectionMode.PHONE_TO_PHONE,
      peerName = session.request.senderName,
      peerAddress = clientIp,
      mimeType = meta.mimeType,
    )
    transferManager.upsertLiveTransfer(baseItem)

    var receivedThisChunk = 0L
    runCatching {
      RandomAccessFile(partFile, "rw").use { raf ->
        raf.seek(offset)
        val buf = ByteArray(bufSize)
        var lastUi = System.currentTimeMillis()
        var delta = 0L

        while (receivedThisChunk < contentLen) {
          val (paused, cancelled) = controlCheck()
          if (cancelled) throw InterruptedException("CANCELLED")
          if (paused) throw InterruptedException("PAUSED")

          val maxRead = minOf(buf.size.toLong(), contentLen - receivedThisChunk).toInt()
          val read = input.read(buf, 0, maxRead)
          if (read <= 0) break
          raf.write(buf, 0, read)
          receivedThisChunk += read
          delta += read

          val now = System.currentTimeMillis()
          val dt = now - lastUi
          if (dt >= 250L) {
            val totalNow = offset + receivedThisChunk
            val speed = (delta * 1000L) / dt.coerceAtLeast(1L)
            val eta = if (speed > 0L) (meta.size - totalNow).coerceAtLeast(0L) / speed else 0L
            transferManager.upsertLiveTransfer(
              baseItem.copy(bytesTransferred = totalNow, speedBytesPerSec = speed, etaSeconds = eta),
            )
            lastUi = now
            delta = 0L
          }
        }
      }

      val totalOnDisk = partFile.length()
      if (totalOnDisk < meta.size) {
        throw IllegalStateException("Stream incomplete ($totalOnDisk / ${meta.size} bytes)")
      }

      // Compute SHA-256 of completed .part file and copy to final destination
      val digest = MessageDigest.getInstance("SHA-256")
      val settings = settingsRepository.settings.value
      val target = fileManager.openDestinationOutput(
        treeUriString = settings.downloadTreeUri,
        relativePath = meta.relativePath,
        mimeType = meta.mimeType,
        duplicateAction = session.duplicateResolution,
      ).getOrThrow()

      runCatching {
        partFile.inputStream().use { partIn ->
          target.outputStream.use { destOut ->
            val buf = ByteArray(bufSize)
            while (true) {
              val read = partIn.read(buf)
              if (read <= 0) break
              digest.update(buf, 0, read)
              destOut.write(buf, 0, read)
            }
            destOut.flush()
          }
        }
        target.finalizeAction()
      }.onFailure { e ->
        target.abortAction()
        throw e
      }

      partFile.delete()
      partTransferIds.remove("$sessionId:$fileId")

      val localSha = digest.digest().joinToString("") { "%02x".format(it) }
      val verified = remoteSha.isNullOrBlank() || localSha.equals(remoteSha, ignoreCase = true)

      transferManager.completeIncomingTransfer(
        baseItem.copy(
          fileName = target.finalFileName,
          status = if (verified) TransferStatus.COMPLETED else TransferStatus.FAILED,
          bytesTransferred = meta.size,
          speedBytesPerSec = 0L,
          etaSeconds = 0L,
          sha256Local = localSha,
          sha256Remote = remoteSha,
          verified = verified,
          savedUri = target.displayUri,
          errorMessage = if (verified) null else "SHA-256 integrity verification failed",
        ),
      )

      writeJson(
        output,
        200,
        JSONObject().apply {
          put("status", "ok")
          put("sha256", localSha)
          put("verified", verified)
        }.toString(),
      )
    }.onFailure { err ->
      when (err.message) {
        "PAUSED" -> {
          transferManager.upsertLiveTransfer(
            baseItem.copy(
              status = TransferStatus.PAUSED,
              bytesTransferred = offset + receivedThisChunk,
              speedBytesPerSec = 0L,
            ),
          )
          writeError(output, 499, "Paused by receiver")
        }
        "CANCELLED" -> {
          partFile.delete()
          partTransferIds.remove("$sessionId:$fileId")
          transferManager.completeIncomingTransfer(
            baseItem.copy(status = TransferStatus.CANCELLED, bytesTransferred = offset + receivedThisChunk),
          )
          writeError(output, 499, "Cancelled by receiver")
        }
        else -> {
          transferManager.completeIncomingTransfer(
            baseItem.copy(
              status = TransferStatus.FAILED,
              bytesTransferred = offset + receivedThisChunk,
              errorMessage = err.localizedMessage ?: "Receive stream error",
            ),
          )
          writeError(output, 500, err.localizedMessage ?: "Receive stream error")
        }
      }
    }
  }

  private fun getPartFile(sessionId: String, fileId: String): File {
    val staging = File(context.cacheDir, "p2p_staging")
    return File(staging, "${sessionId}_${fileId}.part")
  }

  private fun readHttpHeaders(input: InputStream): List<String>? {
    val lines = mutableListOf<String>()
    val sb = StringBuilder()
    var prev = -1
    var totalBytes = 0
    while (totalBytes < 16384) {
      val b = input.read()
      if (b == -1) return if (lines.isEmpty()) null else lines
      totalBytes++
      if (b == '\n'.code) {
        val line = sb.toString().trimEnd('\r')
        sb.clear()
        if (line.isEmpty()) {
          return lines
        }
        lines += line
      } else {
        sb.append(b.toChar())
      }
      prev = b
    }
    return null
  }

  private fun readFixedBytes(input: InputStream, count: Int): ByteArray {
    if (count <= 0) return ByteArray(0)
    val result = ByteArray(count)
    var offset = 0
    while (offset < count) {
      val read = input.read(result, offset, count - offset)
      if (read <= 0) break
      offset += read
    }
    return if (offset == count) result else result.copyOf(offset)
  }

  private fun parseQueryParams(query: String): Map<String, String> {
    if (query.isBlank()) return emptyMap()
    val map = LinkedHashMap<String, String>()
    for (pair in query.split('&')) {
      val eq = pair.indexOf('=')
      if (eq > 0) {
        val k = URLDecoder.decode(pair.substring(0, eq), "UTF-8")
        val v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
        map[k] = v
      }
    }
    return map
  }

  private fun writeJson(out: java.io.OutputStream, code: Int, json: String) {
    val bytes = json.toByteArray(Charsets.UTF_8)
    val statusText = when (code) {
      200 -> "OK"
      400 -> "Bad Request"
      401 -> "Unauthorized"
      403 -> "Forbidden"
      404 -> "Not Found"
      507 -> "Insufficient Storage"
      else -> "Error"
    }
    val head = "HTTP/1.1 $code $statusText\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
    out.write(head.toByteArray(Charsets.UTF_8))
    out.write(bytes)
    out.flush()
  }

  private fun writeHtml(out: java.io.OutputStream, html: String) {
    val bytes = html.toByteArray(Charsets.UTF_8)
    val head = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
    out.write(head.toByteArray(Charsets.UTF_8))
    out.write(bytes)
    out.flush()
  }

  private fun writeError(out: java.io.OutputStream, code: Int, message: String) {
    writeJson(out, code, JSONObject().put("error", message).toString())
  }

  private fun generateToken(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    val rng = SecureRandom()
    return (1..6).map { chars[rng.nextInt(chars.length)] }.joinToString("")
  }
}
