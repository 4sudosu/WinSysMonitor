import { WebSocketServer } from 'ws';
import express from 'express';
import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3001);
const HOST = process.env.HOST || '0.0.0.0';
const APP_DIR = __dirname;
// Admin password from config file (written by Android app) or environment variable
let ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'Alok@1234';

function loadAdminPassword() {
  try {
    const configPath = path.join(APP_DIR, 'server.config.json');
    const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    if (config.adminPassword) {
      ADMIN_PASSWORD = config.adminPassword;
      console.log('[INFO] Loaded admin password from config file');
    }
  } catch (e) {
    console.log('[INFO] No admin password in config file, using default/env');
  }
}

// Load admin password at startup
loadAdminPassword();
// Login brute-force protection: after N bad passwords the dashboard locks
// until the process restarts (in-memory flag — a Render restart clears it).
const MAX_LOGIN_ATTEMPTS = 3;
let failedLoginCount = 0;
let loginLocked = false;

const AGENTS_FILE = path.join(APP_DIR, 'agents.json');
const BLOCKED_DEVICES_FILE = path.join(APP_DIR, 'blocked_devices.json');
const SERVER_VERSION = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(APP_DIR, 'package.json'), 'utf8')).version; }
  catch { return '1.0.0'; }
})();

// ── helpers ──────────────────────────────────────────────────────────────
const makeId = () => crypto.randomBytes(8).toString('hex');

const SESSION_SECRET = crypto.randomBytes(32).toString('hex');
const SESSION_TTL = 7 * 24 * 60 * 60 * 1000;
const SESSION_COOKIE = 'wsm_auth';
const sessions = new Map(); // token -> expiry

// ── Device blocking ──────────────────────────────────────────────────────
const MAX_DEVICE_ATTEMPTS = 3;
// PERMANENT LOCKOUT - no auto-unlock, only manual unlock from dashboard
const LOCKOUT_DURATION_MS = 0; // 0 = permanent until manual unlock

function readBlockedDevices() {
  try { return JSON.parse(fs.readFileSync(BLOCKED_DEVICES_FILE, 'utf8')); }
  catch { return {}; }
}

function writeBlockedDevices(data) {
  try { fs.writeFileSync(BLOCKED_DEVICES_FILE, JSON.stringify(data, null, 2)); }
  catch (e) { console.warn('Could not write blocked_devices.json:', e.message); }
}

function isDeviceBlocked(deviceId) {
  const blocked = readBlockedDevices();
  const entry = blocked[deviceId];
  if (!entry) return false;
  // Permanent block - only manual unlock clears it
  if (entry.locked) return true;
  return false;
}

function recordFailedAttempt(deviceId) {
  console.log('[DEBUG recordFailedAttempt] Called for device:', deviceId);
  const blocked = readBlockedDevices();
  const entry = blocked[deviceId] || { attempts: 0, locked: false };
  entry.attempts = (entry.attempts || 0) + 1;
  console.log('[DEBUG recordFailedAttempt] Attempts:', entry.attempts);
  if (entry.attempts >= MAX_DEVICE_ATTEMPTS && !entry.locked) {
    entry.locked = true;
    entry.lockedAt = Date.now();
    console.log('[DEBUG recordFailedAttempt] Device LOCKED:', deviceId);
  }
  blocked[deviceId] = entry;
  writeBlockedDevices(blocked);
  console.log('[DEBUG recordFailedAttempt] Written to file:', JSON.stringify(readBlockedDevices(), null, 2));
  return entry;
}

function unlockDevice(deviceId) {
  const blocked = readBlockedDevices();
  delete blocked[deviceId];
  writeBlockedDevices(blocked);
}

function getBlockedDevices() {
  return readBlockedDevices();
}

