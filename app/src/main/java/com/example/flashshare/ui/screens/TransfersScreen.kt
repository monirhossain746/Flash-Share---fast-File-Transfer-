package com.example.flashshare.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flashshare.model.TransferDirection
import com.example.flashshare.model.TransferItem
import com.example.flashshare.model.TransferStatus
import com.example.flashshare.ui.components.StatusBadge
import com.example.flashshare.ui.components.TransferStatusBadge
import com.example.flashshare.util.Formatters
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun TransfersScreen(
  liveTransfers: List<TransferItem>,
  historyItems: List<TransferItem>,
  onPause: (String) -> Unit,
  onResume: (String) -> Unit,
  onCancel: (String) -> Unit,
  onRetry: (String) -> Unit,
  onDeleteHistory: (String) -> Unit,
  onClearHistoryGroup: (Set<TransferStatus>) -> Unit,
) {
  var selectedTab by remember { mutableIntStateOf(0) }
  var inspectedItem by remember { mutableStateOf<TransferItem?>(null) }

  val merged = remember(liveTransfers, historyItems) {
    val map = LinkedHashMap<String, TransferItem>()
    liveTransfers.forEach { map[it.id] = it }
    historyItems.forEach { if (!map.containsKey(it.id)) map[it.id] = it }
    map.values.sortedByDescending { it.timestamp }
  }

  val activeList = remember(merged) {
    merged.filter {
      it.status == TransferStatus.IN_PROGRESS ||
        it.status == TransferStatus.PENDING ||
        it.status == TransferStatus.PAUSED
    }
  }
  val completedList = remember(merged) {
    merged.filter { it.status == TransferStatus.COMPLETED }
  }
  val failedList = remember(merged) {
    merged.filter {
      it.status == TransferStatus.FAILED ||
        it.status == TransferStatus.CANCELLED ||
        it.status == TransferStatus.REJECTED
    }
  }

  val displayed = when (selectedTab) {
    0 -> activeList
    1 -> completedList
    else -> failedList
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
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(text = "Transfers & History", style = MaterialTheme.typography.headlineMedium)
          Text(
            text = "${activeList.size} active • ${completedList.size} completed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        if (selectedTab != 0 && displayed.isNotEmpty()) {
          TextButton(
            onClick = {
              val statuses = if (selectedTab == 1) setOf(TransferStatus.COMPLETED)
              else setOf(TransferStatus.FAILED, TransferStatus.CANCELLED, TransferStatus.REJECTED)
              onClearHistoryGroup(statuses)
            },
            modifier = Modifier.testTag("clear_transfer_tab_button"),
          ) {
            Text("Clear Tab")
          }
        }
      }

      TabRow(selectedTabIndex = selectedTab) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Active (${activeList.size})") },
          modifier = Modifier.testTag("tab_active_transfers"),
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Done (${completedList.size})") },
          modifier = Modifier.testTag("tab_completed_transfers"),
        )
        Tab(
          selected = selectedTab == 2,
          onClick = { selectedTab = 2 },
          text = { Text("Failed (${failedList.size})") },
          modifier = Modifier.testTag("tab_failed_transfers"),
        )
      }

      if (displayed.isEmpty()) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.SwapVert,
              contentDescription = "No transfers",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(48.dp),
            )
            Text(
              text = when (selectedTab) {
                0 -> "No active transfers"
                1 -> "No completed transfers yet"
                else -> "No failed or cancelled transfers"
              },
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              text = "Active transfers show real-time MB/s throughput, remaining bytes, ETA, and SHA-256 verification.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(18.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          items(displayed, key = { it.id }) { item ->
            TransferDashboardCard(
              item = item,
              onInspect = { inspectedItem = item },
              onPause = { onPause(item.id) },
              onResume = { onResume(item.id) },
              onCancel = { onCancel(item.id) },
              onRetry = { onRetry(item.id) },
              onDelete = { onDeleteHistory(item.id) },
            )
          }
        }
      }
    }
  }

  inspectedItem?.let { item ->
    AlertDialog(
      onDismissRequest = { inspectedItem = null },
      title = { Text(item.fileName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text("Mode: ${item.mode.label}", style = MaterialTheme.typography.bodySmall)
          Text("Peer: ${item.peerName} (${item.peerAddress})", style = MaterialTheme.typography.bodySmall)
          Text("Size: ${Formatters.formatBytes(item.fileSize)}", style = MaterialTheme.typography.bodySmall)
          Text("Time: ${Formatters.formatTimestamp(item.timestamp)}", style = MaterialTheme.typography.bodySmall)
          if (item.sha256Local != null) {
            Text(
              text = "SHA-256: ${item.sha256Local}",
              style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            )
          }
          if (item.savedUri != null) {
            Text("Saved URI: ${item.savedUri}", style = MaterialTheme.typography.labelSmall)
          }
          if (item.isExecutableOrInstaller) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = StatusWarning.copy(alpha = 0.16f),
            ) {
              Text(
                text = "Security notice: Executable/installer files are never executed automatically by FlashShare.",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(8.dp),
              )
            }
          }
          if (item.errorMessage != null) {
            Text(
              text = "Error: ${item.errorMessage}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { inspectedItem = null }) { Text("Close") }
      },
    )
  }
}

