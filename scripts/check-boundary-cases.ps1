<#
.SYNOPSIS
Runs full boundary checks against the deployed gateway and operations endpoints.

.DESCRIPTION
This script validates authentication, RBAC, trusted identity propagation,
resume upload/error handling, delivery state transitions, AI edge inputs,
monitoring endpoints, and restore-script safety guards. It writes a Markdown
report to reports/deploy/boundary-cases-<timestamp>.md.

.EXAMPLE
.\scripts\check-boundary-cases.ps1 -BaseUrl http://192.168.6.130:8080 -Vm1Ip 192.168.6.130 -Vm2Ip 192.168.6.141 -Vm3Ip 192.168.6.142 -DemoPassword 123456
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Vm1Ip = "192.168.6.130",
    [string]$Vm2Ip = "192.168.6.141",
    [string]$Vm3Ip = "192.168.6.142",
    [string]$DemoPassword = "",
    [int]$TimeoutSeconds = 15,
    [string]$ReportDirectory = "",
    [switch]$SkipMonitoring,
    [switch]$SkipRestoreSafety
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $Root "reports\deploy"
}
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    $DemoPassword = $env:THREE_VM_DEMO_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($DemoPassword)) {
    $DemoPassword = "123456"
}

$script:Results = New-Object System.Collections.Generic.List[object]
$script:IssuedTokens = @{}

$BoundaryMatrix = @(
    @{ Category = "Auth"; Cases = "valid login, wrong password, missing token, malformed token" },
    @{ Category = "RBAC"; Cases = "student/admin isolation, company/student AI separation, admin-only observability" },
    @{ Category = "Trusted identity"; Cases = "gateway identity overrides conflicting body/query studentId and companyId" },
    @{ Category = "Resume"; Cases = "DOCX upload, missing multipart file, unknown resume detail/analyze" },
    @{ Category = "Delivery"; Cases = "unknown delivery ID, invalid enum status, negative parsed length normalization" },
    @{ Category = "AI"; Cases = "blank/minimal coach advice, long coach input, company task identity override" },
    @{ Category = "Monitoring"; Cases = "Prometheus, Grafana, Sentinel, node-exporter, Prometheus targets" },
    @{ Category = "Backup/restore"; Cases = "restore requires -Force and rejects a missing archive before touching VM data" }
)

function Resolve-Url {
    param([string]$Path)
    return "{0}{1}" -f $BaseUrl.TrimEnd("/"), $Path
}

function Add-Result {
    param(
        [string]$Category,
        [string]$Name,
        [ValidateSet("PASS", "FAIL", "SKIPPED")]
        [string]$Status,
        [string]$Detail
    )

    $script:Results.Add([pscustomobject]@{
        Category = $Category
        Name = $Name
        Status = $Status
        Detail = $Detail
    })

    $color = switch ($Status) {
        "PASS" { "Green" }
        "FAIL" { "Red" }
        default { "Yellow" }
    }
    Write-Host ("[{0}] {1} / {2} - {3}" -f $Status, $Category, $Name, $Detail) -ForegroundColor $color
}

