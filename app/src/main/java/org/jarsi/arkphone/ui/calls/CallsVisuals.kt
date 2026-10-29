package org.jarsi.arkphone.ui.calls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** The green of the call buttons, as in the HyperOS dialer. */
internal val CallButtonGreen = Color(0xFF2DD04D)

/** A thin "›" used on the round details button at the end of a call row. */
internal val ChevronRightIcon: ImageVector = ImageVector.Builder(
    name = "ChevronRight",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.6f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9.5f, 6f)
        lineTo(15.5f, 12f)
        lineTo(9.5f, 18f)
    }
}.build()

/** Row feedback without a ripple: the row tints while pressed, like Miuix lists. */
internal fun Modifier.pressHighlight(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tint = if (pressed) MiuixTheme.colorScheme.onBackground.copy(alpha = 0.08f) else Color.Transparent
    background(tint).combinedClickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** The 3x4 dot grid of a dialpad, drawn white on the green call button. */
@Composable
internal fun DialpadDotsIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(26.dp)) {
        val radius = size.width * 0.075f
        val stepX = size.width * 0.3f
        val stepY = size.height * 0.24f
        val startX = size.width / 2 - stepX
        val startY = size.height / 2 - stepY * 1.5f
        for (row in 0 until 4) {
            for (col in 0 until 3) {
                drawCircle(tint, radius, Offset(startX + col * stepX, startY + row * stepY))
            }
        }
    }
}

/** Contact photo is not available for call rows, so HyperOS shows a person
 *  silhouette on a round tile; this draws the same. */
@Composable
internal fun PersonTile(size: Dp, modifier: Modifier = Modifier) {
    val tile = MiuixTheme.colorScheme.tertiaryContainer
    val glyph = MiuixTheme.colorScheme.onTertiaryContainer
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(tile),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            drawCircle(glyph, radius = w * 0.14f, center = Offset(w / 2, w * 0.38f))
            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = w * 0.25f,
                        top = w * 0.56f,
                        right = w * 0.75f,
                        bottom = w * 0.72f,
                        topLeftCornerRadius = CornerRadius(w * 0.17f),
                        topRightCornerRadius = CornerRadius(w * 0.17f),
                        bottomLeftCornerRadius = CornerRadius(w * 0.05f),
                        bottomRightCornerRadius = CornerRadius(w * 0.05f),
                    ),
                )
            }
            drawPath(body, glyph)
        }
    }
}
