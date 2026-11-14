package ru.bpdiary.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.bpdiary.app.feature.settings.R
import ru.bpdiary.core.designsystem.BpIcon
import ru.bpdiary.core.designsystem.BpTheme
import ru.bpdiary.core.ui.BpTopBar

/** Library groups used at runtime (names are proper nouns, not translated). */
internal val LIBRARY_GROUPS: List<String> =
    listOf(
        "AndroidX",
        "Kotlin",
        "Kotlin Coroutines",
        "kotlinx.serialization",
        "Hilt/Dagger",
        "Room",
        "WorkManager",
        "Paging",
        "DataStore",
        "Material Symbols",
    )

/**
 * «Лицензии» (docs/05 §5.7): the font licence, the list of libraries and the Apache 2.0 text. The texts come from
 * the assets, [LicensesDestination] reads them.
 */
@Composable
fun LicensesScreen(
    fontLicense: String,
    apacheLicense: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.bg)
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 10.dp, end = 20.dp, bottom = 24.dp),
    ) {
        BpTopBar(
            title = stringResource(R.string.licenses_title),
            navigationIcon = BpIcon.ArrowBack,
            navigationDescription = stringResource(R.string.settings_back_description),
            onNavigate = onBack,
        )
        Column(modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            LicenseHeading(stringResource(R.string.licenses_font_heading))
            Text(fontLicense, style = BpTheme.typography.secondary, color = colors.ink)
            LicenseHeading(stringResource(R.string.licenses_libraries_heading))
            Text(LIBRARY_GROUPS.joinToString("\n"), style = BpTheme.typography.body, color = colors.ink)
            Text(apacheLicense, style = BpTheme.typography.secondary, color = colors.ink)
        }
    }
}

@Composable
private fun LicenseHeading(text: String) {
    Text(text, style = BpTheme.typography.body.copy(fontWeight = FontWeight.W700), color = BpTheme.colors.ink)
}
