package com.tixly.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import com.tixly.app.data.Ticket
import java.io.File
import java.io.FileOutputStream
import java.util.*

/**
 * Processor for handling image files (JPG, PNG) as ticket attachments
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_IMAGE_WIDTH = 1920
        private const val MAX_IMAGE_HEIGHT = 1080
        private const val JPEG_QUALITY = 85
    }

    /**
     * Process an image file and create a ticket
     */
    fun processImage(uri: Uri): Ticket? {
        return try {
            Log.d(TAG, "Processing image: $uri")

            // Save image to internal storage
            val savedImagePath = saveImageToInternalStorage(uri)
                ?: throw Exception("Failed to save image")

            // Create ticket with image
            val ticket = Ticket(
                title = context.getString(com.tixly.app.R.string.new_event),
                description = context.getString(com.tixly.app.R.string.ticket_created_from_image),
                imageFilePath = savedImagePath,
                imageUri = uri.toString(),
                fileType = Ticket.FileType.IMAGE,
                createdDate = Date()
            )

            // Save ticket to repository
            com.tixly.app.data.TicketsRepository.addTicket(ticket)

            Log.d(TAG, "Image processed successfully: ${ticket.title}")
            ticket

        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            null
        }
    }

    /**
     * Save image to internal storage with optimization
     */
    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            Log.d(TAG, "Saving image to internal storage")

            // Create folder for image tickets
            val imageDir = File(context.filesDir, "image_tickets")
            if (!imageDir.exists()) {
                imageDir.mkdirs()
                Log.d(TAG, "Created image directory: ${imageDir.absolutePath}")
            }

            // Generate unique filename
            val fileName = "ticket_image_${UUID.randomUUID()}.jpg"
            val destinationFile = File(imageDir, fileName)

            // Load and optimize image
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    ?: throw Exception("Failed to decode image")

                // Resize if too large
                val resizedBitmap = resizeImageIfNeeded(originalBitmap)

                // Save as JPEG with compression
                FileOutputStream(destinationFile).use { outputStream ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
                }

                // Clean up bitmaps
                if (resizedBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }
                resizedBitmap.recycle()
            }

            val finalPath = destinationFile.absolutePath
            Log.d(TAG, "Image saved successfully: $finalPath")
            Log.d(TAG, "File size: ${destinationFile.length() / 1024}KB")

            finalPath

        } catch (e: Exception) {
            Log.e(TAG, "Error saving image to internal storage", e)
            null
        }
    }

    /**
     * Resize image if its dimensions exceed the maximum allowed
     */
    private fun resizeImageIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_WIDTH && height <= MAX_IMAGE_HEIGHT) {
            Log.d(TAG, "Image dimensions are within limits, no resizing needed")
            return bitmap
        }

        Log.d(TAG, "Image is too large ($width x $height), resizing...")

        val ratio = width.toFloat() / height.toFloat()
        var newWidth = MAX_IMAGE_WIDTH
        var newHeight = (newWidth / ratio).toInt()

        if (newHeight > MAX_IMAGE_HEIGHT) {
            newHeight = MAX_IMAGE_HEIGHT
            newWidth = (newHeight * ratio).toInt()
        }

        val resizedBitmap = bitmap.scale(newWidth, newHeight)
        Log.d(TAG, "Image resized to $newWidth x $newHeight")

        return resizedBitmap
    }
}
