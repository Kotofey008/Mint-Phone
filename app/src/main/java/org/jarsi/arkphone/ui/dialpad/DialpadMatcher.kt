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

    private const val HISTORY_MIN_TYPED = 3

    private fun String.dialableCharacters() = filter { it.isDigit() || it == '+' }

    private fun canonical(number: String, e164: (String) -> String?): String {
        val stripped = number.dialableCharacters()
        return e164(stripped) ?: stripped
    }

    /** Every form the number could be typed in: as stored, as E164, and as the
     *  trunk-0 national form. The national form is reconstructed by guessing the
     *  country-code length and round-tripping the candidate through [e164], so
     *  no country table is needed. */
    private fun dialForms(stripped: String, e164: (String) -> String?): Set<String> {
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
}
