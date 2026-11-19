package com.example.flashshare.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.flashshare.network.QRCodeManager
import com.example.ui.theme.FlashCyan
import java.util.concurrent.Executors

@Composable
fun QrScannerDialog(
  onQrDetected: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  var hasPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED,
    )
  }
  var detected by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission(),
  ) { granted ->
    hasPermission = granted
  }

  LaunchedEffect(Unit) {
    if (!hasPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column(
        modifier = Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Scan FlashShare QR",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.align(Alignment.CenterStart),
          )
          IconButton(
            onClick = onDismiss,
            modifier = Modifier
              .align(Alignment.CenterEnd)
              .testTag("close_qr_scanner"),
          ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close scanner")
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (hasPermission) {
          val executor = remember { Executors.newSingleThreadExecutor() }
          DisposableEffect(Unit) {
            onDispose { executor.shutdown() }
          }

          Box(
            modifier = Modifier
              .fillMaxWidth()
              .aspectRatio(1f)
              .clip(RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center,
          ) {
            AndroidView(
              modifier = Modifier.fillMaxSize(),
              factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                  {
                    val provider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@addListener
                    val preview = Preview.Builder().build().also {
                      it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                      .setTargetResolution(Size(960, 720))
                      .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                      .build()
                      .also { imgAnalysis ->
                        imgAnalysis.setAnalyzer(executor) { proxy ->
                          try {
                            if (!detected) {
                              val text = QRCodeManager.decodeFromImageProxy(proxy)
                              if (text != null) {
                                detected = true
                                previewView.post { onQrDetected(text) }
                              }
                            }
                          } finally {
                            proxy.close()
                          }
                        }
                      }
                    runCatching {
                      provider.unbindAll()
                      provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                      )
                    }
                  },
                  ContextCompat.getMainExecutor(ctx),
                )
                previewView
              },
            )
            Surface(
              modifier = Modifier.size(190.dp),
              shape = RoundedCornerShape(18.dp),
              color = FlashCyan.copy(alpha = 0.04f),
              border = BorderStroke(2.dp, FlashCyan),
            ) {}
          }
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Point camera at the receiver's FlashShare QR code.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          Column(
            modifier = Modifier.padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.CameraAlt,
              contentDescription = "Camera permission required",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(44.dp),
            )
            Text(
              text = "Camera permission is needed to scan QR pairing codes. You can also enter the IP address manually.",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier.testTag("grant_camera_button"),
            ) {
              Text("Allow Camera")
            }
          }
        }

        Spacer(modifier = Modifier.height(6.dp))
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  }
}
