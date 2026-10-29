package org.jarsi.arkphone.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jarsi.arkphone.data.BlockedNumbersRepository
import org.jarsi.arkphone.data.CallLogRepository
import org.jarsi.arkphone.data.ContactsRepository
import org.jarsi.arkphone.data.SimAccountRepository
import org.jarsi.arkphone.data.WhatsAppCallLogRepository
import org.jarsi.arkphone.data.model.CallHistorySummary
import org.jarsi.arkphone.data.model.CallHistoryTarget
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallSource
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.ContactMatch
import org.jarsi.arkphone.data.model.SimAccount
import javax.inject.Inject

/** Calls shown before "Show more" is pressed. */
internal const val FIRST_PAGE = 3

/** Calls added by each "Show more". */
internal const val PAGE_SIZE = 10

data class CallStats(
    val total: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val totalDurationSeconds: Long,
    val latestMillis: Long?,
)

fun computeCallStats(entries: List<CallLogEntry>): CallStats = CallStats(
    total = entries.size,
    incoming = entries.count { it.type == CallType.INCOMING },
    outgoing = entries.count { it.type == CallType.OUTGOING },
    missed = entries.count { it.type == CallType.MISSED },
    totalDurationSeconds = entries.sumOf { it.durationSeconds },
    latestMillis = entries.maxOfOrNull { it.timestampMillis },
)

data class CallDetailUiState(
    val number: String = "",
    val displayName: String? = null,
    val photoUri: String? = null,
    val blocked: Boolean = false,
    val canBlock: Boolean = false,
    /** The calls loaded so far, newest first — never the whole history at once. */
    val entries: List<CallLogEntry> = emptyList(),
    /** Counters over every call with this caller, not just the loaded ones. */
    val stats: CallStats = computeCallStats(entries),
    /** How many calls the caller has in all; [entries] may hold fewer. */
    val totalCalls: Int = entries.size,
    val hasWhatsAppCalls: Boolean = entries.any { it.source == CallSource.WHATSAPP },
    val loading: Boolean = true,
    val simAccounts: List<SimAccount> = emptyList(),
) {
    val remainingCalls: Int get() = (totalCalls - entries.size).coerceAtLeast(0)
    val hasMore: Boolean get() = remainingCalls > 0

    /** False for a name-only WhatsApp caller — phone actions are hidden then. */
    val hasNumber: Boolean get() = number.isNotBlank()
}

private fun CallHistorySummary.toStats() = CallStats(
    total = total,
    incoming = incoming,
    outgoing = outgoing,
    missed = missed,
    totalDurationSeconds = totalDurationSeconds,
    latestMillis = latestMillis,
)

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    private val callLogRepository: CallLogRepository,
    private val contactsRepository: ContactsRepository,
    private val blockedNumbersRepository: BlockedNumbersRepository,
    private val whatsAppCallLogRepository: WhatsAppCallLogRepository,
    private val simAccountRepository: SimAccountRepository,
) : ViewModel() {

    private val simAccounts = MutableStateFlow<List<SimAccount>>(emptyList())

    init {
        viewModelScope.launch { simAccounts.value = simAccountRepository.accounts() }
    }

    /** What the detail view is about: a phone number, or — for WhatsApp
     *  callers whose number could not be safely resolved — a bare name.
     *  A name key never coalesces with numbered rows: those belong to the
     *  number's own detail view. */
    private sealed interface DetailKey
    private data class NumberKey(val number: String) : DetailKey
    private data class NameKey(val name: String) : DetailKey

    private val key = MutableStateFlow<DetailKey?>(null)
    private val contact = MutableStateFlow<ContactMatch?>(null)
    private val blocked = MutableStateFlow(false)
    private val canBlock = MutableStateFlow(false)

    /** Counters and ids for the whole history; null until first loaded. */
    private val summary = MutableStateFlow<CallHistorySummary?>(null)

    /** The rows fetched so far, a page at a time. */
    private val loadedEntries = MutableStateFlow<List<CallLogEntry>>(emptyList())
    private var loadingMore = false

    private val blockingState = combine(blocked, canBlock, simAccounts) { isBlocked, available, sims ->
        Triple(isBlocked, available, sims)
    }

    val uiState: StateFlow<CallDetailUiState> = combine(
        key.filterNotNull(), summary, loadedEntries, contact, blockingState,
    ) { target, history, loaded, match, (isBlocked, blockingAvailable, sims) ->
        CallDetailUiState(
            number = (target as? NumberKey)?.number.orEmpty(),
            displayName = when (target) {
                is NameKey -> target.name
                is NumberKey -> match?.displayName?.takeIf { it.isNotBlank() }
                    ?: loaded.firstOrNull { it.displayName != null }?.displayName
            },
            photoUri = match?.photoUri,
            blocked = isBlocked,
            canBlock = blockingAvailable,
            entries = loaded,
            stats = history?.toStats() ?: CallStats(0, 0, 0, 0, 0, null),
            totalCalls = history?.total ?: 0,
            hasWhatsAppCalls = history?.hasWhatsAppCalls ?: false,
            loading = history == null,
            simAccounts = sims,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CallDetailUiState(),
    )

    /** Reads the counters and ids for the whole history in one slim pass, then
     *  only the first page of rows — so a long history never stalls the screen. */
    private suspend fun loadHistory() {
        val target = when (val current = key.value) {
            is NumberKey -> CallHistoryTarget.Number(current.number)
            is NameKey -> CallHistoryTarget.Name(current.name)
            null -> return
        }
        val history = callLogRepository.historyFor(target)
        val firstRows = callLogRepository.entriesByIds(history.items.take(FIRST_PAGE).map { it.id })
        loadedEntries.value = firstRows
        summary.value = history
    }

    /** Loads the next [PAGE_SIZE] calls below the ones already shown. */
    fun onShowMore() {
        val history = summary.value ?: return
        if (loadingMore) return
        loadingMore = true
        viewModelScope.launch {
            try {
                val have = loadedEntries.value.size
                val nextIds = history.items.drop(have).take(PAGE_SIZE).map { it.id }
                if (nextIds.isNotEmpty()) {
                    val rows = callLogRepository.entriesByIds(nextIds)
                    loadedEntries.value = loadedEntries.value + rows
                }
            } finally {
                loadingMore = false
            }
        }
    }

    fun setNumber(target: String) {
        if (key.value == NumberKey(target)) return
        key.value = NumberKey(target)
        viewModelScope.launch { loadHistory() }
        viewModelScope.launch {
            contact.value = contactsRepository.lookupContact(target)
            canBlock.value = blockedNumbersRepository.canBlock()
            blocked.value = blockedNumbersRepository.isBlocked(target)
        }
    }

    fun setNameKey(name: String) {
        if (key.value == NameKey(name)) return
        key.value = NameKey(name)
        viewModelScope.launch { loadHistory() }
    }

    fun onToggleBlocked() {
        val target = (key.value as? NumberKey)?.number ?: return
        viewModelScope.launch {
            if (blocked.value) {
                if (blockedNumbersRepository.unblock(target)) blocked.value = false
            } else {
                if (blockedNumbersRepository.block(target)) blocked.value = true
            }
        }
    }

    fun onDeleteHistory() {
        when (val target = key.value) {
            is NumberKey -> viewModelScope.launch {
                callLogRepository.deleteCallsFor(target.number)
                loadHistory()
            }
            is NameKey -> viewModelScope.launch {
                whatsAppCallLogRepository.deleteCallsForName(target.name)
                loadHistory()
            }
            null -> Unit
        }
    }
}
