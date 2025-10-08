package com.tixly.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tixly.app.R
import java.text.SimpleDateFormat
import java.util.*

object TicketsRepository {
    private var sharedPreferences: SharedPreferences? = null
    private val gson = Gson()
    private val tickets = mutableListOf<Ticket>()
    private var isInitialized = false
    private var context: Context? = null

    fun initialize(context: Context) {
        if (!isInitialized) {
            this.context = context
            sharedPreferences = context.getSharedPreferences("tixly_tickets_data", Context.MODE_PRIVATE)
            loadTickets()
            // Add logging for diagnostics
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
            // Clear corrupted data
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
        android.util.Log.d("TicketsRepository", "addTicket called: ID=${ticket.id}, Title='${ticket.title}', TempTicket=${ticket.pdfFilePath != null}")
        android.util.Log.d("TicketsRepository", "Stack trace:", Exception("addTicket called"))
        tickets.add(ticket)
        saveTickets()
    }

    fun removeTicket(ticketId: String) {
        // Find ticket to remove its PDF file
        val ticketToRemove = tickets.find { it.id == ticketId }

        // Remove PDF file from internal storage if it exists
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

        // Remove ticket from list
        tickets.removeAll { it.id == ticketId }
        saveTickets()
    }

    fun getTicketById(ticketId: String): Ticket? {
        return tickets.find { it.id == ticketId }
    }

    fun updateTicket(updatedTicket: Ticket) {
        android.util.Log.d("TicketsRepository", "=== Updating ticket ===")
        android.util.Log.d("TicketsRepository", "Ticket ID: ${updatedTicket.id}")
        android.util.Log.d("TicketsRepository", "Title: ${updatedTicket.title}")
        android.util.Log.d("TicketsRepository", "PDF file path: ${updatedTicket.pdfFilePath}")
        android.util.Log.d("TicketsRepository", "PDF URI: ${updatedTicket.pdfUri}")

        val index = tickets.indexOfFirst { it.id == updatedTicket.id }
        android.util.Log.d("TicketsRepository", "Found ticket at index: $index")

        if (index != -1) {
            val oldTicket = tickets[index]
            android.util.Log.d("TicketsRepository", "Old PDF path: ${oldTicket.pdfFilePath}")
            android.util.Log.d("TicketsRepository", "New PDF path: ${updatedTicket.pdfFilePath}")

            // Verify that the new PDF file actually exists before updating
            updatedTicket.pdfFilePath?.let { newPath ->
                val file = java.io.File(newPath)
                if (!file.exists()) {
                    android.util.Log.e("TicketsRepository", "ERROR: New PDF file does not exist at: $newPath")
                    android.util.Log.e("TicketsRepository", "File exists check: ${file.exists()}")
                    android.util.Log.e("TicketsRepository", "File absolute path: ${file.absolutePath}")
                    android.util.Log.e("TicketsRepository", "Parent directory exists: ${file.parentFile?.exists()}")
                } else {
                    android.util.Log.d("TicketsRepository", "New PDF file verified: $newPath (${file.length()} bytes)")
                }
            }

            // Clear the old URI when updating with a new file path
            val finalTicket = if (updatedTicket.pdfFilePath != oldTicket.pdfFilePath && !updatedTicket.pdfFilePath.isNullOrEmpty()) {
                android.util.Log.d("TicketsRepository", "Clearing old URI - new file saved internally")
                updatedTicket.copy(pdfUri = null)
            } else {
                updatedTicket
            }

            tickets[index] = finalTicket
            saveTickets()

            // Verify the save was successful by reloading
            val verifyTicket = tickets.find { it.id == finalTicket.id }
            android.util.Log.d("TicketsRepository", "Post-save verification:")
            android.util.Log.d("TicketsRepository", "  Saved PDF path: ${verifyTicket?.pdfFilePath}")
            android.util.Log.d("TicketsRepository", "  Saved PDF URI: ${verifyTicket?.pdfUri}")

            android.util.Log.d("TicketsRepository", "Ticket updated and saved successfully")
        } else {
            android.util.Log.w("TicketsRepository", "Ticket with ID ${updatedTicket.id} not found for update")
        }
    }

    fun getUpcomingTickets(): List<Ticket> {
        return tickets.filter { it.isUpcoming() }
    }

    fun getPastTickets(): List<Ticket> {
        return tickets.filter { !it.isUpcoming() }
    }

    private fun initializeWithSampleTickets() {
        context?.let { ctx ->
            // Add test tickets only if no saved tickets exist
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

            val sampleTickets = listOf(
                Ticket(
                    id = "1",
                    title = ctx.getString(R.string.sample_ticket_1_title),
                    description = "",
                    venue = ctx.getString(R.string.sample_ticket_1_venue),
                    eventDate = try { dateFormat.parse("15.12.2024 19:00") } catch (e: Exception) { null },
                    seatInfo = null,
                    price = null,
                    pdfFilePath = null
                ),
                Ticket(
                    id = "2",
                    title = ctx.getString(R.string.sample_ticket_2_title),
                    description = "",
                    venue = ctx.getString(R.string.sample_ticket_2_venue),
                    eventDate = try { dateFormat.parse("20.12.2024 18:30") } catch (e: Exception) { null },
                    seatInfo = null,
                    price = null,
                    pdfFilePath = null
                )
            )

            tickets.addAll(sampleTickets)
            saveTickets()
        }
    }
}
