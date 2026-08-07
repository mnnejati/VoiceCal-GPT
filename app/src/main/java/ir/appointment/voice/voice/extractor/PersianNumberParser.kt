package ir.appointment.voice.voice.extractor

/**
 * Lightweight Persian number parser.
 *
 * Designed for speech-recognition output such as:
 *
 *   یک
 *   دو
 *   پنج
 *   دوازده
 *   بیست
 *   بیست و پنج
 *   سی و دو
 *   صد
 *   صد و بیست
 *   هزار
 *
 * It does not use any external library or ML model.
 */
object PersianNumberParser {

    private val units = mapOf(
        "صفر" to 0,
        "یک" to 1,
        "یه" to 1,
        "اول" to 1,

        "دو" to 2,
        "سه" to 3,
        "چهار" to 4,
        "پنج" to 5,
        "شش" to 6,
        "شیش" to 6,
        "هفت" to 7,
        "هشت" to 8,
        "نه" to 9
    )

    private val teens = mapOf(
        "ده" to 10,
        "یازده" to 11,
        "دوازده" to 12,
        "سیزده" to 13,
        "چهارده" to 14,
        "پانزده" to 15,
        "پونزده" to 15,
        "شانزده" to 16,
        "شونزده" to 16,
        "هفده" to 17,
        "هیفده" to 17,
        "هجده" to 18,
        "هیجده" to 18,
        "نوزده" to 19
    )

    private val tens = mapOf(
        "بیست" to 20,
        "سی" to 30,
        "چهل" to 40,
        "پنجاه" to 50,
        "شصت" to 60,
        "هفتاد" to 70,
        "هشتاد" to 80,
        "نود" to 90
    )

    private val hundreds = mapOf(
        "صد" to 100,
        "دویست" to 200,
        "سیصد" to 300,
        "چهارصد" to 400,
        "پانصد" to 500,
        "پونصد" to 500,
        "ششصد" to 600,
        "هفتصد" to 700,
        "هشتصد" to 800,
        "نهصد" to 900
    )

    private val scales = mapOf(
        "هزار" to 1_000,
        "میلیون" to 1_000_000,
        "میلیارد" to 1_000_000_000
    )

    /**
     * Parse a complete Persian number.
     *
     * Examples:
     *
     *   "پنج" -> 5
     *   "دوازده" -> 12
     *   "بیست و پنج" -> 25
     *   "صد و بیست" -> 120
     *   "دویست و سی و پنج" -> 235
     *   "هزار و دویست" -> 1200
     */
    fun parse(text: String): Int? {

        var normalized =
            normalize(text)

        if (normalized.isEmpty()) {
            return null
        }

        /*
         * Direct numeric representation.
         */
        normalized.toIntOrNull()?.let {
            return it
        }

        /*
         * Remove words which sometimes appear naturally in
         * speech around numbers.
         */
        normalized =
            normalized
                .replace(
                    Regex("\\bعدد\\b"),
                    ""
                )
                .replace(
                    Regex("\\bتا\\b"),
                    ""
                )
                .replace(
                    Regex("\\s+"),
                    " "
                )
                .trim()

        if (normalized.isEmpty()) {
            return null
        }

        /*
         * Very common special forms.
         */
        units[normalized]?.let {
            return it
        }

        teens[normalized]?.let {
            return it
        }

        tens[normalized]?.let {
            return it
        }

        hundreds[normalized]?.let {
            return it
        }

        /*
         * Composite numbers.
         */
        return parseComposite(normalized)
    }

    /**
     * Parses a number only when it is within a specified range.
     *
     * Useful for:
     *
     *   hours: 0..23
     *   minutes: 0..59
     *   days: 1..31
     */
    fun parseInRange(
        text: String,
        min: Int,
        max: Int
    ): Int? {

        val value =
            parse(text)
                ?: return null

        return if (
            value in min..max
        ) {
            value
        } else {
            null
        }
    }

    /**
     * Returns true if the supplied text contains a recognizable
     * Persian number.
     */
    fun isNumber(
        text: String
    ): Boolean {
        return parse(text) != null
    }

