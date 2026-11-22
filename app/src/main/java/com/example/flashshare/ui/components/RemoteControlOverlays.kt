package com.example.flashshare.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.flashshare.remote.AppControlConfig
import com.example.ui.theme.StatusDanger

@Composable
fun AppDisabledOverlay(
  config: AppControlConfig,
  onOpenActionUrl: (String) -> Unit,
) {
  BackHandler(enabled = true) {
    // Intentionally blocked while app_disabled = true
  }

  Surface(
    color = MaterialTheme.colorScheme.background,
    modifier = Modifier
      .fillMaxSize()
      .testTag("app_disabled_overlay"),
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        modifier = Modifier.widthIn(max = 460.dp),
      ) {
        Column(
          modifier = Modifier.padding(26.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          Box(
            modifier = Modifier
              .size(64.dp)
              .clip(CircleShape)
              .background(StatusDanger.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
          ) {
            Icon(
              imageVector = Icons.Rounded.Block,
              contentDescription = "App unavailable",
              tint = StatusDanger,
              modifier = Modifier.size(34.dp),
            )
          }

          Text(
            text = "App Unavailable",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
          )

          Text(
            text = config.disableMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )

          if (config.actionUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
              onClick = { onOpenActionUrl(config.actionUrl) },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("app_disabled_action_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Open Details")
            }
          }
        }
      }
    }
  }
}

@Composable
fun ForceUpdateDialog(
  config: AppControlConfig,
  onOpenUpdateUrl: (String) -> Unit,
) {
  UpdateDialogContent(
    config = config,
    isForced = true,
    onUpdateNow = { onOpenUpdateUrl(config.resolvedUpdateUrl()) },
    onDismiss = {},
  )
}

@Composable
fun OptionalUpdateDialog(
  config: AppControlConfig,
  onOpenUpdateUrl: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  UpdateDialogContent(
    config = config,
    isForced = false,
    onUpdateNow = { onOpenUpdateUrl(config.resolvedUpdateUrl()) },
    onDismiss = onDismiss,
  )
}

@Composable
private fun UpdateDialogContent(
  config: AppControlConfig,
  isForced: Boolean,
  onUpdateNow: () -> Unit,
  onDismiss: () -> Unit,
) {
  Dialog(
    onDismissRequest = { if (!isForced) onDismiss() },
    properties = DialogProperties(
      dismissOnBackPress = !isForced,
      dismissOnClickOutside = !isForced,
    ),
  ) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp,
      modifier = Modifier
        .fillMaxWidth()
        .testTag(if (isForced) "force_update_dialog" else "optional_update_dialog"),
    ) {
      Column(
        modifier = Modifier.padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Box(
          modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = Icons.Rounded.SystemUpdate,
            contentDescription = "Update available",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
          )
        }

        Text(
          text = if (isForced) "Update Required" else "Update Available",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
          textAlign = TextAlign.Center,
        )

        Text(
          text = "Installed: v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})  →  New: v${config.latestVersionName} (${config.latestVersionCode})",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
        )

        Text(
          text = config.updateMessage,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (isForced) {
          Button(
            onClick = onUpdateNow,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("force_update_button"),
          ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Update Now")
          }
        } else {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            OutlinedButton(
              onClick = onDismiss,
              modifier = Modifier
                .weight(1f)
                .testTag("dismiss_update_button"),
            ) {
              Text("Later")
            }
            Button(
              onClick = onUpdateNow,
              modifier = Modifier
                .weight(1f)
                .testTag("update_now_button"),
            ) {
              Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Update Now")
            }
          }
        }
      }
    }
  }
}
