# Hidden-number caller name and the callee's carrier-fallback row — review request (2026-10-19)

Starting point: `main` = `feature/voip-spike` = `3ea874d` (v1.28). The fix is one commit on
`fix/hidden-caller-name-and-callee-rows`: `e835c6a`. Nothing is pushed or released; the version
is still 1.28 / versionCode 16 and will be bumped to 1.28.1 / 17 only after this review.

## Field evidence (Pixel 9a, Android 17, fi-FI, Telia; 2026-10-19)

1. A call from a restricted number was announced as "Anonymous soittaa" (an earlier one as
   "Unknown soittaa"). Radio log of the test call:
   `ImsPhoneConnection: updateAddressDisplay: address =  name = [****] nump = 2 namep = 1`
   — the number is restricted, but the network attaches a display name whose presentation is
   allowed. Telecom forwards it as `Call.Details.callerDisplayName`; `TelecomCallHandle.displayName`
   used it, and `CallerAnnouncer` spoke `announce_caller` ("%1$s soittaa") with it. The installed
   APK's `announce_unknown_caller` is translated in all 11 languages; the English word came from the
   carrier, not from resources. The stock dialer shows "Private number" whenever the number
   presentation is not allowed, regardless of any name.
2. Call logs of three phones were dumped. The callee's side of a carrier fallback can leave two rows
   for one call (ARK missed/0 s row + carrier row): `VoipCallCoordinator.finish()` wrote every incoming
   row at once, while only the caller's `fallBack()` suppresses its own row. The last such pairs in
   the data are from 2026-08-09 and 2026-08-20 (before the fallback redesign); the code path was
   still open.

## Fixes

- `telecom/CallHandle.kt`: `displayNameFor(contactName, carrierName, numberPresentation)` returns
  null unless the number presentation is `PRESENTATION_ALLOWED`; otherwise the saved contact, then
  the carrier name, blanks ignored. `TelecomCallHandle.displayName` now goes through it with
  `Call.Details.handlePresentation`. Every ringing surface (announcement, notification, in-call
  screen) reads the same `CallInfo.displayName`, so all of them fall back to the unknown-caller texts.
- `voip/telecom/VoipCallCoordinator.kt`: an incoming ARK call that ends without ever connecting
  (`connectTimeMillis == 0`) for any reason but the callee's own `local-reject` no longer writes its
  row at once. `holdForCarrierFallback()` waits up to `CARRIER_FALLBACK_GRACE_MS` (20 s) on a new
  `carrierCallRinging: Flow<String?>`; if a carrier call from the same caller (`sameCaller`) starts
  ringing, the ARK row and the missed-call notification are dropped (log line
  "ARK row dropped: carrier call from the same caller"); otherwise the record computed at end time is
  written unchanged after the window. The wait runs in the coordinator's scope, so the next call is not
  blocked; process death inside the window loses the row (documented, accepted).
- `voip/di/VoipModule.kt`: the flow is derived from `CallController.calls` — calls with
  `viaArkCall == false` in `CallStatus.RINGING`, their `number` (null when hidden).

## Verification

Sixteen tests added, each seen failing before its implementation (compile errors on the missing
helper, constant and parameter; the behaviour tests then failed on the immediate row):

- `CallerDisplayNameTest` (5): contact wins, carrier name without contact, restricted keeps no name,
  unknown/payphone keep no name, blanks are no name.
- `VoipCallCoordinatorTest` (+8): row dropped when the same caller rings over the carrier; logged
  missed after the window without one; a different number or a hidden carrier call does not cancel
  the row; an answered call whose media failed is held too; local reject and a connected call are
  logged at once; the wait does not block the next ARK call. Three existing log tests now advance
  past the window.
- `LocalesTest` (+3): the five unknown-caller strings and the named announcement are non-blank and
  translated in all 11 languages; the Finnish texts are exactly "Tuntematon soittaja soittaa" and
  "Jarsi soittaa".

| Check | Result |
| --- | --- |
| `:app:testDebugUnitTest` | 1 036 tests (was 1 020); 0 failures, 0 errors |
| `:app:lintDebug` | 0 errors, 0 warnings, 1 pre-existing hint |
| `:app:compileReleaseKotlin`, `:app:compileBetaKotlin` | passed |
| `:app:assembleRelease`, `:app:assembleBeta`, `:app:assembleDebug` | passed |

Build artefacts (versionCode 16, same signing key as v1.28):
`app/build/outputs/apk/release/ARK-phone-1.28-release.apk` SHA-256 `070e1e415f1465513699350e8d621fd39d80b8bda2c8110694018503d8ac899d`;
`app/build/outputs/apk/beta/ARK-phone-1.28-beta.apk` SHA-256 `6262837c2aa80ba0bfff00b7b7656deb5022f51dd3b497852445a876afccb0c7`.

## Questions for the reviewer

1. Is dropping the carrier name for every non-allowed presentation right, or should
   `PRESENTATION_UNKNOWN` keep a contact match? (The contact lookup needs a number, which such calls
   do not carry, so in practice both are null today.)
2. The grace window matches on the number of the carrier call; a caller whose carrier number differs
   from the number stored on the ARK link still produces two rows. Acceptable, or should the link's
   other numbers be consulted?
3. Any race between `finish()` clearing `active` and the deferred write, or with a backup restore
   (`holdForRestore` checks `active`, not the pending write)?
4. The deferred missed-call notification: `missedCalls(record)` now fires up to 20 s after the
   ring ends. Any surface that assumed it fired synchronously?

## Round 1 findings (2026-10-19) and fixes

| # | Finding | Fix |
| --- | --- | --- |
| P2 | A row held for the grace window landed unread after the user had opened the call log, with a notification over the list in view. | `MissedCallNotifier` tracks when the main screen is in front (`onCallLogShown()` from `MainActivity.onResume`, `onCallLogHidden()` from a new `onPause`); a missed call that lands meanwhile is acknowledged through `onCallLogSeen()` instead of notified. Two tests in `MissedCallNotifierTest`. |
| P3 | `first()` on the constructor's default `emptyFlow()` threw past `withTimeoutOrNull`, losing the row and the notification. | `firstOrNull()`: a completed feed counts as no carrier call. Test `aCompletingCarrierFeedStillLogsTheMissedCall`. |

The in-front rule applies to every missed call, carrier calls included: a call that is missed while the
app's own screen is resumed is acknowledged as it lands, where before a notification was posted and
then cancelled by the next resume. Please say if that widening is unwanted.

After the fixes: 1 039 tests, 0 failures; lint 0 errors, 0 warnings, 1 pre-existing hint; release,
beta and debug APKs rebuilt. Release SHA-256 `a2fa13bbe10315faa713b7d2187ec9b6ead989464e65572011ea999ef0f4c251`, beta `5cccf3e233d07da1e15f53cccdf6466993889dfcf29b337b388e5c0d4a51b0a3`.

## Round 2 request

Review the two fix commits after `e835c6a` for the questions above, the widened in-front rule, and
anything the first round did not cover.

## Device test plan (after the review)

- Pixel 9a: call it from the Fairphone 6 with the `#31#` prefix; the announcement must say
  "Tuntematon soittaja soittaa" and the screen "Tuntematon numero".
- Pixel 9a as callee: ARK call from the Fairphone 6 left unanswered until the carrier takes over
  (about 30 s); the 9a's log must show one carrier row and no ARK missed notification.
- Fairphone 6 as callee: the same two checks the other way round.
