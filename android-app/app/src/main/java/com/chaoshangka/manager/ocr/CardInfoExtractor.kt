package com.chaoshangka.manager.ocr

import com.chaoshangka.manager.domain.model.BarcodeDraft
import com.chaoshangka.manager.domain.model.CardType
import com.chaoshangka.manager.domain.model.ExpiryInfo
import com.chaoshangka.manager.domain.model.ExpiryStatus
import com.chaoshangka.manager.domain.model.RecognitionStatus
import com.chaoshangka.manager.domain.model.StoreType
import com.chaoshangka.manager.util.CardTypeClassifier
import com.chaoshangka.manager.util.DateConfidence
import com.chaoshangka.manager.util.DateParser
import com.chaoshangka.manager.util.DenominationOptions
import java.time.LocalDate

/**
 * 從 OCR 文字區塊與條碼辨識結果推導卡片欄位。
 *
 * 設計原則（重要）：
 *  - 只用「文字內容 + 版面位置 + 條碼結果」綜合判斷，**不依賴固定座標**。
 *  - 辨識不到就回傳 null，交由 UI 顯示「待確認」並讓使用者手動補登，**不捏造資料**。
 *  - 條碼內容永遠是字串（前導零不會消失）；卡號文字不會被當成條碼。
 *  - 條碼被遮蔽或刮除時不猜測內容。
 */
object CardInfoExtractor {

    data class Extraction(
        val storeType: StoreType,
        val productName: String?,
        val denomination: Int?,
        val productSpecification: String?,
        val cardType: CardType,
        val cardNumber: String?,
        val expiryText: String?,
        val expiry: ExpiryInfo,
        val barcodeDrafts: List<BarcodeDraft>,
        val warnings: List<String>,
    )

    private val STORE_KEYWORDS: List<Pair<StoreType, List<String>>> = listOf(
        StoreType.SEVEN_ELEVEN to listOf(
            "7-eleven", "7‑eleven", "7－eleven", "統一超商", "統一超", "city cafe", "citycafe", "open!",
        ),
        StoreType.FAMILYMART to listOf("familymart", "family mart", "全家便利", "全家", "fmc"),
        StoreType.HILIFE to listOf("萊爾富", "hi-life", "hilife", "hi life"),
        StoreType.OKMART to listOf("ok超商", "okmart", "ok mart", "ok café", "ok cafe", "ok·mart"),
    )

    private val DATE_LABELS = listOf(
        "有效期限", "有效日期", "使用期限", "兌換期限", "兌換期限至", "期限至", "到期日", "截止日",
        "本卡兌換期限", "開卡日", "儲值期限", "兌換日期",
    )

    private val BOILERPLATE = listOf(
        "注意", "條款", "逾期", "不得", "本公司", "服務專線", "服務電話", "發行人", "地址", "統一編號",
        "www", "http", "客服", "申訴", "退貨", "記載", "所有權", "遺失", "毀損", "保管", "本人",
        "掛失", "偽造", "爭議", "法院", "條例", "權益", "恕不", "敬請", "依照", "為準", "消費",
    )

    private val PRODUCT_KEYWORDS = listOf(
        "咖啡", "美式", "拿鐵", "義式", "茶", "紅茶", "綠茶", "奶茶", "蛋", "飯糰", "壽司", "三明治",
        "便當", "麵包", "雞胸", "肉", "沙拉", "冰", "泡麵", "礦泉水", "水", "豆漿", "牛奶", "乳",
        "可樂", "零食", "餅乾", "甜點", "蛋糕", "商品卡", "禮券卡", "禮品卡", "禮物卡", "抵用券",
        "兌換券", "提貨卡", "提領卡", "熱狗", "關東煮", "地瓜",
    )

    private val SPEC_REGEX = Regex(
        """([小中大特]杯|冰熱不拘|[熱冰]{1}|[xX×]\s*\d+|\d+\s*(杯|個|瓶|份|碗|顆|包|張|件)|數量\s*\d+)""",
    )

    private val CARD_NUMBER_REGEX = Regex("""[A-Za-z]{0,2}\d{7,}""")

