param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TimeoutSeconds = 8
)

$ErrorActionPreference = "Stop"
$script:FailedChecks = 0

function Resolve-Url {
    param([string]$Path)
    return ("{0}{1}" -f $BaseUrl.TrimEnd("/"), $Path)
}

function Write-CheckResult {
    param(
        [string]$Name,
        [bool]$Ok,
        [string]$Detail
    )

    if ($Ok) {
        Write-Host ("[OK]   {0} ({1})" -f $Name, $Detail) -ForegroundColor Green
    } else {
        Write-Host ("[FAIL] {0} ({1})" -f $Name, $Detail) -ForegroundColor Red
        $script:FailedChecks++
    }
}

function Invoke-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null
    )

    $params = @{
        Uri = Resolve-Url -Path $Path
        Method = $Method
        TimeoutSec = $TimeoutSeconds
        Headers = @{ "Content-Type" = "application/json" }
    }

    if ($null -ne $Body) {
        $params.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    try {
        $response = Invoke-RestMethod @params
        $ok = $response.code -eq 0
        Write-CheckResult -Name $Name -Ok $ok -Detail ("code={0}" -f $response.code)
        return $response
    } catch {
        Write-CheckResult -Name $Name -Ok $false -Detail $_.Exception.Message
        return $null
    }
}

Write-Host "Running API smoke checks against $BaseUrl"

Invoke-Api -Name "auth login" -Method "POST" -Path "/api/auth/login" -Body @{
    username = "student"
    password = "123456"
} | Out-Null

Invoke-Api -Name "student profile" -Method "GET" -Path "/api/students/profile" | Out-Null
Invoke-Api -Name "job list" -Method "GET" -Path "/api/jobs" | Out-Null
Invoke-Api -Name "ai status" -Method "GET" -Path "/api/ai/status" | Out-Null

$delivery = Invoke-Api -Name "create delivery" -Method "POST" -Path "/api/deliveries" -Body @{
    studentId = "S001"
    resumeId = "R001"
    jobId = "J001"
}

if ($null -ne $delivery -and $null -ne $delivery.data.deliveryId) {
    Invoke-Api -Name "delivery events" -Method "GET" -Path "/api/deliveries/events" | Out-Null
} else {
    Write-CheckResult -Name "delivery events" -Ok $false -Detail "delivery creation did not return an id"
}

Invoke-Api -Name "admin dashboard" -Method "GET" -Path "/api/admin/dashboard" | Out-Null

if ($script:FailedChecks -gt 0) {
    Write-Host "$script:FailedChecks API smoke check(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "All API smoke checks passed." -ForegroundColor Green
