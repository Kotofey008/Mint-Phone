package org.jarsi.arkphone.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallSource
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.SimAccount
import org.jarsi.arkphone.data.simLabelFor
import org.jarsi.arkphone.ui.calls.CallButtonGreen
import org.jarsi.arkphone.ui.components.MiuixConfirmDialog
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.recents.callTypeLabelRes
import org.jarsi.arkphone.util.formatDuration
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CallDetailScreen(
    viewModel: CallDetailViewModel,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onEditBeforeCall: (String) -> Unit,
    onDeleteHistory: () -> Unit,
    onWhatsAppCall: (String, String?) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    CallDetailContent(
        uiState = uiState,
        onBack = onBack,
        onCall = { onCall(uiState.number) },
        onMessage = { onMessage(uiState.number) },
        onCopyNumber = { clipboard.setText(AnnotatedString(uiState.number)) },
        onEditBeforeCall = { onEditBeforeCall(uiState.number) },
        onToggleBlocked = viewModel::onToggleBlocked,
        onDeleteHistory = onDeleteHistory,
        onWhatsAppCall = { onWhatsAppCall(uiState.number, uiState.displayName) },
        onShowMore = viewModel::onShowMore,
    )
}

@Composable
fun CallDetailContent(
    uiState: CallDetailUiState,
    onBack: () -> Unit,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onCopyNumber: () -> Unit,
    onEditBeforeCall: () -> Unit,
    onToggleBlocked: () -> Unit,
    onDeleteHistory: () -> Unit,
    onWhatsAppCall: () -> Unit = {},
    onShowMore: () -> Unit = {},
) {
    val haptics = rememberHaptics()
    var confirmDelete by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()
    val copyLabel = stringResource(R.string.detail_copy_number)
    val editLabel = stringResource(R.string.detail_edit_before_call)
    val deleteLabel = stringResource(R.string.detail_delete_history)
    // The "⋮" menu is a Miuix icon dropdown: number actions, then a separated
    // group for the destructive one.
    val menuEntries = buildList {
        if (uiState.hasNumber) {
            add(
                DropdownEntry(
                    items = listOf(
                        DropdownItem(text = copyLabel, onClick = onCopyNumber),
                        DropdownItem(text = editLabel, onClick = onEditBeforeCall),
                    ),
                ),
            )
        }
        add(DropdownEntry(items = listOf(DropdownItem(text = deleteLabel, onClick = { confirmDelete = true }))))
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = uiState.displayName ?: uiState.number,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = {
                            haptics.click()
                            onBack()
                        },
                    ) {
                        Icon(
                            MiuixIcons.Back,
                            contentDescription = stringResource(R.string.settings_back),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
                actions = {
                    OverlayIconDropdownMenu(entries = menuEntries, collapseOnSelection = true) {
                        Icon(
                            MiuixIcons.More,
                            contentDescription = stringResource(R.string.detail_more_options),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
        ) {
            ContactCard(
                uiState = uiState,
                onCall = onCall,
                onMessage = onMessage,
                onWhatsAppCall = onWhatsAppCall,
            )
            StatsCard(uiState.stats)
            HistorySection(uiState, onShowMore)
            if (uiState.canBlock) {
                BlockCard(blocked = uiState.blocked, onClick = {
                    haptics.click()
                    onToggleBlocked()
                })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        MiuixConfirmDialog(
            title = stringResource(R.string.detail_delete_confirm_title),
            text = stringResource(R.string.detail_delete_confirm_text),
            confirmText = stringResource(R.string.common_delete),
            dismissText = stringResource(R.string.common_cancel),
            onConfirm = {
                haptics.click()
                confirmDelete = false
                onDeleteHistory()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** The caller: number with round call and message buttons at its end, and a
 *  WhatsApp row when some of the calls came over WhatsApp. */
@Composable
private fun ContactCard(
    uiState: CallDetailUiState,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onWhatsAppCall: () -> Unit,
) {
    val haptics = rememberHaptics()
    if (!uiState.hasNumber && !uiState.hasWhatsAppCalls) return
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        if (uiState.hasNumber) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = uiState.number,
                        fontSize = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceContainer,
                    )
                    if (uiState.blocked) {
                        Text(
                            text = stringResource(R.string.detail_blocked_badge),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                RoundAction(stringResource(R.string.dialpad_call), onClick = {
                    haptics.confirm()
                    onCall()
                }) {
                    Icon(MiuixIcons.Phone, contentDescription = null, tint = CallButtonGreen)
                }
                Spacer(Modifier.width(12.dp))
                RoundAction(stringResource(R.string.detail_message), onClick = {
                    haptics.click()
                    onMessage()
                }) {
                    Icon(MiuixIcons.Messages, contentDescription = null, tint = CallButtonGreen)
                }
            }
        }
        if (uiState.hasWhatsAppCalls) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            ) {
                Text(
                    text = stringResource(R.string.call_source_whatsapp),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceContainer,
                    modifier = Modifier.weight(1f),
                )
                val whatsAppLabel = stringResource(R.string.call_whatsapp)
                Image(
                    painterResource(R.drawable.ic_whatsapp),
                    contentDescription = whatsAppLabel,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = null, indication = null) {
                            haptics.confirm()
                            onWhatsAppCall()
                        },
                )
            }
        }
    }
}

/** A round action button: dark disc, green glyph. */
@Composable
private fun RoundAction(
    description: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(CallButtonGreen.copy(alpha = 0.18f))
            .semantics { contentDescription = description }
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        icon()
    }
}

/** Counters over the whole history: four big numbers, then talk time and the
 *  latest call. Sits between the caller and the call list. */
@Composable
private fun StatsCard(stats: CallStats) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(vertical = 14.dp)) {
            Text(
                text = stringResource(R.string.detail_stats_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                StatTile(stringResource(R.string.detail_stat_calls), stats.total, Modifier.weight(1f))
                StatTile(stringResource(R.string.detail_stat_outgoing), stats.outgoing, Modifier.weight(1f))
                StatTile(stringResource(R.string.detail_stat_incoming), stats.incoming, Modifier.weight(1f))
                StatTile(
                    stringResource(R.string.detail_stat_missed),
                    stats.missed,
                    Modifier.weight(1f),
                    highlight = stats.missed > 0,
                )
            }
            StatLine(
                stringResource(R.string.detail_stat_talk_time),
                formatTalkTime(stats.totalDurationSeconds),
            )
            stats.latestMillis?.let { latest ->
                StatLine(
                    stringResource(R.string.detail_stat_latest),
                    DateUtils.getRelativeTimeSpanString(latest).toString(),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier, highlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = value.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = if (highlight) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainer,
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurfaceContainer,
        )
    }
}

@Composable
private fun formatTalkTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_minutes_seconds, minutes, seconds)
        else -> stringResource(R.string.duration_seconds, seconds)
    }
}

