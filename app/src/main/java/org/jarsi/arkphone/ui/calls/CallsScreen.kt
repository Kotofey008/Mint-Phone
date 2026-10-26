package org.jarsi.arkphone.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.ui.components.ContactAvatar
import org.jarsi.arkphone.ui.components.SearchRow
import org.jarsi.arkphone.ui.components.clickableListItem
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.contacts.ContactsViewModel
import org.jarsi.arkphone.ui.detail.CallDetailActivity
import org.jarsi.arkphone.ui.dialpad.DialpadGrid
import org.jarsi.arkphone.ui.dialpad.DialpadNumberField
import org.jarsi.arkphone.ui.dialpad.DialpadUiState
import org.jarsi.arkphone.ui.dialpad.DialpadViewModel
import org.jarsi.arkphone.ui.dialpad.rememberSpeedDialAction
import org.jarsi.arkphone.ui.recents.RecentsContent
import org.jarsi.arkphone.ui.recents.RecentsFilter
import org.jarsi.arkphone.ui.recents.RecentsFilterChips
import org.jarsi.arkphone.ui.recents.RecentsUiState
import org.jarsi.arkphone.ui.recents.RecentsViewModel
import org.jarsi.arkphone.ui.settings.SettingsActivity

/** Room kept under the call history while the keypad is folded into the
 *  round button, so the last rows are never hidden behind it. */
private val CollapsedKeypadClearance = 88.dp

/** Contact and history matches shown above the typed number, combined. */
private const val MAX_SUGGESTIONS = 3

/** The keypad panel never takes more than this share of the screen. */
private const val KEYPAD_MAX_HEIGHT_FRACTION = 0.92f

/**
 * The Calls tab: the call history, with the keypad folded into a round call
 * button at the bottom. Tapping the button unfolds the keypad over the list;
 * scrolling the list (or pressing back) folds it again.
 *
 * All call logic lives in the existing ViewModels; this screen only joins them.
 */
@Composable
fun CallsScreen(
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onVoicemail: () -> Unit = {},
    initialNumber: String? = null,
    onInitialNumberConsumed: () -> Unit = {},
    recentsViewModel: RecentsViewModel = hiltViewModel(),
    // Own instance: sharing the contacts tab's ViewModel would filter the
    // favorites row by whatever search query is active on that tab.
    contactsViewModel: ContactsViewModel = hiltViewModel(key = "calls-favorites"),
    dialpadViewModel: DialpadViewModel = hiltViewModel(),
) {
    val recentsUiState by recentsViewModel.uiState.collectAsStateWithLifecycle()
    val contactsUiState by contactsViewModel.uiState.collectAsStateWithLifecycle()
    val dialpadUiState by dialpadViewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        recentsViewModel.refreshPermissionState()
        contactsViewModel.refreshPermissionState()
        onPauseOrDispose { }
    }
    var keypadExpanded by rememberSaveable { mutableStateOf(false) }
    // A tel: / DIAL intent arrives with a number (possibly empty): show the
    // keypad with it filled in, like the old Keypad tab did.
    LaunchedEffect(initialNumber) {
        if (initialNumber != null) {
            dialpadViewModel.setNumber(initialNumber)
            keypadExpanded = true
            onInitialNumberConsumed()
        }
    }
    BackHandler(enabled = keypadExpanded) { keypadExpanded = false }
    val onSpeedDial = rememberSpeedDialAction(
        speedDial = dialpadUiState.speedDial,
        onCall = onCall,
        onSave = dialpadViewModel::saveSpeedDial,
    )
    val context = LocalContext.current
    CallsContent(
        favorites = contactsUiState.favorites,
        recentsUiState = recentsUiState,
        dialpadUiState = dialpadUiState,
        keypadExpanded = keypadExpanded,
        onKeypadExpandedChange = { keypadExpanded = it },
        onCall = onCall,
        onRequestPermissions = onRequestPermissions,
        onOpenSettings = { context.startActivity(SettingsActivity.intent(context)) },
        onOpenDetails = { number -> context.startActivity(CallDetailActivity.intent(context, number)) },
        onOpenNameDetails = { name -> context.startActivity(CallDetailActivity.nameIntent(context, name)) },
        onWhatsAppCall = recentsViewModel::onWhatsAppCall,
        onQueryChange = recentsViewModel::onQueryChange,
        onFilterChange = recentsViewModel::onFilterChange,
        onKey = dialpadViewModel::onKey,
        onDelete = dialpadViewModel::onDelete,
        onClear = dialpadViewModel::onClear,
        onPaste = dialpadViewModel::onPaste,
        onSuggestion = dialpadViewModel::setNumber,
        onDial = { onCall(dialpadUiState.number) },
        onVoicemail = onVoicemail,
        onSpeedDial = onSpeedDial,
    )
}

