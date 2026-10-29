package org.jarsi.arkphone.ui.detail

import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
import org.jarsi.arkphone.ui.components.MenuEntry
import org.jarsi.arkphone.ui.components.MiuixConfirmDialog
import org.jarsi.arkphone.ui.components.MiuixPopupMenu
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.recents.CallsAvatar
import org.jarsi.arkphone.ui.recents.callTypeLabelRes
import org.jarsi.arkphone.util.formatDuration
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Blocklist
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
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val menuEntries = buildList {
        if (uiState.hasNumber) {
            add(MenuEntry(stringResource(R.string.detail_copy_number)) {
                menuOpen = false
                onCopyNumber()
            })
            add(MenuEntry(stringResource(R.string.detail_edit_before_call)) {
                menuOpen = false
                onEditBeforeCall()
            })
        }
        if (uiState.canBlock) {
            add(
                MenuEntry(
                    stringResource(if (uiState.blocked) R.string.detail_unblock else R.string.detail_block),
                ) {
                    menuOpen = false
                    onToggleBlocked()
                },
            )
        }
        add(MenuEntry(stringResource(R.string.detail_delete_history), destructive = true) {
            menuOpen = false
            confirmDelete = true
        })
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "",
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
                    Box {
                        IconButton(
                            onClick = {
                                haptics.click()
                                menuOpen = true
                            },
                        ) {
                            Icon(
                                MiuixIcons.More,
                                contentDescription = stringResource(R.string.detail_more_options),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                        MiuixPopupMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            entries = menuEntries,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CallsAvatar(uiState.photoUri, size = 96.dp)
            Text(
                text = uiState.displayName ?: uiState.number,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
            if (uiState.displayName != null && uiState.hasNumber) {
                Text(
                    text = uiState.number,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (uiState.blocked) {
                Text(
                    text = stringResource(R.string.detail_blocked_badge),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(vertical = 20.dp),
            ) {
                if (uiState.hasNumber) {
                    ActionButton(
                        label = stringResource(R.string.dialpad_call),
                        container = CallButtonGreen,
                        onClick = {
                            haptics.confirm()
                            onCall()
                        },
                    ) {
                        Icon(
                            MiuixIcons.Phone,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    ActionButton(
                        label = stringResource(R.string.detail_message),
                        container = MiuixTheme.colorScheme.secondaryContainer,
                        onClick = {
                            haptics.click()
                            onMessage()
                        },
                    ) {
                        Icon(
                            MiuixIcons.Messages,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (uiState.hasWhatsAppCalls) {
                    ActionButton(
                        label = stringResource(R.string.call_source_whatsapp),
                        container = MiuixTheme.colorScheme.secondaryContainer,
                        onClick = {
                            haptics.confirm()
                            onWhatsAppCall()
                        },
                    ) {
                        Image(
                            painterResource(R.drawable.ic_whatsapp),
                            contentDescription = stringResource(R.string.call_whatsapp),
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
            StatsCard(uiState.stats)
            HistorySection(uiState, onShowMore)
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

/** A round action with its label underneath, like the HyperOS contact card. */
@Composable
private fun ActionButton(
    label: String,
    container: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(container),
        ) {
            icon()
        }
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Counters over the whole history: four big numbers, then talk time and the
 *  latest call. Sits right under the actions, above the call list. */
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

/** The newest few calls, then "Show more" adds ten at a time. Rows are only
 *  ever composed for what has been loaded. */
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
    uiState.entries.forEach { entry ->
        HistoryRow(entry, uiState.simAccounts)
    }
    if (uiState.hasMore) {
        TextButton(
            text = stringResource(
                R.string.detail_show_more,
                minOf(uiState.remainingCalls, PAGE_SIZE),
            ),
            onClick = {
                haptics.click()
                onShowMore()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HistoryRow(entry: CallLogEntry, simAccounts: List<SimAccount>) {
    val typeLabel = stringResource(callTypeLabelRes(entry.type))
    val whatsapp = entry.source == CallSource.WHATSAPP
    val time = DateUtils.formatDateTime(
        LocalContext.current,
        entry.timestampMillis,
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR,
    )
    val wifi = if (entry.wasWifiCall) " · " + stringResource(R.string.call_feature_wifi) else ""
    val ark = if (entry.viaArkCall) " · " + stringResource(R.string.call_feature_ark) else ""
    val sim = simLabelFor(entry.simAccountId, simAccounts)?.let { " · $it" }.orEmpty()
    val missed = entry.type == CallType.MISSED || entry.type == CallType.REJECTED
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        BasicComponent(
            startAction = {
                Icon(
                    imageVector = if (entry.type == CallType.BLOCKED) MiuixIcons.Blocklist else MiuixIcons.Phone,
                    contentDescription = null,
                    tint = when {
                        missed -> MiuixTheme.colorScheme.error
                        entry.type == CallType.OUTGOING -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            endActions = {
                if (entry.durationSeconds > 0) {
                    Text(
                        text = formatDuration(entry.durationSeconds),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            },
        ) {
            Text(
                text = if (whatsapp) typeLabel + " · " + stringResource(R.string.call_source_whatsapp) else typeLabel,
                style = MiuixTheme.textStyles.body1,
                color = if (missed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainer,
            )
            Text(
                text = time + wifi + ark + sim,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
    }
}
