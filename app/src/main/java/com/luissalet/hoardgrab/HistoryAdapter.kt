package com.luissalet.hoardgrab

import android.graphics.Bitmap
import android.net.Uri
import android.text.format.DateUtils
import android.util.LruCache
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryAdapter(
    private val scope: CoroutineScope,
    private val onOpen: (History.Item) -> Unit,
    private val onMenu: (History.Item, View) -> Unit,
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private var items: List<History.Item> = emptyList()
    private val thumbs = LruCache<String, Bitmap>(80)

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.thumb)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val more: View = v.findViewById(R.id.more)
    }

    fun submit(list: List<History.Item>) {
        items = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_download, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.title.text = item.title.ifBlank { item.name }
        val ago = DateUtils.getRelativeTimeSpanString(item.ts, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        h.sub.text = listOf(item.platform, item.author, ago.toString()).filter { it.isNotBlank() }.joinToString("  ·  ")
        h.itemView.setOnClickListener { onOpen(item) }
        h.itemView.setOnLongClickListener { onMenu(item, h.more); true }
        h.more.setOnClickListener { onMenu(item, it) }

        h.thumb.tag = item.uri
        val cached = thumbs.get(item.uri)
        if (cached != null) {
            h.thumb.setImageBitmap(cached)
            return
        }
        h.thumb.setImageResource(R.drawable.ic_thumb_placeholder)
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                runCatching { h.itemView.context.contentResolver.loadThumbnail(Uri.parse(item.uri), Size(240, 240), null) }.getOrNull()
            } ?: return@launch
            thumbs.put(item.uri, bmp)
            if (h.thumb.tag == item.uri) h.thumb.setImageBitmap(bmp)
        }
    }
}
