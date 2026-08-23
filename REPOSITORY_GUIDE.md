# WinSysMonitor — Repository Architecture & Release Guide

## Overview

This document describes the dual-repository setup for WinSysMonitor, the CI/CD pipeline, and how to make future updates.

---

## Repository Structure

### Private Repository (Source of Truth)
**URL:** `https://github.com/4sudosu/WinSysMonitorPrivate`
**Visibility:** Private

Contains **all source code**:
```
WinSysMonitorPrivate/
├── Agent/                    # Windows C# agent (.NET 8)
├── Android/                  # Native Kotlin Android app
├── Server/                   # Node.js server + web dashboard
├── Installer/                # Inno Setup installer
├── .github/workflows/        # CI/CD pipelines
├── build-agent.ps1           # Agent build script
├── bundle-server.ps1         # Server bundling script
└── README.md
```

**Purpose:**
- Development
- Source control
- CI/CD execution
- Secret storage (keystore, tokens)

---

### Public Repository (Distribution)
**URL:** `https://github.com/4sudosu/WinSysMonitor`
**Visibility:** Public

Contains **only releases**:
```
WinSysMonitor/
├── Releases/
│   ├── v1.1.8/              # APK + release notes
│   ├── v1.1.7/
│   └── libnode-binaries/    # Native dependencies
```

**Purpose:**
- User-facing downloads
- Update checks from Android app
- Clean public interface (no source code)

---

## CI/CD Pipeline

### Workflow: `.github/workflows/android-release.yml`

**Triggers:** Push to `master` branch

**Steps:**
1. **Checkout** source from private repo
2. **Setup** JDK 17, Android SDK
3. **Download** libnode binaries (from public repo `libnode-binaries` release)
4. **Decode** release keystore (from `ANDROID_KEYSTORE_BASE64` secret)
5. **Build** signed release APK (`./gradlew assembleRelease`)
6. **Publish** to BOTH repositories:
   - Public: `4sudosu/WinSysMonitor` (primary distribution)
   - Private: `4sudosu/WinSysMonitorPrivate` (mirror)

**Required Secrets (Private Repo → Settings → Secrets → Actions):**
| Secret | Description |
|--------|-------------|
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded release keystore |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (e.g., `winsysmonitor`) |
| `ANDROID_KEY_PASSWORD` | Key password |
| `RELEASE_TOKEN` | GitHub token with `repo` scope for public repo |

---

## Android App Update Flow

### Configuration (in `LauncherActivity.kt`):
```kotlin
private val GITHUB_REPO = "4sudosu/WinSysMonitor"  // PUBLIC repo
```

### Update Check Process:
1. App starts → Disables all buttons
2. Checks `https://api.github.com/repos/4sudosu/WinSysMonitor/releases/latest`
3. Compares `versionName` with installed app
4. **If newer:** Shows mandatory update screen with "Update Now"
5. **If same:** Shows "✓ Up to date (4sudosu/WinSysMonitor)", enables buttons
6. **If error:** Blocks app, shows retry dialog

### Update Screen (`UpdateActivity.kt`):
- Downloads APK via `DownloadManager`
- On completion: launches system installer (`ACTION_VIEW`)
- User taps "Install" → done
- **Cannot auto-install** (Android security restriction)

---

## Making Future Updates

### 1. Code Changes
Edit files in `WinSysMonitorPrivate/` as normal.

### 2. Version Bump (Optional)
The workflow auto-generates version: `1.1.{GITHUB_RUN_NUMBER}`
- `versionCode` = run number (incrementing)
- `versionName` = `1.1.{run_number}`

To manually set version, edit `Android/app/build.gradle`:
```gradle
defaultConfig {
    versionCode 100        // integer
    versionName "1.2.0"    // string
}
```
*Workflow overrides these via `-P` flags if not set.*

### 3. Commit & Push
```bash
git add -A
git commit -m "Your changes"
git push origin master
```

### 4. Automatic Release
- GitHub Actions starts automatically
- Watch progress: `https://github.com/4sudosu/WinSysMonitorPrivate/actions`
- On success: releases created on **both repos** with tag `v1.1.{run_number}`

