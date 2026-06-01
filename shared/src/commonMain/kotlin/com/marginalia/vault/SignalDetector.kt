package com.marginalia.vault

object SignalDetector {

    // Extract capitalised terms that are not sentence-start words.
    // Targets proper nouns and concepts: "Eudaimonia", "Virtue Ethics", "Stoicism".
    // Skips: all-caps acronyms, short words (<3 chars), first word of each sentence.
    fun extractCandidates(text: String): List<String> {
        val sentences = text.split(Regex("""(?<=[.!?])\s+"""))
        val candidates = linkedSetOf<String>()
        for (sentence in sentences) {
            val words = sentence.trim().split(Regex("""\s+"""))
            for (i in 1 until words.size) {
                val word = words[i]
                    .trimEnd { !it.isLetterOrDigit() }
                    .trimStart { !it.isLetterOrDigit() }
                if (word.length >= 3
                    && word[0].isUpperCase()
                    && word.drop(1).any { it.isLowerCase() }
                ) {
                    candidates.add(word)
                }
            }
        }
        return candidates.toList()
    }
}
