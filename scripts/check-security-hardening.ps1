<#
.SYNOPSIS
Checks the deployment security baseline without printing secrets.

.DESCRIPTION
Validates the local deployment env and repository configuration for common
production risks: default passwords, disabled Gateway auth, weak JWT secret,
missing frontend security headers, and accidentally tracked runtime env files.

.EXAMPLE
.\scripts\check-security-hardening.ps1 -EnvFile .\.env

.EXAMPLE
.\scripts\check-security-hardening.ps1 -EnvFile .\deploy\three-vm.env.example -AllowExampleDefaults
#>
[CmdletBinding()]
param(
    [string]$EnvFile = ".\.env",
    [string]$ReportDirectory = ".\reports\security",
    [switch]$AllowExampleDefaults
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$script:Results = New-Object System.Collections.Generic.List[object]

function Resolve-ProjectPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return Join-Path $Root $Path
}

function Import-DotEnvMap {
    param([string]$Path)
    $values = @{}
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in Get-Content -LiteralPath $Path) {
            if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#") -or -not $line.Contains("=")) {
                continue
            }
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            if (-not [string]::IsNullOrWhiteSpace($key)) {
                $values[$key] = $parts[1].Trim()
            }
        }
    }
    return $values
}

function Add-Result {
    param(
        [string]$Status,
        [string]$Name,
        [string]$Detail
    )
    $script:Results.Add([pscustomobject]@{ Status = $Status; Name = $Name; Detail = $Detail })
    $color = switch ($Status) {
        "PASS" { "Green" }
        "WARN" { "Yellow" }
        default { "Red" }
    }
    Write-Host ("[{0}] {1} - {2}" -f $Status, $Name, $Detail) -ForegroundColor $color
}

function Add-DefaultSecretCheck {
    param(
        [hashtable]$Values,
        [string]$Key,
        [string[]]$UnsafeValues,
        [int]$MinLength
    )
    $value = [string]$Values[$Key]
    if ([string]::IsNullOrWhiteSpace($value)) {
        Add-Result -Status "FAIL" -Name $Key -Detail "missing"
        return
    }
    if ($UnsafeValues -contains $value -or $value.Length -lt $MinLength) {
        if ($AllowExampleDefaults) {
            Add-Result -Status "WARN" -Name $Key -Detail "example/default value allowed in template mode"
        } else {
            Add-Result -Status "FAIL" -Name $Key -Detail "uses a default or weak value"
        }
        return
    }
    Add-Result -Status "PASS" -Name $Key -Detail "configured with non-default value"
}

$envPath = Resolve-ProjectPath $EnvFile
if (Test-Path -LiteralPath $envPath) {
    Add-Result -Status "PASS" -Name "env file" -Detail "configured"
} else {
    Add-Result -Status "FAIL" -Name "env file" -Detail "not found"
}
$values = Import-DotEnvMap -Path $envPath

Add-DefaultSecretCheck -Values $values -Key "JWT_SECRET" -UnsafeValues @("replace-with-a-long-random-jwt-secret") -MinLength 32
Add-DefaultSecretCheck -Values $values -Key "MYSQL_ROOT_PASSWORD" -UnsafeValues @("root123456", "password", "123456") -MinLength 10
Add-DefaultSecretCheck -Values $values -Key "MINIO_ROOT_PASSWORD" -UnsafeValues @("minioadmin", "password", "123456") -MinLength 10
Add-DefaultSecretCheck -Values $values -Key "GRAFANA_ADMIN_PASSWORD" -UnsafeValues @("admin", "admin123456", "replace-with-a-strong-grafana-password") -MinLength 10
Add-DefaultSecretCheck -Values $values -Key "SENTINEL_DASHBOARD_PASSWORD" -UnsafeValues @("sentinel", "sentinel123456", "replace-with-a-strong-sentinel-password") -MinLength 10

if (($values.GATEWAY_AUTH_ENABLED -as [string]).ToLowerInvariant() -eq "true") {
    Add-Result -Status "PASS" -Name "gateway auth" -Detail "enabled"
} else {
    Add-Result -Status "FAIL" -Name "gateway auth" -Detail "GATEWAY_AUTH_ENABLED must be true"
}

if ([string]::IsNullOrWhiteSpace($values.DASHSCOPE_API_KEY)) {
    Add-Result -Status "WARN" -Name "DashScope key" -Detail "missing; AI will fall back to demo responses"
} else {
    Add-Result -Status "PASS" -Name "DashScope key" -Detail "configured without printing value"
}

$trackedEnv = & git -C $Root ls-files ".env" "deploy/three-vm.env" 2>$null
if ([string]::IsNullOrWhiteSpace(($trackedEnv -join ""))) {
    Add-Result -Status "PASS" -Name "runtime env tracking" -Detail ".env and deploy/three-vm.env are not tracked"
} else {
    Add-Result -Status "FAIL" -Name "runtime env tracking" -Detail "runtime env files are tracked"
}

$nginxTemplate = Join-Path $Root "frontend\nginx\default.conf.template"
$nginxContent = if (Test-Path -LiteralPath $nginxTemplate) { Get-Content -LiteralPath $nginxTemplate -Raw } else { "" }
foreach ($header in @("X-Frame-Options", "X-Content-Type-Options", "Referrer-Policy", "Content-Security-Policy")) {
    if ($nginxContent.Contains($header)) {
        Add-Result -Status "PASS" -Name "nginx header $header" -Detail "present"
    } else {
        Add-Result -Status "FAIL" -Name "nginx header $header" -Detail "missing"
    }
}

$composeText = Get-Content -LiteralPath (Join-Path $Root "deploy\docker-compose.vm1.yml") -Raw
$composeText += Get-Content -LiteralPath (Join-Path $Root "deploy\docker-compose.vm2.yml") -Raw
$composeText += Get-Content -LiteralPath (Join-Path $Root "deploy\docker-compose.vm3.yml") -Raw
foreach ($expected in @("MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE", "node-exporter", "prometheus", "grafana", "sentinel-dashboard")) {
    if ($composeText.Contains($expected)) {
        Add-Result -Status "PASS" -Name "compose $expected" -Detail "present"
    } else {
        Add-Result -Status "FAIL" -Name "compose $expected" -Detail "missing"
    }
}

$passCount = @($script:Results | Where-Object Status -eq "PASS").Count
$warnCount = @($script:Results | Where-Object Status -eq "WARN").Count
$failCount = @($script:Results | Where-Object Status -eq "FAIL").Count

$reportDir = Resolve-ProjectPath $ReportDirectory
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportPath = Join-Path $reportDir ("security-hardening-{0}-{1}.md" -f (Get-Date -Format "yyyyMMdd-HHmmss-fff"), ([guid]::NewGuid().ToString("N").Substring(0, 8)))
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Security Hardening Report")
$lines.Add("")
$lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$lines.Add("- Env file: ``$envPath``")
$lines.Add("- Allow example defaults: $AllowExampleDefaults")
$lines.Add("- Summary: PASS=$passCount, WARN=$warnCount, FAIL=$failCount")
$lines.Add("")
$lines.Add("| Status | Check | Detail |")
$lines.Add("| --- | --- | --- |")
foreach ($result in $script:Results) {
    $lines.Add("| $($result.Status) | $($result.Name) | $($result.Detail.Replace('|', '\|')) |")
}
Set-Content -LiteralPath $reportPath -Value $lines -Encoding UTF8
Write-Host "Report written: $reportPath" -ForegroundColor Cyan

if ($failCount -gt 0) {
    exit 1
}
exit 0
