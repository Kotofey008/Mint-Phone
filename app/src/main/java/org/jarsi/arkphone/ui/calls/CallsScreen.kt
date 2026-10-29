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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jarsi.arkphone.R
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.ui.contacts.ContactsViewModel
import org.jarsi.arkphone.ui.detail.CallDetailActivity
import org.jarsi.arkphone.ui.dialpad.DialpadUiState
import org.jarsi.arkphone.ui.dialpad.DialpadViewModel
import org.jarsi.arkphone.ui.recents.RecentsContent
import org.jarsi.arkphone.ui.recents.RecentsFilter
import org.jarsi.arkphone.ui.recents.RecentsUiState
import org.jarsi.arkphone.ui.recents.RecentsViewModel
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
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
 * The large title lives in the host's top bar; [scrollConnection] ties its
 * collapsing to this list and [contentPadding] is the host's inset padding.
 * All call logic lives in the existing ViewModels; this screen only joins them.
 */
@Composable
fun CallsScreen(
    onCall: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onVoicemail: () -> Unit = {},
    initialNumber: String? = null,
    onInitialNumberConsumed: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollConnection: NestedScrollConnection? = null,
    searchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
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
            onSearchActiveChange(false)
            keypadExpanded = true
            onInitialNumberConsumed()
        }
    }
    BackHandler(enabled = keypadExpanded) { keypadExpanded = false }
    val focusManager = LocalFocusManager.current
    BackHandler(enabled = searchActive) {
        recentsViewModel.onQueryChange("")
        onSearchActiveChange(false)
        focusManager.clearFocus()
    }
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
        contentPadding = contentPadding,
        scrollConnection = scrollConnection,
        searchActive = searchActive,
        onSearchActiveChange = onSearchActiveChange,
        banner = banner,
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    scrollConnection: NestedScrollConnection? = null,
    searchActive: Boolean = false,
    onSearchActiveChange: (Boolean) -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
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
    val bottomInset = contentPadding.calculateBottomPadding()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * KEYPAD_MAX_HEIGHT_FRACTION
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(foldOnScroll),
        ) {
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
                searchMode = searchActive,
                modifier = if (scrollConnection != null) Modifier.nestedScroll(scrollConnection) else Modifier,
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = bottomInset +
                        if (keypadExpanded || searchActive) 0.dp else FoldedKeypadClearance,
                ),
                topItems = {
                    if (banner != null) {
                        item(key = "banner") { banner() }
                    }
                    item(key = "search") {
                        CallsSearchBar(
                            query = recentsUiState.query,
                            onQueryChange = onQueryChange,
                            active = searchActive,
                            onActiveChange = onSearchActiveChange,
                        )
                    }
                },
            )
        }
        // Overlay layer: stays above the host's bottom bar.
        Box(Modifier.fillMaxSize().padding(bottom = bottomInset)) {
            AnimatedVisibility(
                visible = !keypadExpanded && !searchActive,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd).padding(KeypadEdgePadding),
            ) {
                FoldedCallButton(onClick = { onKeypadExpandedChange(true) })
            }
            AnimatedVisibility(
                visible = keypadExpanded && !searchActive,
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
}

/**
 * The Miuix search pill. Focusing it makes the search "active": the host hides
 * its bars, the list shows only matches, and "Cancel" appears next to the field.
 */
@Composable
private fun CallsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    active: Boolean,
    onActiveChange: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
    ) {
        SearchBar(
            inputField = {
                InputField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSearch = { focusManager.clearFocus() },
                    expanded = active,
                    onExpandedChange = onActiveChange,
                    label = stringResource(R.string.recents_search_hint),
                )
            },
            onExpandedChange = onActiveChange,
            expanded = false,
            modifier = Modifier.weight(1f),
        ) {}
        AnimatedVisibility(visible = active) {
            Text(
                text = stringResource(R.string.common_cancel),
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(end = 20.dp)
                    .clickable(interactionSource = null, indication = null) {
                        onQueryChange("")
                        onActiveChange(false)
                        focusManager.clearFocus()
                    },
            )
        }
    }
}
