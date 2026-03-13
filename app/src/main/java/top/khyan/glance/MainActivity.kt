package top.khyan.glance

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import top.khyan.glance.notifications.Glimpse
import top.khyan.glance.notifications.GlimpseStore
import top.khyan.glance.notifications.LiveUpdateDeletedReceiver
import top.khyan.glance.notifications.LiveUpdateNotificationManager
import top.khyan.glance.notifications.LiveUpdateNotificationManager.Companion.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS
import top.khyan.glance.ui.theme.GlanceTheme

class MainActivity : ComponentActivity() {
    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        restoreLiveUpdates(this)
        setContent {
            GlanceTheme {
                GlanceApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun GlanceApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val context = LocalContext.current
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* 用户已做出选择，无需额外处理 */ }
        LaunchedEffect(Unit) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        MaterialSymbol(
                            name = it.symbol,
                            contentDescription = it.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(contentModifier)
                AppDestinations.SCHEDULE -> ScheduleScreen(contentModifier)
                AppDestinations.SETTINGS -> SettingsScreen(contentModifier)
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val symbol: String,
) {
    HOME("首页", "home"),
    SCHEDULE("日程提醒", "calendar_month"),
    SETTINGS("设置", "settings"),
}

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { LiveUpdateNotificationManager(context) }
    val store = remember(context) { GlimpseStore(context) }

    var glimpses by remember { mutableStateOf(store.getAll()) }
    var notificationIdInput by remember { mutableStateOf(store.nextAvailableId().toString()) }
    var liveTitle by remember { mutableStateOf("") }
    var liveText by remember { mutableStateOf("") }
    var progressValue by remember { mutableStateOf(-1f) }
    var statusMessage by remember { mutableStateOf("") }

    DisposableEffect(store) {
        val listener = store.registerChangeListener {
            glimpses = store.getAll()
            notificationIdInput = store.nextAvailableId().toString()
        }
        onDispose {
            store.unregisterChangeListener(listener)
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == LiveUpdateDeletedReceiver.ACTION_GLIMPSE_STORE_CHANGED) {
                    glimpses = store.getAll()
                    statusMessage = "实时活动已随通知划除同步删除"
                    notificationIdInput = store.nextAvailableId().toString()
                }
            }
        }
        val filter = IntentFilter(LiveUpdateDeletedReceiver.ACTION_GLIMPSE_STORE_CHANGED)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    fun resetForm() {
        glimpses = store.getAll()
        notificationIdInput = store.nextAvailableId().toString()
        liveTitle = ""
        liveText = ""
        progressValue = -1f
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "首页",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "欢迎来到 Glance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // ── 控制卡片 ──────────────────────────────────────────
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = "管理实时更新通知", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "创建或移除实时更新通知，在你的 Android 16 设备上保持最新。",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = notificationIdInput,
                    onValueChange = { notificationIdInput = it.filter(Char::isDigit) },
                    label = { Text("通知 ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = liveTitle,
                    onValueChange = { liveTitle = it },
                    label = { Text("标题") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = liveText,
                    onValueChange = { liveText = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )

                Text(text = "进度: ${if (progressValue < 0f) "不显示" else "${progressValue.toInt()}%"}")
                Slider(
                    value = progressValue,
                    onValueChange = { progressValue = it },
                    valueRange = -1f..100f,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val id = notificationIdInput.toIntOrNull()
                            if (id == null || liveTitle.isBlank()) {
                                statusMessage = "请输入有效通知 ID 和标题"
                                return@Button
                            }
                            val glimpse = Glimpse(
                                id = id,
                                title = liveTitle,
                                text = liveText,
                                progress = progressValue.toInt(),
                            )
                            val posted = if (ActivityCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) != PackageManager.PERMISSION_GRANTED
                            ) false else
                                manager.createLiveUpdate(
                                    notificationId = id,
                                    content = glimpse.toLiveUpdateContent(),
                                )
                            if (posted) {
                                store.save(glimpse)
                                statusMessage = "已创建实时更新通知 #$id"
                                resetForm()
                            } else {
                                statusMessage = "创建失败：请检查通知权限"
                            }
                        }
                    ) {
                        Text("创建")
                    }

                    TextButton(
                        onClick = {
                            val id = notificationIdInput.toIntOrNull()
                            if (id == null) {
                                statusMessage = "请输入有效通知 ID"
                                return@TextButton
                            }
                            manager.cancelLiveUpdate(id)
                            store.delete(id)
                            statusMessage = "已移除实时更新通知 #$id"
                            resetForm()
                        }
                    ) {
                        Text("移除")
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // ── 列表卡片 ──────────────────────────────────────────
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "现存实时更新通知", style = MaterialTheme.typography.titleMedium)
                    TextButton(
                        onClick = {
                            glimpses.forEach { manager.cancelLiveUpdate(it.id) }
                            store.clear()
                            glimpses = emptyList()
                            statusMessage = "已移除所有实时更新通知"
                        },
                        enabled = glimpses.isNotEmpty(),
                    ) {
                        Text("全部移除")
                    }
                }

                if (glimpses.isEmpty()) {
                    Text(
                        text = "没有实时更新通知",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    glimpses.forEachIndexed { index, glimpse ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = glimpse.toLabel(),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = {
                                notificationIdInput = glimpse.id.toString()
                                liveTitle = glimpse.title
                                liveText = glimpse.text
                                progressValue = glimpse.progress.toFloat()
                                statusMessage = ""
                            }) { Text("编辑") }
                            TextButton(onClick = {
                                manager.cancelLiveUpdate(glimpse.id)
                                store.delete(glimpse.id)
                                glimpses = store.getAll()
                                statusMessage = "已移除实时更新通知 #${glimpse.id}"
                            }) { Text("移除") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    ScreenSection(
        title = "日程提醒",
        symbol = AppDestinations.SCHEDULE.symbol,
        subtitle = "获悉现在和接下来的安排",
        body = "在这里配置日程提醒。",
        modifier = modifier
    )
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var statusMessage by rememberSaveable { mutableStateOf("") }
    val manager = remember(context) { LiveUpdateNotificationManager(context) }

    Column(modifier = modifier) {
        ScreenSection(
            title = "设置",
            symbol = AppDestinations.SETTINGS.symbol,
            subtitle = "应用与偏好配置",
            body = "这里可以调整个性化的选项。",
        )
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = {
                    val started = runCatching {
                        try {
                            context.startActivity(Intent(ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: ActivityNotFoundException) {
                            context.startActivity(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }.isSuccess
                    statusMessage =
                        if (started) "已打开通知设置" else "无法打开系统设置，请手动前往应用通知设置"
                }
            ) {
                Text("打开实时更新系统设置")
            }
            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ScreenSection(
    title: String,
    symbol: String,
    subtitle: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MaterialSymbol(
                    name = symbol,
                    contentDescription = title,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    GlanceTheme {
        HomeScreen()
    }
}