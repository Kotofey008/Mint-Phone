package org.jarsi.arkphone.ui.calls

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.jarsi.arkphone.data.model.CallLogEntry
import org.jarsi.arkphone.data.model.CallType
import org.jarsi.arkphone.data.model.Contact
import org.jarsi.arkphone.ui.MiuixTestHost
import org.jarsi.arkphone.ui.dialpad.DialpadListMatch
import org.jarsi.arkphone.ui.dialpad.DialpadUiState
import org.jarsi.arkphone.ui.recents.GroupedCallLogEntry
import org.jarsi.arkphone.ui.recents.RecentsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CallsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun contact(id: Long, name: String) = Contact(
        id = id,
        displayName = name,
        phoneNumber = "040123456$id",
        photoUri = null,
        starred = true,
    )

    @Test
    fun searchFieldAndTheRecentsDropdownSitOnTop() {
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = emptyList(),
                recentsUiState = RecentsUiState(loading = false),
                onCall = {},
                onRequestPermissions = {},
            )
            }
        }
        composeRule.onNodeWithText("Search calls").assertIsDisplayed()
        composeRule.onNodeWithText("Recents").assertIsDisplayed()
    }

    @Test
    fun favoritesAreShownAndCallable() {
        val called = mutableListOf<String>()
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = listOf(contact(1, "Alice"), contact(2, "Bob")),
                recentsUiState = RecentsUiState(loading = false),
                onCall = { called.add(it) },
                onRequestPermissions = {},
            )
            }
        }
        // The favorites row has no heading of its own.
        composeRule.onNodeWithText("Favorites").assertDoesNotExist()
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Bob").performClick()
        assertEquals(listOf("0401234562"), called)
    }

    @Test
    fun noFavoritesLeavesOnlyTheEmptyMessage() {
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = emptyList(),
                recentsUiState = RecentsUiState(loading = false),
                onCall = {},
                onRequestPermissions = {},
            )
            }
        }
        composeRule.onNodeWithText("Favorites").assertDoesNotExist()
        composeRule.onNodeWithText("No calls yet").assertIsDisplayed()
    }

    @Test
    fun tappingARecentsRowCallsAndTheChevronOpensDetails() {
        val called = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val entry = CallLogEntry(
            id = 1,
            number = "0401234567",
            displayName = "Alice",
            type = CallType.INCOMING,
            timestampMillis = 1_720_000_000_000,
            durationSeconds = 60,
        )
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = emptyList(),
                recentsUiState = RecentsUiState(
                    loading = false,
                    entries = listOf(GroupedCallLogEntry(entry)),
                ),
                onCall = { called.add(it) },
                onRequestPermissions = {},
                onOpenDetails = { opened.add(it) },
            )
            }
        }
        composeRule.onNodeWithText("Alice").performClick()
        assertEquals(listOf("0401234567"), called)
        assertEquals(emptyList<String>(), opened)
        composeRule.onNodeWithContentDescription("Call details").performClick()
        assertEquals(listOf("0401234567"), opened)
    }

    @Test
    fun keypadIsFoldedByDefaultAndTheRoundButtonUnfoldsIt() {
        var expanded: Boolean? = null
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = emptyList(),
                recentsUiState = RecentsUiState(loading = false),
                onCall = {},
                onRequestPermissions = {},
                keypadExpanded = false,
                onKeypadExpandedChange = { expanded = it },
            )
            }
        }
        composeRule.onNodeWithText("5").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Show keypad").performClick()
        assertEquals(true, expanded)
    }

    @Test
    fun unfoldedKeypadShowsKeysDialsTheTypedNumberAndFolds() {
        val pressed = mutableListOf<Char>()
        var dialed = 0
        var expanded: Boolean? = null
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = emptyList(),
                recentsUiState = RecentsUiState(loading = false),
                onCall = {},
                onRequestPermissions = {},
                dialpadUiState = DialpadUiState(number = "040"),
                keypadExpanded = true,
                onKeypadExpandedChange = { expanded = it },
                onKey = { pressed.add(it) },
                onDial = { dialed++ },
            )
            }
        }
        composeRule.onNodeWithContentDescription("Show keypad").assertDoesNotExist()
        composeRule.onNodeWithText("5").performClick()
        assertEquals(listOf('5'), pressed)
        composeRule.onNodeWithContentDescription("Call").performClick()
        assertEquals(1, dialed)
        composeRule.onNodeWithContentDescription("Hide keypad").performClick()
        assertEquals(false, expanded)
    }

    @Test
    fun typedDigitsNarrowTheListToMatchingCallsAndCalllessContacts() {
        fun call(id: Long, name: String) = CallLogEntry(
            id = id,
            number = "040000000$id",
            displayName = name,
            type = CallType.INCOMING,
            timestampMillis = 1_720_000_000_000,
            durationSeconds = 5,
        )
        composeRule.setContent {
            MiuixTestHost {
            CallsContent(
                favorites = listOf(contact(9, "Fave")),
                recentsUiState = RecentsUiState(
                    loading = false,
                    entries = listOf(
                        GroupedCallLogEntry(call(1, "Alice")),
                        GroupedCallLogEntry(call(2, "Bob")),
                    ),
                ),
                onCall = {},
                onRequestPermissions = {},
                dialpadUiState = DialpadUiState(
                    number = "0400000001",
                    listMatch = DialpadListMatch(
                        callIds = setOf(1L),
                        contactsWithoutCalls = listOf(contact(5, "Carol")),
                    ),
                ),
                keypadExpanded = false,
            )
            }
        }
        composeRule.onNodeWithText("Alice").assertIsDisplayed()
        composeRule.onNodeWithText("Bob").assertDoesNotExist()
        composeRule.onNodeWithText("Carol").assertIsDisplayed()
        // Favorites step aside while a number is looked up.
        composeRule.onNodeWithText("Fave").assertDoesNotExist()
    }

    @Test
    fun theNumberFieldStaysHiddenUntilADigitIsTyped() {
        composeRule.setContent {
            MiuixTestHost {
                CallsContent(
                    favorites = emptyList(),
                    recentsUiState = RecentsUiState(loading = false),
                    onCall = {},
                    onRequestPermissions = {},
                    dialpadUiState = DialpadUiState(number = ""),
                    keypadExpanded = true,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Delete digit").assertDoesNotExist()
        composeRule.onNodeWithText("5").assertIsDisplayed()
    }

    @Test
    fun selectionModeShowsTheCountAndItsActions() {
        var copied = 0
        var deleted = 0
        composeRule.setContent {
            MiuixTestHost {
                CallsContent(
                    favorites = emptyList(),
                    recentsUiState = RecentsUiState(loading = false),
                    onCall = {},
                    onRequestPermissions = {},
                    selectionActive = true,
                    selectedIds = setOf(1L, 2L),
                    onCopySelected = { copied++ },
                    onDeleteSelected = { deleted++ },
                )
            }
        }
        composeRule.onNodeWithText("Selected: 2").assertIsDisplayed()
        composeRule.onNodeWithText("Copy numbers").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals(1, copied)
        assertEquals(1, deleted)
        // The keypad button steps aside while picking calls.
        composeRule.onNodeWithContentDescription("Show keypad").assertDoesNotExist()
    }
}
