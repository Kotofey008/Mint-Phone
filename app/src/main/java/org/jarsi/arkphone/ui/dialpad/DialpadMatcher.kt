package org.jarsi.arkphone.ui.dialpad

import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.Contact

/** What the typed digits leave of the whole call history: the call rows to
 *  keep, and matching contacts that have never been called or called by. */
data class DialpadListMatch(
    val callIds: Set<Long>,
    val contactsWithoutCalls: List<Contact>,
)

object DialpadMatcher {

    /**
     * Filters the full call log by the typed digits. A call stays when the
     * caller's name matches (T9), the number matches in any dial form, or its
     * number belongs to a matching contact. Matching contacts that appear in
     * no call at all are returned separately so they can still be listed.
     * [matchedContacts] must come from [filter] for the same [query].
     */
    fun matchList(
        entries: List<CallLogEntry>,
        matchedContacts: List<Contact>,
        query: String,
        e164: (String) -> String?,
    ): DialpadListMatch {
        val typed = query.dialableCharacters()
        val contactKeys = matchedContacts.mapNotNull { it.phoneNumber }
            .map { canonical(it, e164) }
            .toSet()
        val callKeys = mutableSetOf<String>()
        val ids = mutableSetOf<Long>()
        for (entry in entries) {
            val stripped = entry.number.dialableCharacters()
            val key = stripped.takeIf { it.isNotEmpty() }?.let { e164(it) ?: it }
            if (key != null) callKeys += key
            val byName = entry.displayName?.let { matches(it, query) } == true
            val byContact = key != null && key in contactKeys
            val byNumber = stripped.isNotEmpty() && typed.isNotEmpty() && (
                if (typed.length < HISTORY_MIN_TYPED) {
                    stripped.startsWith(typed)
                } else {
                    dialForms(stripped, e164).any { it.startsWith(typed) }
                }
                )
            if (byName || byContact || byNumber) ids += entry.id
        }
        val withoutCalls = matchedContacts.filter { contact ->
            val number = contact.phoneNumber ?: return@filter false
            canonical(number, e164) !in callKeys
        }
        return DialpadListMatch(ids, withoutCalls)
    }

    fun filterHistory(
        entries: List<CallLogEntry>,
        contacts: List<Contact>,
        query: String,
        e164: (String) -> String?,
    ): List<String> {
        val typed = query.dialableCharacters()
        if (typed.length < HISTORY_MIN_TYPED) return emptyList()
        val contactKeys = contacts.mapNotNull { it.phoneNumber }
            .map { canonical(it, e164) }
            .toSet()
        val seen = mutableSetOf<String>()
        val result = mutableListOf<String>()
        for (entry in entries.sortedByDescending { it.timestampMillis }) {
            if (entry.type == CallType.BLOCKED) continue
            val stripped = entry.number.dialableCharacters()
            if (stripped.isEmpty()) continue
            val key = e164(stripped) ?: stripped
            if (key in contactKeys || !seen.add(key)) continue
            if (dialForms(stripped, e164).any { it.startsWith(typed) }) result += entry.number
        }
        return result
    }

    internal const val HISTORY_MIN_TYPED = 3

    internal fun String.dialableCharacters() = filter { it.isDigit() || it == '+' }

    internal fun canonical(number: String, e164: (String) -> String?): String {
        val stripped = number.dialableCharacters()
        return e164(stripped) ?: stripped
    }

    /** Every form the number could be typed in: as stored, as E164, and as the
     *  trunk-0 national form. The national form is reconstructed by guessing the
     *  country-code length and round-tripping the candidate through [e164], so
     *  no country table is needed. */
    internal fun dialForms(stripped: String, e164: (String) -> String?): Set<String> {
        val forms = mutableSetOf(stripped)
        val canonical = e164(stripped) ?: return forms
        forms += canonical
        for (ccLength in 1..3) {
            val candidate = "0" + canonical.drop(1 + ccLength)
            if (e164(candidate) == canonical) {
                forms += candidate
                break
            }
        }
        return forms
    }

