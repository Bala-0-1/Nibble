package com.nibble.app.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nibble.app.core.data.AppState
import com.nibble.app.core.data.Event
import com.nibble.app.core.design.ClayButton
import com.nibble.app.core.design.CookieIcon
import com.nibble.app.core.design.LocalNibbleColors
import com.nibble.app.core.design.LocalNibbleType
import com.nibble.app.core.design.QuietButton
import com.nibble.app.core.design.tap
import com.nibble.app.core.domain.AttentionTier
import com.nibble.app.core.domain.Copy
import com.nibble.app.core.domain.Wit
import kotlin.random.Random

/** 2a — Onboarding · the diagnosis. Deadpan diagnosis register (§11). */
@Composable
fun DiagnosisScreen(state: AppState, onEvent: (Event) -> Unit) {
    val c = LocalNibbleColors.current
    val type = LocalNibbleType.current
    Column(
        Modifier.fillMaxSize().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp),
    ) {
        // Brand
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(c.clay),
                contentAlignment = Alignment.Center,
            ) { CookieIcon(c.onClay, Modifier.size(15.dp)) }
            Text("NIBBLE", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.14.em)
        }
        Spacer(Modifier.size(22.dp))
        Text(Copy.onboardingTitle, style = type.display.copy(fontSize = 37.sp, lineHeight = 40.sp), color = c.ink)
        Spacer(Modifier.size(10.dp))
        Text(Wit.onboardingSub.resolve(state.voice, remember { Random.nextInt(99) }), color = c.inkMuted, fontSize = 14.5.sp, style = type.body)
        Spacer(Modifier.size(18.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AttentionTier.entries.forEach { tier ->
                TierOption(
                    tier = tier,
                    selected = state.tier == tier,
                    onClick = { onEvent(Event.SelectTier(tier)) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Prescription (Ink tone)
        Row(
            Modifier.fillMaxWidth().padding(top = 18.dp)
                .clip(RoundedCornerShape(14.dp)).background(c.ink).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(Icons.Filled.Timer, null, tint = c.gold, modifier = Modifier.size(18.dp))
            val presc = androidx.compose.ui.text.buildAnnotatedString {
                append("Prescription: ")
                pushStyle(androidx.compose.ui.text.SpanStyle(color = c.paper, fontWeight = FontWeight.Bold))
                append("2-minute nibbles. ")
                pop()
                append(Copy.prescription)
            }
            Text(presc, color = c.paper.copy(alpha = 0.85f), fontSize = 13.sp, style = type.body)
        }
        Spacer(Modifier.size(16.dp))
        ClayButton("Diagnose me", onClick = { onEvent(Event.Diagnose) }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(16.dp))
        QuietButton("Skip — I already know", onClick = { onEvent(Event.SkipDiagnosis) })
    }
}

@Composable
private fun TierOption(tier: AttentionTier, selected: Boolean, onClick: () -> Unit) {
    val c = LocalNibbleColors.current
    val shape = RoundedCornerShape(16.dp)
    val bg = if (selected) c.clayWash else c.card
    val base = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).clip(shape).background(bg)
    val withBorder = if (selected) base.border(2.dp, c.clay, shape) else base.border(1.dp, c.rule, shape)
    Row(
        withBorder.tap(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(20.dp).clip(CircleShape)
                .border(if (selected) 6.dp else 2.dp, if (selected) c.clay else c.inkFaint.copy(alpha = 0.6f), CircleShape),
        )
        Column {
            Text(
                tier.label, color = c.ink, fontSize = 15.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Text(tier.sub, color = if (selected) c.clayPress else c.inkFaint, fontSize = 12.5.sp)
        }
    }
}
