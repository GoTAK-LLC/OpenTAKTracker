package com.opentak.tracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentak.tracker.data.TeamColor
import com.opentak.tracker.data.TeamRole
import com.opentak.tracker.ui.servers.ServerManagerSheet
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(viewModel: TrackerViewModel, onDismiss: () -> Unit) {
    val callsign by viewModel.settings.callsign.collectAsState(initial = "")
    val team by viewModel.settings.team.collectAsState(initial = "Cyan")
    val role by viewModel.settings.role.collectAsState(initial = "Team Member")
    val serverConfigs by viewModel.serverConfigs.collectAsState()
    val connectionSummary by viewModel.connectionSummary.collectAsState()
    val broadcastInterval by viewModel.settings.broadcastInterval.collectAsState(initial = 10L)
    val staleTime by viewModel.settings.staleTimeMinutes.collectAsState(initial = 5L)
    val dynamicMode by viewModel.settings.dynamicModeEnabled.collectAsState(initial = false)
    val udpEnabled by viewModel.settings.udpEnabled.collectAsState(initial = true)
    val udpAddress by viewModel.settings.udpAddress.collectAsState(initial = "239.2.3.1")
    val udpPort by viewModel.settings.udpPort.collectAsState(initial = "6969")
    val keepScreenOn by viewModel.settings.keepScreenOn.collectAsState(initial = true)
    val startOnBoot by viewModel.settings.startOnBoot.collectAsState(initial = false)
    val trustAll by viewModel.settings.trustAllCerts.collectAsState(initial = false)
    val lockPin by viewModel.lockPin.collectAsState()

    var showServerManager by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf(lockPin) }

    // Local text state to avoid DataStore round-trip lag
    var callsignInput by remember { mutableStateOf(callsign) }
    var udpAddressInput by remember { mutableStateOf(udpAddress) }
    var udpPortInput by remember { mutableStateOf(udpPort) }

    if (showServerManager) {
        ServerManagerSheet(
            viewModel = viewModel,
            onDismiss = { showServerManager = false }
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
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextWhite)
            Spacer(modifier = Modifier.height(16.dp))

            // User Information
            SectionHeader("USER INFORMATION")
            OutlinedTextField(
                value = callsignInput,
                onValueChange = {
                    callsignInput = it
                    viewModel.updateCallsign(it)
                },
                label = { Text("Callsign") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            DropdownSetting("Team", team, TeamColor.entries.map { it.displayName }) { viewModel.updateTeam(it) }
            Spacer(modifier = Modifier.height(8.dp))
            DropdownSetting("Role", role, TeamRole.entries.map { it.displayName }) { viewModel.updateRole(it) }

            Spacer(modifier = Modifier.height(24.dp))

            // Server Information
            SectionHeader("TAK SERVERS")
            Text(
                "${serverConfigs.size} server${if (serverConfigs.size != 1) "s" else ""} configured",
                color = TextWhite
            )
            Text(connectionSummary, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { showServerManager = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Manage TAK Servers")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tracking Options
            SectionHeader("TRACKING OPTIONS")
            Text("Broadcast Interval: ${broadcastInterval}s", color = TextWhite)
            Slider(
                value = broadcastInterval.toFloat(),
                onValueChange = { viewModel.updateBroadcastInterval(it.toLong()) },
                valueRange = 1f..120f,
                steps = 0
            )
            Text("Stale Time: ${staleTime} min", color = TextWhite)
            Slider(
                value = staleTime.toFloat(),
                onValueChange = { viewModel.updateStaleTime(it.toLong()) },
                valueRange = 1f..60f,
                steps = 0
            )
            SwitchSetting("Dynamic Mode", dynamicMode) { viewModel.updateDynamicMode(it) }
            if (dynamicMode) {
                Text(
                    "Adjusts broadcast rate based on movement. Broadcasts more frequently when moving, less often when stationary.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            SwitchSetting("UDP Broadcast", udpEnabled) { viewModel.updateUdpEnabled(it) }
            if (udpEnabled) {
                OutlinedTextField(
                    value = udpAddressInput,
                    onValueChange = {
                        udpAddressInput = it
                        viewModel.updateUdpAddress(it)
                    },
                    label = { Text("UDP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = udpPortInput,
                    onValueChange = {
                        udpPortInput = it
                        viewModel.updateUdpPort(it)
                    },
                    label = { Text("UDP Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Display
            SectionHeader("DISPLAY")
            SwitchSetting("Keep Screen On", keepScreenOn) { viewModel.updateKeepScreenOn(it) }
            SwitchSetting("Start on Boot", startOnBoot) { viewModel.updateStartOnBoot(it) }

            Spacer(modifier = Modifier.height(24.dp))

            // Advanced
            SectionHeader("ADVANCED")
            SwitchSetting("Trust All Certificates (INSECURE)", trustAll) { viewModel.updateTrustAllCerts(it) }
            if (trustAll) {
                Text("WARNING: All server certificates will be accepted!", color = WarningYellow)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Security / Kiosk Lock
            SectionHeader("SECURITY")
            Text("Set a 4-digit PIN to enable kiosk lock", color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pinInput,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                label = { Text("Lock PIN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.setLockPin(pinInput) },
                    enabled = pinInput.length == 4
                ) {
                    Text(if (lockPin.isEmpty()) "Set PIN" else "Update PIN")
                }
                if (lockPin.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        pinInput = ""
                        viewModel.setLockPin("")
                    }) {
                        Text("Clear PIN")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            SectionHeader("ABOUT")
            Text("Version: 1.0.0", color = TextSecondary)
            Text("Device UID: ${viewModel.settings.deviceUid}", color = TextSecondary)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(title, color = TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
fun SwitchSetting(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextWhite, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSetting(label: String, current: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
