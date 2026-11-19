package com.example.flashshare.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.flashshare.model.AppSettings
import com.example.flashshare.model.DuplicateAction
import com.example.flashshare.model.ThemePreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "flashshare_settings")

class SettingsRepository(private val context: Context) {

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  private val _settings = MutableStateFlow(AppSettings())
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private object Keys {
    val DEVICE_NAME = stringPreferencesKey("device_name")
    val DEFAULT_PORT = intPreferencesKey("default_port")
    val DOWNLOAD_TREE_URI = stringPreferencesKey("download_tree_uri")
    val DOWNLOAD_FOLDER_LABEL = stringPreferencesKey("download_folder_label")
    val BUFFER_SIZE_KB = intPreferencesKey("buffer_size_kb")
    val THEME_PREF = stringPreferencesKey("theme_preference")
    val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
    val AUTO_VERIFY_SHA256 = booleanPreferencesKey("auto_verify_sha256")
    val DUPLICATE_ACTION = stringPreferencesKey("duplicate_action")
  }

  init {
    scope.launch {
      context.dataStore.data
        .map { prefs ->
          AppSettings(
            deviceName = prefs[Keys.DEVICE_NAME]?.takeIf { it.isNotBlank() }
              ?: Build.MODEL.ifBlank { "Android Device" },
            defaultPort = (prefs[Keys.DEFAULT_PORT] ?: 8080).coerceIn(1024, 65535),
            downloadTreeUri = prefs[Keys.DOWNLOAD_TREE_URI],
            downloadFolderLabel = prefs[Keys.DOWNLOAD_FOLDER_LABEL] ?: "Downloads/FlashShare",
            bufferSizeKb = (prefs[Keys.BUFFER_SIZE_KB] ?: 256).coerceIn(32, 1024),
            themePreference = prefs[Keys.THEME_PREF]?.let {
              runCatching { ThemePreference.valueOf(it) }.getOrNull()
            } ?: ThemePreference.SYSTEM,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS] ?: true,
            autoVerifySha256 = prefs[Keys.AUTO_VERIFY_SHA256] ?: true,
            duplicateAction = prefs[Keys.DUPLICATE_ACTION]?.let {
              runCatching { DuplicateAction.valueOf(it) }.getOrNull()
            } ?: DuplicateAction.ASK,
          )
        }
        .collect { loaded ->
          _settings.value = loaded
        }
    }
  }

  suspend fun updateDeviceName(name: String) {
    val clean = name.trim().take(32)
    if (clean.isEmpty()) return
    context.dataStore.edit { it[Keys.DEVICE_NAME] = clean }
  }

  suspend fun updateDefaultPort(port: Int) {
    if (port in 1024..65535) {
      context.dataStore.edit { it[Keys.DEFAULT_PORT] = port }
    }
  }

  suspend fun updateDownloadFolder(treeUri: String?, label: String) {
    context.dataStore.edit { prefs ->
      if (treeUri == null) {
        prefs.remove(Keys.DOWNLOAD_TREE_URI)
      } else {
        prefs[Keys.DOWNLOAD_TREE_URI] = treeUri
      }
      prefs[Keys.DOWNLOAD_FOLDER_LABEL] = label
    }
  }

  suspend fun updateBufferSizeKb(sizeKb: Int) {
    context.dataStore.edit { it[Keys.BUFFER_SIZE_KB] = sizeKb.coerceIn(32, 1024) }
  }

  suspend fun updateTheme(pref: ThemePreference) {
    context.dataStore.edit { it[Keys.THEME_PREF] = pref.name }
  }

  suspend fun updateNotifications(enabled: Boolean) {
    context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }
  }

  suspend fun updateAutoVerify(enabled: Boolean) {
    context.dataStore.edit { it[Keys.AUTO_VERIFY_SHA256] = enabled }
  }

  suspend fun updateDuplicateAction(action: DuplicateAction) {
    context.dataStore.edit { it[Keys.DUPLICATE_ACTION] = action.name }
  }
}
