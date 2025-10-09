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
            // Protect against empty ticket titles
            textTitle.text = if (ticket.title.isBlank()) {
                itemView.context.getString(R.string.ticket_title_placeholder)
            } else {
                ticket.title
            }

            textDate.text = if (ticket.eventDate != null) {
                ticket.getFormattedEventDate()
            } else {
                itemView.context.getString(R.string.date_not_specified)
            }
            textVenue.text = ticket.venue ?: itemView.context.getString(R.string.venue_not_specified)

            // Check if there's an attached file (PDF or image)
            val hasAttachment = when {
                // Check for PDF file
                !ticket.pdfFilePath.isNullOrEmpty() -> {
                    val file = File(ticket.pdfFilePath)
                    file.exists()
                }
                !ticket.pdfUri.isNullOrEmpty() -> true
                // Check for image file
                !ticket.imageFilePath.isNullOrEmpty() -> {
                    val file = File(ticket.imageFilePath)
                    file.exists()
                }
                !ticket.imageUri.isNullOrEmpty() -> true
                else -> false
            }

            buttonOpen.isEnabled = hasAttachment
            buttonOpen.alpha = if (hasAttachment) 1.0f else 0.5f // Visually show inactive state

            // Highlight tickets based on status
            when {
                ticket.eventDate == null -> {
                    // Tickets without date - white background
                    itemView.setBackgroundColor(itemView.context.getColor(android.R.color.white))
                    textTitle.setTextColor(itemView.context.getColor(android.R.color.black))
                    textDate.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    textVenue.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                }
                ticket.isUpcoming() -> {
                    // Future tickets - very pale green background for better readability
                    itemView.setBackgroundColor(0xFFE8F5E8.toInt()) // Very pale green
                    textTitle.setTextColor(itemView.context.getColor(android.R.color.black))
                    textDate.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                    textVenue.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
                }
                else -> {
                    // Expired tickets - gray background
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
                if (hasAttachment) {
                    onOpenClick(ticket)
                }
            }
        }
    }
}
