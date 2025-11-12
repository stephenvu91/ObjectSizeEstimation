package au.com.stephenvu.objectsizeestimation.data

import androidx.camera.core.ImageProxy
import au.com.stephenvu.core_ml.ObjectEstimator
import au.com.stephenvu.core_ml.models.ObjectSizeEstimationObject
import javax.inject.Inject

class ObjectDetectionLocalDatasource @Inject constructor(
    private val estimator: ObjectEstimator
){
    fun detect(imageProxy: ImageProxy, threshold: Float): List<ObjectSizeEstimationObject> {
        return estimator.detectAndEstimateObjects(imageProxy, confidenceThreshold = threshold)
    }

    fun close() {
        estimator.close()
    }
}