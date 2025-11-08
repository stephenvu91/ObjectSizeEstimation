package au.com.stephenvu.objectsizeestimation.ui

import android.graphics.RectF
import androidx.camera.core.ImageProxy

data class ObjectSizeEstimationState(
    val objectSizeEstimationObjects: List<ObjectSizeEstimationObject> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val cameraPermissionGranted: Boolean = false,
)

data class ObjectSizeEstimationObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val estimatedSize: ObjectSize? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotation: Int = 0
)

data class ObjectSize(
    val widthCm: Float,
    val heightCm: Float,
    val distanceMeters: Float
)

sealed interface ObjectSizeEstimationAction {
    data object StartCamera : ObjectSizeEstimationAction
    data object StopCamera : ObjectSizeEstimationAction
    data class ProcessFrame(val imageProxy: ImageProxy) : ObjectSizeEstimationAction
    data class UpdatePermission(val granted: Boolean) : ObjectSizeEstimationAction
    data object ClearError : ObjectSizeEstimationAction
}