    private val SCRATCHED_KEYWORDS = listOf("刮除", "刮開", "遮蔽", "銀漆", "刮刮")

    fun extract(
        analysis: ScanAnalysis,
        today: LocalDate,
        warningDays: Int,
    ): Extraction {
        val lines = analysis.blocks.flatMap { it.lines.ifEmpty { listOf(OcrLine(it.text, it.left, it.top, it.right, it.bottom)) } }
        val allText = analysis.allText
        val normalizedAll = DateParser.normalize(allText).lowercase()

        val storeType = detectStore(normalizedAll)
        val expiryText = detectExpiryText(lines)
        val expiry = DateParser.toExpiryInfo(expiryText, today, warningDays)
        val productLine = detectProductLine(lines)
        val productName = productLine?.text?.trim()?.takeIf { it.isNotBlank() }
        val specification = productLine?.let { extractSpecification(it.text) }
        val denomination = detectDenomination(lines)
        val cardType = CardTypeClassifier.classify(allText)
        val cardNumber = detectCardNumber(lines, analysis.barcodes.map { it.rawValue })

        val drafts = analysis.barcodes
            .filter { it.hasValue }
            .distinctBy { it.rawValue }
            .let { list ->
                val primaryIndex = list.indexOfFirst { !it.barcodeFormat.isTwoDimensional }.takeIf { it >= 0 } ?: 0
                list.mapIndexed { index, detected ->
                    BarcodeDraft(
                        barcodeFormat = detected.barcodeFormat,
                        rawValue = detected.rawValue,
                        recognitionStatus = RecognitionStatus.RECOGNIZED,
                        isPrimary = index == primaryIndex,
                    )
                }
            }

        val warnings = buildWarnings(
            analysis = analysis,
            productName = productName,
            denomination = denomination,
            expiry = expiry,
            normalizedAll = normalizedAll,
        )

        return Extraction(
            storeType = storeType,
            productName = productName,
            denomination = denomination,
            productSpecification = specification,
            cardType = cardType,
            cardNumber = cardNumber,
            expiryText = expiryText,
            expiry = expiry,
            barcodeDrafts = drafts,
            warnings = warnings,
        )
    }

    // ---------------------------------------------------------------- 超商判斷

    private fun detectStore(normalizedLowerText: String): StoreType {
        var best: StoreType = StoreType.UNKNOWN
        var bestScore = 0
        STORE_KEYWORDS.forEach { (store, keywords) ->
            val score = keywords.sumOf { keyword ->
                var count = 0
                var index = normalizedLowerText.indexOf(keyword)
                while (index >= 0) {
                    count++
                    index = normalizedLowerText.indexOf(keyword, index + keyword.length)
                }
                count
            }
            if (score > bestScore) {
                bestScore = score
                best = store
            }
        }
        return best
    }

    // ---------------------------------------------------------------- 有效期限

    private fun detectExpiryText(lines: List<OcrLine>): String? {
        val parsed = lines.map { it.text to DateParser.parse(it.text) }
            .filter { (_, result) -> result.hasDateLikeText }
        if (parsed.isEmpty()) return null

        val labeled = parsed.filter { (text, _) -> DATE_LABELS.any { label -> text.contains(label) } }
        val pool = labeled.ifEmpty { parsed }

        return pool.maxWithOrNull(
            compareBy(
                { (_, result) -> result.end ?: LocalDate.MIN },
                { (_, result) -> if (result.confidence == DateConfidence.HIGH) 1 else 0 },
            ),
        )?.first
    }

    // ---------------------------------------------------------------- 商品名稱

