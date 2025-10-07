package com.tixly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tixly.app.data.Ticket
import com.tixly.app.data.TicketsRepository
import com.tixly.app.utils.PDFProcessor
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.*
import kotlin.system.exitProcess

class TicketsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var ticketsAdapter: TicketsAdapter
    private lateinit var pdfProcessor: PDFProcessor

    // Launcher для вибору PDF файлу
    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { processPdfFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tickets)

        // Ініціалізуємо репозиторій з контекстом
        TicketsRepository.initialize(this)

        pdfProcessor = PDFProcessor(this)

        // Налаштовуємо action bar
        supportActionBar?.title = "Мої квитки"

        setupRecyclerView()
        setupFab()
        loadTickets()

        // Обробляємо Intent якщо додаток запущено через Share або View
        handleIncomingIntent(intent)
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
                        Toast.makeText(this, "PDF файл отримано через Share", Toast.LENGTH_SHORT).show()
                        processPdfFile(uri)
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                if (intent.type == "application/pdf") {
                    intent.data?.let { uri ->
                        Toast.makeText(this, "PDF файл відкрито в додатку", Toast.LENGTH_SHORT).show()
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
            // Відкриваємо файловий менеджер для вибору PDF
            selectPdfLauncher.launch("application/pdf")
        }
    }

    override fun onResume() {
        super.onResume()
        // Оновлюємо список при поверненні до активності
        loadTickets()
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.recyclerViewTickets)
        recyclerView.layoutManager = LinearLayoutManager(this)

        ticketsAdapter = TicketsAdapter(
            onItemClick = { ticket ->
                // Переходимо до детального перегляду/редагування квитка
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            },
            onOpenClick = { ticket ->
                // Відкриваємо PDF файл (якщо є)
                openTicketPdf(ticket)
            },
            onCopyClick = { ticket ->
                // Копіюємо квиток з новим PDF
                copyTicket(ticket)
            }
        )

        recyclerView.adapter = ticketsAdapter
    }

    private fun copyTicket(originalTicket: Ticket) {
        // Переходимо до створення копії квитка
        val intent = Intent(this, TicketEditActivity::class.java)
        intent.putExtra("COPY_FROM_TICKET_ID", originalTicket.id)
        startActivity(intent)
    }

    private fun openTicketPdf(ticket: Ticket) {
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
    }

    private fun processPdfFile(uri: Uri) {
        Toast.makeText(this, "Обробка PDF файлу...", Toast.LENGTH_SHORT).show()

        try {
            val ticket = pdfProcessor.processPDF(uri)
            if (ticket != null) {
                // Додаємо тікет до спільного сховища
                TicketsRepository.addTicket(ticket)
                Toast.makeText(this, "Квиток успішно збережено!", Toast.LENGTH_LONG).show()

                // Одразу відкриваємо екран редагування нового квитка
                val intent = Intent(this, TicketEditActivity::class.java)
                intent.putExtra("TICKET_ID", ticket.id)
                startActivity(intent)
            } else {
                // Перевіряємо, чи це через дублювання PDF
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
                            "Цей PDF файл вже використовується в квитку \"${duplicateTicket.title}\". Оберіть інший файл.",
                            Toast.LENGTH_LONG
                        ).show()
                        return
                    }
                }

                Toast.makeText(this, "Не вдалося обробити PDF файл", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Помилка при обробці PDF: ${e.message}", Toast.LENGTH_LONG).show()
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

        // Розширене сортування квитків згідно з новими вимогами
        val sortedTickets = allTickets.sortedWith { ticket1, ticket2 ->
            val date1 = ticket1.eventDate
            val date2 = ticket2.eventDate
            val now = Date()

            when {
                // 1. Квитки без дати завжди зверху
                date1 == null && date2 != null -> -1
                date1 != null && date2 == null -> 1
                date1 == null && date2 == null -> 0

                // 2. Обидва мають дати - детальне сортування
                date1 != null && date2 != null -> {
                    val isUpcoming1 = date1.after(now)
                    val isUpcoming2 = date2.after(now)

                    when {
                        // Актуальні квитки (майбутні): найближча дата першою
                        isUpcoming1 && isUpcoming2 -> date1.compareTo(date2)

                        // Один актуальний, один прострочений: актуальний вперед
                        isUpcoming1 && !isUpcoming2 -> -1
                        !isUpcoming1 && isUpcoming2 -> 1

                        // Обидва прострочені: старіші внизу (зворотній порядок)
                        !isUpcoming1 && !isUpcoming2 -> date2.compareTo(date1)

                        else -> 0
                    }
                }

                else -> 0
            }
        }

        ticketsAdapter.updateTickets(sortedTickets)

        if (sortedTickets.isEmpty()) {
            Toast.makeText(this, "Немає збережених квитків", Toast.LENGTH_SHORT).show()
        }
    }
}
