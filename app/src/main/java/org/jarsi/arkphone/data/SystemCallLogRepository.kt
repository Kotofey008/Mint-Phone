package org.jarsi.arkphone.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import android.telephony.PhoneNumberUtils
import kotlinx.coroutines.withContext
import org.jarsi.arkphone.data.model.ARK_PHONE_ACCOUNT_ID
import android.database.Cursor
import org.jarsi.arkphone.data.model.CallHistoryItem
import org.jarsi.arkphone.data.model.CallHistorySummary
import org.jarsi.arkphone.data.model.CallHistoryTarget
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallSource
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.di.IoDispatcher
import org.jarsi.arkphone.util.PermissionChecker
import org.jarsi.arkphone.util.sameCaller
import javax.inject.Inject

internal fun callTypeFrom(systemType: Int): CallType = when (systemType) {
    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
    CallLog.Calls.MISSED_TYPE -> CallType.MISSED
    CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
    CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
    else -> CallType.OTHER
}

/** The row produced by a rule block, or null while it has not been inserted
 *  yet: rejected when the block hung up, missed when a voicemail-mode block
 *  silently rang out. A hidden caller has no digits to compare, so it only
 *  ever matches rows that are themselves numberless. */
internal fun rejectedRowToReclassify(
    rows: List<CallLogEntry>,
    number: String,
    notBeforeMillis: Long,
): Long? {
    val hiddenCaller = number.none { it.isDigit() }
    return rows.filter { row ->
        (row.type == CallType.REJECTED || row.type == CallType.MISSED) &&
            row.timestampMillis >= notBeforeMillis &&
            if (hiddenCaller) row.number.none { it.isDigit() } else sameCaller(row.number, number)
    }.maxByOrNull { it.timestampMillis }?.id
}

internal fun callSourceFrom(phoneAccountComponent: String?): CallSource = when {
    phoneAccountComponent == null -> CallSource.PHONE
    phoneAccountComponent.contains("com.whatsapp") -> CallSource.WHATSAPP
    phoneAccountComponent.contains("telephony", ignoreCase = true) ||
        phoneAccountComponent.startsWith("com.android.phone") -> CallSource.PHONE
    else -> CallSource.OTHER
}

/** The telephony stack sets the Wi-Fi feature bit on VoWiFi calls (API 29+
 *  writers; older rows simply never have it). */
internal fun isWifiCall(features: Int): Boolean =
    features and CallLog.Calls.FEATURES_WIFI != 0

internal fun whatsAppPackageFrom(phoneAccountComponent: String?): String? = when {
    phoneAccountComponent == null -> null
    phoneAccountComponent.contains("com.whatsapp.w4b") -> "com.whatsapp.w4b"
    phoneAccountComponent.contains("com.whatsapp") -> "com.whatsapp"
    else -> null
}

class SystemCallLogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionChecker: PermissionChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CallLogRepository {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    override fun callLog(): Flow<List<CallLogEntry>> {
        val resolver = context.contentResolver
        fun query(): List<CallLogEntry> {
            if (!permissionChecker.has(Manifest.permission.READ_CALL_LOG)) return emptyList()
            val entries = mutableListOf<CallLogEntry>()
            resolver.query(
                CallLog.Calls.CONTENT_URI, FULL_PROJECTION, null, null,
                CallLog.Calls.DATE + " DESC",
            )?.use { cursor ->
                while (cursor.moveToNext()) entries += cursor.toEntry()
            }
            return entries
        }