function ConvertTo-SafeDetail {
    param([string]$Value)
    if ($null -eq $Value) {
        return ""
    }
    $text = [string]$Value
    foreach ($token in $script:IssuedTokens.Values) {
        if (-not [string]::IsNullOrWhiteSpace($token)) {
            $text = $text.Replace($token, "<redacted-token>")
        }
    }
    return $text.Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Read-ErrorResponseBody {
    param([object]$ErrorRecord)

    if (-not [string]::IsNullOrWhiteSpace($ErrorRecord.ErrorDetails.Message)) {
        return $ErrorRecord.ErrorDetails.Message
    }
    $response = $ErrorRecord.Exception.Response
    if ($null -eq $response) {
        return ""
    }
    try {
        $stream = $response.GetResponseStream()
        if ($null -eq $stream) {
            return ""
        }
        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch {
        return ""
    }
}

function ConvertFrom-JsonOrNull {
    param([string]$Content)
    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $null
    }
    try {
        return $Content | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Invoke-Http {
    param(
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$Token = "",
        [hashtable]$Headers = @{}
    )

    $requestHeaders = @{}
    foreach ($key in $Headers.Keys) {
        $requestHeaders[$key] = $Headers[$key]
    }
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $requestHeaders.Authorization = "Bearer $Token"
    }

    $params = @{
        Uri = $Url
        Method = $Method
        Headers = $requestHeaders
        TimeoutSec = $TimeoutSeconds
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $params.ContentType = "application/json; charset=utf-8"
        $params.Body = $Body | ConvertTo-Json -Depth 12
    }

    try {
        $response = Invoke-WebRequest @params
        $content = [string]$response.Content
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = $content
            Json = ConvertFrom-JsonOrNull -Content $content
            Error = ""
        }
    } catch {
        $status = 0
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        $content = Read-ErrorResponseBody -ErrorRecord $_
        return [pscustomobject]@{
            StatusCode = $status
            Content = $content
            Json = ConvertFrom-JsonOrNull -Content $content
            Error = $_.Exception.Message
        }
    }
}

function Test-Api {
    param(
        [string]$Category,
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [string]$Token = "",
        [int[]]$ExpectedStatus = @(200),
        [Nullable[int]]$ExpectedCode = $null,
        [scriptblock]$Assert = $null
    )

    $result = Invoke-Http -Method $Method -Url (Resolve-Url $Path) -Body $Body -Token $Token
    $ok = $ExpectedStatus -contains $result.StatusCode
    $detailParts = New-Object System.Collections.Generic.List[string]
    $detailParts.Add("http=$($result.StatusCode)")
    if ($null -ne $result.Json -and $null -ne $result.Json.code) {
        $detailParts.Add("code=$($result.Json.code)")
        if ($null -ne $ExpectedCode) {
            $ok = $ok -and ([int]$result.Json.code -eq [int]$ExpectedCode)
        }
    } elseif ($null -ne $ExpectedCode) {
        $ok = $false
        $detailParts.Add("code=<missing>")
    }
    if ($Assert) {
        try {
            $assertion = & $Assert $result
            if ($assertion -ne $true) {
                $ok = $false
                $detailParts.Add([string]$assertion)
            }
        } catch {
            $ok = $false
            $detailParts.Add($_.Exception.Message)
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($result.Error)) {
        $detailParts.Add($result.Error)
    }

    Add-Result -Category $Category -Name $Name -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Detail (ConvertTo-SafeDetail ($detailParts -join "; "))
    return $result
}

function Login-DemoUser {
    param([string]$Username)

    $response = Test-Api -Category "Auth" -Name "login $Username" -Method "POST" -Path "/api/auth/login" -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
        username = $Username
        password = $DemoPassword
    } -Assert {
        param($result)
        if ($null -ne $result.Json -and $null -ne $result.Json.data -and -not [string]::IsNullOrWhiteSpace($result.Json.data.token)) {
            return $true
        }
        return "token missing"
    }
    if ($null -ne $response.Json -and $null -ne $response.Json.data) {
        $script:IssuedTokens[$Username] = [string]$response.Json.data.token
        return [string]$response.Json.data.token
    }
    return ""
}

function New-TestDocx {
    $id = [guid]::NewGuid().ToString("N")
    $tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) "ai-campus-boundary-$id"
    $docxPath = Join-Path ([System.IO.Path]::GetTempPath()) "ai-campus-boundary-$id.docx"
    New-Item -ItemType Directory -Force -Path (Join-Path $tempRoot "_rels") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $tempRoot "word\_rels") | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $tempRoot "word") | Out-Null
    Set-Content -LiteralPath (Join-Path $tempRoot "[Content_Types].xml") -Encoding UTF8 -Value @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
'@
    Set-Content -LiteralPath (Join-Path $tempRoot "_rels\.rels") -Encoding UTF8 -Value @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>
