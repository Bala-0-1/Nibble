package com.nibble.app.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.nibble.app.core.data.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

/** Home-screen widget (§9) rendered from the shared snapshot, never the DB. */
class NibbleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = readWidgetData(context)
        provideContent { WidgetContent(data) }
    }
}

class NibbleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NibbleWidget()
}

private val widgetJson = Json { ignoreUnknownKeys = true }

private fun readWidgetData(context: Context): WidgetData? = runCatching {
    val f = File(context.filesDir, "nibble_widget.json")
    if (!f.exists()) null else widgetJson.decodeFromString<WidgetData>(f.readText())
}.getOrNull()

@Composable
private fun WidgetContent(data: WidgetData?) {
    val onBg = ColorProvider(Color(0xFFF7F2E8))
    val muted = ColorProvider(Color(0xFFB3A99A))
    val gold = ColorProvider(Color(0xFFD9A85C))
    val uri = Uri.parse("nibble://read/${data?.bookId ?: "jekyll"}")
    Column(
        GlanceModifier.fillMaxSize()
            .background(Color(0xFF221E1A))
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, uri))),
    ) {
        Text("TODAY'S NIBBLE", style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        Spacer(GlanceModifier.height(6.dp))
        Text(
            data?.chapter ?: "Open Nibble",
            style = TextStyle(color = onBg, fontSize = 15.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            data?.excerpt ?: "Pick a book and start a two-minute nibble.",
            style = TextStyle(color = muted, fontSize = 13.sp),
            maxLines = 3,
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            if (data != null) "${data.minutesLeft} min left · ${data.title}" else "",
            style = TextStyle(color = gold, fontSize = 11.sp),
            maxLines = 1,
        )
    }
}

/** Ask the widget host to re-render after the snapshot changes. */
fun refreshNibbleWidget(context: Context) {
    CoroutineScope(Dispatchers.Default).launch {
        runCatching { NibbleWidget().updateAll(context) }
    }
}
