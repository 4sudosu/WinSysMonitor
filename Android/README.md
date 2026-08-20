# WinSysMonitor — Android App

Native Android client for WinSysMonitor. Connects to the Node.js server over
HTTP/WebSocket and shows connected devices in a **native Kotlin UI** — the main
flow is not a WebView. It also embeds its own Node.js runtime (`libnode`) so
the in-app server stays in sync with the desktop server codebase.

## Features

- Two tabs: **💻 Devices** (live list with online/offline status) and
  **⚙️ Settings**.
- **Themes** — 10 color themes that recolor the whole app.
- **Notification tone + icon pickers**.
- **App icon switcher** — pick from 6 adaptive-icon variants (Default,
  Emerald, Violet, Rose, Amber, Ocean). The launcher icon changes instantly via
  `PackageManager.setComponentEnabledSetting` on `activity-alias` entries.
- Native notification + sound when a device connects to the server.
- Server runs on-device via embedded Node (`main.cjs` + `nodejs-project`
  assets) and can be exposed on the LAN for the desktop dashboard.

## Repository layout

```
app/src/main/
├── AndroidManifest.xml        # launcher activity-alias set for icon switching
├── java/com/wsmonitor/app/    # MainActivity, LauncherActivity, NodeService,
│                              # AgentEventService, DeviceAdapter, AppPrefs, ...
├── assets/nodejs-project/     # embedded copy of the Node server + dashboard
├── cpp/native-lib.cpp         # JNI glue for libnode
└── res/                       # layouts, adaptive launcher icons, tones, colors
```

## Build

1. Install **Android Studio** (bundles a JDK; AGP 8.5.2 + Gradle 8.9 are pinned
   in `build.gradle` / `gradle/wrapper/gradle-wrapper.properties`).
2. Open this `Android` folder in Android Studio and let Gradle sync.
3. The build needs the Node embedded assets present — from the repo root run:

   ```bash
   cd Android/app/src/main/assets/nodejs-project
   npm install
   ```

4. Build from Android Studio (Run ▶ or Build APK), or from a terminal:

   ```bash
   cd Android
   ./gradlew assembleDebug      # needs JDK 17 + ANDROID_HOME set
   ```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
Only the `arm64-v8a` ABI is built (`ndk { abiFilters "arm64-v8a" }`), matching
the shipped `libnode` binaries.

## Use

1. Start the server on any PC (`launcher.ps1` → Start server) or use the
   in-app server.
2. In the app, enter the server's **IP:port** (e.g. `192.168.1.50:3001`) —
   it connects to `http://<ip>:<port>`.
3. **Devices** lists every connected agent; **Settings** changes themes,
   notification tone/icon, and the app's launcher icon.
4. When a device connects you get a **native notification + sound**.

## Notes

- Requires Android 8.0+ (minSdk 26), targets SDK 34.
- Server uses plain HTTP by default, so the manifest sets
  `android:usesCleartextTraffic="true"`.
- The launcher icon is selected by enabling one of the `com.wsmonitor.app`
  `.launcher.*` activity-aliases (only one is enabled at a time).