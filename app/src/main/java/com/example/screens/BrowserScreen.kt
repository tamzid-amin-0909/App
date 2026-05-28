package com.example.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.MainActivity
import com.example.constants.AppConstants
import com.example.helpers.NetworkObserver
import com.example.helpers.SecurityManager
import com.example.helpers.UrlHandler
import com.example.widgets.BrowserOfflineView
import com.example.widgets.BrowserProgressBar
import com.example.widgets.BrowserTabBar
import com.example.widgets.BrowserSecurityErrorView
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mainActivity = context as? MainActivity

    // State definitions
    var currentUrl by rememberSaveable { mutableStateOf(AppConstants.MAIN_URL) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isOnline by remember { mutableStateOf(true) }

    // Security constraints state managers
    var isVpnBlocked by remember { mutableStateOf(false) }
    var isProxyBlocked by remember { mutableStateOf(false) }
    var isSslUntrusted by remember { mutableStateOf(false) }

    var isSettingsOpen by remember { mutableStateOf(false) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var popupUrl by remember { mutableStateOf("") }
    
    val sharedPrefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var isTurboMode by remember {
        mutableStateOf(sharedPrefs.getBoolean("turbo_mode", false))
    }

    // Reference to native WebView for navigation command integration
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeRefreshRef by remember { mutableStateOf<SwipeRefreshLayout?>(null) }

    // Helper task to execute SSL pre-flight tests
    val performSecurityAudit: () -> Unit = {
        isVpnBlocked = SecurityManager.isVpnActive(context)
        isProxyBlocked = SecurityManager.isProxyActive()
        
        // Execute dynamic SSL connection ping to detect interception proxies
        SecurityManager.verifySslConnectionAsync(AppConstants.MAIN_URL) { safe ->
            isSslUntrusted = !safe
        }
    }

    // Trigger secure scan immediately on load
    LaunchedEffect(Unit) {
        performSecurityAudit()
    }

    // Observe network states dynamically
    val networkObserver = remember { NetworkObserver(context) }
    LaunchedEffect(Unit) {
        networkObserver.isConnectedFlow.collectLatest { connected ->
            isOnline = connected
            // Audit security parameters on network changes to catch VPN toggle
            performSecurityAudit()
        }
    }

    // Aggressively dismiss system UI (status/navigation bars) whenever keyboard padding changes
    val keyboardPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    LaunchedEffect(keyboardPadding) {
        mainActivity?.runOnUiThread {
            @Suppress("DEPRECATION")
            mainActivity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        }
    }

    // Intercept hardware Android back keys
    BackHandler(enabled = canGoBack) {
        webViewRef?.let { web ->
            if (web.canGoBack()) {
                web.goBack()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Render controls bar only if no security failures are active
        if (!isVpnBlocked && !isProxyBlocked && !isSslUntrusted) {
            BrowserTabBar(
                currentUrl = currentUrl,
                pageTitle = pageTitle,
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                onBackClick = { webViewRef?.goBack() },
                onForwardClick = { webViewRef?.goForward() },
                onRefreshClick = { 
                    performSecurityAudit()
                    if (!isVpnBlocked && !isProxyBlocked && !isSslUntrusted) {
                        if (networkObserver.isCurrentlyConnected()) {
                            webViewRef?.reload() 
                        } else {
                            isOnline = false
                        }
                    }
                },
                onHomeClick = { 
                    performSecurityAudit()
                    if (!isVpnBlocked && !isProxyBlocked && !isSslUntrusted) {
                        if (networkObserver.isCurrentlyConnected()) {
                            webViewRef?.loadUrl(AppConstants.MAIN_URL)
                        } else {
                            isOnline = false
                        }
                    }
                },
                onSettingsClick = { isSettingsOpen = true },
                isOnline = isOnline
            )

            // Indeterminate or relative progress loader bar
            BrowserProgressBar(isLoading = isLoading, progress = progress)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
        ) {
            // Priority check: VPN or Proxy security blocks
            if (isVpnBlocked || isProxyBlocked) {
                BrowserSecurityErrorView(
                    isVpnError = isVpnBlocked,
                    onRetryClick = {
                        performSecurityAudit()
                    }
                )
            }
            // Certificate/SSL failure (interception proxy check fail)
            else if (isSslUntrusted) {
                BrowserSecurityErrorView(
                    isVpnError = false,
                    onRetryClick = {
                        performSecurityAudit()
                    }
                )
            }
            // Device network disconnected view fallback
            else if (!isOnline) {
                BrowserOfflineView(
                    onRetryClick = {
                        performSecurityAudit()
                        if (networkObserver.isCurrentlyConnected()) {
                            isOnline = true
                            webViewRef?.reload()
                        }
                    }
                )
            }
            // Normal WebView viewport render
            else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val webView = WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            
                            // Initialize Cookie Jar & Configuration
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            // Apply robust, highly performant Android WebSettings
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                javaScriptCanOpenWindowsAutomatically = true
                                setSupportMultipleWindows(true)
                                
                                // Uniform, completely stable User Agent Initialization
                                val defaultUA = WebSettings.getDefaultUserAgent(ctx)
                                userAgentString = selectUserAgent(defaultUA)
                            }

                            // Intercept URL loading redirects
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): WebResourceResponse? {
                                    val response = handleTurboModeInterception(view, request)
                                    if (response != null) return response
                                    return super.shouldInterceptRequest(view, request)
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    val url = request?.url?.toString() ?: return false
                                    return UrlHandler.handleUrl(ctx, url)
                                }

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                    
                                    // Live Security Auditing to block users enabling tools post-app-launch
                                    performSecurityAudit()
                                    
                                    // Track history navigation availability
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    url?.let { currentUrl = it }
                                    
                                    canGoBack = view?.canGoBack() ?: false
                                    canGoForward = view?.canGoForward() ?: false
                                    
                                    // Persist sessions by syncing cookies
                                    CookieManager.getInstance().flush()
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // Treat primary main-frame errors as disconnect triggers
                                    if (request?.isForMainFrame == true) {
                                        isOnline = false
                                    }
                                }

                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: android.net.http.SslError?
                                ) {
                                    // CRITICAL SECURITY RULE: NEVER call handler?.proceed() as it allows sniffing.
                                    // We terminate connection instantly upon security failure.
                                    handler?.cancel()
                                    isSslUntrusted = true
                                }
                            }

                            // Monitor Page Loading Progress, Titles, File Uploads, and HTML5 Fullscreen requests
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    super.onProgressChanged(view, newProgress)
                                    progress = newProgress / 100f
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    title?.let { pageTitle = it }
                                }

                                // Native File Upload Interceptor (Crucial for online school portals/documents)
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<android.net.Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    if (mainActivity != null && filePathCallback != null && fileChooserParams != null) {
                                        mainActivity.triggerFileChooser(filePathCallback, fileChooserParams)
                                        return true // Indicates we consume selection
                                    }
                                    return false
                                }

                                // Handle social login popups and window.open redirects natively
                                override fun onCreateWindow(
                                    view: WebView?,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message?
                                ): Boolean {
                                    val context = view?.context ?: return false
                                    val newWebView = WebView(context).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                        
                                        // Align global capabilities for popup container
                                        val cookieManager = CookieManager.getInstance()
                                        cookieManager.setAcceptCookie(true)
                                        cookieManager.setAcceptThirdPartyCookies(this, true)
                                        
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            allowFileAccess = true
                                            allowContentAccess = true
                                            javaScriptCanOpenWindowsAutomatically = true
                                            setSupportMultipleWindows(true)
                                            setSupportZoom(true)
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            
                                            val defaultUA = WebSettings.getDefaultUserAgent(context)
                                            userAgentString = selectUserAgent(defaultUA)
                                        }
                                        
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldInterceptRequest(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): WebResourceResponse? {
                                                val response = handleTurboModeInterception(view, request)
                                                if (response != null) return response
                                                return super.shouldInterceptRequest(view, request)
                                            }

                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                popupUrl = url
                                                if (UrlHandler.handleUrl(context, url)) {
                                                    return true
                                                }
                                                return false
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                if (url != null) {
                                                    popupUrl = url
                                                }
                                                CookieManager.getInstance().flush()
                                            }
                                        }
                                        
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCloseWindow(window: WebView?) {
                                                popupWebView = null
                                                popupUrl = ""
                                            }

                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                super.onProgressChanged(view, newProgress)
                                                view?.url?.let {
                                                    popupUrl = it
                                                }
                                            }
                                        }
                                    }
                                    
                                    val transport = resultMsg?.obj as? WebView.WebViewTransport
                                    if (transport != null) {
                                        transport.webView = newWebView
                                        resultMsg.sendToTarget()
                                        popupWebView = newWebView
                                        return true
                                    }
                                    return false
                                }

                                // Interactive HTML5 video or rich media fullscreen support
                                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                    super.onShowCustomView(view, callback)
                                    if (mainActivity != null && view != null && callback != null) {
                                        mainActivity.triggerCustomViewShow(view, callback)
                                    }
                                }

                                override fun onHideCustomView() {
                                    super.onHideCustomView()
                                    if (mainActivity != null) {
                                        mainActivity.triggerCustomViewHide()
                                    }
                                }
                            }

                            loadUrl(currentUrl)
                        }

                        // Connect embedded pull-to-refresh swipe container with horizontal touch suppression
                        object : SwipeRefreshLayout(ctx) {
                            private var startX = 0f
                            private var startY = 0f
                            private val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

                            override fun onInterceptTouchEvent(event: android.view.MotionEvent): Boolean {
                                when (event.action) {
                                    android.view.MotionEvent.ACTION_DOWN -> {
                                        startX = event.x
                                        startY = event.y
                                    }
                                    android.view.MotionEvent.ACTION_MOVE -> {
                                        val eventX = event.x
                                        val eventY = event.y
                                        val xDiff = Math.abs(eventX - startX)
                                        val yDiff = Math.abs(eventY - startY)

                                        // If user is predominantly swiping horizontally, do NOT trigger pull-to-refresh or vertical drag.
                                        // This ensures they can scroll left/right without any interfering circles or interruptions.
                                        if (xDiff > touchSlop && xDiff > yDiff) {
                                            return false
                                        }
                                    }
                                }
                                return super.onInterceptTouchEvent(event)
                            }
                        }.apply {
                            addView(webView)
                            
                            // Require a deliberate hard pull down to refresh (350dp) to prevent accidental triggers during scroll
                            val density = ctx.resources.displayMetrics.density
                            setDistanceToTriggerSync((350 * density).toInt())
                            
                            setOnRefreshListener {
                                performSecurityAudit()
                                if (!isVpnBlocked && !isProxyBlocked && !isSslUntrusted) {
                                    if (networkObserver.isCurrentlyConnected()) {
                                        webView.reload()
                                    } else {
                                        isOnline = false
                                    }
                                }
                                isRefreshing = false
                            }
                            swipeRefreshRef = this
                            webViewRef = webView
                        }
                    },
                    update = { swipeLayout ->
                        // Swipe refresh configuration update
                    }
                )
            }
        }
    }

    // Rendering popup multiple-windows social logins (e.g. Telegram log in) Dialog overlay
    if (popupWebView != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {
                popupWebView = null
                popupUrl = ""
            },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { popupWebView!! }
                )
                
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (popupUrl.contains("drive", ignoreCase = true)) {
                        IconButton(
                            onClick = {
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(popupUrl)
                                    )
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Cannot open in external browser", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Open in External Browser",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            popupWebView = null
                            popupUrl = ""
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Popup Window",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (isSettingsOpen) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isSettingsOpen = false }
        ) {
            val isDark = androidx.compose.foundation.isSystemInDarkTheme()
            val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
            val titleColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
            val descColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
            val btnBorderColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
            
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Browser Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        ),
                        color = titleColor,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = "Manage your browsing session, clear secure storage and caches, or sync with our updates.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = descColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Turbo Mode Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = if (isDark) Color(0xFF334155).copy(alpha = 0.3f) else Color(0xFFF1F5F9),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Turbo Mode",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                ),
                                color = titleColor
                            )
                            Text(
                                text = "Don't Turn on if don't need",
                                style = MaterialTheme.typography.bodySmall,
                                color = descColor
                            )
                        }
                        Switch(
                            checked = isTurboMode,
                            onCheckedChange = { checked ->
                                isTurboMode = checked
                                sharedPrefs.edit().putBoolean("turbo_mode", checked).apply()
                                android.widget.Toast.makeText(
                                    context, 
                                    if (checked) "Turbo Mode Enabled" else "Turbo Mode Disabled", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    // 1. Join TG Channel (Branded Telegram Blue Option)
                    Button(
                        onClick = {
                            isSettingsOpen = false
                            UrlHandler.handleUrl(context, "https://t.me/eduzod")
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF179CDE),
                            contentColor = Color.White
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Channel: Join Telegram",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    // 2. Clear Cache Option
                    OutlinedButton(
                        onClick = {
                            webViewRef?.clearCache(true)
                            try {
                                context.cacheDir.deleteRecursively()
                            } catch (e: Exception) {}
                            android.widget.Toast.makeText(context, "Cache Cleared", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = titleColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, btnBorderColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear Cache Only",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    // 3. Clear Data Option (Full Reset)
                    OutlinedButton(
                        onClick = {
                            isSettingsOpen = false
                            
                            // Delete cookies
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.removeAllCookies { success ->
                                cookieManager.flush()
                            }
                            
                            // Delete WebView Storage
                            WebStorage.getInstance().deleteAllData()
                            
                            // Delete Local directories
                            try {
                                context.cacheDir.deleteRecursively()
                                context.filesDir.deleteRecursively()
                            } catch (e: Exception) {}
                            
                            // Reset state & reload
                            webViewRef?.clearHistory()
                            webViewRef?.loadUrl(AppConstants.MAIN_URL)
                            
                            android.widget.Toast.makeText(context, "All Data & Cookies Reset", android.widget.Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = titleColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, btnBorderColor),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFFF87171) else Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear All Cookies & Data",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    HorizontalDivider(
                        color = btnBorderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    // Close Settings Button
                    TextButton(
                        onClick = { isSettingsOpen = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Dismiss Settings",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun selectUserAgent(defaultUA: String): String {
    // Clean to look like a standard Mobile Chrome browser to prevent YouTube and Google Auth blocks
    val baseChromeUA = defaultUA
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+"), "")
        
    // Return unified, stable user agent with EduZod/1.0 appended
    return "$baseChromeUA EduZod/1.0"
}

private fun handleTurboModeInterception(
    view: WebView?,
    request: WebResourceRequest?
): WebResourceResponse? {
    if (view == null || request == null) return null
    
    val context = view.context ?: return null
    val sharedPrefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
    val isTurboMode = sharedPrefs.getBoolean("turbo_mode", false)
    if (!isTurboMode) return null
    
    // Only intercept standard GET/HEAD requests to avoid breaking any POSTs/preflights
    val method = request.method?.uppercase() ?: "GET"
    if (method != "GET" && method != "HEAD") return null
    
    val urlStr = request.url?.toString() ?: return null
    val host = request.url?.host?.lowercase() ?: ""
    
    // Support all general Bunny Stream & CDN hostnames to mimic real extension behavior
    val isTargetHost = host.contains("mediadelivery.net") || 
                       host.contains("b-cdn.net") || 
                       host.contains("bunnycdn.com")
                       
    if (isTargetHost) {
        try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.instanceFollowRedirects = true
            
            // Copy request headers while avoiding case-insensitive duplicates of critical headers
            request.requestHeaders?.forEach { (key, value) ->
                if (!key.equals("Referer", ignoreCase = true) && 
                    !key.equals("Origin", ignoreCase = true) && 
                    !key.equals("User-Agent", ignoreCase = true)) {
                    connection.setRequestProperty(key, value)
                }
            }
            
            // Inject correct custom headers as requested (strict, without trailing slashes)
            connection.setRequestProperty("Referer", "https://iframe.mediadelivery.net")
            connection.setRequestProperty("Origin", "https://iframe.mediadelivery.net")
            
            // Safely retrieve and set User-Agent
            val defaultUA = WebSettings.getDefaultUserAgent(context)
            val cleanUA = selectUserAgent(defaultUA)
            connection.setRequestProperty("User-Agent", cleanUA)
            
            // Forward standard Cookies
            val cookieManager = CookieManager.getInstance()
            val cookies = cookieManager.getCookie(urlStr)
            if (!cookies.isNullOrEmpty()) {
                connection.setRequestProperty("Cookie", cookies)
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            
            var contentType = connection.contentType ?: "application/octet-stream"
            var encoding = "UTF-8"
            if (contentType.contains(";")) {
                val parts = contentType.split(";")
                contentType = parts[0].trim()
                for (i in 1 until parts.size) {
                    val part = parts[i].trim()
                    if (part.lowercase().startsWith("charset=")) {
                        encoding = part.substring(8).trim()
                    }
                }
            }
            
            // Gather response headers
            val responseHeaders = HashMap<String, String>()
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && values != null && values.isNotEmpty()) {
                    responseHeaders[key] = values.joinToString(", ")
                }
            }
            
            // Copy Set-Cookie headers back to CookieManager to maintain state synchrony
            connection.headerFields?.forEach { (key, values) ->
                if (key != null && key.equals("Set-Cookie", ignoreCase = true) && values != null) {
                    values.forEach { cookieValue ->
                        cookieManager.setCookie(urlStr, cookieValue)
                    }
                }
            }
            cookieManager.flush()
            
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            
            return WebResourceResponse(
                contentType,
                encoding,
                responseCode,
                if (responseMessage.isNullOrEmpty()) "OK" else responseMessage,
                responseHeaders,
                inputStream
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    return null
}
