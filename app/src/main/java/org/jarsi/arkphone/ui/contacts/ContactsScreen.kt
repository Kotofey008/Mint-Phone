package org.jarsi.arkphone.ui.contacts

import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.ui.calls.pressHighlight
import org.jarsi.arkphone.ui.components.ContactAvatar
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.contactcard.ContactCardActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Phone
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ContactsScreen(
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionState()
        onPauseOrDispose { }
    }
    val context = LocalContext.current
    ContactsContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onCall = onCall,
        onRequestPermissions = onRequestPermissions,
        onOpenContact = { id ->
            context.startActivity(ContactCardActivity.intent(context, id))
        },
        onAddContact = {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_INSERT)
                        .setType(ContactsContract.Contacts.CONTENT_TYPE),
                )
            }
        },
    )
}

@Composable
fun ContactsContent(
    uiState: ContactsUiState,
    onQueryChange: (String) -> Unit,
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenContact: (Long) -> Unit = {},
    onAddContact: () -> Unit = {},
) {
    when {
        uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            InfiniteProgressIndicator()
        }
        !uiState.hasPermission -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val haptics = rememberHaptics()
            Text(
                text = stringResource(R.string.contacts_no_permission),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 16.sp,
            )
            Spacer(Modifier.size(16.dp))
            Button(
                onClick = {
                    haptics.click()
                    onRequestPermissions()
                },
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.common_grant_access))
            }
        }
        else -> Column(Modifier.fillMaxSize()) {
            val haptics = rememberHaptics()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            ) {
                SearchBar(
                    inputField = {
                        InputField(
                            query = uiState.query,
                            onQueryChange = onQueryChange,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            label = stringResource(R.string.contacts_search_hint),
                        )
                    },
                    onExpandedChange = {},
                    expanded = false,
                    modifier = Modifier.weight(1f),
                ) {}
                IconButton(
                    onClick = {
                        haptics.click()
                        onAddContact()
                    },
                ) {
                    Icon(
                        MiuixIcons.Add,
                        contentDescription = stringResource(R.string.contacts_add),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (uiState.favorites.isNotEmpty()) {
                    item(key = "favorites-header") {
                        SectionHeader(stringResource(R.string.contacts_favorites))
                    }
                    items(uiState.favorites, key = { "fav-${it.id}" }) { contact ->
                        ContactRow(contact, onCall, onOpenContact)
                    }
                }
                if (uiState.frequent.isNotEmpty()) {
                    item(key = "frequent-header") {
                        SectionHeader(stringResource(R.string.contacts_frequent))
                    }
                    items(uiState.frequent, key = { "freq-${it.id}" }) { contact ->
                        ContactRow(contact, onCall, onOpenContact)
                    }
                }
                if (uiState.others.isNotEmpty() &&
                    (uiState.favorites.isNotEmpty() || uiState.frequent.isNotEmpty())
                ) {
                    item(key = "all-header") {
                        SectionHeader(stringResource(R.string.contacts_all))
                    }
                }
                items(uiState.others, key = { it.id }) { contact ->
                    ContactRow(contact, onCall, onOpenContact)
                }
                if (uiState.favorites.isEmpty() &&
                    uiState.frequent.isEmpty() &&
                    uiState.others.isEmpty()
                ) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(R.string.contacts_empty),
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

@Composable
private fun ContactRow(
    contact: Contact,
    onCall: (String) -> Unit,
    onOpenContact: (Long) -> Unit,
) {
    val haptics = rememberHaptics()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.number_copied)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .pressHighlight(
                onClick = {
                    haptics.click()
                    onOpenContact(contact.id)
                },
                onLongClick = {
                    contact.phoneNumber?.let { number ->
                        haptics.longPress()
                        clipboard.setText(AnnotatedString(number))
                        Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        ContactAvatar(contact, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = contact.displayName,
                fontSize = 17.sp,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            contact.phoneNumber?.let { number ->
                Text(
                    text = number,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        contact.phoneNumber?.let { number ->
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
    }
}
