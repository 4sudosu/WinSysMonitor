// Bootstrap entry for the embedded WinSysMonitor server (runs via nodejs-mobile).
// Reads the runtime config written by the Android app, then starts server.js.
const fs = require('fs');
const path = require('path');

const LOG_FILE = path.join(__dirname, 'server.log');

function log(msg) {
  try {
    fs.appendFileSync(LOG_FILE, `[${new Date().toISOString()}] ${msg}\n`);
  } catch (e) {
    // ignore
  }
}

// nodejs-mobile must not call exit() (it aborts the process with SIGABRT).
// Swallow uncaught errors and keep the server alive; record them for diagnosis.
process.on('uncaughtException', (err) => {
  log('uncaughtException: ' + (err && err.stack ? err.stack : String(err)));
});
process.on('unhandledRejection', (reason) => {
  log('unhandledRejection: ' + (reason && reason.stack ? reason.stack : String(reason)));
});
process.exit = function (code) {
  log('process.exit() called with code ' + code + ' - ignored to keep server running');
} ;

log('boot starting');

try {
  const cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'server.config.json'), 'utf8'));
  if (cfg.port) process.env.PORT = String(cfg.port);
  if (cfg.host) process.env.HOST = cfg.host;
  if (cfg.password) process.env.ADMIN_PASSWORD = cfg.password;
  log('config: ' + JSON.stringify(cfg));
} catch (e) {
  log('no config file yet - using defaults: ' + e.message);
}

import('./server.js').then(() => {
  log('server.js loaded');
}).catch(err => {
  log('server start failed: ' + (err && err.stack ? err.stack : String(err)));
});