@Composable
fun CallsContent(
    favorites: List<Contact>,
    recentsUiState: RecentsUiState,
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    dialpadUiState: DialpadUiState = DialpadUiState(),
    keypadExpanded: Boolean = false,
    onKeypadExpandedChange: (Boolean) -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDetails: (String) -> Unit = {},
    onOpenNameDetails: (String) -> Unit = {},
    onWhatsAppCall: (CallLogEntry) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onFilterChange: (RecentsFilter) -> Unit = {},
    onKey: (Char) -> Unit = {},
    onDelete: () -> Unit = {},
    onClear: () -> Unit = {},
    onPaste: (String) -> Unit = {},
    onSuggestion: (String) -> Unit = {},
    onDial: () -> Unit = {},
    onVoicemail: () -> Unit = {},
    onSpeedDial: (Int) -> Unit = {},
) {
    val expanded by rememberUpdatedState(keypadExpanded)
    val onExpandedChange by rememberUpdatedState(onKeypadExpandedChange)
    // Any scroll gesture over the history folds the keypad. The connection
    // only observes: it consumes nothing, so the list scrolls as usual.
    val foldOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (expanded && source == NestedScrollSource.UserInput && available.y != 0f) {
                    onExpandedChange(false)
                }
                return Offset.Zero
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * KEYPAD_MAX_HEIGHT_FRACTION
        Column(Modifier.fillMaxSize()) {
            SearchRow(
                query = recentsUiState.query,
                onQueryChange = onQueryChange,
                placeholder = stringResource(R.string.recents_search_hint),
                onOpenSettings = onOpenSettings,
            )
            if (recentsUiState.hasPermission) {
                RecentsFilterChips(
                    selected = recentsUiState.filter,
                    onSelect = onFilterChange,
                    modifier = Modifier.fillMaxWidth(),
                    showWhatsApp = recentsUiState.showWhatsAppFilter,
                )
            }
            if (favorites.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.contacts_favorites),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                    items(favorites, key = { it.id }) { contact ->
                        FavoriteItem(contact = contact, onCall = onCall)
                    }
                }
                Text(
                    text = stringResource(R.string.tab_recents),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).nestedScroll(foldOnScroll)) {
                RecentsContent(
                    uiState = recentsUiState,
                    onCall = onCall,
                    onRequestPermissions = onRequestPermissions,
                    onOpenDetails = onOpenDetails,
                    onOpenNameDetails = onOpenNameDetails,
                    onWhatsAppCall = onWhatsAppCall,
                    contentPadding = PaddingValues(
                        bottom = if (keypadExpanded) 0.dp else CollapsedKeypadClearance,
                    ),
                )
            }
        }
        AnimatedVisibility(
            visible = !keypadExpanded,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            CollapsedCallButton(onClick = { onKeypadExpandedChange(true) })
        }
        AnimatedVisibility(
            visible = keypadExpanded,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            KeypadPanel(
                uiState = dialpadUiState,
                onFold = { onKeypadExpandedChange(false) },
                onKey = onKey,
                onDelete = onDelete,
                onClear = onClear,
                onPaste = onPaste,
                onSuggestion = onSuggestion,
                onDial = onDial,
                onVoicemail = onVoicemail,
                onSpeedDial = onSpeedDial,
                modifier = Modifier.heightIn(max = panelMaxHeight),
            )
        }
    }
}

/** The folded keypad: a compact round call button that unfolds the keypad. */
@Composable
private fun CollapsedCallButton(onClick: () -> Unit) {
    val haptics = rememberHaptics()
    FloatingActionButton(
        onClick = {
            haptics.click()
            onClick()
        },
        shape = CircleShape,
    ) {
        Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.dialpad_show))
    }
}

@Composable
private fun KeypadPanel(
    uiState: DialpadUiState,
    onFold: () -> Unit,
    onKey: (Char) -> Unit,
    onDelete: () -> Unit,
    onClear: () -> Unit,
    onPaste: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onDial: () -> Unit,
    onVoicemail: () -> Unit,
    onSpeedDial: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val contactRows = uiState.suggestions.take(MAX_SUGGESTIONS)
    val historyRows = uiState.historySuggestions.take(MAX_SUGGESTIONS - contactRows.size)
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconButton(
                onClick = {
                    haptics.click()
                    onFold()
                },
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.dialpad_hide),
                )
            }
            // Shrinks first when the screen is short, before the keys do.
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(contactRows, key = { it.id }) { contact ->
                    ListItem(
                        modifier = Modifier.clickableListItem {
                            contact.phoneNumber?.let(onSuggestion)
                        },
                        headlineContent = { Text(contact.displayName) },
                        supportingContent = { contact.phoneNumber?.let { Text(it) } },
                    )
                }
                items(historyRows, key = { it.number }) { suggestion ->
                    ListItem(
                        modifier = Modifier.clickableListItem { onSuggestion(suggestion.number) },
                        leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                        headlineContent = { Text(suggestion.display) },
                        supportingContent = {
                            Text(stringResource(R.string.dialpad_history_suggestion))
                        },
                    )
                }
            }
            DialpadNumberField(
                uiState = uiState,
                onDelete = onDelete,
                onPaste = onPaste,
                onClear = onClear,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            DialpadGrid(onKey = onKey, onVoicemail = onVoicemail, onSpeedDial = onSpeedDial)
            FloatingActionButton(
                onClick = {
                    haptics.confirm()
                    onDial()
                },
                modifier = Modifier.padding(top = 12.dp).size(72.dp),
            ) {
                Icon(Icons.Filled.Call, contentDescription = stringResource(R.string.dialpad_call))
            }
        }
    }
}

@Composable
private fun FavoriteItem(contact: Contact, onCall: (String) -> Unit) {
    val haptics = rememberHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(72.dp)
            .clickable(enabled = contact.phoneNumber != null) {
                haptics.confirm()
                contact.phoneNumber?.let(onCall)
            }
            .padding(4.dp),
    ) {
        ContactAvatar(contact = contact, size = 56.dp)
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
