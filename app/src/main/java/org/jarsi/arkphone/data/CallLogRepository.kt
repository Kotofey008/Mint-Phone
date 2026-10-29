package org.jarsi.arkphone.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.jarsi.arkphone.data.model.CallHistorySummary
import org.jarsi.arkphone.data.model.CallHistoryTarget
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.belongsTo
import org.jarsi.arkphone.data.model.summaryOf

interface CallLogRepository {
    fun callLog(): Flow<List<CallLogEntry>>

    /**
     * Re-checks permission, starts provider observation if it just became
     * possible, and re-queries. Call after a runtime permission change.
     */
    fun refresh()

    /**
     * Rows dated at or after [sinceMillis] only — the call-path repeat-caller
     * check runs inside the screening deadline and must not read the full log.
     */
    suspend fun callsSince(sinceMillis: Long): List<CallLogEntry>

    /**
     * Deletes every log row whose number matches [number]. Returns false when
     * WRITE_CALL_LOG is missing or the delete failed.
     */
    suspend fun deleteCallsFor(number: String): Boolean

    /**
     * Rewrites the newest rejected row from [number] (dated at or after
     * [notBeforeMillis]) to the blocked type, so in-call rule blocks appear
     * under the Blocked filter. Returns false while the row has not been
     * inserted yet or the update failed.
     */
    suspend fun reclassifyLatestRejectionAsBlocked(number: String, notBeforeMillis: Long): Boolean

    /**
     * Counters and ids for every call with [target], newest first, without
     * inflating the rows. The default reads the whole log; the real
     * repositories answer from a slim query instead.
     */
    suspend fun historyFor(target: CallHistoryTarget): CallHistorySummary =
        summaryOf(callLog().first().filter { it.belongsTo(target) })

    /** The full rows for ids from [historyFor], newest first. */
    suspend fun entriesByIds(ids: List<Long>): List<CallLogEntry> {
        val wanted = ids.toSet()
        return callLog().first().filter { it.id in wanted }.sortedByDescending { it.timestampMillis }
    }
}
