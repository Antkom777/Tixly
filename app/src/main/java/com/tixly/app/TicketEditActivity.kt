package com.tixly.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
import com.tixly.app.utils.NotificationScheduler
import com.tixly.app.utils.SettingsManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TicketEditActivity : BaseActivity() {

    private lateinit var editTitle: EditText
    private lateinit var editVenue: EditText
    private lateinit var editDate: EditText
    private lateinit var buttonSave: ImageButton
    private lateinit var buttonOpen: ImageButton
    private lateinit var buttonReplace: ImageButton
    private lateinit var buttonDelete: ImageButton

    private var ticketId: String? = null
    private var copyFromTicketId: String? = null
    private var currentTicket: Ticket? = null
    private var tempTicket: Ticket? = null
    private var selectedDate: Calendar = Calendar.getInstance()
    private var currentLanguage: String = ""

    // Launcher for selecting new PDF file for replacement
    private val selectReplacementPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { replacePdfFile(it) }
    }

    // Launcher for selecting PDF when copying
    private val selectCopyPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { validateAndSetCopyPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_edit)

        val settingsManager = com.tixly.app.utils.SettingsManager(this)
        currentLanguage = settingsManager.getLanguage()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupButtons()

        ticketId = intent.getStringExtra("TICKET_ID")
        copyFromTicketId = intent.getStringExtra("COPY_FROM_TICKET_ID")

        intent.getStringExtra("TEMP_TICKET_DATA")?.let { tempData ->
            tempTicket = Ticket.fromJson(tempData)
        }

        loadTicketData()
    }

    override fun onResume() {
        super.onResume()

        val settingsManager = com.tixly.app.utils.SettingsManager(this)
        val newLanguage = settingsManager.getLanguage()
        if (newLanguage != currentLanguage) {
            recreate()
            return
        }

        updateActivityTitle()
    }

    private fun updateActivityTitle() {
        copyFromTicketId?.let {
            supportActionBar?.title = getString(R.string.create_ticket_copy)
            return
        }

        ticketId?.let {
            supportActionBar?.title = getString(R.string.edit_ticket)
        } ?: run {
            supportActionBar?.title = getString(R.string.new_ticket)
        }
    }

    private fun initViews() {
        editTitle = findViewById(R.id.editTicketTitle)
        editVenue = findViewById(R.id.editTicketVenue)
        editDate = findViewById(R.id.editTicketDate)
        buttonSave = findViewById(R.id.buttonSaveTicket)
        buttonOpen = findViewById(R.id.buttonOpenTicket)
        buttonReplace = findViewById(R.id.buttonReplacePdf)
        buttonDelete = findViewById(R.id.buttonDeleteTicket)

        // Make date field non-editable directly - only through picker
        editDate.isFocusable = false
        editDate.isClickable = true
        editDate.setOnClickListener {
            showDateTimePicker()
        }
    }

    private fun setupButtons() {
        buttonSave.setOnClickListener {
            saveTicket()
        }

        buttonOpen.setOnClickListener {
            openTicketPdf()
        }

        buttonReplace.setOnClickListener {
            selectReplacementPdf()
        }

        buttonDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun loadTicketData() {
        // If this is ticket copying
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { ticket ->
                supportActionBar?.title = getString(R.string.create_ticket_copy)

                // Copy all fields except ID and PDF
                editTitle.setText(ticket.title)
                editVenue.setText(ticket.venue ?: "")

                ticket.eventDate?.let { date ->
                    selectedDate.time = date
                    updateDateField()
                }

                Toast.makeText(this, getString(R.string.select_pdf_for_copy), Toast.LENGTH_LONG).show()
                selectCopyPdfLauncher.launch("application/pdf")

                // PDF buttons unavailable until file is selected
                buttonOpen.isEnabled = false
                buttonOpen.alpha = 0.5f
                buttonReplace.isEnabled = false
                buttonReplace.alpha = 0.5f
            }
            return
        }

        // If this is editing existing ticket
        ticketId?.let { id ->
            currentTicket = TicketsRepository.getTicketById(id)
            currentTicket?.let { ticket ->
                supportActionBar?.title = getString(R.string.edit_ticket)

                editTitle.setText(ticket.title)
                editVenue.setText(ticket.venue ?: "")

                ticket.eventDate?.let { date ->
                    selectedDate.time = date
                    updateDateField()
                }

                // Setup PDF buttons
                val hasPdf = !ticket.pdfUri.isNullOrEmpty() || (!ticket.pdfFilePath.isNullOrEmpty() && File(ticket.pdfFilePath).exists())
                buttonOpen.isEnabled = hasPdf
                buttonOpen.alpha = if (hasPdf) 1.0f else 0.5f
                // Replace PDF button always active
                buttonReplace.isEnabled = true
                buttonReplace.alpha = 1.0f
            }
        } ?: run {
            // New ticket
            supportActionBar?.title = getString(R.string.new_ticket)
            buttonOpen.isEnabled = false
            buttonOpen.alpha = 0.5f
            // Replace PDF button active for adding PDF to new ticket
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        }

        // If this is temporary ticket with PDF
        tempTicket?.let { ticket ->
            supportActionBar?.title = getString(R.string.edit_ticket)

            editTitle.setText(ticket.title)
            editVenue.setText(ticket.venue ?: "")

            ticket.eventDate?.let { date ->
                selectedDate.time = date
                updateDateField()
            }

            val hasPdf = !ticket.pdfUri.isNullOrEmpty() || (!ticket.pdfFilePath.isNullOrEmpty() && File(ticket.pdfFilePath).exists())
            buttonOpen.isEnabled = hasPdf
            buttonOpen.alpha = if (hasPdf) 1.0f else 0.5f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        }
    }

    private fun saveTicket() {
        val title = editTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_ticket_title), Toast.LENGTH_SHORT).show()
            return
        }

        val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
        val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
            selectedDate.time
        } else {
            null
        }

        // Create the ticket to save with the correct data
        val ticketToSave = when {
            tempTicket != null -> {
                // Use tempTicket as base but update with form data
                tempTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            currentTicket != null -> {
                // Update existing ticket with form data only (no PDF changes)
                currentTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            else -> {
                // Create new ticket
                Ticket(
                    title = title,
                    description = getString(R.string.manually_created_ticket),
                    venue = venue,
                    eventDate = eventDate
                )
            }
        }

        android.util.Log.d("TicketEditActivity", "=== Saving ticket ===")
        android.util.Log.d("TicketEditActivity", "Ticket ID: ${ticketToSave.id}")
        android.util.Log.d("TicketEditActivity", "Title: ${ticketToSave.title}")
        android.util.Log.d("TicketEditActivity", "PDF path: ${ticketToSave.pdfFilePath}")
        android.util.Log.d("TicketEditActivity", "Has tempTicket: ${tempTicket != null}")
        android.util.Log.d("TicketEditActivity", "Has currentTicket: ${currentTicket != null}")

        val wasSuccessful = try {
            when {
                // New ticket (no currentTicket)
                currentTicket == null -> {
                    TicketsRepository.addTicket(ticketToSave)
                    Toast.makeText(this, getString(R.string.ticket_saved), Toast.LENGTH_SHORT).show()
                    true
                }
                // Updating existing ticket without PDF changes
                tempTicket == null -> {
                    TicketsRepository.updateTicket(ticketToSave)
                    Toast.makeText(this, getString(R.string.ticket_updated), Toast.LENGTH_SHORT).show()
                    true
                }
                // Updating existing ticket WITH PDF changes
                else -> {
                    val oldPdfPath = currentTicket!!.pdfFilePath

                    // Verify new PDF file exists before proceeding
                    if (ticketToSave.pdfFilePath?.let { File(it).exists() } != true) {
                        android.util.Log.e("TicketEditActivity", "New PDF file missing: ${ticketToSave.pdfFilePath}")
                        Toast.makeText(this, getString(R.string.pdf_save_error, "File missing"), Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Update ticket in repository
                    TicketsRepository.updateTicket(ticketToSave)

                    // Verify save was successful
                    val verifyTicket = TicketsRepository.getTicketById(ticketToSave.id)
                    if (verifyTicket?.pdfFilePath != ticketToSave.pdfFilePath) {
                        android.util.Log.e("TicketEditActivity", "Save verification failed!")
                        Toast.makeText(this, getString(R.string.save_error, "Verification failed"), Toast.LENGTH_SHORT).show()
                        return
                    }

                    android.util.Log.d("TicketEditActivity", "Save verified successfully: ${verifyTicket.pdfFilePath}")

                    // Clean up old PDF file only after successful verification
                    if (!oldPdfPath.isNullOrEmpty() && oldPdfPath != ticketToSave.pdfFilePath) {
                        try {
                            val oldFile = File(oldPdfPath)
                            if (oldFile.exists()) {
                                val deleted = oldFile.delete()
                                android.util.Log.d("TicketEditActivity", "Deleted old PDF: $oldPdfPath, success: $deleted")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("TicketEditActivity", "Failed to delete old PDF: $oldPdfPath", e)
                        }
                    }

                    Toast.makeText(this, getString(R.string.ticket_updated_with_pdf), Toast.LENGTH_SHORT).show()
                    true
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TicketEditActivity", "Error saving ticket", e)
            Toast.makeText(this, getString(R.string.save_error, e.message), Toast.LENGTH_SHORT).show()
            false
        }

        if (wasSuccessful) {
            // Use the new centralized notification manager
            android.util.Log.d("TicketEditActivity", "=== Notification recalculation ===")
            com.tixly.app.utils.NotificationManager.recalculateNotificationForTicket(this, ticketToSave)

            // Clear temporary data
            tempTicket = null
            finish()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_ticket))
            .setMessage(getString(R.string.delete_ticket_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                ticketId?.let { id ->
                    // Use the new centralized notification manager
                    com.tixly.app.utils.NotificationManager.onTicketDeleted(this, id)

                    TicketsRepository.removeTicket(id)
                    Toast.makeText(this, getString(R.string.ticket_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                } ?: run {
                    Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDateTimePicker() {
        // First show calendar picker
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                showTimePicker()
            },
            selectedDate.get(Calendar.YEAR),
            selectedDate.get(Calendar.MONTH),
            selectedDate.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun showTimePicker() {
        val timePickerDialog = TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedDate.set(Calendar.HOUR_OF_DAY, hourOfDay)
                selectedDate.set(Calendar.MINUTE, minute)
                updateDateField()
            },
            selectedDate.get(Calendar.HOUR_OF_DAY),
            selectedDate.get(Calendar.MINUTE),
            true // 24-hour format
        )
        timePickerDialog.show()
    }

    private fun updateDateField() {
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        editDate.setText(format.format(selectedDate.time))
    }

    private fun openTicketPdf() {
        val ticketToOpen = tempTicket ?: currentTicket

        android.util.Log.d("TicketEditActivity", "=== Opening PDF Debug Info ===")
        android.util.Log.d("TicketEditActivity", "tempTicket: ${tempTicket?.id}")
        android.util.Log.d("TicketEditActivity", "currentTicket: ${currentTicket?.id}")
        android.util.Log.d("TicketEditActivity", "ticketToOpen: ${ticketToOpen?.id}")
        android.util.Log.d("TicketEditActivity", "PDF file path: ${ticketToOpen?.pdfFilePath}")
        android.util.Log.d("TicketEditActivity", "PDF URI: ${ticketToOpen?.pdfUri}")

        ticketToOpen?.let { ticket ->
            // First, try to open saved copy from internal storage
            ticket.pdfFilePath?.let { filePath ->
                val file = File(filePath)
                android.util.Log.d("TicketEditActivity", "Checking file exists: ${file.exists()}")
                android.util.Log.d("TicketEditActivity", "File absolute path: ${file.absolutePath}")
                android.util.Log.d("TicketEditActivity", "File length: ${if (file.exists()) file.length() else "N/A"}")

                if (file.exists()) {
                    try {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "${packageName}.fileprovider",
                            file
                        )
                        android.util.Log.d("TicketEditActivity", "FileProvider URI: $uri")

                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "application/pdf")
                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        }

                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                            return
                        } else {
                            android.util.Log.e("TicketEditActivity", "No app can handle PDF intent")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("TicketEditActivity", "Error opening internal PDF file: $filePath", e)
                        // Fallback to original URI
                    }
                } else {
                    android.util.Log.w("TicketEditActivity", "PDF file does not exist at: $filePath")
                }
            }

            // Fallback: try original URI
            ticket.pdfUri?.let { uriString ->
                android.util.Log.d("TicketEditActivity", "Trying fallback URI: $uriString")
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
                    android.util.Log.e("TicketEditActivity", "Error opening PDF from URI: $uriString", e)
                    Toast.makeText(this, getString(R.string.pdf_open_error, e.message), Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                android.util.Log.w("TicketEditActivity", "No PDF URI available")
                Toast.makeText(this, getString(R.string.pdf_not_found), Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            android.util.Log.w("TicketEditActivity", "No ticket available")
            Toast.makeText(this, getString(R.string.pdf_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectReplacementPdf() {
        val ticketToModify = currentTicket ?: tempTicket

        if (ticketToModify != null) {
            // For existing tickets - check if there's a PDF file
            val hasPdf = !ticketToModify.pdfUri.isNullOrEmpty() ||
                        (!ticketToModify.pdfFilePath.isNullOrEmpty() && File(ticketToModify.pdfFilePath).exists())

            if (hasPdf) {
                // If PDF exists, show replacement confirmation dialog
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.replace_pdf_file))
                    .setMessage(getString(R.string.replace_pdf_confirmation))
                    .setPositiveButton(getString(R.string.replace)) { _, _ ->
                        selectReplacementPdfLauncher.launch("application/pdf")
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                // If no PDF, open selector directly
                selectReplacementPdfLauncher.launch("application/pdf")
            }
        } else {
            // For new tickets - open selector directly to add PDF
            selectReplacementPdfLauncher.launch("application/pdf")
        }
    }

    private fun replacePdfFile(uri: Uri) {
        // Get new PDF file name
        val newFileName = getFileNameFromUri(uri)

        if (newFileName != null) {
            // Check if new PDF file is not used in other tickets
            val allTickets = TicketsRepository.getAllTickets()
            val duplicateTicket = allTickets.find { otherTicket ->
                // Skip current ticket when checking (if it exists)
                if (currentTicket != null && otherTicket.id == currentTicket!!.id) return@find false

                // Check filename from pdfFilePath or pdfUri
                val existingFileName = otherTicket.pdfFilePath?.let { File(it).name }
                    ?: otherTicket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }

                existingFileName != null && existingFileName == newFileName
            }

            if (duplicateTicket != null) {
                Toast.makeText(
                    this,
                    getString(R.string.pdf_already_used, duplicateTicket.title),
                    Toast.LENGTH_LONG
                ).show()
                // Re-open selector
                selectReplacementPdfLauncher.launch("application/pdf")
                return
            }
        }

        try {
            // Save new PDF file
            val savedPdfPath = savePdfToInternalStorage(uri)

            if (currentTicket != null) {
                // For existing tickets - DON'T save to database immediately, create temporary
                val updatedTicket = currentTicket!!.copy(
                    pdfUri = uri.toString(),
                    pdfFilePath = savedPdfPath
                )
                tempTicket = updatedTicket
                Toast.makeText(this, getString(R.string.pdf_attached_ready_to_save), Toast.LENGTH_SHORT).show()
            } else {
                // For new tickets - DON'T save to database, only create temporary ticket
                val title = editTitle.text.toString().trim().takeIf { it.isNotEmpty() }
                    ?: getString(R.string.ticket_title_placeholder)
                val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
                val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
                    selectedDate.time
                } else {
                    null
                }

                val newTicket = Ticket(
                    title = title,
                    description = getString(R.string.manually_created_ticket),
                    venue = venue,
                    eventDate = eventDate,
                    pdfUri = uri.toString(),
                    pdfFilePath = savedPdfPath
                )

                tempTicket = newTicket
                supportActionBar?.title = getString(R.string.edit_ticket)
                Toast.makeText(this, getString(R.string.pdf_attached_ready_to_save), Toast.LENGTH_SHORT).show()
            }

            // Activate PDF buttons
            buttonOpen.isEnabled = true
            buttonOpen.alpha = 1.0f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f

        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.pdf_replacement_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndSetCopyPdf(uri: Uri) {
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { originalTicket ->

                val newFileName = getFileNameFromUri(uri)

                if (newFileName != null) {
                    // Check if new PDF file is not used in other tickets
                    val allTickets = TicketsRepository.getAllTickets()
                    val duplicateTicket = allTickets.find { ticket ->
                        // Check filename from pdfFilePath
                        val existingFileName = ticket.pdfFilePath?.let { File(it).name }
                            ?: ticket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }

                        existingFileName != null && existingFileName == newFileName
                    }

                    if (duplicateTicket != null) {
                        Toast.makeText(
                            this,
                            getString(R.string.pdf_already_used, duplicateTicket.title),
                            Toast.LENGTH_LONG
                        ).show()
                        // Re-open selector
                        selectCopyPdfLauncher.launch("application/pdf")
                        return
                    }
                }

                try {
                    // Save new PDF file
                    val savedPdfPath = savePdfToInternalStorage(uri)

                    // Create new ticket with copied data, but DON'T save to database
                    val title = editTitle.text.toString().trim()
                    val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
                    val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
                        selectedDate.time
                    } else {
                        null
                    }

                    val newTicket = Ticket(
                        title = title,
                        description = originalTicket.description,
                        venue = venue,
                        eventDate = eventDate,
                        price = originalTicket.price,
                        seatInfo = originalTicket.seatInfo,
                        qrCode = originalTicket.qrCode,
                        barcode = originalTicket.barcode,
                        pdfUri = uri.toString(),
                        pdfFilePath = savedPdfPath
                    )

                    // Activate PDF buttons
                    buttonOpen.isEnabled = true
                    buttonOpen.alpha = 1.0f
                    buttonReplace.isEnabled = true
                    buttonReplace.alpha = 1.0f

                    // Save ticket as temporary for PDF viewing, but not in database
                    tempTicket = newTicket
                    copyFromTicketId = null // Clear copyFromTicketId

                    // Update title
                    supportActionBar?.title = getString(R.string.edit_ticket)
                    Toast.makeText(this, getString(R.string.pdf_attached_ready_to_save), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.pdf_save_error, e.message), Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun savePdfToInternalStorage(uri: Uri): String? {
        return try {
            android.util.Log.d("TicketEditActivity", "=== Saving PDF to internal storage ===")
            android.util.Log.d("TicketEditActivity", "Source URI: $uri")

            // Create folder for PDF files in internal storage
            val pdfDir = File(filesDir, "pdf_tickets")
            if (!pdfDir.exists()) {
                val created = pdfDir.mkdirs()
                android.util.Log.d("TicketEditActivity", "Created PDF directory: $created")
            }

            // Generate unique filename
            val fileName = "ticket_${UUID.randomUUID()}.pdf"
            val destinationFile = File(pdfDir, fileName)
            android.util.Log.d("TicketEditActivity", "Destination file: ${destinationFile.absolutePath}")

            // Copy file
            contentResolver.openInputStream(uri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    val bytesCopied = inputStream.copyTo(outputStream)
                    android.util.Log.d("TicketEditActivity", "Copied $bytesCopied bytes")
                }
            }

            val finalPath = destinationFile.absolutePath
            android.util.Log.d("TicketEditActivity", "File saved successfully: $finalPath")
            android.util.Log.d("TicketEditActivity", "File exists: ${destinationFile.exists()}")
            android.util.Log.d("TicketEditActivity", "File size: ${destinationFile.length()}")

            finalPath
        } catch (e: Exception) {
            android.util.Log.e("TicketEditActivity", "Error saving PDF to internal storage", e)
            e.printStackTrace()
            null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        checkUnsavedChangesBeforeExit()
        return false // Return false so Android doesn't handle navigation automatically
    }

    override fun onBackPressed() {
        checkUnsavedChangesBeforeExit()
    }

    private fun checkUnsavedChangesBeforeExit() {
        val hasChanges = hasUnsavedChanges()

        if (hasChanges) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.unsaved_changes_message))
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    saveTicket()
                }
                .setNegativeButton(getString(R.string.discard)) { _, _ ->
                    cleanupTempTicket()
                    finish()
                }
                .setNeutralButton(getString(R.string.cancel), null)
                .show()
        } else {
            cleanupTempTicket()
            finish()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = editTitle.text.toString().trim()
        val currentVenue = editVenue.text.toString().trim()
        val currentDateText = editDate.text.toString().trim()

        return when {
            // For ticket copying - always show dialog since ticket is not saved yet
            copyFromTicketId != null -> true
            // For temporary ticket with PDF - always show dialog since ticket is not saved yet
            tempTicket != null -> true
            // For new ticket - has changes if fields are filled
            currentTicket == null -> {
                currentTitle.isNotEmpty() || currentVenue.isNotEmpty() || currentDateText.isNotEmpty()
            }
            // For existing ticket - compare with original values
            else -> {
                val originalTitle = currentTicket?.title ?: ""
                val originalVenue = currentTicket?.venue ?: ""
                val originalDate = currentTicket?.eventDate?.let {
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it)
                } ?: ""

                currentTitle != originalTitle ||
                currentVenue != originalVenue ||
                currentDateText != originalDate
            }
        }
    }

    private fun cleanupTempTicket() {
        // Delete temporary PDF file if it exists
        tempTicket?.pdfFilePath?.let { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    // Check if this is not the original file of existing ticket
                    val isOriginalFile = currentTicket?.pdfFilePath == filePath
                    if (!isOriginalFile) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        tempTicket = null
    }
}