'@
    Set-Content -LiteralPath (Join-Path $tempRoot "word\document.xml") -Encoding UTF8 -Value @'
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    <w:p><w:r><w:t>Java Spring Boot MySQL Redis Docker campus recruitment boundary resume.</w:t></w:r></w:p>
  </w:body>
</w:document>
'@
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [System.IO.Compression.ZipFile]::CreateFromDirectory($tempRoot, $docxPath)
    Remove-Item -LiteralPath $tempRoot -Recurse -Force
    return $docxPath
}

function Invoke-MultipartUpload {
    param(
        [string]$Url,
        [string]$FilePath,
        [string]$Token
    )

    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $client.DefaultRequestHeaders.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue -ArgumentList "Bearer", $Token
    }
    $content = New-Object System.Net.Http.MultipartFormDataContent
    $bytes = [System.IO.File]::ReadAllBytes($FilePath)
    $fileContent = New-Object System.Net.Http.ByteArrayContent -ArgumentList @(,$bytes)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    $content.Add($fileContent, "file", [System.IO.Path]::GetFileName($FilePath))
    try {
        $response = $client.PostAsync($Url, $content).GetAwaiter().GetResult()
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{
            StatusCode = [int]$response.StatusCode
            Content = $body
            Json = ConvertFrom-JsonOrNull -Content $body
            Error = ""
        }
    } catch {
        return [pscustomobject]@{
            StatusCode = 0
            Content = ""
            Json = $null
            Error = $_.Exception.Message
        }
    } finally {
        $content.Dispose()
        $client.Dispose()
    }
}

function Test-MonitorUrl {
    param(
        [string]$Name,
        [string]$Url,
        [scriptblock]$Assert = $null
    )

    if ($SkipMonitoring) {
        Add-Result -Category "Monitoring" -Name $Name -Status "SKIPPED" -Detail "monitoring checks disabled"
        return
    }
    $result = Invoke-Http -Method "GET" -Url $Url
    $ok = $result.StatusCode -ge 200 -and $result.StatusCode -lt 400
    $detail = "http=$($result.StatusCode)"
    if ($Assert) {
        try {
            $assertion = & $Assert $result
            if ($assertion -ne $true) {
                $ok = $false
                $detail = "$detail; $assertion"
            }
        } catch {
            $ok = $false
            $detail = "$detail; $($_.Exception.Message)"
        }
    }
    Add-Result -Category "Monitoring" -Name $Name -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Detail (ConvertTo-SafeDetail $detail)
}

function Test-RestoreSafety {
    if ($SkipRestoreSafety) {
        Add-Result -Category "Backup/restore" -Name "restore safety guards" -Status "SKIPPED" -Detail "restore safety checks disabled"
        return
    }

    try {
        & (Join-Path $Root "scripts\restore-three-vm-data.ps1") -Archive "__missing_boundary_archive__.tar.gz" *> $null
        Add-Result -Category "Backup/restore" -Name "restore requires Force" -Status "FAIL" -Detail "restore script continued without -Force"
    } catch {
        $ok = $_.Exception.Message -like "*-Force*"
        Add-Result -Category "Backup/restore" -Name "restore requires Force" -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Detail (ConvertTo-SafeDetail $_.Exception.Message)
    }

    try {
        & (Join-Path $Root "scripts\restore-three-vm-data.ps1") -Archive "__missing_boundary_archive__.tar.gz" -Force *> $null
        Add-Result -Category "Backup/restore" -Name "restore rejects missing archive" -Status "FAIL" -Detail "restore script continued with a missing archive"
    } catch {
        $ok = $_.Exception.Message -like "*Archive not found*"
        Add-Result -Category "Backup/restore" -Name "restore rejects missing archive" -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Detail (ConvertTo-SafeDetail $_.Exception.Message)
    }
}

