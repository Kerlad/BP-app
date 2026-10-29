package ru.bpdiary.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.model.ThemeMode
import ru.bpdiary.data.reminders.NotificationReminderNotifier
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

    private val shell: ShellViewModel by viewModels()

    /** Set by the reminder notification; BpApp opens Entry and calls back. */
    private var openEntryNew by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { shell.appearance.value == null }
        // After a rotation or process death the intent is the old one: only a fresh launch may open Entry.
        if (savedInstanceState == null) openEntryNew = intent.requestsNewEntry()
        // Direction stays SAGE (D-01).
        setContent {
            val appearance by shell.appearance.collectAsStateWithLifecycle()
            val current = appearance ?: return@setContent
            val dark =
                when (current.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
            BoostedFontScale(current.fontScaleBoost) {
                BpTheme(dark = dark) {
                    BpApp(shell, openEntryNew = openEntryNew, onOpenEntryHandled = ::onOpenEntryHandled)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.requestsNewEntry()) openEntryNew = true
    }

    private fun onOpenEntryHandled() {
        openEntryNew = false
        intent.removeExtra(NotificationReminderNotifier.EXTRA_OPEN)
    }

    private companion object {
        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
