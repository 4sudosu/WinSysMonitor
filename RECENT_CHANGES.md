# Recent Changes Log

> **Location:** Private repo only (`4sudosu/WinSysMonitorPrivate`)
> **Purpose:** Track every change for future AI/human reference

---

## [2026-08-23] Release v1.1.8 — Dual-repo publishing + libnode fix

### Commits
- `6b17126` Add repository architecture guide (REPOSITORY_GUIDE.md)
- `0eec868` Also publish release to private repo (mirror)
- `7e9eb7a` Ignore download exit codes (libnode download resilience)
- `9890d6f` Add clobber to libnode downloads
- `9145186` Add libnode include download (headers)
- `620e9ea` Fix libnode download flag (-D not --output-dir)
- `3f5ae14` Add libnode download step for CI
- `c1bceda` Fix gradlew execute permission in workflow
- `48864cd` Bump Android app version to 1.1.1
- `3f2beaf` Add mandatory Android update releases

### Changes

#### CI/CD (.github/workflows/android-release.yml)
- ✅ Publishes signed APK to **both** repos (public + private)
- ✅ Downloads libnode.so + headers from public `libnode-binaries` release
- ✅ Handles missing libnode gracefully (|| true)
- ✅ Fixed gradlew permission (chmod +x)
- ✅ Version auto-generated: `1.1.{GITHUB_RUN_NUMBER}`

#### Android App
- ✅ Mandatory update check on startup (blocks UI until verified)
- ✅ Fetches from `4sudosu/WinSysMonitor` (public repo)
- ✅ Shows repo name in status: "Checking 4sudosu/WinSysMonitor for updates..."
- ✅ Shows "✓ Up to date (4sudosu/WinSysMonitor)" when current
- ✅ Downloads APK via DownloadManager
- ✅ Launches system installer (user taps "Install")
- ✅ Non-dismissible update screen (no back button, no "Later")
- ✅ Retry dialog on network failure

#### Files Added/Modified
| File | Change |
|------|--------|
| `.github/workflows/android-release.yml` | Full CI pipeline |
| `Android/app/build.gradle` | Version override, signing config |
| `Android/app/src/main/AndroidManifest.xml` | REQUEST_INSTALL_PACKAGES, UpdateActivity |
| `Android/app/src/main/java/.../LauncherActivity.kt` | Update gate, status messages |
| `Android/app/src/main/java/.../UpdateChecker.kt` | GitHub API, asset detection |
| `Android/app/src/main/java/.../UpdateActivity.kt` | Download + install flow |
| `Android/app/src/main/res/layout/activity_update.xml` | Single "Update Now" button |
| `Android/app/src/main/res/values/strings.xml` | Update strings |
| `REPOSITORY_GUIDE.md` | Architecture documentation |
| `RECENT_CHANGES.md` | This file |

#### Public Repo Assets
- Created `libnode-binaries` release with `libnode.so` + `libnode-include.zip`
- Published `v1.1.7` then `v1.1.8` with `app-release.apk`

---

## [2026-08-22] Release v1.1.0 — Baseline

### Commits (from git log)
- `d40df30` Remove unused unlock-dialog layout and drawable
- `dc4a47a` Remove app password gates, add login lockout, fix installer path
- `67d3f4e` Remove capture password field
- `e8c8375` Fix Connect screen headers
- `dc6da1c` Add separate Server Admin Password

### Android Version
- `versionCode: 2`
- `versionName: "1.1.0"`

---

## How to Update This Log

**After every push to master:**
1. Note the commit hash (`git log --oneline -1`)
2. Add entry to this file under new date section
3. List commits, summary, and affected files
4. Commit this file with your changes

**Template:**
```markdown
## [YYYY-MM-DD] Release vX.Y.Z — Short title

### Commits
- `hash` Description

### Changes
#### Component
- ✅ Change description

#### Files Added/Modified
| File | Change |
```

---

## Release History

| Version | Date | Public Repo | Private Repo | Notes |
|---------|------|-------------|--------------|-------|
| 1.1.8 | 2026-08-23 | ✅ | ✅ | Dual-repo publish, libnode fix |
| 1.1.7 | 2026-08-23 | ✅ | ✅ | First successful build |
| 1.1.0 | 2026-08-21 | ✅ | — | Initial public release |

---

## Next Steps / TODO

- [ ] Add workflow_dispatch for manual triggers
- [ ] Migrate to setup-java@v5
- [ ] Add Play Store deployment option
- [ ] Implement delta updates (if feasible)