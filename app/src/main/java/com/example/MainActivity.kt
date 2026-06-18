package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.SleepAppUsage
import com.example.data.SleepSession
import com.example.service.TimerState
import com.example.ui.AppUsageDetail
import com.example.ui.SleepViewModel
import com.example.ui.components.CircularSlider
import com.example.ui.theme.DarkSurface
import com.example.ui.components.CircularSlider
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.MidnightBackground
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.CozyCyan
import com.example.ui.theme.RelaxingViolet
import com.example.ui.theme.CozyAmber
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.foundation.BorderStroke
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MidnightBackground
                ) { innerPadding ->
                    SleepTimerApp(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val viewModel: SleepViewModel = viewModel()

    // Observe stats and statuses
    val timerState by viewModel.timerState.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val totalDurationSeconds by viewModel.totalDurationSeconds.collectAsState()
    val isUsageGranted by viewModel.isUsagePermissionGranted.collectAsState()
    val isNotificationListenerGranted by viewModel.isNotificationListenerGranted.collectAsState()

    // Selected tab state (0: Timer, 1: Statistik)
    var selectedTab by remember { mutableStateOf(0) }
    val pagerState = rememberPagerState(pageCount = { 2 })

    // Sync selectedTab -> Pager
    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab) {
            pagerState.animateScrollToPage(selectedTab)
        }
    }

    // Sync Pager -> selectedTab
    LaunchedEffect(pagerState.currentPage) {
        selectedTab = pagerState.currentPage
    }

    // Request Notification permission (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Notifikasi dinonaktifkan. Anda tidak akan melihat sisa waktu di bar status.", Toast.LENGTH_LONG).show().also {}
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
        viewModel.refreshStats()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Refresh usage stats whenever we enter the Statistics Tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.refreshStats()
        }
    }

    Column(modifier = modifier) {
        // App header with zen twilight banner background
        HeaderBanner()

        // Tab selection capsule
        TabCapsuleSelector(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        )

        // Tab Content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) { page ->
            if (page == 0) {
                TimerTabContent(
                    viewModel = viewModel,
                    timerState = timerState,
                    remainingSeconds = remainingSeconds,
                    totalSeconds = totalDurationSeconds,
                    isNotificationListenerGranted = isNotificationListenerGranted,
                    context = context
                )
            } else {
                StatisticsTabContent(
                    viewModel = viewModel,
                    isUsageGranted = isUsageGranted,
                    context = context
                )
            }
        }
    }
}

@Composable
fun HeaderBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF13172E),
                        MidnightBackground
                    )
                )
            )
    ) {
        // Draw moon and star particles using simple premium geometry
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Star clusters
            val stars = listOf(
                Offset(width * 0.15f, height * 0.25f),
                Offset(width * 0.22f, height * 0.55f),
                Offset(width * 0.45f, height * 0.15f),
                Offset(width * 0.78f, height * 0.35f),
                Offset(width * 0.88f, height * 0.65f),
                Offset(width * 0.62f, height * 0.45f)
            )

            for (star in stars) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f),
                    radius = 3.dp.toPx(),
                    center = star
                )
            }

            // Draw a beautiful elegant glowing crescent moon
            drawCircle(
                color = Color(0xFFFFE082).copy(alpha = 0.85f),
                radius = 32.dp.toPx(),
                center = Offset(width * 0.85f, height * 0.45f)
            )
            // Moon shadow subtraction overlay
            drawCircle(
                color = Color(0xFF13172E),
                radius = 28.dp.toPx(),
                center = Offset(width * 0.81f, height * 0.40f)
            )
        }

        // Title text
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 12.dp)
        ) {
            Text(
                text = "Sleep Timer",
                fontSize = 28.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "Istirahat tenang, hemat daya maksimal",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
fun TabCapsuleSelector(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurface)
            .padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TabCapsuleButton(
                title = "Sleep Timer",
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                icon = Icons.Default.PlayArrow,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_timer")
            )
            TabCapsuleButton(
                title = "Usage & Histori",
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                icon = Icons.Default.DateRange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tab_stats")
            )
        }
    }
}

