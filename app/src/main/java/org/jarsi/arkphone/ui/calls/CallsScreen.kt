package org.jarsi.arkphone.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.ui.components.rememberHaptics
import org.jarsi.arkphone.ui.contacts.ContactsViewModel
import org.jarsi.arkphone.ui.detail.CallDetailActivity
import org.jarsi.arkphone.ui.dialpad.DialpadUiState
import org.jarsi.arkphone.ui.dialpad.DialpadViewModel
import org.jarsi.arkphone.ui.recents.RecentsContent
import org.jarsi.arkphone.ui.recents.RecentsFilter
import org.jarsi.arkphone.ui.recents.RecentsUiState
import org.jarsi.arkphone.ui.recents.RecentsViewModel
import org.jarsi.arkphone.ui.settings.SettingsActivity
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Room kept under the call list while the keypad is folded into the round
 *  button, so the last rows are never hidden behind it. */
private val FoldedKeypadClearance = KeypadSmallButton + KeypadEdgePadding * 2

/** The keypad panel never takes more than this share of the screen. */
private const val KEYPAD_MAX_HEIGHT_FRACTION = 0.92f

/**
 * The Calls tab: call history with the keypad folded into a round call button.
 * Tapping the button unfolds the keypad over the list; scrolling the list (or
 * pressing back) folds it again. While digits are typed the list shows only
 * the calls and contacts that match them.
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
    onDial: () -> Unit = {},
    onVoicemail: () -> Unit = {},
    onSpeedDial: (Int) -> Unit = {},
) {
    val expanded by rememberUpdatedState(keypadExpanded)
    val onExpandedChange by rememberUpdatedState(onKeypadExpandedChange)
    // Any scroll gesture over the list folds the keypad. The connection only
    // observes: it consumes nothing, so the list scrolls as usual.
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
    // While digits are typed the whole list narrows to what they match: the
    // matching calls, plus matching contacts that were never called.
    val listMatch = dialpadUiState.listMatch
    val shownRecents = remember(recentsUiState, listMatch) {
        if (listMatch == null) {
            recentsUiState
        } else {
            recentsUiState.copy(
                entries = recentsUiState.entries.filter { it.entry.id in listMatch.callIds },
            )
        }
    }
    val contactRows = if (
        listMatch != null &&
        recentsUiState.filter == RecentsFilter.ALL &&
        recentsUiState.query.isBlank()
    ) {
        listMatch.contactsWithoutCalls
    } else {
        emptyList()
    }
    val typedNumber = dialpadUiState.number.takeIf { it.isNotEmpty() && !keypadExpanded }
        ?.let { dialpadUiState.displayNumber.ifEmpty { it } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * KEYPAD_MAX_HEIGHT_FRACTION
        Column(Modifier.fillMaxSize()) {
            CallsSearchRow(
                query = recentsUiState.query,
                onQueryChange = onQueryChange,
                onOpenSettings = onOpenSettings,
            )
            Box(Modifier.weight(1f).nestedScroll(foldOnScroll)) {
                RecentsContent(
                    uiState = shownRecents,
                    onCall = onCall,
                    onRequestPermissions = onRequestPermissions,
                    onOpenDetails = onOpenDetails,
                    onOpenNameDetails = onOpenNameDetails,
                    onWhatsAppCall = onWhatsAppCall,
                    onFilterChange = onFilterChange,
                    // Favorites are not narrowed by the typed digits, so
                    // they step aside while a number is being looked up.
                    favorites = if (listMatch == null) favorites else emptyList(),
                    contactRows = contactRows,
                    typedNumber = typedNumber,
                    onTypedClick = { onKeypadExpandedChange(true) },
                    onTypedClear = onClear,
                    contentPadding = PaddingValues(
                        bottom = if (keypadExpanded) 0.dp else FoldedKeypadClearance,
                    ),
                )
            }
        }
        AnimatedVisibility(
            visible = !keypadExpanded,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(KeypadEdgePadding),
        ) {
            FoldedCallButton(onClick = { onKeypadExpandedChange(true) })
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
                onDial = onDial,
                onVoicemail = onVoicemail,
                onSpeedDial = onSpeedDial,
                modifier = Modifier.heightIn(max = panelMaxHeight),
            )
        }
    }
}

/** The Miuix search field with the settings button beside it. */
@Composable
private fun CallsSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val haptics = rememberHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, end = 12.dp),
    ) {
        SearchBar(
            inputField = {
                InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = {},
                    expanded = false,
                    onExpandedChange = {},
                    label = stringResource(R.string.recents_search_hint),
                )
            },
            onExpandedChange = {},
            expanded = false,
            modifier = Modifier.weight(1f),
        ) {}
        IconButton(
            onClick = {
                haptics.click()
                onOpenSettings()
            },
            modifier = Modifier.padding(start = 4.dp),
        ) {
            Icon(
                MiuixIcons.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MiuixTheme.colorScheme.onSurfaceContainer,
            )
        }
    }
}
