package org.jarsi.arkphone.data.model

import android.telephony.PhoneNumberUtils

/** Whose calls a detail view is about: a phone number, or a bare WhatsApp name. */
sealed interface CallHistoryTarget {
    data class Number(val number: String) : CallHistoryTarget
    data class Name(val name: String) : CallHistoryTarget
}

/** One call in a history index: just enough to order it and fetch its row later. */
data class CallHistoryItem(val id: Long, val timestampMillis: Long)

/**
 * A cheap summary of every call with one caller: the counters for the stats
 * card, and the ids (newest first) that rows are loaded from a page at a time.
 * Building it never inflates a [CallLogEntry].
 */
data class CallHistorySummary(
    val items: List<CallHistoryItem> = emptyList(),
    val incoming: Int = 0,
    val outgoing: Int = 0,
    val missed: Int = 0,
    val totalDurationSeconds: Long = 0,
    val hasWhatsAppCalls: Boolean = false,
) {
    val total: Int get() = items.size
    val latestMillis: Long? get() = items.firstOrNull()?.timestampMillis
}

internal fun CallLogEntry.belongsTo(target: CallHistoryTarget): Boolean = when (target) {
    is CallHistoryTarget.Number -> PhoneNumberUtils.compare(number, target.number)
    is CallHistoryTarget.Name ->
        source == CallSource.WHATSAPP && number.isBlank() && displayName == target.name
}

internal fun summaryOf(entries: List<CallLogEntry>): CallHistorySummary = CallHistorySummary(
    items = entries.sortedByDescending { it.timestampMillis }
        .map { CallHistoryItem(it.id, it.timestampMillis) },
    incoming = entries.count { it.type == CallType.INCOMING },
    outgoing = entries.count { it.type == CallType.OUTGOING },
    missed = entries.count { it.type == CallType.MISSED },
    totalDurationSeconds = entries.sumOf { it.durationSeconds },
    hasWhatsAppCalls = entries.any { it.source == CallSource.WHATSAPP },
)