@Composable
fun TabCapsuleButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "tab_color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MidnightBackground else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tab_content_color"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerTabContent(
    viewModel: SleepViewModel,
    timerState: TimerState,
    remainingSeconds: Int,
    totalSeconds: Int,
    isNotificationListenerGranted: Boolean,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        when (timerState) {
            TimerState.IDLE -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 8.dp)
                ) {
                    if (!isNotificationListenerGranted) {
                        item {
                            PermissionBanner(
                                title = "Akses Notifikasi Diperlukan",
                                description = "Izinkan aplikasi untuk mencatat judul lagu/video yang diputar saat tidur.",
                                buttonText = "AKTIFKAN AKSES MEDIA",
                                onAction = {
                                    context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                                }
                            )
                        }
                    }

                    item {
                        DurationSetupCard(viewModel = viewModel)
                    }

                    item {
                        SystemTogglesCard(viewModel = viewModel, context = context)
                    }

                    item {
                        AppKillSelectorCard(viewModel = viewModel)
                    }
                }

                // Sticky big floating action button
                Button(
                    onClick = { viewModel.startSleepTimer() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .testTag("button_start_timer"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Mulai Sleep Timer",
                        tint = MidnightBackground,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MULAI SLEEP TIMER",
                        color = MidnightBackground,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            TimerState.RUNNING -> {
                RunningTimerPanel(
                    remainingSeconds = remainingSeconds,
                    totalSeconds = totalSeconds,
                    onCancelClick = { viewModel.stopSleepTimer() },
                    viewModel = viewModel
                )
            }

            TimerState.FINISHED -> {
                FinishedSleepPanel(
                    onDismiss = { viewModel.resetTimerState() }
                )
            }
        }
    }
}

