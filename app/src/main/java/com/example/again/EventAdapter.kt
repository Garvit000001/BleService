package com.example.again

import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EventAdapter(
    private val list: MutableList<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<EventAdapter.ViewHolder>() {

    inner class ViewHolder(val textView: TextView) :
        RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tv = TextView(parent.context)
        tv.setPadding(24, 24, 24, 24)
        return ViewHolder(tv)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = list[position]
        holder.textView.setOnClickListener {
            onClick(list[position])
        }
    }

    override fun getItemCount() = list.size
}