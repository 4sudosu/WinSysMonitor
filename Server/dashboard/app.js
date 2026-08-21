// ── helpers ─────────────────────────────────────────────────────────────
const $ = (id) => document.getElementById(id);
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

const LS = {
  theme: 'wsm-theme',
  tone: 'wsm-tone',
  notify: 'wsm-notify'
};
const getLS = (k, d) => { try { return localStorage.getItem(k) ?? d; } catch { return d; } };
const setLS = (k, v) => { try { localStorage.setItem(k, v); } catch { } };

// ── tabs ────────────────────────────────────────────────────────────────
function switchTab(name) {
  document.querySelectorAll('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach(p => p.classList.toggle('active', p.id === 'panel-' + name));
}

document.getElementById('tabbar').addEventListener('click', e => {
  const tab = e.target.closest('.tab');
  if (tab) switchTab(tab.dataset.tab);
});

// ── themes (10 combos) ──────────────────────────────────────────────────
const THEMES = {
  ocean: {
    name: 'Midnight Ocean', bg: '#0b1120', bg2: '#0f172a', soft: '#111a2e',
    card: '#131d31', card2: '#172238', border: '#1e293b', border2: '#2b3a55',
    text: '#f8fafc', muted: '#94a3b8', dim: '#64748b',
    primary: '#3b82f6', green: '#10b981', amber: '#f59e0b', red: '#ef4444', cyan: '#22d3ee',
    glass: 'rgba(255,255,255,0.055)', glassBorder: 'rgba(255,255,255,0.12)',
    a: 'rgba(59,130,246,0.4)', b: 'rgba(34,211,238,0.32)', c: 'rgba(16,185,129,0.3)'
  },
  aurora: {
    name: 'Aurora', bg: '#050e1f', bg2: '#0a1f2e', soft: '#0d2231',
    card: '#10283a', card2: '#143042', border: '#1b3b4f', border2: '#2a5368',
    text: '#f0fdfa', muted: '#9fb6c5', dim: '#5f7a8a',
    primary: '#2dd4bf', green: '#34d399', amber: '#fbbf24', red: '#fb7185', cyan: '#22d3ee',
    glass: 'rgba(45,212,191,0.06)', glassBorder: 'rgba(45,212,191,0.16)',
    a: 'rgba(16,185,129,0.4)', b: 'rgba(45,212,191,0.32)', c: 'rgba(167,139,250,0.3)'
  },
  flare: {
    name: 'Solar Flare', bg: '#1a0f07', bg2: '#23120a', soft: '#2a160c',
    card: '#331b0e', card2: '#3d2212', border: '#4a2c16', border2: '#6b4221',
    text: '#fff7ed', muted: '#d4b493', dim: '#94714d',
    primary: '#f97316', green: '#4ade80', amber: '#fbbf24', red: '#f87171', cyan: '#facc15',
    glass: 'rgba(249,115,22,0.07)', glassBorder: 'rgba(249,115,22,0.2)',
    a: 'rgba(249,115,22,0.38)', b: 'rgba(251,191,36,0.3)', c: 'rgba(248,113,113,0.25)'
  },
  cyber: {
    name: 'Cyberpunk', bg: '#120018', bg2: '#0d0616', soft: '#1a0b24',
    card: '#1f0d2e', card2: '#271040', border: '#3a1656', border2: '#5c2187',
    text: '#fdf4ff', muted: '#c4a7d9', dim: '#8b6aa3',
    primary: '#e879f9', green: '#22d3ee', amber: '#facc15', red: '#fb7185', cyan: '#22d3ee',
    glass: 'rgba(232,121,249,0.07)', glassBorder: 'rgba(232,121,249,0.18)',
    a: 'rgba(232,121,249,0.42)', b: 'rgba(34,211,238,0.3)', c: 'rgba(250,204,21,0.25)'
  },
  royal: {
    name: 'Royal Purple', bg: '#120b1e', bg2: '#18102b', soft: '#1b1230',
    card: '#201543', card2: '#2a1a56', border: '#3b2670', border2: '#5a3c9e',
    text: '#faf5ff', muted: '#c2b0e0', dim: '#8b78ad',
    primary: '#a78bfa', green: '#34d399', amber: '#fbbf24', red: '#f87171', cyan: '#67e8f9',
    glass: 'rgba(167,139,250,0.07)', glassBorder: 'rgba(167,139,250,0.18)',
    a: 'rgba(167,139,250,0.42)', b: 'rgba(251,191,36,0.28)', c: 'rgba(52,211,153,0.28)'
  },
  forest: {
    name: 'Forest Calm', bg: '#07130c', bg2: '#0a1c12', soft: '#0d2216',
    card: '#102a1b', card2: '#143421', border: '#1d452b', border2: '#2b663f',
    text: '#ecfdf5', muted: '#9dc9ae', dim: '#5f8a70',
    primary: '#34d399', green: '#4ade80', amber: '#fbbf24', red: '#f87171', cyan: '#2dd4bf',
    glass: 'rgba(52,211,153,0.06)', glassBorder: 'rgba(52,211,153,0.16)',
    a: 'rgba(52,211,153,0.38)', b: 'rgba(45,212,191,0.3)', c: 'rgba(74,222,128,0.28)'
  },
  slate: {
    name: 'Slate Pro', bg: '#0d1117', bg2: '#161b22', soft: '#161b22',
    card: '#1b212a', card2: '#222933', border: '#2d333b', border2: '#444c56',
    text: '#e6edf3', muted: '#9aa7b4', dim: '#6e7681',
    primary: '#58a6ff', green: '#3fb950', amber: '#d29922', red: '#f85149', cyan: '#39c5cf',
    glass: 'rgba(255,255,255,0.05)', glassBorder: 'rgba(255,255,255,0.1)',
    a: 'rgba(88,166,255,0.32)', b: 'rgba(57,197,207,0.28)', c: 'rgba(63,185,80,0.24)'
  },
  paper: {
    name: 'Paper Light', bg: '#eef1f6', bg2: '#e2e8f0', soft: '#f8fafc',
    card: '#ffffff', card2: '#f1f5f9', border: '#e2e8f0', border2: '#cbd5e1',
    text: '#0f172a', muted: '#64748b', dim: '#94a3b8',
    primary: '#2563eb', green: '#059669', amber: '#d97706', red: '#dc2626', cyan: '#0891b2',
    glass: 'rgba(255,255,255,0.65)', glassBorder: 'rgba(15,23,42,0.08)',
    a: 'rgba(37,99,235,0.25)', b: 'rgba(8,145,178,0.2)', c: 'rgba(5,150,105,0.18)'
  },
  mint: {
    name: 'Mint Fresh', bg: '#e8f7f0', bg2: '#d9f2e5', soft: '#f2fbf6',
    card: '#ffffff', card2: '#ecfaf2', border: '#cdeadd', border2: '#a8d9c0',
    text: '#06351f', muted: '#3e7a5d', dim: '#6aa68a',
    primary: '#10b981', green: '#059669', amber: '#d97706', red: '#dc2626', cyan: '#06b6d4',
    glass: 'rgba(255,255,255,0.7)', glassBorder: 'rgba(6,78,59,0.1)',
    a: 'rgba(16,185,129,0.28)', b: 'rgba(6,182,212,0.22)', c: 'rgba(217,119,6,0.16)'
  },
  blush: {
    name: 'Rose Blush', bg: '#faf0f3', bg2: '#f3e2ea', soft: '#fdf6f8',
    card: '#ffffff', card2: '#fbeef3', border: '#f0d8e2', border2: '#e2bccb',
    text: '#4a1020', muted: '#a05c74', dim: '#c491a3',
    primary: '#ec4899', green: '#059669', amber: '#d97706', red: '#e11d48', cyan: '#0891b2',
    glass: 'rgba(255,255,255,0.7)', glassBorder: 'rgba(159,18,57,0.1)',
    a: 'rgba(236,72,153,0.24)', b: 'rgba(225,29,72,0.16)', c: 'rgba(139,92,246,0.16)'
  }
};

function applyTheme(key) {
  const t = THEMES[key] || THEMES.ocean;
  const r = document.documentElement.style;
  const map = {
    '--bg': t.bg, '--bg-2': t.bg2, '--bg-soft': t.soft,
    '--card': t.card, '--card-2': t.card2,
    '--border': t.border, '--border-2': t.border2,
    '--text': t.text, '--muted': t.muted, '--dim': t.dim,
    '--primary': t.primary, '--green': t.green, '--amber': t.amber, '--red': t.red, '--cyan': t.cyan,
    '--glass': t.glass, '--glass-border': t.glassBorder,
    '--blob-a': t.a, '--blob-b': t.b, '--blob-c': t.c
  };
  for (const [k, v] of Object.entries(map)) r.setProperty(k, v);
  document.querySelectorAll('.theme-item').forEach(el => {
    el.classList.toggle('active', el.dataset.theme === key);
  });
  const badge = $('themeNameBadge');
  if (badge) badge.textContent = t.name;
  setLS(LS.theme, key);
}

function buildThemeGrid() {
  const grid = $('themeGrid');
  if (!grid) return;
  grid.innerHTML = Object.entries(THEMES).map(([key, t]) => `
    <button class="theme-item" data-theme="${key}" title="${esc(t.name)}">
      <span class="theme-swatch">
        <i style="background:${t.bg}"></i>
        <i style="background:${t.card}"></i>
        <i style="background:${t.primary}"></i>
      </span>
      <span>${esc(t.name)}</span>
    </button>
  `).join('');
  grid.addEventListener('click', e => {
    const item = e.target.closest('.theme-item');
    if (item) applyTheme(item.dataset.theme);
  });
}

// ── notification tones ──────────────────────────────────────────────────
const TONES = {
  chime:   [{ f: 880, t: 0, d: 0.2 }, { f: 1108, t: 0.22, d: 0.2 }, { f: 1318, t: 0.44, d: 0.38 }],
  ding:    [{ f: 1046, t: 0, d: 0.55 }],
  digital: [{ f: 1318, t: 0, d: 0.1 }, { f: 1318, t: 0.15, d: 0.25 }],
  alert:   [{ f: 659, t: 0, d: 0.16 }, { f: 494, t: 0.22, d: 0.32 }],
  soft:    [{ f: 523, t: 0, d: 0.2 }, { f: 659, t: 0.24, d: 0.3 }],
  none:    []
};
let audioCtx = null;

function playTone(name) {
  const notes = TONES[name];
  if (!notes || !notes.length) return;
  try {
    if (!audioCtx) audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    if (audioCtx.state === 'suspended') audioCtx.resume();
    const wave = name === 'digital' ? 'square' : name === 'alert' ? 'sawtooth' : 'sine';
    notes.forEach(n => {
      const o = audioCtx.createOscillator();
      const g = audioCtx.createGain();
      o.type = wave;
      o.frequency.value = n.f;
      const t0 = audioCtx.currentTime + n.t;
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.linearRampToValueAtTime(0.05, t0 + 0.02);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + n.d);
      o.connect(g);
      g.connect(audioCtx.destination);
      o.start(t0);
      o.stop(t0 + n.d + 0.05);
    });
  } catch { }
}

