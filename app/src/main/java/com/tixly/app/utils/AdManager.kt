package com.tixly.app.utils

import android.content.Context
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.initialization.InitializationStatus
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener

/**
 * Manager for handling Google AdMob advertisements
 */
object AdManager {

    private const val TAG = "AdManager"

    // Test ad unit ID for banner ads (replace with real ID in production)
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    private var isInitialized = false

    /**
     * Initialize the Mobile Ads SDK
     */
    fun initialize(context: Context) {
        if (isInitialized) {
            Log.d(TAG, "AdMob already initialized")
            return
        }

        Log.d(TAG, "Initializing AdMob SDK...")

        MobileAds.initialize(context) { initializationStatus ->
            Log.d(TAG, "AdMob SDK initialized successfully")
            isInitialized = true

            // Log adapter status for debugging
            val statusMap = initializationStatus.adapterStatusMap
            for (adapterClass in statusMap.keys) {
                val status = statusMap[adapterClass]
                Log.d(TAG, "Adapter: $adapterClass, Status: ${status?.initializationState}, Description: ${status?.description}")
            }
        }
    }

    /**
     * Create and load a banner ad view
     */
    fun createBannerAd(context: Context, adContainer: LinearLayout): AdView? {
        Log.d(TAG, "Creating banner ad...")
        Log.d(TAG, "AdMob initialized: $isInitialized")

        // Show container first to avoid layout issues
        adContainer.visibility = View.VISIBLE

        val adView = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
        }

        // Set up ad listener
        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                Log.d(TAG, "Banner ad clicked")
            }

            override fun onAdClosed() {
                Log.d(TAG, "Banner ad closed")
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e(TAG, "Banner ad failed to load: ${adError.message}")
                Log.e(TAG, "Error code: ${adError.code}")
                // Keep container visible but show placeholder
                createAdPlaceholder(context, adContainer)
            }

            override fun onAdImpression() {
                Log.d(TAG, "Banner ad impression recorded")
            }

            override fun onAdLoaded() {
                Log.d(TAG, "Banner ad loaded successfully")
                // Ensure ad container is visible
                adContainer.visibility = View.VISIBLE
            }

            override fun onAdOpened() {
                Log.d(TAG, "Banner ad opened")
            }
        }

        // Add ad view to container
        adContainer.removeAllViews()
        adContainer.addView(adView)

        // Load the ad
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        Log.d(TAG, "Banner ad loading started...")

        return adView
    }

    /**
     * Create a placeholder when ad fails to load
     */
    private fun createAdPlaceholder(context: Context, adContainer: LinearLayout) {
        Log.d(TAG, "Creating ad placeholder")

        adContainer.removeAllViews()

        val placeholderView = android.widget.TextView(context).apply {
            text = "Реклама"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * context.resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
        }

        adContainer.addView(placeholderView)
        adContainer.visibility = View.VISIBLE
    }

    /**
     * Check if ads should be shown (based on user settings)
     */
    fun shouldShowAds(context: Context): Boolean {
        val settingsManager = SettingsManager(context)
        val adsRemoved = settingsManager.getAdsRemoved()
        Log.d(TAG, "Should show ads: ${!adsRemoved}")
        return !adsRemoved
    }

    /**
     * Hide ads and clean up ad views
     */
    fun hideAds(adContainer: LinearLayout) {
        Log.d(TAG, "Hiding ads")
        adContainer.visibility = View.GONE
        adContainer.removeAllViews()
    }

    /**
     * Pause ad view (call in activity onPause)
     */
    fun pauseAd(adView: AdView?) {
        adView?.pause()
    }

    /**
     * Resume ad view (call in activity onResume)
     */
    fun resumeAd(adView: AdView?) {
        adView?.resume()
    }

    /**
     * Destroy ad view (call in activity onDestroy)
     */
    fun destroyAd(adView: AdView?) {
        adView?.destroy()
    }
}
