package com.nibble.app.core.design

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Flat tap with no ripple — the design uses hard-edge press, not elevation/ripple. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
}

// ---- Buttons ------------------------------------------------------------------

/**
 * The one action affordance (§3). Solid clay with a 4dp hard bottom edge (clayPress);
 * pressing collapses the edge to 0 and translates the face down 4dp. No ripple.
 */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    trailing: ImageVector? = null,
    faceHeight: Int = 54,
    fontSize: Int = 16,
) {
    val c = LocalNibbleColors.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val target = if (pressed && enabled) 4.dp else 0.dp
    val animated by animateDpAsState(target)
    val down = if (com.nibble.app.LocalReduceMotion.current) target else animated
    val shape = RoundedCornerShape(14.dp)
    val face = if (enabled) c.clay else c.rule
    val edge = if (enabled) c.clayPress else c.rule

    Box(
        modifier
            .height((faceHeight + 4).dp)
            .clip(shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
    ) {
        // Hard bottom edge — its visible height collapses on press.
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(4.dp - down)
                .background(edge),
        )
        // The face, translated down as the edge collapses.
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .offset(y = down)
                .height(faceHeight.dp)
                .clip(shape)
                .background(face),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (leading != null) Icon(leading, null, tint = c.onClay, modifier = Modifier.size(18.dp))
                Text(
                    text,
                    color = c.onClay,
                    fontSize = fontSize.sp,
                    fontWeight = FontWeight.Bold,
                    style = LocalNibbleType.current.body,
                )
                if (trailing != null) Icon(trailing, null, tint = c.onClay, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Text-only decline action, ≥44dp target (§4). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalNibbleColors.current
    Box(
        modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp).tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = c.inkFaint, fontSize = 13.5.sp, style = LocalNibbleType.current.body)
    }
}

// ---- Surfaces -----------------------------------------------------------------

enum class CardTone { Paper, Ink, Moss }

@Composable
fun PaperCard(
    modifier: Modifier = Modifier,
    tone: CardTone = CardTone.Paper,
    radius: Int = 15,
    content: @Composable () -> Unit,
) {
    val c = LocalNibbleColors.current
    val shape = RoundedCornerShape(radius.dp)
    val bg = when (tone) {
        CardTone.Paper -> c.card
        CardTone.Ink -> c.ink
        CardTone.Moss -> c.moss
    }
    val base = Modifier.clip(shape).background(bg)
    val bordered = if (tone == CardTone.Paper) base.border(1.dp, c.rule, shape) else base
    Box(modifier.then(bordered)) { content() }
}

// ---- Book cover ---------------------------------------------------------------

/** 3:4 cover: the real extracted image when one exists, else the typographic card (§4). */
@Composable
fun BookCover(
    title: String,
    ground: Color,
    accent: Color,
    onColor: Color,
    modifier: Modifier = Modifier,
    author: String? = null,
    imagePath: String? = null,
) {
    val image = rememberBookImage(imagePath)
    if (image != null) {
        androidx.compose.foundation.Image(
            bitmap = image,
            contentDescription = "Cover of $title",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = modifier.aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp)),
        )
        return
    }
    Column(
        modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .background(ground)
            .padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.width(26.dp).height(3.dp).background(accent))
        Column {
            Text(
                title, color = onColor,
                style = LocalNibbleType.current.title.copy(fontSize = 19.sp, lineHeight = 21.sp),
            )
            if (author != null) {
                Text(
                    author.uppercase(), color = onColor.copy(alpha = 0.7f),
                    fontSize = 10.5.sp, letterSpacing = 0.06.em, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 7.dp),
                    style = LocalNibbleType.current.label,
                )
            }
        }
    }
}

/** Small cover thumbnail for list rows: real cover cropped, or the ground colour. */
@Composable
fun CoverThumb(imagePath: String?, ground: Color, modifier: Modifier = Modifier) {
    val image = rememberBookImage(imagePath)
    Box(modifier.background(ground)) {
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---- Progress -----------------------------------------------------------------

/** Segment pills; the current segment is wider (§4). */
@Composable
fun NibbleProgressBar(total: Int, current: Int, modifier: Modifier = Modifier, window: Int = 5) {
    val c = LocalNibbleColors.current
    // Show a window of pills centred loosely on the current index.
    val start = (current - 2).coerceIn(0, (total - window).coerceAtLeast(0))
    val end = (start + window).coerceAtMost(total)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in start until end) {
            val isCurrent = i == current
            val done = i <= current
            Box(
                Modifier
                    .width(if (isCurrent) 30.dp else 16.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (done) c.clay else c.rule),
            )
        }
    }
}