    private val keys = mapOf(
        '2' to "abcäå", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mnoö", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    )
    private val letterToDigit: Map<Char, Char> =
        keys.flatMap { (digit, letters) -> letters.map { it to digit } }.toMap()

    fun digitFor(letter: Char): Char? = letterToDigit[letter.lowercaseChar()]

    fun matches(name: String, digits: String): Boolean {
        if (digits.isEmpty()) return true
        return name.split(' ').any { word ->
            val encoded = word.mapNotNull { digitFor(it) }.joinToString("")
            encoded.startsWith(digits)
        }
    }

    /** Name (T9), raw substring, or — from [HISTORY_MIN_TYPED] typed digits,
     *  the floor that keeps a lone trunk zero from matching the whole book —
     *  any dial form of the number. The history list hides numbers that
     *  belong to a contact, so a contact missed here vanishes from both. */
    fun filter(
        contacts: List<Contact>,
        query: String,
        e164: (String) -> String? = { null },
    ): List<Contact> {
        if (query.isEmpty()) return emptyList()
        val typed = query.dialableCharacters()
        return contacts.filter { contact ->
            matches(contact.displayName, query) ||
                contact.phoneNumber?.contains(query) == true ||
                contact.phoneNumber?.matchesAcrossFormats(typed, e164) == true
        }
    }

    private fun String.matchesAcrossFormats(typed: String, e164: (String) -> String?): Boolean {
        if (typed.length < HISTORY_MIN_TYPED) return false
        val stripped = dialableCharacters()
        if (stripped.isEmpty()) return false
        return dialForms(stripped, e164).any { it.startsWith(typed) }
    }

    /** Everything about a number that matching needs, computed once. */
    class NumberForms(val key: String, val forms: Set<String>, val stripped: String)

    /** The digits a name encodes to on the keypad, one string per word. */
    internal fun t9Words(name: String): List<String> =
        name.split(' ').map { word -> word.mapNotNull { digitFor(it) }.joinToString("") }
            .filter { it.isNotEmpty() }

    private fun matchesT9(words: List<String>, digits: String): Boolean =
        digits.isEmpty() || words.any { it.startsWith(digits) }

    class IndexedContact(
        val contact: Contact,
        val t9: List<String>,
        val number: NumberForms?,
    )

    class IndexedCall(
        val entry: CallLogEntry,
        val t9: List<String>,
        val number: NumberForms?,
    )

    /** Contacts and calls, prepared so a keystroke only compares strings. */
    class DialpadIndex(
        val contacts: List<IndexedContact>,
        /** Newest first. */
        val calls: List<IndexedCall>,
    ) {
        val contactKeys: Set<String> = contacts.mapNotNull { it.number?.key }.toSet()
        val callKeys: Set<String> = calls.mapNotNull { it.number?.key }.toSet()
    }

    /**
     * Builds the index. The costly part is asking libphonenumber (through
     * [forms]) for each number's E164 and national forms, so [forms] is
     * expected to memoize: only numbers new since the last build cost anything.
     */
    fun buildIndex(
        contacts: List<Contact>,
        log: List<CallLogEntry>,
        forms: (String) -> NumberForms?,
    ): DialpadIndex = DialpadIndex(
        contacts = contacts.map { IndexedContact(it, t9Words(it.displayName), it.phoneNumber?.let(forms)) },
        calls = log.sortedByDescending { it.timestampMillis }.map { entry ->
            IndexedCall(entry, entry.displayName?.let(::t9Words).orEmpty(), forms(entry.number))
        },
    )

    fun numberForms(raw: String, e164: (String) -> String?): NumberForms? {
        val stripped = raw.dialableCharacters()
        if (stripped.isEmpty()) return null
        return NumberForms(
            key = e164(stripped) ?: stripped,
            forms = dialForms(stripped, e164),
            stripped = stripped,
        )
    }

    /** What typing [query] finds. Same rules as [filter], [filterHistory] and
     *  [matchList], over the prepared [DialpadIndex]. */
    class Search(
        val contacts: List<Contact>,
        val history: List<String>,
        val listMatch: DialpadListMatch?,
    )

    fun search(index: DialpadIndex, query: String): Search {
        if (query.isEmpty()) return Search(emptyList(), emptyList(), null)
        val typed = query.dialableCharacters()
        val longEnough = typed.length >= HISTORY_MIN_TYPED

        val matched = index.contacts.filter { c ->
            matchesT9(c.t9, query) ||
                c.contact.phoneNumber?.contains(query) == true ||
                (longEnough && c.number?.forms?.any { it.startsWith(typed) } == true)
        }
        val matchedKeys = matched.mapNotNull { it.number?.key }.toSet()

        val history = ArrayList<String>(HISTORY_SUGGESTIONS)
        if (longEnough) {
            val seen = HashSet<String>()
            for (call in index.calls) {
                if (history.size >= HISTORY_SUGGESTIONS) break
                if (call.entry.type == CallType.BLOCKED) continue
                val number = call.number ?: continue
                if (number.key in index.contactKeys || !seen.add(number.key)) continue
                if (number.forms.any { it.startsWith(typed) }) history += call.entry.number
            }
        }

        val ids = HashSet<Long>()
        for (call in index.calls) {
            val number = call.number
            val byName = matchesT9(call.t9, query) && call.entry.displayName != null
            val byContact = number != null && number.key in matchedKeys
            val byNumber = number != null && typed.isNotEmpty() && (
                if (typed.length < HISTORY_MIN_TYPED) {
                    number.stripped.startsWith(typed)
                } else {
                    number.forms.any { it.startsWith(typed) }
                }
                )
            if (byName || byContact || byNumber) ids += call.entry.id
        }
        val withoutCalls = matched.filter { c ->
            val number = c.number ?: return@filter false
            number.key !in index.callKeys
        }.map { it.contact }
        return Search(
            contacts = matched.map { it.contact },
            history = history,
            listMatch = DialpadListMatch(ids, withoutCalls),
        )
    }

    private const val HISTORY_SUGGESTIONS = 3
}
