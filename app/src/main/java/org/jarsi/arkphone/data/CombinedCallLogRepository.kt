package org.jarsi.arkphone.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.jarsi.arkphone.data.model.CallHistoryItem
import org.jarsi.arkphone.data.model.CallHistorySummary
import org.jarsi.arkphone.data.model.CallHistoryTarget
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.belongsTo
import org.jarsi.arkphone.data.model.summaryOf

/**
 * The call log the UI sees: the system log and the app-owned WhatsApp log
 * merged into one timeline.
 */
class CombinedCallLogRepository(
    private val system: CallLogRepository,
    private val whatsApp: WhatsAppCallLogRepository,
) : CallLogRepository {

    override fun callLog(): Flow<List<CallLogEntry>> =
        combine(system.callLog(), whatsApp.calls()) { systemCalls, whatsAppCalls ->
            // WhatsApp ids are negated so they can't collide with system log
            // ids where entries are keyed (list items, detail rows).
            (systemCalls + whatsAppCalls.map { it.copy(id = -it.id) })
                .sortedByDescending { it.timestampMillis }
        }

    // The Room-backed WhatsApp log needs no permission-driven refresh.
    override fun refresh() = system.refresh()

    override suspend fun callsSince(sinceMillis: Long): List<CallLogEntry> =
        system.callsSince(sinceMillis) +
            whatsApp.calls().first().filter { it.timestampMillis >= sinceMillis }

    override suspend fun deleteCallsFor(number: String): Boolean {
        val systemDeleted = system.deleteCallsFor(number)
        whatsApp.deleteCallsFor(number)
        return systemDeleted
    }

    override suspend fun reclassifyLatestRejectionAsBlocked(
        number: String,
        notBeforeMillis: Long,
    ): Boolean = system.reclassifyLatestRejectionAsBlocked(number, notBeforeMillis)

    override suspend fun historyFor(target: CallHistoryTarget): CallHistorySummary {
        val fromSystem = system.historyFor(target)
        val fromWhatsApp = summaryOf(
            whatsApp.calls().first()
                .filter { it.belongsTo(target) }
                .map { it.copy(id = -it.id) },
        )
        return CallHistorySummary(
            items = (fromSystem.items + fromWhatsApp.items)
                .sortedByDescending(CallHistoryItem::timestampMillis),
            incoming = fromSystem.incoming + fromWhatsApp.incoming,
            outgoing = fromSystem.outgoing + fromWhatsApp.outgoing,
            missed = fromSystem.missed + fromWhatsApp.missed,
            totalDurationSeconds = fromSystem.totalDurationSeconds + fromWhatsApp.totalDurationSeconds,
            hasWhatsAppCalls = fromSystem.hasWhatsAppCalls || fromWhatsApp.hasWhatsAppCalls,
        )
    }

    override suspend fun entriesByIds(ids: List<Long>): List<CallLogEntry> {
        val whatsAppIds = ids.filter { it < 0 }.map { -it }.toSet()
        val fromSystem = system.entriesByIds(ids.filter { it >= 0 })
        val fromWhatsApp = if (whatsAppIds.isEmpty()) {
            emptyList()
        } else {
            whatsApp.calls().first()
                .filter { it.id in whatsAppIds }
                .map { it.copy(id = -it.id) }
        }
        return (fromSystem + fromWhatsApp).sortedByDescending { it.timestampMillis }
    }
}
