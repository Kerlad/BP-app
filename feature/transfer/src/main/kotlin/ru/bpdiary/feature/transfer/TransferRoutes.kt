package ru.bpdiary.feature.transfer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

private const val CSV_MIME = "text/csv"
private const val TEXT_MIME = "text/plain"
private const val REPORT_FILE_NAME = "davlenie_import_report.txt"

// Many providers give a CSV as a generic type, so the match-all type closes the list (docs/13 §13.4).
private val OPEN_TYPES =
    arrayOf(CSV_MIME, "text/comma-separated-values", TEXT_MIME, "application/vnd.ms-excel", "*/*")

@Serializable
data object ExportRoute

@Serializable
data object ImportRoute

fun NavGraphBuilder.exportScreen(onClose: () -> Unit) {
    composable<ExportRoute> { ExportDestination(onClose) }
}

fun NavGraphBuilder.importScreen(onClose: () -> Unit) {
    composable<ImportRoute> { ImportDestination(onClose) }
}

@Composable
private fun ExportDestination(onClose: () -> Unit) {
    val viewModel = hiltViewModel<ExportViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val csvSaver =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mime)) { uri ->
            if (uri != null) viewModel.onEvent(ExportEvent.SaveTargetChosen(uri.toString()))
        }
    val pdfSaver =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mime)) { uri ->
            if (uri != null) viewModel.onEvent(ExportEvent.SaveTargetChosen(uri.toString()))
        }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is ExportEffect.Share -> context.shareFile(effect.uri, effect.mime)
                    is ExportEffect.Print -> context.printReport(effect.job)
                }
            }
        }
    }
    ExportScreen(state, onEvent = { event ->
        when (event) {
            ExportEvent.BackClicked -> {
                onClose()
            }

            ExportEvent.SaveClicked -> {
                if (state.format ==
                    ExportFormat.PDF
                ) {
                    pdfSaver.launch(state.fileName)
                } else {
                    csvSaver.launch(state.fileName)
                }
            }

            else -> {
                viewModel.onEvent(event)
            }
        }
    })
}

private fun Context.shareFile(
    uri: String,
    mime: String,
) {
    val send =
        Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    startActivity(Intent.createChooser(send, null))
}

/** Hands the report to the system print dialog, which also offers «Сохранить как PDF». */
private fun Context.printReport(job: PrintJob) {
    // PrintManager prints only from an activity itself, not from a wrapper of it.
    val activity = findActivity() ?: return
    val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val a4 = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
    manager.print(job.name, PdfPrintAdapter(job), a4)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
private fun ImportDestination(onClose: () -> Unit) {
    val viewModel = hiltViewModel<ImportViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val close by rememberUpdatedState(onClose)
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onEvent(ImportEvent.FileChosen(uri.toString()))
        }
    val reportSaver =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TEXT_MIME)) { uri ->
            if (uri != null) viewModel.onEvent(ImportEvent.ReportTargetChosen(uri.toString()))
        }
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    ImportEffect.Close -> close()
                }
            }
        }
    }
    // The system back follows the steps as the arrow does; while the import runs it does nothing.
    BackHandler { viewModel.onEvent(ImportEvent.BackClicked) }
    ImportScreen(state, onEvent = { event ->
        when (event) {
            ImportEvent.PickClicked -> picker.launch(OPEN_TYPES)
            ImportEvent.SaveReportClicked -> reportSaver.launch(REPORT_FILE_NAME)
            else -> viewModel.onEvent(event)
        }
    })
}
