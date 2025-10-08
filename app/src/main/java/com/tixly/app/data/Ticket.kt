package com.tixly.app.data

import org.json.JSONObject
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
    val pdfUri: String? = null // Add URI for opening PDF
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

    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("description", description)
        json.put("eventDate", eventDate?.time)
        json.put("venue", venue)
        json.put("price", price)
        json.put("seatInfo", seatInfo)
        json.put("qrCode", qrCode)
        json.put("barcode", barcode)
        json.put("createdDate", createdDate.time)
        json.put("pdfFilePath", pdfFilePath)
        json.put("pdfUri", pdfUri)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): Ticket {
            val json = JSONObject(jsonString)
            return Ticket(
                id = json.getString("id"),
                title = json.getString("title"),
                description = json.getString("description"),
                eventDate = if (json.has("eventDate") && !json.isNull("eventDate")) Date(json.getLong("eventDate")) else null,
                venue = if (json.has("venue") && !json.isNull("venue")) json.getString("venue") else null,
                price = if (json.has("price") && !json.isNull("price")) json.getString("price") else null,
                seatInfo = if (json.has("seatInfo") && !json.isNull("seatInfo")) json.getString("seatInfo") else null,
                qrCode = if (json.has("qrCode") && !json.isNull("qrCode")) json.getString("qrCode") else null,
                barcode = if (json.has("barcode") && !json.isNull("barcode")) json.getString("barcode") else null,
                createdDate = Date(json.getLong("createdDate")),
                pdfFilePath = if (json.has("pdfFilePath") && !json.isNull("pdfFilePath")) json.getString("pdfFilePath") else null,
                pdfUri = if (json.has("pdfUri") && !json.isNull("pdfUri")) json.getString("pdfUri") else null
            )
        }
    }
}
