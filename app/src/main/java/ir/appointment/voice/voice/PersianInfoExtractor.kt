package ir.appointment.voice.voice

import ir.appointment.voice.voice.extractor.DateExtractor
import ir.appointment.voice.voice.extractor.TimeExtractor
import ir.appointment.voice.voice.extractor.PersonExtractor

/** Result of extracting appointment info from a Persian utterance. */
data class ExtractedAppointment(
    val rawText: String,
    val personName: String?,
    val location: String?,
    val jalaliYear: Int?,
    val jalaliMonth: Int?,
    val jalaliDay: Int?,
    val weekdayName: String?,
    val hour: Int?,
    val minute: Int?,
    val displayDate: String?,
    val displayTime: String?,
    val sortTimestamp: Long?
)

/**
 * Main rule-based extractor for Persian appointment speech.
 *
 * Specialized extractors are used first:
 *
 *   DateExtractor
 *   TimeExtractor
 *
 * The older logic is retained as fallback so that introducing the
 * improved extractors does not reduce compatibility with existing
 * phrases.
 */
object PersianInfoExtractor {

    private val weekdays = listOf(
        "شنبه",
        "یکشنبه",
        "دوشنبه",
        "سه شنبه",
        "سه‌شنبه",
        "چهارشنبه",
        "پنجشنبه",
        "پنج شنبه",
        "پنج‌شنبه",
        "جمعه"
    )

    private val weekdayIndex = mapOf(
        "شنبه" to 0,
        "یکشنبه" to 1,
        "دوشنبه" to 2,
        "سه شنبه" to 3,
        "سه‌شنبه" to 3,
        "چهارشنبه" to 4,
        "پنجشنبه" to 5,
        "پنج شنبه" to 5,
        "پنج‌شنبه" to 5,
        "جمعه" to 6
    )

    private val jalaliMonths =
        PersianCalendar.jalaliMonthNames

    /**
     * Kept as fallback for compatibility with the old extractor.
     */
    private val wordNumbers = mapOf(
        "صفر" to 0,
        "یک" to 1,
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
        "بیست و یک" to 21,
        "بیست و دو" to 22,
        "بیست و سه" to 23,
        "بیست و چهار" to 24,
        "بیست و پنج" to 25,
        "بیست و شش" to 26,
        "بیست و هفت" to 27,
        "بیست و هشت" to 28,
        "بیست و نه" to 29,
        "سی" to 30,
        "سی و یک" to 31
    )

    private val ordinalDayNumbers = mapOf(
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
        "سی ام" to 30,
        "سی‌ام" to 30,
        "سی و یکم" to 31
    )

    // =====================================================================
    // MAIN
    // =====================================================================

