package com.example.widgets

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.helpers.VideoDownloadManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DownloadsSection(
    onDismissRequest: () -> Unit,
    onPlayVideo: (java.io.File, String) -> Unit,
    activeDownloadTitle: String?,
    activeDownloadProgress: Float,
    isDownloading: Boolean
) {
    val context = LocalContext.current
    var downloadedList by remember { mutableStateOf(VideoDownloadManager.getDownloadedVideos(context)) }
    
    // Dynamic refresh when active download completes
    LaunchedEffect(isDownloading) {
        if (!isDownloading) {
            downloadedList = VideoDownloadManager.getDownloadedVideos(context)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme()
        val cardBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC) // Slate 900 / Slate 50
        val cellBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF) // Slate 800 / White
        val textColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
        val mutedText = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .testTag("downloads_container")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = Color(0xFFFF9800), // Match Classic VLC Orange theme and highlight action
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "Downloads Manager",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                ),
                                color = textColor
                            )
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.testTag("downloads_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Downloads Panel",
                                tint = textColor
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Active Downloading Task (if any)
                    if (isDownloading && activeDownloadTitle != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFF9800).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .testTag("active_download_progress_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = Color(0xFFFF9800)
                                        )
                                        Text(
                                            text = activeDownloadTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = textColor
                                        )
                                    }
                                    
                                    Text(
                                        text = "${(activeDownloadProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFF9000)
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { activeDownloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = Color(0xFFFF9800),
                                    trackColor = Color.White.copy(alpha = 0.2f)
                                )
                            }
                        }
                    }

                    // Completed Downloads list
                    if (downloadedList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = mutedText.copy(alpha = 0.5f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Text(
                                    text = "No Downloads Found",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = textColor
                                )
                                Text(
                                    text = "When we load a streaming player or embed, tap the download icon on your bar to encrypt and store files.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = mutedText,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(downloadedList, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(color = cellBg, shape = RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = textColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.sizeString,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = Color(0xFFFF9800)
                                            )
                                            
                                            Text(
                                                text = formatDate(item.timestamp),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = mutedText
                                            )
                                        }

                                        Text(
                                            text = "Encrypted in ${item.partFilesCount} chunks",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                            color = mutedText
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Play Action
                                        IconButton(
                                            onClick = {
                                                // Prepare playing file cache
                                                val cacheFile = VideoDownloadManager.prepareVideoForPlayback(context, item.id)
                                                if (cacheFile != null && cacheFile.exists()) {
                                                    onPlayVideo(cacheFile, item.title)
                                                } else {
                                                    android.widget.Toast.makeText(context, "Failed to decrypt parts", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier.testTag("play_video_item_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play offline in VLC Player",
                                                tint = Color(0xFFFF9800)
                                            )
                                        }

                                        // Delete Action
                                        IconButton(
                                            onClick = {
                                                VideoDownloadManager.deleteVideo(context, item.id)
                                                downloadedList = VideoDownloadManager.getDownloadedVideos(context)
                                            },
                                            modifier = Modifier.testTag("delete_download_item_${item.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete Video",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return try {
        val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
        val netDate = Date(timestamp)
        sdf.format(netDate)
    } catch (e: Exception) {
        "Just now"
    }
}
