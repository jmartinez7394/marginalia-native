package com.marginalia.scribe

import com.marginalia.model.ConceptNote

object ScribeWikilinkResolver {

    fun resolveWikilinks(
        text: String,
        registry: List<ConceptNote>
    ): List<WikilinkSuggestion> {
        return registry.mapNotNull { concept ->
            when {
                text.contains(concept.name) ->
                    WikilinkSuggestion(concept.name, concept.name, 1.0f)
                text.contains(concept.name, ignoreCase = true) ->
                    WikilinkSuggestion(concept.name, concept.name, 0.9f)
                else -> {
                    val matchedAlias = concept.aliases.firstOrNull { text.contains(it) }
                    val matchedAliasCI = if (matchedAlias == null) {
                        concept.aliases.firstOrNull { text.contains(it, ignoreCase = true) }
                    } else null

                    when {
                        matchedAlias != null ->
                            WikilinkSuggestion(matchedAlias, concept.name, 0.9f)
                        matchedAliasCI != null ->
                            WikilinkSuggestion(matchedAliasCI, concept.name, 0.8f)
                        else -> null
                    }
                }
            }
        }
    }
}