function Write-Report {
    $passCount = @($script:Results | Where-Object { $_.Status -eq "PASS" }).Count
    $failCount = @($script:Results | Where-Object { $_.Status -eq "FAIL" }).Count
    $skipCount = @($script:Results | Where-Object { $_.Status -eq "SKIPPED" }).Count
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Boundary Cases Validation Report")
    $lines.Add("")
    $lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    $lines.Add("- Gateway: ``$BaseUrl``")
    $lines.Add("- VM1: ``$Vm1Ip``")
    $lines.Add("- VM2: ``$Vm2Ip``")
    $lines.Add("- VM3: ``$Vm3Ip``")
    $lines.Add("- Summary: PASS=$passCount, FAIL=$failCount, SKIPPED=$skipCount")
    $lines.Add("")
    $lines.Add("## Boundary Matrix")
    $lines.Add("")
    $lines.Add("| Category | Planned edge cases |")
    $lines.Add("| --- | --- |")
    foreach ($item in $BoundaryMatrix) {
        $lines.Add("| $($item.Category) | $($item.Cases) |")
    }
    $lines.Add("")
    $lines.Add("## Results")
    $lines.Add("")
    $lines.Add("| Status | Category | Check | Detail |")
    $lines.Add("| --- | --- | --- | --- |")
    foreach ($result in $script:Results) {
        $lines.Add("| $($result.Status) | $($result.Category) | $($result.Name) | $(ConvertTo-SafeDetail $result.Detail) |")
    }

    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    $path = Join-Path $ReportDirectory ("boundary-cases-{0}.md" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    Set-Content -LiteralPath $path -Encoding UTF8 -Value $lines
    Write-Host "Report written: $path" -ForegroundColor Cyan
    return $failCount
}

Write-Host "Running boundary checks against $BaseUrl" -ForegroundColor Cyan

$studentToken = Login-DemoUser -Username "student"
$companyToken = Login-DemoUser -Username "company"
$adminToken = Login-DemoUser -Username "admin"

Test-Api -Category "Auth" -Name "wrong password rejected" -Method "POST" -Path "/api/auth/login" -ExpectedStatus @(401) -ExpectedCode 401 -Body @{
    username = "student"
    password = "definitely-wrong"
} | Out-Null
Test-Api -Category "Auth" -Name "missing token rejected" -Method "GET" -Path "/api/jobs" -ExpectedStatus @(401) -ExpectedCode 401 | Out-Null
Test-Api -Category "Auth" -Name "malformed token rejected" -Method "GET" -Path "/api/jobs" -ExpectedStatus @(401) -ExpectedCode 401 -Token "not-a-jwt" | Out-Null

Test-Api -Category "RBAC" -Name "student cannot access admin dashboard" -Method "GET" -Path "/api/admin/dashboard" -Token $studentToken -ExpectedStatus @(403) -ExpectedCode 403 | Out-Null
Test-Api -Category "RBAC" -Name "company cannot generate interview questions" -Method "POST" -Path "/api/ai/interview/questions" -Token $companyToken -ExpectedStatus @(403) -ExpectedCode 403 -Body @{
    studentId = "S001"
    targetRole = "Java"
} | Out-Null
Test-Api -Category "RBAC" -Name "student cannot screen candidates" -Method "POST" -Path "/api/ai/candidates/screen" -Token $studentToken -ExpectedStatus @(403) -ExpectedCode 403 -Body @{
    companyId = "C001"
    deliveryId = "D001"
} | Out-Null
Test-Api -Category "RBAC" -Name "company cannot call student coach" -Method "POST" -Path "/api/ai/coach/advice" -Token $companyToken -ExpectedStatus @(403) -ExpectedCode 403 -Body @{
    studentId = "S001"
    targetRole = "Java"
} | Out-Null
Test-Api -Category "RBAC" -Name "admin dashboard allowed" -Method "GET" -Path "/api/admin/dashboard" -Token $adminToken -ExpectedStatus @(200) -ExpectedCode 0 | Out-Null
Test-Api -Category "RBAC" -Name "student cannot read AI observability" -Method "GET" -Path "/api/ai/observability/summary" -Token $studentToken -ExpectedStatus @(403) -ExpectedCode 403 | Out-Null
Test-Api -Category "RBAC" -Name "admin can read AI observability" -Method "GET" -Path "/api/ai/observability/summary" -Token $adminToken -ExpectedStatus @(200) -ExpectedCode 0 | Out-Null

$createdDelivery = Test-Api -Category "Trusted identity" -Name "student delivery body identity overridden" -Method "POST" -Path "/api/deliveries" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
    studentId = "S999"
    resumeId = "R001"
    jobId = "J001"
} -Assert {
    param($result)
    if ($result.Json.data.studentId -eq "S001") { return $true }
    return "studentId=$($result.Json.data.studentId)"
}
$deliveryId = if ($null -ne $createdDelivery.Json -and $null -ne $createdDelivery.Json.data) { [string]$createdDelivery.Json.data.deliveryId } else { "D001" }

