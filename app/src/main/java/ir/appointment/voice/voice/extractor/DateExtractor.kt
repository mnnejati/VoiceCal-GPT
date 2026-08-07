package ir.appointment.voice.voice.extractor
import ir.appointment.voice.voice.PersianCalendar

/**
 * Extracts Jalali date information from Persian speech.
 *
 * Examples:
 *
 *   امروز
 *   فردا
 *   پس فردا
 *   دو روز دیگر
 *   دو روز بعد
 *   دو روز دیگه
 *   سه روز دیگه
 *   یک هفته بعد
 *   شنبه
 *   دوشنبه هفته بعد
 *   دوازدهم مرداد
 *   بیست و پنجم شهریور
 *   12 مرداد
 *   12 مرداد 1405
 *   1405/05/12
 */
object DateExtractor {

    data class Result(
        val year: Int,
        val month: Int,
        val day: Int,
        val weekdayName: String?,
        val displayDate: String
    )

    private val months =
        PersianCalendar.jalaliMonthNames

    private val weekdayIndex =
        mapOf(
            "شنبه" to 0,
            "یکشنبه" to 1,
            "یشنبه" to 1,
            "یک شنبه" to 1,
            "دوشنبه" to 2,
            "سه شنبه" to 3,
            "سه‌شنبه" to 3,
            "چهارشنبه" to 4,
            "پنجشنبه" to 5,
            "پنج شنبه" to 5,
            "پنج‌شنبه" to 5,
            "جمعه" to 6
        )

    private val ordinalNumbers =
        mapOf(
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
     * Main date extraction function.
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
         * 1. Explicit numeric Jalali date
         *
         * 1405/05/12
         * 1405-05-12
         * 1405.05.12
         * -------------------------------------------------------------
         */
        val numericResult =
            extractNumericDate(text)

        if (numericResult != null) {
            return numericResult
        }

        /*
         * -------------------------------------------------------------
         * 2. Day + month
         *
         * 12 مرداد
         * 12 مرداد 1405
         *
         * This is checked before relative dates because it is more
         * explicit.
         * -------------------------------------------------------------
         */
        val numericDayMonthResult =
            extractNumericDayMonth(text)

        if (numericDayMonthResult != null) {
            return numericDayMonthResult
        }

        /*
         * -------------------------------------------------------------
         * 3. Ordinal day + month
         *
         * دوازدهم مرداد
         * بیست و پنجم شهریور
         * -------------------------------------------------------------
         */
        val ordinalDayMonthResult =
            extractOrdinalDayMonth(text)

        if (ordinalDayMonthResult != null) {
            return ordinalDayMonthResult
        }

        /*
         * -------------------------------------------------------------
         * 4. Relative dates
         *
         * دو روز دیگه
         * دو روز دیگر
         * دو روز بعد
         * سه روز دیگه
         * یک هفته بعد
         * -------------------------------------------------------------
         */
        val relativeResult =
            extractRelativeDate(text)

        if (relativeResult != null) {
            return relativeResult
        }

        /*
         * -------------------------------------------------------------
         * 4.5. SPECIAL RELATIVE PERIODS
         *
         * آخر هفته
         * آخر ماه
         * این هفته
         * همین هفته
         * هفته جاری
         * هفته فعلی
         * هفته آینده
         * هفته بعد
         * -------------------------------------------------------------
         */
        val specialPeriodResult =
            extractSpecialRelativePeriod(text)

        if (specialPeriodResult != null) {
            return specialPeriodResult
        }


        /*
         * -------------------------------------------------------------
         * 5. Explicit relative words
         *
         * پس فردا
         * فردا
         * امروز
         * امشب
         * -------------------------------------------------------------
         */
        val simpleRelativeResult =
            extractSimpleRelativeDate(text)

        if (simpleRelativeResult != null) {
            return simpleRelativeResult
        }

        /*
         * -------------------------------------------------------------
         * 6. Weekday
         *
         * شنبه
         * دوشنبه
         * پنجشنبه
         * -------------------------------------------------------------
         */
        val weekdayResult =
            extractWeekday(text)

        if (weekdayResult != null) {
            return weekdayResult
        }

        return null
    }

    // =====================================================================
    // NUMERIC DATE
    // =====================================================================

