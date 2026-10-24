$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:/Program Files/Java/jdk-25' }
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$taskTmp = Join-Path $taskRoot 'runtime-tmp'
New-Item -ItemType Directory -Force -Path $taskTmp | Out-Null
# Windows Java 25 may reject AF_UNIX sockets in a long or redirected user temp directory.
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$taskTmp"
Set-Location (Join-Path $taskRoot 'backend/BattleTrust-Engine')
& ./mvnw.cmd -q spring-boot:run
exit $LASTEXITCODE