// ---- Streak -------------------------------------------------------------------

@Composable
fun StreakPill(count: Int, modifier: Modifier = Modifier) {
    val c = LocalNibbleColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(99.dp))
            .background(c.moss)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LocalFireDepartment, null, tint = c.mossLeaf, modifier = Modifier.size(14.dp))
        Text("$count", color = c.onMoss, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ---- Segmented control --------------------------------------------------------

/** Selection is ink-filled, not clay (§4). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalNibbleColors.current
    val trackBg = if (c.isDark) Color(0xFF1A1613) else Color(0xFFEBE3D3)
    Row(
        modifier
            .clip(RoundedCornerShape(13.dp))
            .background(trackBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) c.ink else Color.Transparent)
                    .tap { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) c.paper else c.inkMuted,
                    fontSize = 13.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ---- Bottom bar ---------------------------------------------------------------

data class BottomDest(val icon: ImageVector, val label: String)

@Composable
fun NibbleBottomBar(
    destinations: List<BottomDest>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    brandFirst: Boolean = true,
) {
    val c = LocalNibbleColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(c.card)
            .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 4.dp),
    ) {
        destinations.forEachIndexed { i, dest ->
            val selected = i == selectedIndex
            val tint = if (selected) c.clay else c.inkFaint
            Column(
                Modifier.weight(1f).defaultMinSize(minHeight = 50.dp).tap { onSelect(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (brandFirst && i == 0) {
                    CookieIcon(tint, Modifier.size(21.dp))
                } else {
                    Icon(dest.icon, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                Text(
                    dest.label, color = tint, fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

// ---- Prose --------------------------------------------------------------------

/**
 * Owns the marker highlight and the dimmed tail (§6). Builds one AnnotatedString from
 * the chapter text plus three ranges: read (ink), current (marker), ahead (inkFaint).
 * Built in a remember(text, ranges), never per frame.
 */
@Composable
fun ProseText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    markerRange: IntRange? = null,
    dimFrom: Int? = null,
) {
    val c = LocalNibbleColors.current
    val annotated: AnnotatedString = remember(text, markerRange, dimFrom, c.isDark) {
        buildAnnotatedString {
            append(text)
            markerRange?.let {
                val s = it.first.coerceIn(0, text.length)
                val e = (it.last + 1).coerceIn(0, text.length)
                if (e > s) addStyle(SpanStyle(background = c.marker, color = c.ink), s, e)
            }
            dimFrom?.let {
                val s = it.coerceIn(0, text.length)
                if (s < text.length) addStyle(SpanStyle(color = c.inkFaint), s, text.length)
            }
        }
    }
    Text(annotated, style = style, color = c.ink, modifier = modifier)
}

// ---- Recap "Who?" callout -----------------------------------------------------

@Composable
fun RecapCallout(lead: String, body: String, modifier: Modifier = Modifier) {
    val c = LocalNibbleColors.current
    val bg = if (c.isDark) Color(0xFF221D18) else Color(0xFFEDE4D0)
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            Icons.Filled.Lightbulb, null, tint = c.gold,
            modifier = Modifier.size(17.dp).padding(top = 2.dp),
        )
        val txt = buildAnnotatedString {
            withStyleBold(c.ink) { append(lead) }
            append(" ")
            append(body)
        }
        Text(txt, color = c.inkMuted, style = LocalNibbleType.current.body.copy(fontSize = 13.sp, lineHeight = 20.sp))
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleBold(
    color: Color,
    block: androidx.compose.ui.text.AnnotatedString.Builder.() -> Unit,
) {
    val i = pushStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold))
    block()
    pop(i)
}

// ---- Small bits ---------------------------------------------------------------

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val c = LocalNibbleColors.current
    Text(
        text.uppercase(),
        color = color ?: c.inkFaint,
        style = LocalNibbleType.current.kicker,
        modifier = modifier,
    )
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, bold: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(99.dp)).background(bg).padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(text, color = fg, fontSize = 11.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
