package com.example.flashshare.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.R
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppControlConfig(
  val appDisabled: Boolean = false,
  val disableMessage: String = DEFAULT_DISABLE_MESSAGE,
  val actionUrl: String = "",
  val forceUpdate: Boolean = false,
  val latestVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
  val latestVersionName: String = BuildConfig.VERSION_NAME,
  val updateMessage: String = DEFAULT_UPDATE_MESSAGE,
  val updateUrl: String = DEFAULT_UPDATE_URL,
  val apkDownloadUrl: String = "",
  val autoDownloadUpdate: Boolean = false,
  val isFirebaseConfigured: Boolean = false,
  val lastFetchStatus: String = "Defaults active",
) {
  fun hasNewerVersion(currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()): Boolean {
    return latestVersionCode > currentVersionCode
  }

  fun isUpdateRequired(currentVersionCode: Long = BuildConfig.VERSION_CODE.toLong()): Boolean {
    return forceUpdate && hasNewerVersion(currentVersionCode)
  }

  fun resolvedUpdateUrl(): String {
    return apkDownloadUrl.takeIf { it.isNotBlank() } ?: updateUrl.ifBlank { DEFAULT_UPDATE_URL }
  }

  companion object {
    const val KEY_APP_DISABLED = "app_disabled"
    const val KEY_DISABLE_MESSAGE = "disable_message"
    const val KEY_ACTION_URL = "action_url"
    const val KEY_FORCE_UPDATE = "force_update"
    const val KEY_LATEST_VERSION_CODE = "latest_version_code"
    const val KEY_LATEST_VERSION_NAME = "latest_version_name"
    const val KEY_UPDATE_MESSAGE = "update_message"
    const val KEY_UPDATE_URL = "update_url"
    const val KEY_APK_DOWNLOAD_URL = "apk_download_url"
    const val KEY_AUTO_DOWNLOAD_UPDATE = "auto_download_update"

    const val DEFAULT_DISABLE_MESSAGE =
      "Flash Share is currently unavailable due to scheduled maintenance. Please check back soon."
    const val DEFAULT_UPDATE_MESSAGE =
      "A newer version of Flash Share - Fast File Share is available with performance and security improvements."
    const val DEFAULT_UPDATE_URL =
      "https://github.com/monirhossain746/Flash-Share---fast-File-Transfer-"
  }
}

sealed interface UpdateCheckResult {
  data object Idle : UpdateCheckResult
  data object Checking : UpdateCheckResult
  data class UpdateAvailable(val config: AppControlConfig) : UpdateCheckResult
  data class UpToDate(val versionName: String, val versionCode: Int) : UpdateCheckResult
  data class Error(val message: String) : UpdateCheckResult
}

class RemoteConfigManager(private val context: Context) {

  companion object {
    private const val TAG = "FlashShareRemoteConfig"
  }

  private val _config = MutableStateFlow(AppControlConfig())
  val config: StateFlow<AppControlConfig> = _config.asStateFlow()

  private val _updateCheckState = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
  val updateCheckState: StateFlow<UpdateCheckResult> = _updateCheckState.asStateFlow()

  private var remoteConfig: FirebaseRemoteConfig? = null
  private var initialized = false

  @Synchronized
  private fun ensureInitialized(): FirebaseRemoteConfig? {
    if (initialized) return remoteConfig
    initialized = true

    val firebaseReady = runCatching {
      FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
    }.getOrDefault(false)

    if (!firebaseReady) {
      Log.i(TAG, "FirebaseApp not initialized (add app/google-services.json to enable Remote Config).")
      _config.value = AppControlConfig(
        isFirebaseConfigured = false,
        lastFetchStatus = "Offline defaults (Firebase not configured)",
      )
      return null
    }

    return runCatching {
      val rc = FirebaseRemoteConfig.getInstance()
      val settings = FirebaseRemoteConfigSettings.Builder()
        .setMinimumFetchIntervalInSeconds(if (BuildConfig.DEBUG) 0L else 3600L)
        .setFetchTimeoutInSeconds(10L)
        .build()
      rc.setConfigSettingsAsync(settings)
      rc.setDefaultsAsync(R.xml.remote_config_defaults)
      remoteConfig = rc
      publishFromInstance(rc, statusLabel = "Cached / defaults loaded")
      rc
    }.onFailure { err ->
      Log.w(TAG, "Failed to initialize FirebaseRemoteConfig: ${err.localizedMessage}")
      _config.value = AppControlConfig(
        isFirebaseConfigured = false,
        lastFetchStatus = "Fallback defaults active",
      )
    }.getOrNull()
  }

