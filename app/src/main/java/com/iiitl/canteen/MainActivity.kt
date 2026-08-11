package com.iiitl.canteen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.iiitl.canteen.ui.navigation.QueuelessNavGraph
import com.iiitl.canteen.ui.theme.QueuelessTheme

class MainActivity : ComponentActivity() {

    // AppContainer holds singleton Firebase instances & repositories.
    private val appContainer = AppContainer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QueuelessTheme {
                QueuelessNavGraph(appContainer = appContainer)
            }
        }
    }
}