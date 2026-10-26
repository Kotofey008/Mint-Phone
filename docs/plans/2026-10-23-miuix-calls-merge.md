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
