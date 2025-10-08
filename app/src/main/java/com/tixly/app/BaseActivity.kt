package com.tixly.app

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.tixly.app.utils.SettingsManager
import com.tixly.app.utils.NotificationHelper
import java.util.*

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContextLocale(newBase))
    }

    override fun onResume() {
        super.onResume()
        // Only ensure notification channel is created, no permission requests
        NotificationHelper.createNotificationChannel(this)
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
}