    private fun extractNumericDate(
        text: String
    ): Result? {

        val regex =
            Regex(
                """\b(1[34]\d{2})[/\-.](\d{1,2})[/\-.](\d{1,2})\b"""
            )

        val match =
            regex.find(text)
                ?: return null

        val year =
            match.groupValues[1]
                .toIntOrNull()
                ?: return null

        val month =
            match.groupValues[2]
                .toIntOrNull()
                ?: return null

        val day =
            match.groupValues[3]
                .toIntOrNull()
                ?: return null

        if (
            !isValidDate(
                year,
                month,
                day
            )
        ) {
            return null
        }

        return makeResult(
            year,
            month,
            day,
            displayDate =
                "$day ${monthName(month)} $year"
        )
    }

    // =====================================================================
    // DAY + MONTH
    // =====================================================================

    private fun extractNumericDayMonth(
        text: String
    ): Result? {

        val monthPattern =
            months
                .sortedByDescending {
                    it.length
                }
                .joinToString("|") {
                    Regex.escape(it)
                }

        val regex =
            Regex(
                """(?<!\d)(\d{1,2})\s*(?:ام)?\s*($monthPattern)(?:\s+(1[34]\d{2}))?"""
            )

        val match =
            regex.find(text)
                ?: return null

        val day =
            match.groupValues[1]
                .toIntOrNull()
                ?: return null

        val monthName =
            match.groupValues[2]

        val month =
            months.indexOf(monthName) + 1

        if (
            month <= 0 ||
            day !in 1..31
        ) {
            return null
        }

        val year =
            match.groupValues[3]
                .toIntOrNull()
                ?: PersianCalendar.todayJalali().first

        if (
            !isValidDate(
                year,
                month,
                day
            )
        ) {
            return null
        }

        return makeResult(
            year,
            month,
            day,
            displayDate =
                "$day $monthName $year"
        )
    }

    // =====================================================================
    // ORDINAL DAY + MONTH
    // =====================================================================

    private fun extractOrdinalDayMonth(
        text: String
    ): Result? {

        val ordinalPattern =
            ordinalNumbers.keys
                .sortedByDescending {
                    it.length
                }
                .joinToString("|") {
                    Regex.escape(it)
                }

        val monthPattern =
            months
                .sortedByDescending {
                    it.length
                }
                .joinToString("|") {
                    Regex.escape(it)
                }

        val regex =
            Regex(
                """($ordinalPattern)\s+($monthPattern)(?:\s+(1[34]\d{2}))?"""
            )

        val match =
            regex.find(text)
                ?: return null

        val day =
            ordinalNumbers[
                match.groupValues[1]
            ]
                ?: return null

        val month =
            months.indexOf(
                match.groupValues[2]
            ) + 1

        if (
            month <= 0
        ) {
            return null
        }

        val year =
            match.groupValues[3]
                .toIntOrNull()
                ?: PersianCalendar.todayJalali().first

        if (
            !isValidDate(
                year,
                month,
                day
            )
        ) {
            return null
        }

        return makeResult(
            year,
            month,
            day,
            displayDate =
                "$day ${match.groupValues[2]} $year"
        )
    }

    // =====================================================================
    // RELATIVE DATE
    // =====================================================================

    private fun extractRelativeDate(
        text: String
    ): Result? {

        /*
         * Important:
         *
         * "دیگه" is intentionally included.
         *
         * This fixes the original failure:
         *
         *   دو روز دیگه
         *
         * which was not recognized by the previous implementation.
         */

        val numberPattern =
            buildNumberPattern()

        val regex =
            Regex(
                """($numberPattern)\s+روز\s+(دیگه|دیگر|بعد)"""
            )

        val match =
            regex.find(text)

        if (match != null) {

            val numberText =
                match.groupValues[1]

            val offset =
                PersianNumberParser.parse(
                    numberText
                )
                    ?: return null

            if (
                offset < 0 ||
                offset > 365
            ) {
                return null
            }

            return shiftFromToday(
                offset = offset,
                display = "$numberText روز ${match.groupValues[2]}"
            )
        }

        /*
         * Numeric form:
         *
         * 2 روز دیگه
         * 3 روز بعد
         */
        val digitRegex =
            Regex(
                """(\d{1,3})\s+روز\s+(دیگه|دیگر|بعد)"""
            )

        val digitMatch =
            digitRegex.find(text)

        if (digitMatch != null) {

            val offset =
                digitMatch
                    .groupValues[1]
                    .toIntOrNull()
                    ?: return null

            if (
                offset !in 0..365
            ) {
                return null
            }

            return shiftFromToday(
                offset = offset,
                display =
                    "${offset} روز ${digitMatch.groupValues[2]}"
            )
        }

        /*
         * هفته بعد / هفته دیگر / هفته دیگه
         */
        val weekRegex =
            Regex(
                """(یک|یک\s+هفته|دو|سه|\d+)\s*هفته\s*(دیگه|دیگر|بعد)"""
            )

        val weekMatch =
            weekRegex.find(text)

        if (weekMatch != null) {

            val rawNumber =
                weekMatch.groupValues[1]
                    .replace(
                        " هفته",
                        ""
                    )
                    .trim()

            val weeks =
                PersianNumberParser.parse(
                    rawNumber
                )
                    ?: return null

            if (
                weeks !in 1..52
            ) {
                return null
            }

            val offset =
                weeks * 7

            return shiftFromToday(
                offset = offset,
                display =
                    "$rawNumber هفته ${weekMatch.groupValues[2]}"
            )
        }

        /*
         * "یک هفته دیگر"
         */
        if (
            text.contains(
                "یک هفته دیگر"
            ) ||
            text.contains(
                "یک هفته دیگه"
            ) ||
            text.contains(
                "یک هفته بعد"
            )
        ) {
            return shiftFromToday(
                offset = 7,
                display = "یک هفته بعد"
            )
        }

        return null
    }

    
    // =====================================================================
    // SPECIAL RELATIVE PERIODS
    // =====================================================================

