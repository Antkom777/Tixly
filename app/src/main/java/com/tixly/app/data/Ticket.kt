package com.tixly.app.data

import java.text.SimpleDateFormat
import java.util.*

data class Ticket(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val eventDate: Date? = null,
    val venue: String? = null,
    val price: String? = null,
    val seatInfo: String? = null,
    val qrCode: String? = null,
    val barcode: String? = null,
    val createdDate: Date = Date(),
    val pdfFilePath: String? = null,
    val pdfUri: String? = null // Додаємо URI для відкриття PDF
) {
    fun getFormattedEventDate(): String {
        return eventDate?.let {
            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it)
        } ?: "Дата не вказана"
    }

    fun getFormattedCreatedDate(): String {
        return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(createdDate)
    }

    fun isUpcoming(): Boolean {
        return eventDate?.after(Date()) ?: false
    }
}
