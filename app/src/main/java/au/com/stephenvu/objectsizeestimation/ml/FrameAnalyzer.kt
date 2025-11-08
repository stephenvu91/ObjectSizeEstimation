package au.com.stephenvu.objectsizeestimation.ml

import android.os.Build
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Image Analysis for each frame with a throttle of 100-150ms
 */
class FrameAnalyzer(
    private val onFrameAnalyzed: (ImageProxy) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalyzedTimestamp = 0L
    private val throttleMs = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) 150L else 100L

    override fun analyze(imageProxy: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastAnalyzedTimestamp < throttleMs) {
            imageProxy.close()
            return
        }

        lastAnalyzedTimestamp = currentTimestamp

        onFrameAnalyzed(imageProxy)
    }
}