    fun extract(text: String): ExtractedAppointment {

        val normalized =
            normalizeText(text)

        // -------------------------------------------------------------
        // 1. PERSON
        // -------------------------------------------------------------

        /*
         * New person extractor is deliberately executed first.
         *
         * Examples:
         *
         *   نوبت دکتر احمدی دارم
         *   فردا ساعت پنج دکتر احمدی
         *   با دکتر احمدی قرار دارم
         *   با علی قرار دارم
         */
        val improvedPerson =
            extractPersonImproved(normalized)

        val oldPerson =
            extractAfterKeyword(
                normalized,
                listOf(
                    "با آقای",
                    "با خانم",
                    "با دکتر",
                    "با"
                )
            )

        val person =
            improvedPerson
                ?: oldPerson

        // -------------------------------------------------------------
        // 2. LOCATION
        // -------------------------------------------------------------

        val location =
            extractAfterKeyword(
                normalized,
                listOf(
                    "در محل",
                    "در آدرس",
                    "در"
                )
            )
                ?: extractLocationAfterGoVerb(
                    normalized
                )

        // -------------------------------------------------------------
        // 3. DATE
        // -------------------------------------------------------------

        val improvedDate =
            DateExtractor.extract(
                normalized
            )

        var jy: Int? =
            improvedDate?.year

        var jm: Int? =
            improvedDate?.month

        var jd: Int? =
            improvedDate?.day

        var displayDate: String? =
            improvedDate?.displayDate

        val spokenWeekday =
            weekdays
                .sortedByDescending {
                    it.length
                }
                .firstOrNull {
                    normalized.contains(it)
                }

        /*
         * Fallback to old date extraction if the new extractor
         * could not resolve a date.
         */
        if (
            improvedDate == null
        ) {

            val oldDate =
                extractDateLegacy(
                    normalized,
                    spokenWeekday
                )

            jy = oldDate?.first
            jm = oldDate?.second
            jd = oldDate?.third
            displayDate = oldDate?.fourth
        }

        /*
         * If year is missing but month/day exist, use current Jalali year.
         */
        if (
            jy == null &&
            jm != null &&
            jd != null
        ) {
            jy =
                PersianCalendar
                    .todayJalali()
                    .first
        }

        /*
         * If the improved extractor returned a relative expression
         * such as "دو روز دیگه", keep its resolved date but generate
         * a useful display string if necessary.
         */
        if (
            displayDate == null &&
            jy != null &&
            jm != null &&
            jd != null
        ) {
            displayDate =
                "$jd ${jalaliMonths.getOrNull(jm - 1) ?: ""} $jy"
        }

        // -------------------------------------------------------------
        // 4. WEEKDAY
        // -------------------------------------------------------------

        val weekday =
            if (
                jy != null &&
                jm != null &&
                jd != null
            ) {
                PersianCalendar.weekdayName(
                    jy,
                    jm,
                    jd
                ) ?: spokenWeekday
            } else {
                spokenWeekday
            }

        // -------------------------------------------------------------
        // 5. TIME
        // -------------------------------------------------------------

        val improvedTime =
            TimeExtractor.extract(
                normalized
            )

        var hour: Int? =
            improvedTime?.hour

        var minute: Int? =
            improvedTime?.minute

        var displayTime: String? =
            improvedTime?.displayTime

        /*
         * Old implementation remains as fallback.
         */
        if (
            improvedTime == null
        ) {

            val oldTime =
                extractTimeLegacy(
                    normalized
                )

            hour = oldTime?.first
            minute = oldTime?.second
            displayTime = oldTime?.third
        }

        // -------------------------------------------------------------
        // 6. SORT TIMESTAMP
        // -------------------------------------------------------------

        val sortTs =
            if (
                jy != null &&
                jm != null &&
                jd != null
            ) {
                PersianCalendar.toEpochMillis(
                    jy,
                    jm,
                    jd,
                    hour,
                    minute
                )
            } else {
                null
            }

        return ExtractedAppointment(
            rawText = text.trim(),
            personName = person,
            location = location,
            jalaliYear = jy,
            jalaliMonth = jm,
            jalaliDay = jd,
            weekdayName = weekday,
            hour = hour,
            minute = minute,
            displayDate = displayDate,
            displayTime = displayTime,
            sortTimestamp = sortTs
        )
    }

    // =====================================================================
    // IMPROVED PERSON EXTRACTION
    // =====================================================================

    private fun extractPersonImproved(
        text: String
    ): String? {

        /*
         * Most specific patterns first.
         *
         * Example:
         *
         *   نوبت دکتر احمدی دارم
         */
        val doctorPatterns =
            listOf(

                Regex(
                    """(?:نوبت|ویزیت|قرار)\s+(?:دکتر|دکترِ)\s+(.+?)(?=\s+(?:دارم|است|هست|داریم|میرم|می‌روم|خواهم|در|ساعت|روز|فردا|امروز|پس‌فردا)|$)"""
                ),

                Regex(
                    """(?:با|پیش)\s+(?:دکتر|دکترِ)\s+(.+?)(?=\s+(?:قرار|نوبت|دارم|است|هست|داریم|میرم|می‌روم|خواهم|در|ساعت|روز|فردا|امروز|پس‌فردا)|$)"""
                ),

                Regex(
                    """(?:دکتر|دکترِ)\s+(.+?)(?=\s+(?:دارم|است|هست|داریم|میرم|می‌روم|خواهم|در|ساعت|روز|فردا|امروز|پس‌فردا)|$)"""
                )
            )

        for (
            pattern in doctorPatterns
        ) {

            val match =
                pattern.find(text)
                    ?: continue

            val name =
                cleanPersonName(
                    match.groupValues
                        .getOrNull(1)
                )

            if (
                !name.isNullOrBlank()
            ) {
                return "دکتر $name"
            }
        }

        /*
         * Normal "با شخص" patterns.
         *
         * Examples:
         *
         *   با علی قرار دارم
         *   با محمد احمدی جلسه دارم
         */
        val normalPatterns =
            listOf(

                Regex(
                    """(?:با|همراه)\s+(?:آقای|خانم)\s+(.+?)(?=\s+(?:قرار|نوبت|جلسه|دارم|است|هست|در|ساعت|روز|فردا|امروز)|$)"""
                ),

                Regex(
                    """(?:با|همراه)\s+(.+?)(?=\s+(?:قرار|نوبت|جلسه|دارم|است|هست|در|ساعت|روز|فردا|امروز)|$)"""
                )
            )

        for (
            pattern in normalPatterns
        ) {

            val match =
                pattern.find(text)
                    ?: continue

            val name =
                cleanPersonName(
                    match.groupValues
                        .getOrNull(1)
                )

            if (
                !name.isNullOrBlank()
            ) {
                return name
            }
        }

        return null
    }

