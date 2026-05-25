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
                isOnline = isOnline
            )

            // Indeterminate or relative progress loader bar
            BrowserProgressBar(isLoading = isLoading, progress = progress)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                
                                // Dynamic User Agent Initialization
                                val defaultUA = WebSettings.getDefaultUserAgent(ctx)
                                userAgentString = selectUserAgent(currentUrl, defaultUA)
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

                                    // Dynamic User Agent adjustments depending on URL host targeting
                                    url?.let {
                                        val defaultUA = WebSettings.getDefaultUserAgent(ctx)
                                        view?.settings?.userAgentString = selectUserAgent(it, defaultUA)
                                    }
                                    
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
}

private fun selectUserAgent(url: String?, defaultUA: String): String {
    if (url == null) return defaultUA
    val host = android.net.Uri.parse(url).host?.lowercase() ?: ""
    val isTarget = host.contains("everythingfree.iceiy.com") || host.contains("iceiy.com")
    
    // Clean to look like a standard Mobile Chrome browser to prevent YouTube and Google Auth blocks
    val baseChromeUA = defaultUA
        .replace("; wv", "")
        .replace(Regex("Version/\\d+\\.\\d+"), "")
        
    return if (isTarget) {
        "$baseChromeUA EduBrowser/1.0 EduZid"
    } else {
        baseChromeUA
    }
}
