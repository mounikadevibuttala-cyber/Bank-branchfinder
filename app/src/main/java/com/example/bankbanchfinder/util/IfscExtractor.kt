package com.example.bankbanchfinder.util

object IfscExtractor {
    // IFSC Regex requirement: [A-Z]{4}0[A-Z0-9]{6}
    private val IFSC_REGEX = Regex("""[A-Z]{4}0[A-Z0-9]{6}""")

    fun extractIfsc(text: String): String? {
        val upperText = text.uppercase()
        
        // 1. Try finding a match directly in the text block
        val match = IFSC_REGEX.find(upperText)
        if (match != null) {
            return match.value
        }

        // 2. Try removing special symbols/spaces except alphanumeric and check again
        val cleaned = upperText.replace(Regex("""[^A-Z0-9]"""), "")
        val matchCleaned = IFSC_REGEX.find(cleaned)
        if (matchCleaned != null) {
            return matchCleaned.value
        }

        // 3. Fallback: Check line by line or word by word
        val words = upperText.split(Regex("""[\s\n\r,:]+"""))
        for (word in words) {
            val wordMatch = IFSC_REGEX.find(word)
            if (wordMatch != null) {
                return wordMatch.value
            }
        }

        return null
    }

    fun isValidIfsc(ifsc: String): Boolean {
        return IFSC_REGEX.matches(ifsc.uppercase().trim())
    }
}
