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

class TicketEditActivity : AppCompatActivity() {

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
    private var selectedDate: Calendar = Calendar.getInstance()

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

        // Налаштовуємо action bar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        setupButtons()

        // Отримуємо ID квитка з Intent
        ticketId = intent.getStringExtra("TICKET_ID")
        copyFromTicketId = intent.getStringExtra("COPY_FROM_TICKET_ID")

        loadTicketData()
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
                supportActionBar?.title = "Створення копії квитка"

                // Копіюємо всі поля крім ID та PDF
                editTitle.setText(ticket.title)
                editVenue.setText(ticket.venue ?: "")

                ticket.eventDate?.let { date ->
                    selectedDate.time = date
                    updateDateField()
                }

                // Показуємо повідомлення про необхідність вибору PDF
                Toast.makeText(this, "Оберіть PDF файл для копії квитка", Toast.LENGTH_LONG).show()

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
                supportActionBar?.title = "Редагувати квиток"

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
        } ?: run {
            // Новий квиток
            supportActionBar?.title = "Новий квиток"
            buttonOpen.isEnabled = false
            buttonOpen.alpha = 0.5f
            buttonReplace.isEnabled = false
            buttonReplace.alpha = 0.5f
        }
    }

    private fun saveTicket() {
        val title = editTitle.text.toString().trim()
        if (title.isEmpty()) {
            Toast.makeText(this, "Введіть назву квитка", Toast.LENGTH_SHORT).show()
            return
        }

        val venue = editVenue.text.toString().trim().takeIf { it.isNotEmpty() }

        // Отримуємо вибрану дату та час (може бути null якщо не вибрано)
        val eventDate = if (editDate.text.toString().trim().isNotEmpty()) {
            selectedDate.time
        } else {
            null
        }

        val ticket = if (currentTicket != null) {
            // Оновлюємо існуючий квиток, зберігаючи pdfUri
            currentTicket!!.copy(
                title = title,
                venue = venue,
                eventDate = eventDate
            )
        } else {
            // Створюємо новий квиток
            Ticket(
                title = title,
                description = "Квиток створено вручну",
                venue = venue,
                eventDate = eventDate
            )
        }

        if (currentTicket != null) {
            TicketsRepository.updateTicket(ticket)
            Toast.makeText(this, "Квиток оновлено", Toast.LENGTH_SHORT).show()
        } else {
            TicketsRepository.addTicket(ticket)
            Toast.makeText(this, "Квиток збережено", Toast.LENGTH_SHORT).show()
        }

        finish()
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Видалити квиток")
            .setMessage("Ви впевнені, що хочете видалити цей квиток?")
            .setPositiveButton("Видалити") { _, _ ->
                ticketId?.let { id ->
                    TicketsRepository.removeTicket(id)
                    Toast.makeText(this, "Квиток видалено", Toast.LENGTH_SHORT).show()
                    finish()
                } ?: run {
                    // Якщо це новий квиток, просто закриваємо екран
                    Toast.makeText(this, "Скасовано", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("Скасувати", null)
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
        currentTicket?.let { ticket ->
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
                        Toast.makeText(this, "Немає додатка для відкриття PDF", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Помилка відкриття PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "PDF файл не знайдено для цього квитка", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(this, "PDF файл не знайдено для цього квитка", Toast.LENGTH_SHORT).show()
        }
    }

    private fun selectReplacementPdf() {
        // Показуємо діалог підтвердження заміни PDF
        AlertDialog.Builder(this)
            .setTitle("Замінити PDF файл")
            .setMessage("Ви впевнені, що хочете замінити поточний PDF файл? Старий файл буде видалено.")
            .setPositiveButton("Замінити") { _, _ ->
                selectReplacementPdfLauncher.launch("application/pdf")
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun replacePdfFile(uri: Uri) {
        currentTicket?.let { ticket ->
            // Отримуємо назву нового PDF файлу
            val newFileName = getFileNameFromUri(uri)

            if (newFileName != null) {
                // Перевіряємо, чи новий PDF файл не використовується в інших квитках
                val allTickets = TicketsRepository.getAllTickets()
                val duplicateTicket = allTickets.find { otherTicket ->
                    // Пропускаємо поточний квиток при перевірці
                    if (otherTicket.id == ticket.id) return@find false

                    // Перевіряємо назву файлу з pdfFilePath або pdfUri
                    val existingFileName = otherTicket.pdfFilePath?.let { File(it).name }
                        ?: otherTicket.pdfUri?.let { getFileNameFromUri(Uri.parse(it)) }

                    existingFileName != null && existingFileName == newFileName
                }

                if (duplicateTicket != null) {
                    Toast.makeText(
                        this,
                        "Цей PDF файл вже використовується в квитку \"${duplicateTicket.title}\". Оберіть інший файл.",
                        Toast.LENGTH_LONG
                    ).show()
                    // Повторно відкриваємо селектор
                    selectReplacementPdfLauncher.launch("application/pdf")
                    return
                }
            }

            try {
                // Видаляємо старий файл з внутрішнього сховища, якщо він існує
                ticket.pdfFilePath?.let { oldFilePath ->
                    val oldFile = File(oldFilePath)
                    if (oldFile.exists()) {
                        oldFile.delete()
                    }
                }

                // Зберігаємо новий PDF файл
                val savedPdfPath = savePdfToInternalStorage(uri)

                // Оновлюємо квиток з новими даними
                val updatedTicket = ticket.copy(
                    pdfUri = uri.toString(),
                    pdfFilePath = savedPdfPath
                )

                TicketsRepository.updateTicket(updatedTicket)
                currentTicket = updatedTicket

                // Активуємо кнопки PDF
                buttonOpen.isEnabled = true
                buttonOpen.alpha = 1.0f
                buttonReplace.isEnabled = true
                buttonReplace.alpha = 1.0f

                Toast.makeText(this, "PDF файл замінено", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Помилка заміни PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
                            "Цей PDF файл вже використовується в квитку \"${duplicateTicket.title}\". Оберіть інший файл.",
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

                    Toast.makeText(this, "Копію квитка створено", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Помилка збереження PDF: ${e.message}", Toast.LENGTH_SHORT).show()
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
        finish()
        return true
    }
}
