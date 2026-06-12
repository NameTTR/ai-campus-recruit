<#
.SYNOPSIS
Runs stepped concurrent smoke load checks against the deployed gateway.

.DESCRIPTION
Executes scripts/run-load-smoke.ps1 for each concurrency step and writes one
aggregate Markdown report. The child smoke script redacts demo passwords and
bearer tokens; this script records only the password source.

.EXAMPLE
.\scripts\run-step-load-smoke.ps1 -BaseUrl http://192.168.6.130:8080 -DemoPassword 123456 -UserSteps 3,10,30,50 -Iterations 3
#>
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [string]$DemoPassword = "",

    [ValidateNotNullOrEmpty()]
    [int[]]$UserSteps = @(3, 10, 30, 50),

    [ValidateRange(1, 10000)]
    [int]$Iterations = 3,

    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 15,

    [string]$ReportDirectory = "",

    [switch]$AllowSkips
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $Root "reports\deploy"
} elseif (-not [System.IO.Path]::IsPathRooted($ReportDirectory)) {
    $ReportDirectory = Join-Path $Root $ReportDirectory
}

$passwordSource = "parameter"
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    if (-not [string]::IsNullOrWhiteSpace($env:THREE_VM_DEMO_PASSWORD)) {
        $DemoPassword = $env:THREE_VM_DEMO_PASSWORD
        $passwordSource = "THREE_VM_DEMO_PASSWORD"
    } else {
        $DemoPassword = "123456"
        $passwordSource = "local demo fallback"
    }
}

function ConvertTo-MarkdownCell {
    param([object]$Value)

    if ($null -eq $Value) {
        return ""
    }
    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Read-LoadSmokeOverall {
    param([string]$Path)

    $lines = Get-Content -LiteralPath $Path
    $inOverall = $false
    foreach ($line in $lines) {
        if ($line -eq "## Overall") {
            $inOverall = $true
            continue
        }
        if ($inOverall -and $line -match '^\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*(\d+)\s*\|\s*([0-9.]+)\s*\|\s*([0-9.]+)\s*\|$') {
            return [pscustomobject]@{
                Pass = [int]$Matches[1]
                Fail = [int]$Matches[2]
                Skipped = [int]$Matches[3]
                AverageMs = [double]$Matches[4]
                P95Ms = [double]$Matches[5]
            }
        }
        if ($inOverall -and $line.StartsWith("## ")) {
            break
        }
    }
    throw "Could not parse overall result from $Path"
}

New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
$smokeScript = Join-Path $PSScriptRoot "run-load-smoke.ps1"
if (-not (Test-Path -LiteralPath $smokeScript)) {
    throw "Smoke script not found: $smokeScript"
}

$stepResults = New-Object System.Collections.Generic.List[object]
Write-Host "Running stepped load smoke against $BaseUrl; steps=$($UserSteps -join ','); iterations=$Iterations; timeout=${TimeoutSeconds}s"

foreach ($users in $UserSteps) {
    Write-Host "==> Step users=$users"
    & $smokeScript `
        -BaseUrl $BaseUrl `
        -DemoPassword $DemoPassword `
        -Users $users `
        -Iterations $Iterations `
        -TimeoutSeconds $TimeoutSeconds `
        -ReportDirectory $ReportDirectory

    $latestReport = Get-ChildItem -LiteralPath $ReportDirectory -Filter "load-smoke-*.md" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $latestReport) {
        throw "No load smoke report was generated for users=$users"
    }

    $overall = Read-LoadSmokeOverall -Path $latestReport.FullName
    $stepResults.Add([pscustomobject]@{
        Users = $users
        Iterations = $Iterations
        Pass = $overall.Pass
        Fail = $overall.Fail
        Skipped = $overall.Skipped
        AverageMs = $overall.AverageMs
        P95Ms = $overall.P95Ms
        Report = $latestReport.Name
    })
}

$totalPass = ($stepResults | Measure-Object -Property Pass -Sum).Sum
$totalFail = ($stepResults | Measure-Object -Property Fail -Sum).Sum
$totalSkipped = ($stepResults | Measure-Object -Property Skipped -Sum).Sum
$maxP95 = ($stepResults | Measure-Object -Property P95Ms -Maximum).Maximum

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportDirectory "step-load-smoke-$timestamp.md"
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# Step Load Smoke Report")
$lines.Add("")
$lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
$lines.Add("- Base URL: ``$BaseUrl``")
$lines.Add("- User steps: ``$($UserSteps -join ',')``")
$lines.Add("- Iterations per user: ``$Iterations``")
$lines.Add("- Timeout seconds: ``$TimeoutSeconds``")
$lines.Add("- Demo password source: ``$passwordSource``")
$lines.Add("- Secret handling: demo passwords and bearer tokens are not written to this report.")
$lines.Add("")
$lines.Add("## Overall")
$lines.Add("")
$lines.Add("| PASS | FAIL | SKIPPED | Max P95 ms |")
$lines.Add("| ---: | ---: | ---: | ---: |")
$lines.Add("| $totalPass | $totalFail | $totalSkipped | $maxP95 |")
$lines.Add("")
$lines.Add("## Steps")
$lines.Add("")
$lines.Add("| Users | Iterations | PASS | FAIL | SKIPPED | Average ms | P95 ms | Child report |")
$lines.Add("| ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |")
foreach ($step in $stepResults) {
    $lines.Add(("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} |" -f `
        $step.Users,
        $step.Iterations,
        $step.Pass,
        $step.Fail,
        $step.Skipped,
        $step.AverageMs,
        $step.P95Ms,
        (ConvertTo-MarkdownCell $step.Report)))
}
$lines.Add("")
$lines.Add("## Scenario")
$lines.Add("")
$lines.Add("- Each step runs ``scripts/run-load-smoke.ps1`` with the same business probes.")
$lines.Add("- RAG answer probes call ``POST /api/ai/knowledge/answer`` with ``useAi=false`` so the test exercises retrieval and citation assembly without external model cost.")
$lines.Add("- A nonzero FAIL count fails this script. SKIPPED also fails unless ``-AllowSkips`` is provided.")

Set-Content -LiteralPath $reportPath -Value $lines -Encoding UTF8
Write-Host "Step summary: PASS=$totalPass, FAIL=$totalFail, SKIPPED=$totalSkipped, maxP95=${maxP95}ms"
Write-Host "Report written: $reportPath"

if ($totalFail -gt 0 -or ($totalSkipped -gt 0 -and -not $AllowSkips)) {
    exit 1
}
