package com.example.minibrowser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private lateinit var urlBar: EditText
    private lateinit var webContainer: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var incognitoToggle: ToggleButton

    private val tabs = mutableListOf<WebView>()
    private var currentTabIndex = 0
    private lateinit var prefs: android.content.SharedPreferences

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("mini_browser", Context.MODE_PRIVATE)

        urlBar = findViewById(R.id.urlBar)
        webContainer = findViewById(R.id.webContainer)
        tabBar = findViewById(R.id.tabBar)
        incognitoToggle = findViewById(R.id.btnIncognito)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { currentWebView()?.let { if (it.canGoBack()) it.goBack() } }
        findViewById<ImageButton>(R.id.btnForward).setOnClickListener { currentWebView()?.let { if (it.canGoForward()) it.goForward() } }
        findViewById<ImageButton>(R.id.btnReload).setOnClickListener { currentWebView()?.reload() }
        findViewById<ImageButton>(R.id.btnNewTab).setOnClickListener { addTab("https://www.google.com") }
        findViewById<ImageButton>(R.id.btnBookmarkAdd).setOnClickListener { addBookmark() }
        findViewById<ImageButton>(R.id.btnBookmarkList).setOnClickListener { showBookmarks() }

        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                loadInCurrentTab(urlBar.text.toString())
                true
            } else false
        }

        addTab("https://www.google.com")
    }

    private fun currentWebView(): WebView? = tabs.getOrNull(currentTabIndex)

    @SuppressLint("SetJavaScriptEnabled")
    private fun addTab(startUrl: String) {
        val incognito = incognitoToggle.isChecked
        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = !incognito
            settings.setSupportZoom(true)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    if (view == currentWebView()) urlBar.setText(url)
                }
            }
            CookieManager.getInstance().setAcceptCookie(!incognito)
        }
        tabs.add(webView)
        currentTabIndex = tabs.size - 1
        webContainer.removeAllViews()
        webContainer.addView(webView)
        webView.loadUrl(normalizeUrl(startUrl))
        rebuildTabBar()
    }

    private fun switchToTab(index: Int) {
        currentTabIndex = index
        webContainer.removeAllViews()
        webContainer.addView(tabs[index])
        urlBar.setText(tabs[index].url ?: "")
        rebuildTabBar()
    }

    private fun rebuildTabBar() {
        tabBar.removeAllViews()
        tabs.forEachIndexed { i, wv ->
            val b = Button(this)
            b.text = "Sekme ${i + 1}"
            b.textSize = 10f
            b.setOnClickListener { switchToTab(i) }
            tabBar.addView(b)
        }
    }

    private fun loadInCurrentTab(input: String) {
        val wv = currentWebView() ?: return
        wv.loadUrl(normalizeUrl(input))
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else if (trimmed.contains(".") && !trimmed.contains(" ")) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${android.net.Uri.encode(trimmed)}"
        }
    }

    private fun addBookmark() {
        val wv = currentWebView() ?: return
        val url = wv.url ?: return
        val title = wv.title ?: url
        val set = HashSet(prefs.getStringSet("bookmarks", emptySet()) ?: emptySet())
        set.add("$title|$url")
        prefs.edit().putStringSet("bookmarks", set).apply()
        Toast.makeText(this, "Yer imlerine eklendi", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val set = (prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()).toList()
        if (set.isEmpty()) {
            Toast.makeText(this, "Yer imi yok", Toast.LENGTH_SHORT).show()
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
}
