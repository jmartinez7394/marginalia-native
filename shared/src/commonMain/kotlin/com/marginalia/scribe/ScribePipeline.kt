package com.marginalia.scribe

import com.marginalia.model.ConceptNote

interface ScribePipeline {
    suspend fun transcribe(input: ScribeInput): ScribeResult
    suspend fun buildPrompt(context: ScribeContext): String
    fun resolveWikilinks(
        text: String,
        registry: List<ConceptNote>
    ): List<WikilinkSuggestion>
}
