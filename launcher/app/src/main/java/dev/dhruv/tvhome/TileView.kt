package dev.dhruv.tvhome

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A rounded card with a label underneath. When focused the card scales to 1.1x, gains a soft
 * white glow (light-coloured elevation shadow) and a thin outline, and the label fades in.
 */
@SuppressLint("ViewConstructor")
class TileView(
    context: Context,
    val key: String,
    private val cardWidth: Int,
    private val cardHeight: Int,
    labelSp: Float,
) : LinearLayout(context) {

    val card = FrameLayout(context)
    val image = ImageView(context)
    private val label = TextView(context)
    private val cardBg = GradientDrawable()
    private val stroke = GradientDrawable()

    var title: String = ""
        set(value) {
            field = value
            label.text = value
            contentDescription = value
        }
    var subtitle: String? = null
    var onFocus: ((TileView) -> Unit)? = null

    var cardColor: Int = DEFAULT_CARD_COLOR
        private set
    var iconMode = false
        private set

    private var level = 0f
    private var anim: ValueAnimator? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        isFocusable = true
        isClickable = true
        isLongClickable = true
        clipChildren = false
        clipToPadding = false

        val radius = dp(12).toFloat()
        cardBg.cornerRadius = radius
        cardBg.setColor(cardColor)
        card.background = cardBg
        card.outlineProvider = ViewOutlineProvider.BACKGROUND
        card.clipToOutline = true
        stroke.cornerRadius = radius
        stroke.setStroke(dp(2), 0xB3FFFFFF.toInt())
        stroke.alpha = 0
        card.foreground = stroke
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            card.outlineSpotShadowColor = 0xFFFFFFFF.toInt()
            card.outlineAmbientShadowColor = 0xFFFFFFFF.toInt()
        }
        card.addView(image, FrameLayout.LayoutParams(MATCH, MATCH))
        addView(card, LayoutParams(cardWidth, cardHeight))

        label.setTextColor(0xFFFFFFFF.toInt())
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelSp)
        label.isSingleLine = true
        label.ellipsize = TextUtils.TruncateAt.END
        label.gravity = Gravity.CENTER_HORIZONTAL
        label.alpha = 0f
        addView(label, LayoutParams(cardWidth, WRAP).apply { topMargin = dp(14) })
    }

    fun showBanner(d: Drawable?) {
        iconMode = false
        image.scaleType = ImageView.ScaleType.CENTER_CROP
        image.setPadding(0, 0, 0, 0)
        image.setImageDrawable(d)
    }

    fun showIcon(d: Drawable?, color: Int) {
        iconMode = true
        setCardColor(color)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        val v = cardHeight / 4
        image.setPadding(v, v, v, v)
        image.setImageDrawable(d)
    }

    fun setCardColor(color: Int) {
        cardColor = color
        cardBg.setColor(color)
    }

    /** Thin playback progress bar along the bottom of the card. */
    fun setProgress(fraction: Float) {
        if (fraction <= 0f) return
        val h = dp(4)
        val track = View(context).apply { setBackgroundColor(0x66000000) }
        val fill = View(context).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            pivotX = 0f
            scaleX = fraction.coerceIn(0f, 1f)
        }
        card.addView(track, FrameLayout.LayoutParams(MATCH, h, Gravity.BOTTOM))
        card.addView(fill, FrameLayout.LayoutParams(MATCH, h, Gravity.BOTTOM))
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        animateTo(if (gainFocus) 1f else 0f)
        if (gainFocus) onFocus?.invoke(this)
    }

    private fun animateTo(target: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(level, target).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener { render(it.animatedValue as Float) }
            start()
        }
    }

    private fun render(v: Float) {
        level = v
        val s = 1f + 0.1f * v
        card.scaleX = s
        card.scaleY = s
        card.translationZ = v * dp(14)
        stroke.alpha = (v * 255).toInt()
        label.alpha = v
    }

    private companion object {
        const val MATCH = LayoutParams.MATCH_PARENT
        const val WRAP = LayoutParams.WRAP_CONTENT
    }
}

const val DEFAULT_CARD_COLOR = 0xFF2B3240.toInt()

fun View.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
