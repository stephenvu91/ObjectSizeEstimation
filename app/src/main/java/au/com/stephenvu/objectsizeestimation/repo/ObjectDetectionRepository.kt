package au.com.stephenvu.objectsizeestimation.repo

import androidx.camera.core.ImageProxy
import au.com.stephenvu.core_ml.models.ObjectSizeEstimationObject
import au.com.stephenvu.objectsizeestimation.data.ObjectDetectionLocalDatasource
import javax.inject.Inject

class ObjectDetectionRepository @Inject constructor(
    private val localDatasource: ObjectDetectionLocalDatasource
) {
    fun detect(imageProxy: ImageProxy, threshold: Float): List<ObjectSizeEstimationObject> {
        return localDatasource.detect(imageProxy, threshold)
    }

    fun close() {
        return localDatasource.close()
    }
}