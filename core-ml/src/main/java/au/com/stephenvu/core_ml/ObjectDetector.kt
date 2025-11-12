package au.com.stephenvu.core_ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.task.core.BaseOptions
import javax.inject.Inject
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFLiteObjectDetector

class ObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var detector: TFLiteObjectDetector? = null

    init {
        loadModel()
    }

    fun getDetector() = detector

    private fun loadModel() {
        try {
            val baseOptions = BaseOptions.builder()
                .setNumThreads(4)
                .build()

            val options = TFLiteObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(10)
                .setScoreThreshold(0.5f)
                .build()

            detector = TFLiteObjectDetector.createFromFileAndOptions(
                context,
                "ssd_mobilenet_v1.tflite",
                options
            )

            Log.d("ObjectDetector", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error loading model", e)
        }
    }

    fun close() {
        detector?.close()
        detector = null

        Log.d("ObjectDetector", "Resources cleaned up")
    }
}