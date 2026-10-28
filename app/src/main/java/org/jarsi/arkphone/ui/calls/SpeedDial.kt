package org.jarsi.arkphone.ui.calls

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/** Long-press on a digit key: dials the assigned speed-dial number, or opens
 *  the contact picker to assign one. */
@Composable
internal fun rememberSpeedDialAction(
    speedDial: Map<Int, String>,
    onCall: (String) -> Unit,
    onSave: (digit: Int, number: String) -> Unit,
): (Int) -> Unit {
    val context = LocalContext.current
    var pendingSpeedDialDigit by rememberSaveable { mutableStateOf<Int?>(null) }
    val speedDialPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val digit = pendingSpeedDialDigit
        pendingSpeedDialDigit = null
        val uri = result.data?.data
        if (digit != null && result.resultCode == Activity.RESULT_OK && uri != null) {
            phoneNumberFromPickedUri(context, uri)?.let { onSave(digit, it) }
        }
    }
    return { digit ->
        val assigned = speedDial[digit]
        if (assigned != null) {
            onCall(assigned)
        } else {
            pendingSpeedDialDigit = digit
            runCatching {
                speedDialPicker.launch(
                    Intent(
                        Intent.ACTION_PICK,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    ),
                )
            }
        }
    }
}

private fun phoneNumberFromPickedUri(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(
        uri,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null,
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
}.getOrNull()