    private fun cleanPersonName(
        value: String?
    ): String? {

        if (
            value == null
        ) {
            return null
        }

        var name =
            value.trim()

        name =
            name.trim(
                ',',
                '.',
                '،',
                '؛',
                ':',
                '!',
                '?'
            )

        /*
         * Remove obvious trailing words that can accidentally
         * be captured by speech-recognition variations.
         */
        val stopWords =
            setOf(
                "دارم",
                "است",
                "هست",
                "داریم",
                "میرم",
                "می‌روم",
                "خواهم",
                "قرار",
                "نوبت",
                "جلسه",
                "ساعت",
                "روز",
                "فردا",
                "امروز",
                "پس‌فردا",
                "در"
            )

        val words =
            name.split(
                Regex("\\s+")
            )

        val cleaned =
            mutableListOf<String>()

        for (
            word in words
        ) {

            if (
                stopWords.contains(word)
            ) {
                break
            }

            if (
                word.isNotBlank()
            ) {
                cleaned.add(word)
            }
        }

        if (
            cleaned.isEmpty()
        ) {
            return null
        }

        return cleaned.joinToString(" ")
    }

    // =====================================================================
    // LEGACY DATE FALLBACK
    // =====================================================================

    private fun extractDateLegacy(
        text: String,
        spokenWeekday: String?
    ): Quadruple? {

        // -------------------------------------------------------------
        // Numeric date
        // -------------------------------------------------------------

        val numericDateRegex =
            Regex(
                """(1[34]\d{2})[/\-](\d{1,2})[/\-](\d{1,2})"""
            )

        val numericMatch =
            numericDateRegex.find(text)

        if (
            numericMatch != null
        ) {

            return Quadruple(
                numericMatch
                    .groupValues[1]
                    .toIntOrNull(),

                numericMatch
                    .groupValues[2]
                    .toIntOrNull(),

                numericMatch
                    .groupValues[3]
                    .toIntOrNull(),

                null
            )
        }

        // -------------------------------------------------------------
        // Numeric day + month
        // -------------------------------------------------------------

        val monthNamePattern =
            jalaliMonths.joinToString("|")

        val dayMonthYearRegex =
            Regex(
                """(\d{1,2})\s*(?:ام)?\s*($monthNamePattern)(?:\s+(1[34]\d{2}))?"""
            )

        val dayMonthMatch =
            dayMonthYearRegex.find(text)

        if (
            dayMonthMatch != null
        ) {

            val day =
                dayMonthMatch
                    .groupValues[1]
                    .toIntOrNull()

            val month =
                jalaliMonths.indexOf(
                    dayMonthMatch.groupValues[2]
                ) + 1

            val year =
                dayMonthMatch
                    .groupValues[3]
                    .toIntOrNull()

            return Quadruple(
                year,
                month,
                day,
                null
            )
        }

        // -------------------------------------------------------------
        // Ordinal day + month
        // -------------------------------------------------------------

        val ordinalPattern =
            ordinalDayNumbers.keys
                .sortedByDescending {
                    it.length
                }
                .joinToString("|")

        val ordinalRegex =
            Regex(
                """($ordinalPattern)\s+($monthNamePattern)(?:\s+(1[34]\d{2}))?"""
            )

        val ordinalMatch =
            ordinalRegex.find(text)

        if (
            ordinalMatch != null
        ) {

            val day =
                ordinalDayNumbers[
                    ordinalMatch.groupValues[1]
                ]

            val month =
                jalaliMonths.indexOf(
                    ordinalMatch.groupValues[2]
                ) + 1

            val year =
                ordinalMatch
                    .groupValues[3]
                    .toIntOrNull()

            return Quadruple(
                year,
                month,
                day,
                null
            )
        }

        // -------------------------------------------------------------
        // Relative days
        // -------------------------------------------------------------

        val digitRelativeRegex =
            Regex(
                """(\d{1,3})\s*روز\s*(دیگه|دیگر|بعد)"""
            )

        val digitRelativeMatch =
            digitRelativeRegex.find(text)

        if (
            digitRelativeMatch != null
        ) {

            val offset =
                digitRelativeMatch
                    .groupValues[1]
                    .toIntOrNull()

            if (
                offset != null
            ) {

                val today =
                    PersianCalendar.todayJalali()

                val shifted =
                    shiftJalaliDay(
                        today.first,
                        today.second,
                        today.third,
                        offset
                    )

                return Quadruple(
                    shifted.first,
                    shifted.second,
                    shifted.third,
                    null
                )
            }
        }

        val wordRelativeRegex =
            Regex(
                """(یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه|ده)\s*روز\s*(دیگه|دیگر|بعد)"""
            )

        val wordRelativeMatch =
            wordRelativeRegex.find(text)

        if (
            wordRelativeMatch != null
        ) {

            val offset =
                wordNumbers[
                    wordRelativeMatch
                        .groupValues[1]
                ]

            if (
                offset != null
            ) {

                val today =
                    PersianCalendar.todayJalali()

                val shifted =
                    shiftJalaliDay(
                        today.first,
                        today.second,
                        today.third,
                        offset
                    )

                return Quadruple(
                    shifted.first,
                    shifted.second,
                    shifted.third,
                    null
                )
            }
        }

        // -------------------------------------------------------------
        // پس فردا
        // -------------------------------------------------------------

        if (
            text.contains("پس فردا") ||
            text.contains("پس‌فردا")
        ) {

            val today =
                PersianCalendar.todayJalali()

            val shifted =
                shiftJalaliDay(
                    today.first,
                    today.second,
                    today.third,
                    2
                )

            return Quadruple(
                shifted.first,
                shifted.second,
                shifted.third,
                "پس‌فردا"
            )
        }

        // -------------------------------------------------------------
        // فردا
        // -------------------------------------------------------------

        if (
            text.contains("فردا")
        ) {

            val today =
                PersianCalendar.todayJalali()

            val shifted =
                shiftJalaliDay(
                    today.first,
                    today.second,
                    today.third,
                    1
                )

            return Quadruple(
                shifted.first,
                shifted.second,
                shifted.third,
                "فردا"
            )
        }

        // -------------------------------------------------------------
        // امروز / امشب
        // -------------------------------------------------------------

        if (
            text.contains("امروز") ||
            text.contains("امشب")
        ) {

            val today =
                PersianCalendar.todayJalali()

            return Quadruple(
                today.first,
                today.second,
                today.third,
                if (
                    text.contains("امشب")
                ) {
                    "امشب"
                } else {
                    "امروز"
                }
            )
        }

        // -------------------------------------------------------------
        // Weekday
        // -------------------------------------------------------------

        if (
            spokenWeekday != null
        ) {

            val targetIdx =
                weekdayIndex[
                    spokenWeekday
                ]

            if (
                targetIdx != null
            ) {

                val today =
                    PersianCalendar.todayJalali()

                val todayName =
                    PersianCalendar.weekdayName(
                        today.first,
                        today.second,
                        today.third
                    )

                val todayIdx =
                    weekdayIndex[
                        todayName
                    ] ?: 0

                var offset =
                    (
                        targetIdx -
                            todayIdx +
                            7
                        ) % 7

                if (
                    offset == 0
                ) {
                    offset = 7
                }

                val shifted =
                    shiftJalaliDay(
                        today.first,
                        today.second,
                        today.third,
                        offset
                    )

                return Quadruple(
                    shifted.first,
                    shifted.second,
                    shifted.third,
                    null
                )
            }
        }

        return null
    }

