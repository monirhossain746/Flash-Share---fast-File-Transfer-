package com.example.flashshare.transfer

import android.content.Context
import com.example.flashshare.data.SettingsRepository
import com.example.flashshare.network.DeviceDiscovery
import com.example.flashshare.network.NetworkManager
import com.example.flashshare.server.LocalServer
import com.example.flashshare.storage.FileManager

class FlashShareContainer private constructor(context: Context) {
  private val appContext = context.applicationContext

  val settingsRepository = SettingsRepository(appContext)
  val fileManager = FileManager(appContext)
  val networkManager = NetworkManager(appContext)
  val transferHistory = TransferHistory(appContext)
  val deviceDiscovery = DeviceDiscovery(appContext, networkManager)
  val transferManager = TransferManager(appContext, fileManager, transferHistory, settingsRepository)
  val localServer = LocalServer(
    context = appContext,
    networkManager = networkManager,
    deviceDiscovery = deviceDiscovery,
    fileManager = fileManager,
    transferManager = transferManager,
    settingsRepository = settingsRepository,
  )

  companion object {
    @Volatile private var instance: FlashShareContainer? = null

    fun get(context: Context): FlashShareContainer {
      return instance ?: synchronized(this) {
        instance ?: FlashShareContainer(context).also { instance = it }
      }
    }
  }
}