function signSession() {
  const token = crypto.randomBytes(32).toString('hex');
  sessions.set(token, Date.now() + SESSION_TTL);
  return token;
}
function isAuthed(req) {
  const given = String((req.headers['x-admin-password'] || '')).trim();
  if (given && given === ADMIN_PASSWORD) return true;
  const cookie = String(req.headers['cookie'] || '');
  const match = cookie.match(new RegExp('(?:^|;\\s*)' + SESSION_COOKIE + '=([^;]+)'));
  if (!match) return false;
  const expiry = sessions.get(match[1]);
  if (!expiry) return false;
  if (Date.now() > expiry) { sessions.delete(match[1]); return false; }
  return true;
}
function requireAuth(req, res, next) {
  if (isAuthed(req)) return next();
  if (req.path.startsWith('/api/')) {
    return res.status(401).json({ error: 'unauthorized' });
  }
  return res.redirect('/login');
}

function readAgentsFile() {
  try { return JSON.parse(fs.readFileSync(AGENTS_FILE, 'utf8')); }
  catch { return []; }
}
function saveAgentsFile(agents) {
  try { fs.writeFileSync(AGENTS_FILE, JSON.stringify(agents, null, 2)); }
  catch (e) { console.warn('Could not write agents.json:', e.message); }
}

// ── Agent registry ───────────────────────────────────────────────────────
const agents = new Map(); // machineName -> { ws, info, lastSeen }

function upsertRegistry(info) {
  const list = readAgentsFile();
  const idx = list.findIndex(a => (a.machineName || '').toLowerCase() === (info.machineName || '').toLowerCase());
  const record = { ...info, lastSeen: new Date().toISOString() };
  if (idx >= 0) list[idx] = record;
  else list.push(record);
  saveAgentsFile(list);
}

// ── WebSocket hub ────────────────────────────────────────────────────────
const app = express();
app.use(express.json({ limit: '200mb' }));

// ── public routes ────────────────────────────────────────────────────────
app.get('/login', (req, res) => {
  if (isAuthed(req)) return res.redirect('/');
  res.sendFile(path.join(APP_DIR, 'dashboard', 'login.html'));
});

// Public static asset for the login page — must NOT go through requireAuth,
// otherwise the browser gets redirected instead of the CSS and /login renders unstyled.
app.get('/style.css', (req, res) => {
  res.sendFile(path.join(APP_DIR, 'dashboard', 'style.css'));
});

app.post('/api/login', (req, res) => {
  if (loginLocked) {
    return res.status(423).json({
      error: 'locked',
      message: 'Server locked after too many failed attempts. Restart the server to unlock.'
    });
  }
  const given = String((req.body && req.body.password) || '');
  if (given !== ADMIN_PASSWORD) {
    failedLoginCount += 1;
    if (failedLoginCount >= MAX_LOGIN_ATTEMPTS) {
      loginLocked = true;
      return res.status(423).json({
        error: 'locked',
        message: 'Server locked after too many failed attempts. Restart the server to unlock.'
      });
    }
    return res.status(403).json({
      error: 'Invalid password',
      attemptsLeft: MAX_LOGIN_ATTEMPTS - failedLoginCount
    });
  }
  failedLoginCount = 0;
  const token = signSession();
  res.setHeader('Set-Cookie', `${SESSION_COOKIE}=${token}; Path=/; HttpOnly; Max-Age=${Math.floor(SESSION_TTL / 1000)}`);
  res.json({ ok: true });
});

app.post('/api/logout', (req, res) => {
  const cookie = String(req.headers['cookie'] || '');
  const match = cookie.match(new RegExp('(?:^|;\\s*)' + SESSION_COOKIE + '=([^;]+)'));
  if (match) sessions.delete(match[1]);
  res.setHeader('Set-Cookie', `${SESSION_COOKIE}=; Path=/; HttpOnly; Max-Age=0`);
  res.json({ ok: true });
});

