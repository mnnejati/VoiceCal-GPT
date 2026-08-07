package ir.appointment.voice.voice.extractor

/**
 * Extracts a person's name from Persian appointment speech.
 *
 * Examples:
 *
 *   با دکتر احمدی
 *   نوبت دکتر احمدی دارم
 *   فردا دکتر احمدی را می‌بینم
 *   فردا پیش دکتر احمدی می‌روم
 *   فردا وقت دکتر احمدی دارم
 *   فردا با آقای احمدی قرار دارم
 *   جلسه با مهندس رضایی دارم
 *   ملاقات با علی دارم
 *
 * The extractor is intentionally rule-based and lightweight.
 * No ML model or external dependency is required.
 */
object PersonExtractor {

    data class Result(
        val name: String,
        val confidence: Int,
        val source: String
    )

    private val prefixes = listOf(
        "دکتر",
        "دكتر",
        "پزشک",
        "پزشک متخصص",
        "دندانپزشک",
        "مهندس",
        "استاد",
        "آقای",
        "خانم",
        "جناب آقای",
        "سرکار خانم"
    )

    /*
     * Phrases which strongly indicate that the following words
     * represent a person.
     *
     * They are ordered from more specific to more general.
     */
    private val strongPatterns = listOf(
        "نوبت دکتر",
        "وقت دکتر",
        "ویزیت دکتر",
        "ملاقات دکتر",
        "پیش دکتر",
        "نزد دکتر",

        "نوبت پزشک",
        "وقت پزشک",
        "ویزیت پزشک",

        "نوبت دندانپزشک",
        "وقت دندانپزشک",
        "ویزیت دندانپزشک",

        "با دکتر",
        "با پزشک",
        "با دندانپزشک",

        "با آقای",
        "با خانم",
        "با مهندس",
        "با استاد",

        "پیش آقای",
        "پیش خانم",
        "پیش مهندس",

        "نزد آقای",
        "نزد خانم",
        "نزد مهندس"
    )

    /*
     * General contextual phrases.
     *
     * These receive a slightly lower confidence because they can
     * occasionally be followed by something other than a name.
     */
    private val generalPatterns = listOf(
        "با",
        "ملاقات با",
        "قرار با",
        "جلسه با",
        "دیدار با",
        "جلسه‌ی با"
    )

    /*
     * Words which indicate that the person's name has ended.
     */
    private val stopWords = setOf(
        "در",
        "روی",
        "داخل",
        "ساعت",
        "روز",
        "تاریخ",
        "فردا",
        "امروز",
        "امشب",
        "پس‌فردا",
        "پس",
        "دیگر",
        "بعد",
        "صبح",
        "ظهر",
        "عصر",
        "شب",
        "بامداد",

        "دارم",
        "داره",
        "دارند",
        "داشتن",
        "داشته",
        "باشد",

        "است",
        "هست",
        "هستم",

        "میرم",
        "می‌رم",
        "می‌روم",
        "برم",
        "بروم",
        "بریم",
        "برویم",

        "دارم",
        "داریم",

        "قراره",
        "قرار",
        "نوبت",
        "وقت",
        "ویزیت",
        "ملاقات",
        "جلسه",
        "دیدار",

        "را",
        "رو",

        "که",
        "برای",
        "به",
        "از",

        "درمانگاه",
        "بیمارستان",
        "کلینیک",
        "مطب",

        "شنبه",
        "یکشنبه",
        "دوشنبه",
        "سه‌شنبه",
        "چهارشنبه",
        "پنجشنبه",
        "جمعه"
    )

