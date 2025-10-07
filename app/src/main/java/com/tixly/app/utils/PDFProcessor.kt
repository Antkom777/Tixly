package com.tixly.app.utils

import android.content.Context
import android.net.Uri
import android.content.Intent
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PDFProcessor(private val context: Context) {

    fun processPDF(uri: Uri): Ticket? {
        return try {
            // First check for PDF file duplication
            val fileName = getFileNameFromUri(uri)
            if (fileName != null) {
                val duplicateTicket = checkForDuplicatePdf(fileName)
                if (duplicateTicket != null) {
                    // Return null and show error in activity
                    return null
                }
            }

            // Take persistent permission for content URIs
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w("PDFProcessor", "Could not take persistable permission for URI: $uri", e)
                    // Continue anyway, the internal copy might work
                }
            }

            // Copy PDF file to app's internal storage
            val savedPdfPath = savePdfToInternalStorage(uri)

            // Create ticket with basic title and path to saved PDF
            Ticket(
                title = context.getString(com.tixly.app.R.string.new_event),
                description = context.getString(com.tixly.app.R.string.manually_created_ticket),
                eventDate = null,
                venue = null,
                pdfUri = uri.toString(),
                pdfFilePath = savedPdfPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun checkForDuplicatePdf(fileName: String): Ticket? {
        val allTickets = TicketsRepository.getAllTickets()
        return allTickets.find { ticket ->
            val existingFileName = ticket.pdfFilePath?.let { File(it).name }
                ?: ticket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }

            existingFileName != null && existingFileName == fileName
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("_display_name")
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    uri.lastPathSegment
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun savePdfToInternalStorage(uri: Uri): String? {
        return try {
            // Create folder for PDF files in internal storage
            val pdfDir = File(context.filesDir, "pdf_tickets")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

            // Generate unique file name
            val fileName = "ticket_${UUID.randomUUID()}.pdf"
            val destinationFile = File(pdfDir, fileName)

            // Copy file
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
