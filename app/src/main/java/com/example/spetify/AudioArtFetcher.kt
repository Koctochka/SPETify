package com.example.spetify

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Buffer
import okio.buffer
import okio.source
import java.io.ByteArrayInputStream

class AudioArtFetcher(
    private val context: Context,
    private val data: Uri
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, data)
            val picture = retriever.embeddedPicture
            
            if (picture != null && picture.isNotEmpty()) {
                SourceResult(
                    source = ImageSource(
                        source = Buffer().apply { write(picture) },
                        context = context
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) { /* ignore */ }
        }
    }

    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme
            if (scheme == "content" || scheme == "file") {
                val mimeType = context.contentResolver.getType(data)
                // If it's an audio file, we handle it
                if (mimeType?.startsWith("audio/") == true || 
                    data.path?.endsWith(".mp3") == true || 
                    data.path?.endsWith(".flac") == true || 
                    data.path?.endsWith(".wav") == true) {
                    return AudioArtFetcher(context, data)
                }
            }
            return null
        }
    }
}
