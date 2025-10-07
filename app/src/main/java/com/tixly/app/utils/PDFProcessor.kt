package com.tixly.app.utils

import android.content.Context
import android.net.Uri
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
import java.io.File
import java.io.FileOutputStream
import java.util.*

class PDFProcessor(private val context: Context) {

    fun processPDF(uri: Uri): Ticket? {
        return try {
            // Спочатку перевіряємо на дублювання PDF файлу
            val fileName = getFileNameFromUri(uri)
            if (fileName != null) {
                val duplicateTicket = checkForDuplicatePdf(fileName)
                if (duplicateTicket != null) {
                    // Повертаємо null і показуємо помилку в активності
                    return null
                }
            }

            // Копіюємо PDF файл у внутрішнє сховище додатка
            val savedPdfPath = savePdfToInternalStorage(uri)

            // Створюємо квиток з базовою назвою та шляхом до збереженого PDF
            Ticket(
                title = "Новий квиток",
                description = "Квиток з PDF файлу",
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
            // Створюємо папку для PDF файлів у внутрішньому сховищі
            val pdfDir = File(context.filesDir, "pdf_tickets")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

            // Генеруємо унікальне ім'я файлу
            val fileName = "ticket_${UUID.randomUUID()}.pdf"
            val destinationFile = File(pdfDir, fileName)

            // Копіюємо файл
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
