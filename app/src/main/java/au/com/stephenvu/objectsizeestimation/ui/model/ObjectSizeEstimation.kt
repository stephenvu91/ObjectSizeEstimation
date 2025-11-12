package au.com.stephenvu.objectsizeestimation.ui.model

import au.com.stephenvu.core_ml.models.ObjectSizeEstimationObject

data class ObjectSizeEstimationState(
    val objectSizeEstimationObjects: List<ObjectSizeEstimationObject> = emptyList(),
    val isProcessing: Boolean = false,
    val error: String? = null,
    val cameraPermissionGranted: Boolean = false,
)
