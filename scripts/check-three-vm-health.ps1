param(
    [string]$EnvFile = "",
    [int]$TimeoutSeconds = 5
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $Root "deploy\three-vm.env"
}

if (-not (Test-Path -LiteralPath $EnvFile)) {
    $exampleEnv = Join-Path $Root "deploy\three-vm.env.example"
    if (Test-Path -LiteralPath $exampleEnv) {
        Write-Warning "Env file not found: $EnvFile. Falling back to example values: $exampleEnv"
        $EnvFile = $exampleEnv
    } else {
        throw "Env file not found: $EnvFile"
    }
}

function Import-DotEnv {
    param([string]$Path)

    $values = @{}
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }

        $index = $line.IndexOf("=")
        if ($index -lt 1) {
            continue
        }

        $key = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()

        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        $values[$key] = $value
    }

    return $values
}

function Get-RequiredValue {
    param(
        [hashtable]$Values,
        [string]$Key
    )

    if (-not $Values.ContainsKey($Key) -or [string]::IsNullOrWhiteSpace($Values[$Key])) {
        throw "Missing required env value: $Key"
    }

    return $Values[$Key]
}

function Get-ValueOrDefault {
    param(
        [hashtable]$Values,
        [string]$Key,
        [string]$DefaultValue
    )

    if ($Values.ContainsKey($Key) -and -not [string]::IsNullOrWhiteSpace($Values[$Key])) {
        return $Values[$Key]
    }

    return $DefaultValue
}

$script:FailedChecks = 0

function Write-CheckResult {
    param(
        [string]$Name,
        [string]$Target,
        [bool]$Ok,
        [string]$Detail
    )

    if ($Ok) {
        Write-Host ("[OK]   {0} -> {1} ({2})" -f $Name, $Target, $Detail) -ForegroundColor Green
    } else {
        Write-Host ("[FAIL] {0} -> {1} ({2})" -f $Name, $Target, $Detail) -ForegroundColor Red
        $script:FailedChecks++
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$BearerToken = ""
    )

    try {
        $headers = @{}
        if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
            $headers["Authorization"] = "Bearer $BearerToken"
        }
        $response = Invoke-WebRequest -Uri $Url -Method Get -Headers $headers -TimeoutSec $TimeoutSeconds -UseBasicParsing
        $ok = $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
        Write-CheckResult -Name $Name -Target $Url -Ok $ok -Detail ("HTTP {0}" -f $response.StatusCode)
    } catch {
        Write-CheckResult -Name $Name -Target $Url -Ok $false -Detail $_.Exception.Message
    }
}

function Test-TcpPort {
    param(
        [string]$Name,
        [string]$HostName,
        [int]$Port
    )

    $target = "{0}:{1}" -f $HostName, $Port
    $client = New-Object System.Net.Sockets.TcpClient

    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $connect.AsyncWaitHandle.WaitOne($TimeoutSeconds * 1000, $false)
        if ($connected -and $client.Connected) {
            $client.EndConnect($connect)
            Write-CheckResult -Name $Name -Target $target -Ok $true -Detail "tcp open"
        } else {
            Write-CheckResult -Name $Name -Target $target -Ok $false -Detail "tcp timeout"
        }
    } catch {
        Write-CheckResult -Name $Name -Target $target -Ok $false -Detail $_.Exception.Message
    } finally {
        $client.Close()
    }
}

function Get-GatewayToken {
    param(
        [string]$GatewayBaseUrl,
        [string]$Username
    )

    try {
        $response = Invoke-RestMethod -Uri ("{0}/api/auth/login" -f $GatewayBaseUrl.TrimEnd("/")) `
            -Method Post `
            -ContentType "application/json" `
            -Body (@{ username = $Username; password = "123456" } | ConvertTo-Json -Depth 4) `
            -TimeoutSec $TimeoutSeconds
        if ($null -ne $response -and $null -ne $response.data -and -not [string]::IsNullOrWhiteSpace($response.data.token)) {
            Write-CheckResult -Name ("gateway {0} login" -f $Username) -Target $GatewayBaseUrl -Ok $true -Detail "token issued"
            return $response.data.token
        }
        Write-CheckResult -Name ("gateway {0} login" -f $Username) -Target $GatewayBaseUrl -Ok $false -Detail "token missing"
    } catch {
        Write-CheckResult -Name ("gateway {0} login" -f $Username) -Target $GatewayBaseUrl -Ok $false -Detail $_.Exception.Message
    }

    return ""
}

