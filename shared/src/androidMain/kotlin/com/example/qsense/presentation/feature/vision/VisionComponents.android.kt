package com.example.qsense.presentation.feature.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.ByteArrayOutputStream

private const val TAG = "QSenseVision"
private const val MAX_SIDE = 640

@Composable
actual fun CameraCapture(onCaptured: (imageB64: String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Camera permission is required to scan a part.")
        }
        return
    }

    val controller = remember {
        LifecycleCameraController(context).apply { bindToLifecycle(lifecycleOwner) }
    }

    Box(modifier) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> PreviewView(ctx).also { it.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )
        Button(
            onClick = {
                controller.takePicture(
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val b64 = image.use { encodeDownscaledJpeg(it) }
                            onCaptured(b64)
                        }

                        override fun onError(exc: ImageCaptureException) {
                            Log.e(TAG, "capture failed", exc)
                        }
                    },
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        ) { Text("Capture") }
    }
}

private fun encodeDownscaledJpeg(image: ImageProxy): String {
    val src = image.toBitmap()
    val rotation = image.imageInfo.rotationDegrees
    val upright = if (rotation != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    } else {
        src
    }
    val longest = maxOf(upright.width, upright.height)
    val scaled = if (longest > MAX_SIDE) {
        val factor = MAX_SIDE.toFloat() / longest
        Bitmap.createScaledBitmap(
            upright,
            (upright.width * factor).toInt(),
            (upright.height * factor).toInt(),
            true,
        )
    } else {
        upright
    }
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

@Composable
actual fun AnnotatedImage(imageB64: String, modifier: Modifier) {
    val bitmap = remember(imageB64) {
        runCatching {
            val bytes = Base64.decode(imageB64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Annotated detection result",
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Could not decode image") }
    }
}