    // =====================================================================
    // LEGACY TIME FALLBACK
    // =====================================================================

    private fun extractTimeLegacy(
        text: String
    ): Triple<Int, Int, String>? {

        var hour: Int? = null
        var minute: Int? = null

        // -------------------------------------------------------------
        // 17:30
        // -------------------------------------------------------------

        val colonRegex =
            Regex(
                """ساعت\s*(\d{1,2})[:٫](\d{2})"""
            )

        val colonMatch =
            colonRegex.find(text)

        if (
            colonMatch != null
        ) {

            hour =
                colonMatch
                    .groupValues[1]
                    .toIntOrNull()

            minute =
                colonMatch
                    .groupValues[2]
                    .toIntOrNull()
        }

        // -------------------------------------------------------------
        // ساعت 5
        // -------------------------------------------------------------

        if (
            hour == null
        ) {

            val numericRegex =
                Regex(
                    """ساعت\s*(\d{1,2})"""
                )

            val numericMatch =
                numericRegex.find(text)

            if (
                numericMatch != null
            ) {

                hour =
                    numericMatch
                        .groupValues[1]
                        .toIntOrNull()

                minute = 0
            }
        }

        // -------------------------------------------------------------
        // ساعت پنج / پنج و نیم / پنج و ربع
        // -------------------------------------------------------------

        if (
            hour == null
        ) {

            val timeRegex =
                Regex(
                    """ساعت\s*([آ-ی]+)(?:\s+و\s+(نیم|ربع))?"""
                )

            val match =
                timeRegex.find(text)

            if (
                match != null
            ) {

                hour =
                    wordNumbers[
                        match.groupValues[1]
                    ]

                minute =
                    when (
                        match.groupValues[2]
                    ) {
                        "نیم" -> 30
                        "ربع" -> 15
                        else -> 0
                    }
            }
        }

        if (
            hour == null
        ) {
            return null
        }

        // -------------------------------------------------------------
        // AM / PM
        // -------------------------------------------------------------

        val isPm =
            text.contains("عصر") ||
                text.contains("بعد از ظهر") ||
                text.contains("بعدازظهر")

        val isNight =
            text.contains("شب")

        if (
            (isPm || isNight) &&
            hour in 1..11
        ) {
            hour += 12
        }

        /*
         * "ربع کم" / "کم ربع"
         */
        if (
            text.contains("ربع کم") ||
            text.contains("کم ربع")
        ) {

            hour =
                if (
                    hour == 0
                ) {
                    23
                } else {
                    hour - 1
                }

            minute = 45
        }

        if (
            hour !in 0..23
        ) {
            return null
        }

        if (
            minute !in 0..59
        ) {
            return null
        }

        return Triple(
            hour,
            minute ?: 0,
            String.format(
                "%02d:%02d",
                hour,
                minute ?: 0
            )
        )
    }

