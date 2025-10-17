package com.tixly.app.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.tixly.app.utils.SettingsManager

/**
 * Manager for handling Google Play Billing operations
 * Simplified version that works with current project setup
 */
class BillingManager(
    private val context: Context,
    private val billingListener: BillingListener? = null
) {

    companion object {
        private const val TAG = "BillingManager"

        // Product ID for removing ads (should match Google Play Console)
        const val REMOVE_ADS_PRODUCT_ID = "remove_ads"
    }

    interface BillingListener {
        fun onBillingSetupFinished(success: Boolean)
        fun onBillingServiceDisconnected()
        fun onPurchaseSuccess(productId: String)
        fun onPurchaseRestored(productId: String) // New method for restored purchases
        fun onPurchaseError(errorMessage: String)
        fun onPurchaseCanceled()
        fun onCloudVerificationStarted() // New method - cloud verification started
        fun onCloudVerificationCompleted(isPurchased: Boolean) // Verification result
    }

    private val settingsManager = SettingsManager(context)
    private var isServiceConnected = false

    init {
        // Don't call initializeBillingClient synchronously in constructor
        // Instead initialize asynchronously
    }

    /**
     * Start billing initialization (called after object creation)
     */
    fun startInitialization() {
        initializeBillingClient()
    }

    /**
     * Initialize the billing client (simplified version)
     */
    private fun initializeBillingClient() {
        Log.d(TAG, "Initializing billing client (simplified)...")

        // Simulate successful initialization for now
        isServiceConnected = true

        // Call callback asynchronously to give SettingsActivity time to initialize
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            billingListener?.onBillingSetupFinished(true)

            // Check existing premium status - call onPurchaseRestored instead of onPurchaseSuccess
            if (settingsManager.getAdsRemoved()) {
                billingListener?.onPurchaseRestored(REMOVE_ADS_PRODUCT_ID)
            }
        }
    }

    /**
     * Verify purchase in cloud before showing purchase dialog
     */
    fun verifyPurchaseInCloud() {
        Log.d(TAG, "Starting cloud verification for existing purchases...")

        billingListener?.onCloudVerificationStarted()

        // Simulate cloud verification delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val isPurchased = settingsManager.getAdsRemoved()
            Log.d(TAG, "Cloud verification result: isPurchased = $isPurchased")

            if (isPurchased) {
                // User already purchased, just update local state
                billingListener?.onPurchaseRestored(REMOVE_ADS_PRODUCT_ID)
            }

            billingListener?.onCloudVerificationCompleted(isPurchased)
        }, 1000) // 1 second delay to simulate network call
    }

    /**
     * Launch purchase flow for removing ads (simplified)
     */
    @Suppress("UNUSED_PARAMETER")
    fun purchaseRemoveAds(activity: Activity) {
        Log.d(TAG, "Starting purchase flow for remove ads (simplified)...")

        if (!isServiceConnected) {
            billingListener?.onPurchaseError("Billing service not connected")
            return
        }

        // First verify in cloud before launching purchase
        Log.d(TAG, "Checking existing purchase in cloud...")
        verifyPurchaseInCloud()
    }

    /**
     * Force launch purchase dialog (after cloud verification)
     */
    @Suppress("UNUSED_PARAMETER")
    fun launchPurchaseDialog(activity: Activity) {
        Log.d(TAG, "Launching actual purchase dialog...")

        // Simulate purchase process
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            settingsManager.setAdsRemoved(true)
            billingListener?.onPurchaseSuccess(REMOVE_ADS_PRODUCT_ID)
        }, 2000) // 2 seconds to simulate purchase process
    }

    /**
     * Check if remove ads is purchased
     */
    fun isRemoveAdsPurchased(): Boolean {
        val result = settingsManager.getAdsRemoved()
        Log.d(TAG, "isRemoveAdsPurchased() returning: $result")
        return result
    }

    /**
     * Get formatted price for remove ads product (placeholder)
     */
    fun getRemoveAdsPrice(): String? {
        return "₴49.00" // Placeholder price
    }

    /**
     * Check if billing client is ready
     */
    fun isReady(): Boolean {
        return isServiceConnected
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        Log.d(TAG, "Destroying billing manager...")
        isServiceConnected = false
    }
}
