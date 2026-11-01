package ru.granta.carlauncher

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.granta.carlauncher.services.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestCarPermissions()
        startService(Intent(this, GpsSpeedService::class.java))

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GrantaLauncherScreen()
            }
        }
    }

    private fun requestCarPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
    }
}

@Composable
fun GrantaLauncherScreen() {
    val context = LocalContext.current
    val gps by GpsSpeedService.telemetry.collectAsState()
    val media by CarMediaAndNavListener.mediaState.collectAsState()
    val nav by CarMediaAndNavListener.navState.collectAsState()
    val obd by ObdElm327Manager.obdState.collectAsState()

    var isRightDrawerOpen by remember { mutableStateOf(false) }
    var rightDrawerTab by remember { mutableStateOf("QUEUE") } // "QUEUE" или "RADIO"
    var isAppPickerOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1C1E))
            .padding(16.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    if (dragAmount < -25f) isRightDrawerOpen = true
                    if (dragAmount > 25f) isRightDrawerOpen = false
                }
            }
    ) {
        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {

            // ЛЕВАЯ КОЛОНКА (Часы + GPS Скорость, Яндекс Навигатор, Настраиваемый Док Иконок)
            Column(
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Карточка Времени, Погоды и GPS-Спидометра
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF212427),
                    modifier = Modifier.fillMaxWidth().weight(0.31f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.SpaceBetween) {
                            Text("10:35 AM", fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Сб, 3 Окт, 2026", fontSize = 15.sp, color = Color(0xFFB8BDC2))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("🌙 Ясно, 18°C • ${obd.batteryVoltage}В • ${obd.coolantTempC}°C", fontSize = 14.sp, color = Color(0xFFD0D5DD))
                        }
                        // Блок GPS-спидометра
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (gps.speedKmh > nav.speedLimitKmh + 19) Color(0xFF5C1B1B) else Color(0xFF15181B),
                            modifier = Modifier.size(92.dp, 86.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "${gps.speedKmh}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF4ADE80)
                                )
                                Text("КМ/Ч GPS", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                        }
                    }
                }

                // 2. Карточка Яндекс Навигатора
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF212427),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.28f)
                        .clickable {
                            AppDockRepository(context).launchApp("ru.yandex.yandexnavi")
                        }
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("Яндекс Навигатор", fontSize = 13.sp, color = Color(0xFF9CA3AF))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF16A34A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TurnLeft, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                                }
                                Column {
                                    Text(nav.distanceText, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(nav.instruction, fontSize = 17.sp, color = Color(0xFFD1D5DB))
                                }
                            }
                            // Знак ограничения скорости
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626))
                                    .padding(4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${nav.speedLimitKmh}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                        }
                        Text(nav.etaSummary, fontSize = 13.sp, color = Color(0xFF9CA3AF))
                    }
                }

                // 3. Карточка Иконок Существующих Приложений (слева снизу + кнопка ДОБАВИТЬ)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF212427),
                    modifier = Modifier.fillMaxWidth().weight(0.41f)
                ) {
                    val dockItems = listOf(
                        Triple("РАДИО", Icons.Default.Radio, "com.ts.MainUI"),
                        Triple("МУЗЫКА", Icons.Default.MusicNote, "ru.yandex.music"),
                        Triple("КАРТЫ", Icons.Default.LocationOn, "ru.yandex.yandexnavi"),
                        Triple("ТЕЛЕФОН", Icons.Default.Phone, "com.android.dialer"),
                        Triple("ОБД2", Icons.Default.Build, "org.prowl.torque"),
                        Triple("НАСТРОЙКИ", Icons.Default.Settings, "com.android.settings"),
                        Triple("+ ИКОНКА", Icons.Default.AddCircleOutline, "ADD_APP"),
                        Triple("ВСЕ ПРИЛ.", Icons.Default.Apps, "ALL_APPS")
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(dockItems) { (title, icon, pkg) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    if (pkg == "ADD_APP" || pkg == "ALL_APPS") {
                                        isAppPickerOpen = true
                                    } else {
                                        AppDockRepository(context).launchApp(pkg)
                                    }
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFFF3F4F6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = title, tint = Color(0xFF1F2937), modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(title, fontSize = 10.sp, color = Color(0xFFD1D5DB), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ПРАВАЯ КОЛОНКА (Плеер сверху + Автомобиль на подиуме + Свайп-панель справа)
            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхний плеер (Bluetooth AVRCP / FM Радио)
                Text(
                    text = media.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Slider(
                        value = media.positionSec.toFloat(),
                        onValueChange = { CarMediaAndNavListener.seekTo((it * 1000).toLong()) },
                        valueRange = 0f..media.durationSec.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { CarMediaAndNavListener.previous() }) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { CarMediaAndNavListener.playPause() }) {
                        Icon(
                            if (media.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "PlayPause",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    IconButton(onClick = { CarMediaAndNavListener.next() }) {
                        Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    // Быстрые кнопки открытия свайп-панели или перехода на радио
                    FilledTonalButton(
                        onClick = {
                            rightDrawerTab = "RADIO"
                            isRightDrawerOpen = true
                            CarMediaAndNavListener.switchToFmRadio(context)
                        }
                    ) {
                        Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("На Радио")
                    }
                }

                // Изображение автомобиля на подиуме
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(id = R.drawable.lada_granta_stage),
                        contentDescription = "Lada Granta Stage",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // ВЫЕЗЖАЮЩАЯ СВАЙПОМ СПРАВА ПАНЕЛЬ (Очередь следующих песен + Быстрый переход на FM-Радио)
        AnimatedVisibility(
            visible = isRightDrawerOpen,
            enter = slideInHorizontally(initialOffsetX = { it }),
            exit = slideOutHorizontally(targetOffsetX = { it }),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp),
                color = Color(0xF21E2124),
                tonalElevation = 12.dp,
                modifier = Modifier.width(380.dp).fillMaxHeight()
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = rightDrawerTab == "QUEUE",
                                onClick = { rightDrawerTab = "QUEUE" },
                                label = { Text("Следующие песни") }
                            )
                            FilterChip(
                                selected = rightDrawerTab == "RADIO",
                                onClick = { rightDrawerTab = "RADIO" },
                                label = { Text("FM Радио") }
                            )
                        }
                        IconButton(onClick = { isRightDrawerOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (rightDrawerTab == "QUEUE") {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(media.queue) { index, item ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color(0xFF2A2E33),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { CarMediaAndNavListener.skipToQueueItem(item.queueId) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${index + 1}. ${item.artist} - ${item.title}", color = Color.White, fontSize = 15.sp)
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color(0xFF4ADE80))
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Быстрый выбор FM-станции:", color = Color(0xFF9CA3AF), fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        val stations = listOf(
                            "101.7 FM — Наше Радио" to 101.7f,
                            "104.2 FM — Радио Energy" to 104.2f,
                            "106.2 FM — Европа Плюс" to 106.2f,
                            "95.2 FM — Rock FM" to 95.2f,
                            "88.3 FM — Ретро FM" to 88.3f
                        )
                        stations.forEach { (name, freq) ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF2A2E33),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { CarMediaAndNavListener.switchToFmRadio(context, freq) }
                            ) {
                                Text(name, color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
