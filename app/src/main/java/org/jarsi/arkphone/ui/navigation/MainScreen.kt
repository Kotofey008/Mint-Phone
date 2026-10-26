package org.jarsi.arkphone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.jarsi.arkphone.R
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.calls.CallsScreen
import org.jarsi.arkphone.ui.contacts.ContactsScreen
import org.jarsi.arkphone.ui.messages.MessagesScreen

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
    Scaffold(
        bottomBar = {
            ArkBottomBar(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                unreadMessages = unreadMessages,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (showDefaultDialerBanner) {
                DefaultDialerBanner(onRequestDefaultDialer)
            }
            when (selectedTab) {
                MainTab.CALLS -> CallsScreen(
                    onCall = onCall,
                    onRequestPermissions = onRequestPermissions,
                    onVoicemail = onVoicemail,
                    initialNumber = requestedNumber,
                    onInitialNumberConsumed = onRequestedNumberConsumed,
                )
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
            icon = { Icon(Icons.Filled.Call, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_calls)) },
        )
        NavigationBarItem(
            selected = selected == MainTab.MESSAGES,
            onClick = { select(MainTab.MESSAGES) },
            icon = {
                if (unreadMessages > 0) {
                    BadgedBox(badge = { Badge { Text(unreadMessages.toString()) } }) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                    }
                } else {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                }
            },
            label = { Text(stringResource(R.string.tab_messages)) },
        )
        NavigationBarItem(
            selected = selected == MainTab.CONTACTS,
            onClick = { select(MainTab.CONTACTS) },
            icon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
            label = { Text(stringResource(R.string.tab_contacts)) },
        )
    }
}

@Composable
private fun DefaultDialerBanner(onRequestDefaultDialer: () -> Unit) {
    val haptics = rememberHaptics()
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.banner_not_default),
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    haptics.click()
                    onRequestDefaultDialer()
                },
            ) {
                Text(stringResource(R.string.banner_set_default))
            }
        }
    }
}