Test-Api -Category "Trusted identity" -Name "company query identity overridden" -Method "GET" -Path "/api/deliveries/company?companyId=C999" -Token $companyToken -ExpectedStatus @(200) -ExpectedCode 0 -Assert {
    param($result)
    $bad = @($result.Json.data | Where-Object { $_.companyId -ne "C001" })
    if ($bad.Count -eq 0) { return $true }
    return "found non-C001 company rows"
} | Out-Null

Test-Api -Category "Trusted identity" -Name "student coach body identity overridden" -Method "POST" -Path "/api/ai/coach/advice" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
    studentId = "S999"
    targetRole = "Java Backend Intern"
    skills = @("Java", "Spring Boot")
    recentDeliveries = @()
    interviewWeaknesses = @()
    careerGoal = "Get an internship offer"
    weeks = 4
} -Assert {
    param($result)
    if ($result.Json.data.studentId -eq "S001") { return $true }
    return "studentId=$($result.Json.data.studentId)"
} | Out-Null

$docxPath = New-TestDocx
try {
    $upload = Invoke-MultipartUpload -Url (Resolve-Url "/api/resumes/upload") -FilePath $docxPath -Token $studentToken
    $ok = $upload.StatusCode -eq 200 -and $upload.Json.code -eq 0 -and $upload.Json.data.sourceFormat -eq "DOCX" -and $upload.Json.data.parseStatus -eq "TEXT_EXTRACTED" -and $upload.Json.data.parsedTextLength -gt 0
    Add-Result -Category "Resume" -Name "DOCX upload extracts text" -Status $(if ($ok) { "PASS" } else { "FAIL" }) -Detail ("http={0}; code={1}; format={2}; parseStatus={3}; parsedTextLength={4}" -f $upload.StatusCode, $upload.Json.code, $upload.Json.data.sourceFormat, $upload.Json.data.parseStatus, $upload.Json.data.parsedTextLength)
} finally {
    if (Test-Path -LiteralPath $docxPath) {
        Remove-Item -LiteralPath $docxPath -Force
    }
}

Test-Api -Category "Resume" -Name "upload without file returns ApiResponse" -Method "POST" -Path "/api/resumes/upload" -Token $studentToken -ExpectedStatus @(400) -ExpectedCode 400 | Out-Null
Test-Api -Category "Resume" -Name "unknown resume detail fails" -Method "GET" -Path "/api/resumes/R-NOT-FOUND-BOUNDARY" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 1 | Out-Null
Test-Api -Category "Resume" -Name "unknown resume analyze fails" -Method "POST" -Path "/api/resumes/R-NOT-FOUND-BOUNDARY/analyze" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 1 | Out-Null

