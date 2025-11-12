package au.com.stephenvu.core_ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageProxy
import au.com.stephenvu.core_ml.models.ObjectSize
import au.com.stephenvu.core_ml.models.ObjectSizeEstimationObject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.support.image.TensorImage
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

    fun detectObjects(
        imageProxy: ImageProxy,
        confidenceThreshold: Float = 0.5f
    ): List<ObjectSizeEstimationObject> {
        if (detector == null) {
            imageProxy.close()
            return emptyList()
        }

        val bitmap: Bitmap?
        val rotation: Int

        val startTime = System.currentTimeMillis()

        try {
            rotation = imageProxy.imageInfo.rotationDegrees

            // Use RenderScript if available, fallback to manual conversion
            bitmap = imageProxy.toBitmap()

            val conversionTime = System.currentTimeMillis() - startTime
            Log.d("ObjectDetector", "Conversion took ${conversionTime}ms")

        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error converting image", e)
            imageProxy.close()
            return emptyList()
        } finally {
            imageProxy.close()
        }

        return try {
            val inferenceStart = System.currentTimeMillis()

            val tensorImage = TensorImage.fromBitmap(bitmap)
            val results = detector?.detect(tensorImage) ?: emptyList()

            val inferenceTime = System.currentTimeMillis() - inferenceStart
            Log.d(
                "ObjectDetector",
                "Inference took ${inferenceTime}ms, found ${results.size} objects"
            )

            results
                .filter { (it.categories.firstOrNull()?.score ?: 0f) >= confidenceThreshold }
                .map { detection ->
                    val category = detection.categories.firstOrNull()
                    val label = category?.label ?: "Unknown"
                    val confidence = category?.score ?: 0f

                    ObjectSizeEstimationObject(
                        label = label,
                        confidence = confidence,
                        boundingBox = detection.boundingBox,
                        estimatedSize = estimateObjectSize(
                            detection.boundingBox,
                            label,
                            bitmap.width,
                        ),
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                        rotation = rotation
                    )
                }
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error during detection", e)
            emptyList()
        }
        // Don't recycle reusable bitmap!
    }

    private fun estimateObjectSize(
        box: RectF,
        label: String,
        imageWidth: Int,
    ): ObjectSize {
        val knownSizes = mapOf(
            "person" to Pair(45f, 170f),
            "car" to Pair(180f, 150f),
            "motorcycle" to Pair(80f, 120f),
            "bicycle" to Pair(60f, 110f),
            "bus" to Pair(250f, 300f),
            "truck" to Pair(250f, 300f),
            "chair" to Pair(50f, 90f),
            "couch" to Pair(180f, 80f),
            "laptop" to Pair(35f, 23f),
            "cell phone" to Pair(7f, 15f),
            "bottle" to Pair(7f, 25f),
            "cup" to Pair(8f, 10f),
            "book" to Pair(15f, 23f),
            "dog" to Pair(40f, 60f),
            "cat" to Pair(25f, 25f)
        )

        val (realWidth, realHeight) = knownSizes[label] ?: Pair(30f, 30f)
        val pixelWidth = box.width()
        val pixelHeight = box.height()

        if (pixelWidth <= 0 || pixelHeight <= 0) {
            return ObjectSize(realWidth, realHeight, 1f)
        }

        val focalLengthPixels = imageWidth * 0.87f
        val distanceFromHeight = (realHeight * focalLengthPixels) / pixelHeight / 100f
        val distanceFromWidth = (realWidth * focalLengthPixels) / pixelWidth / 100f
        val distance = (distanceFromHeight * 0.7f + distanceFromWidth * 0.3f).coerceIn(0.2f, 15f)

        val actualWidth = (pixelWidth * distance * 100f) / focalLengthPixels
        val actualHeight = (pixelHeight * distance * 100f) / focalLengthPixels

        return ObjectSize(actualWidth, actualHeight, distance)
    }

    fun close() {
        detector?.close()
        detector = null

        Log.d("ObjectDetector", "Resources cleaned up")
    }
}