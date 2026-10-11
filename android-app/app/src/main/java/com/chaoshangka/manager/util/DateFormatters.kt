package com.chaoshangka.manager.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 日期顯示與儲存格式轉換。[LocalDate] 以 epochDay 儲存於資料庫。 */
object DateFormatters {

    private const val ROC_YEAR_OFFSET = 1911

    private val DATE_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.TAIWAN)

    private val DATE_TIME_FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm", Locale.TAIWAN)

    fun toDisplay(date: LocalDate?): String = date?.format(DATE_FORMATTER) ?: "無期限"

    /** 民國年顯示，例如 118/06/30。 */
    fun toRocDisplay(date: LocalDate?): String {
        if (date == null) return "無期限"
        return "%d/%02d/%02d".format(date.year - ROC_YEAR_OFFSET, date.monthValue, date.dayOfMonth)
    }

    fun rangeDisplay(start: LocalDate?, end: LocalDate?): String = when {
        start == null && end == null -> "無期限"
        start == null -> toDisplay(end)
        end == null -> toDisplay(start)
        start == end -> toDisplay(start)
        else -> "${toDisplay(start)} ~ ${toDisplay(end)}"
    }

    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    fun fromEpochDay(value: Long?): LocalDate? = value?.let { LocalDate.ofEpochDay(it) }

    fun toEpochMillis(date: LocalDate?): Long? = date?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

    fun epochMillisToLocalDate(millis: Long?): LocalDate? =
        millis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

    /** 使用時間顯示，例如 2026/10/09 14:32。 */
    fun dateTimeDisplay(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DATE_TIME_FORMATTER)
    }

    fun dateDisplay(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        return Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DATE_FORMATTER)
    }

    fun timeDisplay(epochMillis: Long?): String {
        if (epochMillis == null) return "—"
        val time = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime()
        return "%02d:%02d".format(time.hour, time.minute)
    }

    /** 剩餘天數描述，例如「剩 5 天」「已過期 3 天」。 */
    fun remainingDaysText(end: LocalDate?, today: LocalDate): String {
        if (end == null) return "無期限"
        val days = java.time.temporal.ChronoUnit.DAYS.between(today, end)
        return when {
            days > 0 -> "剩 $days 天"
            days == 0L -> "今天到期"
            else -> "已過期 ${-days} 天"
        }
    }
}
