package com.example.helpers

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.constants.AppConstants

object UrlHandler {

    private const val PACKAGE_INSTAGRAM = "com.instagram.android"
    private const val PACKAGE_TWITTER = "com.twitter.android"
    private const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"
    private const val PACKAGE_FACEBOOK_LITE = "com.facebook.lite"

    /**
     * Handles web Navigation URL changes.
     * Returns true if the URL was handled externally, false if it should be loaded inside WebView.
     */
    fun handleUrl(context: Context, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return false
        val host = uri.host?.lowercase() ?: ""

        // Process standard web link protocols
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            
            // 1. WhatsApp redirection
            if (host.contains("whatsapp.com") || host.contains("wa.me")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(AppConstants.PACKAGE_WHATSAPP, PACKAGE_WHATSAPP_BUSINESS)
                )
                if (launched) return true
                return false // Load in-app fallback
            }
            
            // 2. Telegram redirection (Only launch external Telegram app for chat/channel deep links, never for web auth modules)
            if (host.contains("t.me") || host.contains("telegram.me")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(AppConstants.PACKAGE_TELEGRAM, AppConstants.PACKAGE_TELEGRAM_X)
                )
                if (launched) return true
                return false
            }
            
            // 3. Facebook redirection
            if (host.contains("facebook.com") || host.contains("fb.com") || host.contains("fb.me")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(AppConstants.PACKAGE_FACEBOOK, PACKAGE_FACEBOOK_LITE)
                )
                if (launched) return true
                return false
            }

            // 4. Instagram redirection
            if (host.contains("instagram.com") || host.contains("instagr.am") || host.contains("ig.me")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(PACKAGE_INSTAGRAM)
                )
                if (launched) return true
                return false
            }

            // 5. X / Twitter redirection
            if (host.contains("twitter.com") || host.contains("x.com")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(PACKAGE_TWITTER)
                )
                if (launched) return true
                return false
            }

            // 6. YouTube redirection
            if (host.contains("youtube.com") || host.contains("youtu.be")) {
                val launched = launchAppIfInstalled(
                    context, 
                    url, 
                    listOf(AppConstants.PACKAGE_YOUTUBE)
                )
                if (launched) return true
                return false // Re-route inside the local high-fidelity WebView
            }

            // Standard domain matches for educational content & safe login redirects
            if (host.contains(AppConstants.TARGET_DOMAIN) || 
                host.contains("google.com") || 
                host.contains("accounts.google") ||
                host.contains("com.google")
            ) {
                return false 
            }

            return false
        }

        // Custom scheme/intents (e.g. tel:, mailto:, sms:, market:, intent:, tg:, whatsapp:)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Launches a specific package if installed. Return true if successful.
     */
    private fun launchAppIfInstalled(context: Context, url: String, packages: List<String>): Boolean {
        val packageManager = context.packageManager
        val uri = Uri.parse(url)
        
        for (pkg in packages) {
            try {
                // Verify package intent is resolved on the device
                val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
                
                val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(pkg)
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appIntent)
                return true
            } catch (e: Exception) {
                // Next package
            }
        }
        return false
    }
}
