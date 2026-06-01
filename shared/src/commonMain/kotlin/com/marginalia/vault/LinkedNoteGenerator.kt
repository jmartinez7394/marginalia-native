package com.marginalia.vault

import com.marginalia.animachora.Territory
import com.marginalia.model.Book
import com.marginalia.model.Highlight

object LinkedNoteGenerator {

    fun generate(
        book: Book,
        highlights: List<Highlight>,
        territory: Territory,
        createdAtIso: String
    ): String {
        val sb = StringBuilder()

        // Frontmatter (vault contract section 1)
        sb.append("---\n")
        sb.append("title: \"${escapeYaml(book.title)}\"\n")
        sb.append("author: \"${escapeYaml(book.author)}\"\n")
        sb.append("type: \"book-note\"\n")
        sb.append("status: \"reading\"\n")
        sb.append("format: \"${book.format.name.lowercase()}\"\n")
        sb.append("source: \"${book.format.name.lowercase()}\"\n")
        sb.append("bookId: \"${book.id}\"\n")
        sb.append("importedAt: \"$createdAtIso\"\n")
        sb.append("startedAt: \"$createdAtIso\"\n")
        sb.append("finishedAt: null\n")
        sb.append("coverPath: \".marginalia/covers/${book.id}.jpg\"\n")
        sb.append("linkedNoteCreatedAt: \"${createdAtIso}T00:00:00Z\"\n")
        sb.append("isbn: null\n")
        sb.append("tags: []\n")
        sb.append("collections: []\n")
        sb.append("threads: []\n")
        sb.append("---\n\n")

        // Body
        sb.append("# ${book.title}\n")
        sb.append("**${book.author}**\n\n")

        sb.append("## Summary\n\n\n\n")

        sb.append("## Highlights and Annotations\n\n")

        if (highlights.isEmpty()) {
            sb.append("*No highlights yet.*\n\n")
        } else {
            for (highlight in highlights) {
                sb.append("> ${highlight.text}\n")
                sb.append("^ann-${highlight.id}\n")
                if (!highlight.annotation.isNullOrEmpty()) {
                    sb.append("\n${highlight.annotation}\n")
                }
                sb.append("\n")
            }
        }

        sb.append("---\n\n")
        sb.append("## Margin Notes\n\n\n\n")
        sb.append("## Reading Notes\n\n")

        return sb.toString()
    }

    fun sanitiseFilename(input: String): String =
        input.replace(Regex("""[<>:"/\\|?*\t\n\r]"""), "")
            .trim()
            .take(200)

    private fun escapeYaml(value: String): String =
        value.replace("\"", "\\\"")
}
