package com.example.flashshare.util

import java.net.Inet4Address
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object Formatters {

  fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val value = bytes / 1024.0.pow(digitGroups.toDouble())
    return if (digitGroups == 0) {
      "$bytes B"
    } else {
      String.format(Locale.US, "%.2f %s", value, units[digitGroups])
    }
  }

  fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0L) return "0.0 MB/s"
    val mbPerSec = bytesPerSec / (1024.0 * 1024.0)
    return if (mbPerSec >= 0.1) {
      String.format(Locale.US, "%.2f MB/s", mbPerSec)
    } else {
      val kbPerSec = bytesPerSec / 1024.0
      String.format(Locale.US, "%.0f KB/s", kbPerSec)
    }
  }

  fun formatDuration(seconds: Long): String {
    if (seconds <= 0L) return "--"
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
      hrs > 0 -> String.format(Locale.US, "%dh %02dm", hrs, mins)
      mins > 0 -> String.format(Locale.US, "%dm %02ds", mins, secs)
      else -> "${secs}s"
    }
  }

  fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
  }

  fun sanitizeFilename(rawName: String): String {
    val base = rawName.substringAfterLast('/').substringAfterLast('\\').trim()
    val cleaned = base.replace(Regex("[\\x00-\\x1F<>:\"|?*]"), "_")
    return if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
      "file_${System.currentTimeMillis()}"
    } else {
      cleaned
    }
  }

  fun sanitizeRelativePath(rawPath: String): String {
    val parts = rawPath.replace('\\', '/').split('/')
      .map { it.trim() }
      .filter { it.isNotEmpty() && it != "." && it != ".." }
      .map { it.replace(Regex("[\\x00-\\x1F<>:\"|?*]"), "_") }
    return if (parts.isEmpty()) "file_${System.currentTimeMillis()}" else parts.joinToString("/")
  }

  fun isLocalPrivateIp(address: InetAddress): Boolean {
    if (address !is Inet4Address) return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
    val bytes = address.address
    val b0 = bytes[0].toInt() and 0xFF
    val b1 = bytes[1].toInt() and 0xFF
    return when {
      b0 == 127 -> true // Loopback
      b0 == 10 -> true // 10.0.0.0/8
      b0 == 172 && b1 in 16..31 -> true // 172.16.0.0/12
      b0 == 192 && b1 == 168 -> true // 192.168.0.0/16
      b0 == 169 && b1 == 254 -> true // Link-local
      else -> false
    }
  }

  fun isValidIpv4String(ip: String): Boolean {
    val parts = ip.trim().split('.')
    if (parts.size != 4) return false
    return parts.all { part ->
      val num = part.toIntOrNull() ?: return false
      num in 0..255
    }
  }
}
