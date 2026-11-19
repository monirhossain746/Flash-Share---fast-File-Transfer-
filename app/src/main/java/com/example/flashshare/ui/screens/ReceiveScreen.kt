package com.example.flashshare.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.flashshare.model.AppSettings
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.IncomingTransferRequest
import com.example.flashshare.model.NetworkInfoState
import com.example.flashshare.network.QRCodeManager
import com.example.flashshare.ui.components.InfoBanner
import com.example.flashshare.ui.components.MetricPill
import com.example.flashshare.ui.components.StatusBadge
import com.example.flashshare.util.Formatters
import com.example.ui.theme.FlashCyan
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun ReceiveScreen(
  settings: AppSettings,
  networkState: NetworkInfoState,
  serverRunning: Boolean,
  activePort: Int,
  sessionToken: String,
  sharedToPcCount: Int,
  pendingOffer: IncomingTransferRequest?,
  preferredMode: ConnectionMode,
  availableBytes: Long,
  onToggleServer: () -> Unit,
  onRegenerateToken: () -> Unit,
  onSetDownloadFolder: (Uri) -> Unit,
  onRespondOffer: (String, Boolean, DuplicateAction) -> Unit,
  onShowMessage: (String) -> Unit,
) {
  val context = LocalContext.current
  var qrModePc by remember(preferredMode) {
    mutableStateOf(preferredMode == ConnectionMode.PHONE_TO_PC || preferredMode == ConnectionMode.PC_TO_PHONE)
  }

  val folderPicker = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree(),
  ) { treeUri ->
    treeUri?.let { onSetDownloadFolder(it) }
  }

  val effectivePort = if (serverRunning) activePort else settings.defaultPort
  val webUrl = QRCodeManager.buildWebUrl(networkState.primaryIp, effectivePort, sessionToken)
  val pairUri = QRCodeManager.buildPairUri(networkState.primaryIp, effectivePort, sessionToken, settings.deviceName)
  val qrPayload = if (qrModePc) webUrl else pairUri
  val qrBitmap = remember(qrPayload, serverRunning) {
    QRCodeManager.generateQrBitmap(qrPayload, 520)
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      // Header + Server Toggle
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Receive & Web Portal", style = MaterialTheme.typography.headlineMedium)
            Text(
              text = if (qrModePc) "Windows PC Browser Mode" else "Phone-to-Phone Receiver Mode",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge(
              text = if (serverRunning) "Live" else "Stopped",
              color = if (serverRunning) StatusSuccess else Color(0xFF94A3B8),
            )
            Switch(
              checked = serverRunning,
              onCheckedChange = { onToggleServer() },
              modifier = Modifier.testTag("server_toggle_switch"),
            )
          }
        }
      }

      // Incoming Phone-to-Phone Offer Card
      if (pendingOffer != null) {
        item {
          IncomingOfferCard(
            offer = pendingOffer,
            onAccept = { dupAction -> onRespondOffer(pendingOffer.sessionId, true, dupAction) },
            onReject = { onRespondOffer(pendingOffer.sessionId, false, DuplicateAction.SKIP) },
          )
        }
      }

      if (!networkState.isConnected) {
        item {
          InfoBanner(
            icon = Icons.Rounded.WarningAmber,
            title = "No Local Wi-Fi or Hotspot Detected",
            subtitle = "Connect to a Wi-Fi router or enable Android Mobile Hotspot so other devices can reach this phone.",
            accent = StatusWarning,
          )
        }
      }

      // Endpoint & QR Card
      item {
        Card(
          shape = RoundedCornerShape(22.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilterChip(
                selected = !qrModePc,
                onClick = { qrModePc = false },
                label = { Text("Phone-to-Phone QR") },
                modifier = Modifier.weight(1f),
              )
              FilterChip(
                selected = qrModePc,
                onClick = { qrModePc = true },
                label = { Text("PC Browser URL") },
                modifier = Modifier.weight(1f),
              )
            }

            if (!serverRunning) {
              Button(
                onClick = onToggleServer,
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("start_server_button"),
              ) {
                Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Local Receive Server")
              }
            }

            if (qrBitmap != null) {
              Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 2.dp,
              ) {
                Image(
                  bitmap = qrBitmap,
                  contentDescription = "FlashShare Connection QR Code",
                  modifier = Modifier
                    .padding(12.dp)
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp)),
                )
              }
            }

            // Connection Metrics Grid
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              MetricPill(label = "Local IP", value = networkState.primaryIp, modifier = Modifier.weight(1.2f))
              MetricPill(label = "Port", value = ":$effectivePort", modifier = Modifier.weight(0.8f))
              MetricPill(label = "Token", value = sessionToken, modifier = Modifier.weight(0.9f))
            }

            // Browser / Pair Address Copy Strip
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (qrModePc) "Open in Windows / Mac / Linux Browser:" else "Device Name: ${settings.deviceName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                  Text(
                    text = webUrl,
                    style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = MaterialTheme.colorScheme.primary,
                  )
                }
                IconButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("FlashShare URL", webUrl))
                    onShowMessage("Copied $webUrl")
                  },
                  modifier = Modifier.testTag("copy_server_url_button"),
                ) {
                  Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy URL")
                }
                IconButton(
                  onClick = onRegenerateToken,
                  modifier = Modifier.testTag("regenerate_token_button"),
                ) {
                  Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate Session Token")
                }
              }
            }

            if (sharedToPcCount > 0) {
              InfoBanner(
                icon = Icons.Rounded.Check,
                title = "$sharedToPcCount file(s) ready for PC Download",
                subtitle = "Open the URL above in Chrome, Edge, or Firefox on your PC to download or upload files.",
                accent = StatusSuccess,
              )
            }
          }
        }
      }

      // Destination Folder Selector (SAF)
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.Folder,
              contentDescription = "Destination Folder",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
              Text(text = "Destination Folder", style = MaterialTheme.typography.titleSmall)
              Text(
                text = "${settings.downloadFolderLabel} • ${Formatters.formatBytes(availableBytes)} free",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            OutlinedButton(
              onClick = { folderPicker.launch(null) },
              modifier = Modifier.testTag("choose_receive_folder_button"),
            ) {
              Text("Change")
            }
          }
        }
      }

      // Connection Instructions + Security Note
      item {
        Card(
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "How to Connect", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "1. Connect both devices to the same Wi-Fi router or enable Mobile Hotspot.\n" +
                "2. Turn on the Local Receive Server above.\n" +
                "3. For Phone-to-Phone: scan the QR code from the sender's FlashShare app.\n" +
                "4. For PC ↔ Phone: open the http:// address in Chrome, Edge, or Firefox — no PC software needed.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = FlashCyan,
                modifier = Modifier.size(16.dp),
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Local LAN only • Session token protected • Existing files never overwritten without confirmation",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun IncomingOfferCard(
  offer: IncomingTransferRequest,
  onAccept: (DuplicateAction) -> Unit,
  onReject: () -> Unit,
) {
  var dupChoice by remember(offer.sessionId) { mutableStateOf(DuplicateAction.RENAME) }

  Card(
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("incoming_offer_card"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Text(
        text = "Incoming Transfer Request",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      Text(
        text = "${offer.senderName} (${offer.senderIp}) wants to send ${offer.files.size} file(s) • ${Formatters.formatBytes(offer.totalBytes)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
      )
      offer.files.take(3).forEach { f ->
        Text(
          text = "• ${f.name} (${Formatters.formatBytes(f.size)})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }
      if (offer.files.size > 3) {
        Text(
          text = "+ ${offer.files.size - 3} more file(s)",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
      }

      if (offer.duplicateNames.isNotEmpty()) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = StatusWarning.copy(alpha = 0.18f),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Duplicate filename(s) detected: ${offer.duplicateNames.take(2).joinToString()}",
              style = MaterialTheme.typography.labelMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = dupChoice == DuplicateAction.RENAME,
                onClick = { dupChoice = DuplicateAction.RENAME },
                label = { Text("Keep Both") },
              )
              FilterChip(
                selected = dupChoice == DuplicateAction.OVERWRITE,
                onClick = { dupChoice = DuplicateAction.OVERWRITE },
                label = { Text("Overwrite") },
              )
              FilterChip(
                selected = dupChoice == DuplicateAction.SKIP,
                onClick = { dupChoice = DuplicateAction.SKIP },
                label = { Text("Skip") },
              )
            }
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        OutlinedButton(
          onClick = onReject,
          modifier = Modifier
            .weight(1f)
            .testTag("reject_offer_button"),
        ) {
          Icon(Icons.Rounded.Close, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Reject")
        }
        Button(
          onClick = { onAccept(dupChoice) },
          colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
          modifier = Modifier
            .weight(1f)
            .testTag("accept_offer_button"),
        ) {
          Icon(Icons.Rounded.Check, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Accept")
        }
      }
    }
  }
}