// ── toast ───────────────────────────────────────────────────────────────
let toastTimer = null;
function showToast(title, msg) {
  const toast = $('toast');
  toast.hidden = false;
  toast.classList.remove('hide');
  $('toastTitle').textContent = title;
  $('toastMsg').textContent = msg;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(dismissToast, 6000);
}
function dismissToast() {
  const toast = $('toast');
  if (toast.hidden) return;
  toast.classList.add('hide');
  setTimeout(() => { toast.hidden = true; }, 320);
}

// ── notifications ───────────────────────────────────────────────────────
let notifyEnabled = getLS(LS.notify, 'on') === 'on';

function notifyAgentOnline(info) {
  const host = info.hostname || info.machineName || 'Unknown device';
  const detail = [info.model, info.ip].filter(Boolean).join(' · ');
  showToast(`📡 ${host} connected`, detail || 'New device connected');
  playTone($('toneSelect').value);

  if (notifyEnabled && 'Notification' in window) {
    if (Notification.permission === 'granted') {
      try {
        const n = new Notification(`📡 ${host} connected`, { body: detail, tag: 'agent-online' });
        n.onclick = () => { window.focus(); n.close(); };
      } catch { }
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission();
    }
  }
}

function initSettings() {
  // theme
  buildThemeGrid();
  applyTheme(getLS(LS.theme, 'ocean'));

  // tone
  $('toneSelect').value = getLS(LS.tone, 'chime');
  $('toneSelect').addEventListener('change', () => setLS(LS.tone, $('toneSelect').value));
  $('testToneBtn').addEventListener('click', () => playTone($('toneSelect').value));

  // notify toggle
  $('notifyToggle').checked = notifyEnabled;
  $('notifyToggle').addEventListener('change', async () => {
    notifyEnabled = $('notifyToggle').checked;
    setLS(LS.notify, notifyEnabled ? 'on' : 'off');
    if (notifyEnabled && 'Notification' in window && Notification.permission === 'default') {
      const p = await Notification.requestPermission();
      if (p === 'granted') showToast('🔔 Notifications enabled', 'You will be alerted when agents connect.');
    }
  });
}

