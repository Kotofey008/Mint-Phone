package org.jarsi.arkphone.telecom

import android.telecom.TelecomManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerDisplayNameTest {

    @Test
    fun aSavedContactNameWinsOnAnOrdinaryCall() {
        assertEquals(
            "Jarsi",
            displayNameFor(
                contactName = "Jarsi",
                carrierName = "Firma Oy",
                numberPresentation = TelecomManager.PRESENTATION_ALLOWED,
            ),
        )
    }

    @Test
    fun theCarrierNameIsUsedWhenThereIsNoContact() {
        assertEquals(
            "Firma Oy",
            displayNameFor(
                contactName = null,
                carrierName = "Firma Oy",
                numberPresentation = TelecomManager.PRESENTATION_ALLOWED,
            ),
        )
    }

    @Test
    fun aHiddenNumberKeepsNoNameEvenWhenTheCarrierSendsOne() {
        // Field-hit 2026-10-19: a restricted number arrived with the carrier
        // placeholder "Anonymous" as its display name, and the phone announced
        // "Anonymous soittaa" instead of the unknown-caller text.
        assertNull(
            displayNameFor(
                contactName = null,
                carrierName = "Anonymous",
                numberPresentation = TelecomManager.PRESENTATION_RESTRICTED,
            ),
        )
    }

    @Test
    fun anUnknownOrPayphonePresentationKeepsNoName() {
        assertNull(
            displayNameFor(
                contactName = "Jarsi",
                carrierName = "Unknown",
                numberPresentation = TelecomManager.PRESENTATION_UNKNOWN,
            ),
        )
        assertNull(
            displayNameFor(
                contactName = null,
                carrierName = "Payphone",
                numberPresentation = TelecomManager.PRESENTATION_PAYPHONE,
            ),
        )
    }

    @Test
    fun blankNamesCountAsNoName() {
        assertNull(
            displayNameFor(
                contactName = " ",
                carrierName = "",
                numberPresentation = TelecomManager.PRESENTATION_ALLOWED,
            ),
        )
    }
}
