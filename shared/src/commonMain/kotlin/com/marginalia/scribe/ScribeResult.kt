package com.marginalia.scribe

import com.marginalia.ai.AIError

sealed class ScribeResult {
    data class Success(
        val transcribedText: String,
        val detectedConcepts: List<String>,
        val suggestedWikilinks: List<WikilinkSuggestion>
    ) : ScribeResult()

    data class ScribeAiError(val error: AIError) : ScribeResult()

    object NoInkContent : ScribeResult()
}

data class WikilinkSuggestion(
    val term: String,
    val matchedConcept: String?,
    val confidence: Float
)
