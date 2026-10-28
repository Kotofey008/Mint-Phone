package org.jarsi.arkphone.ui.recents

import android.text.format.DateUtils
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallSource
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.data.model.SimAccount
import org.jarsi.arkphone.data.simLabelFor
import org.jarsi.arkphone.ui.components.rememberHaptics
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Blocklist
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@StringRes
internal fun callTypeLabelRes(type: CallType): Int = when (type) {
    CallType.INCOMING -> R.string.call_type_incoming
    CallType.OUTGOING -> R.string.call_type_outgoing
    CallType.MISSED -> R.string.call_type_missed
    CallType.REJECTED -> R.string.call_type_rejected
    CallType.BLOCKED -> R.string.call_type_blocked
    CallType.OTHER -> R.string.call_type_other
}

@StringRes
private fun filterLabelRes(filter: RecentsFilter): Int = when (filter) {
    RecentsFilter.ALL -> R.string.recents_filter_all
    RecentsFilter.MISSED -> R.string.recents_filter_missed
    RecentsFilter.OUTGOING -> R.string.recents_filter_outgoing
    RecentsFilter.BLOCKED -> R.string.recents_filter_blocked
    RecentsFilter.WHATSAPP -> R.string.call_source_whatsapp
}

/**
 * The scrolling body of the Calls tab: an optional typed-number banner, the
 * favorites row, the "Recents" filter dropdown, then the call rows and any
 * matching contacts that have no calls. Everything scrolls together.
 */
@Composable
fun RecentsContent(
    uiState: RecentsUiState,
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenDetails: (String) -> Unit = {},
    onOpenNameDetails: (String) -> Unit = {},
    onWhatsAppCall: (CallLogEntry) -> Unit = {},
    onFilterChange: (RecentsFilter) -> Unit = {},
    favorites: List<Contact> = emptyList(),
    contactRows: List<Contact> = emptyList(),
    typedNumber: String? = null,
    onTypedClick: () -> Unit = {},
    onTypedClear: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val listState = rememberLazyListState()
    val newestId = uiState.entries.firstOrNull()?.entry?.id
    val headerCount = (if (typedNumber != null) 1 else 0) + (if (favorites.isNotEmpty()) 1 else 0) + 1
    // Keyed items anchor the viewport to the previously-first row, so a freshly
    // logged call would land hidden above it. When the list sits at the top,
    // keep it pinned to the newest call; a deeper scroll position is left alone.
    LaunchedEffect(newestId) {
        if (listState.firstVisibleItemIndex <= headerCount) {
            listState.scrollToItem(0)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = contentPadding) {
        if (typedNumber != null) {
            item(key = "typed-number") {
                TypedNumberRow(typedNumber, onClick = onTypedClick, onClear = onTypedClear)
            }
        }
        if (favorites.isNotEmpty()) {
            item(key = "favorites") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favorites, key = { it.id }) { contact ->
                        FavoriteItem(contact = contact, onCall = onCall)
                    }
                }
            }
        }
        item(key = "filter") {
            RecentsFilterDropdown(
                selected = uiState.filter,
                showWhatsApp = uiState.showWhatsAppFilter,
                onSelect = onFilterChange,
            )
        }
        when {
            uiState.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    InfiniteProgressIndicator()
                }
            }
            !uiState.hasPermission -> item(key = "no-permission") {
                val haptics = rememberHaptics()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.recents_no_permission),
                        color = MiuixTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            haptics.click()
                            onRequestPermissions()
                        },
                        colors = ButtonDefaults.buttonColorsPrimary(),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.common_grant_access),
                            color = MiuixTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
            uiState.entries.isEmpty() && contactRows.isEmpty() -> item(key = "empty") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.recents_empty),
                        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            }
            else -> {
                items(uiState.entries, key = { it.entry.id }) { grouped ->
                    RecentsRow(
                        grouped = grouped,
                        simAccounts = uiState.simAccounts,
                        onCall = onCall,
                        onOpenDetails = onOpenDetails,
                        onOpenNameDetails = onOpenNameDetails,
                        onWhatsAppCall = onWhatsAppCall,
                    )
                }
                items(contactRows, key = { "contact-${it.id}" }) { contact ->
                    ContactRow(contact = contact, onCall = onCall, onOpenDetails = onOpenDetails)
                }
            }
        }
    }
}

/** "Recents": tapping it opens a dropdown that switches the call filter. */
@Composable
private fun RecentsFilterDropdown(
    selected: RecentsFilter,
    showWhatsApp: Boolean,
    onSelect: (RecentsFilter) -> Unit,
) {
    val haptics = rememberHaptics()
    val filters = buildList {
        add(RecentsFilter.ALL)
        add(RecentsFilter.MISSED)
        add(RecentsFilter.OUTGOING)
        add(RecentsFilter.BLOCKED)
        if (showWhatsApp) add(RecentsFilter.WHATSAPP)
    }
    val labels = filters.map { stringResource(filterLabelRes(it)) }
    Card(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        OverlayDropdownPreference(
            title = stringResource(R.string.tab_recents),
            items = labels,
            selectedIndex = filters.indexOf(selected).coerceAtLeast(0),
            onSelectedIndexChange = { index ->
                haptics.click()
                onSelect(filters[index])
            },
        )
    }
}

/** Shown while a number is typed but the keypad is folded: the list is still
 *  narrowed to it, so say so, and let the person clear it or reopen the keypad. */
