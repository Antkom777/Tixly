package com.tixly.app.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.tixly.app.data.Ticket
import java.util.*

class CalendarHelper(private val context: Context) {

    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun addTicketToCalendar(ticket: Ticket): Boolean {
        if (!hasCalendarPermission()) {
            return false
        }

        ticket.eventDate?.let { eventDate ->
            try {
                val values = ContentValues().apply {
                    put(CalendarContract.Events.DTSTART, eventDate.time)
                    put(CalendarContract.Events.DTEND, eventDate.time + (2 * 60 * 60 * 1000)) // +2 hours
                    put(CalendarContract.Events.TITLE, ticket.title)
                    put(CalendarContract.Events.DESCRIPTION, buildEventDescription(ticket))
                    put(CalendarContract.Events.EVENT_LOCATION, ticket.venue)
                    put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                    put(CalendarContract.Events.HAS_ALARM, 1)
                }

                val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)

                // Add reminder 1 hour before event
                uri?.let { eventUri ->
                    addEventReminder(eventUri.lastPathSegment?.toLongOrNull() ?: 0L, 60)
                }

                return uri != null
            } catch (e: Exception) {
                e.printStackTrace()
                return false
            }
        }

        return false
    }

    private fun buildEventDescription(ticket: Ticket): String {
        return buildString {
            append("${ticket.description}\n\n")
            ticket.venue?.let { append("Місце: $it\n") }
            ticket.price?.let { append("Ціна: $it\n") }
            ticket.seatInfo?.let { append("Місця: $it\n") }
            ticket.qrCode?.let { append("QR код: $it\n") }
        }
    }

    private fun getDefaultCalendarId(): Long {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null
        )

        cursor?.use {
            if (it.moveToFirst()) {
                return it.getLong(0)
            }
        }

        return 1L // Fallback to first calendar
    }

    private fun addEventReminder(eventId: Long, minutesBefore: Int) {
        val reminderValues = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }

        try {
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
