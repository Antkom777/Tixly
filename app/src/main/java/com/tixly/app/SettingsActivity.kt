package com.tixly.app

import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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

                // Перезавантажуємо активність для застосування нової мови
                recreate()

                // Встановлюємо результат, щоб попередня активність знала про зміну мови
                setResult(RESULT_OK)
            }
        }

        // Notifications enable/disable handler
        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            settingsManager.setNotificationsEnabled(isChecked)
            updateNotificationTimeVisibility(isChecked)
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
                settingsManager.setNotificationTime(timeInHours)
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

        val context = createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
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
