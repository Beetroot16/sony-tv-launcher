package dev.dhruv.tvhome

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable

class AppEntry(
    val key: String,
    val component: ComponentName,
    val label: String,
    val banner: Drawable?,
    val icon: Drawable,
    val color: Int,
)

/** Leanback apps on the device plus the persisted top-row order. */
class AppRepository(private val context: Context) {

    private val pm: PackageManager = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)
    private val cache = HashMap<String, AppEntry>()

    fun invalidate() = cache.clear()

    @Suppress("DEPRECATION")
    fun loadApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map { ri ->
                val ai = ri.activityInfo
                val cn = ComponentName(ai.packageName, ai.name)
                val key = cn.flattenToShortString()
                cache.getOrPut(key) {
                    val icon = ai.loadIcon(pm)
                    AppEntry(key, cn, ri.loadLabel(pm).toString(), ai.loadBanner(pm), icon, cardColorFor(icon))
                }
            }
            .distinctBy { it.key }
    }

    /** Top-row keys in display order, limited to installed apps. */
    fun pinnedKeys(apps: List<AppEntry>): MutableList<String> {
        val installed = apps.mapTo(HashSet()) { it.key }
        val saved = prefs.getString(KEY_PINS, null)
        val keys = saved?.split('\n')?.filter { it.isNotEmpty() }
            ?: DEFAULT_PACKAGES.mapNotNull { pkg -> apps.firstOrNull { it.component.packageName == pkg }?.key }
        return keys.filterTo(ArrayList()) { it in installed }.distinct().toMutableList()
    }

    fun savePinned(keys: List<String>) = prefs.edit().putString(KEY_PINS, keys.joinToString("\n")).apply()

    fun resetPinned() = prefs.edit().remove(KEY_PINS).apply()

    var soundsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUNDS, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUNDS, value).apply()

    private fun cardColorFor(icon: Drawable): Int {
        val size = 12
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val copy = icon.constantState?.newDrawable(context.resources)?.mutate() ?: return DEFAULT_CARD_COLOR
        copy.setBounds(0, 0, size, size)
        copy.draw(Canvas(bmp))
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0L
        for (y in 0 until size) for (x in 0 until size) {
            val p = bmp.getPixel(x, y)
            if (Color.alpha(p) < 200) continue
            r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++
        }
        bmp.recycle()
        if (n == 0L) return DEFAULT_CARD_COLOR
        val hsv = FloatArray(3)
        Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = hsv[1].coerceAtMost(0.7f)
        hsv[2] = (hsv[2] * 0.5f).coerceIn(0.18f, 0.42f)
        return Color.HSVToColor(hsv)
    }

    companion object {
        private const val KEY_PINS = "pins"
        private const val KEY_SOUNDS = "sounds"

        val DEFAULT_PACKAGES = listOf(
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "in.startv.hotstar",
            "com.sonyliv",
            "com.graymatrix.did",
            "com.apple.atve.sony.appletv",
            "com.crunchyroll.crunchyroid",
            "com.mubi",
            "com.mxtech.videoplayer.television",
            "com.mxtech.videoplayer.ad",
        )
    }
}
