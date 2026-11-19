package com.example.flashshare.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.ConnectionTestResult
import com.example.flashshare.model.DiscoveredDevice
import com.example.flashshare.model.FileCategory
import com.example.flashshare.model.ShareableFile
import com.example.flashshare.network.QRCodeManager
import com.example.flashshare.ui.components.QrScannerDialog
import com.example.flashshare.ui.components.StatusBadge
import com.example.flashshare.ui.components.categoryIcon
import com.example.flashshare.ui.components.categoryTint
import com.example.flashshare.util.Formatters
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun SendScreen(
  stagedFiles: List<ShareableFile>,
  selectedIds: Set<String>,
  activeCategory: FileCategory,
  preferredMode: ConnectionMode,
  isLoadingFiles: Boolean,
  discoveredDevices: List<DiscoveredDevice>,
  connectionTest: ConnectionTestResult?,
  isTestingConnection: Boolean,
  primaryIp: String,
  activePort: Int,
  sessionToken: String,
  onSelectCategory: (FileCategory) -> Unit,
  onAddUris: (List<Uri>) -> Unit,
  onAddFolder: (Uri) -> Unit,
  onLoadReceived: () -> Unit,
  onToggleFile: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClearStaged: () -> Unit,
  onLoadThumbnail: suspend (ShareableFile) -> Bitmap?,
  onSendToPeer: (DiscoveredDevice, String) -> Unit,
  onSendManual: (String, Int, String) -> Unit,
  onServeToPc: () -> Unit,
  onTestConnection: (String, String) -> Unit,
  onNavigateToTransfers: () -> Unit,
  onNavigateToReceivePcMode: () -> Unit,
) {
  var showTargetDialog by remember { mutableStateOf(false) }
  var showQrScanner by remember { mutableStateOf(false) }

  val anyFilePicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments(),
  ) { uris ->
    if (uris.isNotEmpty()) onAddUris(uris)
  }

  val mediaPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia(),
  ) { uris ->
    if (uris.isNotEmpty()) onAddUris(uris)
  }

  val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri ->
    treeUri?.let { onAddFolder(it) }
  }

  val filteredFiles = remember(stagedFiles, activeCategory) {
    if (activeCategory == FileCategory.ALL) stagedFiles
    else stagedFiles.filter { it.category == activeCategory }
  }
  val selectedFiles = remember(stagedFiles, selectedIds) {
    stagedFiles.filter { it.id in selectedIds }
  }
  val selectedTotalBytes = remember(selectedFiles) {
    selectedFiles.sumOf { it.sizeBytes }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
    ) {
      // Top header + Pick Actions
      Column(
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column {
            Text(text = "Send Files", style = MaterialTheme.typography.headlineMedium)
            Text(
              text = "All formats supported • Zero compression",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          if (stagedFiles.isNotEmpty()) {
            Row {
              IconButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("send_select_all_button"),
              ) {
                Icon(Icons.Rounded.SelectAll, contentDescription = "Select All")
              }
              IconButton(
                onClick = onClearStaged,
                modifier = Modifier.testTag("send_clear_button"),
              ) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear list")
              }
            }
          }
        }

        // Quick pick buttons row
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          item {
            Button(
              onClick = {
                val mime = when (activeCategory) {
                  FileCategory.IMAGES -> arrayOf("image/*")
                  FileCategory.VIDEOS -> arrayOf("video/*")
                  FileCategory.AUDIO -> arrayOf("audio/*")
                  FileCategory.DOCUMENTS -> arrayOf("application/pdf", "text/*", "application/msword", "application/vnd.openxmlformats-officedocument.*")
                  FileCategory.APK -> arrayOf("application/vnd.android.package-archive", "*/*")
                  FileCategory.ARCHIVES -> arrayOf("application/zip", "application/x-rar-compressed", "application/x-7z-compressed", "*/*")
                  else -> arrayOf("*/*")
                }
                anyFilePicker.launch(mime)
              },
              modifier = Modifier.testTag("pick_any_files_button"),
            ) {
              Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pick Files")
            }
          }
          item {
            OutlinedButton(
              onClick = {
                mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
              },
              modifier = Modifier.testTag("pick_media_button"),
            ) {
              Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Photos & Video")
            }
          }
          item {
            OutlinedButton(
              onClick = { folderPicker.launch(null) },
              modifier = Modifier.testTag("pick_folder_button"),
            ) {
              Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Folder")
            }
          }
          item {
            OutlinedButton(
              onClick = onLoadReceived,
              modifier = Modifier.testTag("load_received_button"),
            ) {
              Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("FlashShare Folder")
            }
          }
        }

        // Category Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(FileCategory.entries) { cat ->
            val count = if (cat == FileCategory.ALL) stagedFiles.size else stagedFiles.count { it.category == cat }
            FilterChip(
              selected = activeCategory == cat,
              onClick = { onSelectCategory(cat) },
              label = {
                Text(if (count > 0) "${cat.label} ($count)" else cat.label)
              },
              modifier = Modifier.testTag("category_chip_${cat.name.lowercase()}"),
            )
          }
        }
      }

      if (isLoadingFiles) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          contentAlignment = Alignment.Center,
        ) {
          CircularProgressIndicator()
        }
      }

      // File List or Empty State
      if (filteredFiles.isEmpty()) {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                imageVector = categoryIcon(activeCategory),
                contentDescription = "Empty file list",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
              )
              Text(
                text = "No ${activeCategory.label} staged",
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = "Tap Pick Files, Photos & Video, or Folder above to select any file type (APK, ZIP, ISO, EXE, MP4, PDF, etc.) with original names preserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Button(onClick = { anyFilePicker.launch(arrayOf("*/*")) }) {
                Text("Browse All Files")
              }
            }
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.weight(1f),
          contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          items(filteredFiles, key = { it.id }) { file ->
            val selected = file.id in selectedIds
            FileSelectableRow(
              file = file,
              selected = selected,
              onLoadThumbnail = onLoadThumbnail,
              onToggle = { onToggleFile(file.id) },
            )
          }
          item { Spacer(modifier = Modifier.height(12.dp)) }
        }
      }

      // Bottom Send Action Bar
      Surface(
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${selectedFiles.size} file(s) selected",
              style = MaterialTheme.typography.titleSmall,
            )
            Text(
              text = Formatters.formatBytes(selectedTotalBytes),
              style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Button(
            onClick = { showTargetDialog = true },
            enabled = selectedFiles.isNotEmpty(),
            modifier = Modifier.testTag("send_selected_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Send (${selectedFiles.size})")
          }
        }
      }
    }
  }

  if (showTargetDialog) {
    SendTargetDialog(
      initialTab = if (preferredMode == ConnectionMode.PHONE_TO_PC) 1 else 0,
      selectedCount = selectedFiles.size,
      totalBytes = selectedTotalBytes,
      discoveredDevices = discoveredDevices,
      connectionTest = connectionTest,
      isTestingConnection = isTestingConnection,
      primaryIp = primaryIp,
      activePort = activePort,
      sessionToken = sessionToken,
      onOpenQrScanner = { showQrScanner = true },
      onTestConnection = onTestConnection,
      onSendToPeer = { dev, token ->
        showTargetDialog = false
        onSendToPeer(dev, token)
        onNavigateToTransfers()
      },
      onSendManual = { ip, port, token ->
        showTargetDialog = false
        onSendManual(ip, port, token)
        onNavigateToTransfers()
      },
      onServeToPc = {
        showTargetDialog = false
        onServeToPc()
        onNavigateToReceivePcMode()
      },
      onDismiss = { showTargetDialog = false },
    )
  }

  if (showQrScanner) {
    QrScannerDialog(
      onQrDetected = { raw ->
        showQrScanner = false
        val parsed = QRCodeManager.parseQrPayload(raw)
        if (parsed != null) {
          showTargetDialog = false
          onSendManual(parsed.ipAddress, parsed.port, parsed.token)
          onNavigateToTransfers()
        }
      },
      onDismiss = { showQrScanner = false },
    )
  }
}

@Composable
private fun FileSelectableRow(
  file: ShareableFile,
  selected: Boolean,
  onLoadThumbnail: suspend (ShareableFile) -> Bitmap?,
  onToggle: () -> Unit,
) {
  var thumb by remember(file.id) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(file.id) {
    if (file.category == FileCategory.IMAGES || file.category == FileCategory.VIDEOS) {
      thumb = onLoadThumbnail(file)
    }
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = if (selected) {
      MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    } else {
      MaterialTheme.colorScheme.surfaceContainer
    },
    border = BorderStroke(
      1.dp,
      if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
      .testTag("file_row_${file.name}"),
  ) {
    Row(
      modifier = Modifier.padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(categoryTint(file.category).copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
      ) {
        if (thumb != null) {
          Image(
            bitmap = thumb!!.asImageBitmap(),
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
        } else {
          Icon(
            imageVector = categoryIcon(file.category),
            contentDescription = file.category.label,
            tint = categoryTint(file.category),
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = file.name,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = "${file.extension} • ${Formatters.formatBytes(file.sizeBytes)}",
            style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (file.isExecutableOrInstaller) {
            Icon(
              imageVector = Icons.Rounded.WarningAmber,
              contentDescription = "Installer or executable",
              tint = StatusWarning,
              modifier = Modifier.size(14.dp),
            )
          }
        }
      }

      Checkbox(
        checked = selected,
        onCheckedChange = { onToggle() },
      )
    }
  }
}

@Composable
private fun SendTargetDialog(
  initialTab: Int,
  selectedCount: Int,
  totalBytes: Long,
  discoveredDevices: List<DiscoveredDevice>,
  connectionTest: ConnectionTestResult?,
  isTestingConnection: Boolean,
  primaryIp: String,
  activePort: Int,
  sessionToken: String,
  onOpenQrScanner: () -> Unit,
  onTestConnection: (String, String) -> Unit,
  onSendToPeer: (DiscoveredDevice, String) -> Unit,
  onSendManual: (String, Int, String) -> Unit,
  onServeToPc: () -> Unit,
  onDismiss: () -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(initialTab) }
  var manualIp by remember { mutableStateOf("") }
  var manualPort by remember { mutableStateOf("8080") }
  var manualToken by remember { mutableStateOf("") }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(modifier = Modifier.padding(18.dp)) {
        Text(
          text = "Choose Destination",
          style = MaterialTheme.typography.titleLarge,
        )
        Text(
          text = "$selectedCount file(s) • ${Formatters.formatBytes(totalBytes)}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(12.dp))

        TabRow(selectedTabIndex = selectedTab) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Phone to Phone") },
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Phone to PC") },
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedTab == 0) {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
              onClick = onOpenQrScanner,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("scan_qr_to_send_button"),
            ) {
              Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Scan Receiver QR Code")
            }

            if (discoveredDevices.isNotEmpty()) {
              Text(
                text = "Nearby Receivers",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
              )
              discoveredDevices.take(3).forEach { dev ->
                Surface(
                  onClick = { onSendToPeer(dev, dev.tokenHint) },
                  shape = RoundedCornerShape(14.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHigh,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(text = dev.name, style = MaterialTheme.typography.titleSmall)
                      Text(
                        text = "${dev.ipAddress}:${dev.port}",
                        style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                      )
                    }
                    StatusBadge(text = "Tap to Send", color = StatusSuccess)
                  }
                }
              }
            }

            HorizontalDivider()

            Text(text = "Manual IP Connection", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = manualIp,
                onValueChange = { manualIp = it },
                label = { Text("Receiver IP") },
                placeholder = { Text("192.168.1.42") },
                singleLine = true,
                modifier = Modifier
                  .weight(2f)
                  .testTag("manual_ip_input"),
              )
              OutlinedTextField(
                value = manualPort,
                onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
                label = { Text("Port") },
                singleLine = true,
                modifier = Modifier
                  .weight(1f)
                  .testTag("manual_port_input"),
              )
            }
            OutlinedTextField(
              value = manualToken,
              onValueChange = { manualToken = it.uppercase().take(8) },
              label = { Text("Session Token (optional if displayed)") },
              singleLine = true,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_token_input"),
            )

            if (connectionTest != null) {
              Text(
                text = connectionTest.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (connectionTest.reachable) StatusSuccess else MaterialTheme.colorScheme.error,
              )
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              OutlinedButton(
                onClick = { onTestConnection(manualIp, manualPort) },
                enabled = manualIp.isNotBlank() && !isTestingConnection,
                modifier = Modifier.testTag("test_peer_button"),
              ) {
                Text(if (isTestingConnection) "Testing..." else "Test IP")
              }
              Button(
                onClick = {
                  onSendManual(
                    manualIp,
                    manualPort.toIntOrNull() ?: 8080,
                    manualToken,
                  )
                },
                enabled = Formatters.isValidIpv4String(manualIp),
                modifier = Modifier.testTag("send_manual_ip_button"),
              ) {
                Text("Send Now")
              }
            }
          }
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Rounded.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Local HTTP Portal", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                  text = "Your selected files will be served over local Wi-Fi/Hotspot without internet or PC software. Open the URL or scan the QR code on the next screen.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                  text = "http://$primaryIp:$activePort/?token=$sessionToken",
                  style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                  color = MaterialTheme.colorScheme.primary,
                )
              }
            }
            Button(
              onClick = onServeToPc,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("serve_to_pc_button"),
            ) {
              Text("Start PC Download Server")
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
          Text("Close")
        }
      }
    }
  }
}
