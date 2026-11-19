package com.example.flashshare.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flashshare.model.ConnectionMode
import com.example.flashshare.model.DiscoveredDevice
import com.example.flashshare.model.NetworkInfoState
import com.example.flashshare.model.TransferDirection
import com.example.flashshare.model.TransferItem
import com.example.flashshare.ui.components.FlashGradient
import com.example.flashshare.ui.components.SectionHeader
import com.example.flashshare.ui.components.StatusBadge
import com.example.flashshare.ui.components.TransferStatusBadge
import com.example.flashshare.util.Formatters
import com.example.ui.theme.FlashBlue
import com.example.ui.theme.FlashCyan
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun HomeScreen(
  deviceName: String,
  networkState: NetworkInfoState,
  serverRunning: Boolean,
  activePort: Int,
  discoveredDevices: List<DiscoveredDevice>,
  recentTransfers: List<TransferItem>,
  onSendClick: (ConnectionMode) -> Unit,
  onReceiveClick: (ConnectionMode) -> Unit,
  onOpenTransfers: () -> Unit,
  onOpenNetwork: () -> Unit,
  onOpenSettings: () -> Unit,
) {
  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter,
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp),
      contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      // Hero Branding Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
              modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(FlashGradient),
              contentAlignment = Alignment.Center,
            ) {
              Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = "FlashShare Logo",
                tint = Color(0xFF031526),
                modifier = Modifier.size(28.dp),
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "FLASHSHARE",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
              )
              Text(
                text = "$deviceName • Offline High-Speed Transfer",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
          Row {
            IconButton(
              onClick = onOpenNetwork,
              modifier = Modifier.testTag("home_network_button"),
            ) {
              Icon(Icons.Rounded.NetworkCheck, contentDescription = "Network Information")
            }
            IconButton(
              onClick = onOpenSettings,
              modifier = Modifier.testTag("home_settings_button"),
            ) {
              Icon(Icons.Rounded.Settings, contentDescription = "Settings")
            }
          }
        }
      }

      // Live Network Banner
      item {
        Card(
          onClick = onOpenNetwork,
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("network_status_card"),
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              Icon(
                imageVector = if (networkState.isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                contentDescription = "Wi-Fi Status",
                tint = if (networkState.isConnected) StatusSuccess else StatusWarning,
                modifier = Modifier.size(24.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = networkState.connectionType,
                  style = MaterialTheme.typography.titleSmall,
                )
                Text(
                  text = if (networkState.isConnected) {
                    val serverPart = if (serverRunning) " • Server :$activePort" else ""
                    "${networkState.primaryIp}$serverPart"
                  } else {
                    "Enable Wi-Fi or Hotspot to transfer"
                  },
                  style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
            StatusBadge(
              text = if (serverRunning) "Server Live" else if (networkState.isConnected) "Ready" else "Offline",
              color = if (serverRunning) FlashCyan else if (networkState.isConnected) StatusSuccess else StatusWarning,
            )
          }
        }
      }

      // Primary Send & Receive Buttons
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Surface(
            onClick = { onSendClick(ConnectionMode.PHONE_TO_PHONE) },
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            modifier = Modifier
              .weight(1f)
              .height(132.dp)
              .testTag("home_send_button"),
          ) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(FlashGradient)
                .padding(16.dp),
            ) {
              Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
              ) {
                Box(
                  modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(
                    imageVector = Icons.Rounded.Upload,
                    contentDescription = "Send Files",
                    tint = Color(0xFF031526),
                  )
                }
                Column {
                  Text(
                    text = "Send Files",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF031526),
                  )
                  Text(
                    text = "Photos, APK, ZIP, Any",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF031526).copy(alpha = 0.78f),
                  )
                }
              }
            }
          }

          Surface(
            onClick = { onReceiveClick(ConnectionMode.PHONE_TO_PHONE) },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, FlashCyan.copy(alpha = 0.45f)),
            modifier = Modifier
              .weight(1f)
              .height(132.dp)
              .testTag("home_receive_button"),
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
              verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Box(
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(FlashCyan.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Download,
                  contentDescription = "Receive Files",
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
              Column {
                Text(
                  text = "Receive Files",
                  style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                  color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                  text = if (serverRunning) "Listening :$activePort" else "QR, IP & Browser",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }

      // Three Connection Modes
      item {
        SectionHeader(title = "Connection Modes")
      }

      item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          ConnectionModeCard(
            icon = Icons.Rounded.PhoneAndroid,
            title = "Phone to Phone",
            subtitle = "High-speed Android ↔ Android via Wi-Fi, Hotspot, QR code, or LAN discovery",
            accent = FlashBlue,
            testTag = "mode_phone_to_phone",
            onClick = { onSendClick(ConnectionMode.PHONE_TO_PHONE) },
          )
          ConnectionModeCard(
            icon = Icons.Rounded.Computer,
            title = "Phone to PC",
            subtitle = "Start local HTTP portal & download files directly in Chrome, Edge, or Firefox",
            accent = FlashCyan,
            testTag = "mode_phone_to_pc",
            onClick = { onSendClick(ConnectionMode.PHONE_TO_PC) },
          )
          ConnectionModeCard(
            icon = Icons.Rounded.Upload,
            title = "PC to Phone",
            subtitle = "Drag & drop files from any Windows PC browser directly into Android storage",
            accent = StatusSuccess,
            testTag = "mode_pc_to_phone",
            onClick = { onReceiveClick(ConnectionMode.PC_TO_PHONE) },
          )
        }
      }

      // Connected / Nearby Devices
      item {
        SectionHeader(
          title = "Nearby FlashShare Devices (${discoveredDevices.size})",
          actionText = "Network Info",
          onAction = onOpenNetwork,
        )
      }

      if (discoveredDevices.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.Devices,
                contentDescription = "No nearby devices",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Scanning local Wi-Fi/Hotspot for devices running FlashShare Receiver...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(discoveredDevices.take(4), key = { it.id }) { dev ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onSendClick(ConnectionMode.PHONE_TO_PHONE) },
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = dev.name, style = MaterialTheme.typography.titleSmall)
                Text(
                  text = "${dev.ipAddress}:${dev.port} • ${dev.source}",
                  style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              StatusBadge(text = "Online", color = StatusSuccess)
            }
          }
        }
      }

      // Recent Transfers
      item {
        SectionHeader(
          title = "Recent Transfers",
          actionText = "View All",
          onAction = onOpenTransfers,
        )
      }

      if (recentTransfers.isEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "No transfers yet. Tap Send Files or Receive Files to start an offline transfer.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp),
            )
          }
        }
      } else {
        items(recentTransfers.take(5), key = { it.id }) { item ->
          Surface(
            onClick = onOpenTransfers,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                  imageVector = if (item.direction == TransferDirection.SEND) Icons.Rounded.Upload else Icons.Rounded.Download,
                  contentDescription = item.direction.name,
                  tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  Text(
                    text = "${Formatters.formatBytes(item.fileSize)} • ${item.peerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              TransferStatusBadge(status = item.status)
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(12.dp)) }
    }
  }
}

@Composable
private fun ConnectionModeCard(
  icon: ImageVector,
  title: String,
  subtitle: String,
  accent: Color,
  testTag: String,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag(testTag),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Box(
        modifier = Modifier
          .size(44.dp)
          .clip(RoundedCornerShape(13.dp))
          .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0.10f)))),
        contentAlignment = Alignment.Center,
      ) {
        Icon(imageVector = icon, contentDescription = title, tint = accent)
      }
      Spacer(modifier = Modifier.width(14.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.width(8.dp))
      Icon(
        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
        contentDescription = "Open $title",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