    private fun extractSpecialRelativePeriod(
        text: String
    ): Result? {

        val today =
            PersianCalendar.todayJalali()

        val todayYear =
            today.first

        val todayMonth =
            today.second

        val todayDay =
            today.third

        // -------------------------------------------------------------
        // آخر ماه / پایان ماه
        //
        // Example:
        //   امروز: 15 مرداد
        //   آخر ماه -> 31 مرداد
        //
        // For month 12:
        //   29 or 30 depending on Jalali leap year.
        // -------------------------------------------------------------

        if (
            text.contains("آخر ماه") ||
            text.contains("پایان ماه")
        ) {

            val lastDay =
                daysInJalaliMonth(
                    todayYear,
                    todayMonth
                )

            return makeResult(
                year = todayYear,
                month = todayMonth,
                day = lastDay,
                displayDate =
                    "آخر ماه ${monthName(todayMonth)}"
            )
        }

        // -------------------------------------------------------------
        // آخر هفته / پایان هفته
        //
        // Jalali week:
        //
        // شنبه    = 0
        // یکشنبه  = 1
        // دوشنبه  = 2
        // سه شنبه = 3
        // چهارشنبه= 4
        // پنجشنبه = 5
        // جمعه    = 6
        //
        // Therefore "آخر هفته" means Friday of the current week.
        // -------------------------------------------------------------

        if (
            text.contains("آخر هفته") ||
            text.contains("پایان هفته")
        ) {

            val todayName =
                PersianCalendar.weekdayName(
                    todayYear,
                    todayMonth,
                    todayDay
                )

            val todayIndex =
                weekdayIndex[todayName]
                    ?: return null

            val daysUntilFriday =
                6 - todayIndex

            return shiftFromToday(
                offset = daysUntilFriday,
                display = "آخر هفته"
            )
        }

        // -------------------------------------------------------------
        // Current week
        //
        // این هفته
        // همین هفته
        // هفته جاری
        // هفته فعلی
        //
        // A bare "current week" does not contain a specific day.
        // We therefore do not manufacture an appointment date here.
        //
        // It becomes useful when combined with a weekday, e.g.:
        //
        //   این هفته جمعه
        //   همین هفته سه شنبه
        //
        // The weekday extractor will handle those cases.
        // -------------------------------------------------------------

        if (
            text.contains("این هفته") ||
            text.contains("همین هفته") ||
            text.contains("هفته جاری") ||
            text.contains("هفته فعلی")
        ) {

            val weekdayResult =
                extractWeekdayWithWeekQualifier(
                    text,
                    weekOffset = 0
                )

            if (weekdayResult != null) {
                return weekdayResult
            }

            /*
             * There is no specific day in phrases such as:
             *
             *   "این هفته"
             *   "همین هفته"
             *
             * Do not incorrectly assign today's date.
             */
            return null
        }

        // -------------------------------------------------------------
        // Next week
        //
        // هفته آینده
        // هفته بعد
        // هفته بعدی
        //
        // If a weekday exists:
        //
        //   هفته آینده سه شنبه
        //
        // resolve that exact weekday in next week.
        // -------------------------------------------------------------

        if (
            text.contains("هفته آینده") ||
            text.contains("هفته بعد") ||
            text.contains("هفته بعدی")
        ) {

            val weekdayResult =
                extractWeekdayWithWeekQualifier(
                    text,
                    weekOffset = 1
                )

            if (weekdayResult != null) {
                return weekdayResult
            }

            /*
             * A bare "هفته بعد" does not specify a day.
             * We use the first day of next week (Saturday) as a
             * deterministic fallback.
             */
            return shiftFromToday(
                offset =
                    daysUntilNextSaturday(today),
                display = "هفته بعد"
            )
        }

        return null
    }

