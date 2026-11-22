package com.example.flashshare.ui

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flashshare.model.AppSettings
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.ConnectionTestResult
import com.example.flashshare.model.DiscoveredDevice
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.FileCategory
import com.example.flashshare.model.IncomingTransferRequest
import com.example.flashshare.model.NetworkInfoState
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.ads.AdMobConfig
import com.example.flashshare.model.ThemePreference
import com.example.flashshare.model.TransferItem
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.network.ParsedPairingQr
import com.example.flashshare.network.QRCodeManager
import com.example.flashshare.remote.AppControlConfig
import com.example.flashshare.remote.UpdateCheckResult
import com.example.flashshare.transfer.FlashShareContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FlashShareViewModel(application: Application) : AndroidViewModel(application) {

  private val container = FlashShareContainer.get(application)
  val networkManager = container.networkManager
  private val discovery = container.deviceDiscovery
  private val fileManager = container.fileManager
  private val transferManager = container.transferManager
  private val history = container.transferHistory
  private val localServer = container.localServer
  private val settingsRepo = container.settingsRepository
  private val remoteConfigManager = container.remoteConfigManager

  val settings: StateFlow<AppSettings> = settingsRepo.settings
  val remoteConfig: StateFlow<AppControlConfig> = remoteConfigManager.config
  val updateCheckState: StateFlow<UpdateCheckResult> = remoteConfigManager.updateCheckState
  val useTestAds: StateFlow<Boolean> = AdMobConfig.useTestAds
  val networkState: StateFlow<NetworkInfoState> = networkManager.networkState
  val serverRunning: StateFlow<Boolean> = localServer.isRunning
  val activePort: StateFlow<Int> = localServer.activePort
  val sessionToken: StateFlow<String> = localServer.sessionToken
  val pendingIncomingOffer: StateFlow<IncomingTransferRequest?> = localServer.pendingIncomingOffer
  val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discovery.discoveredDevices
  val liveTransfers: StateFlow<List<TransferItem>> = transferManager.transfers
  val historyItems: StateFlow<List<TransferItem>> = history.history
  val sharedPcFiles: StateFlow<List<ShareableFile>> = localServer.sharedFiles

  val errorBanner: StateFlow<String?> = combine(
    transferManager.lastError,
    localServer.serverError,
  ) { tErr, sErr -> tErr ?: sErr }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _stagedFiles = MutableStateFlow<List<ShareableFile>>(emptyList())
  val stagedFiles: StateFlow<List<ShareableFile>> = _stagedFiles.asStateFlow()

  private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
  val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

  private val _activeCategory = MutableStateFlow(FileCategory.ALL)
  val activeCategory: StateFlow<FileCategory> = _activeCategory.asStateFlow()

  private val _connectionTest = MutableStateFlow<ConnectionTestResult?>(null)
  val connectionTest: StateFlow<ConnectionTestResult?> = _connectionTest.asStateFlow()

  private val _isTestingConnection = MutableStateFlow(false)
  val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

  private val _isLoadingFiles = MutableStateFlow(false)
  val isLoadingFiles: StateFlow<Boolean> = _isLoadingFiles.asStateFlow()

  private val _preferredMode = MutableStateFlow(ConnectionMode.PHONE_TO_PHONE)
  val preferredMode: StateFlow<ConnectionMode> = _preferredMode.asStateFlow()

  private val _toastMessage = MutableStateFlow<String?>(null)
  val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

  init {
    networkManager.startMonitoring()
    discovery.startScanning()
    remoteConfigManager.fetchAndActivate()
  }

  fun refreshRemoteConfig() {
    remoteConfigManager.fetchAndActivate()
  }

  fun checkForUpdatesNow() {
    remoteConfigManager.checkForUpdatesNow()
  }

  fun clearUpdateCheckState() {
    remoteConfigManager.clearUpdateCheckState()
  }

  fun setUseTestAds(enabled: Boolean) {
    AdMobConfig.setUseTestAds(enabled)
  }

  override fun onCleared() {
    super.onCleared()
    discovery.stopScanning()
    networkManager.stopMonitoring()
  }

  fun setPreferredMode(mode: ConnectionMode) {
    _preferredMode.value = mode
  }

  fun setCategory(category: FileCategory) {
    _activeCategory.value = category
  }

  fun dismissError() {
    transferManager.clearError()
    localServer.clearError()
  }

  fun clearToast() {
    _toastMessage.value = null
  }

  fun postMessage(msg: String) {
    _toastMessage.value = msg
  }

  fun refreshNetwork() {
    networkManager.refreshNetworkInfo()
  }

  fun toggleServer() {
    if (localServer.isRunning.value) {
      localServer.stop()
    } else {
      networkManager.refreshNetworkInfo()
      localServer.start()
    }
  }

  fun ensureServerRunning() {
    if (!localServer.isRunning.value) {
      networkManager.refreshNetworkInfo()
      localServer.start()
    }
  }

  fun regenerateToken() {
    localServer.regenerateToken()
    if (localServer.isRunning.value) {
      discovery.startAdvertising(
        settings.value.deviceName,
        localServer.activePort.value,
        localServer.sessionToken.value,
      )
    }
  }

  fun addPickedUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      _isLoadingFiles.value = true
      val loaded = uris.mapNotNull { uri ->
        runCatching {
          getApplication<Application>().contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
          )
        }
        fileManager.inspectUri(uri)
      }
      mergeStagedFiles(loaded)
      _isLoadingFiles.value = false
    }
  }

  fun addFolderUri(treeUri: Uri) {
    viewModelScope.launch {
      _isLoadingFiles.value = true
      runCatching {
        getApplication<Application>().contentResolver.takePersistableUriPermission(
          treeUri,
          Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
      }
      val folderFiles = fileManager.collectFolderFiles(treeUri)
      mergeStagedFiles(folderFiles)
      _isLoadingFiles.value = false
      if (folderFiles.isEmpty()) {
        _toastMessage.value = "Selected folder contains no files."
      } else {
        _toastMessage.value = "Added ${folderFiles.size} files from folder."
      }
    }
  }

  fun loadReceivedFilesIntoStaging() {
    viewModelScope.launch {
      _isLoadingFiles.value = true
      val existing = fileManager.listReceivedFiles(settings.value.downloadTreeUri)
      mergeStagedFiles(existing, autoSelect = false)
      _isLoadingFiles.value = false
    }
  }

  private fun mergeStagedFiles(newFiles: List<ShareableFile>, autoSelect: Boolean = true) {
    if (newFiles.isEmpty()) return
    val current = _stagedFiles.value.toMutableList()
    val existingUris = current.map { it.uri.toString() }.toMutableSet()
    val addedIds = mutableListOf<String>()
    newFiles.forEach { file ->
      if (existingUris.add(file.uri.toString())) {
        current.add(0, file)
        addedIds += file.id
      } else if (autoSelect) {
        current.firstOrNull { it.uri == file.uri }?.let { addedIds += it.id }
      }
    }
    _stagedFiles.value = current
    if (autoSelect && addedIds.isNotEmpty()) {
      _selectedIds.value = _selectedIds.value + addedIds
    }
  }

  fun toggleFileSelection(id: String) {
    val current = _selectedIds.value
    _selectedIds.value = if (id in current) current - id else current + id
  }

  fun selectAllInCurrentCategory() {
    val visible = filteredStagedFiles()
    val visibleIds = visible.map { it.id }.toSet()
    val current = _selectedIds.value
    _selectedIds.value = if (visible.isNotEmpty() && visibleIds.all { it in current }) {
      current - visibleIds
    } else {
      current + visibleIds
    }
  }

  fun clearSelection() {
    _selectedIds.value = emptySet()
  }

  fun clearStagedFiles() {
    _stagedFiles.value = emptyList()
    _selectedIds.value = emptySet()
  }

  fun selectedFiles(): List<ShareableFile> {
    val ids = _selectedIds.value
    return _stagedFiles.value.filter { it.id in ids }
  }

  private fun filteredStagedFiles(): List<ShareableFile> {
    val cat = _activeCategory.value
    return if (cat == FileCategory.ALL) _stagedFiles.value
    else _stagedFiles.value.filter { it.category == cat }
  }

  suspend fun loadThumbnail(file: ShareableFile): Bitmap? = fileManager.loadThumbnail(file)

  fun testPeerConnection(ip: String, portStr: String) {
    val port = portStr.trim().toIntOrNull() ?: 8080
    viewModelScope.launch {
      _isTestingConnection.value = true
      _connectionTest.value = networkManager.testConnection(ip, port)
      _isTestingConnection.value = false
    }
  }

  fun clearConnectionTest() {
    _connectionTest.value = null
  }

  fun addManualPeer(name: String, ip: String, port: Int, token: String = "") {
    discovery.addManualDevice(name, ip.trim(), port, token.trim())
  }

  fun parseQr(raw: String): ParsedPairingQr? = QRCodeManager.parseQrPayload(raw)

  fun sendSelectedToPeer(device: DiscoveredDevice, tokenOverride: String = "") {
    val files = selectedFiles()
    if (files.isEmpty()) {
      _toastMessage.value = "Select at least one file to send."
      return
    }
    val token = tokenOverride.ifBlank { device.tokenHint }
    transferManager.sendFilesToPeer(
      files = files,
      peerIp = device.ipAddress,
      peerPort = device.port,
      peerToken = token,
      peerName = device.name,
    )
    _toastMessage.value = "Sending ${files.size} file(s) to ${device.name}..."
  }

  fun sendSelectedToManualIp(ip: String, port: Int, token: String) {
    val files = selectedFiles()
    if (files.isEmpty()) {
      _toastMessage.value = "Select at least one file to send."
      return
    }
    transferManager.sendFilesToPeer(
      files = files,
      peerIp = ip.trim(),
      peerPort = port,
      peerToken = token.trim(),
      peerName = "Peer ($ip)",
    )
    _toastMessage.value = "Requesting transfer to $ip:$port..."
  }

  fun serveSelectedFilesToPc() {
    val files = selectedFiles()
    if (files.isEmpty()) {
      _toastMessage.value = "Select files to share with PC first."
      return
    }
    localServer.updateSharedFiles(files)
    ensureServerRunning()
    _toastMessage.value = "Ready! Open the URL in your PC browser to download ${files.size} file(s)."
  }

  fun respondToOffer(sessionId: String, accept: Boolean, duplicateAction: DuplicateAction) {
    localServer.respondToIncomingOffer(sessionId, accept, duplicateAction)
  }

  fun pauseTransfer(id: String) = transferManager.pauseTransfer(id)
  fun resumeTransfer(id: String) = transferManager.resumeTransfer(id)
  fun cancelTransfer(id: String) = transferManager.cancelTransfer(id)
  fun retryTransfer(id: String) = transferManager.retryTransfer(id)

  fun deleteHistoryItem(id: String) {
    viewModelScope.launch { history.remove(id) }
  }

  fun clearHistory(statuses: Set<TransferStatus>? = null) {
    viewModelScope.launch {
      if (statuses == null) history.clearAll()
      else history.clearByStatuses(statuses)
    }
  }

  fun updateDeviceName(name: String) {
    viewModelScope.launch { settingsRepo.updateDeviceName(name) }
  }

  fun updateDefaultPort(portStr: String) {
    val port = portStr.toIntOrNull() ?: return
    if (port in 1024..65535) {
      viewModelScope.launch { settingsRepo.updateDefaultPort(port) }
    }
  }

  fun setDownloadFolder(treeUri: Uri) {
    val ctx = getApplication<Application>()
    runCatching {
      ctx.contentResolver.takePersistableUriPermission(
        treeUri,
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
      )
    }
    val doc = DocumentFile.fromTreeUri(ctx, treeUri)
    val label = doc?.name?.let { "Custom Folder ($it)" } ?: "Selected SAF Folder"
    viewModelScope.launch { settingsRepo.updateDownloadFolder(treeUri.toString(), label) }
  }

  fun resetDownloadFolderToDefault() {
    viewModelScope.launch { settingsRepo.updateDownloadFolder(null, "Downloads/FlashShare") }
  }

  fun updateBufferSizeKb(kb: Int) {
    viewModelScope.launch { settingsRepo.updateBufferSizeKb(kb) }
  }

  fun updateTheme(pref: ThemePreference) {
    viewModelScope.launch { settingsRepo.updateTheme(pref) }
  }

  fun updateNotifications(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.updateNotifications(enabled) }
  }

  fun updateAutoVerify(enabled: Boolean) {
    viewModelScope.launch { settingsRepo.updateAutoVerify(enabled) }
  }

  fun updateDuplicateAction(action: DuplicateAction) {
    viewModelScope.launch { settingsRepo.updateDuplicateAction(action) }
  }

  fun availableStorageBytes(): Long = fileManager.getAvailableStorageBytes()
}