    /*
     * Common non-person words which may appear immediately after
     * "با" but should not be returned as a person's name.
     */
    private val invalidNames = setOf(
        "من",
        "تو",
        "او",
        "ایشان",
        "ما",
        "شما",
        "آنها",
        "اونا",

        "دکتر",
        "پزشک",
        "پزشکی",
        "بیمار",
        "مریض",

        "مطب",
        "کلینیک",
        "بیمارستان",
        "درمانگاه",
        "شرکت",
        "اداره",
        "دانشگاه",
        "بانک",

        "فردا",
        "امروز",
        "امشب",
        "پس‌فردا",

        "صبح",
        "ظهر",
        "عصر",
        "شب",

        "ساعت",
        "روز",
        "تاریخ",

        "قرار",
        "نوبت",
        "وقت",
        "جلسه",
        "ملاقات"
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
         * 1. Strong medical/person patterns.
         *
         * This is the most important part for:
         *
         * "فردا ساعت پنج نوبت دکتر احمدی دارم"
         */
        for (pattern in strongPatterns) {

            val result =
                extractAfterPhrase(
                    normalized,
                    pattern,
                    confidence = 98
                )

            if (result != null) {
                return result
            }
        }

        /*
         * 2. General "با ..." patterns.
         */
        for (pattern in generalPatterns) {

            val result =
                extractAfterPhrase(
                    normalized,
                    pattern,
                    confidence = 90
                )

            if (result != null) {
                return result
            }
        }

        /*
         * 3. Handle a standalone professional title:
         *
         * "فردا دکتر احمدی را می‌بینم"
         *
         * The title itself is part of the useful person value.
         */
        val prefixResult =
            extractAfterProfessionalPrefix(
                normalized
            )

        if (prefixResult != null) {
            return prefixResult
        }

        /*
         * 4. Final fallback:
         *
         * "فردا علی را می‌بینم"
         *
         * We deliberately require a person-related verb/context
         * before accepting a bare name.
         */
        return extractBarePerson(
            normalized
        )
    }

    // ---------------------------------------------------------------------
    // Phrase extraction
    // ---------------------------------------------------------------------

    private fun extractAfterPhrase(
        text: String,
        phrase: String,
        confidence: Int
    ): Result? {

        var start = 0

        while (true) {

            val index =
                text.indexOf(
                    phrase,
                    startIndex = start
                )

            if (index < 0) {
                return null
            }

            if (!isWordBoundaryBefore(text, index)) {
                start = index + phrase.length
                continue
            }

            val afterIndex =
                index + phrase.length

            if (!isWordBoundaryAfter(text, afterIndex)) {
                start = afterIndex
                continue
            }

            val after =
                text.substring(afterIndex).trim()

            val candidate =
                collectPersonWords(
                    after
                )

            if (
                candidate != null &&
                isValidPersonCandidate(candidate)
            ) {

                return Result(
                    name = candidate,
                    confidence = confidence,
                    source = phrase
                )
            }

            start = afterIndex
        }
    }

    // ---------------------------------------------------------------------
    // Professional prefixes
    // ---------------------------------------------------------------------