        var observer: ContentObserver? = null
        return observedQueryFlow(
            hasPermission = { permissionChecker.has(Manifest.permission.READ_CALL_LOG) },
            registerObserver = { notifyChange ->
                val registered = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        notifyChange()
                    }
                }
                observer = registered
                resolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, registered)
            },
            unregisterObserver = { observer?.let(resolver::unregisterContentObserver) },
            refreshSignal = refreshSignal,
            query = ::query,
        ).flowOn(ioDispatcher)
    }

    /** Slim rows (no name/duration) dated at or after [sinceMillis]. */
    private fun queryRowsSince(sinceMillis: Long): List<CallLogEntry> {
        val rows = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DATE),
            CallLog.Calls.DATE + " >= ?",
            arrayOf(sinceMillis.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                rows += CallLogEntry(
                    id = cursor.getLong(0),
                    number = cursor.getString(1).orEmpty(),
                    displayName = null,
                    type = callTypeFrom(cursor.getInt(2)),
                    timestampMillis = cursor.getLong(3),
                    durationSeconds = 0,
                    source = CallSource.PHONE,
                )
            }
        }
        return rows
    }

    override suspend fun callsSince(sinceMillis: Long): List<CallLogEntry> =
        withContext(ioDispatcher) {
            if (!permissionChecker.has(Manifest.permission.READ_CALL_LOG)) {
                return@withContext emptyList()
            }
            runCatching { queryRowsSince(sinceMillis) }.getOrDefault(emptyList())
        }

    override suspend fun reclassifyLatestRejectionAsBlocked(
        number: String,
        notBeforeMillis: Long,
    ): Boolean = withContext(ioDispatcher) {
        if (!permissionChecker.has(Manifest.permission.READ_CALL_LOG) ||
            !permissionChecker.has(Manifest.permission.WRITE_CALL_LOG)
        ) {
            return@withContext false
        }
        runCatching {
            val rowId = rejectedRowToReclassify(queryRowsSince(notBeforeMillis), number, notBeforeMillis)
                ?: return@runCatching false
            val values = ContentValues().apply {
                put(CallLog.Calls.TYPE, CallLog.Calls.BLOCKED_TYPE)
            }
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                values,
                CallLog.Calls._ID + " = ?",
                arrayOf(rowId.toString()),
            ) > 0
        }.getOrDefault(false)
    }

    override suspend fun deleteCallsFor(number: String): Boolean = withContext(ioDispatcher) {
        if (!permissionChecker.has(Manifest.permission.WRITE_CALL_LOG)) return@withContext false
        runCatching {
            // Number formats vary (+358 40 vs 040), so matching rows are
            // resolved client-side and deleted by id.
            val ids = mutableListOf<Long>()
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (PhoneNumberUtils.compare(cursor.getString(1).orEmpty(), number)) {
                        ids += cursor.getLong(0)
                    }
                }
            }
            if (ids.isEmpty()) return@runCatching true
            val where = CallLog.Calls._ID + " IN (" + ids.joinToString(",") + ")"
            context.contentResolver.delete(CallLog.Calls.CONTENT_URI, where, null) >= 0
        }.getOrDefault(false)
    }

    private fun Cursor.toEntry() = CallLogEntry(
        id = getLong(0),
        number = getString(1).orEmpty(),
        displayName = getString(2)?.takeIf { it.isNotBlank() },
        type = callTypeFrom(getInt(3)),
        timestampMillis = getLong(4),
        durationSeconds = getLong(5),
        source = callSourceFrom(getString(6)),
        whatsAppPackage = whatsAppPackageFrom(getString(6)),
        simAccountId = getString(7)?.takeIf { it.isNotBlank() },
        wasWifiCall = isWifiCall(getInt(8)),
        viaArkCall = getString(7) == ARK_PHONE_ACCOUNT_ID,
    )

    /**
     * One slim pass over the log (no names, no SIM columns, no row objects):
     * keeps only the calls with [target], so a long log neither stalls the
     * screen nor floods memory. Most rows are rejected by their last digits
     * before the costly platform comparison runs.
     */
    override suspend fun historyFor(target: CallHistoryTarget): CallHistorySummary =
        withContext(ioDispatcher) {
            if (!permissionChecker.has(Manifest.permission.READ_CALL_LOG)) {
                return@withContext CallHistorySummary()
            }
            runCatching { querySummary(target) }.getOrDefault(CallHistorySummary())
        }

    private fun querySummary(target: CallHistoryTarget): CallHistorySummary {
        val targetDigits = (target as? CallHistoryTarget.Number)?.number?.filter { it.isDigit() }.orEmpty()
        val items = ArrayList<CallHistoryItem>()
        var incoming = 0
        var outgoing = 0
        var missed = 0
        var duration = 0L
        var whatsApp = false
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            ),
            null, null,
            CallLog.Calls.DATE + " DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val number = cursor.getString(1).orEmpty()
                val source = callSourceFrom(cursor.getString(6))
                val matches = when (target) {
                    is CallHistoryTarget.Number ->
                        mayBeSameNumber(number, targetDigits) &&
                            PhoneNumberUtils.compare(number, target.number)
                    is CallHistoryTarget.Name ->
                        source == CallSource.WHATSAPP && number.isBlank() &&
                            cursor.getString(5)?.takeIf { it.isNotBlank() } == target.name
                }
                if (!matches) continue
                items += CallHistoryItem(cursor.getLong(0), cursor.getLong(3))
                when (callTypeFrom(cursor.getInt(2))) {
                    CallType.INCOMING -> incoming++
                    CallType.OUTGOING -> outgoing++
                    CallType.MISSED -> missed++
                    else -> Unit
                }
                duration += cursor.getLong(4)
                if (source == CallSource.WHATSAPP) whatsApp = true
            }
        }
        return CallHistorySummary(items, incoming, outgoing, missed, duration, whatsApp)
    }

    /** Cheap rejection before [PhoneNumberUtils.compare]: formats differ
     *  (+358 40 vs 040) but the last digits of one number never do. Anything
     *  without enough digits to judge (sender ids, short codes) goes on to the
     *  real comparison. */
    private fun mayBeSameNumber(rowNumber: String, targetDigits: String): Boolean {
        if (targetDigits.length < TAIL_DIGITS) return true
        val rowDigits = rowNumber.filter { it.isDigit() }
        if (rowDigits.length < TAIL_DIGITS) return true
        return rowDigits.takeLast(TAIL_DIGITS) == targetDigits.takeLast(TAIL_DIGITS)
    }

    override suspend fun entriesByIds(ids: List<Long>): List<CallLogEntry> =
        withContext(ioDispatcher) {
            if (ids.isEmpty() || !permissionChecker.has(Manifest.permission.READ_CALL_LOG)) {
                return@withContext emptyList()
            }
            runCatching {
                val rows = mutableListOf<CallLogEntry>()
                // SQLite caps bound variables, so very long id lists go in chunks.
                ids.chunked(ID_CHUNK).forEach { chunk ->
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        FULL_PROJECTION,
                        CallLog.Calls._ID + " IN (" + chunk.joinToString(",") { "?" } + ")",
                        chunk.map { it.toString() }.toTypedArray(),
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) rows += cursor.toEntry()
                    }
                }
                rows.sortedByDescending { it.timestampMillis }
            }.getOrDefault(emptyList())
        }

    private companion object {
        const val TAIL_DIGITS = 4
        const val ID_CHUNK = 400

        val FULL_PROJECTION = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME,
            CallLog.Calls.PHONE_ACCOUNT_ID,
            CallLog.Calls.FEATURES,
        )
    }
}
