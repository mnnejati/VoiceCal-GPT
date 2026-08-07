package ir.appointment.voice.voice.extractor

/**
 * Extracts time expressions from Persian speech.
 *
 * Examples:
 *
 *   ساعت 5
 *   ساعت ۵
 *   ساعت پنج
 *   ساعت پنج و نیم
 *   ساعت پنج و ربع
 *   پنج و نیم
 *   پنج و ربع
 *   ربع به پنج
 *   پنج ربع کم
 *   بیست دقیقه به پنج
 *   ده دقیقه بعد از پنج
 *   ساعت 17:30
 *   ساعت ۱۷:۳۰
 *   پنج عصر
 *   پنج بعد از ظهر
 *   هشت صبح
 *   دوازده ظهر
 *   دوازده شب
 *
 * This class is completely rule-based and does not require
 * any additional ML model or Android dependency.
 */
object TimeExtractor {

    data class Result(
        val hour: Int,
        val minute: Int,
        val displayTime: String,
        val confidence: Int
    )

    // ---------------------------------------------------------------------
    // Main extraction
    // ---------------------------------------------------------------------

    fun extract(text: String): Result? {

        val normalized = normalize(text)

        if (normalized.isEmpty()) {
            return null
        }

        /*
         * Highest priority:
         *
         * 17:30
         * 17٫30
         * ساعت 17:30
         */
        extractColonTime(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "بیست دقیقه به پنج"
         *
         * This must be checked before the simpler
         * "پنج" patterns.
         */
        extractMinutesBeforeHour(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "ده دقیقه بعد از پنج"
         */
        extractMinutesAfterHour(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "ربع به پنج"
         *
         * = 04:45
         */
        extractQuarterTo(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "پنج ربع کم"
         *
         * = 04:45
         */
        extractHourQuarterLess(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "پنج و نیم"
         * "ساعت پنج و نیم"
         */
        extractHourAndHalf(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "پنج و ربع"
         * "ساعت پنج و ربع"
         */
        extractHourAndQuarter(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "پنج و بیست"
         * "ساعت پنج و بیست"
         *
         * Also supports:
         * "پنج و بیست و پنج"
         */
        extractHourAndMinuteWords(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * "ساعت پنج"
         * "ساعت 5"
         */
        extractHourAfterSaaat(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        /*
         * Finally, allow a bare hour:
         *
         * "پنج عصر"
         * "هشت صبح"
         * "نه شب"
         *
         * We intentionally do NOT accept a random bare number
         * without a time context, because this creates many
         * false positives.
         */
        extractBareHourWithContext(normalized)?.let {
            return applyDayPeriod(
                it.hour,
                it.minute,
                normalized,
                it.confidence
            )
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Numeric clock time
    // ---------------------------------------------------------------------

    private fun extractColonTime(
        text: String
    ): RawResult? {

        /*
         * Examples:
         *
         * 17:30
         * 17٫30
         * ساعت 17:30
         * ساعت ۱۷:۳۰
         */
        val regex = Regex(
            """(?:ساعت\s*)?\b(\d{1,2})\s*[:٫]\s*(\d{1,2})\b"""
        )

        val match = regex.find(text)
            ?: return null

        val hour =
            match.groupValues[1].toIntOrNull()
                ?: return null

        val minute =
            match.groupValues[2].toIntOrNull()
                ?: return null

        if (!isValidHour(hour)) return null
        if (!isValidMinute(minute)) return null

        return RawResult(
            hour = hour,
            minute = minute,
            confidence = 100
        )
    }

    // ---------------------------------------------------------------------
    // "بیست دقیقه به پنج"
    // ---------------------------------------------------------------------

    private fun extractMinutesBeforeHour(
        text: String
    ): RawResult? {

        val minutePattern =
            "(\\d{1,2}|${numberWordsPattern()})"

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """$minutePattern\s+دقیقه\s+(?:به|مونده\s+به|مانده\s+به)\s+$hourPattern"""
        )

        val match =
            regex.find(text)
                ?: return null

        val minutes =
            parseNumber(match.groupValues[1])
                ?: return null

        val hour =
            parseNumber(match.groupValues[2])
                ?: return null

        if (hour !in 1..12) return null
        if (minutes !in 1..59) return null

        val resultHour =
            if (hour == 1) 0 else hour - 1

        val resultMinute =
            60 - minutes

        return RawResult(
            hour = resultHour,
            minute = resultMinute,
            confidence = 96
        )
    }

    // ---------------------------------------------------------------------
    // "ده دقیقه بعد از پنج"
    // ---------------------------------------------------------------------

    private fun extractMinutesAfterHour(
        text: String
    ): RawResult? {

        val minutePattern =
            "(\\d{1,2}|${numberWordsPattern()})"

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """$minutePattern\s+دقیقه\s+(?:بعد\s+از|بعداز)\s+$hourPattern"""
        )

        val match =
            regex.find(text)
                ?: return null

        val minutes =
            parseNumber(match.groupValues[1])
                ?: return null

        val hour =
            parseNumber(match.groupValues[2])
                ?: return null

        if (hour !in 0..23) return null
        if (minutes !in 1..59) return null

        val totalMinutes =
            hour * 60 + minutes

        val resultHour =
            (totalMinutes / 60) % 24

        val resultMinute =
            totalMinutes % 60

        return RawResult(
            hour = resultHour,
            minute = resultMinute,
            confidence = 94
        )
    }

    // ---------------------------------------------------------------------
    // "ربع به پنج"
    // ---------------------------------------------------------------------

    private fun extractQuarterTo(
        text: String
    ): RawResult? {

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """(?:ربع|یک\s+ربع)\s+(?:به|مونده\s+به|مانده\s+به)\s+$hourPattern"""
        )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        if (hour !in 1..12) return null

        val resultHour =
            if (hour == 1) 0 else hour - 1

        return RawResult(
            hour = resultHour,
            minute = 45,
            confidence = 97
        )
    }

    // ---------------------------------------------------------------------
    // "پنج ربع کم"
    // ---------------------------------------------------------------------

    private fun extractHourQuarterLess(
        text: String
    ): RawResult? {

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """$hourPattern\s+(?:ربع\s+کم|کم\s+ربع)"""
        )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        if (hour !in 1..12) return null

        return RawResult(
            hour = if (hour == 1) 0 else hour - 1,
            minute = 45,
            confidence = 96
        )
    }

    // ---------------------------------------------------------------------
    // "پنج و نیم"
    // ---------------------------------------------------------------------

    private fun extractHourAndHalf(
        text: String
    ): RawResult? {

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """(?:ساعت\s+)?$hourPattern\s+و\s+(?:نیم|نصف)"""
        )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        if (hour !in 0..23) return null

        return RawResult(
            hour = hour,
            minute = 30,
            confidence = 98
        )
    }

    // ---------------------------------------------------------------------
    // "پنج و ربع"
    // ---------------------------------------------------------------------

    private fun extractHourAndQuarter(
        text: String
    ): RawResult? {

        val hourPattern =
            "(\\d{1,2}|${hourWordsPattern()})"

        val regex = Regex(
            """(?:ساعت\s+)?$hourPattern\s+و\s+(?:یک\s+)?ربع"""
        )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        if (hour !in 0..23) return null

        return RawResult(
            hour = hour,
            minute = 15,
            confidence = 98
        )
    }

    // ---------------------------------------------------------------------
    // "پنج و بیست"
    // "پنج و بیست و پنج"
    // ---------------------------------------------------------------------

    private fun extractHourAndMinuteWords(
        text: String
    ): RawResult? {

        val hourPattern =
            "(${hourWordsPattern()})"

        val minutePattern =
            "(${minuteWordsPattern()})"

        /*
         * First try:
         *
         * پنج و بیست و پنج
         */
        val regexWithCompositeMinute = Regex(
            """(?:ساعت\s+)?$hourPattern\s+و\s+$minutePattern\s+و\s+(${
                minuteWordsPattern()
            })"""
        )

        val composite =
            regexWithCompositeMinute.find(text)

        if (composite != null) {

            val hour =
                parseNumber(composite.groupValues[1])
                    ?: return null

            val tens =
                parseNumber(composite.groupValues[2])
                    ?: return null

            val ones =
                parseNumber(composite.groupValues[3])
                    ?: return null

            val minute =
                tens + ones

            if (
                hour in 0..23 &&
                minute in 0..59
            ) {
                return RawResult(
                    hour = hour,
                    minute = minute,
                    confidence = 93
                )
            }
        }

        /*
         * "پنج و بیست"
         */
        val simpleRegex = Regex(
            """(?:ساعت\s+)?$hourPattern\s+و\s+$minutePattern"""
        )

        val match =
            simpleRegex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        val minute =
            parseNumber(match.groupValues[2])
                ?: return null

        if (hour !in 0..23) return null
        if (minute !in 0..59) return null

        return RawResult(
            hour = hour,
            minute = minute,
            confidence = 92
        )
    }

    // ---------------------------------------------------------------------
    // "ساعت پنج"
    // ---------------------------------------------------------------------

    private fun extractHourAfterSaaat(
        text: String
    ): RawResult? {

        /*
         * Numeric:
         *
         * ساعت 5
         */
        val numericRegex = Regex(
            """\bساعت\s+(\d{1,2})\b"""
        )

        val numeric =
            numericRegex.find(text)

        if (numeric != null) {

            val hour =
                numeric.groupValues[1].toIntOrNull()
                    ?: return null

            if (!isValidHour(hour)) {
                return null
            }

            return RawResult(
                hour = hour,
                minute = 0,
                confidence = 96
            )
        }

        /*
         * Word:
         *
         * ساعت پنج
         * ساعت یازده
         * ساعت دوازده
         */
        val wordRegex = Regex(
            """\bساعت\s+(${hourWordsPattern()})\b"""
        )

        val word =
            wordRegex.find(text)
                ?: return null

        val hour =
            parseNumber(word.groupValues[1])
                ?: return null

        if (hour !in 0..23) {
            return null
        }

        return RawResult(
            hour = hour,
            minute = 0,
            confidence = 96
        )
    }

    // ---------------------------------------------------------------------
    // Bare hour + context
    // ---------------------------------------------------------------------

    private fun extractBareHourWithContext(
        text: String
    ): RawResult? {

        val hourPattern =
            "(${hourWordsPattern()})"

        val regex = Regex(
            """\b$hourPattern\s+(?:صبح|بامداد|ظهر|عصر|بعد\s+از\s+ظهر|شب|نیمه\s+شب)\b"""
        )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            parseNumber(match.groupValues[1])
                ?: return null

        if (hour !in 0..23) {
            return null
        }

        return RawResult(
            hour = hour,
            minute = 0,
            confidence = 91
        )
    }

    // ---------------------------------------------------------------------
    // AM / PM handling
    // ---------------------------------------------------------------------

    private fun applyDayPeriod(
        rawHour: Int,
        rawMinute: Int,
        text: String,
        confidence: Int
    ): Result? {

        var hour = rawHour
        val minute = rawMinute

        /*
         * Explicit 24-hour times such as 17:30 should not
         * receive another +12 adjustment.
         */
        val explicit24Hour =
            hour >= 13

        if (!explicit24Hour) {

            when {

                containsAny(
                    text,
                    "بعد از ظهر",
                    "بعدازظهر",
                    "عصر"
                ) -> {

                    if (hour in 1..11) {
                        hour += 12
                    }
                }

                containsAny(
                    text,
                    "ظهر"
                ) -> {

                    /*
                     * 12 ظهر = 12:00
                     *
                     * "یک ظهر" = 13:00
                     * "دو ظهر" = 14:00
                     */
                    if (hour in 1..11) {
                        hour += 12
                    }
                }

                containsAny(
                    text,
                    "شب"
                ) -> {

                    /*
                     * 12 شب = 00:00
                     * 1 شب = 01:00
                     * 8 شب = 20:00
                     */
                    if (hour in 1..11) {
                        hour += 12
                    }

                    if (hour == 12) {
                        hour = 0
                    }
                }

                containsAny(
                    text,
                    "نیمه شب"
                ) -> {
                    hour = 0
                }

                /*
                 * "صبح" and "بامداد" require no change
                 * for 1..11.
                 *
                 * 12 صبح is interpreted as 00:00.
                 */
                containsAny(
                    text,
                    "صبح",
                    "بامداد"
                ) -> {

                    if (hour == 12) {
                        hour = 0
                    }
                }
            }
        }

        if (!isValidHour(hour)) {
            return null
        }

        if (!isValidMinute(minute)) {
            return null
        }

        return Result(
            hour = hour,
            minute = minute,
            displayTime =
                String.format(
                    "%02d:%02d",
                    hour,
                    minute
                ),
            confidence = confidence
        )
    }

    // ---------------------------------------------------------------------
    // Number parsing
    // ---------------------------------------------------------------------

    private val numbers = mapOf(

        "صفر" to 0,

        "یک" to 1,
        "یه" to 1,
        "یک عدد" to 1,

        "دو" to 2,
        "سه" to 3,
        "چهار" to 4,
        "پنج" to 5,
        "شش" to 6,
        "هفت" to 7,
        "هشت" to 8,
        "نه" to 9,

        "ده" to 10,
        "یازده" to 11,
        "دوازده" to 12,
        "سیزده" to 13,
        "چهارده" to 14,
        "پانزده" to 15,
        "شانزده" to 16,
        "هفده" to 17,
        "هجده" to 18,
        "نوزده" to 19,

        "بیست" to 20,
        "سی" to 30,
        "چهل" to 40,
        "پنجاه" to 50
    )

    private val hourNumbers =
        numbers.filter {
            it.value in 0..23
        }

    private val minuteNumbers =
        numbers.filter {
            it.value in 0..59
        }

    private fun parseNumber(
        value: String
    ): Int? {

        val clean =
            value
                .trim()
                .replace(
                    "‌",
                    ""
                )

        /*
         * Direct digit.
         */
        clean.toIntOrNull()?.let {
            return it
        }

        /*
         * Direct word.
         */
        numbers[clean]?.let {
            return it
        }

        /*
         * Composite number:
         *
         * بیست و سه
         * سی و پنج
         */
        val parts =
            clean.split(
                Regex("\\s+و\\s+")
            )

        if (parts.size == 2) {

            val first =
                numbers[parts[0].trim()]

            val second =
                numbers[parts[1].trim()]

            if (
                first != null &&
                second != null
            ) {

                /*
                 * 20 + 3 = 23
                 */
                if (
                    first >= 20 &&
                    second < 10
                ) {
                    return first + second
                }
            }
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Regex patterns
    // ---------------------------------------------------------------------

    private fun hourWordsPattern(): String {

        return hourNumbers.keys
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
    }

    private fun minuteWordsPattern(): String {

        return minuteNumbers.keys
            .filter {
                it.value in 1..59
            }
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
    }

    private fun numberWordsPattern(): String {

        return numbers.keys
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
    }

    // ---------------------------------------------------------------------
    // Text normalization
    // ---------------------------------------------------------------------

    private fun normalize(
        input: String
    ): String {

        var text = input

        /*
         * Arabic/Persian character normalization.
         */
        text = text
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')

        /*
         * Persian and Arabic digits -> English digits.
         */
        text = normalizeDigits(text)

        /*
         * Common spoken forms.
         */
        text = text
            .replace(
                "بعدازظهر",
                "بعد از ظهر"
            )
            .replace(
                "بعد ازظهر",
                "بعد از ظهر"
            )
            .replace(
                "نیمه‌شب",
                "نیمه شب"
            )

        /*
         * Normalize punctuation.
         */
        text = text
            .replace(
                '٫',
                ':'
            )
            .replace(
                '،',
                ' '
            )
            .replace(
                ',',
                ' '
            )

        /*
         * Normalize whitespace.
         */
        text = text
            .replace(
                Regex("\\s+"),
                " "
            )
            .trim()

        return text
    }

    private fun normalizeDigits(
        input: String
    ): String {

        val persianDigits =
            "۰۱۲۳۴۵۶۷۸۹"

        val arabicDigits =
            "٠١٢٣٤٥٦٧٨٩"

        val result =
            StringBuilder(
                input.length
            )

        for (ch in input) {

            val persianIndex =
                persianDigits.indexOf(ch)

            val arabicIndex =
                arabicDigits.indexOf(ch)

            when {

                persianIndex >= 0 ->
                    result.append(
                        persianIndex
                    )

                arabicIndex >= 0 ->
                    result.append(
                        arabicIndex
                    )

                else ->
                    result.append(ch)
            }
        }

        return result.toString()
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private fun containsAny(
        text: String,
        vararg values: String
    ): Boolean {

        return values.any {
            text.contains(
                it,
                ignoreCase = false
            )
        }
    }

    private fun isValidHour(
        hour: Int
    ): Boolean {

        return hour in 0..23
    }

    private fun isValidMinute(
        minute: Int
    ): Boolean {

        return minute in 0..59
    }

    private data class RawResult(
        val hour: Int,
        val minute: Int,
        val confidence: Int
    )
}
