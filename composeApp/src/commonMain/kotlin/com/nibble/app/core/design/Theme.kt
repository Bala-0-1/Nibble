package com.nibble.app.core.design

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nibble.app.resources.Res
import com.nibble.app.resources.dmsans
import com.nibble.app.resources.newsreader
import org.jetbrains.compose.resources.Font

/**
 * The palette is bespoke, not Material (§3). We keep MaterialTheme underneath only for
 * ripple and text selection, and ship [NibbleColors] behind a CompositionLocal. Left
 * value = light (turn 2), right value = dark (turn 3).
 */
@Immutable
data class NibbleColors(
    val paper: Color,
    val paperSunk: Color,
    val card: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val clay: Color,
    val clayPress: Color,
    val clayWash: Color,
    val onClay: Color,      // text/icon that sits on a clay button
    val moss: Color,
    val mossLeaf: Color,
    val onMoss: Color,
    val marker: Color,      // reading highlight (already includes its alpha)
    val gold: Color,        // kicker dots, timer icon, amber accents
    val isDark: Boolean,
)

val NibbleLightColors = NibbleColors(
    paper = Color(0xFFF7F2E8),
    paperSunk = Color(0xFFF4EDDF),
    card = Color(0xFFFFFCF6),
    ink = Color(0xFF221E1A),
    inkMuted = Color(0xFF6B6155),
    inkFaint = Color(0xFF8A7F71),
    rule = Color(0xFFE6DCCA),
    clay = Color(0xFFA85732),
    clayPress = Color(0xFF8B4830),
    clayWash = Color(0xFFF1E2D8),
    onClay = Color(0xFFFFF7EF),
    moss = Color(0xFF1F4D3D),
    mossLeaf = Color(0xFF8FB79E),
    onMoss = Color(0xFFEAF3EC),
    marker = Color(0xFFEDD9A5),
    gold = Color(0xFFD9A85C),
    isDark = false,
)

val NibbleDarkColors = NibbleColors(
    paper = Color(0xFF14110F),
    paperSunk = Color(0xFF17130F),
    card = Color(0xFF1F1B17),
    ink = Color(0xFFF4ECDD),
    inkMuted = Color(0xFFA2957F),
    inkFaint = Color(0xFF8B8071),
    rule = Color(0xFF332C24),
    clay = Color(0xFFC97A56),
    clayPress = Color(0xFF8A5238),
    clayWash = Color(0xFF2E251E),
    onClay = Color(0xFF1A1411),
    moss = Color(0xFF1B3A2E),
    mossLeaf = Color(0xFF8FB79E),
    onMoss = Color(0xFFCFE6D8),
    marker = Color(0x38D9A85C),   // amber @ ~22%
    gold = Color(0xFFD9A85C),
    isDark = true,
)

/**
 * Two families, strictly separated by role (§3): Newsreader is book prose only,
 * DM Sans is every interface string. `prose` is the only style the text-size slider scales.
 */
@Immutable
data class NibbleType(
    val display: TextStyle,
    val title: TextStyle,
    val prose: TextStyle,
    val proseTight: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val kicker: TextStyle,
)

object Dimens {
    val gutter = 20.dp
    val cardGap = 9.dp
    val sectionGap = 18.dp
    val touchMin = 48.dp
}

object Radii {
    val card = 15.dp
    val hero = 20.dp
    val sheet = 24.dp
    val pill = 999.dp
}

val LocalNibbleColors = staticCompositionLocalOf { NibbleLightColors }
val LocalNibbleType = staticCompositionLocalOf<NibbleType> { error("NibbleType not provided") }

/** MaterialTheme.nibble accessor from the guide. */
val MaterialTheme.nibble: NibbleColors
    @Composable @ReadOnlyComposable get() = LocalNibbleColors.current

val MaterialTheme.nibbleType: NibbleType
    @Composable @ReadOnlyComposable get() = LocalNibbleType.current

private val tightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

@Composable
fun rememberNibbleType(): NibbleType {
    val newsreader = FontFamily(
        Font(Res.font.newsreader, FontWeight.Light),
        Font(Res.font.newsreader, FontWeight.Normal),
        Font(Res.font.newsreader, FontWeight.Medium),
    )
    val dmSans = FontFamily(
        Font(Res.font.dmsans, FontWeight.Normal),
        Font(Res.font.dmsans, FontWeight.Medium),
        Font(Res.font.dmsans, FontWeight.Bold),
    )
    return remember {
        NibbleType(
            display = TextStyle(
                fontFamily = newsreader, fontWeight = FontWeight.Medium,
                fontSize = 33.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em,
            ),
            title = TextStyle(
                fontFamily = newsreader, fontWeight = FontWeight.Normal,
                fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em,
            ),
            prose = TextStyle(
                fontFamily = newsreader, fontWeight = FontWeight.Normal,
                fontSize = 22.sp, lineHeight = 35.sp,
            ),
            proseTight = TextStyle(
                fontFamily = newsreader, fontWeight = FontWeight.Normal,
                fontSize = 17.sp, lineHeight = 26.sp,
            ),
            body = TextStyle(
                fontFamily = dmSans, fontWeight = FontWeight.Normal,
                fontSize = 14.sp, lineHeight = 22.sp,
            ),
            label = TextStyle(
                fontFamily = dmSans, fontWeight = FontWeight.Medium,
                fontSize = 12.5.sp, lineHeight = 17.sp,
            ),
            kicker = TextStyle(
                fontFamily = dmSans, fontWeight = FontWeight.Bold,
                fontSize = 10.5.sp, lineHeight = 14.sp, letterSpacing = 0.18.em,
            ),
        )
    }
}

@Composable
fun NibbleTheme(
    dark: Boolean,
    content: @Composable () -> Unit,
) {
    val colors = if (dark) NibbleDarkColors else NibbleLightColors
    val type = rememberNibbleType()
    CompositionLocalProvider(
        LocalNibbleColors provides colors,
        LocalNibbleType provides type,
    ) {
        // MaterialTheme underneath only for ripple and text selection.
        MaterialTheme(content = content)
    }
}
