package com.glasshome.tv

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppEntry(val pkg: String, val label: String, val icon: Drawable)

class Scene(val name: String, val colors: IntArray)

private val SCENES = listOf(
    Scene("Тропический рассвет", intArrayOf(
        0xFF3B82C4.toInt(), 0xFFF3C58B.toInt(), 0xFFE8975A.toInt(), 0xFF2F6B4A.toInt(), 0xFF1F4D3A.toInt())),
    Scene("Лунное озеро", intArrayOf(
        0xFF050B24.toInt(), 0xFF0F1F4D.toInt(), 0xFF1D3A73.toInt(), 0xFF0A1530.toInt())),
    Scene("Дюны", intArrayOf(
        0xFF2D1B4E.toInt(), 0xFFB8447A.toInt(), 0xFFF0925A.toInt(), 0xFF8A4B2D.toInt())),
    Scene("Северное сияние", intArrayOf(
        0xFF04121F.toInt(), 0xFF0B4F5C.toInt(), 0xFF1AA37A.toInt(), 0xFF0A2540.toInt())),
    Scene("Золотой берег", intArrayOf(
        0xFF1E4D8C.toInt(), 0xFF6FA8DC.toInt(), 0xFFFFD27F.toInt(), 0xFFC98A3A.toInt()))
)

