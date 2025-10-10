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
import com.tixly.app.billing.BillingManager
import com.tixly.app.data.TicketsRepository
import com.tixly.app.utils.NotificationHelper
import com.tixly.app.utils.NotificationScheduler
import com.tixly.app.utils.SettingsManager
import java.util.*

class SettingsActivity : BaseActivity(), BillingManager.BillingListener {

    companion object {
        // Debug flag - змініть на false для релізу
        private const val DEBUG_ENABLED = true
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var billingManager: BillingManager
    private lateinit var radioGroupLanguage: RadioGroup
    private lateinit var radioUkrainian: RadioButton
    private lateinit var radioEnglish: RadioButton
    private lateinit var switchNotifications: Switch
    private lateinit var textNotificationTime: TextView
    private lateinit var spinnerNotificationTime: Spinner
    private lateinit var buttonRemoveAds: Button
    private lateinit var textAppStatus: TextView // Новий TextView для статусу

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
        billingManager = BillingManager(this, this)

        initViews()
        setupListeners()
        loadCurrentSettings()

        // DEBUG ONLY: додаємо можливість скинути статус преміум довгим натисканням на статус
        if (DEBUG_ENABLED) {
            textAppStatus.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.debug_reset_premium_title))
                    .setMessage(getString(R.string.debug_reset_premium_message))
                    .setPositiveButton("Yes") { _, _ ->
                        android.util.Log.d("SettingsActivity", "DEBUG: Resetting premium status to FALSE")
                        settingsManager.setAdsRemoved(false)
                        updatePremiumUI()
                        Toast.makeText(this, getString(R.string.debug_status_reset_message), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("No", null)
                    .show()
                true
            }
        }

