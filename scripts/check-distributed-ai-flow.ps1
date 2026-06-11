<#
.SYNOPSIS
Verifies real DashScope AI and the RocketMQ delivery-to-screening flow.

.DESCRIPTION
Logs in through the gateway, verifies that DashScope is configured, requires a
real candidate screening response with mocked=false, creates a delivery, then
polls until exactly one ROCKETMQ-source async screening task completes.

.EXAMPLE
.\scripts\check-distributed-ai-flow.ps1 -BaseUrl http://192.168.6.130:8080 -AiBaseUrl http://192.168.6.140:8106
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:18080",
    [string]$AiBaseUrl = "http://localhost:8106",
    [string]$DemoPassword = "",
    [int]$TimeoutSeconds = 20,
    [int]$TaskTimeoutSeconds = 180,
    [int]$PollSeconds = 3,
    [int]$AiAttempts = 3,
    [string]$ReportDirectory = ""
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$script:Results = New-Object System.Collections.Generic.List[object]

if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    $DemoPassword = $env:THREE_VM_DEMO_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    $DemoPassword = "123456"
}
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $Root "reports\deploy"
}

function Add-Result {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Detail
    )

    $script:Results.Add([pscustomobject]@{
        Name = $Name
        Status = $Status
        Detail = $Detail
    })
    $color = if ($Status -eq "PASS") { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} - {2}" -f $Status, $Name, $Detail) -ForegroundColor $color
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$Token = ""
    )

    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $headers.Authorization = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = $Url
        Headers = $headers
        TimeoutSec = $TimeoutSeconds
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = $Body | ConvertTo-Json -Depth 10
    }
    $response = Invoke-RestMethod @params
    if ($response.code -ne 0) {
        throw "API returned code=$($response.code): $($response.message)"
    }
    return $response
}

function Login {
    param([string]$Username)

    $response = Invoke-Api -Method "POST" -Url ($BaseUrl.TrimEnd("/") + "/api/auth/login") -Body @{
        username = $Username
        password = $DemoPassword
    }
    return $response.data.token
}