Test-Api -Category "Delivery" -Name "invalid status returns ApiResponse" -Method "PUT" -Path "/api/deliveries/$deliveryId/status?status=NOT_A_STATUS" -Token $companyToken -ExpectedStatus @(400) -ExpectedCode 400 | Out-Null
Test-Api -Category "Delivery" -Name "unknown delivery status update fails" -Method "PUT" -Path "/api/deliveries/D-NOT-FOUND-BOUNDARY/status?status=INTERVIEW" -Token $companyToken -ExpectedStatus @(200) -ExpectedCode 1 | Out-Null
Test-Api -Category "Delivery" -Name "negative parsed length normalized" -Method "POST" -Path "/api/deliveries" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
    studentId = "S999"
    resumeId = "R001"
    jobId = "J001"
    resumeParsedTextLength = -10
} -Assert {
    param($result)
    if ($result.Json.data.resumeParsedTextLength -eq 0) { return $true }
    return "resumeParsedTextLength=$($result.Json.data.resumeParsedTextLength)"
} | Out-Null

Test-Api -Category "AI" -Name "blank coach advice handled" -Method "POST" -Path "/api/ai/coach/advice" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{} -Assert {
    param($result)
    if ($null -ne $result.Json.data -and -not [string]::IsNullOrWhiteSpace($result.Json.data.headline)) { return $true }
    return "coach headline missing"
} | Out-Null

$longGoal = ("Java Spring Cloud Alibaba Redis Docker RocketMQ MySQL " * 120)
Test-Api -Category "AI" -Name "long coach advice input handled" -Method "POST" -Path "/api/ai/coach/advice" -Token $studentToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
    studentId = "S999"
    targetRole = $longGoal
    skills = @("Java", "Spring Boot", "Spring Cloud Alibaba", "MySQL", "Redis", "Docker")
    recentDeliveries = @("J001 SUBMITTED")
    interviewWeaknesses = @("needs stronger system design examples")
    careerGoal = $longGoal
    weeks = 12
} -Assert {
    param($result)
    if ($null -ne $result.Json.data -and $result.Json.data.studentId -eq "S001") { return $true }
    return "studentId=$($result.Json.data.studentId)"
} | Out-Null

Test-Api -Category "AI" -Name "company screening task identity overridden" -Method "POST" -Path "/api/ai/candidates/screen/tasks" -Token $companyToken -ExpectedStatus @(200) -ExpectedCode 0 -Body @{
    deliveryId = "D-BOUNDARY-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
    companyId = "C999"
    studentId = "S001"
    resumeId = "R001"
    jobId = "J001"
    targetRole = "Java Backend Intern"
    skills = @("Java", "Spring Boot")
} -Assert {
    param($result)
    if ($result.Json.data.companyId -eq "C001") { return $true }
    return "companyId=$($result.Json.data.companyId)"
} | Out-Null

Test-MonitorUrl -Name "Prometheus ready" -Url ("http://{0}:9090/-/ready" -f $Vm1Ip) -Assert {
    param($result)
    if ($result.Content -match "ready") { return $true }
    return "ready text missing"
}
Test-MonitorUrl -Name "Grafana login" -Url ("http://{0}:3000/login" -f $Vm1Ip)
Test-MonitorUrl -Name "Sentinel dashboard" -Url ("http://{0}:8858/" -f $Vm1Ip)
foreach ($vm in @($Vm1Ip, $Vm2Ip, $Vm3Ip)) {
    Test-MonitorUrl -Name "node-exporter $vm" -Url ("http://{0}:9100/metrics" -f $vm) -Assert {
        param($result)
        if ($result.Content -match "node_exporter_build_info") { return $true }
        return "node exporter metric missing"
    }
}
Test-MonitorUrl -Name "Prometheus targets all up" -Url ("http://{0}:9090/api/v1/targets" -f $Vm1Ip) -Assert {
    param($result)
    if ($null -eq $result.Json -or $null -eq $result.Json.data) {
        return "targets json missing"
    }
    $active = @($result.Json.data.activeTargets)
    $down = @($active | Where-Object { $_.health -ne "up" })
    if ($active.Count -gt 0 -and $down.Count -eq 0) {
        return $true
    }
    return "activeTargets=$($active.Count), down=$($down.Count)"
}

Test-RestoreSafety

$failures = Write-Report
if ($failures -gt 0) {
    exit 1
}
exit 0