// ── server info ─────────────────────────────────────────────────────────
function loadServerInfo() {
  fetch('/api/config')
    .then(r => { if (handleAuthResponse(r)) return null; return r.json(); })
    .then(cfg => {
      if (!cfg) return;
      $('cfgUrl').textContent = cfg.url || '—';
      $('cfgHost').textContent = cfg.host || '—';
      $('cfgPort').textContent = cfg.port || '—';
      $('cfgVersion').textContent = cfg.version || '—';
      $('cfgAgents').textContent = cfg.agents ?? '—';
      $('footerServer').textContent = cfg.url ? `Serving from ${cfg.url}` : '';
    })
    .catch(() => { });
}

// ── live events (SSE) ───────────────────────────────────────────────────
let notifiedAgents = new Set();
function initEvents() {
  const es = new EventSource('/api/events');
  es.addEventListener('agent-online', e => {
    try {
      const info = JSON.parse(e.data);
      if (notifiedAgents.has(info.machineName)) return;
      notifiedAgents.add(info.machineName);
      notifyAgentOnline(info);
      loadMonitor();
    } catch { }
  });
  es.addEventListener('agent-offline', e => {
    try {
      const info = JSON.parse(e.data);
      notifiedAgents.delete(info.machineName);
      loadMonitor();
    } catch { }
  });
  es.onerror = () => { /* EventSource reconnects automatically */ };
}

