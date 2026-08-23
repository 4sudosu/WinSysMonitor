# WinSysMonitor — Repository Guide (Living Document)

> **Location:** Private repo only (`4sudosu/WinSysMonitorPrivate`)
> **Audience:** AI assistants & human maintainers
> **Companion:** `RECENT_CHANGES.md` (complete history) + `START.md` (AI quick start)
> **Rule:** After ANY change → Update RECENT_CHANGES.md
> **Cross-refs:** This guide ↔ `RECENT_CHANGES.md` ↔ `START.md` — all link to each other. Keep all three in sync.

---

## Quick Context for AI

**Read these three files first:**
1. **`START.md`** — AI quick start & instructions (start here)
2. This guide (`REPOSITORY_GUIDE.md`) — Architecture & processes
3. `RECENT_CHANGES.md` — Complete change history with code-level details

**Current State (as of 2026-08-23):**
- ✅ Dual-repo CI/CD working (private → public)
- ✅ Mandatory Android update system live
- ✅ v1.1.15 released on both repos (synced to actual GitHub releases)
- ✅ Keystore configured, secrets set
- ✅ libnode CI dependency resolved
- ✅ Docs: `START.md` → `REPOSITORY_GUIDE.md` → `RECENT_CHANGES.md` (all synced)
- ✅ Agent WebSocket keepalive and phone-host wake-lock changes implemented; see `RECENT_CHANGES.md`
- ✅ Agent protocol keepalive is 15 seconds and reconnect backoff is capped at 30 seconds
- ✅ Device connection probe runs before dashboard auth so failed attempts reach the blocker and appear in the dashboard

---

## Repository Architecture

### Private Repo: `4sudosu/WinSysMonitorPrivate`
- **Visibility:** Private
- **Content:** ALL source code + CI/CD + docs
- **Branch:** `master` (protected, direct push OK for now)
- **CI:** GitHub Actions on every push

### Public Repo: `4sudosu/WinSysMonitor`
- **Visibility:** Public
- **Content:** ONLY releases (APK assets)
- **Purpose:** Distribution + Android app update checks
- **App checks:** `https://api.github.com/repos/4sudosu/WinSysMonitor/releases/latest`

### Data Flow
```
Push to Private/master
       │
       ▼
GitHub Actions builds signed APK
       │
       ├──▶ Creates release on PUBLIC repo (4sudosu/WinSysMonitor)
       │
       └──▶ Creates release on PRIVATE repo (mirror)
                    │
                    ▼
         Android app checks PUBLIC repo → downloads APK → user installs
```

---

## Android Update System — Current Implementation

### Files (Android/app/src/main/java/com/wsmonitor/app/)
| File | Role |
|------|------|
| `LauncherActivity.kt` | Entry point, update gate, status UI |
| `UpdateChecker.kt` | GitHub API client, version compare, asset detection |
| `UpdateActivity.kt` | Download UI, DownloadManager, installer launch |
| `ServerConfig.kt` | Server connection config (separate) |

### Key Constants (in LauncherActivity.kt)
```kotlin
private val GITHUB_REPO = "4sudosu/WinSysMonitor"  // PUBLIC repo
```

### Update Flow
1. App starts → `updateChecked = false` → disable all buttons
2. `checkForUpdates()` → calls `UpdateChecker.checkForUpdates(GITHUB_REPO)`
3. **On newer version:** → `UpdateActivity` (non-dismissible, single "Update Now")
4. **On same version:** → status "✓ Up to date (4sudosu/WinSysMonitor)" → enable buttons
5. **On error:** → block UI, show Retry/Exit dialog

### Version Comparison (UpdateChecker.kt)
```kotlin
isNewerVersion(latest, current):
  Split by '.', map to Int, compare pairwise
  Missing parts = 0
  true if latest > current
```

### APK Asset Detection
```kotlin
// From GitHub release JSON
assets[].browser_download_url where name.endsWith(".apk")
→ apkUrl (fallback to html_url if no APK asset)
```

---

## CI/CD Pipeline (`.github/workflows/android-release.yml`)

### Trigger
```yaml
on:
  push:
    branches: [master]
```

### Secrets Required (Private Repo → Settings → Secrets → Actions)
| Secret | Current Value | Purpose |
|--------|---------------|---------|
| `ANDROID_KEYSTORE_BASE64` | Base64 of release.keystore | Sign APK |
| `ANDROID_KEYSTORE_PASSWORD` | `winsysmonitor123` | Keystore password |
| `ANDROID_KEY_ALIAS` | `winsysmonitor` | Key alias |
| `ANDROID_KEY_PASSWORD` | `winsysmonitor123` | Key password |
| `RELEASE_TOKEN` | GitHub token (repo scope) | Create releases on public repo |

### Build Steps
1. Checkout source
2. Setup JDK 17 (Temurin)
3. Setup Android SDK
4. Download libnode from public `libnode-binaries` release
5. Decode keystore from base64
6. `./gradlew assembleRelease` with version props + signing
7. Publish to PUBLIC repo (`gh release create`)
8. Publish to PRIVATE repo (`gh release create` mirror)

### Version Auto-Generation
```bash
versionName="1.1.${GITHUB_RUN_NUMBER}"  # e.g., 1.1.8
versionCode="${GITHUB_RUN_NUMBER}"       # e.g., 8
```

---

## Keystore (CRITICAL — Never Change)