        // Запускаємо ініціалізацію BillingManager після повного створення об'єкта
        billingManager.startInitialization()
    }

    private fun initViews() {
        radioGroupLanguage = findViewById(R.id.radioGroupLanguage)
        radioUkrainian = findViewById(R.id.radioUkrainian)
        radioEnglish = findViewById(R.id.radioEnglish)
        switchNotifications = findViewById(R.id.switchNotifications)
        textNotificationTime = findViewById(R.id.textNotificationTime)
        spinnerNotificationTime = findViewById(R.id.spinnerNotificationTime)
        buttonRemoveAds = findViewById(R.id.buttonRemoveAds)
        textAppStatus = findViewById(R.id.textAppStatus) // Ініціалізуємо новий TextView

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

        // Remove ads handler - тепер використовуємо справжній BillingManager
        buttonRemoveAds.setOnClickListener {
            if (!billingManager.isRemoveAdsPurchased()) {
                if (billingManager.isReady()) {
                    // Show loading state
                    buttonRemoveAds.isEnabled = false
                    buttonRemoveAds.text = getString(R.string.loading)

                    // Launch purchase flow (з перевіркою в хмарі)
                    billingManager.purchaseRemoveAds(this)
                } else {
                    Toast.makeText(this, getString(R.string.billing_service_unavailable), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.ads_already_removed), Toast.LENGTH_SHORT).show()
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

        // Load ads state and update UI
        updatePremiumUI()
    }

    private fun updatePremiumUI() {
        val isPremium = billingManager.isRemoveAdsPurchased()
        android.util.Log.d("SettingsActivity", "updatePremiumUI: isPremium = $isPremium")

        // ЗАВЖДИ оновлюємо статус
        if (isPremium) {
            textAppStatus.text = getString(R.string.status_premium)
            textAppStatus.setTextColor(ContextCompat.getColor(this, R.color.pale_green))
            textAppStatus.setTypeface(null, android.graphics.Typeface.NORMAL) // Преміум - звичайним шрифтом
            // ХОВАЄМО кнопку для преміум користувачів
            buttonRemoveAds.visibility = android.view.View.GONE
            android.util.Log.d("SettingsActivity", "Premium user - hiding button")
        } else {
            textAppStatus.text = getString(R.string.status_free)
            textAppStatus.setTextColor(ContextCompat.getColor(this, R.color.colorPrimary))
            textAppStatus.setTypeface(null, android.graphics.Typeface.NORMAL) // Безкоштовна - звичайним шрифтом
            // ПОКАЗУЄМО кнопку для безкоштовних користувачів
            buttonRemoveAds.visibility = android.view.View.VISIBLE
            buttonRemoveAds.isEnabled = true
            // Показуємо тільки назву без ціни - ціну користувач побачить в діалозі
            buttonRemoveAds.text = getString(R.string.remove_ads_setting)
            android.util.Log.d("SettingsActivity", "Free user - showing button")
        }
    }

    private fun updateRemoveAdsButton() {
        // Цей метод тепер не потрібен, всю логіку перенесено в updatePremiumUI()
        // Залишаємо його для сумісності, але вся логіка тепер в updatePremiumUI()
        android.util.Log.d("SettingsActivity", "updateRemoveAdsButton called - redirecting to updatePremiumUI")
        updatePremiumUI()
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

    // BillingManager.BillingListener implementation
    override fun onBillingSetupFinished(success: Boolean) {
        android.util.Log.d("SettingsActivity", "Billing setup finished: $success")
        runOnUiThread {
            updatePremiumUI()
        }
    }

    override fun onBillingServiceDisconnected() {
        android.util.Log.d("SettingsActivity", "Billing service disconnected")
        runOnUiThread {
            buttonRemoveAds.isEnabled = false
            buttonRemoveAds.text = getString(R.string.billing_service_unavailable)
        }
    }

    override fun onPurchaseSuccess(productId: String) {
        android.util.Log.d("SettingsActivity", "NEW Purchase successful: $productId")
        runOnUiThread {
            if (productId == BillingManager.REMOVE_ADS_PRODUCT_ID) {
                updatePremiumUI()

                // Show success message ONLY for new purchases
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.purchase_successful))
                    .setMessage(getString(R.string.ads_removed_successfully))
                    .setPositiveButton("OK", null)
                    .show()

                // Notify other activities about ads removal
                setResult(RESULT_OK)
            }
        }
    }

    override fun onPurchaseRestored(productId: String) {
        android.util.Log.d("SettingsActivity", "Purchase RESTORED (not showing dialog): $productId")
        runOnUiThread {
            if (productId == BillingManager.REMOVE_ADS_PRODUCT_ID) {
                // Тільки оновлюємо UI, БЕЗ показу діалогу успіху
                updatePremiumUI()
            }
        }
    }

    override fun onPurchaseError(errorMessage: String) {
        android.util.Log.e("SettingsActivity", "Purchase error: $errorMessage")
        runOnUiThread {
            updatePremiumUI()
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onPurchaseCanceled() {
        android.util.Log.d("SettingsActivity", "Purchase canceled")
        runOnUiThread {
            updatePremiumUI()
            Toast.makeText(this, getString(R.string.purchase_canceled), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCloudVerificationStarted() {
        android.util.Log.d("SettingsActivity", "Starting cloud verification...")
        runOnUiThread {
            // Show loading state during cloud verification
            buttonRemoveAds.isEnabled = false
            buttonRemoveAds.text = getString(R.string.checking_purchase_status)
        }
    }

    override fun onCloudVerificationCompleted(isPurchased: Boolean) {
        android.util.Log.d("SettingsActivity", "Cloud verification completed: isPurchased = $isPurchased")
        runOnUiThread {
            if (isPurchased) {
                // User already purchased - just update UI, no dialog needed
                updatePremiumUI()
                Toast.makeText(this, getString(R.string.ads_already_removed), Toast.LENGTH_SHORT).show()
            } else {
                // User hasn't purchased - show purchase dialog
                showPurchaseConfirmationDialog()
            }
        }
    }

    private fun showPurchaseConfirmationDialog() {
        val price = billingManager.getRemoveAdsPrice() ?: "₴49.00"

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_ads_setting))
            .setMessage(getString(R.string.purchase_confirmation_message, price))
            .setPositiveButton(getString(R.string.purchase_now)) { _, _ ->
                // Launch actual purchase dialog
                buttonRemoveAds.isEnabled = false
                buttonRemoveAds.text = getString(R.string.processing_purchase)
                billingManager.launchPurchaseDialog(this)
            }
            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                // Reset button state
                updatePremiumUI()
            }
            .setOnCancelListener {
                // Reset button state if dialog is cancelled
                updatePremiumUI()
            }
            .show()
    }

    override fun onDestroy() {
        billingManager.destroy()
        super.onDestroy()
    }
}
