package com.mars.planner.screens

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ru = Locale("ru")

internal val ruDateShort: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", ru)
internal val ruDateFull: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", ru)
internal val ruDateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", ru)
internal val ruTimestamp: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", ru)

/** Конец дня (23:59) считается сроком «без точного времени». */
private const val END_OF_DAY_MINUTES = 23 * 60 + 59

internal fun dueDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

internal fun dueMinuteOfDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Int =
    Instant.ofEpochMilli(millis).atZone(zone).let { it.hour * 60 + it.minute }

internal fun hasExplicitTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    dueMinuteOfDay(millis, zone) != END_OF_DAY_MINUTES

/** Короткая подпись срока: «12 сен» или «12 сен, 14:30». */
internal fun dueLabel(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String? {
    if (millis == null) return null
    val zoned = Instant.ofEpochMilli(millis).atZone(zone)
    return if (hasExplicitTime(millis, zone)) {
        zoned.format(ruDateTime)
    } else {
        zoned.format(ruDateShort)
    }
}

internal fun dueLabelOrNoDue(millis: Long?, zone: ZoneId = ZoneId.systemDefault()): String =
    dueLabel(millis, zone) ?: "Без срока"

internal fun timestampLabel(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    if (millis <= 0L) "—" else Instant.ofEpochMilli(millis).atZone(zone).format(ruTimestamp)

internal fun timeLabel(minutesOfDay: Int): String =
    "%02d:%02d".format(minutesOfDay / 60, minutesOfDay % 60)
