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
    private lateinit var settingsManager: SettingsManager
    private lateinit var adBannerLayout: LinearLayout
    private var currentLanguage: String = ""

    // Launcher for PDF file selection
    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPdfFile(it) }
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

    override fun onResume() {
        super.onResume()

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

    private fun setupAdBanner() {
        adBannerLayout = findViewById(R.id.layoutAdBanner)

        // Show/hide ads based on settings
        if (settingsManager.getAdsRemoved()) {
            adBannerLayout.visibility = android.view.View.GONE
        } else {
            adBannerLayout.visibility = android.view.View.VISIBLE
            // Google Ads integration will be here
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type == "application/pdf") {
                    val pdfUri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    }
                    pdfUri?.let { uri ->
                        Toast.makeText(this, getString(R.string.pdf_received_via_share), Toast.LENGTH_SHORT).show()
                        processPdfFile(uri)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                if (intent.type == "application/pdf") {
                    intent.data?.let { uri ->
                        Toast.makeText(this, getString(R.string.pdf_opened_in_app), Toast.LENGTH_SHORT).show()
                        processPdfFile(uri)
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
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                settingsLauncher.launch(intent)
                true
            }
            R.id.action_exit -> {
                finishAffinity()
                exitProcess(0)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
        fab.setOnClickListener {
            // Show dialog to choose ticket addition method
            showAddTicketDialog()
        }
    }

    private fun showAddTicketDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_ticket_dialog_title))
            .setItems(arrayOf(
                getString(R.string.add_ticket_from_pdf),
                getString(R.string.add_ticket_manually)
            )) { _, which ->
                when (which) {
                    0 -> {
                        // Add ticket from PDF file
                        selectPdfLauncher.launch("application/pdf")
                    }
                    1 -> {
                        // Create ticket manually
                        createManualTicket()
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun createManualTicket() {
        // Navigate to new ticket editing screen
        val intent = Intent(this, TicketEditActivity::class.java)
        startActivity(intent)
    }

    private fun updateAdBannerVisibility() {
        if (settingsManager.getAdsRemoved()) {
            adBannerLayout.visibility = android.view.View.GONE
            // Change FAB margin when ad is hidden
            val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
            val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
            fab.layoutParams = params
        } else {
            adBannerLayout.visibility = android.view.View.VISIBLE
            // Restore FAB margin when ad is shown
            val fab = findViewById<FloatingActionButton>(R.id.fabAddTicket)
            val params = fab.layoutParams as androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams
            params.bottomMargin = 80 * resources.displayMetrics.density.toInt()
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
                // Open PDF file (if available)
                openTicketPdf(ticket)
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

    private fun openTicketPdf(ticket: Ticket) {
        android.util.Log.d("TicketsActivity", "=== TicketsActivity Opening PDF Debug Info ===")
        android.util.Log.d("TicketsActivity", "Opening PDF for ticket: ${ticket.id}")
        android.util.Log.d("TicketsActivity", "Ticket title: ${ticket.title}")
        android.util.Log.d("TicketsActivity", "PDF file path: ${ticket.pdfFilePath}")
        android.util.Log.d("TicketsActivity", "PDF URI: ${ticket.pdfUri}")

        try {
            // First, try to open the saved copy from internal storage
            ticket.pdfFilePath?.let { filePath ->
                val file = File(filePath)
                android.util.Log.d("TicketsActivity", "Checking file exists: ${file.exists()}")
                android.util.Log.d("TicketsActivity", "File absolute path: ${file.absolutePath}")
                android.util.Log.d("TicketsActivity", "File length: ${if (file.exists()) file.length() else "N/A"}")

                if (file.exists()) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        android.util.Log.d("TicketsActivity", "FileProvider URI: $uri")

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }

                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            return
                        } else {
                            android.util.Log.e("TicketsActivity", "No app can handle PDF intent")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TicketsActivity", "Error opening internal PDF file: $filePath", e)
                        // Fallback to original URI
                    }
                } else {
                    android.util.Log.w("TicketsActivity", "PDF file does not exist at: $filePath")
                }
            }

            // Fallback: try original URI
            ticket.pdfUri?.let { uriString ->
                android.util.Log.d("TicketsActivity", "Trying fallback URI: $uriString")
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
                    android.util.Log.e("TicketsActivity", "Error opening PDF from URI: $uriString", e)
                    Toast.makeText(this, getString(R.string.pdf_open_error, e.message), Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                android.util.Log.w("TicketsActivity", "No PDF URI available")
                Toast.makeText(this, getString(R.string.pdf_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketsActivity", "General error opening PDF", e)
            Toast.makeText(this, getString(R.string.pdf_open_error, e.message), Toast.LENGTH_SHORT).show()
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
                    val duplicateTicket = allTickets.find { ticket ->
                        val existingFileName = ticket.pdfFilePath?.let { File(it).name }
                            ?: ticket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }
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
