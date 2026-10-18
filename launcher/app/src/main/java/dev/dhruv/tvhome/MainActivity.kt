package dev.dhruv.tvhome

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var repo: AppRepository
    private lateinit var sounds: Sounds

    private lateinit var content: ViewGroup
    private lateinit var rowsScroll: ScrollView
    private lateinit var watchHeader: View
    private lateinit var watchScroll: HorizontalScrollView
    private lateinit var watchRow: LinearLayout
    private lateinit var topScroll: HorizontalScrollView
    private lateinit var topRow: LinearLayout
    private lateinit var allHeader: View
    private lateinit var allScroll: HorizontalScrollView
    private lateinit var allRow: LinearLayout
    private lateinit var heroBg: CrossfadeView
    private lateinit var heroArt: CrossfadeView
    private lateinit var heroTitle: TextView
    private lateinit var heroSubtitle: TextView
    private lateinit var qsOverlay: View
    private lateinit var qsScrim: View
    private lateinit var qsPanel: View
    private lateinit var qsItems: LinearLayout

    private val worker = Executors.newSingleThreadExecutor()
    private var appsSignature = ""
    private var watchSignature = ""
    private var heroKey: String? = null
    private var lastFocusedKey: String? = null
    /** True while focus is moved programmatically: no sound for those moves. */
    private var restoring = false
    private var resumed = false
    private var qsOpen = false
    private var dialog: AlertDialog? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            repo.invalidate()
            refreshApps(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        content = findViewById(R.id.content)
        rowsScroll = findViewById(R.id.rows_scroll)
        watchHeader = findViewById(R.id.watch_header)
        watchScroll = findViewById(R.id.watch_scroll)
        watchRow = findViewById(R.id.watch_row)
        topScroll = findViewById(R.id.top_scroll)
        topRow = findViewById(R.id.top_row)
        allHeader = findViewById(R.id.all_header)
        allScroll = findViewById(R.id.all_scroll)
        allRow = findViewById(R.id.all_row)
        heroBg = findViewById(R.id.hero_bg)
        heroArt = findViewById(R.id.hero_art)
        heroTitle = findViewById(R.id.hero_title)
        heroSubtitle = findViewById(R.id.hero_subtitle)
        qsOverlay = findViewById(R.id.qs_overlay)
        qsScrim = findViewById(R.id.qs_scrim)
        qsPanel = findViewById(R.id.qs_panel)
        qsItems = findViewById(R.id.qs_items)

        repo = AppRepository(this)
        sounds = Sounds(this, repo.soundsEnabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            heroBg.setRenderEffect(RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP))
        }
        qsScrim.setOnClickListener { closeQuickSettings(true) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(packageReceiver, filter)
        }

        refreshApps(force = true)
        focusFirst()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        refreshApps(force = false)
        refreshWatchNext()
        if (currentFocus == null && !qsOpen) focusFirst()
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        dialog?.dismiss()
        sounds.release()
        worker.shutdownNow()
        super.onDestroy()
    }

    /** HOME pressed while already home: go back to the start of the top row. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!resumed) return
        dialog?.dismiss()
        closeQuickSettings(false)
        rowsScroll.smoothScrollTo(0, 0)
        topScroll.smoothScrollTo(0, 0)
        focusFirst()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && currentFocus == null && !qsOpen) focusFirst()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            // Home screen: Back never leaves; it only closes the quick settings panel.
            KeyEvent.KEYCODE_BACK -> {
                if (event.action == KeyEvent.ACTION_UP && qsOpen) closeQuickSettings(true)
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                    if (qsOpen) closeQuickSettings(true) else openQuickSettings()
                }
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ---- Rows -------------------------------------------------------------------------------

    private fun refreshApps(force: Boolean) {
        val apps = repo.loadApps()
        val pinned = repo.pinnedKeys(apps)
        val signature = pinned.joinToString(",") + "|" + apps.joinToString(",") { it.key + "=" + it.label }
        if (!force && signature == appsSignature) return
        appsSignature = signature

        val focusKey = (currentFocus as? TileView)?.key ?: lastFocusedKey
        val byKey = apps.associateBy { it.key }
        restoring = true
        topRow.removeAllViews()
        for (key in pinned) byKey[key]?.let { topRow.addView(appTile(it, big = true, pinned = true), tileParams()) }
        inputsTile()?.let { topRow.addView(it, tileParams()) }
        topRow.addView(settingsTile(), tileParams())

        allRow.removeAllViews()
        apps.filter { it.key !in pinned }
            .sortedBy { it.label.lowercase() }
            .forEach { allRow.addView(appTile(it, big = false, pinned = false), tileParams()) }
        val allVisibility = if (allRow.childCount == 0) View.GONE else View.VISIBLE
        allHeader.visibility = allVisibility
        allScroll.visibility = allVisibility

        if (!qsOpen) restoreFocus(focusKey)
        restoring = false
    }

    private fun refreshWatchNext() {
        worker.execute {
            val items = WatchNext.query(applicationContext)
            runOnUiThread { if (!isDestroyed) renderWatchNext(items) }
        }
    }

    private fun renderWatchNext(items: List<WatchItem>) {
        val signature = items.joinToString("|") { "${it.id}:${it.title}:${it.subtitle}:${it.art}:${it.progress}" }
        if (signature == watchSignature) return
        watchSignature = signature

        val focusKey = (currentFocus as? TileView)?.key
        restoring = true
        watchRow.removeAllViews()
        for (w in items) {
            val t = TileView(this, "watch:${w.id}", dp(176), dp(99), 15f)
            t.title = w.title
            t.subtitle = listOfNotNull(getString(R.string.resume), w.subtitle).joinToString(" · ")
            t.setCardColor(0xFF1E222B.toInt())
            t.setProgress(w.progress)
            w.art?.let { uri ->
                ImageLoader.load(this, uri, t.image, dp(320)) {
                    t.showBanner(t.image.drawable)
                    if (t.isFocused) updateHero(t, force = true)
                }
            }
            t.setOnClickListener {
                sounds.select()
                launchWatchItem(w)
            }
            t.onFocus = ::onTileFocused
            watchRow.addView(t, tileParams())
        }
        val visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        watchHeader.visibility = visibility
        watchScroll.visibility = visibility

        if (!qsOpen && (currentFocus == null || focusKey?.startsWith("watch:") == true)) restoreFocus(focusKey)
        restoring = false
        // The new row above shifts the others down; keep the focused tile on screen.
        rowsScroll.post { currentFocus?.let { it.requestRectangleOnScreen(Rect(0, 0, it.width, it.height), false) } }
    }

    private fun appTile(entry: AppEntry, big: Boolean, pinned: Boolean): TileView {
        val t = if (big) {
            TileView(this, entry.key, dp(176), dp(99), 15f)
        } else {
            TileView(this, entry.key, dp(144), dp(81), 13f)
        }
        t.title = entry.label
        if (entry.banner != null) {
            t.setCardColor(0xFF000000.toInt())
            t.showBanner(entry.banner)
        } else {
            t.showIcon(entry.icon, entry.color)
        }
        t.setOnClickListener {
            sounds.select()
            launchApp(entry)
        }
        t.setOnLongClickListener {
            showAppMenu(entry, pinned)
            true
        }
        t.onFocus = ::onTileFocused
        return t
    }

    private fun inputsTile(): TileView? {
        val intent = launchIntentFor(SONY_INPUTS) ?: return null
        val t = TileView(this, "special:inputs", dp(176), dp(99), 15f)
        t.title = getString(R.string.inputs)
        t.subtitle = getString(R.string.inputs_subtitle)
        t.showIcon(getDrawable(R.drawable.ic_input), DEFAULT_CARD_COLOR)
        t.setOnClickListener {
            sounds.select()
            start(intent, t.title)
        }
        t.onFocus = ::onTileFocused
        return t
    }

    private fun settingsTile(): TileView {
        val t = TileView(this, "special:settings", dp(176), dp(99), 15f)
        t.title = getString(R.string.settings)
        t.subtitle = getString(R.string.settings_subtitle)
        t.showIcon(getDrawable(R.drawable.ic_settings), DEFAULT_CARD_COLOR)
        t.setOnClickListener {
            sounds.select()
            openQuickSettings()
        }
        t.onFocus = ::onTileFocused
        return t
    }

    private fun tileParams() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { marginEnd = dp(28) }

    private fun findTile(key: String?): TileView? {
        if (key == null) return null
        for (row in arrayOf(watchRow, topRow, allRow)) {
            for (i in 0 until row.childCount) {
                val v = row.getChildAt(i)
                if (v is TileView && v.key == key) return v
            }
        }
        return null
    }

    private fun restoreFocus(key: String?) {
        val target = findTile(key) ?: topRow.getChildAt(0) ?: return
        restoring = true
        target.requestFocus()
        restoring = false
    }

    private fun focusFirst() {
        restoring = true
        topRow.getChildAt(0)?.requestFocus()
        restoring = false
    }

    private fun onTileFocused(t: TileView) {
        lastFocusedKey = t.key
        if (!restoring) sounds.focus()
        updateHero(t, force = false)
    }

    // ---- Hero (top shelf) ----------------------------------------------------------------------

    private fun updateHero(t: TileView, force: Boolean) {
        if (!force && heroKey == t.key) return
        heroKey = t.key
        val art = t.image.drawable?.let(::copyOf)
        val color = t.cardColor
        val iconMode = t.iconMode
        heroArt.show { iv ->
            iv.background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(color)
            }
            iv.outlineProvider = ViewOutlineProvider.BACKGROUND
            iv.clipToOutline = true
            if (iconMode) {
                iv.scaleType = ImageView.ScaleType.FIT_CENTER
                val p = dp(44)
                iv.setPadding(p, p, p, p)
            } else {
                iv.scaleType = ImageView.ScaleType.CENTER_CROP
                iv.setPadding(0, 0, 0, 0)
            }
            iv.setImageDrawable(art)
        }
        val blurred = tinyBitmap(art, color, iconMode)
        heroBg.show { iv ->
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(blurred)
        }
        heroTitle.text = t.title
        val sub = t.subtitle
        heroSubtitle.text = sub
        heroSubtitle.visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        val text = findViewById<View>(R.id.hero_text)
        text.animate().cancel()
        text.alpha = 0f
        text.animate().alpha(1f).setDuration(250).start()
    }

    private fun copyOf(d: Drawable): Drawable = d.constantState?.newDrawable(resources)?.mutate() ?: d

    /** A tiny render of the art; scaled up full screen (plus RenderEffect on S+) it reads as a blur. */
    private fun tinyBitmap(art: Drawable?, color: Int, iconMode: Boolean): Bitmap {
        val w = 48
        val h = 27
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        art?.let(::copyOf)?.let {
            if (iconMode) it.setBounds(w / 2 - 7, h / 2 - 7, w / 2 + 7, h / 2 + 7) else it.setBounds(0, 0, w, h)
            it.draw(c)
        }
        return bmp
    }

    // ---- Launching --------------------------------------------------------------------------

    private fun launchApp(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        start(intent, entry.label)
    }

    private fun launchWatchItem(w: WatchItem) {
        val fromUri = w.intentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val intent = fromUri?.apply {
            selector = null
            flags = (flags and GRANT_FLAGS.inv()) or Intent.FLAG_ACTIVITY_NEW_TASK
        } ?: w.packageName?.let(::launchIntentFor)
        if (intent != null) start(intent, w.title) else toast(getString(R.string.cannot_open, w.title))
    }

    private fun launchIntentFor(pkg: String): Intent? =
        (packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun resolvable(intent: Intent): Intent? =
        if (intent.resolveActivity(packageManager) != null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else null

    private fun start(intent: Intent, name: String) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast(getString(R.string.cannot_open, name))
        } catch (e: SecurityException) {
            toast(getString(R.string.cannot_open, name))
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    // ---- Long-press menu --------------------------------------------------------------------

    private fun showAppMenu(entry: AppEntry, pinned: Boolean) {
        val pins = repo.pinnedKeys(repo.loadApps())
        val idx = pins.indexOf(entry.key)
        val options = ArrayList<Pair<String, () -> Unit>>()
        if (pinned && idx >= 0) {
            if (idx > 0) options += getString(R.string.move_left) to { movePin(pins, idx, idx - 1) }
            if (idx < pins.size - 1) options += getString(R.string.move_right) to { movePin(pins, idx, idx + 1) }
            options += getString(R.string.remove_from_top) to {
                pins.removeAt(idx)
                repo.savePinned(pins)
                refreshApps(force = true)
            }
        } else {
            options += getString(R.string.add_to_top) to {
                pins.add(entry.key)
                repo.savePinned(pins)
                refreshApps(force = true)
            }
        }
        options += getString(R.string.app_info) to {
            val uri = Uri.fromParts("package", entry.component.packageName, null)
            start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), entry.label)
        }
        dialog?.dismiss()
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(entry.label)
            .setItems(options.map { it.first }.toTypedArray()) { _, which -> options[which].second() }
            .show()
    }

    private fun movePin(pins: MutableList<String>, from: Int, to: Int) {
        val key = pins.removeAt(from)
        pins.add(to, key)
        repo.savePinned(pins)
        refreshApps(force = true)
    }

    // ---- Quick settings panel ---------------------------------------------------------------

    private fun openQuickSettings() {
        if (qsOpen) return
        qsOpen = true
        buildQuickSettings()
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        qsOverlay.visibility = View.VISIBLE
        qsScrim.alpha = 0f
        qsScrim.animate().alpha(1f).setDuration(PANEL_MS).start()
        qsPanel.translationX = dp(360).toFloat()
        qsPanel.animate().translationX(0f).setDuration(PANEL_MS).setInterpolator(DecelerateInterpolator()).start()
        restoring = true
        for (i in 0 until qsItems.childCount) {
            val v = qsItems.getChildAt(i)
            if (v.isFocusable && v.requestFocus()) break
        }
        restoring = false
    }

    private fun closeQuickSettings(animate: Boolean) {
        if (!qsOpen) return
        qsOpen = false
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        restoreFocus(lastFocusedKey)
        if (animate) {
            qsScrim.animate().alpha(0f).setDuration(PANEL_MS).start()
            qsPanel.animate().translationX(dp(360).toFloat()).setDuration(PANEL_MS)
                .withEndAction { if (!qsOpen) qsOverlay.visibility = View.GONE }
                .start()
        } else {
            qsPanel.animate().cancel()
            qsScrim.animate().cancel()
            qsOverlay.visibility = View.GONE
        }
    }

    private fun buildQuickSettings() {
        qsItems.removeAllViews()
        qsItems.addView(qsHeader(getString(R.string.qs_title), 26f, top = 0))

        fun addIntent(labelRes: Int, intent: Intent?) {
            if (intent == null) return
            val label = getString(labelRes)
            qsItems.addView(qsItem(label) {
                closeQuickSettings(false)
                start(intent, label)
            })
        }
        addIntent(R.string.qs_wifi, resolvable(Intent(Settings.ACTION_WIFI_SETTINGS)))
        addIntent(R.string.qs_bluetooth, resolvable(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)))
        addIntent(R.string.qs_sound, launchIntentFor(SONY_AUDIO_OUTPUT) ?: resolvable(Intent(Settings.ACTION_SOUND_SETTINGS)))
        addIntent(R.string.qs_picture, resolvable(Intent(Settings.ACTION_DISPLAY_SETTINGS)))
        addIntent(R.string.qs_sleep, launchIntentFor(SONY_TIMERS))
        addIntent(R.string.qs_inputs, launchIntentFor(SONY_INPUTS))
        addIntent(R.string.qs_all_settings, resolvable(Intent(Settings.ACTION_SETTINGS)))

        qsItems.addView(qsHeader(getString(R.string.qs_launcher), 15f, top = dp(24)))
        lateinit var soundItem: TextView
        soundItem = qsItem(soundsLabel()) {
            val on = !repo.soundsEnabled
            repo.soundsEnabled = on
            sounds.enabled = on
            soundItem.text = soundsLabel()
            sounds.select()
        }
        qsItems.addView(soundItem)
        qsItems.addView(qsItem(getString(R.string.qs_reset)) {
            repo.resetPinned()
            refreshApps(force = true)
            toast(getString(R.string.qs_reset_done))
        })
        qsItems.addView(TextView(this).apply {
            text = getString(R.string.qs_hint)
            setTextColor(getColor(R.color.text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(dp(16), dp(12), dp(16), 0)
        })
    }

    private fun soundsLabel() = getString(if (repo.soundsEnabled) R.string.qs_sounds_on else R.string.qs_sounds_off)

    private fun qsHeader(text: String, sp: Float, top: Int) = TextView(this).apply {
        this.text = text
        setTextColor(if (sp > 20f) 0xFFFFFFFF.toInt() else getColor(R.color.text_secondary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setPadding(dp(16), top, dp(16), dp(10))
    }

    private fun qsItem(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(0xFFFFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        isFocusable = true
        isClickable = true
        background = StateListDrawable().apply {
            addState(
                intArrayOf(android.R.attr.state_focused),
                GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(0x33FFFFFF)
                },
            )
        }
        setOnFocusChangeListener { _, hasFocus -> if (hasFocus && !restoring) sounds.focus() }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val SONY_INPUTS = "com.sony.dtv.tvx"
        const val SONY_AUDIO_OUTPUT = "com.sony.dtv.audiooutput"
        const val SONY_TIMERS = "com.sony.dtv.timers"
        const val PANEL_MS = 220L
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
    }
}