// ── device polling ───────────────────────────────────────────────────────
let monitorSearchTimer = null;

function loadMonitor() {
  const q = encodeURIComponent($('deviceSearch').value.trim());
  fetch(`/api/agents${q ? '?q=' + q : ''}`)
    .then(r => { if (handleAuthResponse(r)) return null; return r.json(); })
    .then(agents => {
      if (!agents) return;
      const online = agents.filter(a => a.online).length;
      $('agentsHeader').textContent = agents.length ? `● ${online} online · ${agents.length} devices` : '● No devices connected';
      $('agentsSummary').textContent = agents.length
        ? `${agents.length} device(s) · ${online} online${q ? '  ·  filtered by "' + $('deviceSearch').value.trim() + '"' : ''}`
        : q ? 'No devices match that search.' : 'No devices registered yet.';
      const tbody = $('monitorBody');
      if (!agents.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty-row">No devices registered.</td></tr>`;
        return;
      }
      tbody.innerHTML = agents.map(a => `
        <tr>
          <td style="font-weight:700;">${esc(a.hostname || a.machineName)}</td>
          <td style="font-family:Consolas,monospace;">${esc(a.ip) || '—'}</td>
          <td style="font-family:Consolas,monospace;">${esc(a.serial) || '—'}</td>
          <td style="font-family:Consolas,monospace;">${esc(a.version) || '—'}</td>
          <td>${esc(a.model) || '—'}</td>
          <td><span class="badge ${a.online ? 'badge-resolved' : 'badge-offline'}">${a.online ? '● Online' : '● Offline'}</span></td>
          <td style="text-align:right;white-space:nowrap;">
            ${a.online
              ? `<button class="btn-fix" onclick="openMonitor('${esc(a.machineName)}','${esc(a.hostname || a.machineName)}')">📸 Capture</button>`
              : '<span class="muted">—</span>'}
          </td>
        </tr>
      `).join('');
    })
    .catch(() => {});
}

