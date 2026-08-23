# WinSysMonitor — AI Quick Start

> **Give this file to any AI assistant to instantly understand the project**

---

## Required Reading (Private Repo Only)

**Read these two files completely before making any changes:**

1. **`REPOSITORY_GUIDE.md`** — Architecture, CI/CD, update system, AI instructions, roadmap, troubleshooting
2. **`RECENT_CHANGES.md`** — Complete history with code-level details, commits, algorithms, flow diagrams, secrets, keystore

---

## One-Paragraph Context

WinSysMonitor is a LAN monitoring system with a **private source repo** and **public distribution repo**. The Android app has a **mandatory update gate** that checks the public repo on startup, blocks the UI until verified, and forces updates via DownloadManager + system installer. CI/CD runs on every push to `master` in the private repo, builds a signed APK, and publishes to **both repos**. Version auto-increments via `GITHUB_RUN_NUMBER`. Keystore is fixed and must never change.

---

## Repo Structure

| Repo | URL | Purpose |
|------|-----|---------|
| **Private** | `4sudosu/WinSysMonitorPrivate` | Source code, CI/CD, secrets, docs |
| **Public** | `4sudosu/WinSysMonitor` | Releases only (APK assets), update checks |

**Flow:** Push to Private → GitHub Actions builds → Publishes to BOTH repos → Android app checks Public

---

## Critical Constants

```kotlin
// LauncherActivity.kt
private val GITHUB_REPO = "4sudosu/WinSysMonitor"  // PUBLIC repo for update checks
```

```bash
# Version scheme (auto-generated)
versionName="1.1.${GITHUB_RUN_NUMBER}"  # e.g., 1.1.8
versionCode="${GITHUB_RUN_NUMBER}"       # e.g., 8
```

---

## Mandatory Rules for AI

1. **Read both docs first** — `REPOSITORY_GUIDE.md` + `RECENT_CHANGES.md`
2. **Follow File Ownership Map** (in REPOSITORY_GUIDE.md) for which files to touch
3. **After ANY change** — Update `RECENT_CHANGES.md` with:
   - Date, version, commit hashes
   - Component changes (✅/🔄/➕/➖)
   - Files table (File | Change)
4. **Commit both** — Code changes + `RECENT_CHANGES.md` together
5. **Push to master** — Triggers CI/CD automatically
6. **Never change keystore** — Breaks all user updates

---

## Standard Workflow

```bash
# 1. Make changes
# 2. Test locally
cd Android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Update RECENT_CHANGES.md (see template in file)

# 4. Commit & push
git add -A
git commit -m "Descriptive message"
git push origin master

# 5. Verify
gh run watch --repo 4sudosu/WinSysMonitorPrivate
gh release list --repo 4sudosu/WinSysMonitor --limit 1
```

---

## Key Files to Know

| Area | Files |
|------|-------|
| CI/CD | `.github/workflows/android-release.yml` |
| Update logic | `UpdateChecker.kt`, `LauncherActivity.kt`, `UpdateActivity.kt` |
| Version/Signing | `Android/app/build.gradle` |
| Permissions | `AndroidManifest.xml` |
| UI strings | `res/values/strings.xml` |
| Docs | `REPOSITORY_GUIDE.md`, `RECENT_CHANGES.md`, `START.md` |

---

## Secrets (Private Repo → Settings → Secrets → Actions)

| Secret | Purpose |
|--------|---------|
| `ANDROID_KEYSTORE_BASE64` | Base64 release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | `winsysmonitor123` |
| `ANDROID_KEY_ALIAS` | `winsysmonitor` |
| `ANDROID_KEY_PASSWORD` | `winsysmonitor123` |
| `RELEASE_TOKEN` | GitHub token (repo scope) |

---

## Troubleshooting Quick Hits

| Issue | Fix |
|-------|-----|
| `libnode.so` missing | `gh release create libnode-binaries ...` on public repo |
| Keystore not found | Re-set `ANDROID_KEYSTORE_BASE64` secret |
| App stuck "Checking..." | Network/API limit — add token to UpdateChecker |
| "Package already exists" | `adb uninstall com.wsmonitor.app` (debug vs release signature) |
| Release not created | `RELEASE_TOKEN` expired — regenerate with `repo` scope |

---

## Desired Improvements (from REPOSITORY_GUIDE.md)

**High:** `workflow_dispatch`, `setup-java@v5`, GitHub token for UpdateChecker
**Medium:** Play Store, delta updates, beta/stable channels
**Low:** Signed agent releases, auto version bump, Slack notifications

---

## Contact

- **Owner:** 4sudo.su (@4sudosu)
- **Telegram:** @verifiedharyanvi
- **Email:** 4sudo.su@gmail.com

---

## Current State (2026-08-23)

- ✅ v1.1.15 released on both repos (synced to actual GitHub releases)
- ✅ Dual-repo publishing working
- ✅ libnode CI dependency resolved
- ✅ Mandatory update gate live
- ✅ Documentation complete (AI-ready)

---

**Start here:** Read `REPOSITORY_GUIDE.md` → `RECENT_CHANGES.md` → then implement changes.