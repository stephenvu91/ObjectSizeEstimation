package au.com.stephenvu.objectsizeestimation.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.util.Log
import androidx.camera.core.ImageProxy
import au.com.stephenvu.objectsizeestimation.ui.ObjectSize
import au.com.stephenvu.objectsizeestimation.ui.ObjectSizeEstimationObject
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFLiteObjectDetector
import androidx.core.graphics.createBitmap

class ObjectDetector(private val context: Context) {
    private var detector: TFLiteObjectDetector? = null

    // RenderScript objects
    private var renderScript: RenderScript? = null
    private var scriptIntrinsicYuvToRGB: ScriptIntrinsicYuvToRGB? = null

    // Reusable allocations for efficiency
    private var inputAllocation: Allocation? = null
    private var outputAllocation: Allocation? = null
    private var reusableBitmap: Bitmap? = null

    // Track last image size to reuse allocations
    private var lastWidth = 0
    private var lastHeight = 0

    init {
        loadModel()
        initRenderScript()
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

    private fun initRenderScript() {
        try {
            renderScript = RenderScript.create(context)
            scriptIntrinsicYuvToRGB = ScriptIntrinsicYuvToRGB.create(
                renderScript,
                Element.U8_4(renderScript)
            )
            Log.d("ObjectDetector", "RenderScript initialized successfully")
        } catch (e: Exception) {
            Log.e("ObjectDetector", "Failed to initialize RenderScript", e)
            renderScript = null
            scriptIntrinsicYuvToRGB = null
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
            bitmap = if (renderScript != null && scriptIntrinsicYuvToRGB != null) {
                imageProxyToBitmapRenderScript(imageProxy)
            } else {
                imageProxyToBitmapManual(imageProxy)
            }

            val conversionTime = System.currentTimeMillis() - startTime
            Log.d("ObjectDetector", "Conversion took ${conversionTime}ms")

        } catch (e: Exception) {
            Log.e("ObjectDetector", "Error converting image", e)
            imageProxy.close()
            return emptyList()
        } finally {
            imageProxy.close()
        }

        if (bitmap == null) return emptyList()

        return try {
            val inferenceStart = System.currentTimeMillis()

            val tensorImage = TensorImage.fromBitmap(bitmap)
            val results = detector?.detect(tensorImage) ?: emptyList()

            val inferenceTime = System.currentTimeMillis() - inferenceStart
            Log.d("ObjectDetector", "Inference took ${inferenceTime}ms, found ${results.size} objects")

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

    /**
     * Convert ImageProxy to Bitmap using RenderScript (GPU-accelerated)
     * This is 5-10x faster than manual conversion
     */
    private fun imageProxyToBitmapRenderScript(imageProxy: ImageProxy): Bitmap? {
        val rs = renderScript ?: return null
        val script = scriptIntrinsicYuvToRGB ?: return null

        val width = imageProxy.width
        val height = imageProxy.height

        try {
            // Check if we need to recreate allocations (size changed)
            if (width != lastWidth || height != lastHeight) {
                Log.d("ObjectDetector", "Image size changed, recreating allocations")
                cleanupAllocations()
                lastWidth = width
                lastHeight = height

                // Create new bitmap
                reusableBitmap = createBitmap(width, height)
            }

            val bitmap = reusableBitmap ?: return null

            // Get YUV data from ImageProxy
            val yPlane = imageProxy.planes[0]
            val uPlane = imageProxy.planes[1]
            val vPlane = imageProxy.planes[2]

            val yBuffer = yPlane.buffer
            val uBuffer = uPlane.buffer
            val vBuffer = vPlane.buffer

            // Calculate total size needed for YUV data
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            // Create NV21 format data (Y followed by interleaved V,U)
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y plane
            yBuffer.get(nv21, 0, ySize)

            // For NV21, we need V and U interleaved
            // But ImageProxy gives us separate U and V planes
            val uvPixelStride = uPlane.pixelStride
            val uvRowStride = uPlane.rowStride

            if (uvPixelStride == 1) {
                // Planar format - need to interleave manually
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
            } else {
                // Semi-planar format - already interleaved (might be NV12, need to swap)
                // Copy V plane
                var uvIndex = ySize
                vBuffer.rewind()
                for (i in 0 until vSize) {
                    if (uvIndex < nv21.size) {
                        nv21[uvIndex] = vBuffer.get()
                        uvIndex += 1
                    }
                }

                // Copy U plane
                uvIndex = ySize
                uBuffer.rewind()
                for (i in 0 until uSize) {
                    if (uvIndex + 1 < nv21.size) {
                        nv21[uvIndex + 1] = uBuffer.get()
                        uvIndex += 2
                    }
                }
            }

            // Create or reuse input allocation
            if (inputAllocation == null) {
                val yuvType = Type.Builder(rs, Element.U8(rs))
                    .setX(nv21.size)
                    .create()
                inputAllocation = Allocation.createTyped(
                    rs,
                    yuvType,
                    Allocation.USAGE_SCRIPT
                )
            }

            // Create or reuse output allocation
            if (outputAllocation == null) {
                outputAllocation = Allocation.createFromBitmap(
                    rs,
                    bitmap,
                    Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT
                )
            }

            // Copy YUV data to input allocation
            inputAllocation?.copyFrom(nv21)

            // Set input and execute conversion
            script.setInput(inputAllocation)
            script.forEach(outputAllocation)

            // Copy result to bitmap
            outputAllocation?.copyTo(bitmap)

            return bitmap

        } catch (e: Exception) {
            Log.e("ObjectDetector", "RenderScript conversion failed, falling back", e)
            return imageProxyToBitmapManual(imageProxy)
        }
    }

    /**
     * Manual YUV to RGB conversion (fallback when RenderScript unavailable)
     */
    private fun imageProxyToBitmapManual(imageProxy: ImageProxy): Bitmap {
        val planes = imageProxy.planes
        val width = imageProxy.width
        val height = imageProxy.height

        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val yBytes = ByteArray(ySize)
        val uBytes = ByteArray(uSize)
        val vBytes = ByteArray(vSize)

        yBuffer.get(yBytes)
        uBuffer.get(uBytes)
        vBuffer.get(vBytes)

        val bitmap = createBitmap(width, height)
        val pixels = IntArray(width * height)

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = y * yRowStride + x * yPixelStride
                val yValue = if (yIndex < yBytes.size) {
                    yBytes[yIndex].toInt() and 0xFF
                } else 0

                val uvY = y / 2
                val uvX = x / 2
                val uvIndex = uvY * uvRowStride + uvX * uvPixelStride

                val uValue = if (uvIndex < uBytes.size) {
                    uBytes[uvIndex].toInt() and 0xFF
                } else 128

                val vValue = if (uvIndex < vBytes.size) {
                    vBytes[uvIndex].toInt() and 0xFF
                } else 128

                val yAdj = yValue - 16
                val uAdj = uValue - 128
                val vAdj = vValue - 128

                val r = (1.164f * yAdj + 1.596f * vAdj).toInt().coerceIn(0, 255)
                val g = (1.164f * yAdj - 0.392f * uAdj - 0.813f * vAdj).toInt().coerceIn(0, 255)
                val b = (1.164f * yAdj + 2.017f * uAdj).toInt().coerceIn(0, 255)

                pixels[y * width + x] = 0xFF shl 24 or (r shl 16) or (g shl 8) or b
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
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

    private fun cleanupAllocations() {
        inputAllocation?.destroy()
        inputAllocation = null

        outputAllocation?.destroy()
        outputAllocation = null

        reusableBitmap?.recycle()
        reusableBitmap = null
    }

    fun close() {
        detector?.close()
        detector = null

        cleanupAllocations()

        scriptIntrinsicYuvToRGB?.destroy()
        scriptIntrinsicYuvToRGB = null

        renderScript?.destroy()
        renderScript = null

        Log.d("ObjectDetector", "Resources cleaned up")
    }
}