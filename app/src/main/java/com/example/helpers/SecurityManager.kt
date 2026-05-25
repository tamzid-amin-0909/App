package com.example.helpers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.NetworkInterface
import java.util.Collections
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

object SecurityManager {

    /**
     * Checks if a VPN network tunnel is currently active on the device.
     */
    fun isVpnActive(context: Context): Boolean {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            
            // Check NetworkCapabilities for active VPN transport
            val activeNetwork = cm.activeNetwork
            if (activeNetwork != null) {
                val caps = cm.getNetworkCapabilities(activeNetwork)
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    return true
                }
            }

            // Fallback checking of network interface names for virtual tunnels
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            for (networkInterface in Collections.list(interfaces)) {
                if (networkInterface.isUp) {
                    val name = networkInterface.name.lowercase()
                    if (name.contains("tun") || name.contains("ppp") || name.contains("tap") || name.contains("p2p") || name.contains("vpn")) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            // Secure fallback default
        }
        return false
    }

    /**
     * Checks if any HTTP system proxy is active representing potential MITM or recording.
     */
    fun isProxyActive(): Boolean {
        try {
            val proxyHost = System.getProperty("http.proxyHost")
            val proxyPort = System.getProperty("http.proxyPort")
            if (!proxyHost.isNullOrEmpty() && !proxyPort.isNullOrEmpty()) {
                return true
            }
        } catch (e: Exception) {
            // Secure fallback default
        }
        return false
    }

    /**
     * Performs a background SSL pre-flight handshake verification to block MITM interception.
     */
    fun verifySslConnectionAsync(url: String, callback: (Boolean) -> Unit) {
        // Build an ultra-secure network client with immediate timeouts to keep experience responsive
        val client = OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder()
            .url(url)
            .head() // Ultra-light HEAD method
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                val msg = e.localizedMessage?.lowercase() ?: ""
                // If standard SSL, validation, or validation chain trust error arises, mark as compromised!
                val safe = !(msg.contains("cert", ignoreCase = true) || 
                             msg.contains("validation", ignoreCase = true) || 
                             msg.contains("handshake", ignoreCase = true) || 
                             msg.contains("trust", ignoreCase = true) ||
                             msg.contains("ssl", ignoreCase = true))
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(safe)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                // Server returned valid SSL handshake
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(true)
                }
            }
        })
    }
}