    // =====================================================================
    // SIMPLE RELATIVE DATE
    // =====================================================================

    private fun extractSimpleRelativeDate(
        text: String
    ): Result? {

        /*
         * Check "پس فردا" before "فردا".
         */
        if (
            text.contains(
                "پس‌فردا"
            ) ||
            text.contains(
                "پس فردا"
            )
        ) {

            return shiftFromToday(
                offset = 2,
                display = "پس‌فردا"
            )
        }

        if (
            text.contains(
                "فردا"
            )
        ) {

            return shiftFromToday(
                offset = 1,
                display = "فردا"
            )
        }

        if (
            text.contains(
                "امروز"
            ) ||
            text.contains(
                "امشب"
            )
        ) {

            val today =
                PersianCalendar.todayJalali()

            return makeResult(
                year = today.first,
                month = today.second,
                day = today.third,
                displayDate =
                    if (
                        text.contains("امشب")
                    ) {
                        "امشب"
                    } else {
                        "امروز"
                    }
            )
        }

        return null
    }

    // =====================================================================
    // WEEKDAY
    // =====================================================================

    private fun extractWeekday(
        text: String
    ): Result? {

        val weekday =
            weekdayIndex.keys
                .sortedByDescending {
                    it.length
                }
                .firstOrNull {
                    containsWord(
                        text,
                        it
                    )
                }
                ?: return null

        val targetIndex =
            weekdayIndex[weekday]
                ?: return null

        val today =
            PersianCalendar.todayJalali()

        val todayName =
            PersianCalendar.weekdayName(
                today.first,
                today.second,
                today.third
            )

        val todayIndex =
            weekdayIndex[todayName]
                ?: return null

        var offset =
            (
                targetIndex -
                    todayIndex +
                    7
                ) % 7

        /*
         * If user only says:
         *
         *   شنبه
         *
         * interpret it as the upcoming Saturday rather than
         * today when today itself is Saturday.
         */
        if (
            offset == 0
        ) {
            offset = 7
        }

        return shiftFromToday(
            offset = offset,
            display = weekday
        )
    }

    // =====================================================================
    // WEEK QUALIFIED WEEKDAY
    // =====================================================================

    private fun extractWeekdayWithWeekQualifier(
        text: String,
        weekOffset: Int
    ): Result? {

        val normalizedText =
            text
                .replace(
                    "یشنبه",
                    "یکشنبه"
                )
                .replace(
                    "یک شنبه",
                    "یکشنبه"
                )
                .replace(
                    "سه شنبه",
                    "سه‌شنبه"
                )
                .replace(
                    "پنج شنبه",
                    "پنجشنبه"
                )

        val weekday =
            weekdayIndex.keys
                .sortedByDescending {
                    it.length
                }
                .firstOrNull {
                    containsWord(
                        normalizedText,
                        it
                    )
                }
                ?: return null

        val targetIndex =
            weekdayIndex[weekday]
                ?: return null

        val today =
            PersianCalendar.todayJalali()

        val todayName =
            PersianCalendar.weekdayName(
                today.first,
                today.second,
                today.third
            )

        val todayIndex =
            weekdayIndex[todayName]
                ?: return null

        /*
         * Find the beginning of the current Jalali week.
         *
         * Saturday = 0
         */
        val daysFromWeekStart =
            todayIndex

        /*
         * Move to the beginning of the requested week.
         *
         * weekOffset:
         *
         * 0 = current week
         * 1 = next week
         */
        val daysToTarget =
            (
                weekOffset * 7
            ) -
                daysFromWeekStart +
                targetIndex

        /*
         * For "این هفته سه شنبه", if today is already after
         * Tuesday, the phrase still refers to the current week's
         * Tuesday rather than next Tuesday.
         */
        return shiftFromToday(
            offset = daysToTarget,
            display =
                if (weekOffset == 0) {
                    weekday
                } else {
                    "$weekday هفته بعد"
                }
        )
    }


    // =====================================================================
    // NEXT SATURDAY
    // =====================================================================

    private fun daysUntilNextSaturday(
        today: Triple<Int, Int, Int>
    ): Int {

        val todayName =
            PersianCalendar.weekdayName(
                today.first,
                today.second,
                today.third
            )

        val todayIndex =
            weekdayIndex[todayName]
                ?: return 7

        /*
         * Saturday = 0.
         *
         * If today is Saturday, "هفته بعد" means the Saturday
         * seven days later.
         */
        return if (todayIndex == 0) {
            7
        } else {
            7 - todayIndex
        }
    }


