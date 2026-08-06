package ir.appointment.voice.voice.normalizer

object PersianNormalizer {

    fun normalize(text: String): String {

        return text

            .replace('ي', 'ی')
            .replace('ك', 'ک')

            .replace("سه شنبه", "سه‌شنبه")
            .replace("پنج شنبه", "پنج‌شنبه")
            .replace("پس فردا", "پس‌فردا")

            .replace(Regex("\\s+"), " ")

            .trim()
    }

}
