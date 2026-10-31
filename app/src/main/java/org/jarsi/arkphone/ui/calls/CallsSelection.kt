package org.jarsi.arkphone.ui.calls

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Picking several calls. The host's top and bottom bars show the count and the
 * delete action while the list shows the check marks, so the state lives
 * above both and each side reads and writes it here.
 */
@Stable
class CallsSelection {
    var active by mutableStateOf(false)
        private set
    var ids by mutableStateOf(emptySet<Long>())
        private set

    /** Every call currently listed; the list keeps this current for "select all". */
    var allIds by mutableStateOf(emptyList<Long>())

    /** Set by the bottom bar; the list answers by asking to confirm the delete. */
    var deleteRequested by mutableStateOf(false)

    val count: Int get() = ids.size
    val allSelected: Boolean
        get() = allIds.isNotEmpty() && allIds.all { it in ids }

    fun start(id: Long) {
        ids = setOf(id)
        active = true
    }

    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    fun toggleAll() {
        ids = if (allSelected) emptySet() else allIds.toSet()
    }

    fun cancel() {
        active = false
        ids = emptySet()
        deleteRequested = false
    }
}
