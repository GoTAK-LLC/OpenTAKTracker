package com.opentak.tracker.ui.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentak.tracker.data.ConnectionState
import com.opentak.tracker.data.ServerConfig
import com.opentak.tracker.ui.enrollment.EnrollmentSheet
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerManagerSheet(viewModel: TrackerViewModel, onDismiss: () -> Unit) {
    val serverConfigs by viewModel.serverConfigs.collectAsState()
    val serverStates by viewModel.serverStates.collectAsState()

    var showEnrollment by remember { mutableStateOf(false) }
    var deleteConfirmServerId by remember { mutableStateOf<String?>(null) }

    if (showEnrollment) {
        EnrollmentSheet(viewModel = viewModel, onDismiss = { showEnrollment = false })
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxHeight(0.85f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TAK Servers",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextWhite
                )
                Button(onClick = { showEnrollment = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Server")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (serverConfigs.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No servers configured", color = TextSecondary, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap 'Add Server' to enroll with a TAK server",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(serverConfigs, key = { it.id }) { config ->
                        ServerRow(
                            config = config,
                            state = serverStates[config.id] ?: ConnectionState.DISCONNECTED,
                            certInfo = viewModel.getCertificateInfo(config.address),
                            onToggle = { viewModel.toggleServer(config.id) },
                            onDelete = { deleteConfirmServerId = config.id }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    deleteConfirmServerId?.let { serverId ->
        val config = serverConfigs.find { it.id == serverId }
        AlertDialog(
            onDismissRequest = { deleteConfirmServerId = null },
            title = { Text("Remove Server") },
            text = {
                Text("Remove ${config?.name ?: "this server"} and delete its certificates? This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeServer(serverId)
                    deleteConfirmServerId = null
                }) {
                    Text("Remove", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmServerId = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ServerRow(
    config: ServerConfig,
    state: ConnectionState,
    certInfo: com.opentak.tracker.data.CertificateInfo?,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot
            val dotColor = when (state) {
                ConnectionState.CONNECTED, ConnectionState.SENDING -> ConnectedGreen
                ConnectionState.CONNECTING -> WarningYellow
                ConnectionState.RECONNECTING -> ReconnectingOrange
                ConnectionState.FAILED -> ErrorRed
                ConnectionState.DISCONNECTED -> TextSecondary
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Server info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    config.name.ifBlank { config.address },
                    color = TextWhite,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1
                )
                Text(
                    "${config.address}:${config.port}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                if (certInfo != null) {
                    val expiryColor = when {
                        certInfo.isExpired -> ErrorRed
                        certInfo.isExpiringSoon -> WarningYellow
                        else -> ConnectedGreen
                    }
                    val expiryText = when {
                        certInfo.isExpired -> "Cert expired"
                        certInfo.isExpiringSoon -> "Cert expiring soon"
                        certInfo.expiresAt != null -> "Cert valid"
                        else -> ""
                    }
                    if (expiryText.isNotBlank()) {
                        Text(expiryText, color = expiryColor, fontSize = 11.sp)
                    }
                }
            }

            // Enable/disable switch
            Switch(
                checked = config.enabled,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(4.dp))

            // Delete button
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove server",
                    tint = ErrorRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
