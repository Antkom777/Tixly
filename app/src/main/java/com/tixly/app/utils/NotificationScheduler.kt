package com.tixly.app.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tixly.app.data.Ticket
import java.util.*

object NotificationScheduler {

    fun scheduleEventReminder(context: Context, ticket: Ticket, notificationTime: Int) {
        val eventDate = ticket.eventDate ?: return

        android.util.Log.d("NotificationScheduler", "=== Scheduling notification ===")
        android.util.Log.d("NotificationScheduler", "Ticket: ${ticket.title}")
        android.util.Log.d("NotificationScheduler", "Event date: $eventDate")
        android.util.Log.d("NotificationScheduler", "Notification time setting: $notificationTime")

        // Calculate notification time based on user preference (in hours)
        val notificationDate = Calendar.getInstance().apply {
            time = eventDate
            when (notificationTime) {
                SettingsManager.NOTIFICATION_1_HOUR -> {
                    add(Calendar.HOUR, -1)  // 1 hour before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 1 hour before")
                }
                SettingsManager.NOTIFICATION_2_HOURS -> {
                    add(Calendar.HOUR, -2)  // 2 hours before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 2 hours before")
                }
                SettingsManager.NOTIFICATION_1_DAY -> {
                    add(Calendar.DAY_OF_MONTH, -1)  // 1 day before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 1 day before")
                }
                SettingsManager.NOTIFICATION_2_DAYS -> {
                    add(Calendar.DAY_OF_MONTH, -2)  // 2 days before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 2 days before")
                }
                SettingsManager.NOTIFICATION_1_WEEK -> {
                    add(Calendar.WEEK_OF_YEAR, -1)  // 1 week before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 1 week before")
                }
                else -> {
                    add(Calendar.HOUR, -1)  // default: 1 hour before
                    android.util.Log.d("NotificationScheduler", "Setting reminder for 1 hour before (default)")
                }
            }
        }

        android.util.Log.d("NotificationScheduler", "Event date: ${eventDate}")
        android.util.Log.d("NotificationScheduler", "Calculated notification date: ${notificationDate.time}")
        android.util.Log.d("NotificationScheduler", "Current time: ${Date()}")

        val timeUntilNotification = (notificationDate.timeInMillis - System.currentTimeMillis()) / 1000 / 60
        android.util.Log.d("NotificationScheduler", "Time until notification: $timeUntilNotification minutes")

        // Don't schedule if the notification time has already passed
        if (notificationDate.timeInMillis <= System.currentTimeMillis()) {
            android.util.Log.w("NotificationScheduler", "⚠️ Notification time has passed for ticket: ${ticket.title}")
            android.util.Log.w("NotificationScheduler", "Expected: ${notificationDate.time}, Current: ${Date()}")
            return
        }

        // Check notification permissions
        if (!NotificationHelper.areNotificationsEnabled(context)) {
            android.util.Log.w("NotificationScheduler", "⚠️ Notifications are disabled in system settings")
            return
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("ticketTitle", ticket.title)
            putExtra("venue", ticket.venue ?: "")
            putExtra("ticketId", ticket.id)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ticket.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            // Check if we can schedule exact alarms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        notificationDate.timeInMillis,
                        pendingIntent
                    )
                    android.util.Log.d("NotificationScheduler", "✅ Scheduled exact alarm with setExactAndAllowWhileIdle")
                } else {
                    android.util.Log.w("NotificationScheduler", "⚠️ Cannot schedule exact alarms - requesting permission")
                    // For Android 12+ we need to request exact alarm permission
                    val settingsIntent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    if (context is android.app.Activity) {
                        context.startActivity(settingsIntent)
                    }
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notificationDate.timeInMillis,
                    pendingIntent
                )
                android.util.Log.d("NotificationScheduler", "✅ Scheduled exact alarm with setExactAndAllowWhileIdle (API < 31)")
            }

            android.util.Log.d("NotificationScheduler", "🔔 Successfully scheduled notification for ticket: ${ticket.title}")
            android.util.Log.d("NotificationScheduler", "📅 Will trigger at: ${notificationDate.time}")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationScheduler", "❌ Permission denied for scheduling exact alarms", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationScheduler", "❌ Error scheduling notification", e)
        }
    }

    fun cancelEventReminder(context: Context, ticketId: String) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ticketId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        android.util.Log.d("NotificationScheduler", "Cancelled notification for ticket: $ticketId")
    }

    fun rescheduleAllNotifications(context: Context, tickets: List<Ticket>, notificationTime: Int) {
        android.util.Log.d("NotificationScheduler", "=== Rescheduling all notifications ===")
        android.util.Log.d("NotificationScheduler", "Total tickets to process: ${tickets.size}")

        // First, cancel all existing notifications for these tickets
        tickets.forEach { ticket ->
            cancelEventReminder(context, ticket.id)
        }

        // Then, schedule new notifications for upcoming events
        val upcomingTickets = tickets.filter { it.eventDate != null && it.eventDate.after(Date()) }
        android.util.Log.d("NotificationScheduler", "Upcoming tickets to schedule: ${upcomingTickets.size}")

        upcomingTickets.forEach { ticket ->
            scheduleEventReminder(context, ticket, notificationTime)
        }
    }
}