@Composable
private fun TransferDashboardCard(
  item: TransferItem,
  onInspect: () -> Unit,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onCancel: () -> Unit,
  onRetry: () -> Unit,
  onDelete: () -> Unit,
) {
  val pct = (item.progress * 100f).toInt().coerceIn(0, 100)
  Card(
    onClick = onInspect,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("transfer_card_${item.fileName}"),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          Icon(
            imageVector = if (item.direction == TransferDirection.SEND) Icons.Rounded.Upload else Icons.Rounded.Download,
            contentDescription = item.direction.name,
            tint = MaterialTheme.colorScheme.primary,
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = item.fileName,
              style = MaterialTheme.typography.titleSmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = "${item.mode.label} • ${item.peerName}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        TransferStatusBadge(status = item.status)
      }

      if (item.status == TransferStatus.IN_PROGRESS || item.status == TransferStatus.PAUSED || item.status == TransferStatus.PENDING) {
        LinearProgressIndicator(
          progress = { item.progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(99.dp)),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "$pct% • ${Formatters.formatBytes(item.bytesTransferred)} / ${Formatters.formatBytes(item.fileSize)}",
            style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
          )
          Text(
            text = "${Formatters.formatSpeed(item.speedBytesPerSec)} • ETA ${Formatters.formatDuration(item.etaSeconds)}",
            style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Text(
          text = "Remaining: ${Formatters.formatBytes(item.remainingBytes)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          if (item.status == TransferStatus.IN_PROGRESS) {
            OutlinedButton(
              onClick = onPause,
              modifier = Modifier.testTag("pause_transfer_button"),
            ) {
              Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pause")
            }
          } else if (item.status == TransferStatus.PAUSED) {
            Button(
              onClick = onResume,
              modifier = Modifier.testTag("resume_transfer_button"),
            ) {
              Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Resume")
            }
          }
          OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.testTag("cancel_transfer_button"),
          ) {
            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Cancel")
          }
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${Formatters.formatBytes(item.fileSize)} • ${Formatters.formatTimestamp(item.timestamp)}",
              style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.verified && item.status == TransferStatus.COMPLETED) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.Verified,
                  contentDescription = "SHA-256 verified",
                  tint = StatusSuccess,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "SHA-256 Integrity Verified",
                  style = MaterialTheme.typography.labelSmall,
                  color = StatusSuccess,
                )
              }
            }
            if (item.isExecutableOrInstaller && item.status == TransferStatus.COMPLETED) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.WarningAmber,
                  contentDescription = "Installer notice",
                  tint = StatusWarning,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "Installer saved (never auto-executed)",
                  style = MaterialTheme.typography.labelSmall,
                  color = StatusWarning,
                )
              }
            }
            if (!item.errorMessage.isNullOrBlank()) {
              Text(
                text = item.errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
              )
            }
          }

          Row {
            if (item.status == TransferStatus.FAILED && item.direction == TransferDirection.SEND) {
              IconButton(onClick = onRetry, modifier = Modifier.testTag("retry_transfer_button")) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Retry transfer")
              }
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_history_button")) {
              Icon(Icons.Rounded.Delete, contentDescription = "Remove from history")
            }
          }
        }
      }
    }
  }
}
