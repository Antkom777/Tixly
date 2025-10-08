package com.tixly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.tixly.app.utils.PDFProcessor
import com.tixly.app.data.TicketsRepository
import java.io.File
import kotlin.system.exitProcess

class MainActivity : BaseActivity() {

    private lateinit var pdfProcessor: PDFProcessor

    // Launcher for selecting PDF file
    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPdfFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pdfProcessor = PDFProcessor(this)

        // Initialize repository with context
        TicketsRepository.initialize(this)

        // Process Intent if app is launched via Share or View
        if (handleIncomingIntent(intent)) {
            return // If processed PDF, don't show main screen
        }

        // Go directly to tickets list screen
        val intent = Intent(this, TicketsActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIncomingIntent(it) }
    }

    private fun setupButtons() {
        val buttonAddTicket = findViewById<Button>(R.id.buttonAddTicket)
        val buttonViewTickets = findViewById<Button>(R.id.buttonViewTickets)
        val buttonExit = findViewById<Button>(R.id.buttonExit)

        buttonAddTicket.setOnClickListener {
            // Open file manager for PDF selection
            selectPdfLauncher.launch("application/pdf")
        }

        buttonViewTickets.setOnClickListener {
            // Go to tickets list screen
            val intent = Intent(this, TicketsActivity::class.java)
            startActivity(intent)
        }

        buttonExit.setOnClickListener {
            // Close app
            finishAffinity()
            exitProcess(0)
        }
    }

    private fun handleIncomingIntent(intent: Intent): Boolean {
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
                        return true
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                if (intent.type == "application/pdf") {
                    intent.data?.let { uri ->
                        Toast.makeText(this, getString(R.string.pdf_opened_in_app), Toast.LENGTH_SHORT).show()
                        processPdfFile(uri)
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun processPdfFile(uri: Uri) {
        Toast.makeText(this, getString(R.string.processing_pdf), Toast.LENGTH_SHORT).show()

        try {
            val ticket = pdfProcessor.processPDF(uri)
            if (ticket != null) {
                // DO NOT add ticket to repository immediately - pass it to editor
                // Show message about successful PDF processing, not about saving ticket
                Toast.makeText(this, getString(R.string.pdf_processed_successfully), Toast.LENGTH_LONG).show()

                // Open editing screen with temporary ticket
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TEMP_TICKET_DATA", ticket.toJson()) // Pass as JSON
                startActivity(intent)
                // DO NOT call finish() - leave MainActivity in stack for proper back navigation
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
                        // Go to tickets list
                        val intent = Intent(this, TicketsActivity::class.java)
                        startActivity(intent)
                        finish()
                        return
                    }
                }

                Toast.makeText(this, getString(R.string.failed_to_process_pdf), Toast.LENGTH_LONG).show()
                // Go to tickets list
                val intent = Intent(this, TicketsActivity::class.java)
                startActivity(intent)
                finish()
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
}
