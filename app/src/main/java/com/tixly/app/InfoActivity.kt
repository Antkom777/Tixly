package com.tixly.app

import android.os.Bundle
import android.view.MenuItem
import com.tixly.app.utils.SettingsManager

class InfoActivity : BaseActivity() {

    private var currentLanguage: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_info)

        val settingsManager = SettingsManager(this)
        currentLanguage = settingsManager.getLanguage()

        // Configure action bar with back button
        supportActionBar?.apply {
            title = getString(R.string.info)
            setDisplayHomeAsUpEnabled(true)
        }
    }

    override fun onResume() {
        super.onResume()

        // Check if language has changed
        val settingsManager = SettingsManager(this)
        val newLanguage = settingsManager.getLanguage()
        if (newLanguage != currentLanguage) {
            // Language changed, recreate activity to apply new language
            recreate()
            return
        }

        // Update title in case language was changed
        supportActionBar?.title = getString(R.string.info)
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
