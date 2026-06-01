package com.marginalia.android.platform.reader

import android.content.Context
import com.marginalia.model.BookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

sealed class OpenPublicationResult {
    data class Success(val publication: Publication) : OpenPublicationResult()
    data class FileNotFound(val path: String) : OpenPublicationResult()
    data class UnsupportedFormat(val path: String) : OpenPublicationResult()
    data class CorruptFile(val message: String) : OpenPublicationResult()
}

class ReadiumBookOpener(private val context: Context) {

    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
    private val publicationParser = DefaultPublicationParser(
        context = context,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null
    )
    private val publicationOpener = PublicationOpener(
        publicationParser = publicationParser,
        contentProtections = emptyList()
    )

    suspend fun open(filePath: String, format: BookFormat): OpenPublicationResult =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext OpenPublicationResult.FileNotFound(filePath)

            val url = AbsoluteUrl(file.toURI().toString())
                ?: return@withContext OpenPublicationResult.UnsupportedFormat(filePath)

            val asset = assetRetriever.retrieve(url)
                .getOrElse { error ->
                    return@withContext OpenPublicationResult.CorruptFile(
                        error.toString()
                    )
                }

            when (val result = publicationOpener.open(asset, allowUserInteraction = false)) {
                is Try.Success -> OpenPublicationResult.Success(result.value)
                is Try.Failure -> OpenPublicationResult.CorruptFile(result.value.toString())
            }
        }

    fun close(publication: Publication) {
        publication.close()
    }
}
