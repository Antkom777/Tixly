package com.tixly.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.tixly.app.utils.SettingsManager
import com.tixly.app.utils.NotificationHelper
import com.tixly.app.utils.AdManager
import java.util.*

class TixlyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Apply saved language setting on app startup
        applySavedLanguage()

        // Initialize notification channels
        NotificationHelper.createNotificationChannel(this)

        // Initialize AdMob SDK
        AdManager.initialize(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(updateBaseContextLocale(base))
    }

    private fun applySavedLanguage() {
        val settingsManager = SettingsManager(this)
        val languageCode = settingsManager.getLanguage()
        setLocale(languageCode)
    }

    private fun updateBaseContextLocale(context: Context): Context {
        val settingsManager = SettingsManager(context)
        val languageCode = settingsManager.getLanguage()

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)

        return context.createConfigurationContext(configuration)
    }

    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val configuration = Configuration()
        configuration.setLocale(locale)

        // Apply configuration without using deprecated updateConfiguration
        createConfigurationContext(configuration)
    }
}
