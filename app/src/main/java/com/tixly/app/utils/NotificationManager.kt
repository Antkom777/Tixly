package com.tixly.app.utils

import android.content.Context
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository

/**
 * Centralized notification manager for handling ticket notifications
 * Responsible for recalculating notifications when settings or tickets change
 */
object NotificationManager {

    /**
     * Recalculate notification for a single ticket after save/update
     * Called after every ticket save operation
     */
    fun recalculateNotificationForTicket(context: Context, ticket: Ticket) {
        android.util.Log.d("NotificationManager", "=== Recalculating notification for ticket ===")
        android.util.Log.d("NotificationManager", "Ticket: ${ticket.title}")
        android.util.Log.d("NotificationManager", "Event date: ${ticket.eventDate}")

        val settingsManager = SettingsManager(context)

        // First cancel existing notification for this ticket
        NotificationScheduler.cancelEventReminder(context, ticket.id)
        android.util.Log.d("NotificationManager", "Cancelled previous notification for ticket: ${ticket.id}")

        // Check if notifications are enabled and event has a date
        if (settingsManager.getNotificationsEnabled() && ticket.eventDate != null && ticket.isUpcoming()) {
            val notificationTime = settingsManager.getNotificationTime()
            android.util.Log.d("NotificationManager", "Scheduling new notification...")
            android.util.Log.d("NotificationManager", "Time setting: $notificationTime")

            NotificationScheduler.scheduleEventReminder(context, ticket, notificationTime)
            android.util.Log.d("NotificationManager", "✅ Notification scheduled for ticket: ${ticket.title}")
        } else {
            android.util.Log.d("NotificationManager", "❌ Notification not scheduled:")
            if (!settingsManager.getNotificationsEnabled()) {
                android.util.Log.d("NotificationManager", "  - Notifications are disabled")
            }
            if (ticket.eventDate == null) {
                android.util.Log.d("NotificationManager", "  - Event date not specified")
            }
            if (ticket.eventDate != null && !ticket.isUpcoming()) {
                android.util.Log.d("NotificationManager", "  - Event has already passed")
            }
        }
    }

    /**
     * Recalculate notifications for all active tickets
     * Called when notification settings change
     */
    fun recalculateAllNotifications(context: Context) {
        android.util.Log.d("NotificationManager", "=== Recalculating all notifications ===")

        val settingsManager = SettingsManager(context)
        val allTickets = TicketsRepository.getAllTickets()
        val upcomingTickets = allTickets.filter { it.isUpcoming() }

        android.util.Log.d("NotificationManager", "Total tickets: ${allTickets.size}")
        android.util.Log.d("NotificationManager", "Upcoming events: ${upcomingTickets.size}")
        android.util.Log.d("NotificationManager", "Notifications enabled: ${settingsManager.getNotificationsEnabled()}")
        android.util.Log.d("NotificationManager", "Reminder time: ${settingsManager.getNotificationTime()}")

        // First cancel all existing notifications
        allTickets.forEach { ticket ->
            NotificationScheduler.cancelEventReminder(context, ticket.id)
        }
        android.util.Log.d("NotificationManager", "Cancelled all previous notifications")

        // If notifications are enabled, schedule new ones for upcoming events
        if (settingsManager.getNotificationsEnabled()) {
            val notificationTime = settingsManager.getNotificationTime()
            var scheduledCount = 0

            upcomingTickets.forEach { ticket ->
                if (ticket.eventDate != null) {
                    NotificationScheduler.scheduleEventReminder(context, ticket, notificationTime)
                    scheduledCount++
                    android.util.Log.d("NotificationManager", "Scheduled notification for: ${ticket.title}")
                }
            }

            android.util.Log.d("NotificationManager", "✅ Scheduled $scheduledCount notifications")
        } else {
            android.util.Log.d("NotificationManager", "❌ Notifications disabled - nothing scheduled")
        }
    }

    /**
     * Handle notification enabled/disabled change
     */
    fun onNotificationsEnabledChanged(context: Context, enabled: Boolean) {
        android.util.Log.d("NotificationManager", "=== Notification setting changed ===")
        android.util.Log.d("NotificationManager", "Notifications enabled: $enabled")

        if (!enabled) {
            // If notifications are disabled - cancel all
            val allTickets = TicketsRepository.getAllTickets()
            allTickets.forEach { ticket ->
                NotificationScheduler.cancelEventReminder(context, ticket.id)
            }
            android.util.Log.d("NotificationManager", "❌ Cancelled all notifications (disabled)")
        } else {
            // If notifications are enabled - recalculate for all
            android.util.Log.d("NotificationManager", "✅ Notifications enabled - recalculating...")
            recalculateAllNotifications(context)
        }
    }

    /**
     * Handle notification time setting change
     */
    fun onNotificationTimeChanged(context: Context, newNotificationTime: Int) {
        android.util.Log.d("NotificationManager", "=== Notification time changed ===")
        android.util.Log.d("NotificationManager", "New reminder time: $newNotificationTime")

        val settingsManager = SettingsManager(context)

        // Recalculate only if notifications are enabled
        if (settingsManager.getNotificationsEnabled()) {
            android.util.Log.d("NotificationManager", "✅ Recalculating due to time change...")
            recalculateAllNotifications(context)
        } else {
            android.util.Log.d("NotificationManager", "❌ Notifications disabled - nothing to do")
        }
    }

    /**
     * Handle ticket deletion
     */
    fun onTicketDeleted(context: Context, ticketId: String) {
        android.util.Log.d("NotificationManager", "=== Ticket deleted ===")
        android.util.Log.d("NotificationManager", "Ticket ID: $ticketId")

        NotificationScheduler.cancelEventReminder(context, ticketId)
        android.util.Log.d("NotificationManager", "✅ Notification cancelled for deleted ticket")
    }
}