class MainActivity : Activity() {

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var prefs: SharedPreferences
    private lateinit var root: FrameLayout
    private lateinit var bg: ImageView
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var dateView: TextView
    private lateinit var dock: LinearLayout
    private lateinit var tip: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFmt = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))

    private val ticker = object : Runnable {
        override fun run() {
            updateClock()
            handler.postDelayed(this, 1000)
        }
    }

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun tv(sizeSp: Float, bold: Boolean = false): TextView {
        val t = TextView(this)
        t.setTextColor(Color.WHITE)
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD
        t.setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), 0x66000000)
        return t
    }

    private fun glass(radius: Int, fill: Int): GradientDrawable {
        val d = GradientDrawable()
        d.cornerRadius = radius.toFloat()
        d.setColor(fill)
        d.setStroke(dp(1), 0x66FFFFFF)
        return d
    }

    private fun focusBg(radius: Int): StateListDrawable {
        val focused = GradientDrawable()
        focused.cornerRadius = radius.toFloat()
        focused.setColor(0x55FFFFFF)
        focused.setStroke(dp(2), Color.WHITE)
        val normal = GradientDrawable()
        normal.cornerRadius = radius.toFloat()
        normal.setColor(0x00000000)
        val s = StateListDrawable()
        s.addState(intArrayOf(android.R.attr.state_focused), focused)
        s.addState(intArrayOf(), normal)
        return s
    }

    // ---------- lifecycle ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("glasshome", MODE_PRIVATE)
        getExternalFilesDir(null) // creates the folder for a custom wallpaper

        root = FrameLayout(this)

        bg = ImageView(this)
        bg.scaleType = ImageView.ScaleType.CENTER_CROP
        root.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))

        val shade = View(this)
        shade.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x00000000, 0x80000000.toInt())
        )
        root.addView(shade, FrameLayout.LayoutParams(MATCH, dp(320), Gravity.BOTTOM))

        val top = LinearLayout(this)
        top.orientation = LinearLayout.VERTICAL
        top.gravity = Gravity.CENTER_HORIZONTAL
        greeting = tv(22f)
        clock = tv(88f)
        clock.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        dateView = tv(20f)
        top.addView(greeting)
        top.addView(clock)
        top.addView(dateView)
        val topLp = FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP)
        topLp.topMargin = dp(44)
        root.addView(top, topLp)

        dock = LinearLayout(this)
        dock.orientation = LinearLayout.HORIZONTAL
        dock.gravity = Gravity.CENTER
        dock.setPadding(dp(14), dp(8), dp(14), dp(8))
        dock.background = glass(dp(30), 0x44FFFFFF)
        val dockLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        dockLp.bottomMargin = dp(44)
        root.addView(dock, dockLp)

        tip = tv(15f)
        tip.setPadding(dp(14), dp(6), dp(14), dp(6))
        tip.background = glass(dp(10), 0xAA1B2333.toInt())
        tip.visibility = View.INVISIBLE
        val tipLp = FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        tipLp.bottomMargin = dp(44 + 88 + 10)
        root.addView(tip, tipLp)

        setContentView(root)
        applyScene()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (overlay == null) {
            rebuildDock()
            if (currentFocus == null) dock.getChildAt(0)?.requestFocus()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while we are already on top
        if (overlay != null) closeOverlay()
        dock.getChildAt(0)?.requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (overlay != null) closeOverlay()
            return true // a launcher never "goes back"
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- clock ----------

    private fun updateClock() {
        val now = Date()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val g = when (hour) {
            in 5..11 -> "Доброе утро"
            in 12..17 -> "Добрый день"
            in 18..22 -> "Добрый вечер"
            else -> "Доброй ночи"
        }
        val t = timeFmt.format(now)
        val d = dateFmt.format(now).replaceFirstChar { it.uppercase() }
        if (greeting.text.toString() != g) greeting.text = g
        if (clock.text.toString() != t) clock.text = t
        if (dateView.text.toString() != d) dateView.text = d
    }

    // ---------- wallpaper / scenes ----------

    private fun wallpaperFile(): File = File(getExternalFilesDir(null) ?: filesDir, "wallpaper.jpg")

    private fun loadCustomWallpaper(): Bitmap? {
        val f = wallpaperFile()
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 1920) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        return BitmapFactory.decodeFile(f.path, opts)
    }

    private fun applyScene() {
        val idx = prefs.getInt("scene", 0)
        if (idx >= SCENES.size) {
            val bmp = loadCustomWallpaper()
            if (bmp != null) {
                bg.setImageBitmap(bmp)
                return
            }
        }
        val s = SCENES[idx.coerceIn(0, SCENES.size - 1)]
        bg.setImageDrawable(GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, s.colors))
    }

    // ---------- apps ----------

    private fun loadApps(): List<AppEntry> {
        val pm = packageManager
        val result = LinkedHashMap<String, AppEntry>()
        val cats = listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
        for (cat in cats) {
            val i = Intent(Intent.ACTION_MAIN)
            i.addCategory(cat)
            for (ri in pm.queryIntentActivities(i, 0)) {
                val ai = ri.activityInfo ?: continue
                if (ai.packageName == packageName) continue
                if (result.containsKey(ai.packageName)) continue
                result[ai.packageName] = AppEntry(ai.packageName, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    private fun launch(a: AppEntry) {
        val pm: PackageManager = packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(a.pkg) ?: pm.getLaunchIntentForPackage(a.pkg)
        if (i == null) {
            Toast.makeText(this, "Не удалось открыть: " + a.label, Toast.LENGTH_SHORT).show()
            return
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(i)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть: " + a.label, Toast.LENGTH_SHORT).show()
        }
    }

    private fun pins(apps: List<AppEntry>): MutableList<String> {
        val saved = prefs.getString("pins", null)
        if (saved != null) return saved.split("|").filter { it.isNotEmpty() }.toMutableList()
        val preferred = listOf(
            "com.google.android.youtube.tv",
            "com.netflix.ninja",
            "com.google.android.youtube.tvmusic",
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.videos"
        )
        val out = mutableListOf<String>()
        for (p in preferred) if (apps.any { it.pkg == p }) out.add(p)
        for (a in apps) {
            if (out.size >= 6) break
            if (!out.contains(a.pkg)) out.add(a.pkg)
        }
        return out
    }

    private fun savePins(list: List<String>) {
        prefs.edit().putString("pins", list.joinToString("|")).apply()
    }

    // ---------- dock ----------

    private fun cell(icon: Drawable, label: String, iconSize: Int, symbol: Boolean, onClick: () -> Unit): View {
        val f = FrameLayout(this)
        f.isFocusable = true
        f.isClickable = true
        f.background = focusBg(dp(20))
        f.setOnClickListener { onClick() }
        f.setOnFocusChangeListener { v, has ->
            val k = if (has) 1.12f else 1f
            v.animate().scaleX(k).scaleY(k).setDuration(140).start()
            if (has) {
                tip.text = label
                tip.visibility = View.VISIBLE
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                tip.translationX = (loc[0] + v.width / 2f) - root.width / 2f
            }
        }

        val iv = ImageView(this)
        iv.setImageDrawable(icon)
        if (symbol) {
            iv.setColorFilter(Color.WHITE)
        } else {
            iv.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                }
            }
            iv.clipToOutline = true
        }
        f.addView(iv, FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER))

        val lp = LinearLayout.LayoutParams(dp(72), dp(72))
        lp.setMargins(dp(4), 0, dp(4), 0)
        f.layoutParams = lp
        return f
    }

    private fun rebuildDock() {
        val apps = loadApps()
        dock.removeAllViews()
        val byPkg = apps.associateBy { it.pkg }
        for (p in pins(apps)) {
            val a = byPkg[p] ?: continue
            dock.addView(cell(a.icon, a.label, dp(52), false) { launch(a) })
        }
        dock.addView(cell(getDrawable(android.R.drawable.ic_menu_preferences)!!, "Настройки", dp(30), true) { showSettings() })
        dock.addView(cell(getDrawable(android.R.drawable.ic_menu_more)!!, "Все приложения", dp(30), true) { showAllApps() })
    }

    // ---------- overlays ----------

    private fun openOverlay(panel: View, w: Int, h: Int, focusTarget: View?) {
        if (overlay != null) {
            root.removeView(overlay)
            overlay = null
        }
        val o = FrameLayout(this)
        o.setBackgroundColor(0x99060B18.toInt())
        o.isClickable = true
        o.addView(panel, FrameLayout.LayoutParams(w, h, Gravity.CENTER))
        root.addView(o, FrameLayout.LayoutParams(MATCH, MATCH))
        overlay = o
        dock.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        tip.visibility = View.INVISIBLE
        focusTarget?.requestFocus()
    }

    private fun closeOverlay() {
        val o = overlay ?: return
        root.removeView(o)
        overlay = null
        dock.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        rebuildDock()
        dock.getChildAt(0)?.requestFocus()
    }

    private fun panelBox(): LinearLayout {
        val p = LinearLayout(this)
        p.orientation = LinearLayout.VERTICAL
        p.background = glass(dp(28), 0xDD141C30.toInt())
        p.setPadding(dp(28), dp(22), dp(28), dp(16))
        return p
    }

    private fun appTile(a: AppEntry, apps: List<AppEntry>, pinnedInit: Boolean): View {
        var pinned = pinnedInit
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.isFocusable = true
        col.isClickable = true
        col.isLongClickable = true
        col.background = focusBg(dp(14))
        col.setPadding(dp(6), dp(8), dp(6), dp(6))

        val iv = ImageView(this)
        iv.setImageDrawable(a.icon)
        col.addView(iv, LinearLayout.LayoutParams(dp(56), dp(56)))

        val name = tv(13f)
        name.maxLines = 1
        name.ellipsize = TextUtils.TruncateAt.END
        name.gravity = Gravity.CENTER
        val nameLp = LinearLayout.LayoutParams(MATCH, WRAP)
        nameLp.topMargin = dp(6)
        col.addView(name, nameLp)

        fun refreshLabel() {
            name.text = (if (pinned) "★ " else "") + a.label
        }
        refreshLabel()

        col.setOnClickListener { launch(a) }
        col.setOnLongClickListener {
            val list = pins(apps)
            pinned = !pinned
            if (pinned) {
                if (!list.contains(a.pkg)) list.add(a.pkg)
            } else {
                list.remove(a.pkg)
            }
            savePins(list)
            refreshLabel()
            Toast.makeText(
                this,
                if (pinned) "Закреплено в панели" else "Убрано из панели",
                Toast.LENGTH_SHORT
            ).show()
            true
        }
        return col
    }

    private fun showAllApps() {
        val apps = loadApps()
        val pinned = pins(apps)

        val panel = panelBox()
        panel.addView(tv(24f, true).apply { text = "Все приложения" })

        val grid = GridLayout(this)
        grid.columnCount = 6
        var first: View? = null
        for (a in apps) {
            val tile = appTile(a, apps, pinned.contains(a.pkg))
            val lp = GridLayout.LayoutParams()
            lp.width = dp(124)
            lp.height = dp(112)
            lp.setMargins(dp(2), dp(2), dp(2), dp(2))
            grid.addView(tile, lp)
            if (first == null) first = tile
        }
        val scroll = ScrollView(this)
        scroll.addView(grid)
        val scrollLp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        scrollLp.topMargin = dp(12)
        panel.addView(scroll, scrollLp)

        val hint = tv(14f)
        hint.text = "OK — открыть   ·   удерживайте OK — закрепить в панели   ·   Назад — закрыть"
        hint.alpha = 0.8f
        panel.addView(hint)

        openOverlay(panel, dp(860), dp(470), first)
    }

    private fun showSettings() {
        val panel = panelBox()
        panel.addView(tv(26f, true).apply { text = "Glass Home" })
        panel.addView(tv(15f).apply {
            text = "Настройки  ·  Вид и сцены"
            alpha = 0.8f
        })

        val names = SCENES.map { it.name } + "Своя картинка"
        val rows = ArrayList<TextView>()

        fun refresh() {
            val sel = prefs.getInt("scene", 0)
            for (i in rows.indices) {
                rows[i].text = (if (i == sel) "●  " else "○  ") + names[i]
            }
        }

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        for (i in names.indices) {
            val r = tv(18f)
            r.isFocusable = true
            r.isClickable = true
            r.gravity = Gravity.CENTER_VERTICAL
            r.setPadding(dp(16), 0, dp(16), 0)
            r.background = focusBg(dp(12))
            r.setOnClickListener {
                if (i == SCENES.size && !wallpaperFile().exists()) {
                    Toast.makeText(
                        this,
                        "Положите wallpaper.jpg в /sdcard/Android/data/com.glasshome.tv/files/",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    prefs.edit().putInt("scene", i).apply()
                    applyScene()
                    refresh()
                }
            }
            rows.add(r)
            val lp = LinearLayout.LayoutParams(MATCH, dp(46))
            lp.topMargin = dp(4)
            list.addView(r, lp)
        }
        refresh()

        val scroll = ScrollView(this)
        scroll.addView(list)
        val scrollLp = LinearLayout.LayoutParams(MATCH, 0, 1f)
        scrollLp.topMargin = dp(10)
        panel.addView(scroll, scrollLp)

        val foot = tv(14f)
        foot.text = "Glass Home 0.1  ·  без рекламы и аналитики"
        foot.alpha = 0.7f
        panel.addView(foot)

        openOverlay(panel, dp(560), dp(430), rows.firstOrNull())
    }
}
