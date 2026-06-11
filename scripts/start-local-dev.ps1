<#
.SYNOPSIS
Builds and starts the complete local development stack.

.DESCRIPTION
Loads the ignored root .env file, builds runnable Spring Boot jars once, starts
all backend services, starts the Vite frontend, and writes logs under
logs/local-dev. If port 8080 is occupied, the gateway automatically uses 18080.

.EXAMPLE
.\scripts\start-local-dev.ps1 -OpenBrowser

.EXAMPLE
.\scripts\start-local-dev.ps1 -SkipBuild -GatewayPort 18080
#>
[CmdletBinding()]
param(
    [int]$GatewayPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$SkipBuild,
    [switch]$OpenBrowser
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"
$Frontend = Join-Path $Root "frontend"
$LogDirectory = Join-Path $Root "logs\local-dev"

function Import-DotEnv {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    foreach ($line in Get-Content -LiteralPath $Path) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }
        $parts = $line.Split("=", 2)
        $name = $parts[0].Trim()
        if (-not [string]::IsNullOrWhiteSpace($name)) {
            Set-Item -Path ("Env:" + $name) -Value $parts[1]
        }
    }
}

function Test-PortInUse {
    param([int]$Port)

    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Find-FreePort {
    param([int]$PreferredPort)

    $candidate = $PreferredPort
    while (Test-PortInUse -Port $candidate) {
        $candidate++
    }
    return $candidate
}

Import-DotEnv -Path (Join-Path $Root ".env")
New-Item -ItemType Directory -Force -Path $LogDirectory | Out-Null

if (-not $PSBoundParameters.ContainsKey("GatewayPort") -and (Test-PortInUse -Port $GatewayPort)) {
    $GatewayPort = Find-FreePort -PreferredPort 18080
    Write-Warning "Port 8080 is occupied. Gateway will use port $GatewayPort."
} elseif (Test-PortInUse -Port $GatewayPort) {
    throw "Gateway port $GatewayPort is already in use."
}

if (-not $PSBoundParameters.ContainsKey("FrontendPort") -and (Test-PortInUse -Port $FrontendPort)) {
    $FrontendPort = Find-FreePort -PreferredPort 5174
    Write-Warning "Port 5173 is occupied. Frontend will use port $FrontendPort."
} elseif (Test-PortInUse -Port $FrontendPort) {
    throw "Frontend port $FrontendPort is already in use."
}

if (-not $SkipBuild) {
    & mvn -f (Join-Path $Backend "pom.xml") -s (Join-Path $Backend "settings.xml.example") package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        throw "Backend package build failed."
    }
}

$services = @(
    @{ Name = "auth-service"; Args = @() },
    @{ Name = "user-service"; Args = @() },
    @{ Name = "resume-service"; Args = @() },
    @{ Name = "job-service"; Args = @() },
    @{ Name = "match-service"; Args = @() },
    @{ Name = "ai-service"; Args = @() },
    @{ Name = "delivery-service"; Args = @() },
    @{ Name = "gateway-service"; Args = @("--server.port=$GatewayPort") }
)

$started = New-Object System.Collections.Generic.List[object]
foreach ($service in $services) {
    $jar = Join-Path $Backend ("{0}\target\{0}-0.1.0-SNAPSHOT.jar" -f $service.Name)
    if (-not (Test-Path -LiteralPath $jar)) {
        throw "Runnable jar not found: $jar"
    }

    $stdout = Join-Path $LogDirectory ($service.Name + ".out.log")
    $stderr = Join-Path $LogDirectory ($service.Name + ".err.log")
    $arguments = @("-jar", $jar) + $service.Args
    $process = Start-Process java -WindowStyle Hidden -PassThru `
        -ArgumentList $arguments `
        -WorkingDirectory $Backend `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr
    $started.Add([pscustomobject]@{
        Service = $service.Name
        ProcessId = $process.Id
        Log = $stdout
    })
}

$env:VITE_API_PROXY_TARGET = "http://localhost:$GatewayPort"
$frontendStdout = Join-Path $LogDirectory "frontend.out.log"
$frontendStderr = Join-Path $LogDirectory "frontend.err.log"
$frontendCommand = "Set-Location '$Frontend'; npm run dev -- --port $FrontendPort"
$frontendProcess = Start-Process powershell -WindowStyle Hidden -PassThru `
    -ArgumentList @("-NoExit", "-Command", $frontendCommand) `
    -RedirectStandardOutput $frontendStdout `
    -RedirectStandardError $frontendStderr
$started.Add([pscustomobject]@{
    Service = "frontend"
    ProcessId = $frontendProcess.Id
    Log = $frontendStdout
})

$started
Write-Host "Frontend: http://localhost:$FrontendPort"
Write-Host "Gateway:  http://localhost:$GatewayPort"
Write-Host "Logs:     $LogDirectory"

if ($OpenBrowser) {
    Start-Process "http://localhost:$FrontendPort/"
}
