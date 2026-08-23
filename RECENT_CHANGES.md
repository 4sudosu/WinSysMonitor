# Recent Changes Log — Complete History

> **Location:** Private repo only (`4sudosu/WinSysMonitorPrivate`)
> **Purpose:** Complete change history so any AI can reconstruct the system state
> **Read with:** `REPOSITORY_GUIDE.md` for architecture context

---

## Legend
- ✅ = Implemented/Working
- ⚠️ = Known issue/limitation
- 🔄 = Changed/Modified
- ➕ = Added
- ➖ = Removed

---

## [2026-08-23] Documentation Overhaul — AI-Ready Guides

### Git Commits
```
c4b959f Add AI quick start guide (START.md)
22d5699 Update repository guide with AI instructions, roadmap, troubleshooting
d3cc6e8 Expand recent changes log with complete history for AI reconstruction
ff26f73 Add recent changes log
6b17126 Add repository architecture guide
```

### Changes

#### ➕ REPOSITORY_GUIDE.md — Complete Rewrite
- **AI Instructions section** — How to read, implement, test, document
- **File Ownership Map** — Which files to touch for which changes
- **Desired Changes/Roadmap** — Prioritized backlog (High/Medium/Low)
- **Troubleshooting Quick Reference** — Symptom → Cause → Fix table
- **Version History Summary** — Clean table
- **Contact/Escalation** info

#### ➕ RECENT_CHANGES.md — Expanded
- Complete commit history with hashes
- CI/CD pipeline step-by-step
- Android update system code-level detail
- Version comparison algorithm
- Update gate flow with callbacks
- Download + install flow
- Status messages with repo name
- Public repo assets table
- Secrets configuration (with actual values)
- Keystore generation command
- Versioning scheme
- Complete ASCII flow diagram
- Known limitations table
- AI Reconstruction Guide

#### 📋 Process Established
> **Rule:** After ANY change → Update RECENT_CHANGES.md
> - Add entry with date, version, commits, changes, files table
> - Commit both code + RECENT_CHANGES.md together

---

## [2026-08-23] Release v1.1.8 — Dual-Repo Publishing & Libnode CI Fix

### Git Commits (chronological)
```
22d5699 Update repository guide with AI instructions, roadmap, troubleshooting
d3cc6e8 Expand recent changes log with complete history for AI reconstruction
ff26f73 Add recent changes log
6b17126 Add repository architecture guide
0eec868 Also publish release to private repo
7e9eb7a Ignore download exit codes
9890d6f Add clobber to libnode downloads
9145186 Add libnode include download
620e9ea Fix libnode download flag
3f5ae14 Add libnode download step for CI
c1bceda Fix gradlew execute permission
48864cd Bump Android app version to 1.1.1
3f2beaf Add mandatory Android update releases
```

### CI/CD Pipeline (`.github/workflows/android-release.yml`)

#### ➕ Added: Complete GitHub Actions Workflow
```yaml
Triggers: push to master
Environment: RELEASE_REPOSITORY=4sudosu/WinSysMonitor
Jobs: build-and-release (ubuntu-latest)
```

#### ➕ Steps Implemented
1. **Checkout** - actions/checkout@v4
2. **JDK Setup** - actions/setup-java@v4 (Temurin 17)
3. **Android SDK** - android-actions/setup-android@v3
4. **Libnode Download** - Fetches from public `libnode-binaries` release:
   - `libnode.so` → `Android/app/libnode/bin/arm64-v8a/`
   - `libnode-include.zip` → unzipped to `Android/app/libnode/include/`
   - Uses `|| true` for resilience
5. **Keystore Decode** - Base64 → file from `ANDROID_KEYSTORE_BASE64` secret
6. **Build Signed APK** - `./gradlew assembleRelease` with:
   - `-PversionName="1.1.${GITHUB_RUN_NUMBER}"`
   - `-PversionCode="${GITHUB_RUN_NUMBER}"`
   - Signing config via `-PreleaseStoreFile`, `-PreleaseStorePassword`, etc.
7. **Publish to Public Repo** - `gh release create` to `4sudosu/WinSysMonitor`
8. **Publish to Private Repo** - `gh release create` to `${{ github.repository }}` (mirror)

#### 🔄 Fixed: Gradle Wrapper Permission
```bash
chmod +x ./gradlew  # Added before ./gradlew assembleRelease
```