// Connection probe is public so wrong device passwords are recorded before
// the dashboard authentication middleware can reject the request.
app.get('/api/config', (req, res) => {
  const deviceId = String(req.headers['x-device-id'] || '').trim();
  const adminPass = String(req.headers['x-admin-password'] || '');
  const base = {
    host: HOST,
    port: PORT,
    version: SERVER_VERSION,
    url: `http://${req.hostname || HOST}:${PORT}`,
    agents: agents.size
  };

  if (deviceId && isDeviceBlocked(deviceId)) {
    return res.json({ ...base, deviceBlocked: true, unlockAt: 0, authError: false });
  }
  if (deviceId && adminPass && adminPass !== ADMIN_PASSWORD) {
    const entry = recordFailedAttempt(deviceId);
    console.log('[DEVICE AUTH] failed attempt', deviceId, entry.attempts);
    if (entry.locked) {
      return res.json({ ...base, deviceBlocked: true, unlockAt: 0, authError: true });
    }
    return res.json({ ...base, deviceBlocked: false, unlockAt: 0, authError: true });
  }
  return res.json({ ...base, deviceBlocked: false, unlockAt: 0, authError: false });
});

app.get('/api/device-status', (req, res) => {
  const deviceId = String(req.headers['x-device-id'] || '').trim();
  if (!deviceId) return res.status(400).json({ error: 'deviceId required' });
  res.json({ deviceBlocked: isDeviceBlocked(deviceId) });
});

// ── gated area (dashboard + API) ─────────────────────────────────────────
app.use(requireAuth);
app.use(express.static(path.join(APP_DIR, 'dashboard')));

const server = app.listen(PORT, HOST, () => {
  console.log(`WinSysMonitor server running at: http://${HOST}:${PORT}`);
});

const wss = new WebSocketServer({ server, path: '/ws/agent' });
const pending = new Map(); // taskId -> { resolve }

// ── SSE events (live dashboard notifications) ────────────────────────────
const sseClients = new Set();

