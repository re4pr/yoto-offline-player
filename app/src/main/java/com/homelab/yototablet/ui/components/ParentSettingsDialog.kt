package com.homelab.yototablet.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.homelab.yototablet.model.SyncStatusState
import com.homelab.yototablet.model.formatBytes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ParentSettingsDialog(
    currentBaseUrl: String,
    syncStatus: SyncStatusState,
    accentColor: Color,
    onDismiss: () -> Unit,
    onForceSyncAll: () -> Unit,
    onSaveBaseUrl: (String) -> Unit,
) {
    var apiInput by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }

    val lastSyncFormatted = remember(syncStatus.lastSyncedAtMs) {
        if (syncStatus.lastSyncedAtMs <= 0L) {
            "Never"
        } else {
            SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(syncStatus.lastSyncedAtMs))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151824),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD0D5E4),
        title = {
            Text(
                text = "Parent Settings",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Hub Status: ${if (syncStatus.isServerReachable) "Connected" else "Offline"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (syncStatus.isServerReachable) Color(0xFF4ADE80) else Color(0xFFFBBF24),
                )
                Text(
                    text = "Offline Playlists: ${syncStatus.completedCards} / ${syncStatus.totalDownloadableCards} (${formatBytes(syncStatus.totalOfflineBytes)})",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Last Network Refresh: $lastSyncFormatted",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = apiInput,
                    onValueChange = { apiInput = it },
                    label = { Text("Homelab Hub API URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onForceSyncAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color(0xFF0A0B10),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sync & Download All Now", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSaveBaseUrl(apiInput)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = Color(0xFF0A0B10),
                ),
            ) {
                Text("Save URL")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Close", color = Color.White)
            }
        },
    )
}
