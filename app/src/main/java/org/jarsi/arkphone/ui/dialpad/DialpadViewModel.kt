package org.jarsi.arkphone.ui.dialpad

import android.telephony.PhoneNumberUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.jarsi.arkphone.data.CallLogRepository
import org.jarsi.arkphone.di.DefaultDispatcher
import org.jarsi.arkphone.data.ContactsRepository
import org.jarsi.arkphone.data.SpeedDialRepository
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.util.DialingRegion
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class DialpadUiState(
    val number: String = "",
    val displayNumber: String = "",
    val suggestions: List<Contact> = emptyList(),
    val historySuggestions: List<HistorySuggestion> = emptyList(),
    val speedDial: Map<Int, String> = emptyMap(),
    /** The whole call list narrowed to what is typed; null while nothing is typed. */
    val listMatch: DialpadListMatch? = null,
)

/** A call-log number matching the typed prefix, offered like a contact hit. */
data class HistorySuggestion(val number: String, val display: String)

/** The typed number as shown: formatted for [countryIso], except for star/hash codes. */
internal fun formatDialpadNumber(number: String, countryIso: String): String {
    if (number.isBlank()) return number
    if (number.any { it == '*' || it == '#' }) return number
    return PhoneNumberUtils.formatNumber(number, countryIso) ?: number
}

private val PHONE_CHARACTERS = setOf('+', '*', '#')

@HiltViewModel
class DialpadViewModel @Inject constructor(
    contactsRepository: ContactsRepository,
    private val speedDialRepository: SpeedDialRepository,
    callLogRepository: CallLogRepository,
    private val dialingRegion: DialingRegion,
    @DefaultDispatcher private val matchDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val callLog = callLogRepository.callLog()

    private val number = MutableStateFlow("")

    private data class Suggestions(
        val contacts: List<Contact> = emptyList(),
        val history: List<HistorySuggestion> = emptyList(),
        val listMatch: DialpadListMatch? = null,
    )

    /** E164 and national forms of a number, remembered across index rebuilds
     *  so only numbers that are new since the last build reach libphonenumber. */
    private val formsCache = ConcurrentHashMap<String, DialpadMatcher.NumberForms>()

    /** The slow part, done once per change of contacts or call log instead of
     *  once per keystroke: libphonenumber over every number, T9 encoding of
     *  every name. Off the main thread; a newer change replaces a running build. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val index: Flow<DialpadMatcher.DialpadIndex> =
        combine(contactsRepository.contacts(), callLog) { contacts, log -> contacts to log }
            .mapLatest { (contacts, log) ->
                val country = dialingRegion.countryIso()
                val e164 = { candidate: String -> PhoneNumberUtils.formatNumberToE164(candidate, country) }
                DialpadMatcher.buildIndex(contacts, log) { raw ->
                    val stripped = raw.filter { it.isDigit() || it == '+' }
                    if (stripped.isEmpty()) {
                        null
                    } else {
                        formsCache.getOrPut("$country:$stripped") {
                            DialpadMatcher.numberForms(raw, e164)!!
                        }
                    }
                }
            }
            .flowOn(matchDispatcher)

    /** A keystroke now only compares strings against the prepared index, so
     *  suggestions follow the typing without lag; mapLatest still drops a
     *  search the next keystroke has already outdated. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val suggestions =
        combine(index, number) { prepared, typed -> prepared to typed }
            .mapLatest { (prepared, typed) ->
                val found = DialpadMatcher.search(prepared, typed)
                val country = dialingRegion.countryIso()
                Suggestions(
                    contacts = found.contacts.take(3),
                    history = found.history.map { HistorySuggestion(it, formatDialpadNumber(it, country)) },
                    listMatch = found.listMatch,
                )
            }.flowOn(matchDispatcher)
            // The typed number must echo before the call-log query answers:
            // returning to the dialpad restarts the upstream flows, and a
            // combine without this seed swallows the first keystrokes.
            .onStart { emit(Suggestions()) }

    val uiState: StateFlow<DialpadUiState> =
        combine(
            number,
            speedDialRepository.entries,
            suggestions,
        ) { number, speedDial, matched ->
            DialpadUiState(
                number = number,
                displayNumber = formatDialpadNumber(number, dialingRegion.countryIso()),
                suggestions = matched.contacts,
                historySuggestions = matched.history,
                speedDial = speedDial,
                listMatch = matched.listMatch,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DialpadUiState(),
        )

    fun onPaste(text: String) {
        number.value += text.filter { it.isDigit() || it in PHONE_CHARACTERS }
    }

    fun saveSpeedDial(digit: Int, number: String) {
        viewModelScope.launch { speedDialRepository.set(digit, number) }
    }

    fun onKey(digit: Char) {
        number.value += digit
    }

    fun onDelete() {
        number.value = number.value.dropLast(1)
    }

    fun onClear() {
        number.value = ""
    }

    fun setNumber(value: String) {
        number.value = value
    }
}
