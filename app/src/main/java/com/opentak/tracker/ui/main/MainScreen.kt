package com.opentak.tracker.ui.main

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.opentak.tracker.data.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.opentak.tracker.ui.emergency.EmergencySheet
import com.opentak.tracker.ui.enrollment.EnrollmentSheet
import com.opentak.tracker.ui.logs.LogViewerSheet
import com.opentak.tracker.ui.servers.ServerManagerSheet
import com.opentak.tracker.ui.settings.SettingsSheet
import com.opentak.tracker.ui.theme.*
import com.opentak.tracker.util.CoordinateConverter
import com.opentak.tracker.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MainScreen(
    viewModel: TrackerViewModel,
    showEmergencyInitially: Boolean = false,
    initialEnrollmentParams: EnrollmentParameters? = null
) {
    val location by viewModel.location.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val lastTxTime by viewModel.lastTransmitTime.collectAsState()
    val coordFormat by viewModel.coordinateFormat.collectAsState()
    val speedUnit by viewModel.speedUnit.collectAsState()
    val headingUnit by viewModel.headingUnit.collectAsState()
    val compassUnit by viewModel.compassUnit.collectAsState()
    val isTracking by viewModel.isTracking.collectAsState()
    val callsign by viewModel.settings.callsign.collectAsState(initial = "---")
    val emergencyActive by viewModel.settings.emergencyActive.collectAsState(initial = false)
    val connectionSummary by viewModel.connectionSummary.collectAsState()
    val isLocked by viewModel.isLocked.collectAsState()
    val lockPin by viewModel.lockPin.collectAsState()

    var showSettings by remember { mutableStateOf(false) }
    var showEmergency by remember { mutableStateOf(showEmergencyInitially) }
    var showLogs by remember { mutableStateOf(false) }
    var showServerManager by remember { mutableStateOf(false) }
    var showEnrollment by remember { mutableStateOf(initialEnrollmentParams != null) }
    var enrollmentParams by remember { mutableStateOf(initialEnrollmentParams) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    // React to enrollment params arriving via URI (handles onNewIntent when app is already running)
    LaunchedEffect(initialEnrollmentParams) {
        if (initialEnrollmentParams != null) {
            enrollmentParams = initialEnrollmentParams
            showEnrollment = true
        }
    }
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Helper: run action or prompt PIN if locked
    val guardedAction = { action: () -> Unit ->
        if (isLocked) {
            pendingAction = action
            showPinDialog = true
        } else {
            action()
        }
    }

    // Location permissions (fine + coarse)
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Background location (requested separately after foreground is granted)
    val backgroundLocationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else null

    // Notification permission (Android 13+)
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

    val hasLocationPermission = locationPermissions.permissions.any { it.status.isGranted }

    // Request location permission on first launch
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenTAK Tracker", fontWeight = FontWeight.Bold) },
                actions = {
                    // Lock button (only when PIN is set)
                    if (lockPin.isNotEmpty()) {
                        IconButton(onClick = {
                            if (isLocked) {
                                showPinDialog = true
                                pendingAction = null // just unlock, no follow-up
                            } else {
                                viewModel.lock()
                            }
                        }) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = if (isLocked) "Unlock" else "Lock",
                                tint = if (isLocked) WarningYellow else TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = { guardedAction { showEmergency = true } }) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Emergency",
                            tint = if (emergencyActive) ErrorRed else TextWhite
                        )
                    }
                    IconButton(onClick = { guardedAction { showSettings = true } }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextWhite)
                    }
                    IconButton(onClick = { guardedAction { showLogs = true } }) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Logs", tint = TextWhite)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkSurface
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Callsign
                Text(
                    text = callsign,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )

                // Permission banner if location not granted
                if (!hasLocationPermission) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningYellow.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Location permission required",
                                color = WarningYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "OpenTAK Tracker needs location access to transmit your position.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { locationPermissions.launchMultiplePermissionRequest() }) {
                                Text("Grant Location Permission")
                            }
                        }
                    }
                }

                // Background location banner: shown when foreground is granted but background is not
                if (hasLocationPermission && backgroundLocationPermission != null && !backgroundLocationPermission.status.isGranted) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = WarningYellow.copy(alpha = 0.15f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "Background location required",
                                color = WarningYellow,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "OpenTAK Tracker uses a foreground service for GPS tracking. " +
                                    "Please set location permission to \"Allow all the time\" " +
                                    "so tracking continues when the app is in the background.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { backgroundLocationPermission.launchPermissionRequest() }) {
                                Text("Enable Background Location")
                            }
                        }
                    }
                }

                // Start/Stop + Server Manager buttons
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val doTrackingToggle = {
                                if (isTracking) {
                                    viewModel.stopTracking()
                                } else {
                                    if (!hasLocationPermission) {
                                        locationPermissions.launchMultiplePermissionRequest()
                                        showPermissionRationale = true
                                    } else {
                                        notificationPermission?.let {
                                            if (!it.status.isGranted) it.launchPermissionRequest()
                                        }
                                        backgroundLocationPermission?.let {
                                            if (!it.status.isGranted) it.launchPermissionRequest()
                                        }
                                        viewModel.startTracking()
                                    }
                                }
                            }
                            guardedAction(doTrackingToggle)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTracking) ErrorRed else ConnectedGreen
                        )
                    ) {
                        Text(if (isTracking) "Stop Tracking" else "Start Tracking")
                    }

                    OutlinedButton(onClick = { guardedAction { showServerManager = true } }) {
                        Text(connectionSummary, fontSize = 12.sp)
                    }
                }

                // Location panel
                LocationPanel(
                    location = location,
                    format = coordFormat,
                    onTap = { viewModel.cycleCoordinateFormat() }
                )

                // Heading / Compass / Speed row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DataPanel(
                        title = "Heading",
                        subtitle = "(${"\u00B0"}${headingUnit.label})",
                        value = if (location.isValid) {
                            val h = if (headingUnit == DirectionUnit.TN) location.bearing else location.magneticHeading
                            CoordinateConverter.formatHeading(h)
                        } else "--",
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.toggleHeadingUnit() }
                    )
                    DataPanel(
                        title = "Compass",
                        subtitle = "(${"\u00B0"}${compassUnit.label})",
                        value = if (location.isValid) {
                            val h = if (compassUnit == DirectionUnit.TN) location.bearing else location.magneticHeading
                            CoordinateConverter.formatHeading(h)
                        } else "--",
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.toggleCompassUnit() }
                    )
                    DataPanel(
                        title = "Speed",
                        subtitle = "(${speedUnit.label})",
                        value = if (location.isValid) {
                            CoordinateConverter.convertSpeed(location.speed, speedUnit)
                        } else "--",
                        modifier = Modifier.weight(1f),
                        onTap = { viewModel.cycleSpeedUnit() }
                    )
                }

                // OSM Dark Map
                val context = LocalContext.current
                val mapViewRef = remember { mutableStateOf<MapView?>(null) }
                val markerRef = remember { mutableStateOf<Marker?>(null) }
                var hasInitialZoom by remember { mutableStateOf(false) }

                // Update marker and camera when location changes
                LaunchedEffect(location.latitude, location.longitude) {
                    val map = mapViewRef.value ?: return@LaunchedEffect
                    if (location.isValid) {
                        val point = GeoPoint(location.latitude, location.longitude)

                        // Update or create marker
                        val marker = markerRef.value ?: Marker(map).also {
                            it.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            map.overlays.add(it)
                            markerRef.value = it
                        }
                        marker.position = point
                        marker.title = callsign

                        // Animate to location on first fix, then follow
                        if (!hasInitialZoom) {
                            map.controller.setZoom(16.0)
                            map.controller.setCenter(point)
                            hasInitialZoom = true
                        } else {
                            map.controller.animateTo(point)
                        }
                        map.invalidate()
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(10.dp)
                        .clipToBounds()
                        .border(1.dp, PanelBorder)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            Configuration.getInstance().userAgentValue = ctx.packageName
                            MapView(ctx).apply {
                                // CartoDB Dark Matter tiles
                                setTileSource(XYTileSource(
                                    "CartoDB_DarkMatter",
                                    0, 19, 256, ".png",
                                    arrayOf(
                                        "https://a.basemaps.cartocdn.com/dark_all/",
                                        "https://b.basemaps.cartocdn.com/dark_all/",
                                        "https://c.basemaps.cartocdn.com/dark_all/"
                                    )
                                ))
                                setMultiTouchControls(true)
                                controller.setZoom(3.0)
                                controller.setCenter(GeoPoint(0.0, 0.0))
                                mapViewRef.value = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Server status badge
            ServerStatusBadge(
                summary = connectionSummary,
                state = connectionState,
                lastTxTime = lastTxTime,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            )

            // Powered by GoTAK
            val uriHandler = LocalUriHandler.current
            Text(
                text = "Powered by GoTAK",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
                    .background(PanelBlack.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
                    .clickable { uriHandler.openUri("https://getgotak.com") }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    // Permission rationale dialog
    if (showPermissionRationale && !hasLocationPermission) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Location Permission Required") },
            text = {
                Text("OpenTAK Tracker needs precise location access to transmit your position to the TAK server. Without this permission, tracking cannot start.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationale = false
                    locationPermissions.launchMultiplePermissionRequest()
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // PIN unlock dialog
    if (showPinDialog) {
        var pinEntry by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pendingAction = null
            },
            title = { Text("Enter PIN to Unlock") },
            text = {
                Column {
                    OutlinedTextField(
                        value = pinEntry,
                        onValueChange = {
                            if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                pinEntry = it
                                pinError = false
                            }
                        },
                        label = { Text("4-digit PIN") },
                        singleLine = true,
                        isError = pinError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) {
                        Text("Incorrect PIN", color = ErrorRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.unlock(pinEntry)) {
                        showPinDialog = false
                        pendingAction?.invoke()
                        pendingAction = null
                    } else {
                        pinError = true
                        pinEntry = ""
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pendingAction = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sheets
    if (showSettings) {
        SettingsSheet(viewModel = viewModel, onDismiss = { showSettings = false })
    }
    if (showEmergency) {
        EmergencySheet(viewModel = viewModel, onDismiss = { showEmergency = false })
    }
    if (showLogs) {
        LogViewerSheet(viewModel = viewModel, onDismiss = { showLogs = false })
    }
    if (showServerManager) {
        ServerManagerSheet(viewModel = viewModel, onDismiss = { showServerManager = false })
    }
    if (showEnrollment) {
        EnrollmentSheet(
            viewModel = viewModel,
            initialParams = enrollmentParams,
            onDismiss = {
                showEnrollment = false
                enrollmentParams = null
            }
        )
    }
}

@Composable
fun LocationPanel(location: LocationData, format: CoordinateFormat, onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp)
            .background(PanelBlack)
            .border(1.dp, PanelBorder)
            .clickable { onTap() }
            .padding(12.dp)
    ) {
        Text("Location (${format.name})", color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))

        if (!location.isValid) {
            Text("---", color = TextWhite, fontSize = 28.sp)
        } else {
            when (format) {
                CoordinateFormat.DMS -> {
                    Text(CoordinateConverter.latToDMS(location.latitude), color = TextWhite, fontSize = 26.sp)
                    Text(CoordinateConverter.lonToDMS(location.longitude), color = TextWhite, fontSize = 26.sp)
                }
                CoordinateFormat.DECIMAL -> {
                    Row {
                        Text("Lat  ", color = TextSecondary, fontSize = 26.sp)
                        Text(CoordinateConverter.latToDecimal(location.latitude), color = TextWhite, fontSize = 26.sp)
                    }
                    Row {
                        Text("Lon  ", color = TextSecondary, fontSize = 26.sp)
                        Text(CoordinateConverter.lonToDecimal(location.longitude), color = TextWhite, fontSize = 26.sp)
                    }
                }
                CoordinateFormat.MGRS -> {
                    Text(
                        CoordinateConverter.toMGRS(location.latitude, location.longitude),
                        color = TextWhite,
                        fontSize = 26.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DataPanel(title: String, subtitle: String, value: String, modifier: Modifier, onTap: () -> Unit) {
    Column(
        modifier = modifier
            .background(PanelBlack)
            .border(1.dp, PanelBorder)
            .clickable { onTap() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = TextWhite, fontSize = 12.sp)
        Text(subtitle, color = TextSecondary, fontSize = 10.sp)
        Text(value, color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ServerStatusBadge(summary: String, state: ConnectionState, lastTxTime: Long, modifier: Modifier) {
    val color = when (state) {
        ConnectionState.CONNECTED, ConnectionState.SENDING -> ConnectedGreen
        ConnectionState.CONNECTING -> WarningYellow
        ConnectionState.RECONNECTING -> ReconnectingOrange
        ConnectionState.FAILED -> ErrorRed
        ConnectionState.DISCONNECTED -> TextSecondary
    }

    val timeStr = if (lastTxTime > 0) {
        " | ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastTxTime))}"
    } else ""

    Text(
        text = "$summary$timeStr",
        color = color,
        fontSize = 13.sp,
        modifier = modifier
            .background(PanelBlack.copy(alpha = 0.9f), RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
