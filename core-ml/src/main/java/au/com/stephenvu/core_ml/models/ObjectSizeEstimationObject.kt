package au.com.stephenvu.core_ml.models

import android.graphics.RectF

data class ObjectSizeEstimationObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF,
    val estimatedSize: ObjectSize? = null,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val rotation: Int = 0
)