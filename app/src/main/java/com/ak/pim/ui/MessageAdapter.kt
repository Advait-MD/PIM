package com.ak.pim.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ak.pim.R
import com.ak.pim.model.ChatMessage

class MessageAdapter(
    private val items: MutableList<ChatMessage>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val VIEW_SENT = 1
    private val VIEW_RECEIVED = 2

    override fun getItemViewType(position: Int): Int =
        if (items[position].isSentByUser) VIEW_SENT else VIEW_RECEIVED

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inf = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_SENT) {
            SentHolder(inf.inflate(R.layout.item_message_sent, parent, false))
        } else {
            ReceivedHolder(inf.inflate(R.layout.item_message_received, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is SentHolder -> holder.bind(msg)
            is ReceivedHolder -> holder.bind(msg)
        }
    }

    override fun getItemCount(): Int = items.size

    fun addMessage(message: ChatMessage) {
        items.add(message)
        notifyItemInserted(items.size - 1)
    }

    class SentHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val body: TextView = itemView.findViewById(R.id.text_message_body)
        fun bind(m: ChatMessage) { body.text = m.text }
    }

    class ReceivedHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val body: TextView = itemView.findViewById(R.id.text_message_body)
        fun bind(m: ChatMessage) { body.text = m.text }
    }
}
