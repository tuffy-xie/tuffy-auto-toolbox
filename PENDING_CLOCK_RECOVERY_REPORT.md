# H14 pending receipt / isolation / clock — final handoff

**Business BLOCKED. CODE_IMPLEMENTED and UNIT_PASS apply to an offline candidate, not a recovered Routine.**

## Current device and durable data

Current phone is the original code44, functional source15808a006de5a06e93989c1fd8a251627e7c734d; actual installed APK SHA256 ccf0b824b93e055be30de78187b07cb3dbe53db0251b05674ee54f01c2fa7a17 rechecked on-device. No APK installed this turn. ANA HomeActivity/task1687 remains with the protected third-Boost150pt modal. No active fullscreen SDK, no FGS; A11y Enabled+Bound. No Tuffy/ANA-main exits or LOW_MEMORY since phone20:00 in captured exit records.

Fresh formal production export:19 joined,18 at5/5,111 confirmed challenge receipts,one current-day UNKNOWN; daily local0, official NOT_TESTED; login REWARD_CONFIRMED. Entire today challenge array, daily attempts, login, Routine and settings equal the previous formal export. CRC passed; SHA256 64b6e8569820aa8a9fac7e67dd11679f1a65d30d54cc09de2dccd50f46bb50ff. Historical three UNKNOWN remain the prior verified baseline, not a new direct read of historical private SQLite. No replay, direct DB edit, fabricated GAIN/NO_GAIN, new ad or new receipt. No new Routine was started this turn.

Original CAR40 attempt stays UNKNOWN: date2026-11-04/key CAR|車、電車、モノレールで40km移動しよう！/run CH37ba687e-fb6e-4f71-bb5a-8cf9ddd7feb9/index4/start1793729755524/DISPATCHED. Before2/5 and150pt; expected next3/5 and200pt. The visible modal still offers third Boost at150pt. This is not a wallet-credit claim.

## Why it still blocks, and why150pt is insufficient

DailyRoutine.startup defers to the existing challenge → ChallengeBoost.run sees the one pending attempt → isolateSubmittedUnknowns rejects the active Boost modal → reconcileRecordedInitialReceipt is inapplicable to a Boost → reconcileBoundLateAdvance requires ADVANCED and returns false → no official resume prompt or load-failure result exists → ChallengeRecoveryPolicy.canResume(observedAd=false) rejects replay → DailyRoutine.challenges returns BLOCKED → RoutineSequence stops before ADS. The first current rejecting decision is the pending-ad resume guard, not a newly broken ad closer.

NO_GAIN already has a production path: awaitResumedBoost after a bounded official resume/recovery, exact native identity, stable ordinal/reward ladder, no SDK/loading, and at least15s observation. Current third-Boost150pt is not an authoritative fresh failure result. Current Android Fragment arguments identify mission3623/participation1774354887, but contain cached ADDITIONAL_BOOST_COUNT=2 and reward data; the original ledger did not persist a directly comparable server-ID binding. Prior dispatch and continuous-window evidence identify the operator-assisted return much better than a number alone, but do not prove the server's final reward outcome. Do not change UNKNOWN to FAILED merely because a cached panel did not advance.

Only watching or abandoning is exposed in the current modal. Its explicit warning says closing loses Boost eligibility. No verified non-destructive return-to-list route was found. Noやめる/back/dismiss, new Boost, forced restart, deep-link experiment or account operation was used to bypass it. Candidate installation is deferred: it does not supply missing official evidence or justify risking this retained qualification. The remaining same-ANA business depends on this irreversible choice, so current CAR/MOV_ANY/daily cannot safely resume under existing authorization.

## Existing independent-task mechanism and smallest changes

Existing submittedUnknownIsolation metadata already represents ISOLATED_UNKNOWN without changing the original UNCONFIRMED ad. It requires a stable clean native page, no daily pending attempt and one exact official participating match. PendingInitialJoinStore delegates to that metadata; onlyIsolatedRemaining plus completed/partial scans lets DailyRoutine continue independent daily ads. The original unknown remains visible and overall Routine remains pending review. Records are SQL-scoped by date; yesterday's UNKNOWN alone is not queried as today's pending work. A still-open global modal/SDK can correctly block a new day; changing the date is not authority to dismiss it.

Source commit 9034abf124e059fffb5dc07c5544dd7a268ddead on fix/h14-pending-isolation-clock-20261104; H12 main and all prior43/44/892 refs preserved. Three existing production files only:

