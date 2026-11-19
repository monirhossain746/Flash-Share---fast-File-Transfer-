package com.example.flashshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flashshare.model.FileCategory
import com.example.flashshare.model.TransferStatus
import com.example.ui.theme.FlashBlue
import com.example.ui.theme.FlashCyan
import com.example.ui.theme.MonoMetricStyle
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

val FlashGradient = Brush.linearGradient(listOf(FlashBlue, FlashCyan))

fun categoryIcon(category: FileCategory): ImageVector = when (category) {
  FileCategory.IMAGES -> Icons.Rounded.Image
  FileCategory.VIDEOS -> Icons.Rounded.VideoFile
  FileCategory.AUDIO -> Icons.Rounded.AudioFile
  FileCategory.DOCUMENTS -> Icons.Rounded.Description
  FileCategory.APK -> Icons.Rounded.Android
  FileCategory.ARCHIVES -> Icons.Rounded.FolderZip
  FileCategory.OTHER, FileCategory.ALL -> Icons.Rounded.InsertDriveFile
}

fun categoryTint(category: FileCategory): Color = when (category) {
  FileCategory.IMAGES -> Color(0xFF06B6D4)
  FileCategory.VIDEOS -> Color(0xFF6366F1)
  FileCategory.AUDIO -> Color(0xFFEC4899)
  FileCategory.DOCUMENTS -> Color(0xFF3B82F6)
  FileCategory.APK -> Color(0xFF10B981)
  FileCategory.ARCHIVES -> Color(0xFFF59E0B)
  FileCategory.OTHER, FileCategory.ALL -> Color(0xFF64748B)
}

@Composable
fun StatusBadge(
  text: String,
  color: Color,
  modifier: Modifier = Modifier,
  showDot: Boolean = true,
) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(999.dp),
    color = color.copy(alpha = 0.14f),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      if (showDot) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color),
        )
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun TransferStatusBadge(status: TransferStatus) {
  val (label, color) = when (status) {
    TransferStatus.PENDING -> "Waiting" to StatusWarning
    TransferStatus.IN_PROGRESS -> "Transferring" to FlashCyan
    TransferStatus.PAUSED -> "Paused" to StatusWarning
    TransferStatus.COMPLETED -> "Completed" to StatusSuccess
    TransferStatus.FAILED -> "Failed" to StatusDanger
    TransferStatus.CANCELLED -> "Cancelled" to Color(0xFF94A3B8)
    TransferStatus.REJECTED -> "Declined" to StatusDanger
  }
  StatusBadge(text = label, color = color)
}

@Composable
fun MetricPill(label: String, value: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(12.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = value,
        style = MonoMetricStyle.copy(fontSize = MaterialTheme.typography.labelLarge.fontSize),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun SectionHeader(title: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onBackground,
    )
    if (actionText != null && onAction != null) {
      Surface(
        onClick = onAction,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
      ) {
        Text(
          text = actionText,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
  }
}

@Composable
fun InfoBanner(
  icon: ImageVector,
  title: String,
  subtitle: String,
  accent: Color = MaterialTheme.colorScheme.primary,
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = accent.copy(alpha = 0.10f),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = title,
        tint = accent,
        modifier = Modifier.size(22.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
      Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}
