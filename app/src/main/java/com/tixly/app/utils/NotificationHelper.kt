package com.tixly.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tixly.app.R
import com.tixly.app.TicketsActivity

object NotificationHelper {
    private const val CHANNEL_ID = "tixly_event_reminders"
    private const val CHANNEL_NAME = "Event Reminders"
    private const val CHANNEL_DESCRIPTION = "Notifications for upcoming events"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            android.util.Log.d("NotificationHelper", "Notification channel created: $CHANNEL_ID")
        }
    }

    fun showEventReminder(context: Context, ticketTitle: String, venue: String, ticketId: String) {
        android.util.Log.d("NotificationHelper", "Showing event reminder for: $ticketTitle")

        // Create intent to open the app when notification is tapped
        val intent = Intent(context, TicketsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ticketId", ticketId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context,
            ticketId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.event_reminder_title))
            .setContentText(context.getString(R.string.event_reminder_text, ticketTitle, venue))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.notify(ticketId.hashCode(), notification)
            android.util.Log.d("NotificationHelper", "Notification sent successfully")
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "Permission denied for notifications", e)
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Error sending notification", e)
        }
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For Android 13+ we need to request POST_NOTIFICATIONS permission
            // This should be handled in the activity
            android.util.Log.d("NotificationHelper", "Notification permission should be requested in activity for Android 13+")
        }
    }
}
