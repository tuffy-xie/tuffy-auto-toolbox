# Repository and release workflow

Use one stable main branch. Work on fix/h13-* for bug fixes, feature/* for explicitly requested features, and docs/* for independent documentation or repository configuration. Preserve archive/* branches as historical baselines. A permanent develop branch is not required.

Workflow: new branch -> implementation -> JVM tests -> necessary, permitted device verification -> PR -> main -> version tag -> NAS release archive. Unverified fixes must not merge automatically. No force push or history replacement.

The Android Build workflow runs on PRs targeting main, pushes to main/master, and manual dispatch. It uses JDK 17, Android SDK 34 and Gradle 8.9. JVM tasks and Debug APK tasks use --no-build-cache --rerun-tasks. Dependency caches may be reused, but are not proof that tests executed. JUnit XML, HTML reports and an unsigned-by-production-key Debug APK are retained as CI artifacts. The private production signer is never required or uploaded.

Before merging, review the actual diff and exact head SHA, all applicable CI checks and conflicts. Configure required status checks only after they successfully report. Server-enforced protection for a private personal repository depends on the GitHub plan and granted administration permission; never claim documentation itself enforces protection or make this repository public to enable it.

Release tags must point at the verified merged main commit. Record versionName, versionCode, source commit, frozen production APK SHA256, actual test results and known limitations in tag/release notes. Never replace an existing tag. Keep APKs, source ZIPs, private diagnostics and original evidence in the separate NAS archive instead of adding them to Git. Existing historical R44 release files remain part of preserved history. Signing keys, passwords, encrypted recovery envelopes and local configuration must stay outside the Git worktree.

H12 is 22.0.12-R47 / code34. Its archived production APK SHA256 is bc37d89472f2aa154e394401c769ce400a8ec017175d46647ec000e158da663a. The prior independent H checkout executed 504 JVM tests in 72 suites with zero failures/errors/skips. H12's 10 targeted Android isolation checks are historical; the original 110 were not rerun. This repository-management change does not operate the phone or repeat device tests.

The 2026-11-01 device evidence confirmed 24 selected challenge joins and their 5/5 Boosts, plus official daily 25/25. The fresh official daily recheck was at 12:38 JST; the 18:02 diagnostic export retained the ledger, not a new SDK-advertisement recovery test. Signup remained UNCONFIRMED, as did the Rakuten bonus and steak darts. Manual app recovery and an ANR acknowledgement occurred. Continuous low-memory recovery and the automatic AppInfo exit route are not fully accepted. Do not describe this as a fully unattended, all-rewards-confirmed run.