function broadcastSSE(event, data) {
  const payload = `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const res of sseClients) {
    try { res.write(payload); } catch { sseClients.delete(res); }
  }
}

app.get('/api/events', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no'
  });
  res.write('retry: 3000\n\n');
  sseClients.add(res);
  const keep = setInterval(() => { try { res.write(': ping\n\n'); } catch { /* noop */ } }, 25000);
  req.on('close', () => { clearInterval(keep); sseClients.delete(res); });
});

wss.on('connection', (ws, req) => {
  const url = new URL(req.url, 'http://localhost');
  const token = url.searchParams.get('token') || '';
  if (!token) {
    ws.close(4001, 'Unauthorized');
    return;
  }

  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data) => {
    let msg;
    try { msg = JSON.parse(data.toString()); } catch { return; }
    handleMessage(ws, msg);
  });

  ws.on('close', () => {
    const machineName = ws.machineName;
    if (machineName) {
      agents.delete(machineName);
      broadcastSSE('agent-offline', { machineName });
      console.log(`[AGENT OFFLINE] ${machineName}`);
    }
  });

  ws.on('error', () => {});
  ws.send(JSON.stringify({ type: 'hello', message: 'connected' }));
});

function handleMessage(ws, msg) {
  switch (msg.type) {
    case 'register': {
      const machineName = (msg.machineName || '').toLowerCase();
      ws.machineName = machineName;
      const info = {
        machineName,
        hostname: msg.hostname || machineName,
        model: msg.model || '',
        serial: msg.serial || '',
        username: msg.username || msg.user || '',
        os: msg.os || '',
        user: msg.user || '',
        version: msg.version || '',
        ip: msg.ip || ''
      };
      ws.info = info;
      agents.set(machineName, { ws, info, lastSeen: Date.now() });
      upsertRegistry(info);
      broadcastSSE('agent-online', {
        machineName,
        hostname: info.hostname,
        model: info.model,
        ip: info.ip,
        serial: info.serial
      });
      console.log(`[AGENT ONLINE] ${machineName} | ${info.model} | ${info.username} | ${info.ip}`);
      ws.send(JSON.stringify({ type: 'registered', machineName }));
      break;
    }
    case 'result': {
      const task = pending.get(msg.taskId);
      if (task) {
        pending.delete(msg.taskId);
        task.resolve(msg);
      }
      break;
    }
  }
}

// ── HTTP API ─────────────────────────────────────────────────────────────
app.get('/api/agents', (req, res) => {
  const q = String(req.query.q || '').toLowerCase();
  const list = [];
  for (const [machineName, agent] of agents) {
    if (agent.ws.readyState !== agent.ws.OPEN) continue;
    const { info } = agent;
    if (q && ![info.hostname, machineName, info.serial, info.ip, info.model]
      .some(v => String(v || '').toLowerCase().includes(q))) continue;
    list.push({ ...info, online: true, lastSeen: new Date().toISOString() });
  }
  res.json(list);
});

app.post('/api/monitor/:machineName/screenshot', async (req, res) => {
  // Authenticated via requireAuth (cookie session or X-Admin-Password header).
  const agent = agents.get((req.params.machineName || '').toLowerCase());
  if (!agent || agent.ws.readyState !== agent.ws.OPEN) {
    return res.status(409).json({ success: false, error: 'Agent offline' });
  }

  const taskId = makeId();
  try {
    const result = await new Promise((resolve) => {
      const timer = setTimeout(() => {
        if (pending.has(taskId)) {
          pending.delete(taskId);
          resolve({ success: false, error: 'TIMEOUT' });
        }
      }, 35000);
      pending.set(taskId, {
        resolve: (r) => { clearTimeout(timer); resolve(r); }
      });
      agent.ws.send(JSON.stringify({ type: 'capture_screenshot', taskId }));
    });

    if (!result.success) {
      const err = result.error === 'AGENT_OFFLINE' ? 'Agent offline' : (result.error || 'Capture failed');
      return res.status(result.error === 'AGENT_OFFLINE' ? 409 : 500).json({ success: false, error: err });
    }
    console.log(`[SCREENSHOT] ${req.params.machineName}`);
    res.json({ success: true, image: result.output, at: new Date().toISOString() });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.post('/api/shutdown', (req, res) => {
  // Authenticated via requireAuth (cookie session or X-Admin-Password header).
  console.log('[SHUTDOWN] requested - stopping server');
  try { res.json({ success: true }); } catch { /* noop */ }
  // Close every handle so the event loop drains and node exits cleanly.
  // (process.exit is intentionally swallowed by the embedded bootstrap.)
  setTimeout(() => {
    try { for (const c of sseClients) { try { c.end(); } catch { /* noop */ } } sseClients.clear(); } catch { /* noop */ }
    try { clearInterval(interval); } catch { /* noop */ }
    try {
      for (const [, agent] of agents) { try { agent.ws.close(); } catch { /* noop */ } }
      agents.clear();
    } catch { /* noop */ }
    try { wss.close(); } catch { /* noop */ }
    try { server.close(); } catch { /* noop */ }
    if (typeof server.closeAllConnections === 'function') { try { server.closeAllConnections(); } catch { /* noop */ } }
  }, 200);
});

app.get('/api/health', (req, res) => {
  res.json({ ok: true, agents: agents.size, version: SERVER_VERSION });
});

// ── Device blocking API ──────────────────────────────────────────────────
app.get('/api/admin/blocked-devices', (req, res) => {
  res.json(getBlockedDevices());
});

app.post('/api/admin/unlock-device', (req, res) => {
  const { deviceId } = req.body;
  if (!deviceId) {
    return res.status(400).json({ error: 'deviceId required' });
  }
  unlockDevice(deviceId);
  res.json({ success: true, message: `Device ${deviceId} unlocked` });
});

app.post('/api/admin/record-failed-attempt', (req, res) => {
  const { deviceId } = req.body;
  if (!deviceId) {
    return res.status(400).json({ error: 'deviceId required' });
  }
  const entry = recordFailedAttempt(deviceId);
  res.json({ success: true, entry });
});

// ── heartbeat ────────────────────────────────────────────────────────────
const interval = setInterval(() => {
  for (const [machineName, agent] of agents) {
    if (!agent.ws.isAlive) {
      agent.ws.terminate();
      agents.delete(machineName);
      continue;
    }
    agent.ws.isAlive = false;
    try { agent.ws.ping(); } catch { agents.delete(machineName); }
  }
}, 30000);

wss.on('close', () => clearInterval(interval));

console.log(`WinSysMonitor WebSocket hub ready at /ws/agent`);
