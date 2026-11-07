# AppLovin transient control — final device investigation

## Result

Current installed code44 is unchanged. The AppLovin ad was closed and ANA returned: DEVICE_PASS applies only to this one operator-assisted close. Routine remains BLOCKED, not ROUTINE_RESUMED/DONE. Natural scheduled execution is NOT_TESTED.

The mandatory first reveal was done manually after explicit ACK. Video captured an approximately five-second Google Play capsule; the user identified its LEFT triangle-and-bar glyph. Following explicit authorization for one extra retry, a fresh visible glyph and node bounds were matched before ONE icon input. Operation completed 979ms after the reveal gesture completed; this is not a measurement from the first visible frame. The actual next activity was Google Play TransparentMainActivityPrivate, not ANA. No install or download was requested. Two bounded Back actions dismissed the product sheet and store main screen. On return, a new endcard had a real visible top-right X, outside the creative endcard content subtree. A fresh image-node/parent-bounds and live X-glyph check preceded ONE ACTION_CLICK. ANA HomeActivity then returned. This proves icon → store → bounded return → X → ANA on this instance; it does NOT prove a semantic Skip → X path or authorize a general store-click rule.

Official ANA repeatedly showed third Boost /150pt, with two completed Boost marks; no advance to fourth/200pt was observed. Original CAR40 key/date and CH37ba687e-fb6e-4f71-bb5a-8cf9ddd7feb9/index4/start1793729755524 remain UNCONFIRMED. Production Continue was requested once. It correctly deferred login, never dispatched a new ad, then stopped at unconfirmed_challenge_requires_evidence_review. No fake or inferred reward was written.

## Durable results

Official production export current_state.json: 2026-11-04,19 joined,18 at5/5,111 confirmed challenge receipts,one current-day UNKNOWN. Daily local attempts0; official daily count NOT_TESTED. Today's login is REWARD_CONFIRMED. Historical three UNKNOWN remain the prior verified baseline; no historical rewrite/replay was requested. CAR40 entire row and all four original attempts compare exactly equal to the saved pre-trigger row using key+date and full tuple, not runId alone (one challenge runId spans several tasks). Settings and checkin config compare equal to the prior formal export. Current export ZIP CRC and SHA256 verified: d66f0ef66db396a4a6de22e5842417a6c13c2fa7b0b3e79f699fd8d0bad7ed0e. This is the app's formal logical export, not a raw private SQLite copy.

Original installed APK was freshly pulled and SHA256 verified: ccf0b824b93e055be30de78187b07cb3dbe53db0251b05674ee54f01c2fa7a17; original certificate ab39397d23fca7803fa7cd3e33125874d010874d5641e6ffd044a2a3b55f484c; package com.anapocket.auto/versionCode44. Functional source15808a006de5a06e93989c1fd8a251627e7c734d. No new candidate or APK installation. Baseline610 tests/81 suites had UNIT_PASS in preceding P0 evidence; no product change or new candidate JUnit claim in this phase.

## First failing decision and retained safeguards

Call chain: MainActivity.btnStartRoutine → AutoAccessibilityService.startDailyRoutine → DailyRoutine.challenges → ChallengeBoost.run pending attempt → reconcileBoundLateAdvance → ChallengeRecoveryPolicy.canResume → check failure. reconcileBoundLateAdvance accepts only ADVANCED. Current normal native panel is unchanged and no SDK/restart prompt remains, so canResume(observedAd=false) rejects replay. Exact task/date labels are absent from the active modal root; boundNativeIdentity requires those fields. The temporary shell captures establish the human-assisted continuity, but no existing production transaction imports that evidence as a Boost NO_GAIN review. A repeated count or150pt alone must not become an identity proof or a new advertising authorization.

isolateSubmittedUnknowns requires a clean native list and a unique official participating scan. The remaining panel explicitly warns that closing it loses Boost eligibility, so no button_no/Back/dismiss was used. Consequently independent daily/challenge navigation cannot currently be proved harmless to this remaining right. No reset/rescan, replacement ad, forced restart, permission bypass, cleanup or lock was used.

AdUiClose did not miss a semantic Skip node: current glyph has no Skip text and was experimentally a store clickthrough. The old native AppLovin close container was GONE/0x0; the successful later X was in a newly inserted WebView endcard. CanvasControlPolicy's rejection of generic SKIP remains appropriate. Do not hard-code a blank/left-corner tap from this single instance.

## Changes and minimal future repair

Production-source changes0. Temporary diagnostics repaired HARDWARE Bitmap pixel access via an exception-only readable copy, with pre-input self-check, bounded node/event capture, one icon budget, one X budget and released resources. No permanent controller/service/thread, normal-path screenshot allocation or product memory optimization is claimed. Temporary CurrentNodes emitted a legacy noInput=true summary even on its explicitly logged Tuffy Continue action; production-continue-request.jsonl contains the actual one accepted app-control request. Do not misread that summary as zero actions.

A minimum future repair should reuse the existing pending-attempt and NO_GAIN settlement transaction, requiring a persisted exact identity and verifiable continuous return binding before an endcard close. Reuse the existing15s no-loading/stability check and existing recovery budget; bind key/date/run/index/startedAt, commit idempotently, never change a confirmed receipt or dispatch the original attempt again. If the binding cannot be established, retain UNKNOWN and the panel. This design is not implemented or tested here because the only claimed transient Skip turned out to be a store CTA and current production proof is insufficient. No speculative universal wake/Skip policy or parallel state machine was added.

## Runtime, scheduling and delivery

Tuffy FGS stopped, accessibility Enabled+Bound. ApplicationExitInfo has no Tuffy/ANA-main exit or LOW_MEMORY since phone JST noon. This is not an all-history or future-stability claim. Existing23:58 protection and00:03 ANA alarms remain; exact alarm AppOp allow and battery exemption present. Switches/config remain unchanged. Independent readiness BLOCKED while unresolved panel is retained; scheduled natural execution NOT_TESTED.

Latest read-only handoff still shows ANA HomeActivity on originaltask1687, business dateNov4. Read-only clock audit found phone epoch about7216s behind capture host, while phone automatic time and automatic time zone are1. Neither clock was changed; no authoritative clock-source judgment is asserted. This discrepancy additionally prevents claiming reliable real-world schedule time.

The production retry queued a native Bot summary at phone15:30. Delivery itself is NOT_TESTED here. The detailed final notification was not sent: Telegram browser calls failed before message entry; Windows target-window approval timed out. No duplicate old result or test message was sent. No user action is falsely claimed completed.
