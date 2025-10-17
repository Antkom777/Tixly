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

    // MIME types for file picker
    private val supportedMimeTypes = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "image/jpg",
        "image/gif",
        "image/webp"
    )

    // Activity result launcher for selecting a replacement file (PDF or image)
    private val selectReplacementFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                processSelectedFile(uri, mimeType)
            }
        }
    }

    // Activity result launcher for selecting a file for a new copied ticket
    private val selectCopyFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val mimeType = contentResolver.getType(uri)
                validateAndSetCopyFile(uri, mimeType)
            }
        } else {
            // User cancelled the file selection during a copy operation
            android.util.Log.d("TicketEditActivity", "Copy file selection cancelled - clearing copy mode")
            copyFromTicketId = null // Clear copy mode
            updateButtonStates() // Update button states to normal mode
            updateActivityTitle() // Update title
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_edit)

        val settingsManager = SettingsManager(this)
        currentLanguage = settingsManager.getLanguage()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupButtons()

        // Set up back press handling
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                checkUnsavedChangesBeforeExit()
            }
        })

        ticketId = intent.getStringExtra("TICKET_ID")
        copyFromTicketId = intent.getStringExtra("COPY_FROM_TICKET_ID")

        intent.getStringExtra("TEMP_TICKET_DATA")?.let { tempData ->
            tempTicket = Ticket.fromJson(tempData)
        }

        loadTicketData()
    }

    override fun onResume() {
        super.onResume()

        val settingsManager = SettingsManager(this)
        val newLanguage = settingsManager.getLanguage()
        if (newLanguage != currentLanguage) {
            recreate()
            return
        }

        updateActivityTitle()

        // Update button states when returning to the activity (e.g., after cancelling file selection)
        updateButtonStates()
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

        // Make the date field non-editable directly, only through the picker
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
        // If this is a ticket copy operation
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { ticket ->
                supportActionBar?.title = getString(R.string.create_ticket_copy)

                // Copy all fields except ID and attachment
                editTitle.setText(ticket.title)
                editVenue.setText(ticket.venue ?: "")

                ticket.eventDate?.let { date ->
                    selectedDate.time = date
                    updateDateField()
                }

                Toast.makeText(this, getString(R.string.select_pdf_for_copy), Toast.LENGTH_LONG).show()

                // Use ACTION_OPEN_DOCUMENT for persistent permissions
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                selectCopyFileLauncher.launch(intent)

                // Attachment buttons are unavailable until a file is selected
                buttonOpen.isEnabled = false
                buttonOpen.alpha = 0.5f
                buttonReplace.isEnabled = false
                buttonReplace.alpha = 0.5f
            }
            return
        }

        // If this is editing an existing ticket
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

                // Set up attachment buttons
                val hasAttachment = ticket.hasAttachment()
                buttonOpen.isEnabled = hasAttachment
                buttonOpen.alpha = if (hasAttachment) 1.0f else 0.5f
                // The replace attachment button is always active
                buttonReplace.isEnabled = true
                buttonReplace.alpha = 1.0f
            }
        } ?: run {
            // New ticket
            supportActionBar?.title = getString(R.string.new_ticket)
            buttonOpen.isEnabled = false
            buttonOpen.alpha = 0.5f
            // The replace attachment button is active for adding an attachment to a new ticket
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        }

        // If this is a temporary ticket with an attachment
        tempTicket?.let { ticket ->
            supportActionBar?.title = getString(R.string.edit_ticket)

            editTitle.setText(ticket.title)
            editVenue.setText(ticket.venue ?: "")

            ticket.eventDate?.let { date ->
                selectedDate.time = date
                updateDateField()
            }

            val hasAttachment = ticket.hasAttachment()
            buttonOpen.isEnabled = hasAttachment
            buttonOpen.alpha = if (hasAttachment) 1.0f else 0.5f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        }

        updateButtonStates()
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
                // Use tempTicket as a base but update with form data
                tempTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            currentTicket != null -> {
                // Update existing ticket with form data only (no attachment changes)
                currentTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            else -> {
                // Create a new ticket
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
                // Updating an existing ticket without attachment changes
                tempTicket == null -> {
                    TicketsRepository.updateTicket(ticketToSave)
                    Toast.makeText(this, getString(R.string.ticket_updated), Toast.LENGTH_SHORT).show()
                    true
                }
                // Updating an existing ticket WITH attachment changes
                else -> {
                    val oldPdfPath = currentTicket!!.pdfFilePath
                    val oldImagePath = currentTicket!!.imageFilePath

                    // Verify that the new attachment file exists before proceeding
                    val newAttachmentExists = when {
                        ticketToSave.pdfFilePath != null -> File(ticketToSave.pdfFilePath).exists()
                        ticketToSave.imageFilePath != null -> File(ticketToSave.imageFilePath).exists()
                        else -> true // No attachment is also a valid state
                    }

                    if (!newAttachmentExists) {
                        android.util.Log.e("TicketEditActivity", "New attachment file missing: PDF=${ticketToSave.pdfFilePath}, Image=${ticketToSave.imageFilePath}")
                        Toast.makeText(this, getString(R.string.file_save_error, "File missing"), Toast.LENGTH_SHORT).show()
                        return
                    }

                    // Update the ticket in the repository
                    TicketsRepository.updateTicket(ticketToSave)

                    // Verify that the save was successful
                    val verifyTicket = TicketsRepository.getTicketById(ticketToSave.id)
                    val saveSuccessful = when {
                        ticketToSave.pdfFilePath != null -> verifyTicket?.pdfFilePath == ticketToSave.pdfFilePath
                        ticketToSave.imageFilePath != null -> verifyTicket?.imageFilePath == ticketToSave.imageFilePath
                        else -> true
                    }

                    if (!saveSuccessful) {
                        android.util.Log.e("TicketEditActivity", "Save verification failed!")
                        Toast.makeText(this, getString(R.string.save_error, "Verification failed"), Toast.LENGTH_SHORT).show()
                        return
                    }

                    android.util.Log.d("TicketEditActivity", "Save verified successfully")

                    // Clean up old files only after successful verification
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

                    if (!oldImagePath.isNullOrEmpty() && oldImagePath != ticketToSave.imageFilePath) {
                        try {
                            val oldFile = File(oldImagePath)
                            if (oldFile.exists()) {
                                val deleted = oldFile.delete()
                                android.util.Log.d("TicketEditActivity", "Deleted old image: $oldImagePath, success: $deleted")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("TicketEditActivity", "Failed to delete old image: $oldImagePath", e)
                        }
                    }

                    Toast.makeText(this, getString(R.string.ticket_updated_with_attachment), Toast.LENGTH_SHORT).show()
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
        // First, show the calendar picker
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

        android.util.Log.d("TicketEditActivity", "=== Opening attachment ===")
        android.util.Log.d("TicketEditActivity", "tempTicket: ${tempTicket?.id}")
        android.util.Log.d("TicketEditActivity", "currentTicket: ${currentTicket?.id}")
        android.util.Log.d("TicketEditActivity", "ticketToOpen: ${ticketToOpen?.id}")
        android.util.Log.d("TicketEditActivity", "File type: ${ticketToOpen?.fileType}")

        ticketToOpen?.let { ticket ->
            when (ticket.fileType) {
                Ticket.FileType.PDF -> openPdfAttachment(ticket)
                Ticket.FileType.IMAGE -> openImageAttachment(ticket)
            }
        } ?: run {
            android.util.Log.w("TicketEditActivity", "No ticket available")
            Toast.makeText(this, getString(R.string.no_ticket_available), Toast.LENGTH_SHORT).show()
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
                    android.util.Log.e("TicketEditActivity", "Error opening internal PDF file", e)
                }
            }
        }

        // Fallback: try the original URI
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
                    android.util.Log.e("TicketEditActivity", "Error opening internal image file", e)
                }
            }
        }

        // Fallback: try the original URI
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

    private fun selectReplacementPdf() {
        val ticketToModify = currentTicket ?: tempTicket

        val openPicker = {
            android.util.Log.d("TicketEditActivity", "Launching system file picker with ACTION_OPEN_DOCUMENT")
            // Use ACTION_OPEN_DOCUMENT for persistent permissions
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            selectReplacementFileLauncher.launch(intent)
        }

        if (ticketToModify != null && ticketToModify.hasAttachment()) {
            // If an attachment exists, show a replacement confirmation dialog
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.replace_attachment))
                .setMessage(getString(R.string.replace_attachment_confirmation))
                .setPositiveButton(getString(R.string.replace)) { _, _ ->
                    openPicker()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } else {
            // If no attachment exists or it's a new ticket, open the picker directly
            openPicker()
        }
    }

    private fun processSelectedFile(uri: Uri, mimeType: String?) {
        // Get the new file name and determine the file type
        val newFileName = getFileNameFromUri(uri)

        android.util.Log.d("TicketEditActivity", "Processing file: $newFileName, MIME type: $mimeType, URI: $uri")

        if (newFileName != null) {
            // Check if the new file is not already used in other tickets
            val allTickets = TicketsRepository.getAllTickets()
            val duplicateTicket = allTickets.find { otherTicket ->
                // Skip the current ticket when checking (if it exists)
                if (currentTicket != null && otherTicket.id == currentTicket!!.id) return@find false

                // Check the filename from pdfFilePath, pdfUri, imageFilePath, or imageUri
                val existingFileName = otherTicket.pdfFilePath?.let { File(it).name }
                    ?: otherTicket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }
                    ?: otherTicket.imageFilePath?.let { File(it).name }
                    ?: otherTicket.imageUri?.let { getFileNameFromUri(Uri.parse(it)) }

                existingFileName != null && existingFileName == newFileName
            }

            if (duplicateTicket != null) {
                Toast.makeText(
                    this,
                    getString(R.string.file_already_used, duplicateTicket.title),
                    Toast.LENGTH_LONG
                ).show()
                // Re-open the system file picker
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                selectReplacementFileLauncher.launch(intent)
                return
            }
        }

        try {
            // Determine the file type using the MIME type and extension
            val isPdf = mimeType == "application/pdf" ||
                       newFileName?.endsWith(".pdf", ignoreCase = true) == true
            val isImage = mimeType?.startsWith("image/") == true ||
                         newFileName?.let { name ->
                             name.endsWith(".jpg", ignoreCase = true) ||
                             name.endsWith(".jpeg", ignoreCase = true) ||
                             name.endsWith(".png", ignoreCase = true) ||
                             name.endsWith(".gif", ignoreCase = true) ||
                             name.endsWith(".webp", ignoreCase = true)
                         } == true

            android.util.Log.d("TicketEditActivity", "File type determined: isPdf=$isPdf, isImage=$isImage, mimeType=$mimeType, fileName=$newFileName")

            if (!isPdf && !isImage) {
                Toast.makeText(this, getString(R.string.unsupported_file_type), Toast.LENGTH_LONG).show()
                // Re-open the system file picker
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                selectReplacementFileLauncher.launch(intent)
                return
            }

            // Copy the file to internal storage
            val savedFilePath = if (isPdf) {
                savePdfToInternalStorage(uri)
            } else {
                saveImageToInternalStorage(uri)
            }

            if (savedFilePath == null) {
                Toast.makeText(this, getString(R.string.file_save_error, "Could not save file"), Toast.LENGTH_SHORT).show()
                return
            }

            android.util.Log.d("TicketEditActivity", "Final file path: $savedFilePath")

            // Get form data for ticket creation
            val formTitle = editTitle.text.toString().trim()
            val formVenue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
            val formEventDate = if (editDate.text.toString().trim().isNotEmpty()) {
                selectedDate.time
            } else {
                null
            }

            if (currentTicket != null) {
                // For existing tickets, create a temporary ticket with the correct file type
                val updatedTicket = if (isPdf) {
                    currentTicket!!.copy(
                        pdfUri = uri.toString(),
                        pdfFilePath = savedFilePath,
                        fileType = Ticket.FileType.PDF,
                        // Clear image fields when setting a PDF
                        imageUri = null,
                        imageFilePath = null
                    )
                } else {
                    currentTicket!!.copy(
                        imageUri = uri.toString(),
                        imageFilePath = savedFilePath,
                        fileType = Ticket.FileType.IMAGE,
                        // Clear PDF fields when setting an image
                        pdfUri = null,
                        pdfFilePath = null
                    )
                }
                tempTicket = updatedTicket
                Toast.makeText(this, getString(R.string.file_attached_ready_to_save), Toast.LENGTH_SHORT).show()
            } else {
                // For new tickets, create a temporary ticket with the correct file type
                val newTicket = if (isPdf) {
                    Ticket(
                        title = formTitle.takeIf { it.isNotEmpty() } ?: getString(R.string.ticket_title_placeholder),
                        description = getString(R.string.manually_created_ticket),
                        venue = formVenue,
                        eventDate = formEventDate,
                        pdfUri = uri.toString(),
                        pdfFilePath = savedFilePath,
                        fileType = Ticket.FileType.PDF
                    )
                } else {
                    Ticket(
                        title = formTitle.takeIf { it.isNotEmpty() } ?: getString(R.string.ticket_title_placeholder),
                        description = getString(R.string.manually_created_ticket),
                        venue = formVenue,
                        eventDate = formEventDate,
                        imageUri = uri.toString(),
                        imageFilePath = savedFilePath,
                        fileType = Ticket.FileType.IMAGE
                    )
                }

                tempTicket = newTicket
                supportActionBar?.title = getString(R.string.edit_ticket)
                Toast.makeText(this, getString(R.string.file_attached_ready_to_save), Toast.LENGTH_SHORT).show()
            }

            // Activate the file-related buttons
            buttonOpen.isEnabled = true
            buttonOpen.alpha = 1.0f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        } catch (e: Exception) {
            android.util.Log.e("TicketEditActivity", "Error processing file", e)
            Toast.makeText(this, getString(R.string.file_replacement_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndSetCopyFile(uri: Uri, mimeType: String?) {
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { originalTicket ->

                val newFileName = getFileNameFromUri(uri)

                if (newFileName != null) {
                    // Check if the new file is not already used in other tickets
                    val allTickets = TicketsRepository.getAllTickets()
                    val duplicateTicket = allTickets.find { ticket ->
                        // Check the filename from pdfFilePath, pdfUri, imageFilePath, or imageUri
                        val existingFileName = ticket.pdfFilePath?.let { File(it).name }
                            ?: ticket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }
                            ?: ticket.imageFilePath?.let { File(it).name }
                            ?: ticket.imageUri?.let { getFileNameFromUri(Uri.parse(it)) }

                        existingFileName != null && existingFileName == newFileName
                    }

                    if (duplicateTicket != null) {
                        Toast.makeText(
                            this,
                            getString(R.string.file_already_used, duplicateTicket.title),
                            Toast.LENGTH_LONG
                        ).show()
                        // Re-open the selector with the proper intent
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        selectCopyFileLauncher.launch(intent)
                        return
                    }
                }

                try {
                    // Determine the file type using the MIME type and extension (same logic as processSelectedFile)
                    val isPdf = mimeType == "application/pdf" ||
                               newFileName?.endsWith(".pdf", ignoreCase = true) == true
                    val isImage = mimeType?.startsWith("image/") == true ||
                                 newFileName?.let { name ->
                                     name.endsWith(".jpg", ignoreCase = true) ||
                                     name.endsWith(".jpeg", ignoreCase = true) ||
                                     name.endsWith(".png", ignoreCase = true) ||
                                     name.endsWith(".gif", ignoreCase = true) ||
                                     name.endsWith(".webp", ignoreCase = true)
                                 } == true

                    if (!isPdf && !isImage) {
                        Toast.makeText(this, getString(R.string.unsupported_file_type), Toast.LENGTH_LONG).show()
                        // Re-open the selector with the proper intent
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                            putExtra(Intent.EXTRA_MIME_TYPES, supportedMimeTypes)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        selectCopyFileLauncher.launch(intent)
                        return
                    }

                    // Save the new file based on the determined type
                    val savedFilePath = if (isPdf) {
                        savePdfToInternalStorage(uri)
                    } else {
                        saveImageToInternalStorage(uri)
                    }

                    // Create a new ticket with the copied data, but DON'T save it to the database yet
                    val title = editTitle.text.toString().trim()
                    val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
                    val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
                        selectedDate.time
                    } else {
                        null
                    }

                    val newTicket = if (isPdf) {
                        // PDF file
                        Ticket(
                            title = title,
                            description = originalTicket.description,
                            venue = venue,
                            eventDate = eventDate,
                            price = originalTicket.price,
                            seatInfo = originalTicket.seatInfo,
                            qrCode = originalTicket.qrCode,
                            barcode = originalTicket.barcode,
                            pdfUri = uri.toString(),
                            pdfFilePath = savedFilePath,
                            fileType = Ticket.FileType.PDF
                        )
                    } else {
                        // Image file
                        Ticket(
                            title = title,
                            description = originalTicket.description,
                            venue = venue,
                            eventDate = eventDate,
                            price = originalTicket.price,
                            seatInfo = originalTicket.seatInfo,
                            qrCode = originalTicket.qrCode,
                            barcode = originalTicket.barcode,
                            imageUri = uri.toString(),
                            imageFilePath = savedFilePath,
                            fileType = Ticket.FileType.IMAGE
                        )
                    }

                    // Activate attachment buttons
                    buttonOpen.isEnabled = true
                    buttonOpen.alpha = 1.0f
                    buttonReplace.isEnabled = true
                    buttonReplace.alpha = 1.0f

                    // Save the ticket as a temporary object for viewing, but not in the database
                    tempTicket = newTicket
                    copyFromTicketId = null // Clear copyFromTicketId

                    // Update the title
                    supportActionBar?.title = getString(R.string.edit_ticket)
                    Toast.makeText(this, getString(R.string.file_attached_ready_to_save), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.file_save_error, e.message), Toast.LENGTH_SHORT).show()
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

            // Create a folder for PDF files in internal storage
            val pdfDir = File(filesDir, "pdf_tickets")
            if (!pdfDir.exists()) {
                val created = pdfDir.mkdirs()
                android.util.Log.d("TicketEditActivity", "Created PDF directory: $created")
            }

            // Generate a unique filename
            val fileName = "ticket_${UUID.randomUUID()}.pdf"
            val destinationFile = File(pdfDir, fileName)
            android.util.Log.d("TicketEditActivity", "Destination file: ${destinationFile.absolutePath}")

            // Copy the file
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
            null
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        return try {
            android.util.Log.d("TicketEditActivity", "=== Saving image to internal storage ===")
            android.util.Log.d("TicketEditActivity", "Source URI: $uri")

            // Create a folder for image files in internal storage
            val imageDir = File(filesDir, "image_tickets")
            if (!imageDir.exists()) {
                val created = imageDir.mkdirs()
                android.util.Log.d("TicketEditActivity", "Created image directory: $created")
            }

            // Generate a unique filename
            val fileName = "ticket_${UUID.randomUUID()}.jpg"
            val destinationFile = File(imageDir, fileName)
            android.util.Log.d("TicketEditActivity", "Destination file: ${destinationFile.absolutePath}")

            // Copy the file
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
            android.util.Log.e("TicketEditActivity", "Error saving image to internal storage", e)
            null
        }
    }

    private fun updateButtonStates() {
        val ticketToCheck = tempTicket ?: currentTicket

        android.util.Log.d("TicketEditActivity", "=== Updating button states ===")
        android.util.Log.d("TicketEditActivity", "tempTicket: ${tempTicket?.id}")
        android.util.Log.d("TicketEditActivity", "currentTicket: ${currentTicket?.id}")
        android.util.Log.d("TicketEditActivity", "copyFromTicketId: $copyFromTicketId")

        if (copyFromTicketId != null) {
            // During a copy operation, buttons should be disabled until a file is selected
            buttonOpen.isEnabled = false
            buttonOpen.alpha = 0.5f
            buttonReplace.isEnabled = false
            buttonReplace.alpha = 0.5f
            android.util.Log.d("TicketEditActivity", "Copy mode - buttons disabled")
        } else {
            // In normal mode, check if the ticket has an attachment
            val hasAttachment = ticketToCheck?.hasAttachment() ?: false

            buttonOpen.isEnabled = hasAttachment
            buttonOpen.alpha = if (hasAttachment) 1.0f else 0.5f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f

            android.util.Log.d("TicketEditActivity", "Normal mode - hasAttachment: $hasAttachment")
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        checkUnsavedChangesBeforeExit()
        return false // Return false so Android doesn't handle navigation automatically
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
            // For a ticket copy, always show the dialog since the ticket is not saved yet
            copyFromTicketId != null -> true
            // For a temporary ticket with an attachment, always show the dialog
            tempTicket != null -> true
            // For a new ticket, there are changes if any fields are filled
            currentTicket == null -> {
                currentTitle.isNotEmpty() || currentVenue.isNotEmpty() || currentDateText.isNotEmpty()
            }
            // For an existing ticket, compare with the original values
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
        // Delete the temporary PDF file if it exists
        tempTicket?.pdfFilePath?.let { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    // Check if this is not the original file of the existing ticket
                    val isOriginalFile = currentTicket?.pdfFilePath == filePath
                    if (!isOriginalFile) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }

        // Delete the temporary image file if it exists
        tempTicket?.imageFilePath?.let { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    // Check if this is not the original file of the existing ticket
                    val isOriginalFile = currentTicket?.imageFilePath == filePath
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
