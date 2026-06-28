package com.yrolland.loom

object FuzzyMatcher {
    /**
     * Checks if [query] is a fuzzy match for [label].
     * A fuzzy match is defined as all characters of the query appearing in the label in the same order (case-insensitive).
     * Empty queries or queries with only whitespace always match (return true).
     */
    fun matches(query: String, label: String): Boolean {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return true
        if (label.isEmpty()) return false

        val cleanQuery = trimmedQuery.removeAccents()
        val cleanLabel = label.removeAccents()

        var queryIdx = 0
        for (char in cleanLabel) {
            if (char.equals(cleanQuery[queryIdx], ignoreCase = true)) {
                queryIdx++
                if (queryIdx == cleanQuery.length) {
                    return true
                }
            }
        }
        return false
    }

    private fun String.removeAccents(): String {
        val normalized = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }
}
