@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ffnews

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val FEED = "https://nfs.faireconomy.media/ff_calendar_thisweek.json"

data class Ev(
    val title: String,
    val country: String,
    val time: ZonedDateTime,
    val impact: String,
    val forecast: String,
    val previous: String
)

fun impactColor(i: String) = when (i) {
    "High" -> Color(0xFFE53935)
    "Medium" -> Color(0xFFFB8C00)
    "Low" -> Color(0xFFFDD835)
    else -> Color.Gray
}

fun impactLabel(i: String) = when (i) {
    "High" -> "Mạnh"
    "Medium" -> "Vừa"
    "Low" -> "Yếu"
    else -> i
}

fun parse(json: String): List<Ev> {
    val arr = JSONArray(json)
    val zone = ZoneId.systemDefault()
    return (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
        runCatching {
            Ev(
                o.getString("title"),
                o.getString("country"),
                OffsetDateTime.parse(o.getString("date")).atZoneSameInstant(zone),
                o.optString("impact"),
                o.optString("forecast"),
                o.optString("previous")
            )
        }.getOrNull()
    }.filter { it.impact in listOf("High", "Medium", "Low") }.sortedBy { it.time }
}

fun download(): String {
    val c = URL(FEED).openConnection() as HttpURLConnection
    c.setRequestProperty("User-Agent", "Mozilla/5.0")
    c.connectTimeout = 15000
    c.readTimeout = 15000
    if (c.responseCode != 200) error("HTTP ${c.responseCode}")
    return c.inputStream.bufferedReader().use { it.readText() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("ff", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var events by remember { mutableStateOf<List<Ev>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(setOf("High", "Medium")) }

    fun load() {
        scope.launch {
            loading = true; msg = null
            try {
                val txt = withContext(Dispatchers.IO) { download() }
                prefs.edit().putString("cache", txt).apply()
                events = parse(txt)
            } catch (e: Exception) {
                val cache = prefs.getString("cache", null)
                if (cache != null) {
                    events = parse(cache)
                    msg = "Không tải được, đang hiển thị dữ liệu đã lưu (${e.message})"
                } else msg = "Lỗi tải dữ liệu: ${e.message}"
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        prefs.getString("cache", null)?.let { events = runCatching { parse(it) }.getOrDefault(emptyList()) }
        load()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Forex Factory News") },
                actions = {
                    IconButton(onClick = { load() }) { Icon(Icons.Default.Refresh, "Tải lại") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("High", "Medium", "Low").forEach { k ->
                    FilterChip(
                        selected = k in filter,
                        onClick = { filter = if (k in filter) filter - k else filter + k },
                        label = { Text(impactLabel(k)) },
                        leadingIcon = {
                            Box(Modifier.size(10.dp).background(impactColor(k), CircleShape))
                        }
                    )
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            msg?.let { Text(it, color = Color(0xFFFFB74D), modifier = Modifier.padding(12.dp), fontSize = 12.sp) }

            val grouped = events.filter { it.impact in filter }.groupBy { it.time.toLocalDate() }
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                grouped.forEach { (day, list) ->
                    item(key = day.toString()) { DayHeader(day) }
                    items(list) { EventCard(it) }
                }
                if (grouped.isEmpty() && !loading) {
                    item { Text("Không có tin nào.", modifier = Modifier.padding(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun DayHeader(d: LocalDate) {
    val fmt = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy")
    Text(
        d.format(fmt),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
fun EventCard(e: Ev) {
    Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 3.dp) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(impactColor(e.impact)))
            Column(Modifier.padding(10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.time.format(DateTimeFormatter.ofPattern("HH:mm")), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(e.country, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text(impactLabel(e.impact), color = impactColor(e.impact), fontSize = 12.sp)
                }
                Text(e.title, modifier = Modifier.padding(vertical = 4.dp))
                Row {
                    Text("Dự báo: ${e.forecast.ifBlank { "-" }}", fontSize = 12.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("Trước đó: ${e.previous.ifBlank { "-" }}", fontSize = 12.sp)
                }
            }
        }
    }
}
