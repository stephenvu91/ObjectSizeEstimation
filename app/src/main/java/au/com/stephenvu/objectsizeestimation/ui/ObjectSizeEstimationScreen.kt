package au.com.stephenvu.objectsizeestimation.ui

import android.Manifest
import android.app.Activity
import android.graphics.Typeface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import au.com.stephenvu.objectsizeestimation.ml.FrameAnalyzer
import au.com.stephenvu.objectsizeestimation.ml.ObjectDetector
import java.util.concurrent.Executors

/**
 * Main screen of the app
 */
@Composable
fun ObjectSizeEstimationScreen() {
    val context = LocalContext.current
    val detector = remember { ObjectDetector(context) }
    val viewModel: ObjectSizeEstimationViewModel = viewModel(
        factory = DetectionViewModelFactory(detector)
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.reduce(ObjectSizeEstimationAction.UpdatePermission(isGranted))
        if (isGranted) {
            viewModel.reduce(ObjectSizeEstimationAction.StartCamera)
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.cameraPermissionGranted) {
            DisposableEffect(Unit) {
                val window = (context as? Activity)?.window
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose {
                    window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFrameAnalyzed = { imageProxy ->
                    viewModel.reduce(ObjectSizeEstimationAction.ProcessFrame(imageProxy))
                }
            )

            DetectionOverlay(
                objectSizeEstimationObjects = state.objectSizeEstimationObjects,
                modifier = Modifier.fillMaxSize()
            )

            ObjectDetailsPanel(
                objectSizeEstimationObjects = state.objectSizeEstimationObjects,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        } else {
            PermissionDeniedScreen()
        }

        state.error?.let { error ->
            ErrorSnackbar(
                message = error,
                onDismiss = { viewModel.reduce(ObjectSizeEstimationAction.ClearError) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

/**
 * Camera X Preview that takes in FrameAnalyzer and return the ImageProxy for processing
 */
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onFrameAnalyzed: (ImageProxy) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, FrameAnalyzer(onFrameAnalyzed))
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

/**
 * Object detection overlay that displays a rectangle of the object
 */
@Composable
fun DetectionOverlay(
    objectSizeEstimationObjects: List<ObjectSizeEstimationObject>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        if (objectSizeEstimationObjects.isEmpty() || canvasWidth == 0f || canvasHeight == 0f) return@Canvas

        val imageWidth = objectSizeEstimationObjects.firstOrNull()?.imageWidth?.toFloat() ?: return@Canvas
        val imageHeight = objectSizeEstimationObjects.firstOrNull()?.imageHeight?.toFloat() ?: return@Canvas

        if (imageWidth == 0f || imageHeight == 0f) return@Canvas

        val imageAspect = imageWidth / imageHeight
        val canvasAspect = canvasWidth / canvasHeight

        val scaleX: Float
        val scaleY: Float
        val offsetX: Float
        val offsetY: Float

        if (imageAspect > canvasAspect) {
            scaleX = canvasWidth / imageWidth
            scaleY = scaleX
            offsetX = 0f
            offsetY = (canvasHeight - imageHeight * scaleY) / 2f
        } else {
            scaleY = canvasHeight / imageHeight
            scaleX = scaleY
            offsetX = (canvasWidth - imageWidth * scaleX) / 2f
            offsetY = 0f
        }

        android.util.Log.d("DetectionOverlay", "Canvas: ${canvasWidth}x${canvasHeight}, Image: ${imageWidth}x${imageHeight}")
        android.util.Log.d("DetectionOverlay", "Scale: ${scaleX}x${scaleY}, Offset: ${offsetX},${offsetY}")

        objectSizeEstimationObjects.forEach { obj ->
            val box = obj.boundingBox

            // Rescale the bounding box because the input and output of model image is 300x300
            val left = box.left * scaleX + offsetX
            val top = box.top * scaleY + offsetY
            val right = box.right * scaleX + offsetX
            val bottom = box.bottom * scaleY + offsetY

            android.util.Log.d("DetectionOverlay", "${obj.label}: [${box.left},${box.top},${box.right},${box.bottom}] -> [${left},${top},${right},${bottom}]")

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 4f)
            )

            val textPaint = Paint().asFrameworkPaint().apply {
                color = android.graphics.Color.WHITE
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val labelText = "${obj.label} ${(obj.confidence * 100).toInt()}%"
            val textWidth = textPaint.measureText(labelText)

            drawRect(
                color = Color.Green,
                topLeft = Offset(left, top - 45f),
                size = Size(textWidth + 16f, 45f)
            )

            drawContext.canvas.nativeCanvas.drawText(
                labelText,
                left + 8f,
                top - 12f,
                textPaint
            )

            obj.estimatedSize?.let { size ->
                val sizeText = "%.0f×%.0fcm (%.1fm)".format(
                    size.widthCm,
                    size.heightCm,
                    size.distanceMeters
                )

                textPaint.color = android.graphics.Color.WHITE
                textPaint.textSize = 32f

                val sizeTextWidth = textPaint.measureText(sizeText)

                drawRect(
                    color = Color.Blue,
                    topLeft = Offset(left, bottom + 5f),
                    size = Size(sizeTextWidth + 16f, 40f)
                )

                drawContext.canvas.nativeCanvas.drawText(
                    sizeText,
                    left + 8f,
                    bottom + 35f,
                    textPaint
                )
            }
        }
    }
}

/**
 * Object information details
 */
@Composable
fun ObjectDetailsPanel(
    objectSizeEstimationObjects: List<ObjectSizeEstimationObject>,
    modifier: Modifier = Modifier
) {
    if (objectSizeEstimationObjects.isEmpty()) return

    Card(
        modifier = modifier.widthIn(max = 280.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "Detected Objects",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            objectSizeEstimationObjects.take(5).forEach { obj ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = obj.label,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    obj.estimatedSize?.let { size ->
                        Text(
                            text = "Size: %.0f × %.0f cm".format(size.widthCm, size.heightCm),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Distance: %.1f m".format(size.distanceMeters),
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (obj != objectSizeEstimationObjects.last()) {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Please grant camera permission to use object detection",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ErrorSnackbar(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Snackbar(
        modifier = modifier,
        action = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    ) {
        Text(message)
    }
}