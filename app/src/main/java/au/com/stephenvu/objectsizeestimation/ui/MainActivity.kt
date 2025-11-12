package au.com.stephenvu.objectsizeestimation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import au.com.stephenvu.objectsizeestimation.ui.theme.ObjectSizeEstimationTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    val viewModel: ObjectSizeEstimationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObjectSizeEstimationTheme {
                ObjectSizeEstimationScreen(viewModel)
            }
        }
    }
}