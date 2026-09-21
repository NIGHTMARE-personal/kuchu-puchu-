package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import com.example.model.AttachmentItem
import com.example.model.AttachmentKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale

data class ProcessedImage(
    val base64Data: String,
    val mimeType: String = "image/jpeg"
)

data class ProcessedMultimodalPayload(
    val images: List<ProcessedImage> = emptyList(),
    val textSnippets: List<String> = emptyList()
)

object AttachmentProcessor {

    private const val MAX_IMAGE_DIMENSION = 1024
    private const val JPEG_QUALITY = 80
    private const val MAX_TEXT_BYTES = 50 * 1024 // 50 KB
    private const val MAX_PDF_PAGES = 5

    suspend fun processAttachments(
        context: Context,
        attachments: List<AttachmentItem>
    ): ProcessedMultimodalPayload = withContext(Dispatchers.IO) {
        val images = mutableListOf<ProcessedImage>()
        val textSnippets = mutableListOf<String>()

        for (attachment in attachments) {
            if (attachment.isInlineError || attachment.errorMessage != null) continue

            try {
                when (attachment.kind) {
                    AttachmentKind.IMAGE -> {
                        val processed = processSingleImage(context, attachment.uri)
                        if (processed != null) {
                            images.add(processed)
                        }
                    }
                    AttachmentKind.PDF -> {
                        val pdfImages = processPdfToImages(context, attachment.uri)
                        images.addAll(pdfImages)
                    }
                    AttachmentKind.TEXT -> {
                        val text = processTextFile(context, attachment.uri, attachment.name)
                        if (text.isNotBlank()) {
                            textSnippets.add(text)
                        }
                    }
                    AttachmentKind.VIDEO -> {
                        val frame = processVideoThumbnail(context, attachment.uri)
                        if (frame != null) images.add(frame)
                        textSnippets.add("[Attached Video: ${attachment.name}]")
                    }
                    AttachmentKind.AUDIO -> {
                        textSnippets.add("[Attached Audio: ${attachment.name}]")
                    }
                    AttachmentKind.FILE -> {
                        if (attachment.mime.startsWith("image/", ignoreCase = true)) {
                            val processed = processSingleImage(context, attachment.uri)
                            if (processed != null) images.add(processed)
                        } else if (attachment.mime == "application/pdf") {
                            images.addAll(processPdfToImages(context, attachment.uri))
                        } else {
                            val text = processTextFile(context, attachment.uri, attachment.name)
                            if (text.isNotBlank()) textSnippets.add(text)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AttachmentProcessor", "Failed to process attachment: ${attachment.name}", e)
                throw RuntimeException("Failed to prepare attachment ${attachment.name}: ${e.message}")
            }
        }

        ProcessedMultimodalPayload(images = images, textSnippets = textSnippets)
    }

    fun processSingleImage(context: Context, uri: Uri): ProcessedImage? {
        val resolver = context.contentResolver

        // Read orientation from EXIF if available
        var orientation = ExifInterface.ORIENTATION_NORMAL
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        } catch (e: Exception) {
            Log.w("AttachmentProcessor", "Could not read EXIF orientation: ${e.message}")
        }

        // Decode bounds
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        val origW = options.outWidth
        val origH = options.outHeight
        if (origW <= 0 || origH <= 0) return null

        // Calculate sample size for memory efficiency
        var sampleSize = 1
        var halfW = origW
        var halfH = origH
        while (halfW / 2 >= MAX_IMAGE_DIMENSION || halfH / 2 >= MAX_IMAGE_DIMENSION) {
            sampleSize *= 2
            halfW /= 2
            halfH /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val sampledBitmap = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        // Apply rotation if needed
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        }

        val orientedBitmap = if (!matrix.isIdentity) {
            val transformed = Bitmap.createBitmap(sampledBitmap, 0, 0, sampledBitmap.width, sampledBitmap.height, matrix, true)
            if (transformed != sampledBitmap) sampledBitmap.recycle()
            transformed
        } else sampledBitmap

        // Downscale to max 1024px long edge
        val currentMax = maxOf(orientedBitmap.width, orientedBitmap.height)
        val finalBitmap = if (currentMax > MAX_IMAGE_DIMENSION) {
            val scale = MAX_IMAGE_DIMENSION.toFloat() / currentMax
            val targetW = (orientedBitmap.width * scale).toInt().coerceAtLeast(1)
            val targetH = (orientedBitmap.height * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(orientedBitmap, targetW, targetH, true)
            if (scaled != orientedBitmap) orientedBitmap.recycle()
            scaled
        } else orientedBitmap

        // Compress to JPEG q80
        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        finalBitmap.recycle()

        val bytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return ProcessedImage(base64Data = base64, mimeType = "image/jpeg")
    }

    fun processVideoThumbnail(context: Context, uri: Uri): ProcessedImage? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(1000000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            retriever.release()
            if (bitmap != null) {
                val currentMax = maxOf(bitmap.width, bitmap.height)
                val scaled = if (currentMax > MAX_IMAGE_DIMENSION) {
                    val scale = MAX_IMAGE_DIMENSION.toFloat() / currentMax
                    Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                } else bitmap

                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                if (scaled != bitmap) bitmap.recycle()
                scaled.recycle()
                val bytes = stream.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ProcessedImage(base64Data = base64, mimeType = "image/jpeg")
            } else null
        } catch (e: Exception) {
            Log.w("AttachmentProcessor", "Could not extract video thumbnail: ${e.message}")
            null
        }
    }

    fun processPdfToImages(context: Context, uri: Uri): List<ProcessedImage> {
        val results = mutableListOf<ProcessedImage>()
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return emptyList()

        pfd.use { descriptor ->
            val renderer = PdfRenderer(descriptor)
            renderer.use { pdfRenderer ->
                val pageCount = pdfRenderer.pageCount
                val pagesToRender = minOf(MAX_PDF_PAGES, pageCount)

                for (pageIndex in 0 until pagesToRender) {
                    val page = pdfRenderer.openPage(pageIndex)
                    try {
                        val pageWidth = page.width
                        val pageHeight = page.height
                        val maxDim = maxOf(pageWidth, pageHeight)
                        val scale = if (maxDim > MAX_IMAGE_DIMENSION) {
                            MAX_IMAGE_DIMENSION.toFloat() / maxDim
                        } else 1.0f

                        val targetW = (pageWidth * scale).toInt().coerceAtLeast(1)
                        val targetH = (pageHeight * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        canvas.drawColor(Color.WHITE)

                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                        bitmap.recycle()

                        val bytes = outputStream.toByteArray()
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        results.add(ProcessedImage(base64Data = base64, mimeType = "image/jpeg"))
                    } finally {
                        page.close()
                    }
                }
            }
        }
        return results
    }

    fun processTextFile(context: Context, uri: Uri, fileName: String): String {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(MAX_TEXT_BYTES)
            var totalRead = 0
            while (totalRead < MAX_TEXT_BYTES) {
                val count = stream.read(buffer, totalRead, MAX_TEXT_BYTES - totalRead)
                if (count == -1) break
                totalRead += count
            }

            val text = String(buffer, 0, totalRead, Charsets.UTF_8)
            val hasMore = stream.read() != -1
            val result = if (hasMore) {
                "$text\n...[truncated]"
            } else {
                text
            }
            return "File: $fileName\n$result"
        }
        return ""
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    renderer.pageCount
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
