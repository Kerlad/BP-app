package ru.bpdiary.feature.entry

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.bpdiary.app.feature.entry.R
import ru.bpdiary.core.designsystem.BpTheme

@Composable
internal fun DiscardDialog(onEvent: (EntryEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.entry_discard_title)) },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryEvent.DiscardConfirmed) }) {
                Text(stringResource(R.string.entry_discard_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryEvent.DialogDismissed) }) {
                Text(stringResource(R.string.entry_discard_continue))
            }
        },
    )
}

@Composable
internal fun DeleteDialog(onEvent: (EntryEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EntryEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.entry_delete_title)) },
        confirmButton = {
            TextButton(onClick = { onEvent(EntryEvent.DeleteConfirmed) }) {
                Text(stringResource(R.string.entry_delete_confirm), color = BpTheme.colors.hi)
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(EntryEvent.DialogDismissed) }) {
                Text(stringResource(R.string.entry_delete_cancel))
            }
        },
    )
}
