package com.github.andreyasadchy.xtra.ui.common

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.text.getSpans
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.integration.webp.decoder.WebpDrawable
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.github.andreyasadchy.xtra.GlideApp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Emote
import com.github.andreyasadchy.xtra.model.chat.Image
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import okhttp3.*
import java.io.IOException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set


class ChatAdapter(
        private val fragment: Fragment,
        private val emoteSize: Int,
        private val badgeSize: Int,
        private val randomColor: Boolean,
        private val boldNames: Boolean,
        private val badgeQuality: Int,
        private val gifs: Boolean,
        private val gifs2: Boolean) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    var messages: MutableList<ChatMessage>? = null
        set(value) {
            val oldSize = field?.size ?: 0
            if (oldSize > 0) {
                notifyItemRangeRemoved(0, oldSize)
            }
            field = value
        }
    private val twitchColors = intArrayOf(-65536, -16776961, -16744448, -5103070, -32944, -6632142, -47872, -13726889, -2448096, -2987746, -10510688, -14774017, -38476, -7722014, -16711809)
    private val noColor = -10066329
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
        val chatMessage = messages?.get(position) ?: return
        val builder = SpannableStringBuilder()
        val images = ArrayList<Image>()
        var index = 0
        var badgesCount = 0
        chatMessage.badges?.forEach { (id, version) ->
            val url: String? = when (id) {
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
                "broadcaster" -> BADGES_URL + "5527c58c-fb7d-422d-b71b-f309dcb85cc1/" + badgeQuality
                "moderator" -> BADGES_URL + "3267646d-33f0-4b17-b3df-f923a41db1d0/" + badgeQuality
                "vip" -> BADGES_URL + "b817aba4-fad8-49e2-b88a-7cc744dfa6ec/" + badgeQuality
                "subscriber" -> when (badgeQuality) {3 -> (chatMessage.subscriberBadge?.imageUrl4x) 2 -> (chatMessage.subscriberBadge?.imageUrl2x) else -> (chatMessage.subscriberBadge?.imageUrl1x)}
                "sub-gifter" -> BADGES_URL + "f1d8486f-eb2e-4553-b44f-4d614617afc1/" + badgeQuality
                "staff" -> BADGES_URL + "d97c37bd-a6f5-4c38-8f57-4e4bef88af34/" + badgeQuality
                "admin" -> BADGES_URL + "9ef7e029-4cdf-4d4d-a0d5-e2b3fb2583fe/" + badgeQuality
                "global_mod" -> BADGES_URL + "9384c43e-4ce7-4e94-b2a1-b93656896eba/" + badgeQuality
                "turbo" -> BADGES_URL + "bd444ec6-8f34-4bf9-91f4-af1e3428d80f/" + badgeQuality
                "premium" -> BADGES_URL + "bbbe0db0-a598-423e-86d0-f9fb98ca1933/" + badgeQuality
                "partner" -> BADGES_URL + "d12a2e27-16f6-41d0-ab77-b780518f00a3/" + badgeQuality
                else -> null
            }
            url?.let {
                builder.append("  ")
                images.add(Image(url, index++, index++, false))
                badgesCount++
            }
        }
        val userName = chatMessage.displayName
        val userNameLength = userName.length
        val userNameEndIndex = index + userNameLength
        val originalMessage: String
        val userNameWithPostfixLength: Int
        builder.append(userName)
        if (!chatMessage.isAction) {
            builder.append(": ")
            originalMessage = "$userName: ${chatMessage.message}"
            userNameWithPostfixLength = userNameLength + 2
        } else {
            builder.append(" ")
            originalMessage = "$userName ${chatMessage.message}"
            userNameWithPostfixLength = userNameLength + 1
        }
        builder.append(chatMessage.message)
        val color = chatMessage.color.let { userColor ->
            if (userColor == null) {
                userColors[userName] ?: getRandomColor().also { userColors[userName] = it }
            } else {
                savedColors[userColor]
                        ?: Color.parseColor(userColor).also { savedColors[userColor] = it }
            }
        }
        builder.setSpan(ForegroundColorSpan(color), index, userNameEndIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
        builder.setSpan(StyleSpan(if (boldNames) Typeface.BOLD else Typeface.NORMAL), index, userNameEndIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
        try {
            var emotename = ""
            var ispng = "image/png"
            chatMessage.emotes?.let { emotes ->
                val copy = emotes.map {
                    val realBegin = chatMessage.message.offsetByCodePoints(0, it.begin)
                    val realEnd = if (it.begin == realBegin) {
                        it.end
                    } else {
                        it.end + realBegin - it.begin
                    }
                    TwitchEmote(it.name, realBegin, realEnd)
                }
                index += userNameWithPostfixLength
                for (e in copy) {
                    emotename = e.name
                    val begin = index + e.begin
                    builder.replace(begin, index + e.end + 1, ".")
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
                if (gifs && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val emoteurl = "https://static-cdn.jtvnw.net/emoticons/v2/$emotename/default/dark/1.0"
                    val future = CallbackFuture()
                    OkHttpClient().newCall(Request.Builder().url(emoteurl).head().build()).enqueue(future)
                    val response = future.get()
                    ispng = response?.header("Content-Type") ?: "image/png"
                    response?.body()?.close()
                }
                if (gifs2) {
                    val emoteurl = "https://static-cdn.jtvnw.net/emoticons/v2/$emotename/default/dark/1.0"
                    val countDownLatch = CountDownLatch(1)
                    OkHttpClient().newCall(Request.Builder().url(emoteurl).head().build()).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            countDownLatch.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            try {
                                ispng = response.header("Content-Type").toString()
                                response.body()?.close()
                            } catch (e: java.lang.Exception) {
                                ispng = "image/png"
                                countDownLatch.countDown()
                            }
                            countDownLatch.countDown()
                        }
                    })
                    countDownLatch.await()
                }
                copy.forEach { images.add(Image(it.url, index + it.begin, index + it.end + 1, true, ispng)) }
            }
            val split = builder.split(" ")
            var builderIndex = 0
            var emotesFound = 0
            var wasMentioned = false
            for (value in split) {
                val length = value.length
                val endIndex = builderIndex + length
                val emote = emotes[value]
                builderIndex += if (emote == null) {
                    if (!Patterns.WEB_URL.matcher(value).matches()) {
                        if (value.startsWith('@')) {
                            builder.setSpan(StyleSpan(if (boldNames) Typeface.BOLD else Typeface.NORMAL), builderIndex, endIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        username?.let {
                            if (!wasMentioned && value.contains(it, true) && chatMessage.userName != it) {
                                wasMentioned = true
                            }
                        }
                    } else {
                        val url = if (value.startsWith("http")) value else "https://$value"
                        builder.setSpan(URLSpan(url), builderIndex, endIndex, SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    length + 1
                } else {
                    for (j in images.lastIndex - emotesFound downTo badgesCount) {
                        val e = images[j]
                        if (e.start > builderIndex) {
                            val remove = length - 1
                            e.start -= remove
                            e.end -= remove
                        }
                    }
                    builder.replace(builderIndex, endIndex, ".")
                    builder.setSpan(ForegroundColorSpan(Color.TRANSPARENT), builderIndex, builderIndex + 1, SPAN_EXCLUSIVE_EXCLUSIVE)
                    images.add(Image(emote.url, builderIndex, builderIndex + 1, true, emote.isPng))
                    emotesFound++
                    2
                }
            }
            if (chatMessage.isAction) {
                builder.setSpan(ForegroundColorSpan(color), userNameEndIndex + 1, builder.length, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (wasMentioned) {
                builder.setSpan(ForegroundColorSpan(Color.WHITE), 0, builder.length, SPAN_INCLUSIVE_INCLUSIVE)
                holder.textView.setBackgroundColor(Color.RED)
            } else {
                holder.textView.background = null
            }
        } catch (e: Exception) {
//            Crashlytics.logException(e)
        }
        holder.bind(originalMessage, builder)
        loadImages(holder, images, originalMessage, builder)
    }

    override fun getItemCount(): Int = messages?.size ?: 0

    @RequiresApi(Build.VERSION_CODES.N)
    internal class CallbackFuture : CompletableFuture<Response?>(), Callback {
        override fun onResponse(call: Call?, response: Response?) {
            super.complete(response)
        }

        override fun onFailure(call: Call?, e: IOException?) {
            super.completeExceptionally(e)
        }
    }

    private fun loadImages(holder: ViewHolder, images: List<Image>, originalMessage: CharSequence, builder: SpannableStringBuilder) {
        images.forEach { (url, start, end, isEmote, isPng) ->
            when (isPng) {
                "image/webp" -> {
                    GlideApp.with(fragment)
                        .asWebp()
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .into(object : CustomTarget<WebpDrawable>() {
                            override fun onResourceReady(resource: WebpDrawable, transition: Transition<in WebpDrawable>?) {
                                if (resource.frameCount > 1) {
                                    anim(resource, transition)
                                } else {
                                    static()
                                }
                            }
                            fun anim(resource: WebpDrawable, transition: Transition<in WebpDrawable>?) {
                                resource.apply {
                                    val size = calculateEmoteSize(this)
                                    setBounds(0, 0, size.first, size.second)
                                    loopCount = WebpDrawable.LOOP_FOREVER
                                    callback = object : Drawable.Callback {
                                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                            holder.textView.removeCallbacks(what)
                                        }

                                        override fun invalidateDrawable(who: Drawable) {
                                            holder.textView.invalidate()
                                        }

                                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                            holder.textView.postDelayed(what, `when`)
                                        }
                                    }
                                    start()
                                }
                                try {
                                    builder.setSpan(ImageSpan(resource), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                                } catch (e: IndexOutOfBoundsException) {
//                                    Crashlytics.logException(e)
                                }
                                holder.bind(originalMessage, builder)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }

                            fun static() {
                                GlideApp.with(fragment)
                                    .load(url)
                                    .diskCacheStrategy(DiskCacheStrategy.DATA)
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
//                                    Crashlytics.logException(e)
                                            }
                                            holder.bind(originalMessage, builder)
                                        }

                                        override fun onLoadCleared(placeholder: Drawable?) {
                                        }
                                    })
                            }
                        })
                }
                "image/gif" -> {
                    GlideApp.with(fragment)
                        .asGif()
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .into(object : CustomTarget<GifDrawable>() {
                            override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                                resource.apply {
                                    val size = calculateEmoteSize(this)
                                    setBounds(0, 0, size.first, size.second)
                                    setLoopCount(GifDrawable.LOOP_FOREVER)
                                    callback = object : Drawable.Callback {
                                        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
                                            holder.textView.removeCallbacks(what)
                                        }

                                        override fun invalidateDrawable(who: Drawable) {
                                            holder.textView.invalidate()
                                        }

                                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
                                            holder.textView.postDelayed(what, `when`)
                                        }
                                    }
                                    start()
                                }
                                try {
                                    builder.setSpan(ImageSpan(resource), start, end, SPAN_EXCLUSIVE_EXCLUSIVE)
                                } catch (e: IndexOutOfBoundsException) {
//                                    Crashlytics.logException(e)
                                }
                                holder.bind(originalMessage, builder)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
                }
                else -> {
                    GlideApp.with(fragment)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
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
//                                    Crashlytics.logException(e)
                                }
                                holder.bind(originalMessage, builder)
                            }

                            override fun onLoadCleared(placeholder: Drawable?) {
                            }
                        })
                }
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

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
            (it.drawable as? GifDrawable)?.start()
            (it.drawable as? WebpDrawable)?.start()
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        (holder.textView.text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
            (it.drawable as? GifDrawable)?.stop()
            (it.drawable as? WebpDrawable)?.stop()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        val childCount = recyclerView.childCount
        for (i in 0 until childCount) {
            ((recyclerView.getChildAt(i) as TextView).text as? Spannable)?.getSpans<ImageSpan>()?.forEach {
                (it.drawable as? GifDrawable)?.stop()
                (it.drawable as? WebpDrawable)?.stop()
            }
        }
        super.onDetachedFromRecyclerView(recyclerView)
    }

    private fun getRandomColor(): Int =
        if (randomColor) {
            twitchColors[random.nextInt(twitchColors.size)]
        } else {
            noColor
        }

    private fun calculateEmoteSize(resource: Drawable): Pair<Int, Int> {
        val widthRatio = resource.intrinsicWidth.toFloat() / resource.intrinsicHeight.toFloat()
        val width: Int
        val height: Int
        when {
            widthRatio == 1f -> {
                width = emoteSize
                height = emoteSize
            }
            widthRatio <= 1.2f -> {
                width = (emoteSize * widthRatio).toInt()
                height = emoteSize
            }
            else -> {
                width = (scaledEmoteSize * widthRatio).toInt()
                height = scaledEmoteSize
            }
        }
        return width to height
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val textView = itemView as TextView

        fun bind(originalMessage: CharSequence, formattedMessage: SpannableStringBuilder) {
            textView.apply {
                text = formattedMessage
                movementMethod = LinkMovementMethod.getInstance()
                setOnClickListener { messageClickListener?.invoke(originalMessage, formattedMessage) }
            }
        }
    }

    private companion object {
        const val BADGES_URL = "https://static-cdn.jtvnw.net/badges/v1/"
    }
}
