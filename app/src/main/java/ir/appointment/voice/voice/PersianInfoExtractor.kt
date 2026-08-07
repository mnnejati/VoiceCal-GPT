package ir.appointment.voice.voice

import ir.appointment.voice.voice.extractor.DateExtractor
import ir.appointment.voice.voice.extractor.PersonExtractor
import ir.appointment.voice.voice.extractor.TimeExtractor

/**
 * Result of extracting appointment info from a Persian utterance.
 */
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
 * Main rule-based Persian appointment information extractor.
 *
 * The extraction itself is divided into lightweight specialized
 * components:
 *
 *   DateExtractor
 *   TimeExtractor
 *   PersonExtractor
 *
 * Location extraction remains here because it is closely tied to
 * appointment-specific phrases such as:
 *
 *   در بیمارستان
 *   در مطب
 *   در کلینیک
 *   می‌روم بیمارستان
 *
 * No additional ML model is required.
 */
object PersianInfoExtractor {

    /**
     * Main entry point.
     */
    fun extract(text: String): ExtractedAppointment {

        val normalized =
            normalize(text.trim())

        /*
         * -------------------------------------------------------------
         * 1. PERSON
         * -------------------------------------------------------------
         *
         * Examples:
         *
         *   نوبت دکتر احمدی دارم
         *   فردا پیش دکتر احمدی می‌روم
         *   جلسه با مهندس رضایی دارم
         */
        val personResult =
            PersonExtractor.extract(normalized)

        val person =
            personResult?.name

        /*
         * -------------------------------------------------------------
         * 2. LOCATION
         * -------------------------------------------------------------
         */
        val location =
            extractLocation(normalized)

        /*
         * -------------------------------------------------------------
         * 3. DATE
         * -------------------------------------------------------------
         *
         * DateExtractor handles:
         *
         *   امروز
         *   فردا
         *   پس فردا
         *   دو روز دیگر
         *   دو روز بعد
         *   12 مرداد
         *   دوازدهم مرداد
         *   1405/5/12
         *   شنبه
         */
        val dateResult =
            DateExtractor.extract(normalized)

        val jy =
            dateResult?.year

        val jm =
            dateResult?.month

        val jd =
            dateResult?.day

        val weekday =
            dateResult?.weekdayName

        val displayDate =
            dateResult?.displayDate

        /*
         * -------------------------------------------------------------
         * 4. TIME
         * -------------------------------------------------------------
         *
         * TimeExtractor handles:
         *
         *   ساعت پنج
         *   ساعت 5
         *   ساعت پنج و نیم
         *   ساعت پنج و ربع
         *   ربع به پنج
         *   بیست دقیقه به پنج
         *   پنج عصر
         *   پنج شب
         *   17:30
         */
        val timeResult =
            TimeExtractor.extract(normalized)

        val hour =
            timeResult?.hour

        val minute =
            timeResult?.minute

        val displayTime =
            timeResult?.displayTime

        /*
         * -------------------------------------------------------------
         * 5. SORT TIMESTAMP
         * -------------------------------------------------------------
         *
         * Keep the same PersianCalendar conversion used by the
         * previous implementation.
         */
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
    // LOCATION
    // =====================================================================

    /**
     * Extracts appointment location.
     *
     * Examples:
     *
     *   در بیمارستان میلاد
     *   در کلینیک مهر
     *   در مطب دکتر احمدی
     *
     * Also supports:
     *
     *   باید بروم بیمارستان
     *   می‌روم درمانگاه
     */
    private fun extractLocation(
        text: String
    ): String? {

        /*
         * First check explicit location markers.
         */
        val explicit =
            extractAfterKeyword(
                text = text,
                keywords = listOf(
                    "در محل",
                    "در آدرس",
                    "در"
                ),
                maxWords = 4
            )

        if (
            explicit != null &&
            !looksLikePersonOrTime(
                explicit
            )
        ) {
            return explicit
        }

        /*
         * Then check common "go to" constructions.
         */
        val afterGo =
            extractAfterKeyword(
                text = text,
                keywords = listOf(
                    "می‌روم",
                    "میرم",
                    "می‌رم",
                    "بروم",
                    "برم"
                ),
                maxWords = 3
            )

        if (
            afterGo != null &&
            !looksLikeTimeOrDate(
                afterGo
            )
        ) {
            return afterGo
        }

        return null
    }

    /**
     * Extracts a limited number of words after a keyword.
     *
     * This is intentionally conservative so that location extraction
     * does not consume the rest of the appointment sentence.
     */
    private fun extractAfterKeyword(
        text: String,
        keywords: List<String>,
        maxWords: Int
    ): String? {

        for (keyword in keywords) {

            var searchFrom = 0

            while (true) {

                val index =
                    text.indexOf(
                        keyword,
                        searchFrom
                    )

                if (index < 0) {
                    break
                }

                /*
                 * Prevent matching a keyword inside another word.
                 */
                val beforeOk =
                    index == 0 ||
                            text[index - 1].isWhitespace()

                val afterIndex =
                    index + keyword.length

                val afterOk =
                    afterIndex >= text.length ||
                            text[afterIndex].isWhitespace()

                if (
                    !beforeOk ||
                    !afterOk
                ) {
                    searchFrom =
                        afterIndex

                    continue
                }

                val after =
                    text
                        .substring(
                            afterIndex
                        )
                        .trim()

                if (after.isEmpty()) {
                    searchFrom =
                        afterIndex

                    continue
                }

                val words =
                    after.split(
                        Regex("\\s+")
                    )

                val collected =
                    mutableListOf<String>()

                for (rawWord in words) {

                    val word =
                        rawWord.trim(
                            ',',
                            '.',
                            '،',
                            '؛',
                            ';',
                            ':',
                            '!',
                            '?',
                            '؟'
                        )

                    if (word.isEmpty()) {
                        continue
                    }

                    if (
                        isLocationStopWord(
                            word
                        )
                    ) {
                        break
                    }

                    collected.add(word)

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

                searchFrom =
                    afterIndex
            }
        }

        return null
    }

    /**
     * Words which indicate that location extraction should stop.
     */
    private fun isLocationStopWord(
        word: String
    ): Boolean {

        return when (word) {

            "ساعت",
            "روز",
            "تاریخ",
            "فردا",
            "امروز",
            "امشب",
            "پس‌فردا",

            "شنبه",
            "یکشنبه",
            "دوشنبه",
            "سه‌شنبه",
            "چهارشنبه",
            "پنجشنبه",
            "جمعه",

            "با",
            "نوبت",
            "وقت",
            "ویزیت",
            "قرار",
            "جلسه",
            "ملاقات",

            "دارم",
            "داریم",
            "دارد",
            "است",
            "هست",

            "می‌روم",
            "میرم",
            "می‌رم",
            "بروم",
            "برم",

            "صبح",
            "ظهر",
            "عصر",
            "شب" -> true

            else -> false
        }
    }

    /**
     * Prevent obvious person/time values from being returned as location.
     */
    private fun looksLikePersonOrTime(
        value: String
    ): Boolean {

        val lower =
            value.trim()

        if (
            lower.startsWith("دکتر ")
        ) {
            return true
        }

        if (
            lower.startsWith("پزشک ")
        ) {
            return true
        }

        if (
            lower.startsWith("مهندس ")
        ) {
            return true
        }

        return looksLikeTimeOrDate(
            lower
        )
    }

    /**
     * Checks whether a value is clearly a date/time expression.
     */
    private fun looksLikeTimeOrDate(
        value: String
    ): Boolean {

        if (
            Regex(
                """^\d{1,2}:\d{1,2}$"""
            ).matches(value)
        ) {
            return true
        }

        if (
            Regex(
                """^1[34]\d{2}[/\-]\d{1,2}[/\-]\d{1,2}$"""
            ).matches(value)
        ) {
            return true
        }

        return value == "ساعت" ||
                value == "امروز" ||
                value == "فردا" ||
                value == "امشب"
    }

    // =====================================================================
    // NORMALIZATION
    // =====================================================================

    /**
     * Normalizes text before passing it to the specialized extractors.
     *
     * This is deliberately lightweight.
     */
    private fun normalize(
        input: String
    ): String {

        var text =
            normalizeDigits(input)

        /*
         * Arabic -> Persian characters.
         */
        text = text
            .replace(
                'ي',
                'ی'
            )
            .replace(
                'ى',
                'ی'
            )
            .replace(
                'ك',
                'ک'
            )
            .replace(
                'ۀ',
                'ه'
            )
            .replace(
                'ة',
                'ه'
            )

        /*
         * Common spacing variants.
         */
        text = text
            .replace(
                "سه شنبه",
                "سه‌شنبه"
            )
            .replace(
                "پنج شنبه",
                "پنجشنبه"
            )
            .replace(
                "پس فردا",
                "پس‌فردا"
            )
            .replace(
                "نیمه‌شب",
                "نیمه شب"
            )

        /*
         * Normalize common punctuation.
         */
        text = text
            .replace(
                '،',
                ' '
            )
            .replace(
                '؛',
                ' '
            )
            .replace(
                ',',
                ' '
            )

        /*
         * Collapse multiple spaces.
         */
        text =
            text.replace(
                Regex("\\s+"),
                " "
            )

        return text.trim()
    }

    /**
     * Persian/Arabic digits -> English digits.
     */
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
