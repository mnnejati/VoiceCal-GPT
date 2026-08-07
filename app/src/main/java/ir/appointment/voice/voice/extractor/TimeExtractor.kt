package ir.appointment.voice.voice.extractor

/**
 * Persian speech time extractor.
 *
 * Supports:
 *
 *   ساعت پنج
 *   ساعت پنج و نیم
 *   ساعت پنج و ربع
 *   ساعت پنج و بیست دقیقه
 *   ساعت پنج و سی دقیقه
 *   ساعت ۵
 *   ساعت ۵:۳۰
 *   پنج عصر
 *   پنج بعدازظهر
 *   پنج شب
 *   ربع به پنج
 *   ربع مانده به پنج
 *   ده دقیقه به پنج
 *   بیست دقیقه به پنج
 *   ده دقیقه از پنج گذشته
 */
object TimeExtractor {

    data class Result(
        val hour: Int,
        val minute: Int,
        val displayTime: String
    )

    /**
     * Main extraction function.
     */
    fun extract(
        input: String
    ): Result? {

        val text =
            normalize(input)

        if (text.isEmpty()) {
            return null
        }

        /*
         * -------------------------------------------------------------
         * 1. Explicit HH:MM
         * -------------------------------------------------------------
         *
         * Examples:
         *
         * 17:30
         * ۵:۳۰
         */
        extractColonTime(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 2. "ربع به پنج"
         * -------------------------------------------------------------
         */
        extractQuarterTo(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 3. "ربع مانده به پنج"
         * -------------------------------------------------------------
         */
        extractQuarterRemainingTo(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 4. "ده دقیقه به پنج"
         * -------------------------------------------------------------
         */
        extractMinutesTo(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 5. "ده دقیقه از پنج گذشته"
         * -------------------------------------------------------------
         */
        extractMinutesPast(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 6. "پنج و نیم"
         *
         * Must be checked before normal hour extraction.
         * -------------------------------------------------------------
         */
        extractHalf(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 7. "پنج و ربع"
         * -------------------------------------------------------------
         */
        extractQuarter(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 8. "پنج و بیست دقیقه"
         * -------------------------------------------------------------
         */
        extractHourAndMinutes(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 9. "ساعت پنج عصر"
         * "پنج عصر"
         * -------------------------------------------------------------
         */
        extractHourWithPeriod(text)?.let {
            return it
        }

        /*
         * -------------------------------------------------------------
         * 10. "ساعت پنج"
         * -------------------------------------------------------------
         */
        extractSimpleHour(text)?.let {
            return it
        }

        return null
    }

    // =====================================================================
    // HH:MM
    // =====================================================================

    private fun extractColonTime(
        text: String
    ): Result? {

        val regex =
            Regex(
                """(?<!\d)(\d{1,2})\s*:\s*(\d{1,2})(?!\d)"""
            )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            match.groupValues[1]
                .toIntOrNull()
                ?: return null

        val minute =
            match.groupValues[2]
                .toIntOrNull()
                ?: return null

        if (
            hour !in 0..23 ||
            minute !in 0..59
        ) {
            return null
        }

        return makeResult(
            hour,
            minute
        )
    }

    // =====================================================================
    // QUARTER TO
    // =====================================================================

    private fun extractQuarterTo(
        text: String
    ): Result? {

        val patterns =
            listOf(
                "ربع به",
                "یک ربع به",
                "یه ربع به"
            )

        for (pattern in patterns) {

            val index =
                text.indexOf(pattern)

            if (index < 0) {
                continue
            }

            val after =
                text.substring(
                    index + pattern.length
                ).trim()

            val hour =
                extractFirstHour(
                    after
                )
                    ?: continue

            return subtractMinutes(
                hour = hour,
                minute = 0,
                minutes = 15
            )
        }

        return null
    }

    // =====================================================================
    // QUARTER REMAINING TO
    // =====================================================================

    private fun extractQuarterRemainingTo(
        text: String
    ): Result? {

        val patterns =
            listOf(
                "ربع مانده به",
                "یک ربع مانده به",
                "یه ربع مانده به"
            )

        for (pattern in patterns) {

            val index =
                text.indexOf(pattern)

            if (index < 0) {
                continue
            }

            val after =
                text.substring(
                    index + pattern.length
                ).trim()

            val hour =
                extractFirstHour(
                    after
                )
                    ?: continue

            return subtractMinutes(
                hour = hour,
                minute = 0,
                minutes = 15
            )
        }

        return null
    }

    // =====================================================================
    // MINUTES TO
    // =====================================================================

    private fun extractMinutesTo(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """($numberPattern)\s+دقیقه\s+(?:به|مانده به)\s+(.+)"""
            )

        val match =
            regex.find(text)
                ?: return null

        val minuteText =
            match.groupValues[1]

        val hourText =
            match.groupValues[2]

        val minutes =
            PersianNumberParser.parseInRange(
                minuteText,
                1,
                59
            )
                ?: return null

        val hour =
            extractFirstHour(
                hourText
            )
                ?: return null

        return subtractMinutes(
            hour = hour,
            minute = 0,
            minutes = minutes
        )
    }

    // =====================================================================
    // MINUTES PAST
    // =====================================================================

    private fun extractMinutesPast(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """($numberPattern)\s+دقیقه\s+از\s+(.+?)\s+(?:گذشته|رد شده)"""
            )

        val match =
            regex.find(text)
                ?: return null

        val minuteText =
            match.groupValues[1]

        val hourText =
            match.groupValues[2]

        val minutes =
            PersianNumberParser.parseInRange(
                minuteText,
                1,
                59
            )
                ?: return null

        val hour =
            extractFirstHour(
                hourText
            )
                ?: return null

        return addMinutes(
            hour = hour,
            minute = 0,
            minutes = minutes
        )
    }

    // =====================================================================
    // HALF
    // =====================================================================

    private fun extractHalf(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """(?:ساعت\s+)?($numberPattern)\s+و\s+(?:نیم|نیمه)"""
            )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            PersianNumberParser.parseInRange(
                match.groupValues[1],
                0,
                23
            )
                ?: return null

        return makeResult(
            hour = hour,
            minute = 30
        )
    }

    // =====================================================================
    // QUARTER
    // =====================================================================

    private fun extractQuarter(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """(?:ساعت\s+)?($numberPattern)\s+و\s+(?:یک\s+)?ربع"""
            )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            PersianNumberParser.parseInRange(
                match.groupValues[1],
                0,
                23
            )
                ?: return null

        return makeResult(
            hour = hour,
            minute = 15
        )
    }

    // =====================================================================
    // HOUR + MINUTES
    // =====================================================================

    private fun extractHourAndMinutes(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """(?:ساعت\s+)?($numberPattern)\s+و\s+($numberPattern)\s+دقیقه"""
            )

        val match =
            regex.find(text)
                ?: return null

        val hour =
            PersianNumberParser.parseInRange(
                match.groupValues[1],
                0,
                23
            )
                ?: return null

        val minute =
            PersianNumberParser.parseInRange(
                match.groupValues[2],
                0,
                59
            )
                ?: return null

        return makeResult(
            hour,
            minute
        )
    }

    // =====================================================================
    // HOUR + AM/PM PERIOD
    // =====================================================================

    private fun extractHourWithPeriod(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .numberPattern()

        val regex =
            Regex(
                """(?:ساعت\s+)?($numberPattern)\s+(صبح|ظهر|عصر|بعدازظهر|بعد از ظهر|شب|بامداد)"""
            )

        val match =
            regex.find(text)
                ?: return null

        var hour =
            PersianNumberParser.parseInRange(
                match.groupValues[1],
                0,
                23
            )
                ?: return null

        val period =
            match.groupValues[2]

        hour =
            when (period) {

                "صبح",
                "بامداد" -> {

                    /*
                     * 12 صبح = 00:00
                     */
                    if (hour == 12) {
                        0
                    } else {
                        hour
                    }
                }

                "ظهر" -> {

                    /*
                     * 12 ظهر = 12:00
                     * 1 ظهر = 13:00
                     */
                    if (hour in 1..11) {
                        hour + 12
                    } else {
                        hour
                    }
                }

                "عصر",
                "بعدازظهر",
                "بعد از ظهر",
                "شب" -> {

                    /*
                     * 12 شب = 00:00
                     * 1 شب = 01:00
                     *
                     * For 1..11, interpret as PM except
                     * when the user explicitly says "شب".
                     */
                    when {
                        period == "شب" &&
                                hour in 1..5 ->
                            hour

                        hour in 1..11 ->
                            hour + 12

                        else ->
                            hour
                    }
                }

                else ->
                    hour
            }

        if (
            hour !in 0..23
        ) {
            return null
        }

        return makeResult(
            hour,
            0
        )
    }

    // =====================================================================
    // SIMPLE HOUR
    // =====================================================================

    private fun extractSimpleHour(
        text: String
    ): Result? {

        val numberPattern =
            PersianNumberParser
                .hourPattern()

        /*
         * Prefer "ساعت X".
         */
        val withSaaat =
            Regex(
                """ساعت\s+($numberPattern)(?!\s+(?:و|نیم|ربع|دقیقه))"""
            )

        val match =
            withSaaat.find(text)

        if (match != null) {

            val hour =
                PersianNumberParser.parseInRange(
                    match.groupValues[1],
                    0,
                    23
                )

            if (hour != null) {
                return makeResult(
                    hour,
                    0
                )
            }
        }

        /*
         * Fallback for:
         *
         * "فردا پنج قرار دارم"
         *
         * This is intentionally conservative. We only accept a
         * bare hour when it appears in an appointment-like context.
         */
        val appointmentContext =
            listOf(
                "قرار",
                "نوبت",
                "وقت",
                "جلسه",
                "ملاقات",
                "ویزیت",
                "دارم",
                "می‌روم",
                "میرم"
            )

        val hasContext =
            appointmentContext.any {
                text.contains(it)
            }

        if (!hasContext) {
            return null
        }

        val bareHourRegex =
            Regex(
                """(?<!\S)($numberPattern)(?!\S)"""
            )

        val bareMatch =
            bareHourRegex.find(text)
                ?: return null

        val hour =
            PersianNumberParser.parseInRange(
                bareMatch.groupValues[1],
                0,
                23
            )
                ?: return null

        return makeResult(
            hour,
            0
        )
    }

    // =====================================================================
    // HOUR EXTRACTION FROM SUBTEXT
    // =====================================================================

    private fun extractFirstHour(
        text: String
    ): Int? {

        val normalized =
            normalize(text)

        /*
         * First try "ساعت X".
         */
        val numberPattern =
            PersianNumberParser
                .hourPattern()

        val explicitRegex =
            Regex(
                """ساعت\s+($numberPattern)"""
            )

        val explicit =
            explicitRegex.find(
                normalized
            )

        if (explicit != null) {

            return PersianNumberParser
                .parseInRange(
                    explicit.groupValues[1],
                    0,
                    23
                )
        }

        /*
         * Then try a plain number.
         */
        val plainRegex =
            Regex(
                """(?<!\S)($numberPattern)(?!\S)"""
            )

        val plain =
            plainRegex.find(
                normalized
            )

        if (plain != null) {

            return PersianNumberParser
                .parseInRange(
                    plain.groupValues[1],
                    0,
                    23
                )
        }

        /*
         * Finally support numeric digits.
         */
        val digits =
            Regex(
                """(?<!\d)(\d{1,2})(?!\d)"""
            )
                .find(normalized)

        return digits
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?.takeIf {
                it in 0..23
            }
    }

    // =====================================================================
    // TIME ARITHMETIC
    // =====================================================================

    private fun subtractMinutes(
        hour: Int,
        minute: Int,
        minutes: Int
    ): Result {

        var total =
            hour * 60 +
                minute -
                minutes

        /*
         * If subtraction crosses midnight.
         */
        while (total < 0) {
            total += 24 * 60
        }

        total %=
            24 * 60

        return makeResult(
            hour = total / 60,
            minute = total % 60
        )
    }

    private fun addMinutes(
        hour: Int,
        minute: Int,
        minutes: Int
    ): Result {

        var total =
            hour * 60 +
                minute +
                minutes

        total %=
            24 * 60

        return makeResult(
            hour = total / 60,
            minute = total % 60
        )
    }

    // =====================================================================
    // RESULT
    // =====================================================================

    private fun makeResult(
        hour: Int,
        minute: Int
    ): Result {

        return Result(
            hour = hour,
            minute = minute,
            displayTime =
                String.format(
                    "%02d:%02d",
                    hour,
                    minute
                )
        )
    }

    // =====================================================================
    // NORMALIZATION
    // =====================================================================

    private fun normalize(
        input: String
    ): String {

        var text =
            input.trim()

        /*
         * Arabic -> Persian characters.
         */
        text = text
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')

        /*
         * Persian/Arabic digits -> English.
         */
        text =
            normalizeDigits(text)

        /*
         * Common speech recognition variants.
         */
        text = text
            .replace(
                "بعد از ظهر",
                "بعدازظهر"
            )
            .replace(
                "بعدازظهر",
                "بعدازظهر"
            )
            .replace(
                "نیم ساعت",
                "نیم"
            )

        /*
         * Normalize ZWNJ to ordinary space for matching.
         */
        text =
            text.replace(
                '\u200C',
                ' '
            )

        /*
         * Normalize whitespace.
         */
        text =
            text.replace(
                Regex("\\s+"),
                " "
            )

        return text.trim()
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
}