$envValues = Import-DotEnv -Path $EnvFile
$vm1Host = Get-RequiredValue -Values $envValues -Key "VM1_HOST"
$vm2Host = Get-RequiredValue -Values $envValues -Key "VM2_HOST"
$vm3Host = Get-RequiredValue -Values $envValues -Key "VM3_HOST"
$frontendPort = Get-ValueOrDefault -Values $envValues -Key "FRONTEND_PORT" -DefaultValue "80"
$gatewayPort = Get-ValueOrDefault -Values $envValues -Key "GATEWAY_PORT" -DefaultValue "8080"
$gatewayBaseUrl = "http://${vm1Host}:${gatewayPort}"

Write-Host "Using env file: $EnvFile"
Write-Host "Checking VM1=$vm1Host VM2=$vm2Host VM3=$vm3Host"

Test-HttpEndpoint -Name "VM1 frontend" -Url "http://${vm1Host}:${frontendPort}/"
Test-HttpEndpoint -Name "VM1 gateway health" -Url "http://${vm1Host}:${gatewayPort}/actuator/health"
$studentToken = Get-GatewayToken -GatewayBaseUrl $gatewayBaseUrl -Username "student"
Test-HttpEndpoint -Name "VM1 frontend api proxy" -Url "http://${vm1Host}:${frontendPort}/api/ai/status" -BearerToken $studentToken
Test-HttpEndpoint -Name "VM1 gateway ai route" -Url "http://${vm1Host}:${gatewayPort}/api/ai/status" -BearerToken $studentToken
Test-HttpEndpoint -Name "VM1 nacos console" -Url "http://${vm1Host}:8848/nacos/"
Test-TcpPort -Name "VM1 nacos grpc" -HostName $vm1Host -Port 9848

$businessServices = @(
    @{ Name = "VM2 auth-service"; Port = 8101 },
    @{ Name = "VM2 user-service"; Port = 8102 },
    @{ Name = "VM2 resume-service"; Port = 8103 },
    @{ Name = "VM2 job-service"; Port = 8104 },
    @{ Name = "VM2 match-service"; Port = 8105 },
    @{ Name = "VM2 delivery-service"; Port = 8107 }
)

foreach ($service in $businessServices) {
    Test-HttpEndpoint -Name $service.Name -Url ("http://{0}:{1}/actuator/health" -f $vm2Host, $service.Port)
}

Test-HttpEndpoint -Name "VM3 ai-service health" -Url "http://${vm3Host}:8106/actuator/health"
Test-HttpEndpoint -Name "VM3 ai-service status" -Url "http://${vm3Host}:8106/api/ai/status"
Test-TcpPort -Name "VM3 mysql" -HostName $vm3Host -Port 3306
Test-TcpPort -Name "VM3 redis" -HostName $vm3Host -Port 6379
Test-HttpEndpoint -Name "VM3 minio api" -Url "http://${vm3Host}:9000/minio/health/ready"
Test-TcpPort -Name "VM3 minio console" -HostName $vm3Host -Port 9001
Test-TcpPort -Name "VM3 rocketmq namesrv" -HostName $vm3Host -Port 9876
Test-TcpPort -Name "VM3 rocketmq broker listen" -HostName $vm3Host -Port 10911
Test-TcpPort -Name "VM3 rocketmq broker vip" -HostName $vm3Host -Port 10909

if ($script:FailedChecks -gt 0) {
    Write-Host "$script:FailedChecks health check(s) failed." -ForegroundColor Red
    exit 1
}

Write-Host "All health checks passed." -ForegroundColor Green