#### 🔄 Fixed: Libnode Download Flags
- Changed `--output-dir` → `-D` (correct gh CLI flag)
- Added `--clobber` for overwrite
- Added `|| true` to prevent step failure

#### ⚠️ Known: Deprecation Warnings
- Node.js 20 deprecated (actions use Node 24)
- setup-java@v4 deprecated (should migrate to v5)

---

### Android App — Mandatory Update System

#### ➕ New Files Created
| File | Purpose |
|------|---------|
| `UpdateChecker.kt` | GitHub API client, version comparison, asset detection |
| `UpdateActivity.kt` | Update UI, DownloadManager integration, auto-install launch |
| `activity_update.xml` | Layout: version info, release notes, single "Update Now" button |
| `strings.xml` | Update-related string resources |

#### ➕ Modified Files
| File | Changes |
|------|---------|
| `LauncherActivity.kt` | Update gate, status messages, repo name display |
| `AndroidManifest.xml` | `REQUEST_INSTALL_PACKAGES`, `UpdateActivity` declaration |
| `build.gradle` | Version override via properties, signing config |
| `strings.xml` | Added update strings |

#### ➕ UpdateChecker.kt — Core Logic
```kotlin
// GitHub API call
GET https://api.github.com/repos/4sudosu/WinSysMonitor/releases/latest

// Response parsing
- tag_name → version (strip 'v' prefix)
- html_url → releaseUrl
- body → releaseNotes
- assets[].browser_download_url (filter *.apk) → apkUrl (fallback to html_url)

// Version comparison
isNewerVersion(latest, current):
  Split by '.', compare integer parts
  Missing parts = 0
  Returns true if latest > current

// Callback interface
onUpdateAvailable(UpdateInfo: latestVersion, releaseUrl, apkUrl, releaseNotes)
onNoUpdate()
onError(error)
```

#### ➕ LauncherActivity.kt — Update Gate
```kotlin
// On startup
updateChecked = false
setLauncherActionsEnabled(false)  // Disable ALL buttons
checkForUpdates()

// On result
onUpdateAvailable:
  updateChecked = true
  start UpdateActivity (CLEAR_TASK flag)

onNoUpdate:
  updateChecked = true
  setLauncherActionsEnabled(true)
  status = "✓ Up to date (4sudosu/WinSysMonitor)"

onError:
  updateChecked = false
  setLauncherActionsEnabled(false)
  status = "Update check failed"
  Show AlertDialog: Retry / Exit (non-dismissible)
```

#### ➕ UpdateActivity.kt — Download & Install
```kotlin
// DownloadManager request
- MIME: application/vnd.android.package-archive
- Destination: external files dir / Downloads / winsysmonitor-update.apk
- Notification: VISIBLE_NOTIFY_COMPLETED

// BroadcastReceiver on DOWNLOAD_COMPLETE
- getUriForDownloadedFile(id)
- Intent.ACTION_VIEW with FLAG_GRANT_READ_URI_PERMISSION
- Launches system package installer

// UI
- Single "Update Now" button (no "Later")
- Disables button after click
- Back button disabled (onBackPressed = Unit)
```

#### ➕ Status Messages (with repo name)
- Checking: `"Checking 4sudosu/WinSysMonitor for updates..."`
- Success: `"✓ Up to date (4sudosu/WinSysMonitor)"`
- Error: `"Update check failed"`

---

### Public Repo Assets Created

| Release | Tag | Assets | Purpose |
|---------|-----|--------|---------|
| libnode-binaries | libnode-binaries | libnode.so, libnode-include.zip | Native deps for CI |
| WinSysMonitor 1.1.7 | v1.1.7 | app-release.apk | First successful build |
| WinSysMonitor 1.1.8 | v1.1.8 | app-release.apk | Dual-repo publish |

---

### Documentation Added

| File | Description |
|------|-------------|
| `REPOSITORY_GUIDE.md` | Complete architecture, CI/CD, update flow, troubleshooting |
| `RECENT_CHANGES.md` | This file - complete change history |

---

## [2026-08-22] Release v1.1.0 — Baseline (Pre-Update System)

