package com.tixly.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.*

object TicketsRepository {
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    private val tickets = mutableListOf<Ticket>()
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            sharedPreferences = context.getSharedPreferences("tixly_tickets_data", Context.MODE_PRIVATE)
            loadTickets()
            // Додаємо логування для діагностики
            android.util.Log.d("TicketsRepository", "Initialized with ${tickets.size} tickets")
            if (tickets.isEmpty()) {
                android.util.Log.d("TicketsRepository", "No saved tickets found, creating sample data")
                initializeWithSampleTickets()
            }
            isInitialized = true
        }
    }

    private fun loadTickets() {
        try {
            val ticketsJson = sharedPreferences?.getString("tickets_data", null)
            android.util.Log.d("TicketsRepository", "Loading tickets, JSON length: ${ticketsJson?.length ?: 0}")

            if (ticketsJson != null && ticketsJson.isNotEmpty()) {
                val type = object : TypeToken<List<Ticket>>() {}.type
                val loadedTickets: List<Ticket> = gson.fromJson(ticketsJson, type)
                tickets.clear()
                tickets.addAll(loadedTickets)
                android.util.Log.d("TicketsRepository", "Successfully loaded ${tickets.size} tickets")
            } else {
                android.util.Log.d("TicketsRepository", "No tickets data found in SharedPreferences")
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketsRepository", "Error loading tickets", e)
            // Очищуємо пошкоджені дані
            tickets.clear()
        }
    }

    private fun saveTickets() {
        try {
            val ticketsJson = gson.toJson(tickets)
            val success = sharedPreferences?.edit()?.putString("tickets_data", ticketsJson)?.commit() ?: false
            android.util.Log.d("TicketsRepository", "Saving ${tickets.size} tickets, success: $success")
        } catch (e: Exception) {
            android.util.Log.e("TicketsRepository", "Error saving tickets", e)
        }
    }

    fun getAllTickets(): List<Ticket> {
        return tickets.toList()
    }

    fun addTicket(ticket: Ticket) {
        tickets.add(ticket)
        saveTickets()
    }

    fun removeTicket(ticketId: String) {
        // Знаходимо квиток для видалення його PDF файлу
        val ticketToRemove = tickets.find { it.id == ticketId }

        // Видаляємо PDF файл з внутрішнього сховища якщо він є
        ticketToRemove?.pdfFilePath?.let { filePath ->
            try {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Видаляємо квиток зі списку
        tickets.removeAll { it.id == ticketId }
        saveTickets()
    }

    fun getTicketById(ticketId: String): Ticket? {
        return tickets.find { it.id == ticketId }
    }

    fun updateTicket(updatedTicket: Ticket) {
        val index = tickets.indexOfFirst { it.id == updatedTicket.id }
        if (index != -1) {
            tickets[index] = updatedTicket
            saveTickets()
        }
    }

    fun getUpcomingTickets(): List<Ticket> {
        return tickets.filter { it.isUpcoming() }
    }

    fun getPastTickets(): List<Ticket> {
        return tickets.filter { !it.isUpcoming() }
    }

    private fun initializeWithSampleTickets() {
        // Додаємо тестові квитки тільки якщо немає збережених
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

        val sampleTickets = listOf(
            Ticket(
                id = "1",
                title = "Концерт рок-групи",
                description = "Дозволено фото та відео",
                venue = "Палац спорту",
                eventDate = try { dateFormat.parse("15.12.2024 19:00") } catch (e: Exception) { null },
                seatInfo = "Сектор А, ряд 5, місце 12",
                price = "1200 грн",
                pdfFilePath = null
            ),
            Ticket(
                id = "2",
                title = "Театральна вистава",
                description = "Дрес-код: офіційний",
                venue = "Національний театр",
                eventDate = try { dateFormat.parse("20.12.2024 18:30") } catch (e: Exception) { null },
                seatInfo = "Партер, ряд 10, місце 8",
                price = "800 грн",
                pdfFilePath = null
            )
        )

        tickets.addAll(sampleTickets)
        saveTickets()
    }
}
