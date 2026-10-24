$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskRuntime = Join-Path $taskRoot 'runtime-tmp'
New-Item -ItemType Directory -Force -Path $taskRuntime | Out-Null
function Test-Http($url) { try { return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200 } catch { return $false } }
if (-not (Test-Http 'http://localhost:8080/api/v1/public/stats')) {
    Start-Process -FilePath 'powershell.exe' -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'run-backend.ps1')) -WorkingDirectory $taskRoot -RedirectStandardOutput (Join-Path $taskRuntime 'backend.out.log') -RedirectStandardError (Join-Path $taskRuntime 'backend.err.log') | Out-Null
}
if (-not (Test-Http 'http://localhost:3000')) {
    $taskNode = (Get-Command node.exe).Source
    Start-Process -FilePath $taskNode -WindowStyle Hidden -ArgumentList @('node_modules/next/dist/bin/next', 'dev', '--port', '3000') -WorkingDirectory (Join-Path $taskRoot 'frontend') -RedirectStandardOutput (Join-Path $taskRuntime 'frontend.out.log') -RedirectStandardError (Join-Path $taskRuntime 'frontend.err.log') | Out-Null
}
Write-Output 'Development services launched. Frontend: http://localhost:3000 | Backend: http://localhost:8080'
Write-Output 'Startup logs are in runtime-tmp. Run scripts/smoke.mjs after the backend is ready.'
