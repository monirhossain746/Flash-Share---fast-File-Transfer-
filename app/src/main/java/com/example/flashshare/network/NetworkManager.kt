package com.example.flashshare.network

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.provider.Settings
import com.example.flashshare.model.ConnectionTestResult
import com.example.flashshare.model.NetworkInfoState
import com.example.flashshare.model.NetworkInterfaceEntry
import com.example.flashshare.util.Formatters
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class NetworkManager(private val context: Context) {

  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private val wifiManager =
    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

  private val _networkState = MutableStateFlow(NetworkInfoState())
  val networkState: StateFlow<NetworkInfoState> = _networkState.asStateFlow()

  private var callbackRegistered = false
  private val networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) { refreshNetworkInfo() }
    override fun onLost(network: Network) { refreshNetworkInfo() }
    override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
      refreshNetworkInfo()
    }
  }

  fun startMonitoring() {
    refreshNetworkInfo()
    if (!callbackRegistered) {
      runCatching {
        val request = NetworkRequest.Builder()
          .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
          .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
          .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        callbackRegistered = true
      }
    }
  }

  fun stopMonitoring() {
    if (callbackRegistered) {
      runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
      callbackRegistered = false
    }
  }

  fun refreshNetworkInfo() {
    val entries = mutableListOf<NetworkInterfaceEntry>()
    runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val nif = interfaces.nextElement()
        if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
        val addrs = nif.inetAddresses
        while (addrs.hasMoreElements()) {
          val addr = addrs.nextElement()
          if (addr is Inet4Address && !addr.isLoopbackAddress && Formatters.isLocalPrivateIp(addr)) {
            val host = addr.hostAddress ?: continue
            val name = nif.name.lowercase()
            val hotspotCandidate = name.startsWith("ap") ||
              name.startsWith("swlan") ||
              name.startsWith("wlan1") ||
              name.startsWith("softap") ||
              host.startsWith("192.168.43.") ||
              host.startsWith("192.168.49.")
            entries += NetworkInterfaceEntry(
              name = nif.name,
              ipAddress = host,
              isHotspotCandidate = hotspotCandidate,
            )
          }
        }
      }
    }

    val sorted = entries.sortedWith(
      compareByDescending<NetworkInterfaceEntry> { it.isHotspotCandidate }
        .thenByDescending { it.name.startsWith("wlan") }
        .thenByDescending { it.name.startsWith("eth") }
    )

    val primaryIp = sorted.firstOrNull()?.ipAddress ?: "0.0.0.0"
    val hotspotLikely = sorted.any { it.isHotspotCandidate }

    @Suppress("DEPRECATION")
    val wifiInfo = runCatching { wifiManager.connectionInfo }.getOrNull()
    val freqMhz = wifiInfo?.frequency?.takeIf { it > 0 }
    val linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 }
    val bandLabel = when {
      freqMhz == null -> if (hotspotLikely) "Hotspot Active" else "Unknown"
      freqMhz in 2400..2500 -> "2.4 GHz"
      freqMhz in 4900..5900 -> "5 GHz"
      freqMhz > 5900 -> "6 GHz"
      else -> "${freqMhz} MHz"
    }

    val activeNet = connectivityManager.activeNetwork
    val caps = activeNet?.let { connectivityManager.getNetworkCapabilities(it) }
    val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    val hasEth = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true

    val connType = when {
      hotspotLikely -> "Wi-Fi Hotspot"
      hasWifi -> "Wi-Fi ($bandLabel)"
      hasEth -> "Ethernet"
      primaryIp != "0.0.0.0" -> "Local LAN"
      else -> "Offline"
    }

    val connected = primaryIp != "0.0.0.0"
    val statusMsg = when {
      hotspotLikely -> "Hotspot active on $primaryIp — ready for devices"
      connected -> "Ready on $connType ($primaryIp)"
      else -> "Connect both devices to the same Wi-Fi or turn on Mobile Hotspot"
    }

    _networkState.value = NetworkInfoState(
      isConnected = connected,
      connectionType = connType,
      primaryIp = primaryIp,
      interfaces = sorted,
      frequencyMhz = freqMhz,
      linkSpeedMbps = linkSpeedMbps,
      bandLabel = bandLabel,
      isHotspotLikely = hotspotLikely,
      statusMessage = statusMsg,
    )
  }

  suspend fun testConnection(ip: String, port: Int): ConnectionTestResult = withContext(Dispatchers.IO) {
    val cleanIp = ip.trim()
    if (!Formatters.isValidIpv4String(cleanIp)) {
      return@withContext ConnectionTestResult(
        reachable = false,
        message = "Invalid IPv4 address format (example: 192.168.1.42).",
      )
    }
    if (port !in 1024..65535) {
      return@withContext ConnectionTestResult(
        reachable = false,
        message = "Port must be between 1024 and 65535.",
      )
    }

    val start = System.currentTimeMillis()
    runCatching {
      Socket().use { socket ->
        socket.soTimeout = 3500
        socket.connect(InetSocketAddress(cleanIp, port), 3500)
        val out = socket.getOutputStream()
        val req = "GET /api/ping HTTP/1.1\r\nHost: $cleanIp:$port\r\nConnection: close\r\n\r\n"
        out.write(req.toByteArray(Charsets.UTF_8))
        out.flush()

        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
        val statusLine = reader.readLine() ?: ""
        var line: String?
        while (reader.readLine().also { line = it } != null) {
          if (line.isNullOrEmpty()) break
        }
        val body = reader.readText()
        val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)

        if (statusLine.contains("200")) {
          val json = runCatching { JSONObject(body) }.getOrNull()
          val serverName = json?.optString("deviceName")?.takeIf { it.isNotBlank() } ?: "FlashShare Peer"
          ConnectionTestResult(
            reachable = true,
            latencyMs = latency,
            serverName = serverName,
            message = "Connected to $serverName in ${latency}ms",
          )
        } else {
          ConnectionTestResult(
            reachable = false,
            latencyMs = latency,
            message = "Port $port is open, but FlashShare is not responding there.",
          )
        }
      }
    }.getOrElse { err ->
      ConnectionTestResult(
        reachable = false,
        message = "Could not reach $cleanIp:$port (${err.localizedMessage ?: "Connection timed out"}).",
      )
    }
  }

  fun openHotspotSettings() {
    val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
      openWifiSettings()
    }
  }

  fun openWifiSettings() {
    val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
  }
}
