package com.example.flashshare.transfer

import android.content.Context
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.TransferDirection
import com.example.flashshare.model.TransferItem
import com.example.flashshare.model.TransferStatus
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

class TransferHistory(context: Context) {

  private val historyFile = File(context.filesDir, "transfer_history.json")
  private val mutex = Mutex()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _history = MutableStateFlow<List<TransferItem>>(emptyList())
  val history: StateFlow<List<TransferItem>> = _history.asStateFlow()

  init {
    scope.launch { loadFromDisk() }
  }

  private suspend fun loadFromDisk() {
    mutex.withLock {
      if (!historyFile.exists()) return@withLock
      runCatching {
        val raw = historyFile.readText()
        val array = JSONArray(raw)
        val items = mutableListOf<TransferItem>()
        for (i in 0 until array.length()) {
          val obj = array.getJSONObject(i)
          items += TransferItem(
            id = obj.getString("id"),
            fileName = obj.getString("fileName"),
            relativePath = obj.optString("relativePath", obj.getString("fileName")),
            fileSize = obj.getLong("fileSize"),
            bytesTransferred = obj.optLong("bytesTransferred", 0L),
            speedBytesPerSec = obj.optLong("speedBytesPerSec", 0L),
            etaSeconds = 0L,
            status = runCatching { TransferStatus.valueOf(obj.getString("status")) }
              .getOrDefault(TransferStatus.FAILED),
            direction = runCatching { TransferDirection.valueOf(obj.getString("direction")) }
              .getOrDefault(TransferDirection.RECEIVE),
            mode = runCatching { ConnectionMode.valueOf(obj.getString("mode")) }
              .getOrDefault(ConnectionMode.PHONE_TO_PHONE),
            peerName = obj.optString("peerName", "Unknown"),
            peerAddress = obj.optString("peerAddress", ""),
            sha256Local = obj.optString("sha256Local").takeIf { it.isNotEmpty() },
            sha256Remote = obj.optString("sha256Remote").takeIf { it.isNotEmpty() },
            verified = obj.optBoolean("verified", false),
            errorMessage = obj.optString("errorMessage").takeIf { it.isNotEmpty() },
            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
            savedUri = obj.optString("savedUri").takeIf { it.isNotEmpty() },
            sourceUri = obj.optString("sourceUri").takeIf { it.isNotEmpty() },
            mimeType = obj.optString("mimeType", "application/octet-stream"),
          )
        }
        _history.value = items.sortedByDescending { it.timestamp }
      }
    }
  }

  suspend fun recordOrUpdate(item: TransferItem) {
    mutex.withLock {
      val current = _history.value.toMutableList()
      val idx = current.indexOfFirst { it.id == item.id }
      if (idx >= 0) {
        current[idx] = item
      } else {
        current.add(0, item)
      }
      val trimmed = current.take(250)
      _history.value = trimmed
      persistLocked(trimmed)
    }
  }

  suspend fun remove(id: String) {
    mutex.withLock {
      val updated = _history.value.filterNot { it.id == id }
      _history.value = updated
      persistLocked(updated)
    }
  }

  suspend fun clearAll() {
    mutex.withLock {
      _history.value = emptyList()
      persistLocked(emptyList())
    }
  }

  suspend fun clearByStatuses(statuses: Set<TransferStatus>) {
    mutex.withLock {
      val updated = _history.value.filterNot { it.status in statuses }
      _history.value = updated
      persistLocked(updated)
    }
  }

  private fun persistLocked(items: List<TransferItem>) {
    runCatching {
      val array = JSONArray()
      items.forEach { item ->
        val obj = JSONObject().apply {
          put("id", item.id)
          put("fileName", item.fileName)
          put("relativePath", item.relativePath)
          put("fileSize", item.fileSize)
          put("bytesTransferred", item.bytesTransferred)
          put("speedBytesPerSec", item.speedBytesPerSec)
          put("status", item.status.name)
          put("direction", item.direction.name)
          put("mode", item.mode.name)
          put("peerName", item.peerName)
          put("peerAddress", item.peerAddress)
          put("sha256Local", item.sha256Local ?: "")
          put("sha256Remote", item.sha256Remote ?: "")
          put("verified", item.verified)
          put("errorMessage", item.errorMessage ?: "")
          put("timestamp", item.timestamp)
          put("savedUri", item.savedUri ?: "")
          put("sourceUri", item.sourceUri ?: "")
          put("mimeType", item.mimeType)
        }
        array.put(obj)
      }
      historyFile.writeText(array.toString())
    }
  }
}
