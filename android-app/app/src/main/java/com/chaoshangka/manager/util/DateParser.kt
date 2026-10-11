package com.chaoshangka.manager.util

import com.chaoshangka.manager.domain.model.ExpiryInfo
import com.chaoshangka.manager.domain.model.ExpiryStatus
import java.time.LocalDate

/** 日期解析信心度。[LOW] 代表需要使用者確認。 */
enum class DateConfidence { HIGH, LOW }

/**
 * 日期解析結果。
 *
 * [hasDateLikeText] 為 true 且 [end] 為 null 時，代表「看起來像日期但解析不出來」，
 * UI 必須顯示「期限待確認」，不可當成「無期限」。
 */
data class DateParseResult(
    val start: LocalDate?,
    val end: LocalDate?,
    val confidence: DateConfidence,
    val hasDateLikeText: Boolean,
    val rawText: String?,
)

/**
 * 卡片有效期限解析器（純 Kotlin，可離線、可單元測試）。
 *
 * 支援格式：
 *  - 西元單一日期：`2029年06月30日`、`2029/06/30`、`2029-06-30`、`2029.06.30`、`20290630`
 *  - 民國單一日期：`115/06/30`、`民國115年6月30日`
 *  - 西元區間：`2025/04/01～2026/06/30`、`2025/04/01-2026/06/30`
 *  - 民國區間：`115/02/26～116/08/26`
 *
 * 年份判斷規則：
 *  - 4 位數（>= 1900）視為西元。
 *  - 3 位數（100~999）視為民國年，加 1911 轉西元。
 *  - 1~2 位數視為民國年但標記為 [DateConfidence.LOW]，需人工確認。
 *
 * 多個日期時的處理：取最早者為起始日、最晚者為結束日（卡片常同時印有開卡日與有效期限）。
 * 若多個日期之間「沒有」明確的區間符號（`～ - ~ 至 到`），信心度降為 [DateConfidence.LOW]。
 * 日期區間一律以結束日作為到期排序依據。
 */
object DateParser {

    private val RANGE_CHARS = listOf('～', '〜', '~', '–', '—', '－', '至', '到', '-')

    private val TOKEN_REGEX =
        Regex("""(\d{2,4})\s*[年/.\-]\s*(\d{1,2})\s*[月/.\-]\s*(\d{1,2})\s*日?""")

    private val COMPACT_REGEX = Regex("""(?<!\d)(\d{8})(?!\d)""")

    /**
     * 「看起來像日期」的判斷必須夠嚴格，否則客服專線（0800-008-711）、
     * 條碼數字等會被誤認為日期而標成「期限待確認」。
     *
     *  - [DATE_LIKE_REGEX]：完整日期形狀（年-月-日），即使數值不合法也算日期文字。
     *  - [PARTIAL_DATE_LIKE_REGEX]：只寫到年月的殘缺日期（需要 `年` 或 `/` 分隔）。
     */
    private val DATE_LIKE_REGEX = Regex("""\d{2,4}\s*[年/.\-]\s*\d{1,2}\s*[月/.\-]\s*\d{1,2}""")

    private val PARTIAL_DATE_LIKE_REGEX = Regex("""\d{2,4}\s*[年/]\s*\d{1,2}""")