$('deviceSearch').addEventListener('input', () => {
  clearTimeout(monitorSearchTimer);
  monitorSearchTimer = setTimeout(loadMonitor, 300);
});

// ── screen capture viewer ────────────────────────────────────────────────
let monitorPassword = sessionStorage.getItem('monitorPassword') || '';
let monitorTarget = null;
let monitorTimer = null;
let monitorImageB64 = null;

function monitorPasswordPrompt() {
  const p = prompt('Enter the admin password to capture screenshots:') || '';
  if (!p) return null;
  monitorPassword = p;
  sessionStorage.setItem('monitorPassword', p);
  return p;
}

function openMonitor(machineName, hostname) {
  monitorTarget = { machineName, hostname };
  $('monitorModalTitle').textContent = `📷 ${hostname}`;
  $('monitorPlaceholder').style.display = 'block';
  $('monitorImg').style.display = 'none';
  $('monitorCaptureInfo').textContent = '—';
  $('monitorRefreshSel').value = '0';
  $('monitorModal').style.display = 'flex';
}

function closeMonitorModal() {
  $('monitorModal').style.display = 'none';
  stopMonitorRefresh();
}

$('monitorModal').addEventListener('click', e => { if (e.target === $('monitorModal')) closeMonitorModal(); });

function captureMonitorNow() {
  if (!monitorTarget) return;
  if (!monitorPassword) {
    const p = monitorPasswordPrompt();
    if (!p) return;
  }
  $('monitorCaptureInfo').textContent = '📸 Capturing…';
  fetch(`/api/monitor/${encodeURIComponent(monitorTarget.machineName)}/screenshot`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: monitorPassword })
  })
    .then(r => r.json().then(d => ({ ok: r.ok, d })))
    .then(({ ok, d }) => {
      if (!ok) {
        if (d.error && d.error.includes('password')) {
          monitorPassword = '';
          sessionStorage.removeItem('monitorPassword');
          const p = monitorPasswordPrompt();
          if (!p) return;
          return captureMonitorNow();
        }
        $('monitorCaptureInfo').textContent = '❌ ' + (d.error || 'Capture failed');
        $('monitorPlaceholder').style.display = 'block';
        $('monitorImg').style.display = 'none';
        return;
      }
      monitorImageB64 = d.image;
      const img = $('monitorImg');
      img.src = 'data:image/png;base64,' + d.image;
      $('monitorPlaceholder').style.display = 'none';
      img.style.display = 'block';
      $('monitorCaptureInfo').textContent = `Captured ${d.at ? new Date(d.at).toLocaleTimeString() : 'now'}`;
    })
    .catch(() => { $('monitorCaptureInfo').textContent = '❌ Could not reach the server.'; });
}

