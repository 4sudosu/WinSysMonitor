package com.wsmonitor.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject

class DeviceAdapter(
    private val onCapture: (String) -> Unit,
    private val onOpen: (JSONObject) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.Holder>() {

    private val items = mutableListOf<JSONObject>()

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.statusDot)
        val hostname: TextView = view.findViewById(R.id.tvHostname)
        val status: TextView = view.findViewById(R.id.tvStatus)
        val model: TextView = view.findViewById(R.id.tvModel)
        val ip: TextView = view.findViewById(R.id.tvIp)
        val serial: TextView = view.findViewById(R.id.tvSerial)
        val version: TextView = view.findViewById(R.id.tvVersion)
        val capture: Button = view.findViewById(R.id.btnCapture)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return Holder(view)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val d = items[position]
        val machine = d.optString("machineName", "")
        val hostname = d.optString("hostname", machine).ifBlank { machine }
        val online = d.optBoolean("online", false)
        holder.hostname.text = hostname
        holder.status.text = if (online) "online" else "offline"
        holder.model.text = d.optString("model", "").ifBlank { "—" }
        holder.ip.text = "IP: ${d.optString("ip", "—")}"
        holder.serial.text = "Serial: ${d.optString("serial", "—")}"
        holder.version.text = "Agent: v${d.optString("version", "—")}"
        holder.dot.background = holder.dot.context.getDrawable(
            if (online) R.drawable.bg_status_online else R.drawable.bg_status_chip
        )
        holder.capture.isEnabled = online
        holder.capture.alpha = if (online) 1f else 0.4f
        holder.capture.setOnClickListener { onCapture(machine) }

        holder.serial.setTextColor(
            holder.serial.context.getColor(com.wsmonitor.app.R.color.cyan)
        )
        holder.serial.setOnClickListener { onOpen(d) }
        holder.itemView.setOnClickListener { onOpen(d) }
    }

    fun submit(list: List<JSONObject>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }
}