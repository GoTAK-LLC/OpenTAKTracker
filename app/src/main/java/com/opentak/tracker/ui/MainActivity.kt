package com.opentak.tracker.ui

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.opentak.tracker.data.EnrollmentParameters
import com.opentak.tracker.enrollment.QRCodeParser
import com.opentak.tracker.ui.main.MainScreen
import com.opentak.tracker.ui.theme.OpenTAKTrackerTheme
import com.opentak.tracker.viewmodel.TrackerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var showEmergency by mutableStateOf(false)
    private var enrollmentParams by mutableStateOf<EnrollmentParameters?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        showEmergency = intent?.action == ACTION_SOS
        enrollmentParams = parseEnrollmentUri(intent)

        setContent {
            OpenTAKTrackerTheme {
                val viewModel: TrackerViewModel = hiltViewModel()

                // Keep screen on based on settings
                val keepScreenOn by viewModel.settings.keepScreenOn.collectAsState(initial = true)
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }

                val launchEmergency = showEmergency
                if (launchEmergency) showEmergency = false

                val launchEnrollment = enrollmentParams
                if (launchEnrollment != null) enrollmentParams = null

                MainScreen(
                    viewModel = viewModel,
                    showEmergencyInitially = launchEmergency,
                    initialEnrollmentParams = launchEnrollment
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_SOS) {
            showEmergency = true
        }
        parseEnrollmentUri(intent)?.let { enrollmentParams = it }
    }

    private fun parseEnrollmentUri(intent: Intent?): EnrollmentParameters? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        val uri = intent.data ?: return null
        if (uri.scheme != "opentaktracker") return null
        val params = QRCodeParser.parse(uri.toString())
        return if (params.isValid) params else null
    }

    companion object {
        const val ACTION_SOS = "com.opentak.tracker.ACTION_SOS"
    }
}
