package au.com.stephenvu.objectsizeestimation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import au.com.stephenvu.objectsizeestimation.ui.theme.ObjectSizeEstimationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ObjectSizeEstimationTheme {
                ObjectSizeEstimationScreen()
            }
        }
    }
}