### 5. Verify
- Public: `https://github.com/4sudosu/WinSysMonitor/releases`
- Private: `https://github.com/4sudosu/WinSysMonitorPrivate/releases`
- APK asset: `app-release.apk`

---

## Key Files to Understand

| File | Purpose |
|------|---------|
| `.github/workflows/android-release.yml` | CI/CD pipeline |
| `Android/app/build.gradle` | Version config, signing |
| `Android/app/src/main/java/.../LauncherActivity.kt` | Update check logic |
| `Android/app/src/main/java/.../UpdateChecker.kt` | GitHub API client |
| `Android/app/src/main/java/.../UpdateActivity.kt` | Download + install UI |
| `Android/app/src/main/AndroidManifest.xml` | Permissions (`REQUEST_INSTALL_PACKAGES`) |

---

## Troubleshooting

### Build Fails: "libnode.so missing"
- Ensure `libnode-binaries` release exists on public repo
- Workflow downloads from there

### Build Fails: "keystore not found"
- Check `ANDROID_KEYSTORE_BASE64` secret is set
- Regenerate: `keytool -genkey -v -keystore release.keystore -alias winsysmonitor -keyalg RSA -keysize 2048 -validity 10000`
- Encode: `base64 -w 0 release.keystore`

### App Stuck on "Checking for updates"
- Verify internet connectivity
- Check GitHub API rate limits (unauthenticated: 60/hr)
- Public repo must be accessible

### "Package already exists" on install
- User has debug APK installed, trying to install release APK
- **Fix:** Uninstall first, then install release
- Future updates (release→release) work seamlessly

---

## Security Notes

- **Never commit** `agent.config.json`, `agents.json`, `launcher.config.json`
- **Never commit** keystore file (only base64 in secrets)
- `RELEASE_TOKEN` needs only `public_repo` scope if public repo is public
- Rotate tokens periodically

---

## Quick Reference Commands

```bash
# Local debug build
cd Android && ./gradlew assembleDebug

# Install local debug APK
adb install -r Android/app/build/outputs/apk/debug/app-debug.apk

# Check latest public release
gh release list --repo 4sudosu/WinSysMonitor --limit 1

# Trigger workflow manually (if workflow_dispatch added)
gh workflow run android-release.yml --repo 4sudosu/WinSysMonitorPrivate

# View workflow logs
gh run watch <run-id> --repo 4sudosu/WinSysMonitorPrivate
```

---

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    DEVELOPER MACHINE                        │
│  Edit code → git commit → git push origin master            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│           PRIVATE REPO (4sudosu/WinSysMonitorPrivate)       │
│  ┌─────────────────────────────────────────────────────┐   │
│  │           GITHUB ACTIONS WORKFLOW                    │   │
│  │  1. Checkout source                                 │   │
│  │  2. Setup JDK / Android SDK                         │   │
│  │  3. Download libnode (from public)                  │   │
│  │  4. Decode keystore (from secrets)                  │   │
│  │  5. Build signed APK                                │   │
│  │  6. Create release on PUBLIC repo                   │   │
│  │  7. Create release on PRIVATE repo (mirror)         │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────┬───────────────────────────────────┘
                          │
              ┌───────────┴───────────┐
                          ▼
┌─────────────────────────────────────────────────────────────┐
│            PUBLIC REPO (4sudosu/WinSysMonitor)              │
│  Releases: v1.1.8, v1.1.7, ...                              │
│  Assets: app-release.apk (signed, same keystore)            │
└─────────────────────────┬───────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────┐
│                      USER'S PHONE                           │
│  App starts → Checks public repo API                        │
│  If newer: Downloads APK → System installer → User taps OK │
└─────────────────────────────────────────────────────────────┘
```

---

## Contact / Maintainer

- **Owner:** 4sudo.su (@4sudosu)
- **Telegram:** @verifiedharyanvi
- **Email:** 4sudo.su@gmail.com

---

*Generated for AI-assisted maintenance. Keep this document updated with any architectural changes.*