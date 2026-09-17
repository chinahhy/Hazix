package tv.hdao.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object {
        private const val START_URL = "https://hdao.tv/"
        private const val CENTER_LONG_PRESS_MS = 700L
        private const val EXIT_CONFIRM_WINDOW_MS = 2_000L
    }

    private lateinit var root: CursorLayout
    private lateinit var webView: WebView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var loadFailed = false
    private var lastBackPressedAt = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var activationKeyDown = false
    private var activationLongPressHandled = false
    private var pendingActivationKeyCode = KeyEvent.KEYCODE_DPAD_CENTER
    private val activationLongPress = Runnable {
        if (activationKeyDown && customView == null && !loadFailed) {
            activationLongPressHandled = true
            toggleCursorMode()
        }
    }

    private val spatialNavigationScript: String by lazy {
        assets.open("spatialnav.js").bufferedReader().use { it.readText() }
    }

    @SuppressLint("SetJavaScriptEnabled", "WebViewApiAvailability")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(8, 11, 18))
            overScrollMode = View.OVER_SCROLL_NEVER
            isFocusable = true
            isFocusableInTouchMode = true
        }

        root = CursorLayout(this).apply {
            attachTarget(webView)
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }
        loadingOverlay = createLoadingOverlay()
        root.addView(
            loadingOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        hideSystemUi()

        configureWebView()
        configureWebClients()
        setCursorMode(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WebView.startSafeBrowsing(this, null)
        }
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(START_URL)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun configureWebClients() {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame) return false
                return blockDisallowedNavigation(request.url.toString())
            }

            @Deprecated("Legacy WebView callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return blockDisallowedNavigation(url)
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadFailed = false
                showLoading()
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (NavigationPolicy.isAllowed(url)) {
                    injectSpatialNavigation()
                }
                if (!loadFailed) hideLoading()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) showLoadError()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    showLoadError()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(
                view: View,
                callback: CustomViewCallback
            ) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }

                customView = view
                customViewCallback = callback
                setCursorMode(false)
                webView.visibility = View.INVISIBLE
                loadingOverlay.visibility = View.GONE
                root.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                view.requestFocus()
                hideSystemUi()
            }

            override fun onHideCustomView() {
                exitFullscreenVideo()
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                // Playback does not need camera/microphone access.
                request.deny()
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, false, false)
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean = false
        }
    }

    private fun blockDisallowedNavigation(url: String): Boolean {
        if (NavigationPolicy.isAllowed(url)) return false
        Toast.makeText(this, R.string.external_link_blocked, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun injectSpatialNavigation() {
        webView.evaluateJavascript(spatialNavigationScript, null)
    }

    private fun createLoadingOverlay(): FrameLayout {
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(8, 11, 18))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }

        val title = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.WHITE)
            textSize = 32f
            gravity = Gravity.CENTER
        }
        progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        statusText = TextView(this).apply {
            text = getString(R.string.loading_message)
            setTextColor(Color.rgb(180, 187, 204))
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
        }

        content.addView(title)
        content.addView(
            progressBar,
            LinearLayout.LayoutParams(56, 56).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 28
            }
        )
        content.addView(statusText)
        overlay.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
        return overlay
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        statusText.text = getString(R.string.loading_message)
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        loadingOverlay.visibility = View.GONE
        webView.visibility = View.VISIBLE
        if (customView == null && !root.cursorEnabled) webView.requestFocus()
    }

    private fun showLoadError() {
        loadFailed = true
        progressBar.visibility = View.GONE
        statusText.text = getString(R.string.load_error)
        loadingOverlay.visibility = View.VISIBLE
    }

    private fun setCursorMode(enabled: Boolean) {
        root.cursorEnabled = enabled
        webView.isFocusable = !enabled
        webView.isFocusableInTouchMode = !enabled
        if (enabled) root.requestFocus() else webView.requestFocus()
    }

    private fun toggleCursorMode() {
        val enabled = !root.cursorEnabled
        setCursorMode(enabled)
        Toast.makeText(
            this,
            if (enabled) R.string.cursor_mode else R.string.focus_mode,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (customView != null) return dispatchFullscreenKeyEvent(event)

        if (isActivationKey(event.keyCode)) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (!activationKeyDown) {
                        activationKeyDown = true
                        activationLongPressHandled = false
                        pendingActivationKeyCode = event.keyCode
                        handler.postDelayed(activationLongPress, CENTER_LONG_PRESS_MS)
                    }
                    return true
                }

                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(activationLongPress)
                    val wasLongPress = activationLongPressHandled
                    activationKeyDown = false
                    activationLongPressHandled = false
                    if (!wasLongPress) activateCurrentTarget()
                    return true
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun isActivationKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun activateCurrentTarget() {
        if (loadFailed && loadingOverlay.visibility == View.VISIBLE) {
            webView.reload()
            return
        }
        if (loadingOverlay.visibility == View.VISIBLE) return

        if (root.cursorEnabled) {
            root.tapAtCursor()
        } else {
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, pendingActivationKeyCode))
            webView.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, pendingActivationKeyCode))
        }
    }

    private fun dispatchFullscreenKeyEvent(event: KeyEvent): Boolean {
        val handledKey = when (event.keyCode) {
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> true
            else -> false
        }
        if (!handledKey) return super.dispatchKeyEvent(event)
        if (event.action != KeyEvent.ACTION_UP) return true

        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK -> exitFullscreenVideo()
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> controlVideo("toggle")
            KeyEvent.KEYCODE_MEDIA_PLAY -> controlVideo("play")
            KeyEvent.KEYCODE_MEDIA_PAUSE -> controlVideo("pause")
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> controlVideo("back")
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> controlVideo("forward")
        }
        return true
    }

    private fun controlVideo(command: String) {
        val action = when (command) {
            "play" -> "v.play();"
            "pause" -> "v.pause();"
            "back" -> "if(isFinite(v.duration))v.currentTime=Math.max(0,v.currentTime-10);"
            "forward" -> "if(isFinite(v.duration))v.currentTime=Math.min(v.duration,v.currentTime+10);"
            else -> "if(v.paused)v.play();else v.pause();"
        }
        webView.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;$action})()",
            null
        )
    }

    private fun exitFullscreenVideo() {
        val view = customView ?: return
        root.removeView(view)
        customView = null
        webView.visibility = View.VISIBLE
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        setCursorMode(false)
        hideSystemUi()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_INFO -> {
                if (event.repeatCount == 0 && !loadFailed) toggleCursorMode()
                return true
            }

            KeyEvent.KEYCODE_BACK -> {
                when {
                    root.cursorEnabled -> setCursorMode(false)
                    webView.canGoBack() -> webView.goBack()
                    SystemClock.uptimeMillis() - lastBackPressedAt <= EXIT_CONFIRM_WINDOW_MS -> finish()
                    else -> {
                        lastBackPressedAt = SystemClock.uptimeMillis()
                        Toast.makeText(this, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                    }
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
        hideSystemUi()
    }

    override fun onPause() {
        handler.removeCallbacks(activationLongPress)
        activationKeyDown = false
        activationLongPressHandled = false
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        customView?.let(root::removeView)
        customView = null
        webView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
}
