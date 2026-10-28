package ru.bpdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.bpdiary.core.designsystem.BpTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Theme mode and direction from settings are wired in Step 10.3; SAGE follows the system theme (D-01).
        setContent { BpTheme { BpApp() } }
    }
}
