package au.com.stephenvu.objectsizeestimation.ui

import android.os.Build
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.com.stephenvu.core_ml.ObjectDetector
import au.com.stephenvu.objectsizeestimation.ui.model.ObjectSizeEstimationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections.emptyList
import javax.inject.Inject

/**
 * Main screen ViewModel that utilises MVI pattern and State Flow for UI updating
 */
@HiltViewModel
class ObjectSizeEstimationViewModel @Inject constructor(
    private val detector: ObjectDetector
) : ViewModel() {

    private val _state = MutableStateFlow(ObjectSizeEstimationState())
    val state: StateFlow<ObjectSizeEstimationState> = _state.asStateFlow()

    fun reduce(intent: ObjectSizeEstimationAction) {
        when (intent) {
            is ObjectSizeEstimationAction.StartCamera -> startCamera()
            is ObjectSizeEstimationAction.StopCamera -> stopCamera()
            is ObjectSizeEstimationAction.ProcessFrame -> processFrame(intent.imageProxy)
            is ObjectSizeEstimationAction.UpdatePermission -> updatePermission(intent.granted)
            is ObjectSizeEstimationAction.ClearError -> clearError()
        }
    }

    private fun startCamera() {
        _state.update { it.copy(isProcessing = true) }
    }

    private fun stopCamera() {
        _state.update {
            it.copy(
                isProcessing = false,
                objectSizeEstimationObjects = emptyList(),
            )
        }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val threshold = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    0.6f
                } else {
                    0.5f
                }

                val objects = detector.detectObjects(imageProxy, confidenceThreshold = threshold)
                _state.update {
                    it.copy(
                        objectSizeEstimationObjects = objects,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(error = "Detection error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updatePermission(granted: Boolean) {
        _state.update { it.copy(cameraPermissionGranted = granted) }
    }

    private fun clearError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        detector.close()
    }
}
