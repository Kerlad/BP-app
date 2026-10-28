package ru.bpdiary.app

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.ThemeMode
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
        // Direction stays SAGE (D-01); the splash that hides the first theme read comes in Step 10.1.
        setContent {
            val shell: ShellViewModel = hiltViewModel()
            val mode by shell.themeMode.collectAsStateWithLifecycle()
            val dark =
                when (mode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            BpTheme(dark = dark) { BpApp(shell) }
        }
    }

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
