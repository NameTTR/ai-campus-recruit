param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$TimeoutSeconds = 8,
    [switch]$CoreOnly
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
        [object]$Body = $null,
        [string]$BearerToken = ""
    )

    $headers = @{ "Content-Type" = "application/json" }
    if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
        $headers["Authorization"] = "Bearer $BearerToken"
    }

    $params = @{
        Uri = Resolve-Url -Path $Path
        Method = $Method
        TimeoutSec = $TimeoutSeconds
        Headers = $headers
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

$studentLogin = Invoke-Api -Name "student auth login" -Method "POST" -Path "/api/auth/login" -Body @{
    username = "student"
    password = "123456"
}
$studentToken = if ($null -ne $studentLogin -and $null -ne $studentLogin.data) { $studentLogin.data.token } else { "" }

$companyLogin = Invoke-Api -Name "company auth login" -Method "POST" -Path "/api/auth/login" -Body @{
    username = "company"
    password = "123456"
}
$companyToken = if ($null -ne $companyLogin -and $null -ne $companyLogin.data) { $companyLogin.data.token } else { "" }

$adminLogin = Invoke-Api -Name "admin auth login" -Method "POST" -Path "/api/auth/login" -Body @{
    username = "admin"
    password = "123456"
}
$adminToken = if ($null -ne $adminLogin -and $null -ne $adminLogin.data) { $adminLogin.data.token } else { "" }

Invoke-Api -Name "auth me" -Method "GET" -Path "/api/auth/me" -BearerToken $studentToken | Out-Null
Invoke-Api -Name "student profile" -Method "GET" -Path "/api/students/profile" -BearerToken $studentToken | Out-Null
Invoke-Api -Name "admin dashboard" -Method "GET" -Path "/api/admin/dashboard" -BearerToken $adminToken | Out-Null

if (-not $CoreOnly) {
    Invoke-Api -Name "job list" -Method "GET" -Path "/api/jobs" -BearerToken $studentToken | Out-Null
    Invoke-Api -Name "ai status" -Method "GET" -Path "/api/ai/status" -BearerToken $studentToken | Out-Null
    Invoke-Api -Name "company delivery list" -Method "GET" -Path "/api/deliveries/company?companyId=C001" -BearerToken $companyToken | Out-Null

    $delivery = Invoke-Api -Name "create delivery" -Method "POST" -Path "/api/deliveries" -BearerToken $studentToken -Body @{
        studentId = "S001"
        resumeId = "R001"
        jobId = "J001"
    }

    if ($null -ne $delivery -and $null -ne $delivery.data.deliveryId) {
        Invoke-Api -Name "delivery events" -Method "GET" -Path "/api/deliveries/events" -BearerToken $studentToken | Out-Null
    } else {
        Write-CheckResult -Name "delivery events" -Ok $false -Detail "delivery creation did not return an id"
    }
}

if ($script:FailedChecks -gt 0) {
    Write-Host "$script:FailedChecks API smoke check(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "All API smoke checks passed." -ForegroundColor Green
