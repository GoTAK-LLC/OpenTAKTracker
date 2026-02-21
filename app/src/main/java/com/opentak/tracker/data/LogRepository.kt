package com.opentak.tracker.data

import com.opentak.tracker.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor() {
    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private fun append(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            timestamp = Instant.now(),
            level = level,
            tag = tag,
            message = message
        )
        buffer.addLast(entry)
        while (buffer.size > Constants.LOG_BUFFER_SIZE) {
            buffer.pollFirst()
        }
        _logs.value = buffer.toList()

        // Also log to Android logcat
        when (level) {
            LogLevel.INFO -> android.util.Log.i("OTT/$tag", message)
            LogLevel.WARN -> android.util.Log.w("OTT/$tag", message)
            LogLevel.ERROR -> android.util.Log.e("OTT/$tag", message)
        }
    }

    fun info(tag: String, message: String) = append(LogLevel.INFO, tag, message)
    fun warn(tag: String, message: String) = append(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = append(LogLevel.ERROR, tag, message)

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }
}
