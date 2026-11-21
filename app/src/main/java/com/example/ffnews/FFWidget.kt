package com.example.ffnews

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class FFWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("ff", Context.MODE_PRIVATE)
        val txt = withContext(Dispatchers.IO) {
            runCatching { download().also { prefs.edit().putString("cache", it).apply() } }.getOrNull()
        } ?: prefs.getString("cache", null)
        val now = ZonedDateTime.now()
        val list = txt?.let { runCatching { parse(it) }.getOrNull() }.orEmpty()
            .filter { it.impact != "Low" && it.time.isAfter(now.minusMinutes(30)) }
            .take(6)
        val updated = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        provideContent { WidgetContent(list, updated) }
    }
}

@Composable
private fun WidgetContent(list: List<Ev>, updated: String) {
    val context = androidx.glance.LocalContext.current
    val white = ColorProvider(Color.White)
    val gray = ColorProvider(Color(0xFFB0B0B0))
    Column(
        GlanceModifier.fillMaxSize()
            .background(ColorProvider(Color(0xE6121212)))
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java)))
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("FF News", style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 14.sp))
            Text("  $updated", style = TextStyle(color = gray, fontSize = 11.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(
                "  ↻  ",
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                style = TextStyle(color = white, fontSize = 18.sp)
            )
        }
        Spacer(GlanceModifier.padding(top = 4.dp))
        if (list.isEmpty()) {
            Text("Không có tin sắp tới", style = TextStyle(color = gray, fontSize = 12.sp))
        }
        list.forEach { e ->
            Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("● ", style = TextStyle(color = ColorProvider(impactColor(e.impact)), fontSize = 12.sp))
                Text(
                    e.time.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " ",
                    style = TextStyle(color = white, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                )
                Text(e.country + " ", style = TextStyle(color = gray, fontSize = 12.sp))
                Text(e.title, maxLines = 1, style = TextStyle(color = white, fontSize = 12.sp))
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        FFWidget().update(context, glanceId)
    }
}

class FFWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FFWidget()
}