    /** 全形轉半形、統一區間符號，讓後續解析邏輯單純化。 */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '０'..'９' -> sb.append('0' + (ch - '０'))
                '／' -> sb.append('/')
                '．', '。' -> sb.append('.')
                '－' -> sb.append('-')
                '～', '〜', '–', '—' -> sb.append('~')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private data class Token(
        val start: Int,
        val end: Int,
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private fun collectTokens(text: String): List<Token> {
        val result = mutableListOf<Token>()
        for (m in TOKEN_REGEX.findAll(text)) {
            val year = m.groupValues[1].toIntOrNull() ?: continue
            val month = m.groupValues[2].toIntOrNull() ?: continue
            val day = m.groupValues[3].toIntOrNull() ?: continue
            result += Token(m.range.first, m.range.last + 1, year, month, day)
        }
        if (result.isNotEmpty()) return result

        for (m in COMPACT_REGEX.findAll(text)) {
            val value = m.groupValues[1]
            val year = value.substring(0, 4).toIntOrNull() ?: continue
            val month = value.substring(4, 6).toIntOrNull() ?: continue
            val day = value.substring(6, 8).toIntOrNull() ?: continue
            result += Token(m.range.first, m.range.last + 1, year, month, day)
        }
        return result
    }

    /** 回傳 (西元年, 信心度)。 */
    private fun resolveYear(year: Int): Pair<Int, DateConfidence> = when {
        year >= 1900 -> year to DateConfidence.HIGH
        year in 100..999 -> (year + 1911) to DateConfidence.HIGH
        else -> (year + 1911) to DateConfidence.LOW
    }

    fun parse(raw: String?): DateParseResult {
        if (raw.isNullOrBlank()) {
            return DateParseResult(null, null, DateConfidence.HIGH, false, null)
        }
        val text = normalize(raw)
        val tokens = collectTokens(text)
        if (tokens.isEmpty()) {
            val dateLike = DATE_LIKE_REGEX.containsMatchIn(text) || PARTIAL_DATE_LIKE_REGEX.containsMatchIn(text)
            return DateParseResult(
                start = null,
                end = null,
                confidence = if (dateLike) DateConfidence.LOW else DateConfidence.HIGH,
                hasDateLikeText = dateLike,
                rawText = raw,
            )
        }

        var lowConfidence = false
        val dates = mutableListOf<LocalDate>()
        for (token in tokens) {
            val (year, confidence) = resolveYear(token.year)
            if (confidence == DateConfidence.LOW) lowConfidence = true
            val date = runCatching { LocalDate.of(year, token.month, token.day) }.getOrNull()
            if (date == null) lowConfidence = true else dates += date
        }
        if (dates.isEmpty()) {
            return DateParseResult(null, null, DateConfidence.LOW, true, raw)
        }

        // 檢查多個日期之間是否有明確的區間符號（只看日期與日期之間的文字）。
        var hasRangeSeparator = false
        for (i in 0 until tokens.size - 1) {
            val from = tokens[i].end.coerceIn(0, text.length)
            val to = tokens[i + 1].start.coerceIn(0, text.length)
            if (from <= to) {
                val gap = text.substring(from, to)
                if (gap.any { it in RANGE_CHARS }) hasRangeSeparator = true
            }
        }
        if (tokens.size >= 2 && !hasRangeSeparator) lowConfidence = true

        return DateParseResult(
            start = dates.min(),
            end = dates.max(),
            confidence = if (lowConfidence) DateConfidence.LOW else DateConfidence.HIGH,
            hasDateLikeText = true,
            rawText = raw,
        )
    }

    /**
     * 依「本機今天」與提醒門檻計算期限狀態。
     * 只有日期而沒有時間時，一律以卡片上標示的日期判斷，不假設超商實際兌換截止時間。
     */
    fun evaluate(
        start: LocalDate?,
        end: LocalDate?,
        needsReview: Boolean,
        today: LocalDate,
        warningDays: Int,
    ): ExpiryStatus {
        if (needsReview) return ExpiryStatus.NEEDS_REVIEW
        val expiry = end ?: return ExpiryStatus.NO_EXPIRY
        return when {
            expiry.isBefore(today) -> ExpiryStatus.EXPIRED
            expiry.isEqual(today) -> ExpiryStatus.DUE_TODAY
            !expiry.isAfter(today.plusDays(warningDays.toLong())) -> ExpiryStatus.EXPIRING_SOON
            else -> ExpiryStatus.VALID
        }
    }

    /** 從卡片上的日期文字產生 [ExpiryInfo]。 */
    fun toExpiryInfo(raw: String?, today: LocalDate, warningDays: Int): ExpiryInfo {
        val parsed = parse(raw)
        if (!parsed.hasDateLikeText && parsed.end == null) {
            return ExpiryInfo(null, null, ExpiryStatus.NO_EXPIRY, raw)
        }
        val needsReview = parsed.confidence == DateConfidence.LOW || parsed.end == null
        return ExpiryInfo(
            startDate = parsed.start,
            endDate = parsed.end,
            status = evaluate(parsed.start, parsed.end, needsReview, today, warningDays),
            rawText = raw,
        )
    }
}
