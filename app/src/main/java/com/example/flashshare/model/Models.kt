package com.example.flashshare.model

import android.net.Uri
import java.util.UUID

enum class ConnectionMode(val label: String) {
  PHONE_TO_PHONE("Phone to Phone"),
  PHONE_TO_PC("Phone to PC"),
  PC_TO_PHONE("PC to Phone"),
}

enum class TransferDirection {
  SEND,
  RECEIVE,
}

enum class TransferStatus {
  PENDING,
  IN_PROGRESS,
  PAUSED,
  COMPLETED,
  FAILED,
  CANCELLED,
  REJECTED,
}

enum class FileCategory(val label: String) {
  ALL("Browse All"),
  IMAGES("Images"),
  VIDEOS("Videos"),
  AUDIO("Audio"),
  DOCUMENTS("Documents"),
  APK("APK"),
  ARCHIVES("ZIP / RAR"),
  OTHER("Other Files"),
}

enum class ThemePreference(val label: String) {
  SYSTEM("System"),
  DARK("Dark"),
  LIGHT("Light"),
}

enum class DuplicateAction(val label: String) {
  ASK("Ask Before Overwrite"),
  RENAME("Keep Both (Rename)"),
  OVERWRITE("Replace Existing"),
  SKIP("Skip Duplicate"),
}

data class ShareableFile(
  val id: String = UUID.randomUUID().toString(),
  val uri: Uri,
  val name: String,
  val extension: String,
  val sizeBytes: Long,
  val mimeType: String,
  val relativePath: String = name,
  val category: FileCategory,
  val lastModified: Long = System.currentTimeMillis(),
) {
  val isExecutableOrInstaller: Boolean
    get() = extension.lowercase() in setOf("apk", "xapk", "exe", "msi", "bat", "sh")
}

data class TransferItem(
  val id: String = UUID.randomUUID().toString(),
  val fileName: String,
  val relativePath: String = fileName,
  val fileSize: Long,
  val bytesTransferred: Long = 0L,
  val speedBytesPerSec: Long = 0L,
  val etaSeconds: Long = 0L,
  val status: TransferStatus = TransferStatus.PENDING,
  val direction: TransferDirection,
  val mode: ConnectionMode,
  val peerName: String,
  val peerAddress: String,
  val sha256Local: String? = null,
  val sha256Remote: String? = null,
  val verified: Boolean = false,
  val errorMessage: String? = null,
  val timestamp: Long = System.currentTimeMillis(),
  val savedUri: String? = null,
  val sourceUri: String? = null,
  val mimeType: String = "application/octet-stream",
) {
  val progress: Float
    get() = if (fileSize <= 0L) 0f else (bytesTransferred.toDouble() / fileSize.toDouble()).toFloat().coerceIn(0f, 1f)

  val remainingBytes: Long
    get() = (fileSize - bytesTransferred).coerceAtLeast(0L)

  val isExecutableOrInstaller: Boolean
    get() {
      val ext = fileName.substringAfterLast('.', "").lowercase()
      return ext in setOf("apk", "xapk", "exe", "msi", "bat", "sh")
    }
}

data class RemoteFileMeta(
  val id: String,
  val name: String,
  val relativePath: String,
  val size: Long,
  val mimeType: String,
  val sha256: String? = null,
)

data class IncomingTransferRequest(
  val sessionId: String,
  val senderName: String,
  val senderIp: String,
  val token: String,
  val files: List<RemoteFileMeta>,
  val totalBytes: Long,
  val duplicateNames: List<String> = emptyList(),
  val createdAt: Long = System.currentTimeMillis(),
)

data class DiscoveredDevice(
  val id: String,
  val name: String,
  val ipAddress: String,
  val port: Int,
  val tokenHint: String = "",
  val source: String = "LAN",
  val lastSeen: Long = System.currentTimeMillis(),
)

data class NetworkInterfaceEntry(
  val name: String,
  val ipAddress: String,
  val isHotspotCandidate: Boolean,
)

data class NetworkInfoState(
  val isConnected: Boolean = false,
  val connectionType: String = "Checking...",
  val primaryIp: String = "0.0.0.0",
  val interfaces: List<NetworkInterfaceEntry> = emptyList(),
  val frequencyMhz: Int? = null,
  val linkSpeedMbps: Int? = null,
  val bandLabel: String = "Unknown",
  val isHotspotLikely: Boolean = false,
  val statusMessage: String = "Connect to Wi-Fi or enable Mobile Hotspot",
)

data class AppSettings(
  val deviceName: String = android.os.Build.MODEL.ifBlank { "Android Device" },
  val defaultPort: Int = 8080,
  val downloadTreeUri: String? = null,
  val downloadFolderLabel: String = "Downloads/FlashShare",
  val bufferSizeKb: Int = 256,
  val themePreference: ThemePreference = ThemePreference.SYSTEM,
  val notificationsEnabled: Boolean = true,
  val autoVerifySha256: Boolean = true,
  val duplicateAction: DuplicateAction = DuplicateAction.ASK,
)

data class ConnectionTestResult(
  val reachable: Boolean,
  val latencyMs: Long = 0L,
  val serverName: String? = null,
  val message: String,
)
