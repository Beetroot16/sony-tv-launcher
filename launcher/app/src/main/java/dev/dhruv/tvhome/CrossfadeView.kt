package dev.dhruv.tvhome

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView

/** Two stacked ImageViews; [show] configures the hidden one and crossfades it in. */
class CrossfadeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val layers = arrayOf(ImageView(context), ImageView(context))
    private var front = 0

    init {
        for (iv in layers) {
            iv.alpha = 0f
            addView(iv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
    }

    fun show(configure: (ImageView) -> Unit) {
        val current = layers[front]
        val next = layers[1 - front]
        current.animate().cancel()
        next.animate().cancel()
        configure(next)
        next.bringToFront()
        next.alpha = 0f
        next.animate().alpha(1f).setDuration(FADE_MS).start()
        current.animate().alpha(0f).setDuration(FADE_MS).start()
        front = 1 - front
    }

    private companion object {
        const val FADE_MS = 250L
    }
}