- SubmittedUnknownIsolationPolicy.cleanNativePage and save/commitOnce: one shared native navigation classifier, reuse HomeNavigationPolicy for ordinary detail pages, explicitly reject Boost modal/SDK; metadata writes re-read the latest bound attempt, are idempotent, and use existing ReliableLedger.updateChallenge/FULL persistence. Original ads remain untouched.
- ChallengeTaskController.cleanIsolationPage/isolateSubmittedUnknowns/run: recheck daily pending and current page at commit instead of hard-coded false; a saved isolation no longer authorizes navigation through a subsequently opened Boost modal. Safe ordinary detail navigation remains available.
- NativeChallengeReceiptPolicy.samePendingBoost and ChallengeTaskController.commitReviewedBoost: existing late GAIN and official resumed GAIN/NO_GAIN re-read date/key/run/index/start/state/reward ladder before mutation. Successful save preserves the original caller ad reference, including cold-recovery follow-up, and clears stale row error only after commit. Existing outcome/identity authorization and recovery budgets are unchanged; no new permission to infer NO_GAIN.

No new controller, state enum, service, resident thread, wake lock, normal-path screenshot/OCR/Bitmap cache, schema migration or version number. No Google/Pangle/AppLovin click policy or Home/stall-restart budget was changed. Normal-run CPU/PSS/Bitmap performance is NOT_TESTED; source contains no added normal-ad screenshot calls. This candidate hardens recovery and isolation boundaries; it does not resolve the current absent official outcome.

## Regression and signed candidate

Final complete run617 tests/82 suites, failures/errors/skips0: UNIT_PASS. Seven focused tests include actual CAR40 modal nodes, safe native detail navigation, stale/multiple/changed identities, isolation once across file reload, interrupted durable callback, old-day proof rejection, and cached NO_GAIN remaining unconfirmed. Existing Google/Pangle/slow95s/Home/max5/5/daily25/exit/scheduling suites also ran. File fsync/callback failure tests are not Android SQLite process-death DEVICE_PASS. No real ad was killed for testing.

Final release build succeeded; candidate NOT_INSTALLED / DEVICE NOT_TESTED / NATURAL_RUN NOT_TESTED. APK Tuffy_code44_pending_isolation_reviewed_signed.apk, SHA256 967a22b44406ce9b188f17c314831fb1b822f7e70588ad9262ac5d0b7b200a69; package com.anapocket.auto/versionCode44, original certificate ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c. Earlier intermediate artifact is retained separately and is not the final candidate. No main merge. Rollback source15808a00 and installed original APK remain intact.

## Clock finding and actual alarm verification

NICT stratum1 and Microsoft NTP agree with Google/Microsoft HTTPS Date responses. Final bracketed phone sample agrees with NICT within its roughly2s conservative sampling bound. Windows is fast by 7214.34s (about2h14s). This is not an Android timezone-display error. Android Asia/Tokyo, automatic time and zone are on. No phone time, business date, historic timestamps or receipt scopes were changed. Official NICT source: https://jjy.nict.go.jp/tsp/PubNtp/index.html . NICT HTTPS certificate error was not bypassed; independent NTP and other TLS sources were used.

Windows Time is stopped with Type=NoSync. Formal Start-Service W32Time attempt failed with service-management access denied; no ACL/UAC/security bypass or manual Set-Date was used. Windows clock repair remains BLOCKED pending administrator action. In an administrator PowerShell, after reviewing effects on host logs/jobs, use formal synchronization:

```powershell
Start-Service W32Time
w32tm /config /manualpeerlist:"time.windows.com,0x8 ntp.nict.jp,0x8" /syncfromflags:manual /update
w32tm /resync /rediscover
w32tm /query /status
```

Phone alarms remain23:58 protection/next00:03 ANA; UTC targets1793804280000/1793804580000 match phone JST and external time. Exact alarm AppOp/battery exemption from current verified evidence preserved, switches unchanged. One existing90s dispatch-only native alarm was scheduled once and actually delivered20:34:29.802 phone JST; scheduling log20:32:59.783 gives90.019s logged interval. It started no ad/challenge and sent only a local notification. Exact alarm target milliseconds were not captured before delivery, so no exact jitter claim. DEVICE_PASS applies to this dispatch-only selftest, not candidate business or next-day natural execution. BootReceiver already reschedules on boot/package/time/timezone changes; no Android scheduling code was changed because the measured fault is on Windows.

## Notifications and hard blockers

The native Telegram settings page reports 推送成功 ·2026-11-04 15:30:48 for the previous real blocked Routine summary. That verifies the old native Bot channel, not a new final message. No test push or stale Routine restart was used to fake a new result. This turn's detailed final notification was not submitted: the existing Telegram tab was on another bot, and creating the authorized result page failed before input. No message was sent to the wrong recipient and no web-account message was presented as phone-Bot output.

Remaining blockers: protected CAR40 qualification vs leaving ANA; UNKNOWN without authoritative settlement/fresh binding; Windows administrative synchronization; no new final Bot push; candidate device/business acceptance and next natural trigger NOT_TESTED. Native schedules remain on. No model-side automatic continuation was configured or promised.
