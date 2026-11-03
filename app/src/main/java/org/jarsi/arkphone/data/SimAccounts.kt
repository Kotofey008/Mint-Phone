package org.jarsi.arkphone.data

import org.jarsi.arkphone.data.model.SimAccount

/** The SIM an outgoing call should use, or null for the system default —
 *  which is also the answer when the chosen SIM is no longer present. */
internal fun selectedSimAccount(chosenId: String?, available: List<SimAccount>): SimAccount? =
    available.firstOrNull { it.id == chosenId }

/** The SIM name to show beside a call, or null when there is nothing worth
 *  showing: one SIM, an unknown account, or no account recorded at all. */
internal fun simLabelFor(accountId: String?, available: List<SimAccount>): String? {
    if (available.size < 2) return null
    return available.firstOrNull { it.id == accountId }?.label
}

/**
 * 1-based slot index of [accountId] among [available], for the HyperOS-style
 * SIM chip on call rows. Null when there is only one SIM or the account is unknown.
 */
internal fun simIndexFor(accountId: String?, available: List<SimAccount>): Int? {
    if (available.size < 2 || accountId.isNullOrBlank()) return null
    val index = available.indexOfFirst { it.id == accountId }
    return if (index >= 0) index + 1 else null
}

