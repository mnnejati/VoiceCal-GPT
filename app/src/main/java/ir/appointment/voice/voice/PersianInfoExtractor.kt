package ir.appointment.voice.voice

import ir.appointment.voice.voice.extractor.DateExtractor
import ir.appointment.voice.voice.extractor.TimeExtractor
import ir.appointment.voice.voice.extractor.PersonExtractor
import ir.appointment.voice.voice.normalizer.PersianNormalizer

/** Result of extracting appointment information from Persian speech. */
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
 * Extraction order:
 *
 *  1. Persian normalization
 *  2. PersonExtractor
 *  3. DateExtractor
 *  4. TimeExtractor
 *  5. Legacy fallback logic
 *
 * No ML model or external dependency is used here.
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

    /*
     * Legacy number dictionary.
     *
     * Kept because the legacy date/time fallback still uses it.
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
    // MAIN EXTRACTION
    // =====================================================================

    fun extract(text: String): ExtractedAppointment {

        /*
         * First normalize the speech-recognition output.
         *
         * This keeps Persian/Arabic character variants, digits,
         * spaces and common spoken forms consistent.
         */
        val normalized =
            PersianNormalizer.normalize(text.trim())

        // -------------------------------------------------------------
        // 1. PERSON
        // -------------------------------------------------------------

        /*
         * PersonExtractor is the primary person extractor.
         *
         * Examples:
         *
         *   نوبت دکتر احمدی دارم
         *   فردا ساعت پنج نوبت دکتر احمدی دارم
         *   با دکتر محمدی قرار دارم
         *   جلسه با مهندس رضایی دارم
         */
        val person =
            PersonExtractor
                .extract(normalized)
                ?.name
                ?: extractAfterKeyword(
                    normalized,
                    listOf(
                        "با آقای",
                        "با خانم",
                        "با دکتر",
                        "با",
                        "پیش دکتر",
                        "نزد دکتر"
                    )
                )

        // -------------------------------------------------------------
        // 2. LOCATION
        // -------------------------------------------------------------

        val location = extractLocation(normalized)

        // -------------------------------------------------------------
        // 3. DATE
        // -------------------------------------------------------------

        /*
         * New DateExtractor has priority.
         */
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
         * Legacy date extraction remains as fallback.
         *
         * This is important for backward compatibility with phrases
         * already supported by the previous application version.
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
         * If month/day are known but year is missing,
         * assume the current Jalali year.
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
         * Generate a display date if the extractor resolved the
         * actual Jalali date but did not provide display text.
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

        /*
         * Prefer the weekday calculated from the resolved date.
         *
         * This prevents an incorrectly recognized weekday from
         * overriding an otherwise correctly resolved date.
         */
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

        /*
         * New TimeExtractor has priority.
         */
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
         * Legacy time extraction remains as fallback.
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

        /*
         * Supports:
         *
         *   2 روز بعد
         *   2 روز دیگر
         *   2 روز دیگه
         */
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

        /*
         * Word-number form:
         *
         *   دو روز بعد
         *   دو روز دیگر
         *   دو روز دیگه
         */
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

                /*
                 * A weekday mentioned without another date means
                 * the next occurrence of that weekday.
                 */
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

        // -------------------------------------------------------------
        // ربع کم / کم ربع
        // -------------------------------------------------------------

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

                /*
                 * Prevent false matches such as:
                 *
                 * "با" inside "باید"
                 */
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
                                "امشب",
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
                                "می‌رم",
                                "قراره",
                                "قرار",
                                "نوبت",
                                "جلسه",
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
                                    ':',
                                    '!',
                                    '?'
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

// ========================================================================
// LOCATION EXTRACTION
// ========================================================================

/**
 * Common Persian location phrases.
 *
 * Longer phrases MUST be checked before their shorter components.
 * For example:
 *
 *   "دفتر کار" before "دفتر"
 *   "کنار خیابان" before "خیابان"
 *   "داخل شعبه" before "شعبه"
 */
private val locationPhrases =
    listOf(
        // Multi-word locations
        "کنار خیابان",
        "داخل دفتر کار",
        "دفتر کار",
        "داخل دفتر",
        "داخل شعبه",

        // Medical
        "درمونگاه",
        "درمانگاه",
        "کلینیک",
        "مطب",
        "بیمارستان",

        // Public / commercial
        "بانک",
        "شعبه",
        "شرکت",
        "کارگاه",
        "ساختمان",
        "آپارتمان",

        // Education
        "کلاس",
        "دانشگاه",
        "مدرسه",

        // Outdoor
        "پارک",
        "خیابان",
        "باغ",
        "ویلا",
        "ساحل",
        "کوه",

        // Home
        "خونه",
        "خانه",

        // Office
        "دفتر"
    )

/**
 * Colloquial location variants.
 *
 * The returned value is normalized so that:
 *
 *   درمونگاه -> درمانگاه
 *   خونه     -> خانه
 */
private val locationNormalization =
    mapOf(
        "درمونگاه" to "درمانگاه",
        "خونه" to "خانه"
    )


