# WinSysMonitor

Monitor Windows machines from your phone. A lightweight agent runs on each PC as a 24x7 service, connects to the server over WebSocket, and lets you grab live screenshots from the Android app or the web dashboard.

## Downloads (`apps/`)

| File | What it is |
|------|------------|
| `WinSysMonitor-Agent-Setup.exe` | Windows agent installer (installs a background service) |
| `WinSysMonitor-latest.apk` | Android app (host or connect to a server) |

## Run the server

Requires [Node.js](https://nodejs.org) 18+.

```bash
cd server
npm install
# set your own dashboard password first!
set ADMIN_PASSWORD=YourSecretPassword     (Windows)
export ADMIN_PASSWORD=YourSecretPassword  (Linux/macOS)
node server.js
```

Dashboard: `http://localhost:3001/login`

- `ADMIN_PASSWORD` — dashboard login password (**always set your own**; default is `changeme`)
- `PORT` — server port (default `3001`)
- `HOST` — bind address (default `0.0.0.0`)

### Deploy free (Render.com)

1. Push the `server/` folder to your repo → new Web Service
2. Build command: `npm install` · Start command: `node server.js`
3. Add environment variable `ADMIN_PASSWORD` = your secret password
4. Agents connect to `wss://your-app.onrender.com/ws/agent`, port `443`

## Install the agent

Run `WinSysMonitor-Agent-Setup.exe` and enter:

- **Server IP / hostname** — e.g. `192.168.1.50` or `your-app.onrender.com`
- **Port** — `3001` for LAN, `443` for Render (TLS is used automatically on 443)
- **Agent token** — shared token agents use to register with the server

The agent installs into `C:\Program Files\WinSysMonitor`, registers an auto-start Windows service with crash recovery, and reconnects automatically.

> The setup wizard asks for these values during install, or run silently:
> `WinSysMonitor-Agent-Setup.exe /VERYSILENT /SERVERIP=host /SERVERPORT=3001 /SERVERTOKEN=token`

## Android app

- **Host mode** — runs the whole server inside the app on your phone (agents connect to the phone's IP)
- **Connect mode** — point it at any running WinSysMonitor server (enter host + port + admin password)

Features: live device list, screenshot capture, auto-refresh (3s/5s/10s), save/copy/share, connection alerts with custom sounds & icons, themes.

## Security notes

- The web dashboard locks itself after 3 wrong password attempts until the server restarts.
- Agent config is locked to SYSTEM/Administrators only.
- Use strong passwords; never expose the server without setting `ADMIN_PASSWORD`.

---

Built by [4sudo.su](https://github.com/4sudosu) · Telegram [@verifiedharyanvi](https://t.me/verifiedharyanvi)
