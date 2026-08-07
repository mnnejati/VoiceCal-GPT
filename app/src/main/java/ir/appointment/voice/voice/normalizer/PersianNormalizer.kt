package ir.appointment.voice.voice.normalizer

object PersianNormalizer {

    fun normalize(text: String): String {

        var result = text

        // ---------------------------------------------------------
        // Persian / Arabic character normalization
        // ---------------------------------------------------------

        result = result
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ئ', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ۀ', 'ه')

        // ---------------------------------------------------------
        // Arabic/Persian digits → English digits
        // ---------------------------------------------------------

        result = normalizeDigits(result)

        // ---------------------------------------------------------
        // Common speech-recognition variations
        // ---------------------------------------------------------

        result = result
            .replace("ديگه", "دیگه")
            .replace("دیگر", "دیگر")

            .replace("پس فردا", "پس‌فردا")
            .replace("پس‌فردا", "پس‌فردا")

            .replace("سه شنبه", "سه‌شنبه")
            .replace("سه‌شنبه", "سه‌شنبه")

            .replace("پنج شنبه", "پنجشنبه")
            .replace("پنج‌شنبه", "پنجشنبه")

            .replace("بعد از ظهر", "بعدازظهر")
            .replace("بعداز ظهر", "بعدازظهر")

        // ---------------------------------------------------------
        // Remove invisible formatting characters
        // ---------------------------------------------------------

        result = result
            .replace("\u200B", "")
            .replace("\u200C", " ")
            .replace("\u200D", "")
            .replace("\uFEFF", "")

        // ---------------------------------------------------------
        // Normalize punctuation
        // ---------------------------------------------------------

        result = result
            .replace('،', ' ')
            .replace('؛', ' ')
            .replace(',', ' ')
            .replace(';', ' ')

        // ---------------------------------------------------------
        // Normalize whitespace
        // ---------------------------------------------------------

        result = result
            .replace(Regex("\\s+"), " ")
            .trim()

        return result
    }

    private fun normalizeDigits(
        text: String
    ): String {

        val persianDigits =
            "۰۱۲۳۴۵۶۷۸۹"

        val arabicDigits =
            "٠١٢٣٤٥٦٧٨٩"

        val result =
            StringBuilder(text.length)

        for (char in text) {

            val persianIndex =
                persianDigits.indexOf(char)

            val arabicIndex =
                arabicDigits.indexOf(char)

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
                    result.append(char)
            }
        }

        return result.toString()
    }
}
