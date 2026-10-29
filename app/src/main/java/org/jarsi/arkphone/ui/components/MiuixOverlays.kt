package org.jarsi.arkphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jarsi.arkphone.ui.calls.pressHighlight
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One line of a [MiuixPopupMenu]. */
data class MenuEntry(
    val text: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * A Miuix-style popup menu: a rounded dark card with bold single-line items,
 * shown over its anchor. Place it inside the anchor's `Box` and pick the
 * corner with [alignment]; [offset] nudges it from there.
 */
@Composable
fun MiuixPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<MenuEntry>,
    alignment: Alignment = Alignment.TopEnd,
    offset: IntOffset = IntOffset.Zero,
) {
    if (!expanded) return
    val shape = RoundedCornerShape(24.dp)
    Popup(
        alignment = alignment,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 200.dp, max = 300.dp)
                .shadow(elevation = 16.dp, shape = shape)
                .clip(shape)
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(vertical = 8.dp),
        ) {
            entries.forEach { entry ->
                Text(
                    text = entry.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (entry.destructive) {
                        MiuixTheme.colorScheme.error
                    } else {
                        MiuixTheme.colorScheme.onSurfaceContainer
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressHighlight(onClick = entry.onClick)
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/** A Miuix-style confirmation dialog with a cancel and a confirm text button. */
@Composable
fun MiuixConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer)
                .padding(24.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(text = dismissText, onClick = onDismiss)
                TextButton(text = confirmText, onClick = onConfirm)
            }
        }
    }
}
