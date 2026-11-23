package com.example.flashshare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsInputAntenna
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.flashshare.model.AppSettings
import com.example.flashshare.model.ConnectionTestResult
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.NetworkInfoState
import com.example.flashshare.model.ThemePreference
import com.example.flashshare.remote.AppControlConfig
import com.example.flashshare.remote.UpdateCheckResult
import com.example.flashshare.ui.components.MetricPill
import com.example.flashshare.ui.components.StatusBadge
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScreen(
  networkState: NetworkInfoState,
  serverRunning: Boolean,
  activePort: Int,
  connectionTest: ConnectionTestResult?,
  isTesting: Boolean,
  onRefresh: () -> Unit,
  onTestConnection: (String, String) -> Unit,
  onOpenHotspotSettings: () -> Unit,
  onOpenWifiSettings: () -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  var testIp by remember { mutableStateOf("") }
  var testPort by remember { mutableStateOf("8080") }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Network Information") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("network_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          IconButton(onClick = onRefresh, modifier = Modifier.testTag("refresh_network_button")) {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh network info")
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text("Active Interface", style = MaterialTheme.typography.titleMedium)
                StatusBadge(
                  text = if (networkState.isConnected) networkState.connectionType else "Offline",
                  color = if (networkState.isConnected) StatusSuccess else StatusWarning,
                )
              }
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricPill("Primary IP", networkState.primaryIp, Modifier.weight(1.2f))
                MetricPill("Band", networkState.bandLabel, Modifier.weight(1f))
                MetricPill(
                  "Link Speed",
                  networkState.linkSpeedMbps?.let { "$it Mbps" } ?: "--",
                  Modifier.weight(1f),
                )
              }
              if (networkState.interfaces.isNotEmpty()) {
                Text("Detected Local Interfaces:", style = MaterialTheme.typography.labelMedium)
                networkState.interfaces.forEach { iface ->
                  Text(
                    text = "• ${iface.name}: ${iface.ipAddress}" + if (iface.isHotspotCandidate) " (Hotspot)" else "",
                    style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              Text(
                text = if (serverRunning) "Local server bound on port $activePort" else "Local receive server is currently stopped",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Hotspot 2.4GHz / 5GHz System Settings note
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SettingsInputAntenna, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("2.4 GHz & 5 GHz Hotspot Band", style = MaterialTheme.typography.titleSmall)
              }
              Text(
                text = "Android does not allow third-party apps to switch the Wi-Fi radio band programmatically. For fastest transfer speeds, choose 5 GHz AP Band in Android Hotspot settings and FlashShare will automatically use it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenHotspotSettings, modifier = Modifier.weight(1f)) {
                  Text("Hotspot Settings")
                }
                OutlinedButton(onClick = onOpenWifiSettings, modifier = Modifier.weight(1f)) {
                  Text("Wi-Fi Settings")
                }
              }
            }
          }
        }

        // Connection Tester
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Test Peer Connection", style = MaterialTheme.typography.titleSmall)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                  value = testIp,
                  onValueChange = { testIp = it },
                  label = { Text("Target IP") },
                  placeholder = { Text("192.168.1.42") },
                  singleLine = true,
                  modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                  value = testPort,
                  onValueChange = { testPort = it.filter(Char::isDigit).take(5) },
                  label = { Text("Port") },
                  singleLine = true,
                  modifier = Modifier.weight(1f),
                )
              }
              if (connectionTest != null) {
                Text(
                  text = connectionTest.message,
                  style = MaterialTheme.typography.bodySmall,
                  color = if (connectionTest.reachable) StatusSuccess else MaterialTheme.colorScheme.error,
                )
              }
              Button(
                onClick = { onTestConnection(testIp, testPort) },
                enabled = testIp.isNotBlank() && !isTesting,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("run_connection_test_button"),
              ) {
                Text(if (isTesting) "Testing Reachability..." else "Test Connection")
              }
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  settings: AppSettings,
  remoteConfig: AppControlConfig,
  updateCheckState: UpdateCheckResult,
  onUpdateDeviceName: (String) -> Unit,
  onUpdatePort: (String) -> Unit,
  onSetDownloadFolder: (Uri) -> Unit,
  onResetFolder: () -> Unit,
  onUpdateBufferKb: (Int) -> Unit,
  onUpdateTheme: (ThemePreference) -> Unit,
  onUpdateNotifications: (Boolean) -> Unit,
  onUpdateAutoVerify: (Boolean) -> Unit,
  onUpdateDuplicateAction: (DuplicateAction) -> Unit,
  onCheckUpdate: () -> Unit,
  onManualUpdate: () -> Unit,
  onOpenDeveloperDetails: () -> Unit,
  onOpenPrivacyPolicy: () -> Unit,
  onClearAllHistory: () -> Unit,
  showBackButton: Boolean = true,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val context = LocalContext.current
  var nameInput by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
  var portInput by remember(settings.defaultPort) { mutableStateOf(settings.defaultPort.toString()) }

  val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { uri ->
    uri?.let { onSetDownloadFolder(it) }
  }

  val notifPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    onUpdateNotifications(granted)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Settings & About") },
        navigationIcon = {
          if (showBackButton) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
            }
          }
        },
      )
    },
  ) { padding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding),
      contentAlignment = Alignment.TopCenter,
    ) {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 720.dp),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        // Identity & Network Port
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Device & Server", style = MaterialTheme.typography.titleMedium)
              OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                label = { Text("Device Name") },
                singleLine = true,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("settings_device_name_input"),
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                  value = portInput,
                  onValueChange = { portInput = it.filter(Char::isDigit).take(5) },
                  label = { Text("Default Port (1024-65535)") },
                  singleLine = true,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("settings_port_input"),
                )
                Button(
                  onClick = {
                    onUpdateDeviceName(nameInput)
                    onUpdatePort(portInput)
                  },
                  modifier = Modifier.testTag("save_device_settings_button"),
                ) {
                  Text("Save")
                }
              }
            }
          }
        }

        // Storage & Buffer Size
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Storage & Streaming", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Download Folder: ${settings.downloadFolderLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.weight(1f)) {
                  Text("Pick SAF Folder")
                }
                OutlinedButton(onClick = onResetFolder, modifier = Modifier.weight(1f)) {
                  Text("Use Downloads")
                }
              }
              Text("Streaming Buffer Size", style = MaterialTheme.typography.labelLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(64, 128, 256, 512).forEach { kb ->
                  FilterChip(
                    selected = settings.bufferSizeKb == kb,
                    onClick = { onUpdateBufferKb(kb) },
                    label = { Text("${kb}KB") },
                  )
                }
              }
              Text("Duplicate File Handling", style = MaterialTheme.typography.labelLarge)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DuplicateAction.ASK, DuplicateAction.RENAME, DuplicateAction.SKIP).forEach { action ->
                  FilterChip(
                    selected = settings.duplicateAction == action,
                    onClick = { onUpdateDuplicateAction(action) },
                    label = {
                      Text(
                        when (action) {
                          DuplicateAction.ASK -> "Ask"
                          DuplicateAction.RENAME -> "Keep Both"
                          DuplicateAction.SKIP -> "Skip"
                          else -> action.label
                        },
                      )
                    },
                  )
                }
              }
            }
          }
        }

        // Theme, Notifications, SHA-256
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Appearance & Integrity", style = MaterialTheme.typography.titleMedium)
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePreference.entries.forEach { pref ->
                  FilterChip(
                    selected = settings.themePreference == pref,
                    onClick = { onUpdateTheme(pref) },
                    label = { Text(pref.label) },
                  )
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Transfer Notifications", style = MaterialTheme.typography.titleSmall)
                  Text("Show ongoing speed and progress notification", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                  checked = settings.notificationsEnabled,
                  onCheckedChange = { enable ->
                    if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                      notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                      onUpdateNotifications(enable)
                    }
                  },
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Automatic SHA-256 Verification", style = MaterialTheme.typography.titleSmall)
                  Text("Verify file checksum after every transfer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                  checked = settings.autoVerifySha256,
                  onCheckedChange = onUpdateAutoVerify,
                )
              }

              OutlinedButton(
                onClick = onClearAllHistory,
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text("Clear All Transfer History")
              }
            }
          }
        }

        // App Update & Remote Config
        item {
          val isCheckingUpdate = updateCheckState is UpdateCheckResult.Checking
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("App Update", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "Current Version: v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})",
                style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Text(
                text = "Latest Config: v${remoteConfig.latestVersionName} (Code ${remoteConfig.latestVersionCode}) • ${remoteConfig.lastFetchStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                  onClick = onCheckUpdate,
                  enabled = !isCheckingUpdate,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("check_update_button"),
                ) {
                  if (isCheckingUpdate) {
                    CircularProgressIndicator(
                      modifier = Modifier.size(16.dp),
                      strokeWidth = 2.dp,
                      color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking...")
                  } else {
                    Text("Check Update")
                  }
                }
                OutlinedButton(
                  onClick = onManualUpdate,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("manual_update_button"),
                ) {
                  Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Manually Update")
                }
              }
            }
          }
        }

        // Developer Details & Privacy Policy
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Text("Developer & Legal", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "Developed by ${DeveloperInfo.NAME} • ${DeveloperInfo.LOCATION}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                  onClick = onOpenDeveloperDetails,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("open_developer_details_button"),
                ) {
                  Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Developer Details")
                }
                OutlinedButton(
                  onClick = onOpenPrivacyPolicy,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("open_privacy_policy_button"),
                ) {
                  Icon(Icons.Rounded.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Privacy Policy")
                }
              }
            }
          }
        }

        // About FlashShare & Security
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Flash Share - Fast File Share v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "• Package: ${BuildConfig.APPLICATION_ID}\n" +
                  "• 100% Offline local Wi-Fi / Hotspot file sharing — zero cloud file storage.\n" +
                  "• Local private IP enforcement (RFC1918/link-local only) & random session tokens.\n" +
                  "• Buffered direct streaming with SHA-256 verification — preserves original filename, extension, and quality.\n" +
                  "• Received APK/EXE installers are never executed automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}
