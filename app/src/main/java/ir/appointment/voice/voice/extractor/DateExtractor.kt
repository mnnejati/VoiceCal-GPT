package ir.appointment.voice.voice.extractor

import ir.appointment.voice.voice.PersianCalendar

/**
 * Extracts Jalali dates from normalized Persian speech text.
 *
 * Supported examples:
 *   امروز
 *   امشب
 *   فردا
 *   پس فردا
 *   دو روز دیگر
 *   دو روز بعد
 *   سه روز دیگر
 *   یک هفته دیگر
 *   هفته آینده
 *   هفته بعد
 *   شنبه
 *   شنبه آینده
 *   سه شنبه بعد
 *   12 مرداد
 *   12 مرداد 1405
 *   دوازدهم مرداد
 *   1405/5/12
 */
object DateExtractor {

    data class Result(
        val year: Int?,
        val month: Int?,
        val day: Int?,
        val weekdayName: String?,
        val displayDate: String?
    )

    private val weekdays = listOf(
        "شنبه",
        "یکشنبه",
        "دوشنبه",
        "سه‌شنبه",
        "چهارشنبه",
        "پنجشنبه",
        "جمعه"
    )

    private val weekdayAliases = mapOf(
        "سه شنبه" to "سه‌شنبه",
        "سه‌شنبه" to "سه‌شنبه",
        "پنج شنبه" to "پنجشنبه",
        "پنج‌شنبه" to "پنجشنبه"
    )

    private val weekdayIndex = mapOf(
        "شنبه" to 0,
        "یکشنبه" to 1,
        "دوشنبه" to 2,
        "سه‌شنبه" to 3,
        "چهارشنبه" to 4,
        "پنجشنبه" to 5,
        "جمعه" to 6
    )

    private val monthNames = PersianCalendar.jalaliMonthNames

    private val monthAliases = mapOf(
        "فروردین" to 1,
        "اردیبهشت" to 2,
        "خرداد" to 3,
        "تیر" to 4,
        "مرداد" to 5,
        "شهریور" to 6,
        "مهر" to 7,
        "آبان" to 8,
        "آذر" to 9,
        "دی" to 10,
        "بهمن" to 11,
        "اسفند" to 12
    )

    /*
     * Ordinal day names.
     *
     * We deliberately keep this dictionary local to the date extractor.
     * It avoids coupling the date parser to the general number parser.
     */
    private val ordinalDays = mapOf(
        "یکم" to 1,
        "اول" to 1,
        "دوم" to 2,
        "سوم" to 3,
        "چهارم" to 4,
        "پنجم" to 5,
        "ششم" to 6,
        "هفتم" to 7,
        "هشتم" to 8,
        "نهم" to 9,
        "دهم" to 10,
        "یازدهم" to 11,
        "دوازدهم" to 12,
        "سیزدهم" to 13,
        "چهاردهم" to 14,
        "پانزدهم" to 15,
        "شانزدهم" to 16,
        "هفدهم" to 17,
        "هجدهم" to 18,
        "نوزدهم" to 19,
        "بیستم" to 20,
        "بیست و یکم" to 21,
        "بیست و دوم" to 22,
        "بیست و سوم" to 23,
        "بیست و چهارم" to 24,
        "بیست و پنجم" to 25,
        "بیست و ششم" to 26,
        "بیست و هفتم" to 27,
        "بیست و هشتم" to 28,
        "بیست و نهم" to 29,
        "سی‌ام" to 30,
        "سی ام" to 30,
        "سی و یکم" to 31
    )

