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
import ru.bpdiary.core.ui.BpCard
import ru.bpdiary.core.ui.BpTopBar

/**
 * «О приложении» (docs/05 §5.7): the full disclaimer, the urgent-care line, the note about a new phone, the version
 * and the privacy policy. The policy line is left out while its address is not known.
 */
@Composable
fun AboutScreen(
    version: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BpTheme.colors
    val privacyUrl = stringResource(R.string.about_privacy_url)
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
            title = stringResource(R.string.about_title),
            navigationIcon = BpIcon.ArrowBack,
            navigationDescription = stringResource(R.string.settings_back_description),
            onNavigate = onBack,
        )
        Column(modifier = Modifier.padding(start = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BpCard {
                Text(stringResource(R.string.about_disclaimer), style = BpTheme.typography.body, color = colors.ink)
            }
            Text(
                text = stringResource(R.string.about_urgent),
                style = BpTheme.typography.body.copy(fontWeight = FontWeight.W700),
                color = colors.ink,
            )
            Text(stringResource(R.string.about_new_phone), style = BpTheme.typography.body, color = colors.mut)
            Text(
                text = stringResource(R.string.about_version, version),
                style = BpTheme.typography.secondary,
                color = colors.mut,
            )
            if (privacyUrl.isNotBlank()) {
                Text(
                    text = stringResource(R.string.about_privacy) + ": " + privacyUrl,
                    style = BpTheme.typography.secondary,
                    color = colors.ac,
                )
            }
        }
    }
}