function scheduleMonitorRefresh() {
  stopMonitorRefresh();
  const secs = parseInt($('monitorRefreshSel').value, 10) || 0;
  if (secs > 0 && monitorTarget) monitorTimer = setInterval(captureMonitorNow, secs * 1000);
}

function stopMonitorRefresh() {
  if (monitorTimer) { clearInterval(monitorTimer); monitorTimer = null; }
}

$('monitorRefreshSel').addEventListener('change', scheduleMonitorRefresh);

async function copyMonitorImage() {
  let img = $('monitorImg');
  const hasImage = !!(monitorImageB64 || (img && img.src && img.src.startsWith('data:image/')));

  if (!hasImage) {
    $('monitorCaptureInfo').textContent = '📸 No screenshot yet — capturing now…';
    await captureMonitorNow();
    img = $('monitorImg');
  }

  let dataUrl = img && img.src && img.src.startsWith('data:image/')
    ? img.src
    : (monitorImageB64 ? 'data:image/png;base64,' + monitorImageB64 : null);

  if (!dataUrl) {
    $('monitorCaptureInfo').textContent = '⚠️ No screenshot available to copy';
    return;
  }

  if (!monitorImageB64 && dataUrl.startsWith('data:image/')) {
    monitorImageB64 = dataUrl.slice(dataUrl.indexOf(',') + 1);
  }

  try {
    if (navigator.clipboard && window.ClipboardItem) {
      let blob;
      if (monitorImageB64) {
        const bin = atob(monitorImageB64);
        const arr = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) arr[i] = bin.charCodeAt(i);
        blob = new Blob([arr], { type: 'image/png' });
      } else {
        blob = await (await fetch(dataUrl)).blob();
      }
      await navigator.clipboard.write([new ClipboardItem({ 'image/png': blob })]);
      $('monitorCaptureInfo').textContent = '✅ Image copied — paste into an image-capable app';
      return;
    }
  } catch (e) { /* fall through */ }

  try {
    const prevAlt = img.alt;
    const prevTitle = img.title;
    img.alt = '';
    img.title = '';
    const range = document.createRange();
    range.selectNode(img);
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(range);
    const ok = document.execCommand('copy');
    sel.removeAllRanges();
    img.alt = prevAlt;
    img.title = prevTitle;
    if (ok) {
      $('monitorCaptureInfo').textContent = '✅ Image copied — paste into Paint, Word, or a chat box';
      return;
    }
  } catch (e) { /* fall through */ }

  try {
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(dataUrl);
      $('monitorCaptureInfo').textContent = '✅ Copied data URL — paste anywhere';
      return;
    }
  } catch (e) { /* fall through */ }

  try {
    const ta = document.createElement('textarea');
    ta.value = dataUrl;
    ta.style.position = 'fixed';
    ta.style.opacity = '0';
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    ta.setSelectionRange(0, ta.value.length);
    const ok = document.execCommand('copy');
    document.body.removeChild(ta);
    if (ok) {
      $('monitorCaptureInfo').textContent = '✅ Copied data URL — paste anywhere';
      return;
    }
  } catch (e) { /* fall through */ }

  $('monitorCaptureInfo').textContent = '⚠️ Copy failed — right-click the image and choose "Copy image"';
}

// ── auth / lock ──────────────────────────────────────────────────────────
function handleAuthResponse(r) {
  if (r.status === 401) { location.href = '/login'; return true; }
  return false;
}

$('logoutBtn').addEventListener('click', async () => {
  try { await fetch('/api/logout', { method: 'POST' }); } catch { }
  location.href = '/login';
});

function checkAuth() {
  fetch('/api/config')
    .then(r => { if (handleAuthResponse(r)) return null; return r.json(); })
    .then(cfg => { if (cfg) $('logoutBtn').hidden = false; })
    .catch(() => { });
}

// ── start ────────────────────────────────────────────────────────────────
checkAuth();
initSettings();
loadServerInfo();
initEvents();
loadMonitor();
setInterval(loadMonitor, 5000);
setInterval(loadServerInfo, 15000);
