<#
.SYNOPSIS
Renders the Prometheus scrape configuration for the three-VM deployment.

.DESCRIPTION
Reads VM1_HOST, VM2_HOST, and VM3_HOST from an env file, then replaces tokens in
deploy/monitoring/prometheus.yml.template. The rendered file is committed with
the current lab VM IPs, but this script should be rerun whenever VMware assigns
new addresses.

.EXAMPLE
.\scripts\render-monitoring-config.ps1 -EnvFile .\.env
#>
[CmdletBinding()]
param(
    [string]$EnvFile = ".\deploy\three-vm.env",
    [string]$Template = ".\deploy\monitoring\prometheus.yml.template",
    [string]$Output = ".\deploy\monitoring\prometheus.yml",
    [string]$Vm1Ip = "",
    [string]$Vm2Ip = "",
    [string]$Vm3Ip = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

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

$envPath = Resolve-ProjectPath $EnvFile
$templatePath = Resolve-ProjectPath $Template
$outputPath = Resolve-ProjectPath $Output

if (-not (Test-Path -LiteralPath $templatePath)) {
    throw "Template not found: $templatePath"
}

$values = Import-DotEnvMap -Path $envPath
$vm1 = if ($Vm1Ip) { $Vm1Ip } elseif ($values.VM1_HOST) { $values.VM1_HOST } else { "192.168.6.130" }
$vm2 = if ($Vm2Ip) { $Vm2Ip } elseif ($values.VM2_HOST) { $values.VM2_HOST } else { "192.168.6.141" }
$vm3 = if ($Vm3Ip) { $Vm3Ip } elseif ($values.VM3_HOST) { $values.VM3_HOST } else { "192.168.6.142" }

$content = Get-Content -LiteralPath $templatePath -Raw
$content = $content.Replace("__VM1_HOST__", $vm1).Replace("__VM2_HOST__", $vm2).Replace("__VM3_HOST__", $vm3)
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
Set-Content -LiteralPath $outputPath -Value $content -Encoding UTF8

Write-Host "Rendered Prometheus config: $outputPath"
Write-Host "VM1=$vm1 VM2=$vm2 VM3=$vm3"