@Composable
private fun TypedNumberRow(number: String, onClick: () -> Unit, onClear: () -> Unit) {
    val haptics = rememberHaptics()
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        showIndication = true,
        onClick = onClick,
    ) {
        BasicComponent(
            endActions = {
                IconButton(
                    onClick = {
                        haptics.click()
                        onClear()
                    },
                ) {
                    Icon(
                        MiuixIcons.Close,
                        contentDescription = stringResource(R.string.calls_clear_number),
                        tint = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    )
                }
            },
        ) {
            Text(
                text = number,
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FavoriteItem(contact: Contact, onCall: (String) -> Unit) {
    val haptics = rememberHaptics()
    Card(
        modifier = Modifier.width(80.dp),
        showIndication = true,
        onClick = {
            haptics.confirm()
            contact.phoneNumber?.let(onCall)
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
        ) {
            CallsAvatar(contact.displayName, contact.photoUri, size = 52.dp)
            Text(
                text = contact.displayName,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun RecentsRow(
    grouped: GroupedCallLogEntry,
    simAccounts: List<SimAccount>,
    onCall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
    onOpenNameDetails: (String) -> Unit,
    onWhatsAppCall: (CallLogEntry) -> Unit,
) {
    val entry = grouped.entry
    val haptics = rememberHaptics()
    val typeLabel = stringResource(callTypeLabelRes(entry.type))
    val sourceSuffix = if (entry.source == CallSource.WHATSAPP) {
        " · " + stringResource(R.string.call_source_whatsapp)
    } else {
        ""
    }
    val wifiSuffix = if (entry.wasWifiCall) " · " + stringResource(R.string.call_feature_wifi) else ""
    val arkSuffix = if (entry.viaArkCall) " · " + stringResource(R.string.call_feature_ark) else ""
    val simSuffix = simLabelFor(entry.simAccountId, simAccounts)?.let { " · $it" }.orEmpty()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.number_copied)
    val name = entry.displayName
        ?: entry.number.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.call_log_unknown_caller)
    val missed = entry.type == CallType.MISSED
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        showIndication = true,
        onClick = {
            when {
                entry.number.isNotBlank() -> onOpenDetails(entry.number)
                entry.source == CallSource.WHATSAPP && entry.displayName != null ->
                    onOpenNameDetails(entry.displayName)
                else -> Unit
            }
        },
        onLongPress = {
            if (entry.number.isNotBlank()) {
                clipboard.setText(AnnotatedString(entry.number))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        BasicComponent(
            startAction = {
                Icon(
                    imageVector = if (entry.type == CallType.BLOCKED) MiuixIcons.Blocklist else MiuixIcons.Phone,
                    contentDescription = typeLabel,
                    tint = when (entry.type) {
                        CallType.MISSED, CallType.REJECTED -> MiuixTheme.colorScheme.error
                        CallType.OUTGOING -> MiuixTheme.colorScheme.primary
                        else -> MiuixTheme.colorScheme.onSurfaceContainerVariant
                    },
                    modifier = Modifier.padding(end = 12.dp),
                )
            },
            endActions = {
                if (entry.source == CallSource.WHATSAPP) {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            onWhatsAppCall(entry)
                        },
                    ) {
                        Image(
                            painterResource(R.drawable.ic_whatsapp),
                            contentDescription = stringResource(R.string.call_whatsapp),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            onCall(entry.number)
                        },
                    ) {
                        Icon(
                            MiuixIcons.Phone,
                            contentDescription = stringResource(R.string.dialpad_call),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            },
        ) {
            Text(
                text = if (grouped.count > 1) "$name (${grouped.count})" else name,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = typeLabel + " · " +
                    DateUtils.getRelativeTimeSpanString(entry.timestampMillis) +
                    sourceSuffix + wifiSuffix + arkSuffix + simSuffix,
                style = MiuixTheme.textStyles.body2,
                color = if (missed) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurfaceContainerVariant,
            )
        }
    }
}

/** A contact that matches the typed digits but has no calls to show. */
@Composable
private fun ContactRow(
    contact: Contact,
    onCall: (String) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val haptics = rememberHaptics()
    val number = contact.phoneNumber
    Card(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        showIndication = true,
        onClick = { number?.let(onOpenDetails) },
    ) {
        BasicComponent(
            startAction = {
                CallsAvatar(contact.displayName, contact.photoUri, size = 40.dp, modifier = Modifier.padding(end = 12.dp))
            },
            endActions = {
                if (number != null) {
                    IconButton(
                        onClick = {
                            haptics.confirm()
                            onCall(number)
                        },
                    ) {
                        Icon(
                            MiuixIcons.Phone,
                            contentDescription = stringResource(R.string.dialpad_call),
                            tint = MiuixTheme.colorScheme.primary,
                        )
                    }
                }
            },
        ) {
            Text(
                text = contact.displayName,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (number != null) {
                Text(
                    text = number,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                )
            }
        }
    }
}

/** Contact photo, or the initial on a Miuix container when there is none. */
@Composable
internal fun CallsAvatar(
    displayName: String?,
    photoUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.tertiaryContainer),
        ) {
            val initial = displayName?.firstOrNull()
            if (initial != null) {
                Text(
                    text = initial.uppercase(),
                    fontSize = (size.value * 0.4f).sp,
                    color = MiuixTheme.colorScheme.onTertiaryContainer,
                )
            } else {
                Icon(
                    MiuixIcons.Contacts,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}
