package com.example

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.screens.BrowserScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    
    // HTML5 Fullscreen View State storage
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    // Register standard modern contract for file picker intent feedback
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultData = result.data
        if (result.resultCode == Activity.RESULT_OK && resultData != null) {
            val uris = when {
                resultData.data != null -> {
                    arrayOf(resultData.data!!)
                }
                resultData.clipData != null -> {
                    val count = resultData.clipData!!.itemCount
                    val list = ArrayList<Uri>()
                    for (i in 0 until count) {
                        list.add(resultData.clipData!!.getItemAt(i).uri)
                    }
                    list.toTypedArray()
                }
                else -> {
                    null
                }
            }
            filePathCallback?.onReceiveValue(uris)
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Bind app content fully edge-to-edge
        enableEdgeToEdge()
        hideSystemUI()
        
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding() // Satisfies bottom gesture bar overlay guidelines
                ) { innerPadding ->
                    BrowserScreen(
                        onBackPressed = { finishOrCloseCustomView() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN
        )
        // Ensure action bar is hidden if present
        actionBar?.hide()
    }

    /**
     * Handles hardware and system back requests. Triggers fullscreen hiding or closes back stack.
     */
    private fun finishOrCloseCustomView() {
        if (customView != null) {
            triggerCustomViewHide()
        } else {
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (customView != null) {
            triggerCustomViewHide()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    /**
     * Interfaces with WebChromeClient requests to launch standard Android storage picking systems.
     */
    fun triggerFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ) {
        // Clear past callbacks safely before executing
        filePathCallback?.onReceiveValue(null)
        filePathCallback = callback

        try {
            val intent = params.createIntent().apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            fileChooserLauncher.launch(intent)
        } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    /**
     * Attaches an HTML5 fullscreen custom view to the window content view.
     */
    fun triggerCustomViewShow(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        // Dynamically toggle user landscape orientation for video fullscreen support
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Hide standard status navigation items for pure full-screen mode
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        val decor = window.decorView as ViewGroup
        decor.addView(customView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    /**
     * Detaches any attached full-screen view and restores normal system UI.
     */
    fun triggerCustomViewHide() {
        val view = customView ?: return
        customView = null

        val decor = window.decorView as ViewGroup
        decor.removeView(view)

        // Restore normal user orientation permissions
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        // Reset visibility to normal state
        hideSystemUI()

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}
