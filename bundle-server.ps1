# WinSysMonitor - Bundle App (self-contained)
# Packs node.exe + the whole Server folder + launcher into one portable
# folder so the app carries its own Node.js runtime ("migrate server with app").
#
# Usage:  powershell -ExecutionPolicy Bypass -File .\bundle-server.ps1
# Output: ..\WinSysMonitorApp\

$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$srcServer = Join-Path $root 'Server'
$out = Join-Path $root 'WinSysMonitorApp'
$nodeExe = $null

if ($env:NODE_PATH -and (Test-Path $env:NODE_PATH)) { $nodeExe = $env:NODE_PATH }
if (-not $nodeExe) {
    $local = Join-Path $root 'node\node.exe'
    if (Test-Path $local) { $nodeExe = $local }
}
if (-not $nodeExe) {
    $candidates = @(
        'C:\Program Files\nodejs\node.exe',
        (Join-Path ${env:ProgramFiles(x86)} 'nodejs\node.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'),
        (Join-Path $env:APPDATA 'npm\node.exe')
    )
    foreach ($c in $candidates) { if (Test-Path $c) { $nodeExe = $c; break } }
}
if (-not $nodeExe) {
    $cmd = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($cmd) { $nodeExe = $cmd.Source }
}
if (-not $nodeExe) { throw "node.exe not found - install Node.js or set env:NODE_PATH to node.exe" }
if (-not (Test-Path $srcServer)) { throw "Server folder not found: $srcServer" }

Write-Host "Using node: $nodeExe" -ForegroundColor Cyan

if (Test-Path $out) { Remove-Item -LiteralPath $out -Recurse -Force }

# 1. Copy node runtime
$nodeDir = Join-Path $out 'node'
New-Item -ItemType Directory -Path $nodeDir -Force | Out-Null
Copy-Item -LiteralPath $nodeExe -Destination (Join-Path $nodeDir 'node.exe') -Force

# 2. Copy server (all node files + modules)
Copy-Item -LiteralPath $srcServer -Destination (Join-Path $out 'Server') -Recurse -Force

# 3. Copy launcher + a double-click bat
Copy-Item -LiteralPath (Join-Path $root 'launcher.ps1') -Destination $out -Force
@'
@echo off
title WinSysMonitor - Launcher
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0launcher.ps1"
'@ | Set-Content -LiteralPath (Join-Path $out 'Start WinSysMonitor.bat') -Encoding ASCII

Write-Host ""
Write-Host "=== APP BUNDLE READY ===" -ForegroundColor Green
Write-Host "Folder : $out"
$sizeMB = [math]::Round(((Get-ChildItem -LiteralPath $out -Recurse -File | Measure-Object -Property Length -Sum).Sum) / 1MB, 1)
Write-Host "Size   : $sizeMB MB"
Write-Host "Run    : $out\Start WinSysMonitor.bat"
Write-Host ""
Write-Host "Copy this folder to any Windows machine - it carries its own Node.js." -ForegroundColor DarkGray
