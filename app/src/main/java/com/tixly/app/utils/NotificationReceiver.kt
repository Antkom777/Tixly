package com.tixly.app.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("NotificationReceiver", "Received notification intent")

        val ticketTitle = intent.getStringExtra("ticketTitle") ?: return
        val venue = intent.getStringExtra("venue") ?: ""
        val ticketId = intent.getStringExtra("ticketId") ?: return

        android.util.Log.d("NotificationReceiver", "Showing notification for: $ticketTitle at $venue")

        NotificationHelper.showEventReminder(context, ticketTitle, venue, ticketId)
    }
}
