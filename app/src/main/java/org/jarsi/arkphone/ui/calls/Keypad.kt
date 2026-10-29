package org.jarsi.arkphone.ui.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.arkphone.R
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.dialpad.DialpadUiState
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.ExpandMore
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Gap between the round buttons and the screen edges. The folded call
 *  button and the keypad's hide button use the same value, so the one sits
 *  exactly where the other was. */
internal val KeypadEdgePadding = 16.dp

/** Diameter of the folded call button and of the hide button. */
internal val KeypadSmallButton = 60.dp

private val KeypadCallButton = 72.dp

private val keypadRows = listOf(
    listOf('1' to "", '2' to "ABC", '3' to "DEF"),
    listOf('4' to "GHI", '5' to "JKL", '6' to "MNO"),
    listOf('7' to "PQRS", '8' to "TUV", '9' to "WXYZ"),
    listOf('*' to "", '0' to "+", '#' to ""),
)

/** The folded keypad: a round call button that unfolds the keypad. */
@Composable
internal fun FoldedCallButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = rememberHaptics()
    FloatingActionButton(
        onClick = {
            haptics.click()
            onClick()
        },
        modifier = modifier,
        shape = CircleShape,
        containerColor = CallButtonGreen,
    ) {
        val description = stringResource(R.string.dialpad_show)
        DialpadDotsIcon(
            tint = Color.White,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

/**
 * The unfolded keypad: typed number, 12 keys, and a bottom row with the round
 * call button in the middle and the round hide button under the right-hand key.
 */
@Composable
internal fun KeypadPanel(
    uiState: DialpadUiState,
    onFold: () -> Unit,
    onKey: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
    onDial: () -> Unit,
    onVoicemail: () -> Unit,
    onSpeedDial: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 8.dp, shape = shape)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(top = 12.dp),
    ) {
        KeypadNumberField(
            uiState = uiState,
            onDelete = onDelete,
            onPaste = onPaste,
            onClear = onClear,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        KeypadGrid(
            onKey = onKey,
            onVoicemail = onVoicemail,
            onSpeedDial = onSpeedDial,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = KeypadEdgePadding),
        ) {
            FloatingActionButton(
                onClick = {
                    haptics.confirm()
                    onDial()
                },
                modifier = Modifier.align(Alignment.BottomCenter).size(KeypadCallButton),
                shape = CircleShape,
                containerColor = CallButtonGreen,
            ) {
                Icon(
                    MiuixIcons.Phone,
                    contentDescription = stringResource(R.string.dialpad_call),
                    tint = Color.White,
                )
            }
            FloatingActionButton(
                onClick = {
                    haptics.click()
                    onFold()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = KeypadEdgePadding)
                    .size(KeypadSmallButton),
                shape = CircleShape,
                containerColor = MiuixTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    MiuixIcons.ExpandMore,
                    contentDescription = stringResource(R.string.dialpad_hide),
                    tint = MiuixTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** The typed number: long-press to paste; the delete button removes a digit,
 *  and a long press on it clears the whole number. */
@Composable
private fun KeypadNumberField(
    uiState: DialpadUiState,
    onDelete: () -> Unit,
    onPaste: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Mirrors the delete button so the number stays centered.
        Spacer(Modifier.size(48.dp))
        val pasteLabel = stringResource(R.string.dialpad_paste)
        Text(
            text = uiState.displayNumber.ifEmpty { uiState.number },
            fontSize = 34.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .combinedClickable(
                    onClick = {},
                    onLongClickLabel = pasteLabel,
                    onLongClick = {
                        haptics.longPress()
                        clipboard.getText()?.text?.let(onPaste)
                    },
                ),
        )
        val clearLabel = stringResource(R.string.dialpad_clear)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .combinedClickable(
                    onClick = {
                        haptics.click()
                        onDelete()
                    },
                    onLongClickLabel = clearLabel,
                    onLongClick = {
                        haptics.longPress()
                        onClear()
                    },
                ),
        ) {
            if (uiState.number.isNotEmpty()) {
                Icon(
                    MiuixIcons.Close,
                    contentDescription = stringResource(R.string.dialpad_delete),
                    tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

@Composable
private fun KeypadGrid(
    onKey: (Char) -> Unit,
    onVoicemail: () -> Unit,
    onSpeedDial: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        keypadRows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (digit, letters) ->
                    KeypadKey(
                        digit = digit,
                        letters = letters,
                        onClick = {
                            haptics.keyTap()
                            onKey(digit)
                        },
                        onLongClick = when (digit) {
                            '0' -> {
                                {
                                    haptics.longPress()
                                    onKey('+')
                                }
                            }
                            '1' -> {
                                {
                                    haptics.longPress()
                                    onVoicemail()
                                }
                            }
                            in '2'..'9' -> {
                                {
                                    haptics.longPress()
                                    onSpeedDial(digit.digitToInt())
                                }
                            }
                            // A no-op (not null) so combinedClickable consumes the
                            // long-press instead of turning it into a click on release.
                            else -> {
                                {}
                            }
                        },
                        onLongClickLabel = when (digit) {
                            '0' -> stringResource(R.string.dialpad_long_press_plus)
                            '1' -> stringResource(R.string.dialpad_long_press_voicemail)
                            in '2'..'9' -> stringResource(R.string.dialpad_long_press_speed_dial)
                            else -> null
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A flat key: no outline and no fill until pressed. */
@Composable
private fun KeypadKey(
    digit: Char,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onLongClickLabel: String?,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(if (pressed) MiuixTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = onLongClickLabel,
            ),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit.toString(),
                fontSize = 30.sp,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    fontSize = 10.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}
