package com.deniscerri.ytdl.whatsapp

import android.content.Context
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deniscerri.ytdl.R
import com.google.android.material.color.MaterialColors

/**
 * Colors the screens read directly. They come from the activity's Violet Flow theme, so they follow the main color,
 * light or dark mode as chosen in Ajustes, and "Contraste alto". The names date from the earlier glass look: panels
 * and edges are now the solid surface and its hairline border.
 */
@Immutable
data class GlassPalette(
    val background: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val edgeBright: Color,
    val edgeDim: Color,
    val text: Color,
    val textSoft: Color,
    val accentText: Color,
    val warning: Color,
)

val LocalGlass = staticCompositionLocalOf<GlassPalette> { error("GlassTheme is missing") }

private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

// Same scale as the XML theme (styles_videodownloader.xml): big titles bold, section titles and labels semibold.
private val VdTypography = Typography().run {
    fun TextStyle.inter(weight: FontWeight) = copy(fontFamily = Inter, fontWeight = weight, letterSpacing = TextStyle.Default.letterSpacing)
    copy(
        displayLarge = displayLarge.inter(FontWeight.Bold),
        displayMedium = displayMedium.inter(FontWeight.Bold),
        displaySmall = displaySmall.inter(FontWeight.Bold),
        headlineLarge = headlineLarge.inter(FontWeight.Bold),
        headlineMedium = headlineMedium.inter(FontWeight.Bold),
        headlineSmall = headlineSmall.inter(FontWeight.SemiBold),
        titleLarge = titleLarge.inter(FontWeight.SemiBold),
        titleMedium = titleMedium.inter(FontWeight.SemiBold),
        titleSmall = titleSmall.inter(FontWeight.SemiBold),
        bodyLarge = bodyLarge.inter(FontWeight.Normal),
        bodyMedium = bodyMedium.inter(FontWeight.Normal),
        bodySmall = bodySmall.inter(FontWeight.Normal),
        labelLarge = labelLarge.inter(FontWeight.SemiBold),
        labelMedium = labelMedium.inter(FontWeight.Medium),
        labelSmall = labelSmall.inter(FontWeight.Medium),
    )
}

private val VdShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The one size scale of the screens: compact controls like current Android apps, so a narrow phone fits more without
 * crowding. Touch targets stay at least 36 dp tall; Android's 48 dp guideline is kept by the spacing around them.
 */
object VdSize {
    /** Main and secondary buttons, text fields and the link box's button */
    val Control = 44.dp
    /** Buttons inside cards, chips and the two-way selectors */
    val SmallControl = 36.dp
    /** The icon inside a button */
    val ButtonIcon = 18.dp
    /** Left and right margin of every screen */
    val ScreenMargin = 16.dp
    /** Inner padding of a card */
    val CardPadding = 12.dp
    /** The colored square of a tool or setting */
    val ToolIcon = 40.dp
}

private fun Context.themeColor(@AttrRes attr: Int) = Color(MaterialColors.getColor(this, attr, android.graphics.Color.MAGENTA))

private fun themeColors(context: Context, dark: Boolean): ColorScheme = with(context) {
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = themeColor(R.attr.colorPrimary),
        onPrimary = themeColor(R.attr.colorOnPrimary),
        primaryContainer = themeColor(R.attr.colorPrimaryContainer),
        onPrimaryContainer = themeColor(R.attr.colorOnPrimaryContainer),
        inversePrimary = themeColor(R.attr.colorPrimaryInverse),
        secondary = themeColor(R.attr.colorSecondary),
        onSecondary = themeColor(R.attr.colorOnSecondary),
        secondaryContainer = themeColor(R.attr.colorSecondaryContainer),
        onSecondaryContainer = themeColor(R.attr.colorOnSecondaryContainer),
        tertiary = themeColor(R.attr.colorTertiary),
        onTertiary = themeColor(R.attr.colorOnTertiary),
        tertiaryContainer = themeColor(R.attr.colorTertiaryContainer),
        onTertiaryContainer = themeColor(R.attr.colorOnTertiaryContainer),
        background = themeColor(android.R.attr.colorBackground),
        onBackground = themeColor(R.attr.colorOnBackground),
        surface = themeColor(R.attr.colorSurface),
        onSurface = themeColor(R.attr.colorOnSurface),
        surfaceVariant = themeColor(R.attr.colorSurfaceVariant),
        onSurfaceVariant = themeColor(R.attr.colorOnSurfaceVariant),
        surfaceContainerLowest = themeColor(R.attr.colorSurfaceContainerLowest),
        surfaceContainerLow = themeColor(R.attr.colorSurfaceContainerLow),
        surfaceContainer = themeColor(R.attr.colorSurfaceContainer),
        surfaceContainerHigh = themeColor(R.attr.colorSurfaceContainerHigh),
        surfaceContainerHighest = themeColor(R.attr.colorSurfaceContainerHighest),
        surfaceBright = themeColor(R.attr.colorSurfaceBright),
        surfaceDim = themeColor(R.attr.colorSurfaceDim),
        inverseSurface = themeColor(R.attr.colorSurfaceInverse),
        inverseOnSurface = themeColor(R.attr.colorOnSurfaceInverse),
        outline = themeColor(R.attr.colorOutline),
        outlineVariant = themeColor(R.attr.colorOutlineVariant),
        error = themeColor(R.attr.colorError),
        onError = themeColor(R.attr.colorOnError),
        errorContainer = themeColor(R.attr.colorErrorContainer),
        onErrorContainer = themeColor(R.attr.colorOnErrorContainer),
    )
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = remember(context, dark) { themeColors(context, dark) }
    val glass = remember(context, colors) {
        GlassPalette(
            background = colors.background,
            panelTop = colors.surfaceContainer,
            panelBottom = colors.surfaceContainer,
            edgeBright = colors.outlineVariant,
            edgeDim = colors.outlineVariant,
            text = colors.onSurface,
            textSoft = colors.onSurfaceVariant,
            accentText = context.themeColor(R.attr.vdColorPrimaryText),
            warning = if (dark) Color(0xFFFFB86B) else Color(0xFFB45309),
        )
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(colorScheme = colors, typography = VdTypography, shapes = VdShapes, content = content)
    }
}

