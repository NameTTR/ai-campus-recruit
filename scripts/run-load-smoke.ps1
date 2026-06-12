<#
.SYNOPSIS
Runs a lightweight gateway load smoke test for the campus recruitment MVP.

.DESCRIPTION
Starts concurrent PowerShell worker jobs. Each worker logs in with the student,
company, and admin demo accounts, then repeatedly calls core read endpoints plus
RAG retrieval probes and records pass/fail counts plus average and P95 latency.
Optional future endpoints returning 404 are recorded as SKIPPED instead of
failing the run.

The script never writes demo passwords or bearer tokens to console output or to
the Markdown report.

.PARAMETER BaseUrl
Gateway base URL. Use the VM1 gateway URL for the three-VM deployment.

.PARAMETER DemoPassword
Demo account password. If omitted, THREE_VM_DEMO_PASSWORD is used, then the
local demo fallback is used.

.PARAMETER Users
Number of concurrent virtual users.

.PARAMETER Iterations
Business endpoint iterations per virtual user.

.PARAMETER TimeoutSeconds
Per-request timeout.

.PARAMETER ReportDirectory
Directory for the Markdown report. Defaults to reports/deploy under the repo.

.EXAMPLE
$password = Read-Host "Demo password"
.\scripts\run-load-smoke.ps1 -BaseUrl http://192.168.6.130:8080 -DemoPassword $password -Users 5 -Iterations 10
#>
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string]$BaseUrl = "http://localhost:8080",

    [string]$DemoPassword = "",

    [ValidateRange(1, 200)]
    [int]$Users = 3,

    [ValidateRange(1, 10000)]
    [int]$Iterations = 3,

    [ValidateRange(1, 300)]
    [int]$TimeoutSeconds = 10,

    [string]$ReportDirectory = ""
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

$script:ControllerSecretsToRedact = New-Object System.Collections.Generic.List[string]
if (-not [string]::IsNullOrWhiteSpace($DemoPassword)) {
    $script:ControllerSecretsToRedact.Add($DemoPassword)
}

function ConvertTo-SafeControllerText {
    param([object]$Value)

    if ($null -eq $Value) {
        return ""
    }

    $text = [string]$Value
    foreach ($secret in $script:ControllerSecretsToRedact) {
        if (-not [string]::IsNullOrWhiteSpace($secret) -and $secret.Length -ge 4) {
            $text = $text.Replace($secret, "<redacted-secret>")
        }
    }
    return $text.Replace("`r", " ").Replace("`n", " ")
}

function New-ControllerResult {
    param(
        [int]$Worker,
        [int]$Iteration,
        [string]$Endpoint,
        [string]$Method,
        [string]$Path,
        [string]$Role,
        [ValidateSet("PASS", "FAIL", "SKIPPED")]
        [string]$Status,
        [int]$HttpStatus,
        [object]$ApiCode,
        [double]$DurationMs,
        [string]$Detail
    )

    return [pscustomobject]@{
        Timestamp = (Get-Date).ToString("o")
        Worker = $Worker
        Iteration = $Iteration
        Endpoint = $Endpoint
        Method = $Method
        Path = $Path
        Role = $Role
        Status = $Status
        HttpStatus = $HttpStatus
        ApiCode = $ApiCode
        DurationMs = [math]::Round($DurationMs, 2)
        Detail = ConvertTo-SafeControllerText $Detail
    }
}

function Get-AverageLatency {
    param([double[]]$Values)

    if ($null -eq $Values -or $Values.Count -eq 0) {
        return 0
    }

    $measurement = $Values | Measure-Object -Average
    return [math]::Round([double]$measurement.Average, 2)
}

function Get-PercentileLatency {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if ($null -eq $Values -or $Values.Count -eq 0) {
        return 0
    }

    $sorted = @($Values | Sort-Object)
    $index = [math]::Ceiling(($Percentile / 100) * $sorted.Count) - 1
    if ($index -lt 0) {
        $index = 0
    }
    if ($index -ge $sorted.Count) {
        $index = $sorted.Count - 1
    }
    return [math]::Round([double]$sorted[$index], 2)
}

