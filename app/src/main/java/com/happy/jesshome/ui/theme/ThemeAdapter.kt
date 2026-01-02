package com.happy.jesshome.ui.theme

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.happy.jesshome.R

class ThemeAdapter(
    private val items: List<ThemeItem>,
    private val onApply: (ThemeItem) -> Unit,
) : RecyclerView.Adapter<ThemeAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_theme_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.thumb.setImageResource(item.thumbRes)
        holder.apply.setOnClickListener { onApply(item) }
    }

    override fun getItemCount(): Int = items.size

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumb: ImageView = itemView.findViewById(R.id.iv_thumb)
        val name: TextView = itemView.findViewById(R.id.tv_name)
        val apply: TextView = itemView.findViewById(R.id.btn_apply)
    }
}
