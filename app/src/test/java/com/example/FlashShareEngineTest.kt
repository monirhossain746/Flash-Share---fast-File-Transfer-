package com.example

import com.example.flashshare.network.QRCodeManager
import com.example.flashshare.util.Formatters
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashShareEngineTest {

  @Test
  fun `formatBytes formats B KB MB GB cleanly`() {
    assertEquals("0 B", Formatters.formatBytes(0))
    assertEquals("512 B", Formatters.formatBytes(512))
    assertEquals("1.50 KB", Formatters.formatBytes(1536))
    assertEquals("2.00 MB", Formatters.formatBytes(2L * 1024L * 1024L))
  }

  @Test
  fun `formatSpeed displays KB or MB per sec`() {
    assertEquals("0.0 MB/s", Formatters.formatSpeed(0))
    assertEquals("10.00 MB/s", Formatters.formatSpeed(10L * 1024L * 1024L))
  }

  @Test
  fun `isLocalPrivateIp accepts LAN IPs and rejects public IPs`() {
    assertTrue(Formatters.isLocalPrivateIp(InetAddress.getByName("192.168.1.42")))
    assertTrue(Formatters.isLocalPrivateIp(InetAddress.getByName("10.0.0.5")))
    assertTrue(Formatters.isLocalPrivateIp(InetAddress.getByName("172.16.4.12")))
    assertTrue(Formatters.isLocalPrivateIp(InetAddress.getByName("127.0.0.1")))
    assertFalse(Formatters.isLocalPrivateIp(InetAddress.getByName("8.8.8.8")))
    assertFalse(Formatters.isLocalPrivateIp(InetAddress.getByName("1.1.1.1")))
  }

  @Test
  fun `sanitizeRelativePath prevents directory traversal`() {
    assertEquals("folder/test.apk", Formatters.sanitizeRelativePath("../../folder/../test.apk"))
    assertEquals("archive.zip", Formatters.sanitizeFilename("../archive.zip"))
  }

  @Test
  fun `qr pairing payload builds and parses accurately`() {
    val uri = QRCodeManager.buildPairUri("192.168.43.1", 8080, "A7K9P2", "Pixel 9")
    val parsed = QRCodeManager.parseQrPayload(uri)
    assertNotNull(parsed)
    assertEquals("192.168.43.1", parsed!!.ipAddress)
    assertEquals(8080, parsed.port)
    assertEquals("A7K9P2", parsed.token)
    assertEquals("Pixel 9", parsed.deviceName)
  }

  @Test
  fun `qr web url parses cleanly`() {
    val url = QRCodeManager.buildWebUrl("192.168.1.15", 9090, "XYZ123")
    val parsed = QRCodeManager.parseQrPayload(url)
    assertNotNull(parsed)
    assertEquals("192.168.1.15", parsed!!.ipAddress)
    assertEquals(9090, parsed.port)
    assertEquals("XYZ123", parsed.token)
  }
}
