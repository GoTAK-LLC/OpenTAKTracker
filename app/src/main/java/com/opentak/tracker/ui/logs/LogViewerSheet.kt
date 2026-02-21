package com.opentak.tracker.ui.logs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opentak.tracker.data.LogLevel
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.viewmodel.TrackerViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerSheet(viewModel: TrackerViewModel, onDismiss: () -> Unit) {
    val logs by viewModel.logs.collectAsState()
    val listState = rememberLazyListState()
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())
    }

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkBackground,
        modifier = Modifier.fillMaxHeight(0.95f)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Logs", style = MaterialTheme.typography.headlineMedium, color = TextWhite)
            Spacer(modifier = Modifier.height(8.dp))

            if (logs.isEmpty()) {
                Text("No log entries yet", color = TextSecondary)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(logs) { entry ->
                        val color = when (entry.level) {
                            LogLevel.INFO -> TextWhite
                            LogLevel.WARN -> WarningYellow
                            LogLevel.ERROR -> ErrorRed
                        }
                        val time = timeFormatter.format(entry.timestamp)
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                "$time ${entry.level.name} ${entry.tag}",
                                color = color,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                            Text(
                                "  ${entry.message}",
                                color = color.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
