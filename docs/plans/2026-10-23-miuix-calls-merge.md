# Miuix integration, stage 1: merged Calls tab

## Scope of stage 1
- Add Miuix as a dependency and host `MiuixTheme` inside `ArkPhoneTheme`.
- Merge Home (recents + favorites) and Keypad into one **Calls** tab.
- Tabs are now Calls | Messages | Contacts. The `tab_keypad` string became
  `tab_calls` in all 11 locales; `tab_home` was removed.
- No call logic touched: `RecentsViewModel`, `DialpadViewModel`,
  `ContactsViewModel` and `CallRouter` are unchanged.

## Versions
| Piece | ARK-phone now | Miuix 0.9.4 built with |
|---|---|---|
| Kotlin | 2.3.21 | 2.4.20 |
| Compose | BOM 2026.06.01 | Compose Multiplatform 1.12.0 |
| AGP | 9.2.0 | 9.4.1 |
| Gradle | 9.4.1 | 9.7.1 |
| minSdk | 26 | 24 (`miuix-blur`: 33, not used) |

Stage 1 keeps the ARK toolchain and adds `miuix-ui:0.9.4` only. Kotlin reads
metadata from one minor version ahead, so 2.3.21 can consume 2.4.x libraries.
If the first build fails on Kotlin metadata or Compose versions, bump Kotlin to
2.4.20 together with KSP (2.3.12 is the version seen paired with it) and the
Compose BOM. If the AAR metadata check asks for a higher `compileSdk`, raise it.

## Calls tab behaviour
- Opens on call history. The keypad is folded into a round call button.
- Tap the button: the keypad unfolds over the list.
- Scroll the list (any vertical drag) or press back: the keypad folds again.
- A `tel:` / DIAL intent opens the tab with the keypad unfolded and the number
  filled in.
- Default state is `keypadExpanded = false` in `CallsScreen`; flip it to start
  unfolded.

## Next stages
1. Move the bottom bar and the Calls tab chrome (search, chips) to Miuix
   components.
2. Contacts, then Messages, then the secondary activities (settings, call
   detail, contact card).
3. Replace Material 3 once nothing references it; then drop the nested
   `MaterialTheme` in `ArkPhoneTheme`.

## Stage 2: Miuix look for the Calls tab
- `compileSdk` / `targetSdk` are 37 (Miuix 0.9.4 needs SDK 37).
- Added `miuix-preference` (dropdown) and `miuix-icons` next to `miuix-ui`.
- `MainScreen` now uses the Miuix `Scaffold`, `NavigationBar`, `Card`/`TextButton`
  (default-dialer banner). Overlay popups such as the dropdown need that Scaffold.
- Theme: API 31+ uses `MonetSystem` (system light/dark and wallpaper colors);
  API 26-30 uses Miuix's own dark palette (the app is dark there already).
- Calls tab: Miuix `SearchBar`/`InputField`, `Card` rows with `BasicComponent`,
  `OverlayDropdownPreference` titled "Recents" for the call filter, Miuix
  `FloatingActionButton`s. Favorites (no heading) scroll with the list.
- Keypad: flat keys without outlines, round call button in the middle, round
  hide button under the right-hand key at the spot the folded call button uses.
- Typing digits narrows the list: matching calls (name by T9, number in any
  dial form, or number of a matching contact) plus matching contacts with no
  calls. Logic: `DialpadMatcher.matchList`, exposed as `DialpadUiState.listMatch`.
  With the keypad folded and digits still typed, a banner row shows the number
  and clears the filter.
- Unchanged on purpose: the Messages and Contacts tabs, `DialpadGrid` (used by
  the in-call DTMF pad), `SearchRow`, `ContactAvatar`.
- Old Material `DialpadScreen`/`DialpadContent` and `RecentsFilterChips` removed.

## Stage 2b: matching real HyperOS / Miuix (from device screenshots)
- Large collapsing title ("Calls") in the host `Scaffold` via Miuix `TopAppBar` +
  `MiuixScrollBehavior`; the settings button moved into its actions. The Calls
  list is tied to it through `nestedScroll`.
- Rows are flat (no cards): person tile, name (red when missed), date and
  details line, round "›" button. Tap = call back, "›" = call details,
  long-press = copy the number. This changes the old tap behavior.
- Search is the first list item, so it scrolls with the title. Focusing it makes
  the search active: top and bottom bars and the keypad button hide, "Cancel"
  appears, and the list shows only matches under "Found: N".
- "Recents" is a flat `OverlayDropdownPreference` (no card).
- Round green call button with the dialpad dots icon; the keypad's call button
  uses the same green.
- Not changed: Messages and Contacts tabs.

## Stage 3: keypad, long-press menu, call details, faster matching
- Keypad panel is shorter (smaller keys and gaps, 64 dp call button) and the
  number field only appears once a digit is typed. Pasting works from the field,
  or by long-pressing `#` while the field is hidden.
- Long-press on a call row opens a popup menu (Select, Copy number, Edit number
  before call). "Select" enters selection mode: bars hide, a top bar shows the
  count with Select all / Cancel, a bottom bar copies the numbers or deletes the
  history of the selected numbers (asks for WRITE_CALL_LOG when needed).
- Call details are Miuix: header with round actions, a statistics card right
  under them, then the history. Only the newest 3 calls are loaded; "Show more"
  adds 10 at a time. `CallLogRepository.historyFor` reads one slim cursor pass
  (counters and ids, last-4-digit prefilter before `PhoneNumberUtils.compare`)
  and `entriesByIds` loads rows for a page. The old code loaded and compared the
  whole log on the main flow, which froze the app on long histories.
- Number matching: `DialpadMatcher.buildIndex` does the libphonenumber and T9
  work once per change of contacts or call log (numbers are memoized across
  rebuilds); `DialpadMatcher.search` only compares strings per keystroke.
- Not done: the long-press menu follows common HyperOS practice, not a
  screenshot (none was attached).
