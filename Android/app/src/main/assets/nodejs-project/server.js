import { WebSocketServer } from 'ws';
import express from 'express';
import path from 'node:path';
import fs from 'node:fs';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3001);
const HOST = process.env.HOST || '0.0.0.0';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || '.\\itdtpadmin';

const APP_DIR = __dirname;
const AGENTS_FILE = path.join(APP_DIR, 'agents.json');
const SERVER_VERSION = (() => {
  try { return JSON.parse(fs.readFileSync(path.join(APP_DIR, 'package.json'), 'utf8')).version; }
  catch { return '1.0.0'; }
})();

// ── helpers ──────────────────────────────────────────────────────────────
const makeId = () => crypto.randomBytes(8).toString('hex');

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
  const expected = ADMIN_PASSWORD;
  const given = String((req.body && req.body.password) || req.headers['x-admin-password'] || '');
  if (given !== expected) {
    console.warn(`[SCREENSHOT DENIED] ${req.params.machineName} — invalid admin password`);
    return res.status(403).json({ success: false, error: 'Invalid admin password' });
  }

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

app.get('/api/health', (req, res) => {
  res.json({ ok: true, agents: agents.size, version: SERVER_VERSION });
});

app.get('/api/config', (req, res) => {
  res.json({
    host: HOST,
    port: PORT,
    version: SERVER_VERSION,
    url: `http://${req.hostname || HOST}:${PORT}`,
    agents: agents.size
  });
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