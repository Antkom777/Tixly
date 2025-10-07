package com.tixly.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tixly.app.data.Ticket
import java.io.File

class TicketsAdapter(
    private val onItemClick: (Ticket) -> Unit,
    private val onOpenClick: (Ticket) -> Unit,
    private val onCopyClick: (Ticket) -> Unit
) : RecyclerView.Adapter<TicketsAdapter.TicketViewHolder>() {

    private var tickets = listOf<Ticket>()

    fun updateTickets(newTickets: List<Ticket>) {
        tickets = newTickets
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ticket, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = tickets[position]
        holder.bind(ticket)
    }

    override fun getItemCount(): Int = tickets.size

    inner class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textTitle: TextView = itemView.findViewById(R.id.textTicketTitle)
        private val textDate: TextView = itemView.findViewById(R.id.textTicketDate)
        private val textVenue: TextView = itemView.findViewById(R.id.textTicketVenue)
        private val buttonCopy: ImageButton = itemView.findViewById(R.id.buttonCopyTicket)
        private val buttonEdit: ImageButton = itemView.findViewById(R.id.buttonEditTicket)
        private val buttonOpen: ImageButton = itemView.findViewById(R.id.buttonOpenTicket)

        fun bind(ticket: Ticket) {
            textTitle.text = ticket.title
            textDate.text = if (ticket.eventDate != null) {
                ticket.getFormattedEventDate()
            } else {
                "Дата не вказана"
            }
            textVenue.text = ticket.venue ?: "Місце не вказано"

            // Перевіряємо чи є прив'язаний PDF файл
            val hasPdf = (!ticket.pdfUri.isNullOrEmpty()) ||
                         (!ticket.pdfFilePath.isNullOrEmpty() && File(ticket.pdfFilePath).exists())
            buttonOpen.isEnabled = hasPdf
            buttonOpen.alpha = if (hasPdf) 1.0f else 0.5f // Візуально показуємо неактивність

            // Підсвічуємо квитки залежно від статусу
            when {
                ticket.eventDate == null -> {
                    // Квитки без дати - білий фон
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.white))
                    textTitle.setTextColor(itemView.context.getColor(android.R.color.black))
                    textDate.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    textVenue.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                }
                ticket.isUpcoming() -> {
                    // Майбутні квитки - дуже блідо-зелений фон для кращої читабельності
                    itemView.setBackgroundColor(0xFFE8F5E8.toInt()) // Дуже блідий зелений
                    textTitle.setTextColor(itemView.context.getColor(android.R.color.black))
                    textDate.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    textVenue.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                }
                else -> {
                    // Прострочені квитки - сірий фон
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.darker_gray))
                    textTitle.setTextColor(itemView.context.getColor(android.R.color.white))
                    textDate.setTextColor(itemView.context.getColor(android.R.color.white))
                    textVenue.setTextColor(itemView.context.getColor(android.R.color.white))
                }
            }

            buttonCopy.setOnClickListener {
                onCopyClick(ticket)
            }

            buttonEdit.setOnClickListener {
                onItemClick(ticket)
            }

            buttonOpen.setOnClickListener {
                if (hasPdf) {
                    onOpenClick(ticket)
                }
            }
        }
    }
}