    // =====================================================================
    // DATE SHIFT
    // =====================================================================

    private fun shiftJalaliDay(
        y: Int,
        m: Int,
        d: Int,
        offsetDays: Int
    ): Triple<Int, Int, Int> {

        val gregorian =
            PersianCalendar.jalaliToGregorian(
                y,
                m,
                d
            )

        val cal =
            java.util.Calendar.getInstance()

        cal.clear()

        cal.set(
            gregorian.first,
            gregorian.second - 1,
            gregorian.third
        )

        cal.add(
            java.util.Calendar.DAY_OF_MONTH,
            offsetDays
        )

        return PersianCalendar.gregorianToJalali(
            cal.get(
                java.util.Calendar.YEAR
            ),
            cal.get(
                java.util.Calendar.MONTH
            ) + 1,
            cal.get(
                java.util.Calendar.DAY_OF_MONTH
            )
        )
    }

    // =====================================================================
    // GENERIC KEYWORD EXTRACTION
    // =====================================================================

    private fun extractAfterKeyword(
        text: String,
        keywords: List<String>,
        maxWords: Int = 2
    ): String? {

        for (
            kw in keywords
        ) {

            var searchFrom = 0

            while (true) {

                val idx =
                    text.indexOf(
                        kw,
                        searchFrom
                    )

                if (
                    idx < 0
                ) {
                    break
                }

                val precededByBoundary =
                    idx == 0 ||
                        text[idx - 1].isWhitespace()

                val followedByBoundary =
                    idx + kw.length >= text.length ||
                        text[idx + kw.length].isWhitespace()

                if (
                    precededByBoundary &&
                    followedByBoundary
                ) {

                    val after =
                        text.substring(
                            idx + kw.length
                        ).trim()

                    if (
                        after.isNotEmpty()
                    ) {

                        val stopWords =
                            setOf(
                                "در",
                                "با",
                                "ساعت",
                                "روز",
                                "تاریخ",
                                "فردا",
                                "امروز",
                                "پس‌فردا",
                                "پس",
                                "دیگه",
                                "دیگر",
                                "بعد",
                                "باید",
                                "برم",
                                "بروم",
                                "میرم",
                                "می‌روم",
                                "قراره",
                                "قرار",
                                "دارم",
                                "است",
                                "هست"
                            )

                        val words =
                            after.split(
                                Regex("\\s+")
                            )

                        val collected =
                            mutableListOf<String>()

                        for (
                            w in words
                        ) {

                            val clean =
                                w.trim(
                                    ',',
                                    '.',
                                    '،',
                                    '؛',
                                    ':'
                                )

                            if (
                                clean.isEmpty()
                            ) {
                                continue
                            }

                            if (
                                stopWords.contains(
                                    clean
                                )
                            ) {
                                break
                            }

                            collected.add(
                                clean
                            )

                            if (
                                collected.size >= maxWords
                            ) {
                                break
                            }
                        }

                        if (
                            collected.isNotEmpty()
                        ) {
                            return collected.joinToString(
                                " "
                            )
                        }
                    }
                }

                searchFrom =
                    idx + kw.length
            }
        }

        return null
    }

