package com.example.flashshare.network

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class ParsedPairingQr(
  val ipAddress: String,
  val port: Int,
  val token: String,
  val deviceName: String,
)

object QRCodeManager {

  fun buildPairUri(ip: String, port: Int, token: String, deviceName: String): String {
    val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
    return "flashshare://pair?ip=${enc(ip)}&port=$port&token=${enc(token)}&name=${enc(deviceName)}"
  }

  fun buildWebUrl(ip: String, port: Int, token: String): String {
    return "http://$ip:$port/?token=$token"
  }

  fun parseQrPayload(raw: String): ParsedPairingQr? {
    val trimmed = raw.trim()
    val uri = runCatching { URI.create(trimmed) }.getOrNull() ?: return null
    val params = parseQuery(uri.rawQuery)
    return when (uri.scheme?.lowercase()) {
      "flashshare" -> {
        val ip = params["ip"]?.takeIf { it.isNotBlank() } ?: return null
        val port = params["port"]?.toIntOrNull() ?: 8080
        val token = params["token"] ?: ""
        val name = params["name"] ?: "FlashShare Peer"
        ParsedPairingQr(ip, port, token, name)
      }
      "http", "https" -> {
        val ip = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = if (uri.port > 0) uri.port else 8080
        val token = params["token"] ?: ""
        ParsedPairingQr(ip, port, token, "FlashShare Server")
      }
      else -> null
    }
  }

  private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    val map = LinkedHashMap<String, String>()
    for (pair in rawQuery.split('&')) {
      val idx = pair.indexOf('=')
      if (idx > 0) {
        val k = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
        val v = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
        map[k] = v
      }
    }
    return map
  }

  fun generateQrBitmap(content: String, sizePx: Int = 512): ImageBitmap? {
    if (content.isBlank()) return null
    return runCatching {
      val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to 2,
      )
      val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
      val pixels = IntArray(sizePx * sizePx)
      val darkColor = android.graphics.Color.parseColor("#060E1A")
      val lightColor = android.graphics.Color.WHITE
      for (y in 0 until sizePx) {
        val offset = y * sizePx
        for (x in 0 until sizePx) {
          pixels[offset + x] = if (matrix[x, y]) darkColor else lightColor
        }
      }
      val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
      bmp.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
      bmp.asImageBitmap()
    }.getOrNull()
  }

  fun decodeFromImageProxy(imageProxy: ImageProxy): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val data = ByteArray(buffer.remaining())
    buffer.get(data)

    val width = imageProxy.width
    val height = imageProxy.height
    val source = PlanarYUVLuminanceSource(
      data,
      plane.rowStride,
      height,
      0,
      0,
      width,
      height,
      false,
    )
    val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
    val reader = MultiFormatReader().apply {
      setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    return runCatching { reader.decode(binaryBitmap).text }.getOrNull()
  }
}
