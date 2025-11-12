package au.com.stephenvu.objectsizeestimation.ui

import androidx.camera.core.ImageProxy

sealed interface ObjectSizeEstimationAction {
    data object StartCamera : ObjectSizeEstimationAction
    data object StopCamera : ObjectSizeEstimationAction
    data class ProcessFrame(val imageProxy: ImageProxy) : ObjectSizeEstimationAction
    data class UpdatePermission(val granted: Boolean) : ObjectSizeEstimationAction
    data object ClearError : ObjectSizeEstimationAction
}