    internal fun scoreProductLine(text: String): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Int.MIN_VALUE
        val lower = trimmed.lowercase()
        if (BOILERPLATE.any { lower.contains(it) }) return -8
        var score = 0
        if (PRODUCT_KEYWORDS.any { lower.contains(it.lowercase()) }) score += 6
        if (listOf("品項", "品名", "商品名稱").any { lower.contains(it) }) score += 4
        if (trimmed.length in 4..40) score += 2
        if (listOf("杯", "個", "瓶", "份", "碗", "顆", "包", "張", "件").any { trimmed.contains(it) }) score += 2
        if (trimmed.contains("元")) score += 1
        val digits = trimmed.count { it.isDigit() }
        if (digits > 0 && digits.toDouble() / trimmed.length > 0.7) score -= 6
        if (listOf("年", "月").any { trimmed.contains(it) }) score -= 4
        return score
    }

    private fun detectProductLine(lines: List<OcrLine>): OcrLine? {
        val scored = lines.map { it to scoreProductLine(it.text) }
        val best = scored.maxByOrNull { it.second } ?: return null
        return if (best.second >= 6) best.first else null
    }

    private fun extractSpecification(text: String): String? {
        val matches = SPEC_REGEX.findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
        return matches.takeIf { it.isNotEmpty() }?.joinToString("・")
    }

    // ---------------------------------------------------------------- 面額

    private fun detectDenomination(lines: List<OcrLine>): Int? {
        val candidates = mutableListOf<Int>()
        val valueCardLines = lines.filter { line ->
            listOf("商品卡", "禮品卡", "禮券卡", "現金禮券").any { line.text.contains(it) }
        }
        valueCardLines.forEach { line ->
            DenominationOptions.parse(line.text)?.let { candidates += it }
        }
        if (candidates.isNotEmpty()) return candidates.max()

        lines.forEach { line ->
            DenominationOptions.parse(line.text)?.let { candidates += it }
        }
        if (candidates.isEmpty()) return null
        val distinct = candidates.distinct()
        if (distinct.size == 1) return distinct.first()

        // 多個不同金額：取出現次數最多者；次數相同則回傳 null（待確認），避免猜測。
        val counts = candidates.groupingBy { it }.eachCount()
        val maxCount = counts.values.max()
        val winners = counts.filterValues { it == maxCount }.keys
        return if (winners.size == 1) winners.first() else null
    }

    // ---------------------------------------------------------------- 卡號

    private fun detectCardNumber(lines: List<OcrLine>, barcodeValues: List<String>): String? {
        val labelLines = lines.filter { line -> listOf("卡號", "編號").any { line.text.contains(it) } }
        val pool = labelLines.ifEmpty {
            lines.filter { line ->
                val lower = line.text.lowercase()
                !listOf("服務", "電話", "客服", "統一編號", "發行", "地址", "有效", "期限").any { lower.contains(it) }
            }
        }
        val tokens = pool.flatMap { line -> CARD_NUMBER_REGEX.findAll(line.text).map { it.value }.toList() }
            .filter { token -> token.length >= 8 }
            .filter { token -> barcodeValues.none { it == token || it.contains(token) } }
            .filter { token -> DateParser.parse(token).end == null }
        return tokens.maxByOrNull { it.length }
    }

    // ---------------------------------------------------------------- 警告訊息

    private fun buildWarnings(
        analysis: ScanAnalysis,
        productName: String?,
        denomination: Int?,
        expiry: ExpiryInfo,
        normalizedAll: String,
    ): List<String> {
        val warnings = mutableListOf<String>()
        if (analysis.barcodes.none { it.hasValue }) {
            warnings += "未辨識到條碼，請手動補登條碼內容。"
        }
        if (analysis.barcodes.size > 1) {
            warnings += "照片中有 ${analysis.barcodes.size} 組條碼，請確認哪一組是主要兌換條碼。"
        }
        if (SCRATCHED_KEYWORDS.any { normalizedAll.contains(it) }) {
            warnings += "卡片上疑似有刮除或遮蔽區域，若有條碼請自行補登（APP 不會猜測內容）。"
        }
        if (productName == null) {
            warnings += "商品名稱未辨識，請手動輸入。"
        }
        if (denomination == null) {
            warnings += "面額未辨識，請手動選擇。"
        }
        if (expiry.status == ExpiryStatus.NEEDS_REVIEW) {
            warnings += "有效期限無法明確判斷，請手動核對。"
        }
        return warnings
    }
}