    // =====================================================================
    // JALALI MONTH LENGTH
    // =====================================================================

    private fun daysInJalaliMonth(
        year: Int,
        month: Int
    ): Int {

        /*
         * Farvardin through Shahrivar:
         * 31 days
         */
        if (month in 1..6) {
            return 31
        }

        /*
         * Mehr through Bahman:
         * 30 days
         */
        if (month in 7..11) {
            return 30
        }

        /*
         * Esfand:
         *
         * 29 days in normal years
         * 30 days in leap years
         *
         * Instead of implementing another leap-year algorithm,
         * validate day 30 using the existing Jalali calendar.
         */
        return if (
            isValidDate(
                year,
                12,
                30
            )
        ) {
            30
        } else {
            29
        }
    }

    // =====================================================================
    // DATE SHIFT
    // =====================================================================
    
    private fun shiftFromToday(
        offset: Int,
        display: String
    ): Result {

        val today =
            PersianCalendar.todayJalali()

        val shifted =
            shiftJalaliDay(
                year = today.first,
                month = today.second,
                day = today.third,
                offsetDays = offset
            )

        return makeResult(
            year = shifted.first,
            month = shifted.second,
            day = shifted.third,
            displayDate = display
        )
    }

    private fun shiftJalaliDay(
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
            calendar.get(
                java.util.Calendar.YEAR
            ),
            calendar.get(
                java.util.Calendar.MONTH
            ) + 1,
            calendar.get(
                java.util.Calendar.DAY_OF_MONTH
            )
        )
    }

    // =====================================================================
    // RESULT
    // =====================================================================

    private fun makeResult(
        year: Int,
        month: Int,
        day: Int,
        displayDate: String
    ): Result {

        val weekday =
            PersianCalendar.weekdayName(
                year,
                month,
                day
            )

        return Result(
            year = year,
            month = month,
            day = day,
            weekdayName = weekday,
            displayDate = displayDate
        )
    }

    // =====================================================================
    // VALIDATION
    // =====================================================================

    private fun isValidDate(
        year: Int,
        month: Int,
        day: Int
    ): Boolean {

        if (
            year !in 1300..1500
        ) {
            return false
        }

        if (
            month !in 1..12
        ) {
            return false
        }

        if (
            day !in 1..31
        ) {
            return false
        }

        /*
         * Validate by attempting Jalali -> Gregorian conversion.
         *
         * This also protects against impossible dates such as
         * 31 Esfand in a non-leap year.
         */
        return try {

            PersianCalendar.jalaliToGregorian(
                year,
                month,
                day
            )

            true

        } catch (
            _: Exception
        ) {

            false
        }
    }

    // =====================================================================
    // HELPERS
    // =====================================================================

    private fun monthName(
        month: Int
    ): String {

        return months.getOrNull(
            month - 1
        ) ?: ""
    }

    private fun containsWord(
        text: String,
        word: String
    ): Boolean {

        val index =
            text.indexOf(word)

        if (
            index < 0
        ) {
            return false
        }

        val beforeOk =
            index == 0 ||
                text[index - 1].isWhitespace()

        val end =
            index + word.length

        val afterOk =
            end >= text.length ||
                text[end].isWhitespace()

        return beforeOk && afterOk
    }

    /**
     * Builds a pattern from the number parser rather than keeping
     * another duplicate number dictionary here.
     */
    private fun buildNumberPattern(): String {

        return PersianNumberParser
            .numberWords()
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
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
         * Persian/Arabic characters.
         */
        text = text
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ۀ', 'ه')
            .replace('ة', 'ه')

        /*
         * Persian and Arabic digits.
         */
        text =
            normalizeDigits(text)

        /*
         * Common speech-recognition variants.
         */
        text = text
            .replace(
                "پس فردا",
                "پس‌فردا"
            )
            .replace(
                "سه شنبه",
                "سه‌شنبه"
            )
            .replace(
                "پنج شنبه",
                "پنجشنبه"
            )
            .replace(
                "یشنبه",
                "یکشنبه"
            )
            .replace(
                "یک شنبه",
                "یکشنبه"
            )

        /*
         * Colloquial "دیگه" is deliberately preserved.
         *
         * We need it for:
         *
         *   دو روز دیگه
         */
        text =
            text.replace(
                "ديگه",
                "دیگه"
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
