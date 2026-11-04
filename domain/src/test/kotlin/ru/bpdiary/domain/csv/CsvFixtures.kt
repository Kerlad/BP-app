package ru.bpdiary.domain.csv

import ru.bpdiary.core.model.Arm
import ru.bpdiary.core.model.Status
import ru.bpdiary.core.model.Tag
import java.io.ByteArrayInputStream

internal val tagLabels =
    mapOf(
        Tag.GOOD to "Хорошо",
        Tag.HEADACHE to "Головная боль",
        Tag.DIZZINESS to "Головокружение",
        Tag.WEAKNESS to "Слабость",
        Tag.AFTER_MEDICINE to "После лекарства",
        Tag.AFTER_EXERCISE to "После нагрузки",
    )

/** The word lists of docs/13 §13.2, as the resources of the app give them. */
internal val vocabulary =
    CsvVocabulary(
        dateTime = setOf("дата и время", "datetime"),
        date = setOf("дата", "date"),
        time = setOf("время", "time"),
        timestamp = setOf("метка времени", "timestamp"),
        sys = setOf("верхнее", "сад", "систолическое", "sys", "systolic", "sbp", "upper"),
        dia = setOf("нижнее", "дад", "диастолическое", "dia", "diastolic", "dbp", "lower"),
        pulse = setOf("пульс", "чсс", "pulse", "heart rate", "hr", "bpm"),
        arm = setOf("рука", "arm", "hand"),
        tags = setOf("самочувствие", "теги", "tags", "symptoms"),
        comment = setOf("комментарий", "заметка", "примечание", "comment", "note", "notes"),
        ignored = setOf("статус", "status"),
        leftWords = setOf("левая", "левой", "левую", "лев", "left", "л"),
        rightWords = setOf("правая", "правой", "правую", "прав", "right", "п"),
        tagLabels = tagLabels,
    )

/** The texts of docs/samples/export-sample.csv. */
internal fun exportTexts(metaLines: List<String> = emptyList()) =
    CsvExportTexts(
        metaLines = metaLines,
        columns =
            listOf(
                "Дата",
                "Время",
                "Верхнее",
                "Нижнее",
                "Пульс",
                "Рука",
                "Статус",
                "Самочувствие",
                "Комментарий",
                "Метка времени",
            ),
        arms = mapOf(Arm.LEFT to "Левая", Arm.RIGHT to "Правая"),
        statuses = mapOf(Status.HIGH to "Повышенное", Status.NORMAL to "В норме", Status.LOW to "Пониженное"),
        tags = tagLabels,
    )

internal fun sourceOf(bytes: ByteArray) = CsvSource { ByteArrayInputStream(bytes) }

internal fun sourceOf(
    text: String,
    charset: String = "UTF-8",
) = sourceOf(text.toByteArray(charset(charset)))

internal fun resourceBytes(name: String): ByteArray =
    checkNotNull(CsvFixture::class.java.getResourceAsStream("/csv/$name")) { "No fixture $name" }.use { it.readBytes() }

private object CsvFixture
