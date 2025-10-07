package com.tixly.app.utils

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_NOTIFICATION_TIME = "notification_time"
        private const val KEY_ADS_REMOVED = "ads_removed"

        const val LANGUAGE_UKRAINIAN = "uk"
        const val LANGUAGE_ENGLISH = "en"

        const val NOTIFICATION_1_HOUR = 1
        const val NOTIFICATION_2_HOURS = 2
        const val NOTIFICATION_1_DAY = 24
        const val NOTIFICATION_2_DAYS = 48
        const val NOTIFICATION_1_WEEK = 168
    }

    fun getLanguage(): String {
        return preferences.getString(KEY_LANGUAGE, LANGUAGE_UKRAINIAN) ?: LANGUAGE_UKRAINIAN
    }

    fun setLanguage(language: String) {
        preferences.edit().putString(KEY_LANGUAGE, language).apply()
    }

    fun getNotificationsEnabled(): Boolean {
        return preferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, false)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
    }

    fun getNotificationTime(): Int {
        return preferences.getInt(KEY_NOTIFICATION_TIME, NOTIFICATION_1_DAY)
    }

    fun setNotificationTime(hours: Int) {
        preferences.edit().putInt(KEY_NOTIFICATION_TIME, hours).apply()
    }

    fun getAdsRemoved(): Boolean {
        return preferences.getBoolean(KEY_ADS_REMOVED, false)
    }

    fun setAdsRemoved(removed: Boolean) {
        preferences.edit().putBoolean(KEY_ADS_REMOVED, removed).apply()
    }
}
