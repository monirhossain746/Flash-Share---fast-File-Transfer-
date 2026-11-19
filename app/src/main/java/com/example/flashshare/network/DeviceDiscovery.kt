package com.example.flashshare.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.example.flashshare.model.DiscoveredDevice
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
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
import org.json.JSONObject

class DeviceDiscovery(
  private val context: Context,
  private val networkManager: NetworkManager,
) {

  companion object {
    private const val SERVICE_TYPE = "_flashshare._tcp."
    private const val UDP_BEACON_PORT = 45678
    private const val STALE_TIMEOUT_MS = 16_000L
  }

  private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
  private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

  private var multicastLock: WifiManager.MulticastLock? = null
  private var registrationListener: NsdManager.RegistrationListener? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private var beaconBroadcastJob: Job? = null
  private var beaconListenJob: Job? = null
  private var cleanupJob: Job? = null
  private var listenSocket: DatagramSocket? = null

  private val deviceMap = LinkedHashMap<String, DiscoveredDevice>()

  fun startAdvertising(deviceName: String, port: Int, tokenHint: String) {
    stopAdvertising()
    acquireMulticastLock()

    // 1. NSD mDNS Registration
    runCatching {
      val info = NsdServiceInfo().apply {
        serviceName = "FlashShare-$deviceName".take(40)
        serviceType = SERVICE_TYPE
        setPort(port)
      }
      val listener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
      }
      registrationListener = listener
      nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    // 2. UDP LAN Broadcast Beacon (works on hotspots where mDNS may be blocked)
    beaconBroadcastJob = scope.launch {
      DatagramSocket().use { socket ->
        socket.broadcast = true
        while (isActive) {
          val localIp = networkManager.networkState.value.primaryIp
          if (localIp != "0.0.0.0") {
            val payload = JSONObject().apply {
              put("app", "FlashShare")
              put("name", deviceName)
              put("ip", localIp)
              put("port", port)
              put("token", tokenHint)
            }.toString().toByteArray(Charsets.UTF_8)

            val targets = collectBroadcastAddresses()
            for (addr in targets) {
              runCatching {
                val packet = DatagramPacket(payload, payload.size, addr, UDP_BEACON_PORT)
                socket.send(packet)
              }
            }
          }
          delay(2500)
        }
      }
    }
  }

  fun stopAdvertising() {
    beaconBroadcastJob?.cancel()
    beaconBroadcastJob = null
    registrationListener?.let { listener ->
      runCatching { nsdManager.unregisterService(listener) }
    }
    registrationListener = null
    releaseMulticastLockIfIdle()
  }

  fun startScanning() {
    stopScanning()
    acquireMulticastLock()

    // 1. NSD Discovery
    runCatching {
      val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(service: NsdServiceInfo) {
          if (service.serviceType.contains("flashshare")) {
            @Suppress("DEPRECATION")
            runCatching {
              nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                  val host = resolved.host?.hostAddress ?: return
                  val myIps = networkManager.networkState.value.interfaces.map { it.ipAddress }.toSet()
                  if (host in myIps) return
                  val cleanName = resolved.serviceName.removePrefix("FlashShare-")
                  upsertDevice(
                    DiscoveredDevice(
                      id = "$host:${resolved.port}",
                      name = cleanName,
                      ipAddress = host,
                      port = resolved.port,
                      source = "mDNS",
                    ),
                  )
                }
              })
            }
          }
        }

        override fun onServiceLost(service: NsdServiceInfo) {}
        override fun onDiscoveryStopped(serviceType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
      }
      discoveryListener = listener
      nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    // 2. UDP Beacon Listener
    beaconListenJob = scope.launch {
      runCatching {
        val socket = DatagramSocket(UDP_BEACON_PORT).apply {
          broadcast = true
          reuseAddress = true
          soTimeout = 3000
        }
        listenSocket = socket
        val buf = ByteArray(2048)
        while (isActive) {
          val packet = DatagramPacket(buf, buf.size)
          runCatching {
            socket.receive(packet)
            val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
            val json = JSONObject(text)
            if (json.optString("app") == "FlashShare") {
              val ip = json.optString("ip").ifBlank { packet.address.hostAddress ?: "" }
              val myIps = networkManager.networkState.value.interfaces.map { it.ipAddress }.toSet()
              if (ip.isNotBlank() && ip !in myIps) {
                val port = json.optInt("port", 8080)
                val name = json.optString("name", "FlashShare Peer")
                val token = json.optString("token", "")
                upsertDevice(
                  DiscoveredDevice(
                    id = "$ip:$port",
                    name = name,
                    ipAddress = ip,
                    port = port,
                    tokenHint = token,
                    source = "LAN Beacon",
                  ),
                )
              }
            }
          }
        }
      }
    }

    // 3. Stale device pruner
    cleanupJob = scope.launch {
      while (isActive) {
        delay(5000)
        val now = System.currentTimeMillis()
        synchronized(deviceMap) {
          val iter = deviceMap.entries.iterator()
          var changed = false
          while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.value.source != "Manual" && now - entry.value.lastSeen > STALE_TIMEOUT_MS) {
              iter.remove()
              changed = true
            }
          }
          if (changed) _discoveredDevices.value = deviceMap.values.toList()
        }
      }
    }
  }

  fun stopScanning() {
    beaconListenJob?.cancel()
    beaconListenJob = null
    cleanupJob?.cancel()
    cleanupJob = null
    runCatching { listenSocket?.close() }
    listenSocket = null
    discoveryListener?.let { listener ->
      runCatching { nsdManager.stopServiceDiscovery(listener) }
    }
    discoveryListener = null
    releaseMulticastLockIfIdle()
  }

  fun addManualDevice(name: String, ip: String, port: Int, token: String = "") {
    upsertDevice(
      DiscoveredDevice(
        id = "$ip:$port",
        name = name.ifBlank { "Manual Peer ($ip)" },
        ipAddress = ip,
        port = port,
        tokenHint = token,
        source = "Manual",
      ),
    )
  }

  private fun upsertDevice(device: DiscoveredDevice) {
    synchronized(deviceMap) {
      deviceMap[device.id] = device.copy(lastSeen = System.currentTimeMillis())
      _discoveredDevices.value = deviceMap.values.toList()
    }
  }

  private fun collectBroadcastAddresses(): List<InetAddress> {
    val result = mutableSetOf<InetAddress>()
    runCatching {
      val interfaces = NetworkInterface.getNetworkInterfaces()
      while (interfaces != null && interfaces.hasMoreElements()) {
        val nif = interfaces.nextElement()
        if (!nif.isUp || nif.isLoopback) continue
        for (ifAddr in nif.interfaceAddresses) {
          if (ifAddr.address is Inet4Address) {
            ifAddr.broadcast?.let { result.add(it) }
          }
        }
      }
      result.add(InetAddress.getByName("255.255.255.255"))
    }
    return result.toList()
  }

  private fun acquireMulticastLock() {
    if (multicastLock == null) {
      multicastLock = wifiManager.createMulticastLock("FlashShareDiscovery").apply {
        setReferenceCounted(false)
      }
    }
    runCatching {
      if (multicastLock?.isHeld == false) multicastLock?.acquire()
    }
  }

  private fun releaseMulticastLockIfIdle() {
    if (registrationListener == null && discoveryListener == null) {
      runCatching {
        if (multicastLock?.isHeld == true) multicastLock?.release()
      }
    }
  }
}