### Git Commits
```
d40df30 Remove unused unlock-dialog layout and drawable
dc4a47a Remove app password gates, add login lockout, fix installer path
67d3f4e Remove capture password field; only Server Admin Password in Connect screen
e8c8375 Fix: Connect screen only has Server Admin + Capture Password; add x-capture-password header for screenshots
dc6da1c Add separate Server Admin Password for X-Admin-Password header (separate from app unlock)
```

### Android State (Before Update System)
- `versionCode: 2`
- `versionName: "1.1.0"`
- No update checking
- No UpdateActivity, UpdateChecker
- App starts directly to launcher
- Debug APK only

---

## [2026-08-23] CI Builds — Versions v1.1.9 through v1.1.15

### Git Commits (auto-generated by CI on push)
```
Subsequent pushes to master triggered CI builds v1.1.9 → v1.1.15
Each push: versionCode = GITHUB_RUN_NUMBER, versionName = 1.1.{GITHUB_RUN_NUMBER}
```

### Changes
#### ✅ CI/CD Pipeline Matured
- Dual-repo publishing stable (public + private)
- libnode download resilient (|| true)
- Keystore signing consistent
- Version auto-increment working

#### 🔄 Workflow Refinements
- Each push triggers clean build
- Release created on both repos simultaneously
- APK asset: `app-release.apk` (signed with fixed keystore)

#### ⚠️ Known
- Multiple rapid releases due to iterative CI fixes
- Version numbers jump with each workflow run

---

## [2026-08-21] Release v1.0.0 — Initial Public Release

### Public Repo
- Tag: `v1.0.0`
- Assets: `WinSysMonitor-Agent-Setup.exe`, `WinSysMonitor-latest.apk`
- Date: 2026-08-20T20:48:03Z

---

## Complete File Change Summary

### Android App (`Android/app/`)

| File | v1.1.0 → v1.1.15 |
|------|-----------------|
| `build.gradle` | 🔄 Version properties, 🔄 Signing config |
| `src/main/AndroidManifest.xml` | ➕ REQUEST_INSTALL_PACKAGES, ➕ UpdateActivity |
| `src/main/java/.../LauncherActivity.kt` | 🔄 Full rewrite: update gate, status, callbacks |
| `src/main/java/.../UpdateChecker.kt` | ➕ New: GitHub API, version compare, asset detection |
| `src/main/java/.../UpdateActivity.kt` | ➕ New: DownloadManager, installer launch |
| `src/main/java/.../UpdateChecker.kt` | ➕ New |
| `src/main/res/layout/activity_update.xml` | ➕ New: Update UI |
| `src/main/res/values/strings.xml` | ➕ Update strings |

### CI/CD

| File | v1.1.0 → v1.1.15 |
|------|-----------------|
| `.github/workflows/android-release.yml` | ➕ Complete workflow |

### Documentation

| File | v1.1.0 → v1.1.15 |
|------|-----------------|
| `REPOSITORY_GUIDE.md` | ➕ New |
| `RECENT_CHANGES.md` | ➕ New |
| `START.md` | ➕ New |

---

## Secrets Configuration (Private Repo → Settings → Secrets → Actions)

| Secret | Value Set | Used In |
|--------|-----------|---------|
| `ANDROID_KEYSTORE_BASE64` | ✅ Base64 of release.keystore | Workflow: Decode release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | ✅ `winsysmonitor123` | Workflow: Sign APK |
| `ANDROID_KEY_ALIAS` | ✅ `winsysmonitor` | Workflow: Sign APK |
| `ANDROID_KEY_PASSWORD` | ✅ `winsysmonitor123` | Workflow: Sign APK |
| `RELEASE_TOKEN` | ✅ `gh auth token` (repo scope) | Workflow: Create releases on public repo |

---

## Keystore Details

```bash
# Generated: 2026-08-23
keytool -genkey -v -keystore release.keystore \
  -alias winsysmonitor \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass winsysmonitor123 -keypass winsysmonitor123 \
  -dname "CN=WinSysMonitor, OU=Android, O=4sudo.su, L=Unknown, ST=Unknown, C=IN"
```

**IMPORTANT:** Same keystore must be used for ALL future releases to allow seamless updates.

---

## Versioning Scheme

```
versionName = "1.1.{GITHUB_RUN_NUMBER}"   # e.g., 1.1.7, 1.1.8
versionCode = {GITHUB_RUN_NUMBER}          # e.g., 7, 8 (monotonically increasing)
```