private fun extractLocation(
    text: String
): String? {

    // ------------------------------------------------------------
    // 1. Explicit location phrases
    //
    // Examples:
    //
    //   در پارک
    //   در کلینیک
    //   داخل دفتر
    //   داخل شعبه
    //   در دفتر کار
    //   کنار خیابان
    // ------------------------------------------------------------

    val explicitPrefixes =
        listOf(
            "در محل",
            "در آدرس",
            "داخل",
            "در"
        )

    for (prefix in explicitPrefixes) {

        val prefixRegex =
            Regex(
                """(?:^|\s)${Regex.escape(prefix)}\s+(.+?)(?=\s+(?:با|ساعت|روز|فردا|پس‌فردا|امروز|قرار|نوبت|جلسه|دارم|خواهم|میرم|می‌روم|برم|بروم)\b|$)"""
            )

        val match =
            prefixRegex.find(text)

        if (match != null) {

            val candidate =
                cleanLocationCandidate(
                    match.groupValues[1]
                )

            if (candidate != null) {
                return candidate
            }
        }
    }

    // ------------------------------------------------------------
    // 2. Direct known location phrase
    //
    // Example:
    //
    //   فردا پارک قرار دارم
    //   فردا کلینیک میرم
    // ------------------------------------------------------------

    val directLocation =
        findKnownLocationPhrase(text)

    if (directLocation != null) {
        return directLocation
    }

    // ------------------------------------------------------------
    // 3. Location after a "go" verb
    //
    // Example:
    //
    //   فردا میرم خونه
    //   پس فردا برم درمانگاه
    //   ساعت پنج می‌روم دفتر کار
    // ------------------------------------------------------------

    return extractLocationAfterGoVerb(
        text
    )
}


/**
 * Find a known location phrase anywhere in the sentence.
 *
 * Longer phrases are checked first.
 */
private fun findKnownLocationPhrase(
    text: String
): String? {

    for (phrase in locationPhrases) {

        val regex =
            Regex(
                """(?:^|\s)${Regex.escape(phrase)}(?=\s|$|[،,.])"""
            )

        if (regex.containsMatchIn(text)) {

            return normalizeLocation(
                phrase
            )
        }
    }

    return null
}


/**
 * Extract location following a go-to verb.
 */
private fun extractLocationAfterGoVerb(
    text: String
): String? {

    val goVerbs =
        listOf(
            "می‌روم",
            "میروم",
            "میرم",
            "می‌رم",
            "برم",
            "بروم"
        )

    for (verb in goVerbs) {

        val regex =
            Regex(
                """(?:^|\s)${Regex.escape(verb)}\s+(.+?)(?=\s+(?:با|ساعت|روز|فردا|پس‌فردا|امروز|قرار|نوبت|جلسه|دارم|خواهم)\b|$)"""
            )

        val match =
            regex.find(text)

        if (match != null) {

            val candidate =
                cleanLocationCandidate(
                    match.groupValues[1]
                )

            if (candidate != null) {
                return candidate
            }
        }
    }

    return null
}


/**
 * Clean a possible location extracted from a sentence.
 */
private fun cleanLocationCandidate(
    input: String
): String? {

    var value =
        input.trim()

    if (value.isEmpty()) {
        return null
    }

    // Remove punctuation around the candidate.
    value =
        value.trim(
            ',',
            '.',
            '،',
            '؛',
            ':',
            ';',
            '!',
            '?',
            '؟'
        )

    if (value.isEmpty()) {
        return null
    }

    /*
     * Try to find a known location inside the candidate.
     *
     * This is important for:
     *
     *   "کلینیک دکتر احمدی"
     *
     * where the location should be "کلینیک",
     * not "کلینیک دکتر احمدی".
     */
    val known =
        findKnownLocationPhrase(
            value
        )

    if (known != null) {
        return known
    }

    /*
     * If there is no known keyword, keep the first meaningful
     * one or two words, compatible with the previous extractor.
     */
    val stopWords =
        setOf(
            "با",
            "ساعت",
            "روز",
            "فردا",
            "امروز",
            "پس‌فردا",
            "قرار",
            "نوبت",
            "جلسه",
            "دارم",
            "خواهم",
            "باید"
        )

    val words =
        value.split(
            Regex("\\s+")
        )

    val collected =
        mutableListOf<String>()

    for (word in words) {

        val clean =
            word.trim(
                ',',
                '.',
                '،',
                '؛',
                ':',
                ';'
            )

        if (clean.isEmpty()) {
            continue
        }

        if (stopWords.contains(clean)) {
            break
        }

        collected.add(clean)

        /*
         * Keep the old extractor conservative.
         * We do not want a complete sentence to become a location.
         */
        if (collected.size >= 2) {
            break
        }
    }

    if (collected.isEmpty()) {
        return null
    }

    return normalizeLocation(
        collected.joinToString(" ")
    )
}


/**
 * Normalize common colloquial location names.
 */
private fun normalizeLocation(
    location: String
): String {

    var result =
        location.trim()

    for ((from, to) in locationNormalization) {

        result =
            result.replace(
                Regex(
                    """\b${Regex.escape(from)}\b"""
                ),
                to
            )
    }

    return result
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