    /**
     * Normalizes a number phrase before parsing.
     */
    private fun normalize(
        input: String
    ): String {

        var text =
            input.trim()

        /*
         * Persian/Arabic character normalization.
         */
        text = text
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')

        /*
         * Persian and Arabic digits -> English digits.
         */
        text =
            normalizeDigits(text)

        /*
         * Normalize ZWNJ.
         */
        text =
            text.replace(
                '\u200C',
                ' '
            )

        /*
         * Common speech-recognition variants.
         */
        text = text
            .replace(
                "دو تا",
                "دو"
            )
            .replace(
                "سه تا",
                "سه"
            )
            .replace(
                "یه",
                "یک"
            )

        /*
         * Normalize multiple spaces.
         */
        text =
            text.replace(
                Regex("\\s+"),
                " "
            )

        return text.trim()
    }

    /**
     * Converts Persian/Arabic digits to English digits.
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

            val pIndex =
                persianDigits.indexOf(ch)

            val aIndex =
                arabicDigits.indexOf(ch)

            when {

                pIndex >= 0 ->
                    result.append(
                        pIndex
                    )

                aIndex >= 0 ->
                    result.append(
                        aIndex
                    )

                else ->
                    result.append(ch)
            }
        }

        return result.toString()
    }

    /**
     * Parses composite Persian numbers.
     *
     * Examples:
     *
     *   بیست و پنج
     *   سی و دو
     *   صد و بیست
     *   صد و بیست و پنج
     *   دویست و سی و پنج
     *   هزار و دویست
     *   هزار و دویست و سی
     */
    private fun parseComposite(
        text: String
    ): Int? {

        val words =
            text.split(
                Regex("\\s+")
            )
                .filter {
                    it.isNotBlank()
                }

        if (words.isEmpty()) {
            return null
        }

        var total = 0
        var current = 0

        var foundNumber = false

        var index = 0

        while (index < words.size) {

            val word =
                words[index]

            /*
             * "و" is only a connector.
             */
            if (word == "و") {
                index++
                continue
            }

            /*
             * Units.
             */
            units[word]?.let {

                current += it
                foundNumber = true

                index++
                continue
            }

            /*
             * Teens.
             */
            teens[word]?.let {

                current += it
                foundNumber = true

                index++
                continue
            }

            /*
             * Tens.
             */
            tens[word]?.let {

                current += it
                foundNumber = true

                index++
                continue
            }

            /*
             * Hundreds.
             */
            hundreds[word]?.let {

                current += it
                foundNumber = true

                index++
                continue
            }

            /*
             * Large scales.
             *
             * Example:
             *
             * دو هزار
             *
             * current = 2
             * scale = 1000
             * result = 2000
             */
            scales[word]?.let { scale ->

                if (current == 0) {
                    current = 1
                }

                total +=
                    current * scale

                current = 0

                foundNumber = true

                index++
                continue
            }

            /*
             * Unknown word -> parsing failed.
             */
            return null
        }

        if (!foundNumber) {
            return null
        }

        return total + current
    }

    // ---------------------------------------------------------------------
    // Utility methods for extractors
    // ---------------------------------------------------------------------

    /**
     * Returns all supported number words ordered from longest
     * to shortest.
     *
     * Useful when constructing regular expressions.
     */
    fun numberWords(): List<String> {

        return (
            units.keys +
            teens.keys +
            tens.keys +
            hundreds.keys
        )
            .distinct()
            .sortedByDescending {
                it.length
            }
    }

    /**
     * Returns a regex-safe pattern containing all number words.
     *
     * Example:
     *
     *   (دوازده|یازده|بیست|...)
     */
    fun numberPattern(): String {

        return numberWords()
            .joinToString("|") {
                Regex.escape(it)
            }
    }

    /**
     * Returns the recognized number words which can represent
     * an hour.
     */
    fun hourWords(): List<String> {

        return numberWords()
            .filter {
                val value =
                    parse(it)

                value != null &&
                        value in 0..23
            }
    }

    /**
     * Returns the recognized number words which can represent
     * minutes.
     */
    fun minuteWords(): List<String> {

        return numberWords()
            .filter {
                val value =
                    parse(it)

                value != null &&
                        value in 0..59
            }
    }

    /**
     * Regex pattern specifically for hour words.
     */
    fun hourPattern(): String {

        return hourWords()
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
    }

    /**
     * Regex pattern specifically for minute words.
     */
    fun minutePattern(): String {

        return minuteWords()
            .sortedByDescending {
                it.length
            }
            .joinToString("|") {
                Regex.escape(it)
            }
    }
}
