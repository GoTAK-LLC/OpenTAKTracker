package com.opentak.tracker.ui.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentak.tracker.data.EmergencyType
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySheet(viewModel: TrackerViewModel, onDismiss: () -> Unit) {
    val emergencyActive by viewModel.settings.emergencyActive.collectAsState(initial = false)

    var activateSwitch by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }
    var selectedType by remember {
        mutableStateOf(if (emergencyActive) EmergencyType.Cancel else EmergencyType.NineOneOne)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxHeight(0.7f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Emergency Beacon",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Turn on both switches to initiate emergency beacon",
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Activate switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Activate Alert", color = TextWhite, fontSize = 16.sp)
                Switch(
                    checked = activateSwitch,
                    onCheckedChange = { activateSwitch = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ErrorRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Confirm Alert", color = TextWhite, fontSize = 16.sp)
                Switch(
                    checked = confirmSwitch,
                    onCheckedChange = { confirmSwitch = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = ErrorRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Alert type picker
            Text("Alert Type", color = TextSecondary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            val types = if (emergencyActive) EmergencyType.entries else EmergencyType.entries.filter { it != EmergencyType.Cancel }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedType.displayName,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    types.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.displayName) },
                            onClick = { selectedType = type; expanded = false }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (selectedType == EmergencyType.Cancel) {
                            viewModel.cancelEmergency()
                        } else {
                            viewModel.activateEmergency(selectedType)
                        }
                        onDismiss()
                    },
                    enabled = activateSwitch && confirmSwitch,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("OK")
                }
            }
        }
    }
}