**Manual override** (in build.gradle):
```gradle
versionCode 100
versionName "1.2.0"
```
Workflow `-P` flags take precedence if provided.

---

## Update Flow Summary (for AI Understanding)

```
APP START
    │
    ▼
LauncherActivity.onCreate()
    │
    ▼
setLauncherActionsEnabled(false)  // Disable all buttons
    │
    ▼
checkForUpdates() → UpdateChecker.checkForUpdates("4sudosu/WinSysMonitor")
    │
    ├──▶ GitHub API: GET /repos/4sudosu/WinSysMonitor/releases/latest
    │       │
    │       ├──▶ Success + newer version
    │       │       │
    │       │       ▼ onUpdateAvailable(UpdateInfo)
    │       │           │
    │       │           ▼ updateChecked = true
    │       │           ▼ start UpdateActivity (CLEAR_TASK)
    │       │
    │       ├──▶ Success + same version
    │       │       │
    │       │       ▼ onNoUpdate()
    │       │           │
    │       │           ▼ updateChecked = true
    │       │           ▼ setLauncherActionsEnabled(true)
    │       │           ▼ status = "✓ Up to date (4sudosu/WinSysMonitor)"
    │       │
    │       └──▶ Error (network, API limit, etc.)
    │               │
    │               ▼ onError(error)
    │                   │
    │                   ▼ updateChecked = false
    │                   ▼ setLauncherActionsEnabled(false)
    │                   ▼ status = "Update check failed"
    │                   ▼ AlertDialog: Retry / Exit (non-dismissible)
    │
    ▼
[If UpdateActivity started]
    │
    ▼
User sees: Current version, Latest version, Release notes
    │
    ▼
User taps "Update Now"
    │
    ▼
DownloadManager.enqueue(APK_URL)
    │
    ▼
Notification: "Downloading version X.Y.Z"
    │
    ▼ [Download complete]
BroadcastReceiver.onReceive()
    │
    ▼
getUriForDownloadedFile() → Intent.ACTION_VIEW → System Installer
    │
    ▼
User taps "Install" → App replaced (same signature = seamless)
    │
    ▼
App restarts → New version active
```

---

## Release History

| Version | Date | Repo | Notes |
|---------|------|------|-------|
| 1.1.15 | 2026-08-23 | Both | Latest |
| 1.1.14 | 2026-08-23 | Both | CI build |
| 1.1.13 | 2026-08-23 | Both | CI build |
| 1.1.12 | 2026-08-23 | Both | CI build |
| 1.1.11 | 2026-08-23 | Both | CI build |
| 1.1.10 | 2026-08-23 | Both | CI build |
| 1.1.9 | 2026-08-23 | Both | CI build |
| 1.1.8 | 2026-08-23 | Both | Dual-repo publish, libnode fix |
| 1.1.7 | 2026-08-23 | Both | First successful CI build |
| 1.1.0 | 2026-08-22 | Private | Baseline (no update system) |
| 1.0.0 | 2026-08-20 | Public | Initial release |

---

## Known Limitations (for AI)

| Limitation | Reason | Workaround |
|------------|--------|------------|
| No silent install | Android security | User must tap "Install" |
| Debug → Release upgrade fails | Different signatures | Uninstall first, then install release |
| GitHub API rate limit | 60/hr unauthenticated | Could add token to UpdateChecker |
| No delta updates | Full APK each time | Acceptable for ~56MB app |
| Requires internet | GitHub API check | Blocks app if offline (by design) |

---

## Next Release Checklist

- [ ] Update RECENT_CHANGES.md with new commits
- [ ] Push to master → auto-triggers workflow
- [ ] Verify both repos get release
- [ ] Test update flow on device
- [ ] Rotate RELEASE_TOKEN periodically

---

## AI Reconstruction Guide

Given only `REPOSITORY_GUIDE.md` and this `RECENT_CHANGES.md`, an AI should be able to:

1. **Understand the dual-repo architecture** (private source → public distribution)
2. **Reproduce the CI/CD pipeline** (workflow file + secrets list)
3. **Reproduce the Android update system** (all source files documented)
4. **Make a new release** (push to master, watch actions, verify)
5. **Debug common failures** (troubleshooting section)
6. **Modify the update logic** (all key files and logic flows documented)
7. **Regenerate keystore/secrets** (exact commands provided)