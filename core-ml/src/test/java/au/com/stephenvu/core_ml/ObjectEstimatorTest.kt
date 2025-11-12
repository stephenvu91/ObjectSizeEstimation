package au.com.stephenvu.core_ml

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector as TFLiteObjectDetector

class ObjectEstimatorTest {

    private val mockObjectDetector: ObjectDetector = mockk(relaxed = true)
    private val mockTFLiteDetector: TFLiteObjectDetector = mockk(relaxed = true)
    private val mockImageProxy: ImageProxy = mockk(relaxed = true)
    private val mockImageInfo: ImageInfo = mockk(relaxed = true)

    private lateinit var estimator: ObjectEstimator

    @BeforeEach
    fun setup() {
        every { mockObjectDetector.getDetector() } returns mockTFLiteDetector
        every { mockImageProxy.imageInfo } returns mockImageInfo
        every { mockImageInfo.rotationDegrees } returns 0
        estimator = ObjectEstimator(mockObjectDetector)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `detectAndEstimateObjects returns mapped results`() {
        // Arrange
        val fakeBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        mockkStatic("androidx.camera.core.ImageProxyExtKt")
        every { mockImageProxy.toBitmap() } returns fakeBitmap

        val fakeCategory = Category("person", 0.9f)
        val fakeDetection = mockk<Detection>()
        every { fakeDetection.categories } returns listOf(fakeCategory)
        every { fakeDetection.boundingBox } returns RectF(100f, 100f, 300f, 500f)

        every { mockTFLiteDetector.detect(any<TensorImage>()) } returns listOf(fakeDetection)

        // Act
        val results = estimator.detectAndEstimateObjects(mockImageProxy)

        // Assert
        assertEquals(1, results.size)
        val obj = results.first()
        assertEquals("person", obj.label)
        assertTrue(obj.confidence > 0.8f)
        assertEquals(640, obj.imageWidth)
        assertEquals(480, obj.imageHeight)

        verify { mockTFLiteDetector.detect(any<TensorImage>()) }
        verify { mockImageProxy.close() }
    }

    @Test
    fun `returns empty list if detector is null`() {
        every { mockObjectDetector.getDetector() } returns null

        val results = estimator.detectAndEstimateObjects(mockImageProxy)
        assertTrue(results.isEmpty())
        verify { mockImageProxy.close() }
    }
}