package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.ThemePreference
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.ui.FlashShareViewModel
import com.example.flashshare.ui.screens.HomeScreen
import com.example.flashshare.ui.screens.NetworkScreen
import com.example.flashshare.ui.screens.ReceiveScreen
import com.example.flashshare.ui.screens.SendScreen
import com.example.flashshare.ui.screens.SettingsScreen
import com.example.flashshare.ui.screens.TransfersScreen
import com.example.ui.theme.FlashShareTheme

object FlashRoutes {
  const val HOME = "home"
  const val SEND = "send"
  const val RECEIVE = "receive"
  const val TRANSFERS = "transfers"
  const val NETWORK = "network"
  const val SETTINGS = "settings"
}

private data class NavTab(
  val route: String,
  val label: String,
  val icon: ImageVector,
  val testTag: String,
)

class MainActivity : ComponentActivity() {

  private val viewModel: FlashShareViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    handleShareIntent(intent)

    setContent {
      val settings by viewModel.settings.collectAsStateWithLifecycle()
      val isDark = when (settings.themePreference) {
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
      }

      FlashShareTheme(darkTheme = isDark) {
        FlashShareRoot(viewModel = viewModel)
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    handleShareIntent(intent)
  }

  private fun handleShareIntent(intent: Intent?) {
    if (intent == null) return
    val uris = mutableListOf<Uri>()
    when (intent.action) {
      Intent.ACTION_SEND -> {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        uri?.let { uris += it }
      }
      Intent.ACTION_SEND_MULTIPLE -> {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
          @Suppress("DEPRECATION")
          intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
        }
        list?.let { uris.addAll(it) }
      }
    }
    if (uris.isNotEmpty()) {
      viewModel.addPickedUris(uris)
    }
  }
}