/** One card: the newest few calls, then "Show more" adds ten at a time. Rows
 *  are only ever composed for what has been loaded. */
@Composable
private fun HistorySection(uiState: CallDetailUiState, onShowMore: () -> Unit) {
    val haptics = rememberHaptics()
    Text(
        text = stringResource(R.string.detail_history_title),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 14.dp, bottom = 6.dp),
    )
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column {
            uiState.entries.forEach { entry ->
                HistoryRow(entry, uiState.simAccounts)
            }
            if (uiState.hasMore) {
                Text(
                    text = stringResource(
                        R.string.detail_show_more,
                        minOf(uiState.remainingCalls, PAGE_SIZE),
                    ),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(interactionSource = null, indication = null) {
                            haptics.click()
                            onShowMore()
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

/** A call as HyperOS lists it: the date and time large, then what happened. */
@Composable
private fun HistoryRow(entry: CallLogEntry, simAccounts: List<SimAccount>) {
    val typeLabel = stringResource(callTypeLabelRes(entry.type))
    val whatsapp = entry.source == CallSource.WHATSAPP
    val time = DateUtils.formatDateTime(
        LocalContext.current,
        entry.timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH,
    )
    val duration = if (entry.durationSeconds > 0) " | " + formatDuration(entry.durationSeconds) else ""
    val wifi = if (entry.wasWifiCall) " · " + stringResource(R.string.call_feature_wifi) else ""
    val ark = if (entry.viaArkCall) " · " + stringResource(R.string.call_feature_ark) else ""
    val sim = simLabelFor(entry.simAccountId, simAccounts)?.let { " · $it" }.orEmpty()
    val missed = entry.type == CallType.MISSED || entry.type == CallType.REJECTED
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = time,
            fontSize = 17.sp,
            color = if (missed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainer,
        )
        Text(
            text = (if (whatsapp) typeLabel + " · " + stringResource(R.string.call_source_whatsapp) else typeLabel) +
                duration + wifi + ark + sim,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** The red "Block" row on its own card, as on the HyperOS call details. */
@Composable
private fun BlockCard(blocked: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(
            text = stringResource(if (blocked) R.string.detail_unblock else R.string.detail_block),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = null, indication = null, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 18.dp),
        )
    }
}
