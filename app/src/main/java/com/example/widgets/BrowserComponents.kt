package com.example.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.constants.AppConstants
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserTabBar(
    currentUrl: String,
    pageTitle: String,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBackClick: () -> Unit,
    onForwardClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onHomeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val displayDomain = remember(currentUrl) {
        try {
            val uri = URI(currentUrl)
            val host = uri.host ?: ""
            host.removePrefix("www.")
        } catch (e: Exception) {
            AppConstants.TARGET_DOMAIN
        }
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC) // Slate-900 / Slate-50
    val borderColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0) // Slate-800 / Slate-200
    val capsuleBg = if (isDark) Color(0xFF020617) else Color(0xFFFFFFFF) // Slate-950 / White
    val capsuleBorder = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1) // Slate-700 / Slate-300
    val textColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155) // Slate-200 / Slate-700
    val iconTintEnabled = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569) // Slate-400 / Slate-600
    val iconTintDisabled = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1) // Slate-700 / Slate-300

    Surface(
        color = bgColor,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp) // Beautiful spacious header padding
            ) {
                // Back Button
                IconButton(
                    onClick = onBackClick,
                    enabled = canGoBack,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = if (canGoBack) iconTintEnabled else iconTintDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Forward Button
                IconButton(
                    onClick = onForwardClick,
                    enabled = canGoForward,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_forward_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Navigate Forward",
                        tint = if (canGoForward) iconTintEnabled else iconTintDisabled,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Address Capsule showing domain
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                        .height(36.dp) // Luxurious & spacious address line like Chrome
                        .clip(RoundedCornerShape(18.dp))
                        .background(capsuleBg)
                        .border(1.dp, capsuleBorder, RoundedCornerShape(18.dp))
                        .clickable(onClick = onHomeClick)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Connection Security/State Indicator
                        val isHttps = currentUrl.startsWith("https")
                        Icon(
                            imageVector = if (isHttps) Icons.Default.Lock else Icons.Default.Info,
                            contentDescription = "Connection Security Status",
                            tint = if (isHttps) Color(0xFF10B981) else Color(0xFFEF4444), // Tailwind Emerald-500 / Red-500
                            modifier = Modifier.size(13.dp)
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = displayDomain.ifEmpty { "Loading..." },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.SansSerif
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = textColor
                            )
                        }

                        // Real-time Offline tiny dot alert
                        if (!isOnline) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFEF4444)) // Tailwind Red-500
                            )
                        }
                    }
                }

                // Refresh Button
                IconButton(
                    onClick = onRefreshClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_refresh_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reload Page",
                        tint = iconTintEnabled,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Home Button
                IconButton(
                    onClick = onHomeClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_home_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Go Home",
                        tint = iconTintEnabled,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Settings Button
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("browser_settings_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Open Settings",
                        tint = iconTintEnabled,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Beautiful crisp bottom border
            HorizontalDivider(
                color = borderColor,
                thickness = 1.dp
            )
        }
    }
}

@Composable
fun BrowserProgressBar(
    isLoading: Boolean,
    progress: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it }
    ) {
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            modifier = modifier
                .fillMaxWidth()
                .height(3.dp)
        )
    }
}

@Composable
fun JoinTelegramButton(
    context: android.content.Context,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = {
            com.example.helpers.UrlHandler.handleUrl(context, "https://t.me/eduzod")
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF179CDE), // Telegram Blue
            contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            contentDescription = "Telegram Link Icon",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Join Telegram",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun BrowserOfflineView(
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Offline Illustration Background Glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "No Network Connection Icon",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            }

            Text(
                text = "Connection Offline",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "The connection to the educational network is currently unavailable. Please verify your internet and tap retry.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .testTag("offline_retry_button")
            ) {
                Icon(
                     imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Retry Loading",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            JoinTelegramButton(
                context = LocalContext.current,
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}
