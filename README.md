# WinSysMonitor

Monitor your Windows machines over your local network from a phone or a browser.

WinSysMonitor is a LAN device-monitoring system made of four parts:

- **Agent** — a small C# Windows agent that reports the machine's hardware,
  OS, and status and streams live info to the server over WebSocket.
- **Server** — a Node.js (Express + `ws`) server that keeps a registry of
  agents, serves the web dashboard, and exposes a REST/WebSocket API.
- **Android app** — a native Kotlin client that embeds its own Node.js runtime
  (`libnode`), talks to the server, shows devices in a native UI, and posts a
  native notification + sound when a device connects.
- **Installer** — an Inno Setup script that packages the agent and installs it
  as a Windows service.

## Features

- Live device list with online/offline status (CPU, memory, disk, battery).
- Native Android UI — two tabs: **💻 Devices** and **⚙️ Settings** — no WebView
  for the main flow.
- 10 dashboard/app themes, notification tones, and a notification icon picker.
- **Switchable launcher icon** — pick from 6 adaptive-icon variants (Default,
  Emerald, Violet, Rose, Amber, Ocean) right from the app's Settings.
- Native notification + sound whenever a new agent connects.
- Web dashboard served by the server, with per-device details and a contact /
  developer section.
- Portable bundle mode: pack `node.exe` + server into a self-contained folder
  so the app carries its own Node.js runtime.

## Architecture

```
┌─────────────┐   WebSocket     ┌──────────────────────────┐   HTTP/WebSocket
│  Windows PC │ ──────────────▶ │  Node.js server (port    │ ◀─────────────  Browser
│  C# Agent   │   ws://ip:3001  │  3001) + web dashboard   │   http://ip:3001
└─────────────┘                 └───────────┬──────────────┘
                                            │ HTTP / WebSocket
                                   ┌────────▼─────────┐
                                   │  Android app     │
                                   │  (native UI)     │
                                   └──────────────────┘
```

## Repository structure

```
WinSysMonitor/
├── Agent/                  # C# .NET 8 WinForms agent (source of truth)
│   ├── *.cs                #   AgentClient, DeviceInfo, PowerShellRunner, ...
│   ├── WinSysMonitor.csproj
│   └── agent.config.sample.json   # copy to agent.config.json and fill in
├── Server/                 # Node.js server (source of truth)
│   ├── server.js           #   Express + WebSocket server, agent registry
│   ├── package.json        #   deps: express, ws
│   └── dashboard/          #   app.js / index.html / style.css
├── Installer/              # Inno Setup script (agent → Windows service)
├── Android/                # Native Android client (Kotlin, Gradle)
│   ├── app/src/main/       #   manifest, Kotlin, res, embedded server assets
│   └── README.md           #   Android-specific build/use docs
├── launcher.ps1            # Start a server or connect to one (menu)
├── bundle-server.ps1       # Build the self-contained portable bundle
└── build-agent.ps1         # Publish agent + compile versioned installer
```

> `WinSysMonitorApp/`, `installer-output/`, `Android/app/build/`,
> `Android/app/libnode/` and all `node_modules/` are generated artifacts and
> are not tracked. Rebuild them with the scripts above.

## Quick start

### 1. Run the server

```powershell
powershell -ExecutionPolicy Bypass -File .\launcher.ps1
```

Choose **Start server** to host the dashboard + agent endpoint, or
**Connect** to join an already-running server. Requires Node.js 18+ (or run the
portable bundle produced by `bundle-server.ps1`, which carries its own runtime).

### 2. Install the agent on a Windows machine

Run `build-agent.ps1` (requires the .NET 8 SDK and Inno Setup 6) to produce a
versioned installer, or just `dotnet publish` the `Agent` project. Point the
agent at the server in `agent.config.json`:

```json
{
  "ServerUrl": "ws://<SERVER_IP>:3001/ws/agent",
  "Token": "<SHARED_TOKEN>",
  "ReconnectDelaySec": 5
}
```

### 3. Watch from your phone

Build and install the Android app (see `Android/README.md`), enter the
server's `IP:port`, and the device list + connection notifications appear.

## Building the Android app

```bash
cd Android
./gradlew assembleDebug   # needs JDK 17 + Android SDK; see Android/README.md
```

The APK embeds `Server/` inside `app/src/main/assets/nodejs-project/` and
bundles Node via `libnode` native libraries (`abiFilters: arm64-v8a`).

## Security notes

- The server binds `0.0.0.0` on a configurable port and defaults to plain HTTP
  — intended for trusted LANs only. Set an `ADMIN_PASSWORD` environment
  variable to protect admin actions.
- The agent authenticates with a shared token (default example only).
- Android sets `android:usesCleartextTraffic="true"` because the server uses
  HTTP by default.

## License

All rights reserved. No license is granted for use, modification, or
distribution until one is explicitly added.

---

Built by [4sudo.su](https://github.com/4sudosu).