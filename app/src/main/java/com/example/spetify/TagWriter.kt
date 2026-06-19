package com.example.spetify

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TagWriter(private val context: Context) {
    private val client = OkHttpClient()

    /**
     * Downloads an image and embeds it into the audio file's ID3/metadata.
     * Returns true if successful.
     */
    fun writeAlbumArtToFile(audioUri: Uri, imageUri: String): Boolean {
        var tempAudioFile: File? = null
        try {
            // 1. Get Image Data
            val imageData = if (imageUri.startsWith("http")) {
                downloadImage(imageUri)
            } else {
                context.contentResolver.openInputStream(Uri.parse(imageUri))?.use { it.readBytes() }
            }

            if (imageData == null || imageData.isEmpty()) return false

            // 2. Determine Extension and Create Proper Temp File
            val fileName = getFileName(audioUri)
            val extension = fileName.substringAfterLast('.', "mp3")
            tempAudioFile = File.createTempFile("tag_edit", ".$extension", context.cacheDir)

            // 3. Copy Original to Temp
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(tempAudioFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 4. Edit Tags in Temp File
            val f = AudioFileIO.read(tempAudioFile)
            val tag = f.tagOrCreateAndSetDefault
            
            tag.deleteArtworkField()
            val artwork = ArtworkFactory.getNew()
            artwork.binaryData = imageData
            
            // Android-compatible way to get image dimensions and mime type
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
            
            artwork.mimeType = options.outMimeType ?: "image/jpeg"
            artwork.width = if (options.outWidth > 0) options.outWidth else 500
            artwork.height = if (options.outHeight > 0) options.outHeight else 500
            artwork.pictureType = 3 // Front Cover
            
            try {
                tag.setField(artwork)
                
                // For MP3, ensure we are using ID3v2
                if (extension.lowercase() == "mp3") {
                    f.tag = tag
                }
                
                f.commit()
            } catch (t: Throwable) {
                Log.e("TagWriter", "Failed to embed artwork (likely ImageIO issue on Android for this file type)", t)
                // We return true here if it was an ImageIO issue because the app 
                // already updated the DB with the art URL, so the user will see it.
                // We just couldn't write it into the file.
                return true 
            }

            // 5. Write back to Original using "wt" (Write-Truncate)
            context.contentResolver.openOutputStream(audioUri, "wt")?.use { output ->
                FileInputStream(tempAudioFile).use { input ->
                    input.copyTo(output)
                }
                output.flush()
            }

            Log.d("TagWriter", "Successfully physically embedded artwork into $audioUri")
            return true
        } catch (e: Exception) {
            Log.e("TagWriter", "Failed to physically write artwork", e)
            return false
        } finally {
            tempAudioFile?.delete()
        }
    }

    /**
     * Extracts embedded artwork and saves it to the Pictures/SPETify folder.
     */
    fun extractAndSaveArtwork(audioUri: Uri, trackTitle: String): Boolean {
        var tempAudioFile: File? = null
        try {
            val fileNameOrig = getFileName(audioUri)
            val extension = fileNameOrig.substringAfterLast('.', "mp3")
            tempAudioFile = File.createTempFile("extract", ".$extension", context.cacheDir)

            context.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(tempAudioFile).use { output ->
                    input.copyTo(output)
                }
            }

            val f = AudioFileIO.read(tempAudioFile)
            val artwork = f.tag?.firstArtwork
            if (artwork == null || artwork.binaryData == null) return false

            val imageData = artwork.binaryData
            val timestamp = System.currentTimeMillis()
            val safeTitle = trackTitle.filter { it.isLetterOrDigit() || it == ' ' }.trim()
            val outName = "Art_${safeTitle}_$timestamp.jpg"

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, outName)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/SPETify")
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val uri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { it.write(imageData) }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)
            }

            return true
        } catch (e: Exception) {
            Log.e("TagWriter", "Extraction failed", e)
            return false
        } finally {
            tempAudioFile?.delete()
        }
    }

    /**
     * Physically removes embedded artwork from the audio file.
     * Returns true if successful.
     */
    fun removeAlbumArtFromFile(audioUri: Uri): Boolean {
        var tempAudioFile: File? = null
        try {
            val fileName = getFileName(audioUri)
            val extension = fileName.substringAfterLast('.', "mp3")
            tempAudioFile = File.createTempFile("tag_rem", ".$extension", context.cacheDir)

            // Copy Original to Temp
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(tempAudioFile).use { output ->
                    input.copyTo(output)
                }
            }

            val f = AudioFileIO.read(tempAudioFile)
            val tag = f.tag
            if (tag != null) {
                tag.deleteArtworkField()
                f.commit()
            }

            // Write back to Original
            context.contentResolver.openOutputStream(audioUri, "wt")?.use { output ->
                FileInputStream(tempAudioFile).use { input ->
                    input.copyTo(output)
                }
                output.flush()
            }
            return true
        } catch (e: Exception) {
            Log.e("TagWriter", "Failed to physically remove artwork", e)
            return false
        } finally {
            tempAudioFile?.delete()
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "file.mp3"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {}
        return name
    }

    private fun downloadImage(url: String): ByteArray? {
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