function ConvertTo-MarkdownCell {
    param([object]$Value)

    if ($null -eq $Value) {
        return ""
    }

    return ([string]$Value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function Write-LoadSmokeReport {
    param(
        [object[]]$Results,
        [string]$ReportDirectory,
        [string]$BaseUrl,
        [int]$Users,
        [int]$Iterations,
        [int]$TimeoutSeconds,
        [string]$PasswordSource
    )

    $resultsArray = @($Results)
    $passCount = @($resultsArray | Where-Object { $_.Status -eq "PASS" }).Count
    $failCount = @($resultsArray | Where-Object { $_.Status -eq "FAIL" }).Count
    $skippedCount = @($resultsArray | Where-Object { $_.Status -eq "SKIPPED" }).Count
    $latencies = @($resultsArray | Where-Object { $_.DurationMs -gt 0 } | ForEach-Object { [double]$_.DurationMs })
    $averageLatency = Get-AverageLatency -Values $latencies
    $p95Latency = Get-PercentileLatency -Values $latencies -Percentile 95

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# Load Smoke Report")
    $lines.Add("")
    $lines.Add("- Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')")
    $lines.Add("- Base URL: ``$BaseUrl``")
    $lines.Add("- Users: ``$Users``")
    $lines.Add("- Iterations per user: ``$Iterations``")
    $lines.Add("- Timeout seconds: ``$TimeoutSeconds``")
    $lines.Add("- Demo password source: ``$PasswordSource``")
    $lines.Add("- Secret handling: demo passwords and bearer tokens are not written to this report.")
    $lines.Add("")
    $lines.Add("## Overall")
    $lines.Add("")
    $lines.Add("| PASS | FAIL | SKIPPED | Average latency ms | P95 latency ms |")
    $lines.Add("| ---: | ---: | ---: | ---: | ---: |")
    $lines.Add("| $passCount | $failCount | $skippedCount | $averageLatency | $p95Latency |")
    $lines.Add("")
    $lines.Add("## Endpoint Summary")
    $lines.Add("")
    $lines.Add("| Endpoint | Method | Path | Role | Count | PASS | FAIL | SKIPPED | Average ms | P95 ms |")
    $lines.Add("| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |")

    foreach ($group in ($resultsArray | Group-Object Endpoint, Method, Path, Role | Sort-Object Name)) {
        $items = @($group.Group)
        if ($items.Count -eq 0) {
            continue
        }
        $sample = $items[0]
        $endpointLatencies = @($items | Where-Object { $_.DurationMs -gt 0 } | ForEach-Object { [double]$_.DurationMs })
        $lines.Add(("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} | {8} | {9} |" -f `
            (ConvertTo-MarkdownCell $sample.Endpoint),
            (ConvertTo-MarkdownCell $sample.Method),
            (ConvertTo-MarkdownCell $sample.Path),
            (ConvertTo-MarkdownCell $sample.Role),
            $items.Count,
            @($items | Where-Object { $_.Status -eq "PASS" }).Count,
            @($items | Where-Object { $_.Status -eq "FAIL" }).Count,
            @($items | Where-Object { $_.Status -eq "SKIPPED" }).Count,
            (Get-AverageLatency -Values $endpointLatencies),
            (Get-PercentileLatency -Values $endpointLatencies -Percentile 95)))
    }

    $interesting = @($resultsArray | Where-Object { $_.Status -ne "PASS" } | Select-Object -First 100)
    $lines.Add("")
    $lines.Add("## Failures And Optional Skips")
    $lines.Add("")
    if ($interesting.Count -eq 0) {
        $lines.Add("No failed or skipped calls.")
    } else {
        $lines.Add("| Status | Worker | Iteration | Endpoint | HTTP | Code | Duration ms | Detail |")
        $lines.Add("| --- | ---: | ---: | --- | ---: | --- | ---: | --- |")
        foreach ($item in $interesting) {
            $lines.Add(("| {0} | {1} | {2} | {3} | {4} | {5} | {6} | {7} |" -f `
                (ConvertTo-MarkdownCell $item.Status),
                $item.Worker,
                $item.Iteration,
                (ConvertTo-MarkdownCell $item.Endpoint),
                $item.HttpStatus,
                (ConvertTo-MarkdownCell $item.ApiCode),
                $item.DurationMs,
                (ConvertTo-MarkdownCell $item.Detail)))
        }
        if (@($resultsArray | Where-Object { $_.Status -ne "PASS" }).Count -gt $interesting.Count) {
            $lines.Add("")
            $lines.Add("Only the first 100 failed or skipped calls are shown.")
        }
    }

    $lines.Add("")
    $lines.Add("## Scenario")
    $lines.Add("")
    $lines.Add("- Login once per worker for ``student``, ``company``, and ``admin``.")
    $lines.Add("- Per iteration: ``GET /api/jobs`` as student.")
    $lines.Add("- Per iteration: ``GET /api/deliveries/my`` as student.")
    $lines.Add("- Per iteration: ``GET /api/deliveries/company?companyId=C001`` as company.")
    $lines.Add("- Per iteration: ``GET /api/ai/status`` as student.")
    $lines.Add("- Per iteration: ``POST /api/ai/knowledge/search`` and ``GET /api/ai/knowledge/stats`` as admin; HTTP 404 is recorded as ``SKIPPED``.")
    $lines.Add("- Per iteration: ``POST /api/ai/knowledge/answer`` as admin with ``useAi=false`` to exercise RAG retrieval without external model cost; HTTP 404 is recorded as ``SKIPPED``.")
    $lines.Add("- Per iteration: ``GET /api/notifications/my`` as student; HTTP 404 is recorded as ``SKIPPED``.")

    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    $path = Join-Path $ReportDirectory ("load-smoke-{0}.md" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    Set-Content -LiteralPath $path -Value $lines -Encoding UTF8
    return [pscustomobject]@{
        Path = $path
        Pass = $passCount
        Fail = $failCount
        Skipped = $skippedCount
        AverageLatencyMs = $averageLatency
        P95LatencyMs = $p95Latency
    }
}

$workerScript = {
    param(
        [int]$WorkerId,
        [string]$BaseUrlArg,
        [string]$DemoPasswordArg,
        [int]$IterationsArg,
        [int]$TimeoutSecondsArg
    )

    $ErrorActionPreference = "Stop"
    $ProgressPreference = "SilentlyContinue"
    $script:SecretsToRedact = New-Object System.Collections.Generic.List[string]
    if (-not [string]::IsNullOrWhiteSpace($DemoPasswordArg)) {
        $script:SecretsToRedact.Add($DemoPasswordArg)
    }

    function Register-Secret {
        param([string]$Value)

        if (-not [string]::IsNullOrWhiteSpace($Value)) {
            $script:SecretsToRedact.Add($Value)
        }
    }

    function ConvertTo-SafeText {
        param([object]$Value)

        if ($null -eq $Value) {
            return ""
        }

        $text = [string]$Value
        foreach ($secret in $script:SecretsToRedact) {
            if (-not [string]::IsNullOrWhiteSpace($secret) -and $secret.Length -ge 4) {
                $text = $text.Replace($secret, "<redacted-secret>")
            }
        }
        return $text.Replace("`r", " ").Replace("`n", " ")
    }

    function Resolve-Url {
        param([string]$Path)
        return "{0}{1}" -f $BaseUrlArg.TrimEnd("/"), $Path
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
            if ($response -is [System.Net.Http.HttpResponseMessage]) {
                return $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            }
        } catch {
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

    function New-LoadResult {
        param(
            [int]$Iteration,
            [string]$Endpoint,
            [string]$Method,
            [string]$Path,
            [string]$Role,
            [ValidateSet("PASS", "FAIL", "SKIPPED")]
            [string]$Status,
            [int]$HttpStatus,
            [object]$ApiCode,
            [double]$DurationMs,
            [string]$Detail
        )

        return [pscustomobject]@{
            Timestamp = (Get-Date).ToString("o")
            Worker = $WorkerId
            Iteration = $Iteration
            Endpoint = $Endpoint
            Method = $Method
            Path = $Path
            Role = $Role
            Status = $Status
            HttpStatus = $HttpStatus
            ApiCode = $ApiCode
            DurationMs = [math]::Round($DurationMs, 2)
            Detail = ConvertTo-SafeText $Detail
        }
    }

    function Invoke-LoadRequest {
        param(
            [int]$Iteration,
            [string]$Endpoint,
            [string]$Method,
            [string]$Path,
            [string]$Role,
            [object]$Body = $null,
            [string]$Token = "",
            [bool]$Optional404 = $false
        )

        if (-not [string]::IsNullOrWhiteSpace($Role) -and $Endpoint -notlike "login *" -and [string]::IsNullOrWhiteSpace($Token)) {
            $record = New-LoadResult `
                -Iteration $Iteration `
                -Endpoint $Endpoint `
                -Method $Method `
                -Path $Path `
                -Role $Role `
                -Status "FAIL" `
                -HttpStatus 0 `
                -ApiCode "" `
                -DurationMs 0 `
                -Detail "bearer token missing because role login failed"
            return [pscustomobject]@{ Record = $record; Json = $null }
        }

        $headers = @{ Accept = "application/json" }
        if (-not [string]::IsNullOrWhiteSpace($Token)) {
            $headers.Authorization = "Bearer $Token"
        }

        $params = @{
            Uri = Resolve-Url -Path $Path
            Method = $Method
            Headers = $headers
            TimeoutSec = $TimeoutSecondsArg
            UseBasicParsing = $true
        }
        if ($null -ne $Body) {
            $params.ContentType = "application/json; charset=utf-8"
            $params.Body = $Body | ConvertTo-Json -Depth 12
        }

        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        $httpStatus = 0
        $content = ""
        $errorText = ""

        try {
            $response = Invoke-WebRequest @params
            $httpStatus = [int]$response.StatusCode
            $content = [string]$response.Content
        } catch {
            if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
                $httpStatus = [int]$_.Exception.Response.StatusCode
            }
            $content = Read-ErrorResponseBody -ErrorRecord $_
            $errorText = $_.Exception.Message
        } finally {
            $stopwatch.Stop()
        }

        $json = ConvertFrom-JsonOrNull -Content $content
        $hasApiCode = $false
        $apiCode = ""
        $message = ""
        if ($null -ne $json) {
            $codeProperty = $json.PSObject.Properties["code"]
            if ($null -ne $codeProperty) {
                $hasApiCode = $true
                $apiCode = $codeProperty.Value
            }
            $messageProperty = $json.PSObject.Properties["message"]
            if ($null -ne $messageProperty) {
                $message = [string]$messageProperty.Value
            }
        }

        $status = "FAIL"
        if ($Optional404 -and $httpStatus -eq 404) {
            $status = "SKIPPED"
        } elseif ($httpStatus -ge 200 -and $httpStatus -lt 300 -and $hasApiCode -and [int]$apiCode -eq 0) {
            $status = "PASS"
        }

        $detailParts = New-Object System.Collections.Generic.List[string]
        $detailParts.Add("http=$httpStatus")
        if ($hasApiCode) {
            $detailParts.Add("code=$apiCode")
        } else {
            $detailParts.Add("code=<missing>")
        }
        if ($status -eq "SKIPPED") {
            $detailParts.Add("optional endpoint returned 404")
        }
        if (-not [string]::IsNullOrWhiteSpace($message)) {
            $detailParts.Add("message=$message")
        }
        if (-not [string]::IsNullOrWhiteSpace($errorText) -and $status -ne "SKIPPED") {
            $detailParts.Add($errorText)
        }

        $record = New-LoadResult `
            -Iteration $Iteration `
            -Endpoint $Endpoint `
            -Method $Method `
            -Path $Path `
            -Role $Role `
            -Status $status `
            -HttpStatus $httpStatus `
            -ApiCode $apiCode `
            -DurationMs $stopwatch.Elapsed.TotalMilliseconds `
            -Detail ($detailParts -join "; ")

        return [pscustomobject]@{ Record = $record; Json = $json }
    }

    function Login-DemoUser {
        param([string]$Username)

        $attempt = Invoke-LoadRequest `
            -Iteration 0 `
            -Endpoint "login $Username" `
            -Method "POST" `
            -Path "/api/auth/login" `
            -Role $Username `
            -Body @{
                username = $Username
                password = $DemoPasswordArg
            }

        $token = ""
        if ($attempt.Record.Status -eq "PASS" -and $null -ne $attempt.Json -and $null -ne $attempt.Json.data) {
            $token = [string]$attempt.Json.data.token
            if (-not [string]::IsNullOrWhiteSpace($token)) {
                Register-Secret -Value $token
                $attempt.Record.Detail = "http=$($attempt.Record.HttpStatus); code=$($attempt.Record.ApiCode); token issued"
            }
        }

        if ($attempt.Record.Status -eq "PASS" -and [string]::IsNullOrWhiteSpace($token)) {
            $attempt.Record.Status = "FAIL"
            $attempt.Record.Detail = "$($attempt.Record.Detail); token missing"
        }

        return [pscustomobject]@{
            Token = $token
            Record = $attempt.Record
        }
    }

    $records = New-Object System.Collections.Generic.List[object]

    try {
        $studentLogin = Login-DemoUser -Username "student"
        $companyLogin = Login-DemoUser -Username "company"
        $adminLogin = Login-DemoUser -Username "admin"
        $records.Add($studentLogin.Record)
        $records.Add($companyLogin.Record)
        $records.Add($adminLogin.Record)

        $tokens = @{
            student = $studentLogin.Token
            company = $companyLogin.Token
            admin = $adminLogin.Token
        }

        $endpointSpecs = @(
            [pscustomobject]@{
                Endpoint = "jobs"
                Method = "GET"
                Path = "/api/jobs"
                Role = "student"
                Optional404 = $false
                Body = $null
            },
            [pscustomobject]@{
                Endpoint = "deliveries my"
                Method = "GET"
                Path = "/api/deliveries/my"
                Role = "student"
                Optional404 = $false
                Body = $null
            },
            [pscustomobject]@{
                Endpoint = "deliveries company"
                Method = "GET"
                Path = "/api/deliveries/company?companyId=C001"
                Role = "company"
                Optional404 = $false
                Body = $null
            },
            [pscustomobject]@{
                Endpoint = "ai status"
                Method = "GET"
                Path = "/api/ai/status"
                Role = "student"
                Optional404 = $false
                Body = $null
            },
            [pscustomobject]@{
                Endpoint = "ai knowledge search"
                Method = "POST"
                Path = "/api/ai/knowledge/search"
                Role = "admin"
                Optional404 = $true
                Body = @{
                    query = "Java backend internship"
                    role = "ADMIN"
                    limit = 5
                }
            },
            [pscustomobject]@{
                Endpoint = "ai knowledge stats"
                Method = "GET"
                Path = "/api/ai/knowledge/stats"
                Role = "admin"
                Optional404 = $true
                Body = $null
            },
            [pscustomobject]@{
                Endpoint = "ai knowledge answer"
                Method = "POST"
                Path = "/api/ai/knowledge/answer"
                Role = "admin"
                Optional404 = $true
                Body = @{
                    query = "Java backend internship"
                    role = "ADMIN"
                    limit = 4
                    useAi = $false
                }
            },
            [pscustomobject]@{
                Endpoint = "notifications my"
                Method = "GET"
                Path = "/api/notifications/my"
                Role = "student"
                Optional404 = $true
                Body = $null
            }
        )

        for ($iteration = 1; $iteration -le $IterationsArg; $iteration++) {
            foreach ($spec in $endpointSpecs) {
                $token = ""
                if ($tokens.ContainsKey($spec.Role)) {
                    $token = $tokens[$spec.Role]
                }

                $attempt = Invoke-LoadRequest `
                    -Iteration $iteration `
                    -Endpoint $spec.Endpoint `
                    -Method $spec.Method `
                    -Path $spec.Path `
                    -Role $spec.Role `
                    -Body $spec.Body `
                    -Token $token `
                    -Optional404 ([bool]$spec.Optional404)
                $records.Add($attempt.Record)
            }
        }
    } catch {
        $records.Add((New-LoadResult `
            -Iteration 0 `
            -Endpoint "worker job" `
            -Method "N/A" `
            -Path "" `
            -Role "" `
            -Status "FAIL" `
            -HttpStatus 0 `
            -ApiCode "" `
            -DurationMs 0 `
            -Detail $_.Exception.Message))
    }

    return $records
}

Write-Host ("Running load smoke against {0}; users={1}; iterations={2}; timeout={3}s" -f $BaseUrl, $Users, $Iterations, $TimeoutSeconds)

$jobs = New-Object System.Collections.Generic.List[object]
for ($workerId = 1; $workerId -le $Users; $workerId++) {
    $job = Start-Job -Name ("load-smoke-{0}" -f $workerId) -ScriptBlock $workerScript -ArgumentList $workerId, $BaseUrl, $DemoPassword, $Iterations, $TimeoutSeconds
    $jobs.Add($job)
}

$allResults = New-Object System.Collections.Generic.List[object]
foreach ($job in $jobs) {
    Wait-Job -Job $job | Out-Null
    $received = @(Receive-Job -Job $job -ErrorAction SilentlyContinue)
    foreach ($item in $received) {
        if ($null -ne $item -and $null -ne $item.PSObject.Properties["Endpoint"]) {
            $allResults.Add($item)
        }
    }

    if ($job.State -ne "Completed") {
        $reason = ""
        if ($job.ChildJobs.Count -gt 0 -and $null -ne $job.ChildJobs[0].JobStateInfo.Reason) {
            $reason = $job.ChildJobs[0].JobStateInfo.Reason.Message
        }
        $allResults.Add((New-ControllerResult `
            -Worker 0 `
            -Iteration 0 `
            -Endpoint $job.Name `
            -Method "N/A" `
            -Path "" `
            -Role "" `
            -Status "FAIL" `
            -HttpStatus 0 `
            -ApiCode "" `
            -DurationMs 0 `
            -Detail ("job state={0}; {1}" -f $job.State, $reason)))
    }

    Remove-Job -Job $job -Force
}

$summary = Write-LoadSmokeReport `
    -Results $allResults.ToArray() `
    -ReportDirectory $ReportDirectory `
    -BaseUrl $BaseUrl `
    -Users $Users `
    -Iterations $Iterations `
    -TimeoutSeconds $TimeoutSeconds `
    -PasswordSource $passwordSource

Write-Host ("Summary: PASS={0}, FAIL={1}, SKIPPED={2}, avg={3}ms, p95={4}ms" -f `
    $summary.Pass,
    $summary.Fail,
    $summary.Skipped,
    $summary.AverageLatencyMs,
    $summary.P95LatencyMs)
Write-Host ("Report written: {0}" -f $summary.Path)

if ($summary.Fail -gt 0) {
    exit 1
}

exit 0
