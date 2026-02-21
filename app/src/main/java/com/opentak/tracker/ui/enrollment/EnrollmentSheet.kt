package com.opentak.tracker.ui.enrollment

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.opentak.tracker.data.EnrollmentParameters
import com.opentak.tracker.data.EnrollmentStatus
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.util.Constants
import com.opentak.tracker.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun EnrollmentSheet(
    viewModel: TrackerViewModel,
    onDismiss: () -> Unit,
    initialParams: EnrollmentParameters? = null
) {
    val enrollmentStatus by viewModel.enrollmentStatus.collectAsState()
    val enrollmentMessage by viewModel.enrollmentMessage.collectAsState()

    var serverUrl by remember { mutableStateOf(initialParams?.serverURL ?: "") }
    var serverPort by remember { mutableStateOf(initialParams?.serverPort?.ifBlank { null } ?: Constants.DEFAULT_STREAMING_PORT) }
    var username by remember { mutableStateOf(initialParams?.username ?: "") }
    var password by remember { mutableStateOf(initialParams?.password ?: "") }
    var csrPort by remember { mutableStateOf(initialParams?.csrPort?.ifBlank { null } ?: Constants.DEFAULT_CSR_PORT) }
    var secureApiPort by remember { mutableStateOf(initialParams?.secureApiPort?.ifBlank { null } ?: Constants.DEFAULT_SECURE_API_PORT) }
    var showPassword by remember { mutableStateOf(false) }
    var showQRScanner by remember { mutableStateOf(false) }
    var qrError by remember { mutableStateOf<String?>(null) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Reset enrollment state when entering
    LaunchedEffect(Unit) {
        viewModel.resetEnrollment()
        // Auto-submit if URI params included credentials
        if (initialParams?.shouldAutoSubmit == true) {
            viewModel.beginEnrollment(initialParams)
        }
    }

    // QR Scanner full-screen overlay
    if (showQRScanner) {
        QRScannerView(
            onQRCodeScanned = { rawValue ->
                showQRScanner = false
                val params = viewModel.parseQRCode(rawValue)
                if (params.isValid) {
                    serverUrl = params.serverURL
                    serverPort = params.serverPort
                    username = params.username
                    password = params.password
                    if (params.csrPort.isNotBlank()) csrPort = params.csrPort
                    if (params.secureApiPort.isNotBlank()) secureApiPort = params.secureApiPort
                    qrError = null
                    // Auto-submit if credentials were included
                    if (params.shouldAutoSubmit) {
                        viewModel.beginEnrollment(params)
                    }
                } else {
                    qrError = params.errorMessage
                }
            },
            onDismiss = { showQRScanner = false }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Certificate Enrollment", style = MaterialTheme.typography.headlineSmall, color = TextWhite)
                IconButton(onClick = {
                    if (cameraPermission.status.isGranted) {
                        showQRScanner = true
                    } else {
                        cameraPermission.launchPermissionRequest()
                    }
                }) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = TextWhite)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Server Options
            Text("SERVER OPTIONS", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("Host Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showPassword = !showPassword }) {
                        Text(if (showPassword) "Hide" else "Show")
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced Options
            Text("ADVANCED OPTIONS", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = serverPort,
                onValueChange = { serverPort = it },
                label = { Text("Streaming Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = csrPort,
                onValueChange = { csrPort = it },
                label = { Text("Cert Enrollment Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = secureApiPort,
                onValueChange = { secureApiPort = it },
                label = { Text("Secure API Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            if (enrollmentStatus == EnrollmentStatus.SUCCEEDED) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
            } else {
                Button(
                    onClick = {
                        val params = EnrollmentParameters(
                            serverURL = serverUrl,
                            serverPort = serverPort,
                            username = username,
                            password = password,
                            csrPort = csrPort,
                            secureApiPort = secureApiPort
                        )
                        viewModel.beginEnrollment(params)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Enrollment")
                }
            }

            // Camera permission rationale
            if (!cameraPermission.status.isGranted && cameraPermission.status.shouldShowRationale) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Camera permission is needed to scan QR codes. Tap the QR icon again to grant.",
                    color = WarningYellow
                )
            }

            // Open scanner after permission is freshly granted
            LaunchedEffect(cameraPermission.status.isGranted) {
                if (cameraPermission.status.isGranted && !showQRScanner) {
                    // Don't auto-open on initial composition, only after a grant
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status
            val statusColor = when (enrollmentStatus) {
                EnrollmentStatus.SUCCEEDED -> ConnectedGreen
                EnrollmentStatus.FAILED, EnrollmentStatus.UNTRUSTED -> ErrorRed
                EnrollmentStatus.NOT_STARTED -> TextSecondary
                else -> WarningYellow
            }
            Text("Status: ${enrollmentStatus.name}", color = statusColor)
            if (enrollmentMessage.isNotBlank()) {
                Text(enrollmentMessage, color = TextSecondary)
            }
            if (serverUrl.isNotBlank()) {
                Text("For Server: $serverUrl", color = TextSecondary)
            }

            qrError?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text("QR Error: $it", color = ErrorRed)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
