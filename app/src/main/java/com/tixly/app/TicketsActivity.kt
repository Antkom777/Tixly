package com.tixly.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
import com.tixly.app.utils.NotificationScheduler
import com.tixly.app.utils.PDFProcessor
import com.tixly.app.utils.ImageProcessor
import com.tixly.app.utils.SettingsManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.system.exitProcess

class TicketsActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var ticketsAdapter: TicketsAdapter
    private lateinit var pdfProcessor: PDFProcessor
    private lateinit var imageProcessor: ImageProcessor
    private lateinit var settingsManager: SettingsManager
    private lateinit var adBannerLayout: LinearLayout
    private var currentAdView: com.google.android.gms.ads.AdView? = null
    private var currentLanguage: String = ""

    // Launcher for PDF file selection
    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPdfFile(it) }
    }

    // Launcher for image file selection
    private val selectImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processImageFile(it) }
    }

    // Launcher for settings activity
    private val settingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Language was changed, recreate activity
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        // Initialize repository with context
        TicketsRepository.initialize(this)

        pdfProcessor = PDFProcessor(this)
        imageProcessor = ImageProcessor(this)
        settingsManager = SettingsManager(this)

        // Remember current language
        currentLanguage = settingsManager.getLanguage()

        // Configure action bar
        supportActionBar?.title = getString(R.string.my_tickets)

        setupAdBanner()
        setupRecyclerView()
        setupFab()
        loadTickets()

        // Initialize notifications if enabled
        initializeNotifications()

        // Handle Intent if app is launched via Share or View
        handleIncomingIntent(intent)
    }

    override fun onPause() {
        // Pause ad view
        com.tixly.app.utils.AdManager.pauseAd(currentAdView)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        // Resume ad view
        com.tixly.app.utils.AdManager.resumeAd(currentAdView)

        // Check if language has changed
        val newLanguage = settingsManager.getLanguage()
        if (newLanguage != currentLanguage) {
            // Language changed, recreate activity
            recreate()
            return
        }

        // Update activity title when returning (e.g., after language change)
        supportActionBar?.title = getString(R.string.my_tickets)
        // Refresh ticket list when returning to activity
        loadTickets()
        // Update ad visibility when returning from settings
        updateAdBannerVisibility()
        // Update tickets list to display new language
        ticketsAdapter.notifyDataSetChanged()
    }

    override fun onDestroy() {
        // Destroy ad view
        com.tixly.app.utils.AdManager.destroyAd(currentAdView)
        super.onDestroy()
    }

    private fun setupAdBanner() {
        adBannerLayout = findViewById(R.id.layoutAdBanner)

        // Check if ads should be shown based on user settings
        if (com.tixly.app.utils.AdManager.shouldShowAds(this)) {
            // Always show the ad container
            adBannerLayout.visibility = android.view.View.VISIBLE

            // Try to create and load Google AdMob banner ad
            currentAdView = com.tixly.app.utils.AdManager.createBannerAd(this, adBannerLayout)

            // If ad creation failed, show placeholder
            if (currentAdView == null) {
                createAdPlaceholder()
            }
        } else {
            // Hide ads if user has removed them
            com.tixly.app.utils.AdManager.hideAds(adBannerLayout)
        }
    }

    private fun createAdPlaceholder() {
        // Create a simple placeholder view
        val placeholderView = android.widget.TextView(this).apply {
            text = "Рекламне місце"
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * resources.displayMetrics.density).toInt()
            )
            setBackgroundColor(android.graphics.Color.parseColor("#F0F0F0"))
        }

        adBannerLayout.removeAllViews()
        adBannerLayout.addView(placeholderView)
        adBannerLayout.visibility = android.view.View.VISIBLE
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                }

                uri?.let {
                    when {
                        intent.type == "application/pdf" -> {
                            Toast.makeText(this, getString(R.string.pdf_received_via_share), Toast.LENGTH_SHORT).show()
                            processPdfFile(it)
                        }
                        intent.type?.startsWith("image/") == true -> {
                            Toast.makeText(this, getString(R.string.image_received_via_share), Toast.LENGTH_SHORT).show()
                            processImageFile(it)
                        }
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    when {
                        intent.type == "application/pdf" -> {
                            Toast.makeText(this, getString(R.string.pdf_opened_in_app), Toast.LENGTH_SHORT).show()
                            processPdfFile(uri)
                        }
                        intent.type?.startsWith("image/") == true -> {
                            Toast.makeText(this, getString(R.string.image_opened_in_app), Toast.LENGTH_SHORT).show()
                            processImageFile(uri)
                        }
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.tickets_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_info -> {
                val intent = Intent(this, InfoActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                settingsLauncher.launch(intent)
                true
            }
            R.id.action_exit -> {
                finishAffinity()
                exitProcess(0)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
        fab.setOnClickListener {
            // Directly create new ticket manually without dialog
            createManualTicket()
        }
    }

    private fun createManualTicket() {
        // Navigate to new ticket editing screen
        val intent = Intent(this, TicketEditActivity::class.java)
        startActivity(intent)
    }

    private fun updateAdBannerVisibility() {
        // Check if ads should be shown and update accordingly
        if (com.tixly.app.utils.AdManager.shouldShowAds(this)) {
            // Show ads - create new ad if needed
            if (currentAdView == null) {
                currentAdView = com.tixly.app.utils.AdManager.createBannerAd(this, adBannerLayout)
            }

            // Restore FAB margin when ad is shown
            val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
            val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = 80 * resources.displayMetrics.density.toInt()
            fab.layoutParams = params
        } else {
            // Hide ads
            com.tixly.app.utils.AdManager.hideAds(adBannerLayout)
            currentAdView = null

            // Change FAB margin when ad is hidden
            val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
            val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
            fab.layoutParams = params
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewTickets)
        recyclerView.layoutManager = LinearLayoutManager(this)

        ticketsAdapter = TicketsAdapter(
            onItemClick = { ticket ->
                // Navigate to ticket detail/edit screen
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            },
            onOpenClick = { ticket ->
                // Open attachment file (PDF or image)
                openTicketAttachment(ticket)
            },
            onCopyClick = { ticket ->
                // Copy ticket with new PDF
                copyTicket(ticket)
            }
        )

        recyclerView.adapter = ticketsAdapter
    }

    private fun copyTicket(originalTicket: Ticket) {
        // Proceed to ticket copy creation
        val intent = Intent(this, TicketEditActivity::class.java)
        intent.putExtra("COPY_FROM_TICKET_ID", originalTicket.id)
        startActivity(intent)
    }

    private fun openTicketAttachment(ticket: Ticket) {
        android.util.Log.d("TicketsActivity", "=== Opening ticket attachment ===")
        android.util.Log.d("TicketsActivity", "Ticket: ${ticket.id}, title: ${ticket.title}")
        android.util.Log.d("TicketsActivity", "File type: ${ticket.fileType}")

        try {
            when (ticket.fileType) {
                Ticket.FileType.PDF -> openPdfAttachment(ticket)
                Ticket.FileType.IMAGE -> openImageAttachment(ticket)
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketsActivity", "Error opening attachment", e)
            Toast.makeText(this, getString(R.string.attachment_open_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdfAttachment(ticket: Ticket) {
        // First, try to open the saved copy from internal storage
        ticket.pdfFilePath?.let { filePath ->
            val file = File(filePath)
            if (file.exists()) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/pdf")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TicketsActivity", "Error opening internal PDF file", e)
                }
            }
        }

        // Fallback: try original URI
        ticket.pdfUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.no_app_to_open_pdf), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.pdf_open_error, e.message), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.pdf_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openImageAttachment(ticket: Ticket) {
        // First, try to open the saved copy from internal storage
        ticket.imageFilePath?.let { filePath ->
            val file = File(filePath)
            if (file.exists()) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        file
                    )

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "image/*")
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TicketsActivity", "Error opening internal image file", e)
                }
            }
        }

        // Fallback: try original URI
        ticket.imageUri?.let { uriString ->
            try {
                val uri = Uri.parse(uriString)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.no_app_to_open_image), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, getString(R.string.image_open_error, e.message), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, getString(R.string.image_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun processPdfFile(uri: Uri) {
        Toast.makeText(this, getString(R.string.processing_pdf), Toast.LENGTH_SHORT).show()

        try {
            val ticket = pdfProcessor.processPDF(uri)
            if (ticket != null) {
                // DO NOT save the ticket immediately - pass as temporary
                Toast.makeText(this, getString(R.string.pdf_processed_successfully), Toast.LENGTH_LONG).show()

                // Open editing screen with temporary ticket
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TEMP_TICKET_DATA", ticket.toJson()) // Pass as temporary
                startActivity(intent)
            } else {
                // Check if this is due to PDF duplication
                val fileName = getFileNameFromUri(uri)
                if (fileName != null) {
                    val allTickets = TicketsRepository.getAllTickets()
                    val duplicateTicket = allTickets.find { existingTicket ->
                        val existingFileName = existingTicket.pdfFilePath?.let { File(it).name }
                            ?: existingTicket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }
                        existingFileName != null && existingFileName == fileName
                    }

                    if (duplicateTicket != null) {
                        Toast.makeText(
                            this,
                            getString(R.string.pdf_already_used, duplicateTicket.title),
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                }

                Toast.makeText(this, getString(R.string.failed_to_process_pdf), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.pdf_processing_error, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun processImageFile(uri: Uri) {
        Toast.makeText(this, getString(R.string.processing_image), Toast.LENGTH_SHORT).show()

        try {
            val ticket = imageProcessor.processImage(uri)
            if (ticket != null) {
                // Image processed and ticket created
                Toast.makeText(this, getString(R.string.image_processed_successfully), Toast.LENGTH_LONG).show()

                // Open editing screen with new ticket
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            } else {
                Toast.makeText(this, getString(R.string.failed_to_process_image), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.image_processing_error, e.message), Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex("_display_name")
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else {
                    uri.lastPathSegment
                }
            }
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun loadTickets() {
        val allTickets = TicketsRepository.getAllTickets()

        android.util.Log.d("TicketsActivity", "=== Loading tickets, total count: ${allTickets.size} ===")
        allTickets.forEachIndexed { index, ticket ->
            android.util.Log.d("TicketsActivity", "Ticket $index: id=${ticket.id}, title=${ticket.title}")
            android.util.Log.d("TicketsActivity", "  pdfFilePath=${ticket.pdfFilePath}")
            android.util.Log.d("TicketsActivity", "  pdfUri=${ticket.pdfUri}")
            ticket.pdfFilePath?.let { path ->
                val file = File(path)
                android.util.Log.d("TicketsActivity", "  File exists: ${file.exists()}, size: ${if (file.exists()) file.length() else "N/A"}")
            }
        }

        // Extended ticket sorting according to new requirements
        val sortedTickets = allTickets.sortedWith { ticket1, ticket2 ->
            val date1 = ticket1.eventDate
            val date2 = ticket2.eventDate
            val now = Date()

            when {
                // 1. Tickets without date always on top
                date1 == null && date2 != null -> -1
                date1 != null && date2 == null -> 1
                date1 == null && date2 == null -> 0

                // 2. Both have dates - detailed sorting
                date1 != null && date2 != null -> {
                    val isUpcoming1 = date1.after(now)
                    val isUpcoming2 = date2.after(now)

                    when {
                        // Upcoming tickets (future): closest date first
                        isUpcoming1 && isUpcoming2 -> date1.compareTo(date2)

                        // One upcoming, one expired: upcoming first
                        isUpcoming1 && !isUpcoming2 -> -1
                        !isUpcoming1 && isUpcoming2 -> 1

                        // Both expired: older ones at bottom (reverse order)
                        !isUpcoming1 && !isUpcoming2 -> date2.compareTo(date1)

                        else -> 0
                    }
                }

                else -> 0
            }
        }

        ticketsAdapter.updateTickets(sortedTickets)

        if (sortedTickets.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_saved_tickets), Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeNotifications() {
        // Initialize notifications for all upcoming tickets if enabled
        if (settingsManager.getNotificationsEnabled()) {
            val notificationTime = settingsManager.getNotificationTime()
            val upcomingTickets = TicketsRepository.getUpcomingTickets()

            android.util.Log.d("TicketsActivity", "Initializing notifications for ${upcomingTickets.size} upcoming tickets")

            NotificationScheduler.rescheduleAllNotifications(this, upcomingTickets, notificationTime)
        }
    }
}
