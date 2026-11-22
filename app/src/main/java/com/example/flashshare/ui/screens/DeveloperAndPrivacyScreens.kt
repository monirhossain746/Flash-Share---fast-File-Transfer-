package com.example.flashshare.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.flashshare.ads.AdMobConfig
import com.example.flashshare.ui.components.FlashGradient

object DeveloperInfo {
  const val NAME = "Md. Monir Hossain"
  const val ROLE = "Web Developer, SEO Specialist & Graphic Designer"
  const val LOCATION = "Mymensingh, Bangladesh"
  const val EMAIL = "Monirhossain6830@gmail.com"
  const val WEBSITE = "https://Monirhossain746.blogspot.com"
  const val GITHUB_URL = "https://github.com/monirhossain746/Flash-Share---fast-File-Transfer-"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperDetailsScreen(
  onOpenUrl: (String) -> Unit,
  onSendEmail: (String) -> Unit,
  onShowMessage: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)
  val context = LocalContext.current

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Developer Details") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("developer_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
        // Profile Banner Card
        item {
          Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Box(
                modifier = Modifier
                  .size(72.dp)
                  .clip(CircleShape)
                  .background(FlashGradient),
                contentAlignment = Alignment.Center,
              ) {
                Icon(
                  imageVector = Icons.Rounded.Person,
                  contentDescription = DeveloperInfo.NAME,
                  tint = Color(0xFF031526),
                  modifier = Modifier.size(40.dp),
                )
              }
              Text(
                text = DeveloperInfo.NAME,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
              )
              Text(
                text = DeveloperInfo.ROLE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
              )
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Rounded.LocationOn,
                  contentDescription = "Location",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = DeveloperInfo.LOCATION,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        // Overview Card
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Background & Craft", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "Md. Monir Hossain is a multidisciplinary Web Developer, SEO Specialist, and Graphic Designer based in Mymensingh, Bangladesh. With hands-on experience combining clean interface design, search optimization, and practical utility software, Flash Share - Fast File Share was built to give Android and Windows PC users a fast, lightweight, offline local file-transfer experience without cloud uploads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }

        // Contact & Links Card
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
              Text("Contact & Portfolio", style = MaterialTheme.typography.titleMedium)

