package org.jarsi.arkphone.ui.recents

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallSource
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.SimAccount
import org.jarsi.arkphone.ui.MiuixTestHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RecentsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(
        source: CallSource = CallSource.PHONE,
        number: String = "0401234567",
        name: String? = "Matti Meikäläinen",
        simAccountId: String? = null,
    ) = CallLogEntry(
        id = 1,
        number = number,
        displayName = name,
        type = CallType.INCOMING,
        timestampMillis = 0,
        durationSeconds = 10,
        source = source,
        simAccountId = simAccountId,
    )

    private fun setContent(
        entry: CallLogEntry,
        count: Int = 1,
        simAccounts: List<SimAccount> = emptyList(),
        onCall: (String) -> Unit = {},
        onOpenDetails: (String) -> Unit = {},
        onOpenNameDetails: (String) -> Unit = {},
        onWhatsAppCall: (CallLogEntry) -> Unit = {},
        selectionMode: Boolean = false,
        selectedIds: Set<Long> = emptySet(),
        onToggleSelect: (Long) -> Unit = {},
        onStartSelection: (Long) -> Unit = {},
        onEditBeforeCall: (String) -> Unit = {},
        onMessage: (String) -> Unit = {},
        onCallOnSim: (String, String) -> Unit = { _, _ -> },
        onRequestDelete: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            MiuixTestHost {
            RecentsContent(
                uiState = RecentsUiState(
                    loading = false,
                    entries = listOf(GroupedCallLogEntry(entry, count)),
                    simAccounts = simAccounts,
                ),
                onCall = onCall,
                onRequestPermissions = {},
                onOpenDetails = onOpenDetails,
                onOpenNameDetails = onOpenNameDetails,
                onWhatsAppCall = onWhatsAppCall,
                selectionMode = selectionMode,
                selectedIds = selectedIds,
                onToggleSelect = onToggleSelect,
                onStartSelection = onStartSelection,
                onEditBeforeCall = onEditBeforeCall,
                onMessage = onMessage,
                onCallOnSim = onCallOnSim,
                onRequestDelete = onRequestDelete,
            )
            }
        }
    }

    private val twoSims = listOf(
        SimAccount("sub-1", "DNA", null),
        SimAccount("sub-2", "Elisa", null),
    )

    @Test
    fun theSimNameIsShownOnARowWhenTheDeviceHasSeveralSims() {
        setContent(entry(simAccountId = "sub-2"), simAccounts = twoSims)
        composeRule.onNodeWithText("Elisa", substring = true).assertIsDisplayed()
    }

    @Test
    fun theSimNameIsHiddenOnASingleSimDevice() {
        setContent(
            entry(simAccountId = "sub-1"),
            simAccounts = listOf(SimAccount("sub-1", "DNA", null)),
        )
        composeRule.onNodeWithText("DNA", substring = true).assertDoesNotExist()
    }

    @Test
    fun theDetailsButtonOfANumberlessWhatsAppRowOpensNameDetails() {
        val openedNames = mutableListOf<String>()
        val openedNumbers = mutableListOf<String>()
        setContent(
            entry(source = CallSource.WHATSAPP, number = ""),
            onOpenDetails = { openedNumbers += it },
            onOpenNameDetails = { openedNames += it },
        )
        composeRule.onNodeWithContentDescription("Call details").performClick()
        assertEquals(listOf("Matti Meikäläinen"), openedNames)
        assertEquals(emptyList<String>(), openedNumbers)
    }

    @Test
    fun theDetailsButtonOpensDetailsForTheNumber() {
        var opened: String? = null
        setContent(entry(), onOpenDetails = { opened = it })
        composeRule.onNodeWithContentDescription("Call details").performClick()
        assertEquals("0401234567", opened)
    }

    @Test
    fun longPressOpensAMenuInsteadOfCopyingRightAway() {
        setContent(entry())
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Select").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Edit number and call").assertIsDisplayed()
        composeRule.onNodeWithText("Send message").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").assertIsDisplayed()
    }

    @Test
    fun theMenuCopiesTheNumber() {
        setContent(entry())
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Copy").performClick()
        val clipboard = ApplicationProvider.getApplicationContext<android.app.Application>()
            .getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("0401234567", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun theMenuStartsSelectionOnThatRow() {
        var started: Long? = null
        val entry = entry()
        setContent(entry, onStartSelection = { started = it })
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Select").performClick()
        assertEquals(entry.id, started)
    }

    @Test
    fun theMenuCanEditTheNumberBeforeCalling() {
        var edited: String? = null
        setContent(entry(), onEditBeforeCall = { edited = it })
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Edit number and call").performClick()
        assertEquals("0401234567", edited)
    }

    @Test
    fun inSelectionModeATapTogglesInsteadOfCalling() {
        var dialed: String? = null
        var toggled: Long? = null
        val entry = entry()
        setContent(
            entry,
            onCall = { dialed = it },
            selectionMode = true,
            onToggleSelect = { toggled = it },
        )
        composeRule.onNodeWithText("Matti Meikäläinen").performClick()
        assertEquals(entry.id, toggled)
        assertNull(dialed)
    }

    @Test
    fun groupedRowShowsTheCallCount() {
        setContent(entry(), count = 3)
        composeRule.onNodeWithText("Matti Meikäläinen (3)").assertExists()
    }

    @Test
    fun tappingAWhatsAppRowCallsBackOnWhatsApp() {
        var called: CallLogEntry? = null
        val entry = entry(source = CallSource.WHATSAPP)
        setContent(entry, onWhatsAppCall = { called = it })
        composeRule.onNodeWithText("Matti Meikäläinen").performClick()
        assertEquals(entry, called)
    }

    @Test
    fun tappingAPhoneRowCallsTheNumber() {
        var dialed: String? = null
        var whatsApp: CallLogEntry? = null
        setContent(
            entry(source = CallSource.PHONE),
            onCall = { dialed = it },
            onWhatsAppCall = { whatsApp = it },
        )
        composeRule.onNodeWithText("Matti Meikäläinen").performClick()
        assertEquals("0401234567", dialed)
        assertNull(whatsApp)
    }

    @Test
    fun aRowWithoutANumberDoesNotCallOrOpenDetails() {
        var dialed: String? = null
        var opened: String? = null
        setContent(
            entry(number = "", name = null),
            onCall = { dialed = it },
            onOpenDetails = { opened = it },
        )
        composeRule.onNodeWithText("Unknown", substring = true).performClick()
        composeRule.onNodeWithContentDescription("Call details").performClick()
        assertNull(dialed)
        assertNull(opened)
    }

    @Test
    fun theMenuSendsAMessageAndAsksToDeleteTheNumber() {
        var messaged: String? = null
        var deleted: String? = null
        setContent(entry(), onMessage = { messaged = it }, onRequestDelete = { deleted = it })
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Send message").performClick()
        assertEquals("0401234567", messaged)
        composeRule.onNodeWithText("Matti Meikäläinen").performTouchInput { longClick() }
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals("0401234567", deleted)
    }
}
