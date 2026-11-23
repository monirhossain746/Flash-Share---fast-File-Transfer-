package com.example.flashshare.ads

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AdMobConfig {
  private const val TAG = "FlashShareAdMob"

  const val ADMOB_APP_ID = "ca-app-pub-3181872815781744~9894324318"
  const val LIVE_BANNER_AD_UNIT_ID = "ca-app-pub-3181872815781744/6198621889"

  private val initStarted = AtomicBoolean(false)

  private val _isSdkReady = MutableStateFlow(false)
  val isSdkReady: StateFlow<Boolean> = _isSdkReady.asStateFlow()

  fun activeBannerUnitId(): String = LIVE_BANNER_AD_UNIT_ID

  fun initialize(context: Context) {
    if (!initStarted.compareAndSet(false, true)) return
    runCatching {
      MobileAds.initialize(context.applicationContext) { status ->
        Log.d(TAG, "MobileAds initialized: ${status.adapterStatusMap.keys}")
        _isSdkReady.value = true
      }
    }.onFailure { err ->
      Log.w(TAG, "MobileAds initialization skipped/failed: ${err.localizedMessage}")
      _isSdkReady.value = false
    }
  }
}

@Composable
fun AdaptiveBannerAd(
  adUnitId: String,
  modifier: Modifier = Modifier,
) {
  if (LocalInspectionMode.current) return

  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val sdkReady by AdMobConfig.isSdkReady.collectAsStateWithLifecycle()

  if (!sdkReady) return

  BoxWithConstraints(
    modifier = modifier
      .fillMaxWidth()
      .background(MaterialTheme.colorScheme.surface)
      .animateContentSize()
      .testTag("admob_banner_container"),
    contentAlignment = Alignment.Center,
  ) {
    val widthDp = maxWidth.value.toInt().coerceAtLeast(320)
    val adSize = remember(widthDp, context) {
      AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
    }
    val adHeightDp = remember(adSize, context) {
      adSize.getHeightInPixels(context).let { px ->
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        (px / density).dp
      }
    }

    var isAdLoaded by remember(adUnitId) { mutableStateOf(false) }

    val adView = remember(adUnitId, adSize) {
      AdView(context).apply {
        setAdSize(adSize)
        setAdUnitId(adUnitId)
        adListener = object : AdListener() {
          override fun onAdLoaded() {
            isAdLoaded = true
          }

          override fun onAdFailedToLoad(error: LoadAdError) {
            isAdLoaded = false
          }
        }
      }
    }

    LaunchedEffect(adView) {
      runCatching { adView.loadAd(AdRequest.Builder().build()) }
    }

    DisposableEffect(lifecycleOwner, adView) {
      val observer = LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_RESUME -> runCatching { adView.resume() }
          Lifecycle.Event.ON_PAUSE -> runCatching { adView.pause() }
          else -> Unit
        }
      }
      lifecycleOwner.lifecycle.addObserver(observer)
      onDispose {
        lifecycleOwner.lifecycle.removeObserver(observer)
        runCatching { adView.destroy() }
      }
    }

    // Collapses cleanly to 0.dp when ad hasn't loaded or fails (e.g. offline mode)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(if (isAdLoaded) adHeightDp else 0.dp),
      contentAlignment = Alignment.Center,
    ) {
      AndroidView(
        factory = { adView },
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