              InfoRow(
                icon = Icons.Rounded.Email,
                label = "Email Address",
                value = DeveloperInfo.EMAIL,
              )

              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                  onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Developer Email", DeveloperInfo.EMAIL))
                    onShowMessage("Copied ${DeveloperInfo.EMAIL}")
                  },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("copy_developer_email_button"),
                ) {
                  Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Copy Email")
                }
                Button(
                  onClick = { onSendEmail(DeveloperInfo.EMAIL) },
                  modifier = Modifier
                    .weight(1f)
                    .testTag("email_developer_button"),
                ) {
                  Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Email")
                }
              }

              InfoRow(
                icon = Icons.Rounded.Language,
                label = "Portfolio Website",
                value = DeveloperInfo.WEBSITE,
              )

              Button(
                onClick = { onOpenUrl(DeveloperInfo.WEBSITE) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("open_portfolio_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Visit Portfolio Website")
              }

              OutlinedButton(
                onClick = { onOpenUrl(DeveloperInfo.GITHUB_URL) },
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("open_github_repo_button"),
              ) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Flash Share GitHub Repository")
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
fun PrivacyPolicyScreen(
  onOpenUrl: (String) -> Unit,
  onSendEmail: (String) -> Unit,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Privacy Policy") },
        navigationIcon = {
          IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        item {
          Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                imageVector = Icons.Rounded.PrivacyTip,
                contentDescription = "Privacy Policy",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
              )
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Flash Share - Fast File Share",
                  style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                  text = "Package: ${BuildConfig.APPLICATION_ID} • Effective: Nov 2026",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }

        item {
          PolicySectionCard(
            title = "1. App Overview",
            body = "Flash Share - Fast File Share (${BuildConfig.APPLICATION_ID}) is a local offline file-transfer utility developed by ${DeveloperInfo.NAME}. It enables fast file sharing between Android devices and between Android devices and Windows/desktop web browsers over your local Wi-Fi network or mobile hotspot.",
          )
        }

        item {
          PolicySectionCard(
            title = "2. Local File Sharing & Permissions",
            body = "• Storage & Document Access: Files and folders are selected strictly by you using Android's Photo Picker and Storage Access Framework (SAF). Flash Share does not scan, index, or upload your personal media or files to any external cloud server.\n" +
              "• Network & Local Discovery: Wi-Fi, network state, and multicast permissions are used solely to detect your local LAN IP address, run the local transfer server, and discover nearby Flash Share devices on the same Wi-Fi/hotspot.\n" +
              "• Camera Permission (Optional): Requested only when you choose to scan a Flash Share QR pairing code; camera frames are processed strictly on-device and are never recorded or transmitted.\n" +
              "• Notifications (Optional): Used on Android 13+ solely to display ongoing transfer progress and speed in the foreground notification.",
          )
        }

        item {
          PolicySectionCard(
            title = "3. Firebase Remote Config & App Updates",
            body = "Flash Share integrates Google Firebase Remote Config (App ID: 1:86558931675:android:29e535f2b976f95df92afe) to fetch lightweight app configuration flags such as update availability, maintenance notices, and service status. Firebase SDKs may collect standard technical diagnostics and app-instance configuration signals in accordance with Google's Firebase Privacy Policy.",
          )
        }

        item {
          PolicySectionCard(
            title = "4. Advertising (Google AdMob)",
            body = "Flash Share uses Google AdMob (App ID: ${AdMobConfig.ADMOB_APP_ID}) to display adaptive banner advertisements. When an internet connection is available, Google AdMob may collect and process device identifiers (such as the Android Advertising ID), IP address, app interaction signals, and crash/performance diagnostics to serve and measure ads in accordance with Google's Privacy Policy.\n\n" +
              "When you use Flash Share completely offline, banner ads remain hidden without interrupting file transfers. You can manage or reset your Advertising ID and ad personalization preferences anytime in your Android device under Settings → Privacy → Ads (or Google → Ads).",
          )
        }

        item {
          PolicySectionCard(
            title = "5. Data Security & Retention",
            body = "• Local-Only Transfers: All transferred files move directly between your connected devices over private local IP addresses protected by per-session pairing tokens and optional SHA-256 checksum verification.\n" +
              "• Local History & Settings: Your transfer history and app preferences (device name, port, destination folder) are stored strictly on your own device and can be cleared anytime from Settings.\n" +
              "• Installer Safety: Flash Share never executes received APK, XAPK, or EXE files automatically.",
          )
        }

        item {
          PolicySectionCard(
            title = "6. Children's Privacy & Policy Updates",
            body = "Flash Share is a general-purpose file-sharing utility and is not directed to children under the age of 13. We do not knowingly collect personal information from children.\n\n" +
              "We may update this Privacy Policy from time to time to reflect app improvements or legal/platform requirements. Updated versions will be made available directly inside the app and on our official website.",
          )
        }

        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("7. Contact Information", style = MaterialTheme.typography.titleMedium)
              }
              Text(
                text = "If you have questions or requests regarding this Privacy Policy or Flash Share, contact the developer:\n" +
                  "• Developer: ${DeveloperInfo.NAME} (${DeveloperInfo.LOCATION})\n" +
                  "• Email: ${DeveloperInfo.EMAIL}\n" +
                  "• Website: ${DeveloperInfo.WEBSITE}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                  onClick = { onSendEmail(DeveloperInfo.EMAIL) },
                  modifier = Modifier.weight(1f),
                ) {
                  Text("Email Developer")
                }
                Button(
                  onClick = { onOpenUrl(DeveloperInfo.WEBSITE) },
                  modifier = Modifier.weight(1f),
                ) {
                  Text("Visit Website")
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun PolicySectionCard(title: String, body: String) {
  Card(
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
      Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
