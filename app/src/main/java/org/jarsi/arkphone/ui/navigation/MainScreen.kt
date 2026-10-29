package org.jarsi.arkphone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jarsi.arkphone.R
import org.jarsi.arkphone.ui.calls.CallsScreen
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.contacts.ContactsScreen
import org.jarsi.arkphone.ui.messages.MessagesScreen
import org.jarsi.arkphone.ui.settings.SettingsActivity
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Contacts
import top.yukonga.miuix.kmp.icon.extended.Messages
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Calls is the call history with the keypad folded into it. */
enum class MainTab { CALLS, MESSAGES, CONTACTS }

@Composable
fun MainScreen(
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    showDefaultDialerBanner: Boolean,
    onRequestDefaultDialer: () -> Unit,
    requestedNumber: String? = null,
    onRequestedNumberConsumed: () -> Unit = {},
    messagesRequested: Boolean = false,
    onMessagesRequestConsumed: () -> Unit = {},
    onVoicemail: () -> Unit = {},
    onOpenThread: (Long) -> Unit = {},
    onNewMessage: () -> Unit = {},
    onRequestSmsPermission: () -> Unit = {},
    onRequestSmsRole: () -> Unit = {},
    unreadMessages: Int = 0,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.CALLS) }
    LaunchedEffect(requestedNumber) {
        if (requestedNumber != null) selectedTab = MainTab.CALLS
    }
    // A tap on the Viestit launcher icon lands on the Messages tab.
    LaunchedEffect(messagesRequested) {
        if (messagesRequested) {
            selectedTab = MainTab.MESSAGES
            onMessagesRequestConsumed()
        }
    }
    BackHandler(enabled = selectedTab != MainTab.CALLS) {
        selectedTab = MainTab.CALLS
    }
    val context = LocalContext.current
    val haptics = rememberHaptics()
    // The Calls tab has a large title that collapses as its list scrolls.
    val scrollBehavior = MiuixScrollBehavior()
    // While the Calls search is active, the bars step aside for the results.
    var callsSearchActive by rememberSaveable { mutableStateOf(false) }
    // The same while several calls are being picked.
    var callsSelectionActive by rememberSaveable { mutableStateOf(false) }
    val callsFullScreen = callsSearchActive || callsSelectionActive
    LaunchedEffect(selectedTab) {
        if (selectedTab != MainTab.CALLS) {
            callsSearchActive = false
            callsSelectionActive = false
        }
    }
    Scaffold(
        topBar = {
            if (selectedTab == MainTab.CALLS && !callsFullScreen) {
                TopAppBar(
                    title = stringResource(R.string.tab_calls),
                    scrollBehavior = scrollBehavior,
                    actions = {
                        IconButton(
                            onClick = {
                                haptics.click()
                                context.startActivity(SettingsActivity.intent(context))
                            },
                        ) {
                            Icon(
                                MiuixIcons.Settings,
                                contentDescription = stringResource(R.string.settings_title),
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!callsFullScreen) {
                ArkBottomBar(
                    selected = selectedTab,
                    onSelect = { selectedTab = it },
                    unreadMessages = unreadMessages,
                )
            }
        },
    ) { padding ->
        if (selectedTab == MainTab.CALLS) {
            CallsScreen(
                onCall = onCall,
                onRequestPermissions = onRequestPermissions,
                onVoicemail = onVoicemail,
                initialNumber = requestedNumber,
                onInitialNumberConsumed = onRequestedNumberConsumed,
                contentPadding = padding,
                scrollConnection = scrollBehavior.nestedScrollConnection,
                searchActive = callsSearchActive,
                onSearchActiveChange = { callsSearchActive = it },
                selectionActive = callsSelectionActive,
                onSelectionActiveChange = { callsSelectionActive = it },
                banner = if (showDefaultDialerBanner) {
                    { DefaultDialerBanner(onRequestDefaultDialer) }
                } else {
                    null
                },
            )
        } else {
            Column(Modifier.padding(padding)) {
            if (showDefaultDialerBanner) {
                DefaultDialerBanner(onRequestDefaultDialer)
            }
            when (selectedTab) {
                MainTab.CALLS -> Unit
                MainTab.MESSAGES -> MessagesScreen(
                    onOpenThread = onOpenThread,
                    onNewMessage = onNewMessage,
                    onRequestPermission = onRequestSmsPermission,
                    onRequestSmsRole = onRequestSmsRole,
                )
                MainTab.CONTACTS -> ContactsScreen(onCall = onCall, onRequestPermissions = onRequestPermissions)
            }
            }
        }
    }
}

@Composable
fun ArkBottomBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    unreadMessages: Int = 0,
) {
    val haptics = rememberHaptics()
    val select: (MainTab) -> Unit = { tab ->
        haptics.click()
        onSelect(tab)
    }
    NavigationBar {
        NavigationBarItem(
            selected = selected == MainTab.CALLS,
            onClick = { select(MainTab.CALLS) },
            icon = MiuixIcons.Phone,
            label = stringResource(R.string.tab_calls),
        )
        NavigationBarItem(
            selected = selected == MainTab.MESSAGES,
            onClick = { select(MainTab.MESSAGES) },
            icon = MiuixIcons.Messages,
            label = stringResource(R.string.tab_messages),
            badge = if (unreadMessages > 0) {
                { UnreadBadge(unreadMessages) }
            } else {
                null
            },
        )
        NavigationBarItem(
            selected = selected == MainTab.CONTACTS,
            onClick = { select(MainTab.CONTACTS) },
            icon = MiuixIcons.Contacts,
            label = stringResource(R.string.tab_contacts),
        )
    }
}

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(CircleShape)
            .background(MiuixTheme.colorScheme.error)
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            fontSize = 10.sp,
            color = Color.White,
        )
    }
}

@Composable
private fun DefaultDialerBanner(onRequestDefaultDialer: () -> Unit) {
    val haptics = rememberHaptics()
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.banner_not_default),
                color = MiuixTheme.colorScheme.onSurfaceContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = stringResource(R.string.banner_set_default),
                onClick = {
                    haptics.click()
                    onRequestDefaultDialer()
                },
            )
        }
    }
}
