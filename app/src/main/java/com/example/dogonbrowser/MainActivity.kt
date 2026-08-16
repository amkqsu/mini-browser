package com.example.dogonbrowser

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var urlBar: EditText
    private lateinit var urlBarWrap: FrameLayout
    private lateinit var webContainer: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var tabScroll: HorizontalScrollView
    private lateinit var progressFill: View
    private lateinit var btnIncognito: ImageButton
    private lateinit var btnBookmarkAdd: ImageButton
    private lateinit var iconLock: ImageView
    private lateinit var statusText: TextView
    private lateinit var tabCountText: TextView

    private data class Tab(val webView: WebView, var incognito: Boolean, var chip: LinearLayout)

    private val tabs = mutableListOf<Tab>()
    private var currentTabIndex = 0
    private lateinit var prefs: android.content.SharedPreferences
    private var progressAnimator: ValueAnimator? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableHighRefreshRate()
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("dogon_browser", Context.MODE_PRIVATE)

        urlBar = findViewById(R.id.urlBar)
        urlBarWrap = findViewById(R.id.urlBarWrap)
        webContainer = findViewById(R.id.webContainer)
        tabBar = findViewById(R.id.tabBar)
        tabScroll = findViewById(R.id.tabScroll)
        progressFill = findViewById(R.id.progressFill)
        btnIncognito = findViewById(R.id.btnIncognito)
        btnBookmarkAdd = findViewById(R.id.btnBookmarkAdd)
        iconLock = findViewById(R.id.iconLock)
        statusText = findViewById(R.id.statusText)
        tabCountText = findViewById(R.id.tabCountText)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            bounce(it)
            currentWebView()?.let { wv -> if (wv.canGoBack()) wv.goBack() }
        }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener {
            bounce(it)
            currentWebView()?.let { wv -> if (wv.canGoForward()) wv.goForward() }
        }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener {
            bounce(it)
            currentWebView()?.reload()
        }
        findViewById<ImageButton>(R.id.btnNewTab).setOnClickListener {
            bounce(it)
            addTab("https://www.google.com")
        }
        btnBookmarkAdd.setOnClickListener {
            bounce(it)
            addBookmark()
        }
        findViewById<ImageButton>(R.id.btnBookmarkList).setOnClickListener {
            bounce(it)
            showBookmarks()
        }
        btnIncognito.setOnClickListener {
            bounce(it)
            toggleIncognitoForCurrentTab()
        }

        urlBar.setOnFocusChangeListener { _, hasFocus ->
            urlBarWrap.setBackgroundResource(if (hasFocus) R.drawable.bg_urlbar_focused else R.drawable.bg_urlbar)
            if (hasFocus) urlBar.selectAll()
        }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadInCurrentTab(urlBar.text.toString())
                urlBar.clearFocus()
                hideKeyboard()
                true
            } else false
        }

        addTab("https://www.google.com")
    }

    private fun enableHighRefreshRate() {
        // Ask the system for the highest supported refresh rate (e.g. 90/120Hz) if available.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
                val modes = display?.supportedModes
                if (modes != null && modes.isNotEmpty()) {
                    val best = modes.maxByOrNull { it.refreshRate }
                    if (best != null) {
                        val lp = window.attributes
                        lp.preferredDisplayModeId = best.modeId
                        window.attributes = lp
                    }
                }
            } catch (_: Exception) { }
        }
    }

    private fun bounce(v: View) {
        v.animate().cancel()
        v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
        }.start()
    }

    private fun currentWebView(): WebView? = tabs.getOrNull(currentTabIndex)?.webView
    private fun currentTab(): Tab? = tabs.getOrNull(currentTabIndex)

    private fun toggleIncognitoForCurrentTab() {
        // Applies to the next new tab created; also pulses current icon for feedback.
        val active = !(currentTab()?.incognito ?: false)
        btnIncognito.setBackgroundResource(if (active) R.drawable.bg_incognito_active else R.drawable.bg_icon_btn_ripple)
        addTab(if (active) "https://www.google.com" else "https://www.google.com", forceIncognito = active)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(startUrl: String, forceIncognito: Boolean = false) {
        val incognito = forceIncognito
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = !incognito
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            CookieManager.getInstance().setAcceptCookie(!incognito)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    if (view == currentWebView()) {
                        statusText.text = "Yükleniyor…"
                        iconLock.alpha = 0.4f
                    }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (view == currentWebView()) {
                        urlBar.setText(url)
                        statusText.text = "Hazır"
                        iconLock.alpha = 0.9f
                        animateProgressTo(0)
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (view == currentWebView()) {
                        animateProgressTo(newProgress)
                    }
                }
                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (view == currentWebView() || true) {
                        val idx = tabs.indexOfFirst { it.webView == view }
                        if (idx >= 0) updateChipLabel(idx, title ?: view.url ?: "Sekme")
                    }
                }
            }
        }

        val chip = createTabChip(tabs.size, startUrl, incognito)
        val tab = Tab(webView, incognito, chip)
        tabs.add(tab)
        currentTabIndex = tabs.size - 1
        tabBar.addView(chip)
        chip.startAnimation(AnimationUtils.loadAnimation(this, R.anim.tab_pop_in))

        webView.loadUrl(normalizeUrl(startUrl))
        switchToTab(currentTabIndex, animate = false)
        tabScroll.post { tabScroll.fullScroll(View.FOCUS_RIGHT) }
        tabCountText.text = "${tabs.size} sekme"
    }

    private fun createTabChip(index: Int, initialLabel: String, incognito: Boolean): LinearLayout {
        val chip = LinearLayout(this)
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = android.view.Gravity.CENTER_VERTICAL
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.marginEnd = 6
        params.topMargin = 4
        params.bottomMargin = 4
        chip.layoutParams = params
        chip.setPadding(24, 10, 14, 10)
        chip.setBackgroundResource(R.drawable.bg_tab_unselected)

        val label = TextView(this)
        label.text = if (incognito) "🕶 Gizli" else shortLabel(initialLabel)
        label.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        label.textSize = 12f
        label.maxWidth = 260
        label.maxLines = 1
        label.tag = "label"

        val close = ImageButton(this)
        close.setImageResource(R.drawable.ic_close)
        close.background = null
        close.setPadding(12, 4, 4, 4)
        val closeParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        close.layoutParams = closeParams

        chip.addView(label)
        chip.addView(close)

        chip.setOnClickListener {
            val i = tabBar.indexOfChild(chip)
            if (i >= 0) switchToTab(i)
        }
        close.setOnClickListener {
            val i = tabBar.indexOfChild(chip)
            if (i >= 0) closeTab(i)
        }
        return chip
    }

    private fun shortLabel(text: String): String {
        val cleaned = text.replace("https://", "").replace("http://", "").replace("www.", "")
        return if (cleaned.length > 16) cleaned.take(16) + "…" else cleaned.ifBlank { "Yeni sekme" }
    }

    private fun updateChipLabel(index: Int, title: String) {
        val tab = tabs.getOrNull(index) ?: return
        if (tab.incognito) return
        val label = tab.chip.findViewWithTag<TextView>("label")
        label?.text = shortLabel(title)
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        val tab = tabs.removeAt(index)
        tabBar.removeView(tab.chip)
        tab.webView.destroy()
        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        switchToTab(currentTabIndex, animate = false)
        tabCountText.text = "${tabs.size} sekme"
    }

    private fun switchToTab(index: Int, animate: Boolean = true) {
        currentTabIndex = index
        val tab = tabs.getOrNull(index) ?: return

        webContainer.removeAllViews()
        webContainer.addView(tab.webView)
        if (animate) {
            tab.webView.alpha = 0f
            tab.webView.animate().alpha(1f).setDuration(160).start()
        }

        urlBar.setText(tab.webView.url ?: "")
        btnIncognito.setBackgroundResource(if (tab.incognito) R.drawable.bg_incognito_active else R.drawable.bg_icon_btn_ripple)

        tabs.forEachIndexed { i, t ->
            t.chip.setBackgroundResource(if (i == index) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected)
        }
    }

    private fun animateProgressTo(target: Int) {
        progressAnimator?.cancel()
        val parentWidth = (progressFill.parent as View).width
        val startWidth = progressFill.layoutParams.width.coerceAtLeast(0)
        val targetWidth = if (target <= 0) 0 else (parentWidth * (target / 100f)).toInt()

        progressAnimator = ValueAnimator.ofInt(startWidth, targetWidth).apply {
            duration = 180
            addUpdateListener {
                val lp = progressFill.layoutParams
                lp.width = it.animatedValue as Int
                progressFill.layoutParams = lp
            }
            start()
        }
    }

    private fun loadInCurrentTab(input: String) {
        val wv = currentWebView() ?: return
        wv.loadUrl(normalizeUrl(input))
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
    }

    private fun addBookmark() {
        val wv = currentWebView() ?: return
        val url = wv.url ?: return
        val title = wv.title ?: url
        val set = HashSet(prefs.getStringSet("bookmarks", emptySet()) ?: emptySet())
        set.add("$title|$url")
        prefs.edit().putStringSet("bookmarks", set).apply()
        btnBookmarkAdd.setImageResource(R.drawable.ic_star_filled)
        btnBookmarkAdd.animate().rotationBy(360f).setDuration(320).start()
        Toast.makeText(this, "Yer imlerine eklendi", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val set = (prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()).toList()
        if (set.isEmpty()) {
            Toast.makeText(this, "Henüz yer imi yok", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = set.map { it.substringBefore("|") }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Yer İmleri")
            .setItems(titles) { _, which ->
                val url = set[which].substringAfter("|")
                loadInCurrentTab(url)
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
    }
}