@Composable
fun DurationSetupCard(viewModel: SleepViewModel) {
    val durationMinutes by viewModel.selectedDurationMinutes.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Durasi Sleep",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Custom Circular Slider
            CircularSlider(
                value = durationMinutes.toFloat(),
                onValueChange = { viewModel.setDuration(it.toInt()) },
                valueRange = 1f..180f,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = DarkSurfaceVariant,
                centerLabel = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "$durationMinutes",
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Menit",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Short predefines
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shortcuts = listOf(15, 30, 45, 60, 90)
                shortcuts.forEach { mins ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (durationMinutes == mins) MaterialTheme.colorScheme.primary else DarkSurfaceVariant)
                            .clickable { viewModel.setDuration(mins) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${mins}m",
                            color = if (durationMinutes == mins) MidnightBackground else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SystemTogglesCard(viewModel: SleepViewModel, context: Context) {
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "Integrasi Sistem & Hemat Daya",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Toggle 1: Media Kill Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Force Terminate (Apps)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Hentikan caching background ram player pilihan saat tidur.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = killSwitchEnabled,
                    onCheckedChange = { viewModel.toggleKillSwitch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}

@Composable
fun AppKillSelectorCard(viewModel: SleepViewModel) {
    val lists by viewModel.userInstalledApps.collectAsState()
    val selections by viewModel.selectedAppsToKill.collectAsState()
    val killSwitchEnabled by viewModel.killSwitchEnabled.collectAsState()

    AnimatedVisibility(
        visible = killSwitchEnabled,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Target Tutup Aplikasi",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        @SuppressLint("DefaultLocale")
                        Text(
                            text = "${selections.size} Aplikasi Dipilih",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Daftar Aplikasi",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (lists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(lists) { app ->
                                val selected = selections.contains(app.packageName)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) DarkSurfaceVariant else Color.Transparent)
                                        .clickable { viewModel.toggleAppSelection(app.packageName) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Custom visual avatar for app logo
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    colors = listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                                        Color.Transparent
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when {
                                                app.packageName.contains("youtube") -> Icons.Default.PlayArrow
                                                app.packageName.contains("spotify") || app.packageName.contains("music") -> Icons.Default.PlayArrow
                                                app.packageName.contains("netflix") || app.packageName.contains("video") -> Icons.Default.PlayArrow
                                                app.packageName.contains("chrome") || app.packageName.contains("browser") -> Icons.Default.PlayArrow
                                                else -> Icons.Default.PlayArrow
                                            },
                                            contentDescription = null,
                                            tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 9.sp,
                                            color = TextSecondary.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { viewModel.toggleAppSelection(app.packageName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary,
                                            uncheckedColor = TextSecondary
                                        )
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

@SuppressLint("DefaultLocale")
@Composable
fun RunningTimerPanel(
    remainingSeconds: Int,
    totalSeconds: Int,
    onCancelClick: () -> Unit,
    viewModel: SleepViewModel
) {
    val durationMin by viewModel.selectedDurationMinutes.collectAsState()
    val isKillOn by viewModel.killSwitchEnabled.collectAsState()

    // Breathing wave animation helpers
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Timer Sedang Berjalan",
            fontSize = 18.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "Cahaya hp meredup, tarik nafas perlahan...",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        // Visual Countdown Ticker Ring
        Box(
            modifier = Modifier
                .size(240.dp)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background pulsing shadow ring
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                // Adjust drawing dimensions based on our animated scale to create a "breathing" effect
            ) {
                drawCircle(
                    color = CozyCyan.copy(alpha = 0.05f),
                    radius = (size.minDimension / 2) * pulseScale,
                    center = center
                )
            }

            // Custom stroke drawing
            val strokeColor = MaterialTheme.colorScheme.primary
            val remainingPercent = if (totalSeconds > 0) remainingSeconds.toFloat() / totalSeconds.toFloat() else 1f

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Background Track
                drawCircle(
                    color = DarkSurface,
                    radius = size.minDimension / 2,
                    style = Stroke(width = 12.dp.toPx())
                )

                // Progress Outline Arc
                drawArc(
                    color = strokeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * remainingPercent,
                    useCenter = false,
                    style = Stroke(width = 12.dp.toPx())
                )
            }

            // Time text
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val h = remainingSeconds / 3600
                val m = (remainingSeconds % 3600) / 60
                val s = remainingSeconds % 60
                val textTime = if (h > 0) {
                    String.format("%02d:%02d:%02d", h, m, s)
                } else {
                    String.format("%02d:%02d", m, s)
                }

                Text(
                    text = textTime,
                    fontSize = 38.sp,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Sisa Waktu",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Active details info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Aksi yang dijadwalkan:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionBulletItem("Hentikan Music", true, Modifier.weight(1f))
                    ActionBulletItem("Kill Apps", isKillOn, Modifier.weight(1f))
                    ActionBulletItem("Audio Fade", true, Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onCancelClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .width(180.dp)
                .height(48.dp)
                .testTag("button_cancel_timer"),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = null, tint = MidnightBackground)
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "BATALKAN",
                fontWeight = FontWeight.Bold,
                color = MidnightBackground
            )
        }
    }
}

@Composable
fun ActionBulletItem(title: String, active: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else DarkSurfaceVariant)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = if (active) MaterialTheme.colorScheme.primary else TextSecondary.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FinishedSleepPanel(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Timer Selesai",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "Tidur Nyenyak Terlaksana!",
            fontSize = 20.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Semua aktivitas media telah dihentikan total dan koneksi radio dinonaktifkan.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp)
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            modifier = Modifier.width(150.dp)
        ) {
            Text("Kembali", color = Color.White)
        }
    }
}

@Composable
fun StatisticsTabContent(
    viewModel: SleepViewModel,
    isUsageGranted: Boolean,
    context: Context
) {
    val weeklyStats by viewModel.weeklyUsageStatsFlow.collectAsState()
    val sessionLogs by viewModel.historySessions.collectAsState()
    val selectedSessionId by viewModel.selectedSessionIdForDetail.collectAsState()
    val activeDetails by viewModel.activeSessionDetailList.collectAsState()
    val isNotificationListenerGranted by viewModel.isNotificationListenerGranted.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        // Section: Usage permission checker banner
        if (!isUsageGranted) {
            item {
                PermissionRequestBanner(context = context)
            }
        }
        
        if (!isNotificationListenerGranted) {
            item {
                PermissionBanner(
                    title = "Akses Notifikasi Diperlukan",
                    description = "Izinkan aplikasi untuk mencatat judul lagu/video yang diputar saat tidur agar histori lebih detail.",
                    buttonText = "AKTIFKAN AKSES MEDIA",
                    onAction = {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }
                )
            }
        }

        // Section: Weekly usage insights (Device Health)
        item {
            WeeklyDeviceHealthCard(
                weeklyStats = weeklyStats,
                isUsageGranted = isUsageGranted
            )
        }

        // Section: Sleep History logs from database
        item {
            SleepLogsTitleHeader(onClearAll = { viewModel.clearHistory() })
        }

        if (sessionLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada histori sleep timer.\nMulai timer malam ini untuk melihat statistik tidur Anda!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(sessionLogs) { log ->
                val isSelected = selectedSessionId == log.id
                SleepLogItemCard(
                    log = log,
                    isSelected = isSelected,
                    appDetails = if (isSelected) activeDetails else emptyList(),
                    onClick = {
                        if (isSelected) {
                            viewModel.clearSessionSelection()
                        } else {
                            viewModel.loadSessionUsageDetails(log.id)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionBanner(
    title: String,
    description: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(buttonText, color = MidnightBackground, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionRequestBanner(context: Context) {
    PermissionBanner(
        title = "Izin Akses Diperlukan",
        description = "Izin 'Usage Access' diperlukan agar aplikasi dapat melacak statistik penggunaan hp mingguan Anda dan mendeteksi aktivitas pemutaran media saat tertidur.",
        buttonText = "AKTIFKAN IZIN STATISTIK",
        onAction = {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                context.startActivity(intent)
            }
        }
    )
}

@Composable
fun WeeklyDeviceHealthCard(
    weeklyStats: List<AppUsageDetail>,
    isUsageGranted: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Statistik Penggunaan Mingguan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!isUsageGranted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Izin akses belum diberikan.\nKesehatan statistik tidak dapat dikumpulkan.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (weeklyStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Mengumpulkan data statistik perangkatan...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                // Display app metrics horizontally as custom gorgeous bar logs
                val maxUsage = weeklyStats.firstOrNull()?.usageDurationMs ?: 1L
                weeklyStats.take(5).forEach { stat ->
                    val hrs = stat.usageDurationMs / 3600000.0
                    val textLabel = if (hrs >= 1) {
                        String.format("%.1f jam", hrs)
                    } else {
                        val mins = stat.usageDurationMs / 60000
                        "$mins mnt"
                    }

                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stat.appName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                text = textLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(DarkSurfaceVariant)
                        ) {
                            val ratio = stat.usageDurationMs.toFloat() / maxUsage.toFloat()
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(ratio)
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SleepLogsTitleHeader(onClearAll: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Histori Timer Tidur",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Hapus Semua",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onClearAll() }
                .padding(4.dp)
                .testTag("clear_history_log")
        )
    }
}

@Composable
fun SleepLogItemCard(
    log: SleepSession,
    isSelected: Boolean,
    appDetails: List<SleepAppUsage>,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val dateText = formatter.format(Date(log.startTimeMs))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .clickable { onClick() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Text(
                        text = "Durasi Terencana: ${log.durationMinutes} mnt",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (log.lastMediaTitle != null) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = "TERAKHIR DIPUTAR:",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "${log.lastMediaTitle}${if (log.lastMediaArtist != null) " • ${log.lastMediaArtist}" else ""}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CozyAmber,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Badge
                    if (log.endedSuccessfully) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "SELESAI",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "BATAL",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Icon(
                        imageVector = if (isSelected) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expanding sub details (App Usage during this Sleep Timer period!)
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = DarkSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Aplikasi Terdata Aktif Saat Timer:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    if (appDetails.isEmpty()) {
                        Text(
                            text = "Tidak terdeteksi aplikasi multimedia yang didiamkan menyala, atau izin pelacak penggunaan belum aktif.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        appDetails.forEach { item ->
                            val activeMins = item.usageDurationMs / 60000
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• ${item.appName}",
                                    fontSize = 11.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "$activeMins menit aktif",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // System actions recap
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurfaceVariant)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "Aksi Sistem: Wifi Off=${if (log.internetOffAttempted) "Ya" else "Tidak"}, Force Close=${log.appsKilledCount} aplikasi.",
                            fontSize = 9.sp,
                            color = TextSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