    /**
     * Main entry point.
     */
    fun extract(text: String): Result {

        val normalized = normalize(text)

        if (normalized.isEmpty()) {
            return Result(
                year = null,
                month = null,
                day = null,
                weekdayName = null,
                displayDate = null
            )
        }

        /*
         * Explicit dates have priority over relative dates.
         *
         * Example:
         * "فردا 12 مرداد قرار دارم"
         *
         * In this situation the explicit date is the more informative
         * expression.
         */
        extractNumericDate(normalized)?.let {
            return buildResult(
                it.first,
                it.second,
                it.third
            )
        }

        extractDayMonthDate(normalized)?.let {
            return buildResult(
                it.first,
                it.second,
                it.third
            )
        }

        extractOrdinalDayMonth(normalized)?.let {
            return buildResult(
                it.first,
                it.second,
                it.third
            )
        }

        /*
         * Relative expressions:
         *
         * دو روز دیگر
         * دو روز بعد
         * دو روز دیگه
         * 3 روز دیگر
         */
        extractRelativeDays(normalized)?.let { offset ->
            val today = PersianCalendar.todayJalali()
            val shifted = shiftDays(
                today.first,
                today.second,
                today.third,
                offset
            )

            return buildResult(
                shifted.first,
                shifted.second,
                shifted.third,
                relativeDisplay(offset)
            )
        }

        /*
         * Weeks.
         */
        extractRelativeWeeks(normalized)?.let { weeks ->
            val today = PersianCalendar.todayJalali()

            val shifted = shiftDays(
                today.first,
                today.second,
                today.third,
                weeks * 7
            )

            return buildResult(
                shifted.first,
                shifted.second,
                shifted.third,
                if (weeks == 1) "هفته آینده" else "$weeks هفته دیگر"
            )
        }

        /*
         * امروز / امشب
         */
        if (
            containsWord(normalized, "امروز") ||
            containsWord(normalized, "امشب")
        ) {
            val today = PersianCalendar.todayJalali()

            return buildResult(
                today.first,
                today.second,
                today.third,
                if (containsWord(normalized, "امشب")) {
                    "امشب"
                } else {
                    "امروز"
                }
            )
        }

        /*
         * فردا
         */
        if (containsWord(normalized, "فردا")) {

            val tomorrow = shiftFromToday(1)

            return buildResult(
                tomorrow.first,
                tomorrow.second,
                tomorrow.third,
                "فردا"
            )
        }

        /*
         * پس فردا
         */
        if (
            containsWord(normalized, "پس‌فردا") ||
            containsWord(normalized, "پس فردا")
        ) {

            val afterTomorrow = shiftFromToday(2)

            return buildResult(
                afterTomorrow.first,
                afterTomorrow.second,
                afterTomorrow.third,
                "پس‌فردا"
            )
        }

        /*
         * Weekday expressions.
         */
        extractWeekdayDate(normalized)?.let {
            return buildResult(
                it.first,
                it.second,
                it.third,
                it.fourth
            )
        }

        return Result(
            year = null,
            month = null,
            day = null,
            weekdayName = extractSpokenWeekday(normalized),
            displayDate = null
        )
    }

    // ---------------------------------------------------------------------
    // Explicit numeric dates
    // ---------------------------------------------------------------------

    private fun extractNumericDate(
        text: String
    ): Triple<Int, Int, Int>? {

        /*
         * 1405/5/12
         * 1405-5-12
         * 1405.5.12
         */
        val regex = Regex(
            """\b(1[34]\d{2})\s*[/\-\.]\s*(\d{1,2})\s*[/\-\.]\s*(\d{1,2})\b"""
        )

        val match = regex.find(text) ?: return null

        val year = match.groupValues[1].toIntOrNull()
            ?: return null

        val month = match.groupValues[2].toIntOrNull()
            ?: return null

        val day = match.groupValues[3].toIntOrNull()
            ?: return null

        return if (isValidDate(year, month, day)) {
            Triple(year, month, day)
        } else {
            null
        }
    }

    // ---------------------------------------------------------------------
    // "12 مرداد 1405"
    // ---------------------------------------------------------------------

    private fun extractDayMonthDate(
        text: String
    ): Triple<Int, Int, Int>? {

        val monthPattern = monthNames
            .sortedByDescending { it.length }
            .joinToString("|") {
                Regex.escape(it)
            }

        val regex = Regex(
            """\b(\d{1,2})\s*(?:ام)?\s*($monthPattern)(?:\s+(1[34]\d{2}))?\b"""
        )

        val match = regex.find(text) ?: return null

        val day = match.groupValues[1].toIntOrNull()
            ?: return null

        val month = monthAliases[match.groupValues[2]]
            ?: return null

        val year = if (match.groupValues[3].isNotEmpty()) {
            match.groupValues[3].toIntOrNull()
        } else {
            PersianCalendar.todayJalali().first
        }

        if (year == null) return null

        return if (isValidDate(year, month, day)) {
            Triple(year, month, day)
        } else {
            null
        }
    }

    // ---------------------------------------------------------------------
    // "دوازدهم مرداد"
    // ---------------------------------------------------------------------

