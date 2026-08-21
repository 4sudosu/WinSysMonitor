# 📡 WinSysMonitor

> **Monitor every Windows machine on your LAN — right from your phone or browser.**

WinSysMonitor turns any Windows PC into a monitored agent that reports to a
self-hosted server over your local network. Watch **live device status**,
grab **full-screen screenshots** on demand, and get **instant notifications**
when a machine comes online — no internet, no cloud, no subscription.

---

## ✨ Features

| | |
|---|---|
| 🖥️ **Live Device List** | Every connected Windows agent with hostname, IP, serial number, model, OS & agent version |
| 🔴 **Online/Offline Status** | See at a glance which machines are up right now |
| 📸 **Screen Capture** | One-click full-screen screenshot of any online machine — zoom, save, copy & share |
| 🔔 **Connect Alerts** | Native notification **+ sound** the moment a new agent connects |
| 🎨 **10 Beautiful Themes** | Hand-crafted light & dark themes that recolor the entire dashboard |
| 📱 **Native Android App** | No WebView — a real Kotlin UI with devices & settings tabs |
| 🎵 **Notification Tones** | Pick a tone (or your own sound) & notification icon |
| 🔁 **Auto Refresh** | Screenshot auto-refresh every 3s / 5s / 10s on any device |
| 🌐 **Run on 0.0.0.0** | Host the server on all interfaces so everyone on the network can access it |
| ⚡ **Self-Contained** | Agent installs as a 24x7 Windows service; server can bundle its own Node.js runtime |

---

## 📥 Download

Grab the latest release from the **[Releases](../../releases)** page:

- 🪟 **Windows Agent Installer** — `WinSysMonitor-Setup-*.exe`
- 📱 **Android App** — `WinSysMonitor-latest.apk` (arm64-v8a)
- 🖥️ **Web Dashboard** — included below in the `dashboard/` folder

---

## 🚀 Installation

### 🪟 1. Install the Windows Agent

1. Download the **Windows Agent Installer** from Releases.
2. Run it **as Administrator**.
3. Enter your **server IP / hostname**, **port** (default `3001`) and the
   **agent token** when prompted.
4. Done — the agent installs as a **24x7 Windows service** (runs as
   LocalSystem, auto-restarts on crash), so the machine is monitored even with
   no user logged on.

> Change the server IP/port/token later by editing `agent.config.json` in the
> install folder — the agent reloads it automatically.

### 📱 2. Install the Android App

1. Download `WinSysMonitor-latest.apk` from Releases.
2. Allow "install from unknown sources" if prompted.
3. Open the app → **🔗 Connect to Server** → enter `IP:port`.
4. Or use **🚀 Start Server** / **🌐 Run Server on 0.0.0.0** to host the
   server from your phone.

> Requires **Android 8.0+** (arm64-v8a).

### 🖥️ 3. Use the Web Dashboard

The dashboard is the folder you're reading now — serve it from any static web
server, or let the Android app / Node server host it for you.

Once running, open `http://<server-ip>:3001` in any browser to see your
devices.

---

## ✅ Requirements

| Component | Requirement |
|---|---|
| 🪟 Windows Agent | Windows 10+ (x64) |
| 📱 Android App | Android 8.0+ (minSdk 26), arm64-v8a |
| 🖥️ Web Dashboard | Any modern browser (Chrome, Edge, Firefox) |
| 🌐 Server (optional) | Node.js 18+ — or use the bundled runtime from the Android app |

---

## 🔐 Security Notes

- Intended for **trusted LANs only** — the server uses plain HTTP by default.
- The agent authenticates with a **shared token**.
- Admin actions (screenshots, shutdown) are protected by an **admin password**.
- The Windows installer locks `agent.config.json` so only **SYSTEM** and
  **Administrators** can modify it.

---

## 🧩 Dashboard Files

```
dashboard/
├── index.html   # single-page dashboard UI
├── app.js       # live device polling, SSE notifications, screen capture
└── style.css    # 10 themes, glass-morphism design, animations
```

---

Built with ❤️ by [**4sudo.su**](https://github.com/4sudosu) ·
[Telegram](https://t.me/verifiedharyanvi)