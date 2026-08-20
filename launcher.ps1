# WinSysMonitor - Launcher
# Asks whether to START a server (host this dashboard) or CONNECT to an
# already-running server, then opens the dashboard in your browser.
# Usage:  powershell -ExecutionPolicy Bypass -File .\launcher.ps1

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$cfgFile = Join-Path $root 'launcher.config.json'

function Read-Config {
    if (Test-Path $cfgFile) {
        try { return Get-Content -LiteralPath $cfgFile -Raw | ConvertFrom-Json } catch { }
    }
    return [pscustomobject]@{ ip = '0.0.0.0'; port = 3001; connect = '' }
}
function Save-Config($cfg) {
    try { $cfg | ConvertTo-Json | Set-Content -LiteralPath $cfgFile -Encoding UTF8 } catch { }
}

function Find-Node {
    if ($env:NODE_PATH -and (Test-Path $env:NODE_PATH)) { return $env:NODE_PATH }
    $local = Join-Path $root 'node\node.exe'
    if (Test-Path $local) { return $local }
    $candidates = @(
        'C:\Program Files\nodejs\node.exe',
        (Join-Path ${env:ProgramFiles(x86)} 'nodejs\node.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'),
        (Join-Path $env:APPDATA 'npm\node.exe')
    )
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $cmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Write-Title {
    Clear-Host
    Write-Host ""
    Write-Host "   ______     __        ____                       " -ForegroundColor Cyan
    Write-Host "  / ____/  __/ /____   / __ \_      ______ __  __   " -ForegroundColor Cyan
    Write-Host " / /      / __/ ___/  / /_/ / | /| / / __ `/ / / /  " -ForegroundColor Cyan
    Write-Host "/ /___   / /_(__  )  / ____/| |/ |/ / /_/ / /_/ /   " -ForegroundColor Cyan
    Write-Host "\____/   \__/____/  /_/    |__/|__/\__,_/\__, /    " -ForegroundColor Cyan
    Write-Host "                                        /____/     " -ForegroundColor Cyan
    Write-Host ""
    Write-Host "   Device Monitoring - Launcher" -ForegroundColor White
    Write-Host ""
}

$node = Find-Node
$cfg = Read-Config

while ($true) {
    Write-Title
    Write-Host "  [1] Start server" -ForegroundColor Green
    Write-Host "  [2] Connect to existing server" -ForegroundColor Cyan
    Write-Host "  [3] Exit" -ForegroundColor DarkGray
    Write-Host ""
    $choice = Read-Host "  Choose an option"

    if ($choice -eq '3') { Write-Host "  Bye!" -ForegroundColor DarkGray; break }

    if ($choice -eq '1') {
        if (-not $node) {
            Write-Host "  `n  ERROR: node.exe not found. Install Node.js or set NODE_PATH." -ForegroundColor Red
            Read-Host "  Press Enter to continue"
            continue
        }
        Write-Host ""
        Write-Host "  Start a server. This host will serve the dashboard and accept agent connections." -ForegroundColor DarkGray
        $ip = Read-Host "  Bind IP [default $($cfg.ip)]"
        if (-not $ip) { $ip = $cfg.ip }
        $port = Read-Host "  Port [default $($cfg.port)]"
        if (-not $port) { $port = $cfg.port }
        if (-not ($port -match '^\d+$')) {
            Write-Host "  ERROR: invalid port." -ForegroundColor Red
            Read-Host "  Press Enter to continue"
            continue
        }
        $cfg.ip = $ip
        $cfg.port = [int]$port
        Save-Config $cfg

        $serverDir = Join-Path $root 'Server'
        if (-not (Test-Path (Join-Path $serverDir 'server.js'))) {
            Write-Host "  ERROR: Server folder not found: $serverDir" -ForegroundColor Red
            Read-Host "  Press Enter to continue"
            continue
        }

        Write-Host ""
        Write-Host "  Starting server on $ip`:$port ..." -ForegroundColor Green
        $cmdLine = "set HOST=$ip&& set PORT=$port&& node server.js"
        Start-Process -FilePath 'cmd.exe' -ArgumentList "/c $cmdLine" -WorkingDirectory $serverDir

        $open = if ($ip -eq '0.0.0.0' -or $ip -eq '::') { "http://localhost:$port" } else { "http://$ip`:$port" }
        Start-Sleep -Milliseconds 1200
        Start-Process $open
        Write-Host "  Dashboard: $open  (server window opened beside this one)" -ForegroundColor DarkGray
        Read-Host "  Press Enter to return to the menu"
    }
    elseif ($choice -eq '2') {
        Write-Host ""
        Write-Host "  Connect to a server that is already running." -ForegroundColor DarkGray
        $addr = Read-Host "  Server ip:port"
        if (-not $addr) {
            if ($cfg.connect) { $addr = $cfg.connect } else { continue }
        }
        if ($addr -notmatch '^.+:\d+$') {
            Write-Host "  ERROR: expected ip:port, e.g. 192.168.1.50:3001" -ForegroundColor Red
            Read-Host "  Press Enter to continue"
            continue
        }
        $cfg.connect = $addr
        Save-Config $cfg
        $open = if ($addr -match '^(localhost|127\.0\.0\.1)') { "http://$addr" } else { "http://$addr" }
        Start-Process $open
        Write-Host "  Opened $open in your browser." -ForegroundColor DarkGray
        Read-Host "  Press Enter to return to the menu"
    }
    else {
        Write-Host "  Invalid option." -ForegroundColor Yellow
    }
}