```bash
# Generated once, used for ALL releases
keytool -genkey -v -keystore release.keystore \
  -alias winsysmonitor \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass winsysmonitor123 -keypass winsysmonitor123 \
  -dname "CN=WinSysMonitor, OU=Android, O=4sudo.su, L=Unknown, ST=Unknown, C=IN"
```

**If keystore changes → users must uninstall/reinstall (signature mismatch).**

---

## Making Changes — Standard Process

### 1. Make Code Changes
Edit files in private repo.

### 2. Test Locally
```bash
cd Android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Update RECENT_CHANGES.md
**Before committing, add entry:**
```markdown
## [YYYY-MM-DD] Release vX.Y.Z — Short title

### Commits
- `hash` Description

### Changes
#### Component
- ✅ What changed

#### Files Added/Modified
| File | Change |
```

### 4. Commit & Push
```bash
git add -A
git commit -m "Descriptive message"
git push origin master
```

### 5. Verify
- Watch: `https://github.com/4sudosu/WinSysMonitorPrivate/actions`
- Check both repos have new release
- Test update on device

---

## Desired Changes / Roadmap

### High Priority
- [ ] **Add `workflow_dispatch` trigger** — manual workflow runs
- [ ] **Migrate to `setup-java@v5`** — current v4 deprecated
- [ ] **Add GitHub token to UpdateChecker** — avoid 60/hr API limit
- [ ] **Handle GitHub API pagination** — for repos with many releases

### Medium Priority
- [ ] **Play Store deployment option** — for automatic updates
- [ ] **Delta updates** — reduce download size
- [ ] **Custom update channel** — beta/stable tracks
- [ ] **In-app changelog display** — show RECENT_CHANGES.md summary

### Low Priority / Nice to Have
- [ ] **Signed Windows agent releases** — via CI
- [ ] **Automated version bump** — conventional commits
- [ ] **Release notes from commit messages** — auto-generate
- [ ] **Slack/Discord notification** — on release

---

## Troubleshooting Quick Reference

| Symptom | Likely Cause | Fix |
|---------|--------------|-----|
| Build fails: `libnode.so` missing | libnode-binaries release missing | `gh release create libnode-binaries ...` |
| Build fails: keystore not found | Secret not set / base64 corrupt | Re-set `ANDROID_KEYSTORE_BASE64` |
| App stuck "Checking..." | No internet / API limit | Check network, add token to UpdateChecker |
| "Package already exists" | Debug APK installed, installing release | `adb uninstall com.wsmonitor.app` first |
| Update not detected | Version comparison bug | Check `isNewerVersion` logic |
| Release not created | RELEASE_TOKEN expired / no permission | Regenerate token with `repo` scope |

---

## File Ownership Map

| Area | Files | Modify When |
|------|-------|-------------|
| CI/CD | `.github/workflows/android-release.yml` | Build process, release targets |
| Android version | `Android/app/build.gradle` | Version scheme, signing |
| Update logic | `UpdateChecker.kt`, `LauncherActivity.kt`, `UpdateActivity.kt` | Update behavior, UI |
| Permissions | `AndroidManifest.xml` | New permissions, activities |
| UI strings | `res/values/strings.xml` | User-facing text |
| Update UI | `res/layout/activity_update.xml` | Update screen layout |
| Documentation | `REPOSITORY_GUIDE.md`, `RECENT_CHANGES.md` | **After ANY change** |

---

## AI Instructions — Read This Section

### When User Asks for a Change:
1. **Read** `RECENT_CHANGES.md` to understand current state
2. **Read** this guide for architecture
3. **Identify** affected files from "File Ownership Map"
4. **Implement** the change
5. **Test** locally (`gradlew assembleDebug` + adb install)
6. **Update** `RECENT_CHANGES.md` with:
   - Date, version (if releasing)
   - Commit hashes
   - What changed (component + files table)
7. **Commit** both code + RECENT_CHANGES.md
8. **Push** to master (triggers CI)

### When Debugging:
1. Check `RECENT_CHANGES.md` "Known Limitations" & "Troubleshooting"
2. Check workflow logs: `gh run watch <id> --repo 4sudosu/WinSysMonitorPrivate`
3. Check public releases: `gh release list --repo 4sudosu/WinSysMonitor`

### When Adding Features:
1. Follow existing patterns (Kotlin, coroutines, Material Design)
2. Keep `GITHUB_REPO` constant in LauncherActivity
3. Maintain backward compatibility (same keystore!)
4. Update both docs

---

## Version History Summary

| Version | Date | Key Change |
|---------|------|------------|
| 1.1.15 | 2026-08-23 | Latest release |
| 1.1.14 | 2026-08-23 | CI build |
| 1.1.13 | 2026-08-23 | CI build |
| 1.1.12 | 2026-08-23 | CI build |
| 1.1.11 | 2026-08-23 | CI build |
| 1.1.10 | 2026-08-23 | CI build |
| 1.1.9 | 2026-08-23 | CI build |
| 1.1.8 | 2026-08-23 | Dual-repo publish, libnode CI fix |
| 1.1.7 | 2026-08-23 | First successful CI build |
| 1.1.0 | 2026-08-22 | Baseline (no update system) |
| 1.0.0 | 2026-08-20 | Initial public release |

---

## Contact / Escalation

- **Owner:** 4sudo.su (@4sudosu)
- **Telegram:** @verifiedharyanvi
- **Email:** 4sudo.su@gmail.com

---

## Last Updated
**2026-08-23** — After WebSocket disconnect resilience changes

> **Next AI:** Read RECENT_CHANGES.md first, then this guide. Update both after changes.
