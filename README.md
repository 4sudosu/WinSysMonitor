# 📡 WinSysMonitor — Full Source

> **The complete source for the WinSysMonitor LAN monitoring system.**
> Monitor every Windows machine on your network from your phone or browser —
> no internet, no cloud, no subscription.

This is the **development repository**. Ready-to-install binaries live in the
public companion repo: [**4sudosu/WinSysMonitor**](https://github.com/4sudosu/WinSysMonitor).

---

## 📸 Screenshots

### 📱 Android App

| Launcher | Devices |
|---|---|
| ![Launcher](docs/screenshots/app-launcher.png) | ![Devices](docs/screenshots/app-devices.png) |

| Device Detail | Settings |
|---|---|
| ![Device Detail](docs/screenshots/app-detail.png) | ![Settings](docs/screenshots/app-settings.png) |

---

## 🧠 What It Does

A lightweight agent installs on each Windows PC and connects to a small
self-hosted server over your LAN. The server keeps a live registry of every
machine and serves a beautiful web dashboard. A native **Kotlin Android app**
(no WebView for the main flow) shows your devices, lets you grab
full-screen screenshots, and fires a native notification + sound the moment a
new machine connects.

```
┌──────────────┐   WebSocket    ┌──────────────┐   HTTP / SSE    ┌────────────────┐
│ 🖥️ Windows   │ ─────────────► │ 🌐 Node.js   │ ──────────────► │ 🖥️ Web dashboard │
│    Agent (C#) │   token auth    │    Server    │                 │   (browser)     │
└──────────────┘                 └──────────────┘                 └────────────────┘
                                          ▲
                                          │ REST
                                 ┌────────┴────────┐
                                 │ 📱 Android App  │  (embeds its own Node.js
                                 │    (Kotlin)     │   runtime via libnode)
                                 └─────────────────┘
```

---

## ✨ Features

| | |
|---|---|
| 🖥️ **Live Device List** | Hostname, IP, serial number, model, OS, agent version, online/offline status |
| 📸 **Screen Capture** | One-click full-screen screenshot of any online machine — zoom, save, copy & share |
| 🔔 **Connect Alerts** | Native notification + sound when a new agent connects |
| 🎨 **10 Themes** | Hand-crafted light & dark themes that recolor dashboard and app |
| 📱 **Native Android UI** | Kotlin + Material — Devices & Settings tabs (no WebView) |
| 🎵 **Notification Tones & Icons** | Pick your own tone and notification icon |
| 🌀 **Switchable Launcher Icon** | 6 adaptive icons: Default, Emerald, Violet, Rose, Amber, Ocean |
| 🔁 **Auto-Refresh Screenshots** | Every 3s / 5s / 10s per device |
| 🌐 **Run on 0.0.0.0** | Host the server on all interfaces |
| 🪟 **24x7 Agent Service** | Installs as a Windows service (LocalSystem, auto-restart) |
| 🔐 **Token + Admin Auth** | Shared agent token, password-protected admin actions |
| 🎁 **Portable Bundle** | Pack `node.exe` + server into a self-contained folder |

---

## 📁 Repository Structure

```
WinSysMonitor/
├── Agent/                  # 🪟 C# (.NET 8) Windows agent
│   ├── AgentClient.cs      #   WebSocket client + reconnect logic
│   ├── AgentService.cs     #   Windows service host
│   ├── SysInfo.cs          #   hardware / OS / status collection
│   ├── agent.config.json   #   runtime config (server, token) — gitignored
│   └── WinSysMonitor.csproj
│
├── Android/                # 📱 Native Kotlin Android app
│   └── app/
│       ├── build.gradle    #   compileSdk 34 · minSdk 26 · versionName 1.1.0
│       └── src/main/
│           ├── assets/nodejs-project/  # bundled Node.js server + dashboard
│           ├── java/com/wsmonitor/app/ # activities, server config, services
│           └── res/        #   layouts, themes, icons, xml
│
├── Server/                 # 🌐 Node.js server + web dashboard
│   ├── server.js           #   Express + ws + SSE + REST API
│   ├── dashboard/          #   index.html · app.js · style.css
│   └── package.json
│
├── Installer/              # 📦 Inno Setup 6 installer for the agent
│   └── installer.iss       #   wizard, service install, config locking
│
├── build-agent.ps1         # 🔨 one-command build: publish → install → package
├── bundle-server.ps1       # 📦 pack node.exe + server into a portable folder
├── launcher.ps1            # 🚀 dev helper for running the server
└── README.md
```

---

## ✅ Requirements

| Component | Requirement |
|---|---|
| 🪟 Agent build | Windows 10+, .NET 8 SDK |
| 📦 Agent installer | Inno Setup 6 |
| 📱 Android app | JDK 17, Android SDK (compileSdk 34), CMake 3.22.1, `libnode` |
| 🌐 Server | Node.js 18+ |
| ▶️ Runtime | Windows 10+ (agent) · Android 8.0+ (app) |

---

## 🔨 Build From Source

### 📱 Android app

```bash
# set SDK + CMake paths in Android/local.properties (this file is gitignored)
sdk.dir=C:\\path\\to\\android-sdk
cmake.dir=C:\\path\\to\\cmake-3.22.1

cd Android
gradlew.bat assembleDebug
# → Android/app/build/outputs/apk/debug/app-debug.apk
```

### 🪟 Windows agent + installer

```powershell
# one command: dotnet publish → Inno Setup 6 packaging
./build-agent.ps1
# → installer-output\WinSysMonitor-Setup-<version>.exe
```

> `build-agent.ps1` bumps the agent version, publishes a single-file
> self-contained build, and compiles the Inno Setup installer. Requires the
> .NET 8 SDK and Inno Setup 6 (`ISCC.exe`).

### 🌐 Server (dev)

```bash
cd Server
npm install
node server.js        # listens on 0.0.0.0:3001 (HOST/PORT env overrides)
```

---

## 🚀 Install & Run

1. **Install agents** — run `WinSysMonitor-Setup-*.exe` as Administrator on
   each Windows machine, enter the server IP/port/token when prompted. The
   agent installs as a 24x7 service.
2. **Start the server** — on your phone: **🚀 Start Server** / **🌐 Run Server
   on 0.0.0.0**; or run `node server.js` on any PC on the network.
3. **Open the dashboard** — `http://<server-ip>:3001` in any browser.
4. **Connect the Android app** — **🔗 Connect to Server** → enter `IP:port`.

**Admin password** defaults to `admin` (override with `ADMIN_PASSWORD` env on
the server). Change it before exposing the server beyond a trusted LAN.

---

## 🔐 Security Notes

- Built for **trusted LANs** — plain HTTP by default.
- Agents authenticate with a **shared token**.
- Admin actions (screenshots) are protected by an **admin password**.
- The installer locks `agent.config.json` so only **SYSTEM** and
  **Administrators** can modify it.
- `agents.json`, `agent.config.json`, `launcher.config.json` are gitignored —
  never commit runtime secrets.

---

## 🧱 Tech Stack

- **Agent:** C# / .NET 8 · Windows Forms · System.ServiceProcess
- **Server:** Node.js · Express · `ws` · Server-Sent Events
- **Android:** Kotlin · Jetpack · native Node.js runtime (`libnode`)
- **Dashboard:** HTML · CSS (10 themes) · vanilla JS
- **Packaging:** Inno Setup 6 · Gradle · dotnet publish

---

Built with ❤️ by [**4sudo.su**](https://github.com/4sudosu) ·
[Telegram](https://t.me/verifiedharyanvi)