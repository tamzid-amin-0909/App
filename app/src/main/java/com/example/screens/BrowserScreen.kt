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
                                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                            
                                            val defaultUA = WebSettings.getDefaultUserAgent(context)
                                            userAgentString = selectUserAgent(defaultUA)
                                        }
                                        
                                        webViewClient = object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?
                                            ): Boolean {
                                                val url = request?.url?.toString() ?: return false
                                                val host = request.url?.host?.lowercase() ?: ""
                                                
                                                if (host.contains(AppConstants.TARGET_DOMAIN)) {
                                                    webViewRef?.loadUrl(url)
                                                    popupWebView = null
                                                    CookieManager.getInstance().flush()
                                                    return true
                                                }
                                                
                                                if (UrlHandler.handleUrl(context, url)) {
                                                    return true
                                                }
                                                return false
                                            }

                                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                super.onPageStarted(view, url, favicon)
                                                if (url != null) {
                                                     val uri = android.net.Uri.parse(url)
                                                     val host = uri.host?.lowercase() ?: ""
                                                     if (host.contains(AppConstants.TARGET_DOMAIN)) {
                                                         webViewRef?.loadUrl(url)
                                                         view?.stopLoading()
                                                         popupWebView = null
                                                         CookieManager.getInstance().flush()
                                                     }
                                                }
                                            }

                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                CookieManager.getInstance().flush()
                                            }
                                        }
                                        
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onCloseWindow(window: WebView?) {
                                                popupWebView = null
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

                        // Connect embedded pull-to-refresh swipe container
                        SwipeRefreshLayout(ctx).apply {
                            addView(webView)
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
                
                // Overlay persistent close button for safety
                IconButton(
                    onClick = {
                        popupWebView = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
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
        
    // Return unified, stable user agent across all navigation context to align cookies / session states
    return "$baseChromeUA EduBrowser/1.0 EduZid"
}
