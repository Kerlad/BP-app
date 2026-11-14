package ru.bpdiary.feature.transfer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.bpdiary.core.model.Arm
import ru.bpdiary.domain.csv.ColumnRole
import ru.bpdiary.domain.csv.CsvEncoding
import ru.bpdiary.domain.csv.CsvInspection
import ru.bpdiary.domain.csv.CsvOptions
import ru.bpdiary.domain.csv.ImportReport
import ru.bpdiary.domain.csv.RowError
import ru.bpdiary.domain.csv.RowProblem
import ru.bpdiary.domain.report.DoctorReport
import java.io.IOException
import java.time.LocalDate

internal val options =
    CsvOptions(
        encoding = CsvEncoding.UTF_8,
        delimiter = ',',
        hasHeader = false,
        columns =
            listOf(
                ColumnRole.DATE_TIME,
                ColumnRole.SYS,
                ColumnRole.DIA,
                ColumnRole.PULSE,
                ColumnRole.COMMENT,
            ),
    )

internal val inspection =
    CsvInspection(
        header = null,
        preview =
            listOf(
                listOf("07.10.2026 06:48", "117", "77", "80", "вчера 1-й день препарата"),
                listOf("09.10.2026 06:49", "118", "83", "70", ""),
                listOf("09.10.2026 06:50", "121", "86", "73", "контрольное"),
                listOf("10.10.2026 09:51", "131", "87", "84", "суббота, до приёма"),
                listOf("11.10.2026 11:10", "146", "92", "71", "левая"),
            ),
        rows = 27,
        valid = 27,
        errors = emptyList(),
        errorCount = 0,
        ambiguousDayOrder = false,
        problem = null,
    )

internal val report =
    ImportReport(
        imported = 27,
        duplicates = 0,
        errorCount = 0,
        armFromComment = 1,
        armDefaulted = 26,
        defaultArm = Arm.LEFT,
        commentsCut = 0,
        aheadOfClock = 0,
        withoutTime = 0,
        from = LocalDate.of(2026, 10, 7),
        to = LocalDate.of(2026, 10, 20),
        errors = emptyList(),
    )

/** Bad rows on lines 1, 2, 3… */
internal fun badRows(count: Int) = (1..count).map { RowError(it, RowProblem.SYS_NOT_GREATER) }

internal class FakeImportWorkspace : ImportWorkspace {
    var pickResult: PickResult = PickResult.Copied("davlenie.csv")
    var detectFailure: Exception? = null
    var progress: Flow<ImportProgress> = flowOf(ImportProgress.Done(report))
    var badRows: List<RowError> = emptyList()
    var saveFailure: IOException? = null
    var running = false

    val inspected = ArrayList<CsvOptions>()
    val runs = ArrayList<Boolean>()
    val saved = HashMap<String, String>()
    var cleared = 0
    var picks = 0
    var attached = 0

    override suspend fun pick(uri: String): PickResult {
        picks++
        return pickResult
    }

    override suspend fun isRunning(): Boolean = running

    override fun attach(): Flow<ImportProgress> {
        attached++
        return progress
    }

    override suspend fun detect(
        encoding: CsvEncoding?,
        delimiter: Char?,
    ): CsvOptions {
        detectFailure?.let { throw it }
        return options.copy(encoding = encoding ?: options.encoding, delimiter = delimiter ?: options.delimiter)
    }

    override suspend fun inspect(options: CsvOptions): CsvInspection {
        inspected += options
        return inspection
    }

    override fun run(
        options: CsvOptions,
        rows: Int,
        replaceAll: Boolean,
    ): Flow<ImportProgress> {
        runs += replaceAll
        return progress
    }

    override suspend fun errors(): List<RowError> = badRows

    override suspend fun saveText(
        uri: String,
        text: String,
    ) {
        saveFailure?.let { throw it }
        saved[uri] = text
    }

    override fun clear() {
        cleared++
    }
}

internal class FakeExportFiles : ExportFiles {
    var failure: IOException? = null
    val savedBytes = HashMap<String, ByteArray>()
    val sharedBytes = HashMap<String, ByteArray>()
    val saved: Map<String, String> get() = savedBytes.mapValues { it.value.toString(Charsets.UTF_8) }
    val shared: Map<String, String> get() = sharedBytes.mapValues { it.value.toString(Charsets.UTF_8) }
    var cleared = 0

    override suspend fun save(
        uri: String,
        bytes: ByteArray,
    ) {
        failure?.let { throw it }
        savedBytes[uri] = bytes
    }

    override suspend fun forShare(
        fileName: String,
        bytes: ByteArray,
    ): String {
        failure?.let { throw it }
        sharedBytes[fileName] = bytes
        return "content://$fileName"
    }

    override fun clearShared() {
        cleared++
    }
}

/** Remembers the reports it was given and answers with the page count as the "PDF". */
internal class FakeReportRenderer : ReportRenderer {
    val reports = ArrayList<DoctorReport>()
    var failure: Throwable? = null

    /** When set, a render waits for it: lets a test click again while the first job is still working. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun render(report: DoctorReport): ByteArray {
        reports += report
        gate?.await()
        failure?.let { throw it }
        return "pdf:${report.pageCount}".toByteArray()
    }
}