/** The page background */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(LocalGlass.current.background),
        content = content,
    )
}

/** The top of a screen: an arrow back, the screen's name and, at the end, the screen's own [action] (diseño/imagenes/06-1-elegir.png) */
@Composable
fun GlassHeader(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
    val glass = LocalGlass.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.vd_back), tint = glass.text)
        }
        Spacer(Modifier.width(8.dp))
        Text(title, color = glass.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action()
    }
}

/** A video's length over its picture: a play sign and the time (diseño/imagenes/06-1-elegir.png and 07-2-grilla.png) */
@Composable
fun PlayBadge(length: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_lyrics_play), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        if (length != null) {
            Spacer(Modifier.width(4.dp))
            Text(length, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * One of the app's drawings (res/drawable-nodpi/vd_art_*.webp, made in ChatGPT on 24/11/2026 in the Violet Flow
 * style, diseño/imagenes/19-ilustraciones) above an empty, finished or failed screen. It only decorates: the title
 * next to it says the same in words.
 */
@Composable
fun Illustration(@DrawableRes art: Int, modifier: Modifier = Modifier, size: Dp = 150.dp) {
    Image(painterResource(art), contentDescription = null, modifier = modifier.size(size))
}

/** The heading of a panel */
@Composable
fun GlassTitle(text: String) {
    Text(text, color = LocalGlass.current.text, style = MaterialTheme.typography.titleLarge)
}

/** Running text in a panel; [soft] is for secondary notes */
@Composable
fun GlassBody(text: String, soft: Boolean = false) {
    val glass = LocalGlass.current
    Text(text, color = if (soft) glass.textSoft else glass.text, style = MaterialTheme.typography.bodyLarge)
}

/** A card: the surface color with a hairline border */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.panelTop)
            .border(1.dp, glass.edgeBright, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** The one main action of a step, filled with the main color */
@Composable
fun GlassPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = VdSize.Control)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(MaterialTheme.shapes.small)
            .background(colors.primary)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = colors.onPrimary, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** A secondary action, outlined in the main color */
@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val glass = LocalGlass.current
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .heightIn(min = VdSize.Control)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .border(1.dp, glass.accentText, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = glass.text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

/** "Borrar" with a trash can: filled in red, or only [outlined] in red next to the main action */
@Composable
fun GlassDeleteButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, outlined: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val content = if (outlined) colors.error else colors.onError
    Row(
        modifier
            .heightIn(min = VdSize.Control)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .then(if (outlined) Modifier.border(1.dp, colors.error, shape) else Modifier.background(colors.error))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.baseline_delete_24), contentDescription = null, tint = content, modifier = Modifier.size(VdSize.ButtonIcon))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.vd_files_delete), color = content, style = MaterialTheme.typography.labelLarge)
    }
}

/** Asks before deleting: a trash can, the question, what happens and, if any, a [note] on what comes next (diseño/imagenes/15-4-borrar.png) */
@Composable
fun DeleteDialog(title: String, text: String, note: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.surfaceContainerHigh)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(64.dp).clip(MaterialTheme.shapes.medium).background(colors.error.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.baseline_delete_24), contentDescription = null, tint = colors.error, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(title, color = colors.onSurface, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            if (note != null) {
                Spacer(Modifier.height(10.dp))
                Text(note, color = LocalGlass.current.warning, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(22.dp))
            GlassDeleteButton(onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            GlassButton(stringResource(R.string.cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A choice in a row of options; the chosen one fills with the main color */
@Composable
fun GlassChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .heightIn(min = VdSize.SmallControl)
            .clip(shape)
            .background(if (selected) colors.primary else Color.Transparent)
            .border(1.dp, if (selected) colors.primary else colors.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) colors.onPrimary else glass.text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** A progress bar; without [fraction] a segment runs along it while the length of the work is unknown */
@Composable
fun GlassProgress(fraction: Float?, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val fill = MaterialTheme.colorScheme.primary
    val sweep = if (fraction == null) {
        val transition = rememberInfiniteTransition(label = "progress")
        val value by transition.animateFloat(-0.35f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep")
        value
    } else {
        0f
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(glass.edgeBright)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                if (fraction != null) {
                    drawRoundRect(fill, size = size.copy(width = size.width * fraction.coerceIn(0f, 1f)), cornerRadius = radius)
                } else {
                    drawRoundRect(fill, topLeft = Offset(sweep * size.width, 0f), size = size.copy(width = size.width * 0.35f), cornerRadius = radius)
                }
            },
    )
}