    // =====================================================================
    // LOCATION
    // =====================================================================

    private fun extractLocationAfterGoVerb(
        text: String
    ): String? {

        val goVerbs =
            listOf(
                "می‌روم",
                "میرم",
                "برم",
                "بروم",
                "می‌رم"
            )

        return extractAfterKeyword(
            text,
            goVerbs,
            maxWords = 2
        )
    }

    // =====================================================================
    // NORMALIZATION
    // =====================================================================

    private fun normalizeText(
        input: String
    ): String {

        var text =
            normalizeDigits(
                input.trim()
            )

        text =
            text.replace(
                'ي',
                'ی'
            )

        text =
            text.replace(
                'ى',
                'ی'
            )

        text =
            text.replace(
                'ك',
                'ک'
            )

        text =
            text.replace(
                "پس فردا",
                "پس‌فردا"
            )

        text =
            text.replace(
                "سه شنبه",
                "سه‌شنبه"
            )

        text =
            text.replace(
                "پنج شنبه",
                "پنجشنبه"
            )

        /*
         * Normalize Arabic/other forms of "دیگه".
         */
        text =
            text.replace(
                "ديگه",
                "دیگه"
            )

        /*
         * Normalize ZWNJ to a space for regex matching.
         * Keep the special "پس‌فردا" form above intact.
         */
        text =
            text.replace(
                '\u200C',
                ' '
            )

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

        val sb =
            StringBuilder(
                input.length
            )

        for (
            ch in input
        ) {

            val pIdx =
                persianDigits.indexOf(ch)

            val aIdx =
                arabicDigits.indexOf(ch)

            when {

                pIdx >= 0 ->
                    sb.append(
                        pIdx
                    )

                aIdx >= 0 ->
                    sb.append(
                        aIdx
                    )

                else ->
                    sb.append(
                        ch
                    )
            }
        }

        return sb.toString()
    }

    // =====================================================================
    // SMALL INTERNAL DATA HOLDER
    // =====================================================================

    private data class Quadruple(
        val first: Int?,
        val second: Int?,
        val third: Int?,
        val fourth: String?
    )
}