  fun fetchAndActivate() {
    val rc = ensureInitialized() ?: return
    runCatching {
      rc.fetchAndActivate()
        .addOnSuccessListener { updated ->
          publishFromInstance(
            rc = rc,
            statusLabel = if (updated) "Updated from Firebase" else "Up to date",
          )
        }
        .addOnFailureListener { err ->
          Log.w(TAG, "Remote Config fetch failed (keeping safe fallback): ${err.localizedMessage}")
          publishFromInstance(
            rc = rc,
            statusLabel = "Offline / cached fallback",
          )
        }
    }.onFailure { err ->
      Log.w(TAG, "Remote Config fetch error: ${err.localizedMessage}")
    }
  }

  fun checkForUpdatesNow() {
    val rc = ensureInitialized()
    if (rc == null) {
      _updateCheckState.value = UpdateCheckResult.Error(
        "Firebase is not configured. Use Manually Update to check GitHub releases.",
      )
      return
    }
    _updateCheckState.value = UpdateCheckResult.Checking
    runCatching {
      rc.fetch(0L)
        .continueWithTask { fetchTask ->
          if (!fetchTask.isSuccessful) {
            throw fetchTask.exception ?: IllegalStateException("Unable to fetch update info")
          }
          rc.activate()
        }
        .addOnSuccessListener {
          val latest = publishFromInstance(rc, statusLabel = "Checked for updates")
          _updateCheckState.value = if (latest.hasNewerVersion()) {
            UpdateCheckResult.UpdateAvailable(latest)
          } else {
            UpdateCheckResult.UpToDate(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
          }
        }
        .addOnFailureListener { err ->
          Log.w(TAG, "Manual update check failed: ${err.localizedMessage}")
          _updateCheckState.value = UpdateCheckResult.Error(
            "Unable to check updates right now (offline or unreachable). Try Manually Update on GitHub.",
          )
        }
    }.onFailure { err ->
      _updateCheckState.value = UpdateCheckResult.Error(
        err.localizedMessage ?: "Update check failed.",
      )
    }
  }

  fun clearUpdateCheckState() {
    _updateCheckState.value = UpdateCheckResult.Idle
  }

  private fun publishFromInstance(rc: FirebaseRemoteConfig, statusLabel: String): AppControlConfig {
    val updated = AppControlConfig(
      appDisabled = rc.getBoolean(AppControlConfig.KEY_APP_DISABLED),
      disableMessage = rc.getString(AppControlConfig.KEY_DISABLE_MESSAGE)
        .ifBlank { AppControlConfig.DEFAULT_DISABLE_MESSAGE },
      actionUrl = rc.getString(AppControlConfig.KEY_ACTION_URL).trim(),
      forceUpdate = rc.getBoolean(AppControlConfig.KEY_FORCE_UPDATE),
      latestVersionCode = rc.getLong(AppControlConfig.KEY_LATEST_VERSION_CODE)
        .coerceAtLeast(1L),
      latestVersionName = rc.getString(AppControlConfig.KEY_LATEST_VERSION_NAME)
        .ifBlank { BuildConfig.VERSION_NAME },
      updateMessage = rc.getString(AppControlConfig.KEY_UPDATE_MESSAGE)
        .ifBlank { AppControlConfig.DEFAULT_UPDATE_MESSAGE },
      updateUrl = rc.getString(AppControlConfig.KEY_UPDATE_URL)
        .ifBlank { AppControlConfig.DEFAULT_UPDATE_URL },
      apkDownloadUrl = rc.getString(AppControlConfig.KEY_APK_DOWNLOAD_URL).trim(),
      autoDownloadUpdate = rc.getBoolean(AppControlConfig.KEY_AUTO_DOWNLOAD_UPDATE),
      isFirebaseConfigured = true,
      lastFetchStatus = statusLabel,
    )
    _config.value = updated
    return updated
  }
}
