package ru.bpdiary.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import ru.bpdiary.core.designsystem.BpTheme
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * The UI is Russian only, but plural forms («5 измерений») follow the locale of the configuration:
     * on an English phone they would come out wrong. Language splits are off in the bundle for the same reason.
     */
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(RUSSIAN) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Theme mode and direction from settings are wired in Step 10.3; SAGE follows the system theme (D-01).
        setContent { BpTheme { BpApp() } }
    }

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