    private fun extractOrdinalDayMonth(
        text: String
    ): Triple<Int, Int, Int>? {

        val ordinalPattern = ordinalDays.keys
            .sortedByDescending { it.length }
            .joinToString("|") {
                Regex.escape(it)
            }

        val monthPattern = monthNames
            .sortedByDescending { it.length }
            .joinToString("|") {
                Regex.escape(it)
            }

        val regex = Regex(
            """($ordinalPattern)\s+($monthPattern)(?:\s+(1[34]\d{2}))?"""
        )

        val match = regex.find(text) ?: return null

        val day = ordinalDays[match.groupValues[1]]
            ?: return null

        val month = monthAliases[match.groupValues[2]]
            ?: return null

        val year = if (match.groupValues[3].isNotEmpty()) {
            match.groupValues[3].toIntOrNull()
        } else {
            PersianCalendar.todayJalali().first
        }

        if (year == null) return null

        return if (isValidDate(year, month, day)) {
            Triple(year, month, day)
        } else {
            null
        }
    }

    // ---------------------------------------------------------------------
    // Relative days
    // ---------------------------------------------------------------------

    private fun extractRelativeDays(
        text: String
    ): Int? {

        /*
         * First try digits.
         *
         * Examples:
         * 2 روز دیگر
         * 2 روز بعد
         * ۲ روز دیگه
         */
        val digitRegex = Regex(
            """\b(\d{1,2})\s*روز\s*(?:دیگر|بعد|دیگه)\b"""
        )

        digitRegex.find(text)?.let {
            return it.groupValues[1].toIntOrNull()
        }

        /*
         * Word numbers.
         *
         * The parser intentionally accepts the common small numbers
         * used in natural appointment speech.
         */
        val numberWords = mapOf(
            "یک" to 1,
            "یه" to 1,
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
            "بیست" to 20
        )

        val wordPattern = numberWords.keys
            .sortedByDescending { it.length }
            .joinToString("|") {
                Regex.escape(it)
            }

        val wordRegex = Regex(
            """($wordPattern)\s*روز\s*(?:دیگر|بعد|دیگه)\b"""
        )

        wordRegex.find(text)?.let {
            return numberWords[it.groupValues[1]]
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Relative weeks
    // ---------------------------------------------------------------------

    private fun extractRelativeWeeks(
        text: String
    ): Int? {

        if (
            containsPhrase(text, "هفته آینده") ||
            containsPhrase(text, "هفته بعد") ||
            containsPhrase(text, "هفته دیگه")
        ) {
            return 1
        }

        val digitRegex = Regex(
            """\b(\d{1,2})\s*هفته\s*(?:دیگر|بعد|دیگه)\b"""
        )

        digitRegex.find(text)?.let {
            return it.groupValues[1].toIntOrNull()
        }

        val words = mapOf(
            "یک" to 1,
            "یه" to 1,
            "دو" to 2,
            "سه" to 3,
            "چهار" to 4,
            "پنج" to 5,
            "شش" to 6,
            "هفت" to 7,
            "هشت" to 8,
            "نه" to 9,
            "ده" to 10
        )

        for ((word, number) in words) {

            if (
                containsPhrase(text, "$word هفته دیگر") ||
                containsPhrase(text, "$word هفته بعد") ||
                containsPhrase(text, "$word هفته دیگه")
            ) {
                return number
            }
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Weekdays
    // ---------------------------------------------------------------------

    private fun extractWeekdayDate(
        text: String
    ): Quadruple<Int, Int, Int, String>? {

        val spoken = extractSpokenWeekday(text)
            ?: return null

        val canonical = weekdayAliases[spoken] ?: spoken

        val targetIndex = weekdayIndex[canonical]
            ?: return null

        val today = PersianCalendar.todayJalali()

        val todayName = PersianCalendar.weekdayName(
            today.first,
            today.second,
            today.third
        ) ?: return null

        val todayCanonical =
            weekdayAliases[todayName] ?: todayName

        val todayIndex =
            weekdayIndex[todayCanonical] ?: return null

        var offset =
            (targetIndex - todayIndex + 7) % 7

        /*
         * "شنبه" by itself means the next occurrence.
         * Therefore if today is Saturday, select next Saturday.
         */
        if (offset == 0) {
            offset = 7
        }

        /*
         * Explicit words such as "آینده" and "بعد" also mean
         * the next occurrence. For normal weekday names this
         * is already satisfied by the offset calculation above.
         */
        val date = shiftDays(
            today.first,
            today.second,
            today.third,
            offset
        )

        return Quadruple(
            date.first,
            date.second,
            date.third,
            canonical
        )
    }

    private fun extractSpokenWeekday(
        text: String
    ): String? {

        val candidates = listOf(
            "سه شنبه",
            "سه‌شنبه",
            "پنج شنبه",
            "پنج‌شنبه",
            "یکشنبه",
            "دوشنبه",
            "چهارشنبه",
            "پنجشنبه",
            "شنبه",
            "جمعه"
        )

        return candidates
            .sortedByDescending { it.length }
            .firstOrNull {
                containsWord(text, it)
            }
    }

    // ---------------------------------------------------------------------
    // Result construction
    // ---------------------------------------------------------------------

    private fun buildResult(
        year: Int,
        month: Int,
        day: Int,
        explicitDisplay: String? = null
    ): Result {

        val weekday =
            PersianCalendar.weekdayName(
                year,
                month,
                day
            )

        val display =
            explicitDisplay
                ?: "$day ${monthNames.getOrNull(month - 1) ?: ""} $year"

        return Result(
            year = year,
            month = month,
            day = day,
            weekdayName = weekday,
            displayDate = display
        )
    }

    // ---------------------------------------------------------------------
    // Date arithmetic
    // ---------------------------------------------------------------------

    private fun shiftFromToday(
        offset: Int
    ): Triple<Int, Int, Int> {

        val today = PersianCalendar.todayJalali()

        return shiftDays(
            today.first,
            today.second,
            today.third,
            offset
        )
    }

    private fun shiftDays(
        year: Int,
        month: Int,
        day: Int,
        offsetDays: Int
    ): Triple<Int, Int, Int> {

        val gregorian =
            PersianCalendar.jalaliToGregorian(
                year,
                month,
                day
            )

        val calendar =
            java.util.Calendar.getInstance()

        calendar.clear()

        calendar.set(
            gregorian.first,
            gregorian.second - 1,
            gregorian.third
        )

        calendar.add(
            java.util.Calendar.DAY_OF_MONTH,
            offsetDays
        )

        return PersianCalendar.gregorianToJalali(
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    // ---------------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------------

    private fun isValidDate(
        year: Int,
        month: Int,
        day: Int
    ): Boolean {

        if (year < 1300 || year > 1600) return false
        if (month !in 1..12) return false
        if (day !in 1..31) return false

        if (month <= 6 && day > 31) return false
        if (month in 7..11 && day > 30) return false

        if (month == 12) {

            val leap =
                isJalaliLeapYear(year)

            val maxDay =
                if (leap) 30 else 29

            if (day > maxDay) return false
        }

        return true
    }

    private fun isJalaliLeapYear(
        year: Int
    ): Boolean {

        /*
         * Use the calendar implementation already present
         * in the application rather than introducing another
         * Jalali calendar library.
         */
        return try {

            val g1 =
                PersianCalendar.jalaliToGregorian(
                    year,
                    12,
                    29
                )

            val g2 =
                PersianCalendar.jalaliToGregorian(
                    year,
                    12,
                    30
                )

            g1 != g2

        } catch (_: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------
    // Text helpers
    // ---------------------------------------------------------------------

    private fun normalize(
        input: String
    ): String {

        var text = input

        text = text
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')

        text = normalizeDigits(text)

        text = text
            .replace("سه شنبه", "سه‌شنبه")
            .replace("پنج شنبه", "پنج‌شنبه")
            .replace("پس فردا", "پس‌فردا")
            .replace("پس‌فردای", "پس‌فردا")
            .replace("دیگه", "دیگر")

        text = text
            .replace(Regex("[،,؛;]+"), " ")
            .replace(Regex("\\s+"), " ")
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
            StringBuilder(input.length)

        for (char in input) {

            val p =
                persianDigits.indexOf(char)

            val a =
                arabicDigits.indexOf(char)

            when {

                p >= 0 ->
                    result.append(p)

                a >= 0 ->
                    result.append(a)

                else ->
                    result.append(char)
            }
        }

        return result.toString()
    }

    private fun containsWord(
        text: String,
        word: String
    ): Boolean {

        val escaped =
            Regex.escape(word)

        return Regex(
            """(?<![\p{L}\p{N}])$escaped(?![\p{L}\p{N}])"""
        ).containsMatchIn(text)
    }

    private fun containsPhrase(
        text: String,
        phrase: String
    ): Boolean {
        return text.contains(
            phrase,
            ignoreCase = false
        )
    }

    private fun relativeDisplay(
        offset: Int
    ): String {

        return when (offset) {
            1 -> "فردا"
            2 -> "پس‌فردا"
            else -> "$offset روز دیگر"
        }
    }

    /*
     * Small internal tuple class because Kotlin does not provide
     * Quadruple in the standard library.
     */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
