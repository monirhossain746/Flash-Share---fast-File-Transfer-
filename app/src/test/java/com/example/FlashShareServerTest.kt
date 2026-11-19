package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.flashshare.model.FileCategory
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.transfer.FlashShareContainer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FlashShareServerTest {

  private var container: FlashShareContainer? = null

  @After
  fun tearDown() {
    container?.localServer?.stop()
  }

  @Test
  fun `localServer serves web portal ping downloads with range and accepts streaming upload`() {
    val ctx = ApplicationProvider.getApplicationContext<Context>()
    val c = FlashShareContainer.get(ctx)
    container = c

    // Create real sample file in cacheDir
    val sampleBytes = "FlashShare-Offline-Transfer-Engine-Payload-1234567890".toByteArray()
    val sampleFile = File(ctx.cacheDir, "sample_doc.txt").apply { writeBytes(sampleBytes) }
    val shareable = ShareableFile(
      id = "file-1",
      uri = Uri.fromFile(sampleFile),
      name = "sample_doc.txt",
      extension = "TXT",
      sizeBytes = sampleBytes.size.toLong(),
      mimeType = "text/plain",
      relativePath = "sample_doc.txt",
      category = FileCategory.DOCUMENTS,
    )
    c.localServer.updateSharedFiles(listOf(shareable))
    c.localServer.start(18080)

    // Wait briefly for server socket bind
    var attempts = 0
    while (!c.localServer.isRunning.value && attempts < 40) {
      Thread.sleep(50)
      attempts++
    }
    assertTrue("LocalServer should start and bind", c.localServer.isRunning.value)
    val port = c.localServer.activePort.value
    val token = c.localServer.sessionToken.value

    // 1. Test /api/ping
    val pingConn = URL("http://127.0.0.1:$port/api/ping").openConnection() as HttpURLConnection
    assertEquals(200, pingConn.responseCode)
    val pingBody = pingConn.inputStream.bufferedReader().readText()
    assertTrue(pingBody.contains("FlashShare"))

    // 2. Test /api/download/file-1 with Range header
    val dlConn = URL("http://127.0.0.1:$port/api/download/file-1?token=$token").openConnection() as HttpURLConnection
    dlConn.setRequestProperty("Range", "bytes=0-9")
    assertEquals(206, dlConn.responseCode)
    val partial = dlConn.inputStream.readBytes().decodeToString()
    assertEquals("FlashShare", partial)

    // 3. Test /api/upload streaming + SHA-256 verification
    val uploadPayload = ByteArray(4096) { (it % 251).toByte() }
    val expectedSha = MessageDigest.getInstance("SHA-256")
      .digest(uploadPayload)
      .joinToString("") { "%02x".format(it) }

    val upConn = URL("http://127.0.0.1:$port/api/upload?token=$token").openConnection() as HttpURLConnection
    upConn.requestMethod = "POST"
    upConn.doOutput = true
    upConn.setFixedLengthStreamingMode(uploadPayload.size)
    upConn.setRequestProperty("X-File-Name", "pc_upload_test.bin")
    upConn.setRequestProperty("X-File-Size", uploadPayload.size.toString())
    upConn.setRequestProperty("Content-Type", "application/octet-stream")
    BufferedOutputStream(upConn.outputStream).use { it.write(uploadPayload) }

    assertEquals(200, upConn.responseCode)
    val upResp = BufferedReader(InputStreamReader(BufferedInputStream(upConn.inputStream))).readText()
    assertTrue("Upload response should verify matching SHA-256", upResp.contains(expectedSha))
  }
}