@Composable
private fun FlashShareRoot(viewModel: FlashShareViewModel) {
  val navController = rememberNavController()
  val navBackStack by navController.currentBackStackEntryAsState()
  val currentRoute = navBackStack?.destination?.route ?: FlashRoutes.HOME

  val settings by viewModel.settings.collectAsStateWithLifecycle()
  val networkState by viewModel.networkState.collectAsStateWithLifecycle()
  val serverRunning by viewModel.serverRunning.collectAsStateWithLifecycle()
  val activePort by viewModel.activePort.collectAsStateWithLifecycle()
  val sessionToken by viewModel.sessionToken.collectAsStateWithLifecycle()
  val pendingOffer by viewModel.pendingIncomingOffer.collectAsStateWithLifecycle()
  val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
  val liveTransfers by viewModel.liveTransfers.collectAsStateWithLifecycle()
  val historyItems by viewModel.historyItems.collectAsStateWithLifecycle()
  val sharedPcFiles by viewModel.sharedPcFiles.collectAsStateWithLifecycle()
  val stagedFiles by viewModel.stagedFiles.collectAsStateWithLifecycle()
  val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
  val activeCategory by viewModel.activeCategory.collectAsStateWithLifecycle()
  val preferredMode by viewModel.preferredMode.collectAsStateWithLifecycle()
  val isLoadingFiles by viewModel.isLoadingFiles.collectAsStateWithLifecycle()
  val connectionTest by viewModel.connectionTest.collectAsStateWithLifecycle()
  val isTestingConnection by viewModel.isTestingConnection.collectAsStateWithLifecycle()
  val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
  val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(toastMessage) {
    val msg = toastMessage ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(msg)
    viewModel.clearToast()
  }

  LaunchedEffect(errorBanner) {
    val err = errorBanner ?: return@LaunchedEffect
    snackbarHostState.showSnackbar(err)
    viewModel.dismissError()
  }

  // Surface incoming Phone-to-Phone offer automatically by navigating to Receive
  LaunchedEffect(pendingOffer?.sessionId) {
    if (pendingOffer != null && currentRoute != FlashRoutes.RECEIVE) {
      navController.navigate(FlashRoutes.RECEIVE) { launchSingleTop = true }
    }
  }

  val activeTransferCount = remember(liveTransfers) {
    liveTransfers.count {
      it.status == TransferStatus.IN_PROGRESS || it.status == TransferStatus.PENDING
    }
  }

  val tabs = remember {
    listOf(
      NavTab(FlashRoutes.HOME, "Home", Icons.Rounded.Home, "nav_home"),
      NavTab(FlashRoutes.SEND, "Send", Icons.Rounded.Upload, "nav_send"),
      NavTab(FlashRoutes.RECEIVE, "Receive", Icons.Rounded.Download, "nav_receive"),
      NavTab(FlashRoutes.TRANSFERS, "Transfers", Icons.Rounded.SwapVert, "nav_transfers"),
    )
  }

  val showPrimaryChrome = currentRoute in setOf(
    FlashRoutes.HOME,
    FlashRoutes.SEND,
    FlashRoutes.RECEIVE,
    FlashRoutes.TRANSFERS,
  )

  fun navigateTab(route: String) {
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
      }
      launchSingleTop = true
      restoreState = true
    }
  }

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 600.dp

    Scaffold(
      contentWindowInsets = WindowInsets.safeDrawing,
      snackbarHost = { SnackbarHost(snackbarHostState) },
      bottomBar = {
        if (showPrimaryChrome && !useRail) {
          NavigationBar {
            tabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationBarItem(
                selected = selected,
                onClick = { navigateTab(tab.route) },
                icon = {
                  if (tab.route == FlashRoutes.TRANSFERS && activeTransferCount > 0) {
                    BadgedBox(badge = { Badge { Text("$activeTransferCount") } }) {
                      Icon(tab.icon, contentDescription = tab.label)
                    }
                  } else {
                    Icon(tab.icon, contentDescription = tab.label)
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }
      },
    ) { innerPadding ->
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
      ) {
        if (showPrimaryChrome && useRail) {
          NavigationRail(modifier = Modifier.fillMaxHeight()) {
            tabs.forEach { tab ->
              val selected = currentRoute == tab.route
              NavigationRailItem(
                selected = selected,
                onClick = { navigateTab(tab.route) },
                icon = {
                  if (tab.route == FlashRoutes.TRANSFERS && activeTransferCount > 0) {
                    BadgedBox(badge = { Badge { Text("$activeTransferCount") } }) {
                      Icon(tab.icon, contentDescription = tab.label)
                    }
                  } else {
                    Icon(tab.icon, contentDescription = tab.label)
                  }
                },
                label = { Text(tab.label) },
                modifier = Modifier.testTag(tab.testTag),
              )
            }
          }
        }

        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
          NavHost(
            navController = navController,
            startDestination = FlashRoutes.HOME,
          ) {
            composable(FlashRoutes.HOME) {
              val mergedRecent = remember(liveTransfers, historyItems) {
                (liveTransfers + historyItems).distinctBy { it.id }.sortedByDescending { it.timestamp }
              }
              HomeScreen(
                deviceName = settings.deviceName,
                networkState = networkState,
                serverRunning = serverRunning,
                activePort = activePort,
                discoveredDevices = discoveredDevices,
                recentTransfers = mergedRecent,
                onSendClick = { mode ->
                  viewModel.setPreferredMode(mode)
                  navigateTab(FlashRoutes.SEND)
                },
                onReceiveClick = { mode ->
                  viewModel.setPreferredMode(mode)
                  viewModel.ensureServerRunning()
                  navigateTab(FlashRoutes.RECEIVE)
                },
                onOpenTransfers = { navigateTab(FlashRoutes.TRANSFERS) },
                onOpenNetwork = { navController.navigate(FlashRoutes.NETWORK) },
                onOpenSettings = { navController.navigate(FlashRoutes.SETTINGS) },
              )
            }

            composable(FlashRoutes.SEND) {
              SendScreen(
                stagedFiles = stagedFiles,
                selectedIds = selectedIds,
                activeCategory = activeCategory,
                preferredMode = preferredMode,
                isLoadingFiles = isLoadingFiles,
                discoveredDevices = discoveredDevices,
                connectionTest = connectionTest,
                isTestingConnection = isTestingConnection,
                primaryIp = networkState.primaryIp,
                activePort = activePort,
                sessionToken = sessionToken,
                onSelectCategory = viewModel::setCategory,
                onAddUris = viewModel::addPickedUris,
                onAddFolder = viewModel::addFolderUri,
                onLoadReceived = viewModel::loadReceivedFilesIntoStaging,
                onToggleFile = viewModel::toggleFileSelection,
                onSelectAll = viewModel::selectAllInCurrentCategory,
                onClearStaged = viewModel::clearStagedFiles,
                onLoadThumbnail = viewModel::loadThumbnail,
                onSendToPeer = viewModel::sendSelectedToPeer,
                onSendManual = viewModel::sendSelectedToManualIp,
                onServeToPc = viewModel::serveSelectedFilesToPc,
                onTestConnection = viewModel::testPeerConnection,
                onNavigateToTransfers = { navigateTab(FlashRoutes.TRANSFERS) },
                onNavigateToReceivePcMode = {
                  viewModel.setPreferredMode(ConnectionMode.PHONE_TO_PC)
                  navigateTab(FlashRoutes.RECEIVE)
                },
              )
            }

            composable(FlashRoutes.RECEIVE) {
              ReceiveScreen(
                settings = settings,
                networkState = networkState,
                serverRunning = serverRunning,
                activePort = activePort,
                sessionToken = sessionToken,
                sharedToPcCount = sharedPcFiles.size,
                pendingOffer = pendingOffer,
                preferredMode = preferredMode,
                availableBytes = viewModel.availableStorageBytes(),
                onToggleServer = viewModel::toggleServer,
                onRegenerateToken = viewModel::regenerateToken,
                onSetDownloadFolder = viewModel::setDownloadFolder,
                onRespondOffer = viewModel::respondToOffer,
                onShowMessage = viewModel::postMessage,
              )
            }

            composable(FlashRoutes.TRANSFERS) {
              TransfersScreen(
                liveTransfers = liveTransfers,
                historyItems = historyItems,
                onPause = viewModel::pauseTransfer,
                onResume = viewModel::resumeTransfer,
                onCancel = viewModel::cancelTransfer,
                onRetry = viewModel::retryTransfer,
                onDeleteHistory = viewModel::deleteHistoryItem,
                onClearHistoryGroup = { statuses -> viewModel.clearHistory(statuses) },
              )
            }

            composable(FlashRoutes.NETWORK) {
              NetworkScreen(
                networkState = networkState,
                serverRunning = serverRunning,
                activePort = activePort,
                connectionTest = connectionTest,
                isTesting = isTestingConnection,
                onRefresh = viewModel::refreshNetwork,
                onTestConnection = viewModel::testPeerConnection,
                onOpenHotspotSettings = { viewModel.networkManager.openHotspotSettings() },
                onOpenWifiSettings = { viewModel.networkManager.openWifiSettings() },
                onBack = { navController.popBackStack() },
              )
            }

            composable(FlashRoutes.SETTINGS) {
              SettingsScreen(
                settings = settings,
                onUpdateDeviceName = {
                  viewModel.updateDeviceName(it)
                  viewModel.postMessage("Device settings updated")
                },
                onUpdatePort = viewModel::updateDefaultPort,
                onSetDownloadFolder = viewModel::setDownloadFolder,
                onResetFolder = viewModel::resetDownloadFolderToDefault,
                onUpdateBufferKb = viewModel::updateBufferSizeKb,
                onUpdateTheme = viewModel::updateTheme,
                onUpdateNotifications = viewModel::updateNotifications,
                onUpdateAutoVerify = viewModel::updateAutoVerify,
                onUpdateDuplicateAction = viewModel::updateDuplicateAction,
                onClearAllHistory = {
                  viewModel.clearHistory(null)
                  viewModel.postMessage("Transfer history cleared")
                },
                onBack = { navController.popBackStack() },
              )
            }
          }
        }
      }
    }
  }
}
