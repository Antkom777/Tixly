package com.tixly.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
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
    private var tempTicket: Ticket? = null // Додаємо для тимчасових квитків з PDF
    private var selectedDate: Calendar = Calendar.getInstance()
    private var currentLanguage: String = ""

    // Launcher для вибору нового PDF файлу при заміні
    private val selectReplacementPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { replacePdfFile(it) }
    }

    // Launcher для вибору PDF при копіюванні
    private val selectCopyPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { validateAndSetCopyPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ticket_edit)

        // Запам'ятовуємо поточну мову
        val settingsManager = com.tixly.app.utils.SettingsManager(this)
        currentLanguage = settingsManager.getLanguage()

        // Налаштовуємо action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupButtons()

        // Отримуємо ID квитка з Intent
        ticketId = intent.getStringExtra("TICKET_ID")
        copyFromTicketId = intent.getStringExtra("COPY_FROM_TICKET_ID")

        // Перевіряємо, чи є тимчасовий квиток з PDF
        intent.getStringExtra("TEMP_TICKET_DATA")?.let { tempData ->
            tempTicket = Ticket.fromJson(tempData)
        }

        loadTicketData()
    }

    override fun onResume() {
        super.onResume()

        // Перевіряємо, чи змінилася мова
        val settingsManager = com.tixly.app.utils.SettingsManager(this)
        val newLanguage = settingsManager.getLanguage()
        if (newLanguage != currentLanguage) {
            // Мова змінилася, перезавантажуємо активність
            recreate()
            return
        }

        // Оновлюємо заголовок активності при поверненні (наприклад, після зміни мови)
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

        // Робимо поле дати неедитованим напряму - тільки через picker
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
        // Якщо це копіювання квитка
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { ticket ->
                supportActionBar?.title = getString(R.string.create_ticket_copy)

                // Копіюємо всі поля крім ID та PDF
                editTitle.setText(ticket.title)
                editVenue.setText(ticket.venue ?: "")

                ticket.eventDate?.let { date ->
                    selectedDate.time = date
                    updateDateField()
                }

                // Показуємо повідомлення про необхідність вибору PDF
                Toast.makeText(this, getString(R.string.select_pdf_for_copy), Toast.LENGTH_LONG).show()

                // Автоматично відкриваємо селектор PDF
                selectCopyPdfLauncher.launch("application/pdf")

                // PDF кнопки недоступні поки не обрано файл
                buttonOpen.isEnabled = false
                buttonOpen.alpha = 0.5f
                buttonReplace.isEnabled = false
                buttonReplace.alpha = 0.5f
            }
            return
        }

        // Якщо це редагування існуючого квитка
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

                // Налаштовуємо кнопки PDF
                val hasPdf = !ticket.pdfUri.isNullOrEmpty() || (!ticket.pdfFilePath.isNullOrEmpty() && File(ticket.pdfFilePath).exists())
                buttonOpen.isEnabled = hasPdf
                buttonOpen.alpha = if (hasPdf) 1.0f else 0.5f
                // Кнопка заміни PDF завжди активна
                buttonReplace.isEnabled = true
                buttonReplace.alpha = 1.0f
            }
        } ?: run {
            // Новий квиток
            supportActionBar?.title = getString(R.string.new_ticket)
            buttonOpen.isEnabled = false
            buttonOpen.alpha = 0.5f
            // Кнопка заміни PDF активна для можливості додавання PDF до нового квитка
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f
        }

        // Якщо це тимчасовий квиток з PDF
        tempTicket?.let { ticket ->
            supportActionBar?.title = getString(R.string.edit_ticket)

            editTitle.setText(ticket.title)
            editVenue.setText(ticket.venue ?: "")

            ticket.eventDate?.let { date ->
                selectedDate.time = date
                updateDateField()
            }

            // Налаштовуємо кнопки PDF
            val hasPdf = !ticket.pdfUri.isNullOrEmpty() || (!ticket.pdfFilePath.isNullOrEmpty() && File(ticket.pdfFilePath).exists())
            buttonOpen.isEnabled = hasPdf
            buttonOpen.alpha = if (hasPdf) 1.0f else 0.5f
            buttonReplace.isEnabled = hasPdf
            buttonReplace.alpha = if (hasPdf) 1.0f else 0.5f
        }
    }

    private fun saveTicket() {
        val title = editTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, getString(R.string.enter_ticket_title), Toast.LENGTH_SHORT).show()
            return
        }

        val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }

        // Отримуємо вибрану дату та час (може бути null якщо не вибрано)
        val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
            selectedDate.time
        } else {
            null
        }

        val ticket = when {
            currentTicket != null -> {
                // Оновлюємо існуючий квиток
                currentTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            tempTicket != null -> {
                // Зберігаємо тимчасовий квиток з PDF
                tempTicket!!.copy(
                    title = title,
                    venue = venue,
                    eventDate = eventDate
                )
            }
            else -> {
                // Створюємо новий квиток
                Ticket(
                    title = title,
                    description = getString(R.string.manually_created_ticket),
                    venue = venue,
                    eventDate = eventDate
                )
            }
        }

        if (currentTicket != null) {
            TicketsRepository.updateTicket(ticket)
            Toast.makeText(this, getString(R.string.ticket_updated), Toast.LENGTH_SHORT).show()
        } else {
            TicketsRepository.addTicket(ticket)
            Toast.makeText(this, getString(R.string.ticket_saved), Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_ticket))
            .setMessage(getString(R.string.delete_ticket_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                ticketId?.let { id ->
                    TicketsRepository.removeTicket(id)
                    Toast.makeText(this, getString(R.string.ticket_deleted), Toast.LENGTH_SHORT).show()
                    finish()
                } ?: run {
                    // Якщо це новий квиток, просто закриваємо екран
                    Toast.makeText(this, getString(R.string.cancelled), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showDateTimePicker() {
        // Спочатку показуємо calendar picker
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate.set(Calendar.YEAR, year)
                selectedDate.set(Calendar.MONTH, month)
                selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Після вибору дати показуємо time picker
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

                // Оновлюємо поле дати з вибраними значеннями
                updateDateField()
            },
            selectedDate.get(Calendar.HOUR_OF_DAY),
            selectedDate.get(Calendar.MINUTE),
            true // 24-годинний формат
        )

        timePickerDialog.show()
    }

    private fun updateDateField() {
        val format = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        editDate.setText(format.format(selectedDate.time))
    }

    private fun openTicketPdf() {
        val ticketToOpen = currentTicket ?: tempTicket

        ticketToOpen?.let { ticket ->
            // Спочатку пробуємо відкрити збережену копію з внутрішнього сховища
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
                        // Якщо не вдалося відкрити збережену копію, пробуємо оригінальний URI
                    }
                }
            }

            // Fallback: пробуємо оригінальний URI
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
        } ?: run {
            Toast.makeText(this, getString(R.string.pdf_not_found), Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectReplacementPdf() {
        val ticketToModify = currentTicket ?: tempTicket

        if (ticketToModify != null) {
            // Для існуючих квитків - перевіряємо, чи є PDF файл
            val hasPdf = !ticketToModify.pdfUri.isNullOrEmpty() ||
                        (!ticketToModify.pdfFilePath.isNullOrEmpty() && File(ticketToModify.pdfFilePath).exists())

            if (hasPdf) {
                // Якщо PDF є, показуємо діалог підтвердження заміни
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.replace_pdf_file))
                    .setMessage(getString(R.string.replace_pdf_confirmation))
                    .setPositiveButton(getString(R.string.replace)) { _, _ ->
                        selectReplacementPdfLauncher.launch("application/pdf")
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                // Якщо PDF немає, одразу відкриваємо селектор
                selectReplacementPdfLauncher.launch("application/pdf")
            }
        } else {
            // Для нових квитків - одразу відкриваємо селектор для додавання PDF
            selectReplacementPdfLauncher.launch("application/pdf")
        }
    }

    private fun replacePdfFile(uri: Uri) {
        // Отримуємо назву нового PDF файлу
        val newFileName = getFileNameFromUri(uri)

        if (newFileName != null) {
            // Перевіряємо, чи новий PDF файл не використовується в інших квитках
            val allTickets = TicketsRepository.getAllTickets()
            val duplicateTicket = allTickets.find { otherTicket ->
                // Пропускаємо поточний квиток при перевірці (якщо він існує)
                if (currentTicket != null && otherTicket.id == currentTicket!!.id) return@find false

                // Перевіряємо назву файлу з pdfFilePath або pdfUri
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
                // Повторно відкриваємо селектор
                selectReplacementPdfLauncher.launch("application/pdf")
                return
            }
        }

        try {
            // Видаляємо старий файл з внутрішнього сховища, якщо він існує
            currentTicket?.pdfFilePath?.let { oldFilePath ->
                val oldFile = File(oldFilePath)
                if (oldFile.exists()) {
                    oldFile.delete()
                }
            }

            // Зберігаємо новий PDF файл
            val savedPdfPath = savePdfToInternalStorage(uri)

            if (currentTicket != null) {
                // Оновлюємо існуючий квиток
                val updatedTicket = currentTicket!!.copy(
                    pdfUri = uri.toString(),
                    pdfFilePath = savedPdfPath
                )

                TicketsRepository.updateTicket(updatedTicket)
                currentTicket = updatedTicket
            } else {
                // Створюємо новий квиток з PDF файлом
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

                TicketsRepository.addTicket(newTicket)
                currentTicket = newTicket

                // Оновлюємо заголовок активності
                supportActionBar?.title = getString(R.string.edit_ticket)
            }

            // Активуємо кнопки PDF
            buttonOpen.isEnabled = true
            buttonOpen.alpha = 1.0f
            buttonReplace.isEnabled = true
            buttonReplace.alpha = 1.0f

            val message = if (currentTicket?.id != null && TicketsRepository.getTicketById(currentTicket!!.id) != null) {
                getString(R.string.pdf_file_replaced)
            } else {
                getString(R.string.ticket_saved_successfully)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.pdf_replacement_error, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateAndSetCopyPdf(uri: Uri) {
        copyFromTicketId?.let { copyId ->
            val templateTicket = TicketsRepository.getTicketById(copyId)
            templateTicket?.let { originalTicket ->

                // Отримуємо назву нового PDF файлу
                val newFileName = getFileNameFromUri(uri)

                if (newFileName != null) {
                    // Перевіряємо, чи новий PDF файл не використовується в інших квитках
                    val allTickets = TicketsRepository.getAllTickets()
                    val duplicateTicket = allTickets.find { ticket ->
                        // Перевіряємо назву файлу з pdfFilePath
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
                        // Повторно відкриваємо селектор
                        selectCopyPdfLauncher.launch("application/pdf")
                        return
                    }
                }

                try {
                    // Зберігаємо новий PDF файл
                    val savedPdfPath = savePdfToInternalStorage(uri)

                    // Створюємо новий квиток з скопійованими даними
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

                    TicketsRepository.addTicket(newTicket)

                    // Активуємо кнопки PDF
                    buttonOpen.isEnabled = true
                    buttonOpen.alpha = 1.0f
                    buttonReplace.isEnabled = true
                    buttonReplace.alpha = 1.0f

                    // Оновлюємо поточний квиток для можливості перегляду PDF
                    currentTicket = newTicket

                    Toast.makeText(this, getString(R.string.ticket_copy_created), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, getString(R.string.pdf_save_error, e.message), Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
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
            // Створюємо папку для PDF файлів у внутрішньому сховищі
            val pdfDir = File(filesDir, "pdf_tickets")
            if (!pdfDir.exists()) {
                pdfDir.mkdirs()
            }

            // Генеруємо унікальне ім'я файлу
            val fileName = "ticket_${UUID.randomUUID()}.pdf"
            val destinationFile = File(pdfDir, fileName)

            // Копіюємо файл
            contentResolver.openInputStream(uri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        // Перевіряємо, чи були внесені зміни перед виходом
        checkUnsavedChangesBeforeExit()
        return false // Повертаємо false, щоб Android не обробляв навігацію автоматично
    }

    override fun onBackPressed() {
        // Перевіряємо, чи були внесені зміни перед виходом
        checkUnsavedChangesBeforeExit()
    }

    private fun checkUnsavedChangesBeforeExit() {
        // Додаємо логування для діагностики
        android.util.Log.d("TicketEditActivity", "checkUnsavedChangesBeforeExit called")
        android.util.Log.d("TicketEditActivity", "tempTicket: ${tempTicket?.id}, currentTicket: ${currentTicket?.id}")

        // Перевіряємо, чи були внесені зміни
        val hasChanges = hasUnsavedChanges()
        android.util.Log.d("TicketEditActivity", "hasUnsavedChanges: $hasChanges")

        if (hasChanges) {
            // Показуємо діалог підтвердження
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.unsaved_changes))
                .setMessage(getString(R.string.unsaved_changes_message))
                .setPositiveButton(getString(R.string.save)) { _, _ ->
                    android.util.Log.d("TicketEditActivity", "User chose to save changes")
                    saveTicket()
                }
                .setNegativeButton(getString(R.string.discard)) { _, _ ->
                    android.util.Log.d("TicketEditActivity", "User chose to discard changes")
                    // Видаляємо тимчасовий квиток якщо він існує
                    cleanupTempTicket()
                    // Закриваємо активність
                    finish()
                }
                .setNeutralButton(getString(R.string.cancel)) { _, _ ->
                    android.util.Log.d("TicketEditActivity", "User chose to cancel")
                }
                .show()
        } else {
            android.util.Log.d("TicketEditActivity", "No unsaved changes, closing activity")
            // Якщо змін немає, просто закриваємо
            cleanupTempTicket()
            finish()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        val currentTitle = editTitle.text.toString().trim()
        val currentVenue = editVenue.text.toString().trim()
        val currentDateText = editDate.text.toString().trim()

        android.util.Log.d("TicketEditActivity", "hasUnsavedChanges - currentTitle: '$currentTitle'")
        android.util.Log.d("TicketEditActivity", "hasUnsavedChanges - currentVenue: '$currentVenue'")
        android.util.Log.d("TicketEditActivity", "hasUnsavedChanges - currentDateText: '$currentDateText'")

        return when {
            // Для тимчасового квитка з PDF - завжди показуємо діалог, оскільки квиток ще не збережено
            tempTicket != null -> {
                android.util.Log.d("TicketEditActivity", "tempTicket - always has changes (not saved yet)")
                true // Завжди true для тимчасових квитків
            }
            // Для нового квитка - є зміни якщо заповнені поля
            currentTicket == null -> {
                val hasChanges = currentTitle.isNotEmpty() || currentVenue.isNotEmpty() || currentDateText.isNotEmpty()
                android.util.Log.d("TicketEditActivity", "newTicket - hasChanges: $hasChanges")
                hasChanges
            }
            // Для існуючого квитка - порівнюємо з оригінальними значеннями
            else -> {
                val originalTitle = currentTicket?.title ?: ""
                val originalVenue = currentTicket?.venue ?: ""
                val originalDate = currentTicket?.eventDate?.let {
                    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(it)
                } ?: ""

                android.util.Log.d("TicketEditActivity", "existingTicket - originalTitle: '$originalTitle'")
                android.util.Log.d("TicketEditActivity", "existingTicket - originalVenue: '$originalVenue'")
                android.util.Log.d("TicketEditActivity", "existingTicket - originalDate: '$originalDate'")

                val hasChanges = currentTitle != originalTitle ||
                currentVenue != originalVenue ||
                currentDateText != originalDate

                android.util.Log.d("TicketEditActivity", "existingTicket - hasChanges: $hasChanges")
                hasChanges
            }
        }
    }

    private fun cleanupTempTicket() {
        // Видаляємо тимчасовий PDF файл якщо він існує
        tempTicket?.pdfFilePath?.let { filePath ->
            try {
                val file = File(filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ігноруємо помилки при видаленні тимчасового файлу
            }
        }
    }

    private fun saveTicketWithDefaultTitle() {
        val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }
        val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
            selectedDate.time
        } else {
            null
        }

        // Створюємо автоматичну назву на основі дати або просто "Новий квиток"
        val autoTitle = if (eventDate != null) {
            val format = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault())
            "${getString(R.string.event_on)} ${format.format(eventDate)}"
        } else {
            getString(R.string.new_event)
        }

        val ticket = if (currentTicket != null) {
            currentTicket!!.copy(
                title = autoTitle,
                venue = venue,
                eventDate = eventDate
            )
        } else {
            com.tixly.app.data.Ticket(
                title = autoTitle,
                description = getString(R.string.manually_created_ticket),
                venue = venue,
                eventDate = eventDate
            )
        }

        if (currentTicket != null) {
            com.tixly.app.data.TicketsRepository.updateTicket(ticket)
            Toast.makeText(this, getString(R.string.ticket_updated), Toast.LENGTH_SHORT).show()
        } else {
            com.tixly.app.data.TicketsRepository.addTicket(ticket)
            Toast.makeText(this, getString(R.string.ticket_saved), Toast.LENGTH_SHORT).show()
        }

        finish()
    }
}
