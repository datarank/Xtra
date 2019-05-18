package com.github.exact7.xtra.ui.common

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.crashlytics.android.Crashlytics
import com.github.exact7.xtra.GlideApp
import com.github.exact7.xtra.R
import com.github.exact7.xtra.model.chat.BttvEmote
import com.github.exact7.xtra.model.chat.ChatMessage
import com.github.exact7.xtra.model.chat.Emote
import com.github.exact7.xtra.model.chat.FfzEmote
import com.github.exact7.xtra.model.chat.Image
import java.util.Random
import kotlin.collections.set
import kotlin.math.min

const val EMOTES_URL = "https://static-cdn.jtvnw.net/emoticons/v1/"
const val BTTV_URL = "https://cdn.betterttv.net/emote/"

class ChatAdapter(private val emoteSize: Int, private val badgeSize: Int) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    var messages: MutableList<ChatMessage>? = null
        set(value) {
            val oldSize = field?.size ?: 0
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            field = value
        }
    private val twitchColors = intArrayOf(-65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096, -2987746, -10510688, -14774017, -38476, -7722014, -16711809)
    private val random = Random()
    private val userColors = HashMap<String, Int>()
    private val savedColors = HashMap<String, Int>()
    private val emotes = HashMap<String, Emote>()
    private var username: String? = null
    private val scaledEmoteSize = (emoteSize * 0.78f).toInt()

    private var messageClickListener: ((CharSequence, CharSequence) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_list_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val builder = SpannableStringBuilder()
        val chatMessage = messages?.get(position) ?: return
        val badgesUrl = "https://static-cdn.jtvnw.net/chat-badges/"
        val images = ArrayList<Image>()
        var index = 0
        chatMessage.badges?.forEach { (id, version) ->
            val url: String? = when (id) {
                "admin" -> badgesUrl + "admin.png"
                "bits" -> {
                    val count = version.toInt()
                    val color = when {
                        count < 100 -> "gray"
                        count < 1000 -> "purple"
                        count < 5000 -> "green"
                        count < 10000 -> "blue"
                        else -> "red"
                    }
                    "https://static-cdn.jtvnw.net/bits/dark/static/$color/2" //TODO change theme based on app theme
                }
                "broadcaster" -> badgesUrl + "broadcaster.png"
                "global_mod" -> badgesUrl + "globalmod.png"
                "moderator" -> badgesUrl + "mod.png"
                "subscriber" -> chatMessage.subscriberBadge?.imageUrl2x
                "staff" -> badgesUrl + "staff.png"
                "turbo" -> badgesUrl + "turbo.png"
                "sub-gifter" -> "https://static-cdn.jtvnw.net/badges/v1/4592e9ea-b4ca-4948-93b8-37ac198c0433/2"
                "premium" -> "https://static-cdn.jtvnw.net/badges/v1/a1dd5073-19c3-4911-8cb4-c464a7bc1510/2"
                "partner" -> "https://static-cdn.jtvnw.net/badges/v1/d12a2e27-16f6-41d0-ab77-b780518f00a3/2"
                "clip-champ" -> "https://static-cdn.jtvnw.net/badges/v1/f38976e0-ffc9-11e7-86d6-7f98b26a9d79/2"
                else -> null
            }
            url?.let {
                builder.append("  ")
                images.add(Image(url, index++, index++, false))
            }
        }
        val userName = chatMessage.displayName
        builder.append(userName).append(": ").append(chatMessage.message)
        val color = chatMessage.color.let { userColor ->
            if (userColor == null) {
                userColors[userName] ?: getRandomColor().also { userColors[userName] = it }
            } else {
                savedColors[userColor] ?: Color.parseColor(userColor).also { savedColors[userColor] = it }
            }
        }
        val userNameLength = userName.length
        builder.setSpan(ForegroundColorSpan(color), index, index + userNameLength, SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(StyleSpan(Typeface.BOLD), index, index + userNameLength, SPAN_EXCLUSIVE_EXCLUSIVE)
        val originalMessage = "$userName: ${chatMessage.message}"
        try {
            chatMessage.emotes?.let {
                val copy = it.map { e -> e.copy() }
                index += userNameLength + 2
                for (e in copy) {
                    val begin = index + e.begin
                    builder.replace(begin, index + e.end + 1, ".") //TODO emojis break this
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), begin, begin + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    val length = e.end - e.begin
                    for (e1 in copy) {
                        if (e.begin < e1.begin) {
                            e1.begin -= length
                            e1.end -= length
                        }
                    }
                    e.end -= length
                }
                copy.forEach { (id, begin, end) -> images.add(Image("$EMOTES_URL$id/2.0", index + begin, index + end + 1, true)) }
            }
            val split = builder.split(" ")
            var builderIndex = 0
            for (i in 0 until split.size) {
                val value = split[i]
                val length = value.length
                val endIndex = builderIndex + length
                val emote = emotes[value]
                builderIndex += if (emote == null) {
                    if (Patterns.WEB_URL.matcher(value).matches()) {
                        var url = value
                        if (!value.startsWith("http")) {
                            url = "https://$url"
                        }
                        builder.setSpan(URLSpan(url), builderIndex, endIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else {
                        if (value.startsWith('@')) {
                            builder.setSpan(StyleSpan(Typeface.BOLD), builderIndex, endIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        username?.let {
                            if (value.contains(it, true) && !value.endsWith(':')) {
                                builder.setSpan(BackgroundColorSpan(Color.RED), 0, builder.length, SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                    }
                    length + 1
                } else {
                    chatMessage.emotes?.let {
                        for (j in it.size - 1 downTo 0) {
                            val e = images[j]
                            if (e.start > builderIndex) {
                                e.start -= length
                                e.end -= length
                            } else {
                                break
                            }
                        }
                    }
                    builder.replace(builderIndex, endIndex, ".")
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    val url: String
                    val isPng: Boolean
                    if (emote is BttvEmote) {
                        url = "$BTTV_URL${emote.id}/2x"
                        isPng = emote.isPng
                    } else { //FFZ
                        (emote as FfzEmote).also {
                            url = it.url
                            isPng = true
                        }
                    }
                    images.add(Image(url, builderIndex, builderIndex + 1, true, isPng))
                    if (i != split.lastIndex) 2 else 1
                }
            }
            loadImages(holder, images, originalMessage, builder)
        } catch (e: Exception) {
            Crashlytics.logException(e)
        }
        holder.bind(originalMessage, builder)
    }

    override fun getItemCount(): Int = messages?.size ?: 0

    private fun loadImages(holder: ViewHolder, images: List<Image>, originalMessage: CharSequence, builder: SpannableStringBuilder) {
        val context = holder.itemView.context
        images.forEach { (url, start, end, isEmote, isPng) ->
            if (isPng) {
                GlideApp.with(context)
                        .load(url)
                        .into(object : CustomTarget<Drawable>() {
                            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                val width: Int
                                val height: Int
                                if (isEmote) {
                                    val size = calculateEmoteSize(resource)
                                    width = size.first
                                    height = size.second
                                } else {
                                    width = badgeSize
                                    height = badgeSize
                                }
                                resource.setBounds(0, 0, width, height)
                                try {
                                    builder.setSpan(ImageSpan(resource), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                                } catch (e: IndexOutOfBoundsException) {

                                }
                                holder.bind(originalMessage, builder)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
            } else {
                GlideApp.with(context)
                        .asGif()
                        .load(url)
                        .into(object : CustomTarget<GifDrawable>() {
                            override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                                val textView = holder.itemView as TextView
                                val callback = object : Drawable.Callback {
                                    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                        textView.removeCallbacks(what)
                                    }

                                    override fun invalidateDrawable(who: Drawable) {
                                        textView.invalidate()
                                    }

                                    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                        textView.postDelayed(what, `when`)
                                    }
                                }
                                resource.apply {
                                    val size = calculateEmoteSize(this)
                                    setBounds(0, 0, size.first, size.second)
                                    setLoopCount(GifDrawable.LOOP_FOREVER)
                                    this.callback = callback
                                    start()
                                }
                                try {
                                    builder.setSpan(ImageSpan(resource), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                                } catch (e: IndexOutOfBoundsException) {

                                }
                                holder.bind(originalMessage, builder)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
            }
        }
    }

    fun addEmotes(list: List<Emote>) {
        emotes.putAll(list.associateBy { it.name })
    }

    fun setUsername(username: String) {
        this.username = username
    }

    fun setOnClickListener(listener: (CharSequence, CharSequence) -> Unit) {
        messageClickListener = listener
    }

    private fun getRandomColor(): Int = twitchColors[random.nextInt(twitchColors.size)]

    private fun calculateEmoteSize(resource: Drawable): Pair<Int, Int> {
        val intrinsicWidth = resource.intrinsicWidth.toFloat()
        val intrinsicHeight = resource.intrinsicHeight.toFloat()
        val widthRatio = intrinsicWidth / intrinsicHeight
        val width: Int
        val height: Int
        when {
            widthRatio == 1f -> {
                width = emoteSize
                height = emoteSize
            }
            widthRatio in 0.8f..1.2f -> {
                width = (emoteSize * widthRatio).toInt()
                height = emoteSize
            }
            widthRatio > 1.2f -> {
                width = (scaledEmoteSize * widthRatio).toInt()
                height = scaledEmoteSize
            }
            else -> {
                width = scaledEmoteSize
                height = (scaledEmoteSize * min(intrinsicHeight / intrinsicWidth, 1.5f)).toInt()
            }
        }
        return width to height
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        fun bind(originalMessage: CharSequence, formattedMessage: SpannableStringBuilder) {
            (itemView as TextView).apply {
                text = formattedMessage
                movementMethod = LinkMovementMethod.getInstance()
                setOnClickListener { messageClickListener?.invoke(originalMessage, formattedMessage) }
            }
        }
    }
}
