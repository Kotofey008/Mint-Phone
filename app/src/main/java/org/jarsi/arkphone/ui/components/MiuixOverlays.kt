package org.jarsi.arkphone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One line of a [MiuixPopupMenu]. */
data class MenuEntry(
    val text: String,
    val onClick: () -> Unit,
)

/**
 * The Miuix popup list (the one behind dropdown preferences) opened over its
 * anchor, for a context menu. Groups are separated by a thin line, like the
 * HyperOS call menu. Place it in the anchor's `Box`; it needs the host's
 * Miuix `Scaffold` to draw.
 */
@Composable
fun MiuixPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    groups: List<List<MenuEntry>>,
) {
    val rows = groups.flatMapIndexed { group, entries -> entries.map { group to it } }
    OverlayListPopup(
        show = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        ListPopupColumn {
            rows.forEachIndexed { index, (group, entry) ->
                if (index > 0 && rows[index - 1].first != group) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                            .height(0.5.dp)
                            .background(MiuixTheme.colorScheme.onSurfaceContainerVariant.copy(alpha = 0.3f)),
                    )
                }
                DropdownImpl(
                    text = entry.text,
                    optionSize = rows.size,
                    isSelected = false,
                    index = index,
                    onSelectedIndexChange = { entry.onClick() },
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