    private fun extractAfterProfessionalPrefix(
        text: String
    ): Result? {

        /*
         * Longer prefixes must be checked first.
         *
         * For example:
         *
         * "دندانپزشک احمدی"
         *
         * must not be partially interpreted as "پزشک احمدی".
         */
        val sortedPrefixes =
            prefixes.sortedByDescending {
                it.length
            }

        for (prefix in sortedPrefixes) {

            var start = 0

            while (true) {

                val index =
                    text.indexOf(
                        prefix,
                        startIndex = start
                    )

                if (index < 0) {
                    break
                }

                if (!isWordBoundaryBefore(text, index)) {
                    start = index + prefix.length
                    continue
                }

                val afterIndex =
                    index + prefix.length

                if (!isWordBoundaryAfter(text, afterIndex)) {
                    start = afterIndex
                    continue
                }

                val after =
                    text.substring(afterIndex).trim()

                val candidate =
                    collectPersonWords(
                        after
                    )

                if (
                    candidate != null &&
                    isValidPersonCandidate(candidate)
                ) {

                    /*
                     * Include the professional title in the returned
                     * value because for appointments such as
                     * "نوبت دکتر احمدی" the title is useful information.
                     */
                    return Result(
                        name =
                            "$prefix $candidate",
                        confidence = 96,
                        source = prefix
                    )
                }

                start = afterIndex
            }
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Bare person fallback
    // ---------------------------------------------------------------------

    private fun extractBarePerson(
        text: String
    ): Result? {

        /*
         * Examples:
         *
         * "فردا علی را می‌بینم"
         * "امروز احمدی را ملاقات دارم"
         *
         * This fallback is intentionally conservative.
         */
        val contextPatterns = listOf(
            "می‌بینم",
            "میبینم",
            "ملاقات دارم",
            "دیدار دارم",
            "قرار دارم",
            "جلسه دارم",
            "قرار می‌گذارم",
            "قرار میذارم"
        )

        for (context in contextPatterns) {

            val contextIndex =
                text.indexOf(context)

            if (contextIndex < 0) {
                continue
            }

            val before =
                text.substring(
                    0,
                    contextIndex
                ).trim()

            val candidate =
                extractLastLikelyName(
                    before
                )

            if (
                candidate != null &&
                isValidPersonCandidate(candidate)
            ) {

                return Result(
                    name = candidate,
                    confidence = 72,
                    source = context
                )
            }
        }

        return null
    }

    // ---------------------------------------------------------------------
    // Candidate collection
    // ---------------------------------------------------------------------

    private fun collectPersonWords(
        text: String
    ): String? {

        if (text.isEmpty()) {
            return null
        }

        val words =
            text.split(
                Regex("\\s+")
            )

        val collected =
            mutableListOf<String>()

        for (rawWord in words) {

            val word =
                cleanWord(rawWord)

            if (word.isEmpty()) {
                continue
            }

            /*
             * Stop as soon as the appointment information begins.
             */
            if (
                isStopWord(word)
            ) {
                break
            }

            /*
             * Do not accidentally consume dates/times.
             */
            if (
                looksLikeTime(word) ||
                looksLikeDate(word)
            ) {
                break
            }

            /*
             * Punctuation at the end of a person's name
             * should not be included.
             */
            collected.add(word)

            /*
             * Persian names are usually one to three words.
             *
             * Examples:
             * دکتر علی احمدی
             * آقای محمد رضا احمدی
             *
             * We allow four words for compound names, but stop there.
             */
            if (collected.size >= 4) {
                break
            }
        }

        if (collected.isEmpty()) {
            return null
        }

        return collected.joinToString(" ")
    }

    // ---------------------------------------------------------------------
    // Last-name fallback
    // ---------------------------------------------------------------------

    private fun extractLastLikelyName(
        text: String
    ): String? {

        val words =
            text.split(
                Regex("\\s+")
            )
                .map {
                    cleanWord(it)
                }
                .filter {
                    it.isNotEmpty()
                }

        if (words.isEmpty()) {
            return null
        }

        /*
         * Remove obvious temporal/context words from the end.
         */
        val filtered =
            words.toMutableList()

        while (
            filtered.isNotEmpty() &&
            (
                isStopWord(
                    filtered.last()
                ) ||
                looksLikeTime(
                    filtered.last()
                )
            )
        ) {
            filtered.removeAt(
                filtered.lastIndex
            )
        }

        if (filtered.isEmpty()) {
            return null
        }

        /*
         * If the phrase contains "را", use the words immediately
         * before it.
         */
        val raIndex =
            filtered.indexOfLast {
                it == "را" || it == "رو"
            }

        if (raIndex > 0) {

            val candidate =
                filtered
                    .subList(
                        maxOf(
                            0,
                            raIndex - 3
                        ),
                        raIndex
                    )
                    .joinToString(" ")

            if (
                isValidPersonCandidate(
                    candidate
                )
            ) {
                return candidate
            }
        }

        /*
         * Otherwise take up to the last three words.
         */
        val start =
            maxOf(
                0,
                filtered.size - 3
            )

        val candidate =
            filtered
                .subList(
                    start,
                    filtered.size
                )
                .joinToString(" ")

        return if (
            isValidPersonCandidate(
                candidate
            )
        ) {
            candidate
        } else {
            null
        }
    }

    // ---------------------------------------------------------------------
    // Candidate validation
    // ---------------------------------------------------------------------

    private fun isValidPersonCandidate(
        candidate: String
    ): Boolean {

        val clean =
            candidate.trim()

        if (clean.isEmpty()) {
            return false
        }

        if (
            clean.length < 2
        ) {
            return false
        }

        if (
            invalidNames.contains(
                clean
            )
        ) {
            return false
        }

        val words =
            clean.split(
                Regex("\\s+")
            )

        if (words.isEmpty()) {
            return false
        }

        /*
         * A candidate consisting entirely of numbers cannot be
         * a person's name.
         */
        if (
            words.all {
                it.toIntOrNull() != null
            }
        ) {
            return false
        }

        /*
         * Reject obvious date/time phrases.
         */
        if (
            words.any {
                looksLikeTime(it) ||
                looksLikeDate(it)
            }
        ) {
            return false
        }

        /*
         * Reject phrases which are clearly not names.
         */
        val invalidCount =
            words.count {
                invalidNames.contains(it)
            }

        if (
            invalidCount > 0
        ) {
            return false
        }

        return true
    }

    // ---------------------------------------------------------------------
    // Token helpers
    // ---------------------------------------------------------------------

    private fun cleanWord(
        input: String
    ): String {

        return input
            .trim()
            .trim(
                ',',
                '.',
                '،',
                '؛',
                ';',
                ':',
                '!',
                '?',
                '؟',
                '(',
                ')',
                '[',
                ']'
            )
            .trim()
    }

    private fun isStopWord(
        word: String
    ): Boolean {

        return stopWords.contains(
            word
        )
    }

    private fun looksLikeTime(
        word: String
    ): Boolean {

        /*
         * 5
         * 17
         * 5:30
         */
        if (
            word.toIntOrNull() != null
        ) {
            val value =
                word.toIntOrNull()!!

            return value in 0..23
        }

        return word.contains(":")
    }

    private fun looksLikeDate(
        word: String
    ): Boolean {

        /*
         * 1405/5/12
         * 1405-5-12
         */
        return Regex(
            """1[34]\d{2}[/\-]\d{1,2}[/\-]\d{1,2}"""
        ).matches(word)
    }

    // ---------------------------------------------------------------------
    // Word boundaries
    // ---------------------------------------------------------------------

    private fun isWordBoundaryBefore(
        text: String,
        index: Int
    ): Boolean {

        if (index == 0) {
            return true
        }

        return text[index - 1].isWhitespace()
    }

    private fun isWordBoundaryAfter(
        text: String,
        index: Int
    ): Boolean {

        if (
            index >= text.length
        ) {
            return true
        }

        return text[index].isWhitespace()
    }

    // ---------------------------------------------------------------------
    // Normalization
    // ---------------------------------------------------------------------

    private fun normalize(
        input: String
    ): String {

        var text =
            input

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
         * Normalize ZWNJ.
         */
        text =
            text.replace(
                '\u200C',
                '‌'
            )

        /*
         * Common spoken variants.
         */
        text = text
            .replace(
                "دكتر",
                "دکتر"
            )
            .replace(
                "میبینم",
                "می‌بینم"
            )
            .replace(
                "میروم",
                "می‌روم"
            )
            .replace(
                "میرم",
                "میرم"
            )
            .replace(
                "می روم",
                "می‌روم"
            )
            .replace(
                "می بینم",
                "می‌بینم"
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
                "پس فردا",
                "پس‌فردا"
            )

        /*
         * Normalize punctuation to spaces.
         */
        text = text
            .replace(
                Regex("[،,؛;]+"),
                " "
            )

        /*
         * Collapse whitespace.
         */
        text =
            text.replace(
                Regex("\\s+"),
                " "
            )

        return text.trim()
    }
}