function Write-Report {
    $passCount = @($script:Results | Where-Object { $_.Status -eq "PASS" }).Count
    $failCount = @($script:Results | Where-Object { $_.Status -eq "FAIL" }).Count
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Distributed AI Flow Report")
    $lines.Add("")
    $lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    $lines.Add("- Gateway: ``$BaseUrl``")
    $lines.Add("- AI service: ``$AiBaseUrl``")
    $lines.Add("- Summary: PASS=$passCount, FAIL=$failCount")
    $lines.Add("")
    $lines.Add("| Status | Check | Detail |")
    $lines.Add("| --- | --- | --- |")
    foreach ($result in $script:Results) {
        $detail = ([string]$result.Detail).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
        $lines.Add("| $($result.Status) | $($result.Name) | $detail |")
    }
    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    $path = Join-Path $ReportDirectory ("distributed-ai-flow-{0}.md" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    Set-Content -LiteralPath $path -Value $lines -Encoding UTF8
    Write-Host "Report written: $path" -ForegroundColor Cyan
    return $failCount
}

try {
    $studentToken = Login -Username "student"
    $companyToken = Login -Username "company"
    Add-Result -Name "gateway login" -Status "PASS" -Detail "student and company tokens issued"

    $status = Invoke-Api -Method "GET" -Url ($BaseUrl.TrimEnd("/") + "/api/ai/status") -Token $studentToken
    if ($status.data.configured -ne $true) {
        throw "DashScope status is not configured: $($status.data.fallbackReason)"
    }
    Add-Result -Name "DashScope configured" -Status "PASS" -Detail ("provider={0}, model={1}" -f $status.data.provider, $status.data.model)

    $realAiSucceeded = $false
    for ($attempt = 1; $attempt -le $AiAttempts -and -not $realAiSucceeded; $attempt++) {
        try {
            $screening = Invoke-Api -Method "POST" -Url ($AiBaseUrl.TrimEnd("/") + "/api/ai/candidates/screen") -Body @{
                deliveryId = "D-AI-VERIFY-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
                companyId = "C001"
                studentId = "S001"
                resumeId = "R001"
                jobId = "J001"
                resumeSourceFormat = "DOCX"
                resumeParseStatus = "TEXT_EXTRACTED"
                resumeParsedTextLength = 1280
                targetRole = "Java Backend Engineer"
                skills = @("Java", "Spring Boot", "MySQL", "Redis", "Docker")
                projects = @("AI campus recruitment microservice platform")
                jobRequirements = @("Java", "Spring Cloud Alibaba", "RocketMQ", "MySQL", "Redis")
                resumeSummary = "Built and tested an AI-powered campus recruitment microservice platform."
                jobDescription = "Develop Java microservices, persistence, messaging, and AI workflows."
            }
            $realAiSucceeded = $screening.data.mocked -eq $false
            if (-not $realAiSucceeded) {
                Start-Sleep -Seconds $PollSeconds
            }
        } catch {
            if ($attempt -eq $AiAttempts) {
                throw
            }
            Start-Sleep -Seconds $PollSeconds
        }
    }
    if (-not $realAiSucceeded) {
        throw "DashScope calls completed only through mocked fallback."
    }
    Add-Result -Name "real DashScope candidate screening" -Status "PASS" -Detail ("mocked=false, score={0}" -f $screening.data.score)

    $delivery = Invoke-Api -Method "POST" -Url ($BaseUrl.TrimEnd("/") + "/api/deliveries") -Token $studentToken -Body @{
        studentId = "S001"
        resumeId = "R001"
        jobId = "J001"
        resumeSourceFormat = "DOCX"
        resumeParseStatus = "TEXT_EXTRACTED"
        resumeParsedTextLength = 1280
    }
    $deliveryId = $delivery.data.deliveryId
    Add-Result -Name "delivery created" -Status "PASS" -Detail ("deliveryId={0}" -f $deliveryId)

    $deadline = (Get-Date).AddSeconds($TaskTimeoutSeconds)
    $rocketTasks = @()
    do {
        Start-Sleep -Seconds $PollSeconds
        $tasks = Invoke-Api -Method "GET" -Url ($BaseUrl.TrimEnd("/") + "/api/ai/candidates/screen/tasks?companyId=C001&deliveryId=$deliveryId") -Token $companyToken
        $rocketTasks = @($tasks.data | Where-Object { $_.source -eq "ROCKETMQ" })
        $completed = @($rocketTasks | Where-Object { $_.status -eq "COMPLETED" })
    } while ($completed.Count -eq 0 -and (Get-Date) -lt $deadline)

    if ($completed.Count -eq 0) {
        $statuses = @($rocketTasks | ForEach-Object { $_.status }) -join ","
        throw "No completed ROCKETMQ screening task before timeout. statuses=$statuses"
    }
    Start-Sleep -Seconds ($PollSeconds * 2)
    $tasks = Invoke-Api -Method "GET" -Url ($BaseUrl.TrimEnd("/") + "/api/ai/candidates/screen/tasks?companyId=C001&deliveryId=$deliveryId") -Token $companyToken
    $rocketTasks = @($tasks.data | Where-Object { $_.source -eq "ROCKETMQ" })
    if ($rocketTasks.Count -ne 1) {
        throw "Expected exactly one idempotent ROCKETMQ task, found $($rocketTasks.Count)."
    }
    Add-Result -Name "RocketMQ delivery-to-screening flow" -Status "PASS" -Detail ("taskId={0}, status={1}, count=1" -f $rocketTasks[0].taskId, $rocketTasks[0].status)
} catch {
    Add-Result -Name "distributed AI flow" -Status "FAIL" -Detail $_.Exception.Message
}

$failures = Write-Report
if ($failures -gt 0) {
    exit 1
}
exit 0
