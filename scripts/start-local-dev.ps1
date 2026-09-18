<#
.SYNOPSIS
Builds and starts the complete local development stack.

.DESCRIPTION
Loads the ignored root .env file, builds runnable Spring Boot jars once, starts
all backend services, starts the Vite frontend, and writes logs under
logs/local-dev. If port 8080 is occupied, the gateway automatically uses 18080.
Use -DemoMode to run without Nacos, MySQL, Redis, RocketMQ, MinIO, or Milvus.

.EXAMPLE
.\scripts\start-local-dev.ps1 -OpenBrowser

.EXAMPLE
.\scripts\start-local-dev.ps1 -SkipBuild -GatewayPort 18080

.EXAMPLE
.\scripts\start-local-dev.ps1 -DemoMode -OpenBrowser

.EXAMPLE
.\scripts\start-local-dev.ps1 -SeedDemoData
# Keeps database persistence enabled and adds sample accounts only if absent.
#>
[CmdletBinding()]
param(
    [int]$GatewayPort = 8080,
    [int]$FrontendPort = 5173,
    [switch]$SkipBuild,
    [switch]$OpenBrowser,
    [switch]$DemoMode,
    [switch]$SeedDemoData
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

function Set-DefaultEnv {
    param(
        [string]$Name,
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace((Get-Item -Path ("Env:" + $Name) -ErrorAction SilentlyContinue).Value)) {
        Set-Item -Path ("Env:" + $Name) -Value $Value
    }
}

function Enable-DemoMode {
    $disabledSettings = @(
        "NACOS_ENABLED",
        "AUTH_PERSISTENCE_ENABLED",
        "USER_PERSISTENCE_ENABLED",
        "AI_CORE_PERSISTENCE_ENABLED",
        "RESUME_PERSISTENCE_ENABLED",
        "JOB_PERSISTENCE_ENABLED",
        "MATCH_PERSISTENCE_ENABLED",
        "DELIVERY_PERSISTENCE_ENABLED",
        "AI_SCREENING_PERSISTENCE_ENABLED",
        "AI_PLANNING_PERSISTENCE_ENABLED",
        "AI_KNOWLEDGE_PERSISTENCE_ENABLED",
        "DASHBOARD_REALTIME_ENABLED",
        "RESUME_OBJECT_STORAGE_ENABLED",
        "AI_KNOWLEDGE_OBJECT_STORAGE_ENABLED",
        "AI_KNOWLEDGE_VECTOR_ENABLED",
        "DELIVERY_EVENTS_ROCKETMQ_ENABLED",
        "AI_SCREENING_ROCKETMQ_ENABLED",
        "RESUME_DB_HEALTH_ENABLED",
        "RESUME_REDIS_HEALTH_ENABLED",
        "JOB_DB_HEALTH_ENABLED",
        "JOB_REDIS_HEALTH_ENABLED",
        "MATCH_DB_HEALTH_ENABLED",
        "MATCH_REDIS_HEALTH_ENABLED",
        "DELIVERY_DB_HEALTH_ENABLED",
        "DELIVERY_REDIS_HEALTH_ENABLED",
        "AI_SCREENING_DB_HEALTH_ENABLED"
    )

    foreach ($name in $disabledSettings) {
        Set-Item -Path ("Env:" + $name) -Value "false"
    }
}

Import-DotEnv -Path (Join-Path $Root ".env")
Set-DefaultEnv -Name "MYSQL_DATABASE" -Value "ai_campus_recruit"
Set-DefaultEnv -Name "MYSQL_HOST_PORT" -Value "3306"
Set-DefaultEnv -Name "SPRING_DATASOURCE_URL" -Value ("jdbc:mysql://127.0.0.1:{0}/{1}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" -f $env:MYSQL_HOST_PORT, $env:MYSQL_DATABASE)
Set-DefaultEnv -Name "SPRING_DATASOURCE_USERNAME" -Value "root"
if ([string]::IsNullOrWhiteSpace($env:SPRING_DATASOURCE_PASSWORD) -and -not [string]::IsNullOrWhiteSpace($env:MYSQL_ROOT_PASSWORD)) {
    Set-Item -Path "Env:SPRING_DATASOURCE_PASSWORD" -Value $env:MYSQL_ROOT_PASSWORD
}
Set-DefaultEnv -Name "SPRING_DATA_REDIS_HOST" -Value "127.0.0.1"
Set-DefaultEnv -Name "REDIS_HOST_PORT" -Value "6379"
Set-DefaultEnv -Name "SPRING_DATA_REDIS_PORT" -Value $env:REDIS_HOST_PORT
Set-DefaultEnv -Name "AUTH_PERSISTENCE_ENABLED" -Value "true"
Set-DefaultEnv -Name "USER_PERSISTENCE_ENABLED" -Value "true"
Set-DefaultEnv -Name "AI_CORE_PERSISTENCE_ENABLED" -Value "true"
Set-DefaultEnv -Name "DEMO_SEED_ENABLED" -Value "false"
if ($DemoMode -or $SeedDemoData) {
    $env:DEMO_SEED_ENABLED = "true"
}
if ($DemoMode) {
    Enable-DemoMode
    Write-Host "Demo mode enabled: Nacos, database/Redis persistence, RocketMQ, object storage, and vector search are disabled; all application data is in memory and is lost when services stop."
}
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

$aiServiceArgs = @()
if ($DemoMode) {
    $aiServiceArgs += "--management.health.redis.enabled=false"
}

$services = @(
    @{ Name = "auth-service"; Args = @() },
    @{ Name = "user-service"; Args = @() },
    @{ Name = "resume-service"; Args = @() },
    @{ Name = "job-service"; Args = @() },
    @{ Name = "match-service"; Args = @() },
    @{ Name = "ai-service"; Args = $aiServiceArgs },
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

$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:$GatewayPort"
$frontendStdout = Join-Path $LogDirectory "frontend.out.log"
$frontendStderr = Join-Path $LogDirectory "frontend.err.log"
$viteCommand = Join-Path $Frontend "node_modules\.bin\vite.cmd"
$frontendCommand = "Set-Location '$Frontend'; `$env:VITE_API_PROXY_TARGET='http://127.0.0.1:$GatewayPort'; & '$viteCommand' --host 0.0.0.0 --port $FrontendPort"
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
