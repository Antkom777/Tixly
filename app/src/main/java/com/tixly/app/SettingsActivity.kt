package com.tixly.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tixly.app.data.TicketsRepository
import com.tixly.app.utils.NotificationHelper
import com.tixly.app.utils.NotificationScheduler
import com.tixly.app.utils.SettingsManager
import java.util.*

class SettingsActivity : BaseActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var radioGroupLanguage: RadioGroup
    private lateinit var radioUkrainian: RadioButton
    private lateinit var radioEnglish: RadioButton
    private lateinit var switchNotifications: Switch
    private lateinit var textNotificationTime: TextView
    private lateinit var spinnerNotificationTime: Spinner
    private lateinit var buttonRemoveAds: Button

    // Notification permission launcher for Android 13+
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("SettingsActivity", "Notification permission granted")
            // Now enable notifications and schedule them
            settingsManager.setNotificationsEnabled(true)
            updateNotificationTimeVisibility(true)
            com.tixly.app.utils.NotificationManager.onNotificationsEnabledChanged(this, true)
        } else {
            android.util.Log.d("SettingsActivity", "Notification permission denied")
            // Disable notifications if permission was denied
            switchNotifications.isChecked = false
            settingsManager.setNotificationsEnabled(false)
            updateNotificationTimeVisibility(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Configure action bar with back button
        supportActionBar?.apply {
            title = getString(R.string.settings)
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager(this)

        initViews()
        setupListeners()
        loadCurrentSettings()
    }

    private fun initViews() {
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage)
        radioUkrainian = findViewById(R.id.radioUkrainian)
        radioEnglish = findViewById(R.id.radioEnglish)
        switchNotifications = findViewById(R.id.switchNotifications)
        textNotificationTime = findViewById(R.id.textNotificationTime)
        spinnerNotificationTime = findViewById(R.id.spinnerNotificationTime)
        buttonRemoveAds = findViewById(R.id.buttonRemoveAds)

        // Setup spinner for reminder timing
        setupNotificationTimeSpinner()
    }

    private fun setupNotificationTimeSpinner() {
        val timeOptions = arrayOf(
            getString(R.string.time_1_hour),
            getString(R.string.time_2_hours),
            getString(R.string.time_1_day),
            getString(R.string.time_2_days),
            getString(R.string.time_1_week)
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, timeOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNotificationTime.adapter = adapter
    }

    private fun setupListeners() {
        // Language change handler
        radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLanguage = when (checkedId) {
                R.id.radioUkrainian -> SettingsManager.LANGUAGE_UKRAINIAN
                R.id.radioEnglish -> SettingsManager.LANGUAGE_ENGLISH
                else -> SettingsManager.LANGUAGE_UKRAINIAN
            }

            if (newLanguage != settingsManager.getLanguage()) {
                settingsManager.setLanguage(newLanguage)

                // Reload activity to apply new language
                recreate()

                // Set result so previous activity knows about language change
                setResult(RESULT_OK)
            }
        }

        // Notifications enable/disable handler
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            android.util.Log.d("SettingsActivity", "=== Notification setting changed ===")

            if (isChecked) {
                // When enabling notifications, request permission first if needed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when {
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED -> {
                            // Permission already granted, proceed with enabling
                            android.util.Log.d("SettingsActivity", "Notification permission already granted")
                            settingsManager.setNotificationsEnabled(true)
                            updateNotificationTimeVisibility(true)
                            com.tixly.app.utils.NotificationManager.onNotificationsEnabledChanged(this, true)
                        }
                        else -> {
                            // Request the permission
                            android.util.Log.d("SettingsActivity", "Requesting notification permission")
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                } else {
                    // No permission needed for older Android versions
                    settingsManager.setNotificationsEnabled(true)
                    updateNotificationTimeVisibility(true)
                    com.tixly.app.utils.NotificationManager.onNotificationsEnabledChanged(this, true)
                }
            } else {
                // When disabling notifications, no permission needed
                settingsManager.setNotificationsEnabled(false)
                updateNotificationTimeVisibility(false)
                com.tixly.app.utils.NotificationManager.onNotificationsEnabledChanged(this, false)
            }
        }

        // Reminder time change handler
        spinnerNotificationTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val timeInHours = when (position) {
                    0 -> SettingsManager.NOTIFICATION_1_HOUR
                    1 -> SettingsManager.NOTIFICATION_2_HOURS
                    2 -> SettingsManager.NOTIFICATION_1_DAY
                    3 -> SettingsManager.NOTIFICATION_2_DAYS
                    4 -> SettingsManager.NOTIFICATION_1_WEEK
                    else -> SettingsManager.NOTIFICATION_1_DAY
                }

                // Only reschedule if the value actually changed
                val currentNotificationTime = settingsManager.getNotificationTime()
                if (timeInHours != currentNotificationTime) {
                    settingsManager.setNotificationTime(timeInHours)

                    // Use the new centralized notification manager
                    android.util.Log.d("SettingsActivity", "=== Notification time change ===")
                    com.tixly.app.utils.NotificationManager.onNotificationTimeChanged(this@SettingsActivity, timeInHours)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Remove ads handler
        buttonRemoveAds.setOnClickListener {
            if (!settingsManager.getAdsRemoved()) {
                settingsManager.setAdsRemoved(true)
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.remove_ads_setting))
                    .setMessage(getString(R.string.remove_ads_message))
                    .setPositiveButton("OK") { _, _ ->
                        buttonRemoveAds.isEnabled = false
                        buttonRemoveAds.text = getString(R.string.remove_ads_message)
                    }
                    .show()
            } else {
                Toast.makeText(this, getString(R.string.remove_ads_message), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCurrentSettings() {
        // Load current language
        when (settingsManager.getLanguage()) {
            SettingsManager.LANGUAGE_UKRAINIAN -> radioUkrainian.isChecked = true
            SettingsManager.LANGUAGE_ENGLISH -> radioEnglish.isChecked = true
        }

        // Load notifications settings
        val notificationsEnabled = settingsManager.getNotificationsEnabled()
        switchNotifications.isChecked = notificationsEnabled
        updateNotificationTimeVisibility(notificationsEnabled)

        // Load reminder time
        val notificationTime = settingsManager.getNotificationTime()
        val spinnerPosition = when (notificationTime) {
            SettingsManager.NOTIFICATION_1_HOUR -> 0
            SettingsManager.NOTIFICATION_2_HOURS -> 1
            SettingsManager.NOTIFICATION_1_DAY -> 2
            SettingsManager.NOTIFICATION_2_DAYS -> 3
            SettingsManager.NOTIFICATION_1_WEEK -> 4
            else -> 2
        }
        spinnerNotificationTime.setSelection(spinnerPosition)

        // Load ads state
        if (settingsManager.getAdsRemoved()) {
            buttonRemoveAds.isEnabled = false
            buttonRemoveAds.text = getString(R.string.remove_ads_message)
        }
    }

    private fun updateNotificationTimeVisibility(enabled: Boolean) {
        textNotificationTime.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
        spinnerNotificationTime.visibility = if (enabled) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun changeLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = resources.configuration
        config.setLocale(locale)

        // Apply configuration without using deprecated updateConfiguration
        createConfigurationContext(config)
    }

    // Schedule notifications based on current settings
    private fun scheduleNotifications() {
        if (!settingsManager.getNotificationsEnabled()) return

        val notificationTime = settingsManager.getNotificationTime()
        TicketsRepository.initialize(this)
        val tickets = TicketsRepository.getUpcomingTickets()

        android.util.Log.d("SettingsActivity", "Scheduling notifications for ${tickets.size} upcoming tickets")

        NotificationScheduler.rescheduleAllNotifications(this, tickets, notificationTime)
    }

    // Cancel all scheduled notifications
    private fun cancelScheduledNotifications() {
        TicketsRepository.initialize(this)
        val tickets = TicketsRepository.getAllTickets()

        android.util.Log.d("SettingsActivity", "Cancelling notifications for ${tickets.size} tickets")

        tickets.forEach { ticket ->
            NotificationScheduler.cancelEventReminder(this, ticket.id)
        }
    }

    // Reschedule notifications, e.g., after changing the notification time
    private fun rescheduleNotifications() {
        if (!settingsManager.getNotificationsEnabled()) return

        val notificationTime = settingsManager.getNotificationTime()
        TicketsRepository.initialize(this)
        val tickets = TicketsRepository.getUpcomingTickets()

        android.util.Log.d("SettingsActivity", "Rescheduling notifications for ${tickets.size} upcoming tickets")

        NotificationScheduler.rescheduleAllNotifications(this, tickets, notificationTime)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
