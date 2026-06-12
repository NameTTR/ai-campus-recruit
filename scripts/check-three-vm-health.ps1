<#
.SYNOPSIS
Runs the v3.2 three-VM deployment acceptance smoke checks.

.DESCRIPTION
Checks local VMware vmrun availability, VMX discovery, VM running state,
Docker Compose configuration files, VM IP connectivity, and key HTTP/TCP
health endpoints for the three-VM deployment.

The script does not hardcode SSH passwords or application login passwords.
SSH is not executed by default; the report includes copyable SSH commands
that can use the caller's current ssh config or key. Gateway checks that need
a bearer token are skipped unless -DemoPassword or THREE_VM_DEMO_PASSWORD is
provided.

Every run writes a Markdown report to reports/deploy/three-vm-smoke-<timestamp>.md.

.PARAMETER EnvFile
Path to deploy/three-vm.env. If omitted, deploy/three-vm.env is used when it
exists, otherwise deploy/three-vm.env.example is used.

.PARAMETER Vm1Vmx
VM1 .vmx path override. If omitted, the script searches D:\Virtual_Machines\Ubuntu18_64_2.

.PARAMETER Vm2Vmx
VM2 .vmx path override. If omitted, the script searches D:\Virtual_Machines\ai-recruit-vm2.

.PARAMETER Vm3Vmx
VM3 .vmx path override. If omitted, the script searches D:\Virtual_Machines\ai-recruit-vm3.

.PARAMETER Vm1Ip
VM1 IP override. Defaults to VM1_HOST from the env file.

.PARAMETER Vm2Ip
VM2 IP override. Defaults to VM2_HOST from the env file.

.PARAMETER Vm3Ip
VM3 IP override. Defaults to VM3_HOST from the env file.

.PARAMETER SshUser
SSH user used only in report command hints. SSH is not executed by this script.

.PARAMETER DemoPassword
Optional demo account password for token-based gateway checks. If omitted,
the script also reads THREE_VM_DEMO_PASSWORD. The value is never written to
the console or report.

.PARAMETER DryRun
Prints and reports the planned checks without invoking vmrun, docker, network,
HTTP, TCP, or login checks.

.PARAMETER Help
Shows detailed help and exits.

.EXAMPLE
.\scripts\check-three-vm-health.ps1 -DryRun

.EXAMPLE
.\scripts\check-three-vm-health.ps1 -EnvFile .\deploy\three-vm.env -Vm1Ip 192.168.56.11 -Vm2Ip 192.168.56.12 -Vm3Ip 192.168.56.13

.EXAMPLE
$password = Read-Host "Demo password"
.\scripts\check-three-vm-health.ps1 -DemoPassword $password

Pass the demo password only at runtime. Do not commit passwords to scripts or docs.
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [int]$TimeoutSeconds = 5,
    [string]$Vm1Vmx = "",
    [string]$Vm2Vmx = "",
    [string]$Vm3Vmx = "",
    [string]$Vm1Ip = "",
    [string]$Vm2Ip = "",
    [string]$Vm3Ip = "",
    [string]$SshUser = "ubuntu",
    [string]$DemoUsername = "student",
    [string]$DemoPassword = "",
    [string]$VmrunPath = "",
    [string]$VmrunType = "ws",
    [string]$ReportDirectory = "",
    [switch]$DryRun,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Get-Help -Detailed $PSCommandPath
    return
}

$Root = Split-Path -Parent $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ReportDirectory)) {
    $ReportDirectory = Join-Path $Root "reports\deploy"
}

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path $Root "deploy\three-vm.env"
}

if (-not [System.IO.Path]::IsPathRooted($EnvFile)) {
    $EnvFile = Join-Path $Root $EnvFile
}

$envFileWasFallback = $false
if (-not (Test-Path -LiteralPath $EnvFile)) {
    $exampleEnv = Join-Path $Root "deploy\three-vm.env.example"
    if (Test-Path -LiteralPath $exampleEnv) {
        Write-Warning "Env file not found: $EnvFile. Falling back to example values: $exampleEnv"
        $EnvFile = $exampleEnv
        $envFileWasFallback = $true
    } else {
        throw "Env file not found: $EnvFile"
    }
}

if ([string]::IsNullOrWhiteSpace($DemoPassword) -and -not [string]::IsNullOrWhiteSpace($env:THREE_VM_DEMO_PASSWORD)) {
    $DemoPassword = $env:THREE_VM_DEMO_PASSWORD
}

$script:Results = New-Object System.Collections.Generic.List[object]

function ConvertTo-DisplayPath {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ""
    }

    try {
        return (Resolve-Path -LiteralPath $Path -ErrorAction Stop).ProviderPath
    } catch {
        return [System.IO.Path]::GetFullPath($Path)
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

function Add-CheckResult {
    param(
        [string]$Category,
        [string]$Name,
        [string]$Target,
        [ValidateSet("PASS", "FAIL", "SKIPPED")]
        [string]$Status,
        [string]$Detail
    )

    $script:Results.Add([pscustomobject]@{
        Category = $Category
        Name = $Name
        Target = $Target
        Status = $Status
        Detail = $Detail
    })

    $line = "[{0}] {1} -> {2} ({3})" -f $Status.PadRight(7), $Name, $Target, $Detail
    switch ($Status) {
        "PASS" { Write-Host $line -ForegroundColor Green }
        "FAIL" { Write-Host $line -ForegroundColor Red }
        default { Write-Host $line -ForegroundColor Yellow }
    }
}

function Add-SkippedIfDryRun {
    param(
        [string]$Category,
        [string]$Name,
        [string]$Target
    )

    if ($DryRun) {
        Add-CheckResult -Category $Category -Name $Name -Target $Target -Status "SKIPPED" -Detail "dry-run"
        return $true
    }

    return $false
}

function Resolve-ToolPath {
    param(
        [string]$ExplicitPath,
        [string]$CommandName,
        [string[]]$CandidatePaths
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (Test-Path -LiteralPath $ExplicitPath -PathType Leaf) {
            return (Resolve-Path -LiteralPath $ExplicitPath).ProviderPath
        }
        return $ExplicitPath
    }

    $command = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($null -ne $command) {
        return $command.Source
    }

    foreach ($candidate in $CandidatePaths) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            return (Resolve-Path -LiteralPath $candidate).ProviderPath
        }
    }

    return ""
}

function Invoke-NativeCommand {
    param(
        [string]$FilePath,
        [string[]]$Arguments,
        [hashtable]$Environment = @{}
    )

    $previousErrorActionPreference = $ErrorActionPreference
    $previousEnvironment = @{}
    foreach ($key in $Environment.Keys) {
        $previousEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, "Process")
        [Environment]::SetEnvironmentVariable($key, [string]$Environment[$key], "Process")
    }

    try {
        $ErrorActionPreference = "Continue"
        $output = & $FilePath @Arguments 2>&1 | ForEach-Object { $_.ToString() }
        $exitCode = $LASTEXITCODE
        if ($null -eq $exitCode) {
            $exitCode = 0
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
        foreach ($key in $Environment.Keys) {
            [Environment]::SetEnvironmentVariable($key, $previousEnvironment[$key], "Process")
        }
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output)
    }
}

function Resolve-VmxPath {
    param(
        [string]$Name,
        [string]$ExplicitPath,
        [string]$DefaultDirectory
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        if (Test-Path -LiteralPath $ExplicitPath -PathType Leaf) {
            $resolved = (Resolve-Path -LiteralPath $ExplicitPath).ProviderPath
            Add-CheckResult -Category "vmx" -Name "$Name vmx path" -Target $resolved -Status "PASS" -Detail "explicit path exists"
            return $resolved
        }

        Add-CheckResult -Category "vmx" -Name "$Name vmx path" -Target $ExplicitPath -Status "FAIL" -Detail "explicit path not found"
        return ""
    }

    if (-not (Test-Path -LiteralPath $DefaultDirectory -PathType Container)) {
        $status = if ($DryRun) { "SKIPPED" } else { "FAIL" }
        $detail = if ($DryRun) { "default VM directory not inspected in dry-run" } else { "default VM directory not found" }
        Add-CheckResult -Category "vmx" -Name "$Name vmx path" -Target $DefaultDirectory -Status $status -Detail $detail
        return ""
    }

    $vmx = Get-ChildItem -LiteralPath $DefaultDirectory -Filter "*.vmx" -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName |
        Select-Object -First 1

    if ($null -eq $vmx) {
        $status = if ($DryRun) { "SKIPPED" } else { "FAIL" }
        $detail = if ($DryRun) { "no vmx selected in dry-run" } else { "no .vmx file found" }
        Add-CheckResult -Category "vmx" -Name "$Name vmx path" -Target $DefaultDirectory -Status $status -Detail $detail
        return ""
    }

    Add-CheckResult -Category "vmx" -Name "$Name vmx path" -Target $vmx.FullName -Status "PASS" -Detail "discovered from default directory"
    return $vmx.FullName
}

function Normalize-PathForCompare {
    param([string]$Path)

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return ""
    }

    try {
        return ((Resolve-Path -LiteralPath $Path -ErrorAction Stop).ProviderPath.TrimEnd("\", "/")).ToLowerInvariant()
    } catch {
        return ([System.IO.Path]::GetFullPath($Path).TrimEnd("\", "/")).ToLowerInvariant()
    }
}

function Test-Vmrun {
    param(
        [string]$ResolvedVmrunPath
    )

    if (Add-SkippedIfDryRun -Category "vmrun" -Name "vmrun available" -Target $ResolvedVmrunPath) {
        return $false
    }

    if ([string]::IsNullOrWhiteSpace($ResolvedVmrunPath) -or -not (Test-Path -LiteralPath $ResolvedVmrunPath -PathType Leaf)) {
        Add-CheckResult -Category "vmrun" -Name "vmrun available" -Target "vmrun" -Status "FAIL" -Detail "vmrun not found in PATH or VMware Workstation defaults"
        return $false
    }

    Add-CheckResult -Category "vmrun" -Name "vmrun available" -Target $ResolvedVmrunPath -Status "PASS" -Detail "found"
    return $true
}

function Get-RunningVmxPaths {
    param(
        [string]$ResolvedVmrunPath
    )

    if ($DryRun -or [string]::IsNullOrWhiteSpace($ResolvedVmrunPath)) {
        return @()
    }

    $result = Invoke-NativeCommand -FilePath $ResolvedVmrunPath -Arguments @("-T", $VmrunType, "list")
    if ($result.ExitCode -ne 0) {
        Add-CheckResult -Category "vmrun" -Name "vmrun list" -Target $ResolvedVmrunPath -Status "FAIL" -Detail (($result.Output -join " ") | Select-Object -First 1)
        return @()
    }

    Add-CheckResult -Category "vmrun" -Name "vmrun list" -Target $ResolvedVmrunPath -Status "PASS" -Detail (($result.Output | Select-Object -First 1) -as [string])
    return @($result.Output | Select-Object -Skip 1)
}

function Test-VmRunning {
    param(
        [string]$Name,
        [string]$VmxPath,
        [string[]]$RunningVmxPaths,
        [bool]$VmrunAvailable
    )

    if ($DryRun) {
        Add-CheckResult -Category "vmrun" -Name "$Name running state" -Target $VmxPath -Status "SKIPPED" -Detail "dry-run"
        return
    }

    if (-not $VmrunAvailable) {
        Add-CheckResult -Category "vmrun" -Name "$Name running state" -Target $VmxPath -Status "SKIPPED" -Detail "vmrun unavailable"
        return
    }

    if ([string]::IsNullOrWhiteSpace($VmxPath)) {
        Add-CheckResult -Category "vmrun" -Name "$Name running state" -Target "<missing vmx>" -Status "SKIPPED" -Detail "vmx path unavailable"
        return
    }

    $expected = Normalize-PathForCompare -Path $VmxPath
    $running = @($RunningVmxPaths | ForEach-Object { Normalize-PathForCompare -Path $_ })
    if ($running -contains $expected) {
        Add-CheckResult -Category "vmrun" -Name "$Name running state" -Target $VmxPath -Status "PASS" -Detail "running"
    } else {
        Add-CheckResult -Category "vmrun" -Name "$Name running state" -Target $VmxPath -Status "FAIL" -Detail "not listed by vmrun"
    }
}

function Get-ComposeCommand {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($null -ne $docker) {
        return [pscustomobject]@{
            FilePath = $docker.Source
            Prefix = @("compose")
            DisplayName = "docker compose"
        }
    }

    $dockerCompose = Get-Command docker-compose -ErrorAction SilentlyContinue
    if ($null -ne $dockerCompose) {
        return [pscustomobject]@{
            FilePath = $dockerCompose.Source
            Prefix = @()
            DisplayName = "docker-compose"
        }
    }

    return $null
}

function Test-ComposeConfig {
    param(
        [string]$Name,
        [string]$ComposeFile,
        [object]$ComposeCommand,
        [hashtable]$ComposeEnvironment
    )

    if (-not (Test-Path -LiteralPath $ComposeFile -PathType Leaf)) {
        Add-CheckResult -Category "compose" -Name "$Name compose file" -Target $ComposeFile -Status "FAIL" -Detail "file not found"
        return
    }

    Add-CheckResult -Category "compose" -Name "$Name compose file" -Target $ComposeFile -Status "PASS" -Detail "file exists"

    if (Add-SkippedIfDryRun -Category "compose" -Name "$Name compose config" -Target $ComposeFile) {
        return
    }

    if ($null -eq $ComposeCommand) {
        Add-CheckResult -Category "compose" -Name "$Name compose config" -Target $ComposeFile -Status "FAIL" -Detail "docker compose command not found"
        return
    }

    $arguments = @()
    $arguments += $ComposeCommand.Prefix
    $arguments += @("--env-file", $EnvFile, "-f", $ComposeFile, "config", "--quiet")
    $result = Invoke-NativeCommand -FilePath $ComposeCommand.FilePath -Arguments $arguments -Environment $ComposeEnvironment
    if ($result.ExitCode -eq 0) {
        Add-CheckResult -Category "compose" -Name "$Name compose config" -Target $ComposeFile -Status "PASS" -Detail $ComposeCommand.DisplayName
    } else {
        $detail = ($result.Output -join " ").Trim()
        if ([string]::IsNullOrWhiteSpace($detail)) {
            $detail = "exit code $($result.ExitCode)"
        }
        Add-CheckResult -Category "compose" -Name "$Name compose config" -Target $ComposeFile -Status "FAIL" -Detail $detail
    }
}

function Test-Ping {
    param(
        [string]$Name,
        [string]$IpAddress
    )

    if (Add-SkippedIfDryRun -Category "network" -Name "$Name ping" -Target $IpAddress) {
        return
    }

    if ([string]::IsNullOrWhiteSpace($IpAddress)) {
        Add-CheckResult -Category "network" -Name "$Name ping" -Target "<empty>" -Status "FAIL" -Detail "IP not configured"
        return
    }

    try {
        $ping = New-Object System.Net.NetworkInformation.Ping
        $reply = $ping.Send($IpAddress, $TimeoutSeconds * 1000)
        if ($reply.Status -eq [System.Net.NetworkInformation.IPStatus]::Success) {
            Add-CheckResult -Category "network" -Name "$Name ping" -Target $IpAddress -Status "PASS" -Detail ("{0} ms" -f $reply.RoundtripTime)
        } else {
            Add-CheckResult -Category "network" -Name "$Name ping" -Target $IpAddress -Status "FAIL" -Detail $reply.Status.ToString()
        }
    } catch {
        Add-CheckResult -Category "network" -Name "$Name ping" -Target $IpAddress -Status "FAIL" -Detail $_.Exception.Message
    }
}

function Test-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [string]$BearerToken = "",
        [bool]$RequiresToken = $false
    )

    if (Add-SkippedIfDryRun -Category "http" -Name $Name -Target $Url) {
        return
    }

    if ($RequiresToken -and [string]::IsNullOrWhiteSpace($BearerToken)) {
        Add-CheckResult -Category "http" -Name $Name -Target $Url -Status "SKIPPED" -Detail "bearer token unavailable"
        return
    }

    try {
        $headers = @{}
        if (-not [string]::IsNullOrWhiteSpace($BearerToken)) {
            $headers["Authorization"] = "Bearer $BearerToken"
        }

        $response = Invoke-WebRequest -Uri $Url -Method Get -Headers $headers -TimeoutSec $TimeoutSeconds -UseBasicParsing
        $ok = $response.StatusCode -ge 200 -and $response.StatusCode -lt 400
        $status = if ($ok) { "PASS" } else { "FAIL" }
        Add-CheckResult -Category "http" -Name $Name -Target $Url -Status $status -Detail ("HTTP {0}" -f $response.StatusCode)
    } catch {
        Add-CheckResult -Category "http" -Name $Name -Target $Url -Status "FAIL" -Detail $_.Exception.Message
    }
}

function Test-TcpPort {
    param(
        [string]$Name,
        [string]$HostName,
        [int]$Port
    )

    $target = "{0}:{1}" -f $HostName, $Port
    if (Add-SkippedIfDryRun -Category "tcp" -Name $Name -Target $target) {
        return
    }

    $client = New-Object System.Net.Sockets.TcpClient

    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        $connected = $connect.AsyncWaitHandle.WaitOne($TimeoutSeconds * 1000, $false)
        if ($connected -and $client.Connected) {
            $client.EndConnect($connect)
            Add-CheckResult -Category "tcp" -Name $Name -Target $target -Status "PASS" -Detail "tcp open"
        } else {
            Add-CheckResult -Category "tcp" -Name $Name -Target $target -Status "FAIL" -Detail "tcp timeout"
        }
    } catch {
        Add-CheckResult -Category "tcp" -Name $Name -Target $target -Status "FAIL" -Detail $_.Exception.Message
    } finally {
        $client.Close()
    }
}

function Get-GatewayToken {
    param(
        [string]$GatewayBaseUrl,
        [string]$Username,
        [string]$Password
    )

    if ($DryRun) {
        Add-CheckResult -Category "auth" -Name "gateway $Username login" -Target $GatewayBaseUrl -Status "SKIPPED" -Detail "dry-run"
        return ""
    }

    if ([string]::IsNullOrWhiteSpace($Password)) {
        Add-CheckResult -Category "auth" -Name "gateway $Username login" -Target $GatewayBaseUrl -Status "SKIPPED" -Detail "DemoPassword or THREE_VM_DEMO_PASSWORD not provided"
        return ""
    }

    try {
        $response = Invoke-RestMethod -Uri ("{0}/api/auth/login" -f $GatewayBaseUrl.TrimEnd("/")) `
            -Method Post `
            -ContentType "application/json" `
            -Body (@{ username = $Username; password = $Password } | ConvertTo-Json -Depth 4) `
            -TimeoutSec $TimeoutSeconds
        if ($null -ne $response -and $null -ne $response.data -and -not [string]::IsNullOrWhiteSpace($response.data.token)) {
            Add-CheckResult -Category "auth" -Name "gateway $Username login" -Target $GatewayBaseUrl -Status "PASS" -Detail "token issued"
            return $response.data.token
        }
        Add-CheckResult -Category "auth" -Name "gateway $Username login" -Target $GatewayBaseUrl -Status "FAIL" -Detail "token missing"
    } catch {
        Add-CheckResult -Category "auth" -Name "gateway $Username login" -Target $GatewayBaseUrl -Status "FAIL" -Detail $_.Exception.Message
    }

    return ""
}

function ConvertTo-MarkdownCell {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }

    return ($Value -replace "\|", "\|" -replace "(\r?\n)", "<br>")
}

function New-SshHint {
    param(
        [string]$Name,
        [string]$IpAddress,
        [string]$ComposeFile
    )

    if ([string]::IsNullOrWhiteSpace($IpAddress)) {
        return ""
    }

    return "ssh $SshUser@$IpAddress `"cd <repo-path> && docker compose --env-file deploy/three-vm.env -f $ComposeFile config --quiet && docker compose --env-file deploy/three-vm.env -f $ComposeFile ps`""
}

function Write-Report {
    param(
        [string]$Path,
        [object[]]$Vms,
        [string]$EnvFilePath,
        [bool]$UsedFallbackEnv
    )

    $passCount = @($script:Results | Where-Object { $_.Status -eq "PASS" }).Count
    $failCount = @($script:Results | Where-Object { $_.Status -eq "FAIL" }).Count
    $skippedCount = @($script:Results | Where-Object { $_.Status -eq "SKIPPED" }).Count
    $generatedAt = Get-Date -Format "yyyy-MM-dd HH:mm:ss zzz"

    $lines = New-Object System.Collections.Generic.List[string]
    $envFileDisplay = ConvertTo-DisplayPath -Path $EnvFilePath
    $lines.Add('# Three-VM Smoke Report')
    $lines.Add('')
    $lines.Add("- Generated: $generatedAt")
    $lines.Add("- Env file: ``$envFileDisplay``")
    $lines.Add("- Env fallback: $UsedFallbackEnv")
    $lines.Add("- Dry run: $($DryRun.IsPresent)")
    $lines.Add("- Timeout seconds: $TimeoutSeconds")
    $lines.Add("- Result summary: PASS=$passCount, FAIL=$failCount, SKIPPED=$skippedCount")
    $lines.Add('')
    $lines.Add('## VM Inputs')
    $lines.Add('')
    $lines.Add('| VM | IP | VMX | Compose file |')
    $lines.Add('| --- | --- | --- | --- |')
    foreach ($vm in $Vms) {
        $ipCell = ConvertTo-MarkdownCell -Value $vm.Ip
        $vmxCell = ConvertTo-MarkdownCell -Value $vm.Vmx
        $composeCell = ConvertTo-MarkdownCell -Value $vm.ComposeFile
        $lines.Add(('| {0} | `{1}` | `{2}` | `{3}` |' -f $vm.Name, $ipCell, $vmxCell, $composeCell))
    }
    $lines.Add('')
    $lines.Add('## Results')
    $lines.Add('')
    $lines.Add('| Status | Category | Name | Target | Detail |')
    $lines.Add('| --- | --- | --- | --- | --- |')
    foreach ($result in $script:Results) {
        $nameCell = ConvertTo-MarkdownCell -Value $result.Name
        $targetCell = ConvertTo-MarkdownCell -Value $result.Target
        $detailCell = ConvertTo-MarkdownCell -Value $result.Detail
        $lines.Add(('| {0} | {1} | {2} | `{3}` | {4} |' -f $result.Status, $result.Category, $nameCell, $targetCell, $detailCell))
    }
    $lines.Add('')
    $lines.Add('## SSH Command Hints')
    $lines.Add('')
    $lines.Add('SSH is not executed by this script. Use the current ssh config or a user-provided key; do not put passwords in scripts.')
    $lines.Add('')
    $lines.Add('```bash')
    foreach ($vm in $Vms) {
        $hint = New-SshHint -Name $vm.Name -IpAddress $vm.Ip -ComposeFile $vm.ComposeFile
        if (-not [string]::IsNullOrWhiteSpace($hint)) {
            $lines.Add($hint)
        }
    }
    $lines.Add('```')
    $lines.Add('')
    $lines.Add('## Exit Criteria')
    $lines.Add('')
    $lines.Add('- PASS: check completed successfully.')
    $lines.Add('- FAIL: check ran and did not meet the expected condition.')
    $lines.Add('- SKIPPED: dry-run, missing optional credential, or unavailable dependent tool/path.')

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Path) | Out-Null
    Set-Content -LiteralPath $Path -Value $lines -Encoding UTF8

    return [pscustomobject]@{
        Path = $Path
        Pass = $passCount
        Fail = $failCount
        Skipped = $skippedCount
    }
}

$envValues = Import-DotEnv -Path $EnvFile
$vm1Host = if ([string]::IsNullOrWhiteSpace($Vm1Ip)) { Get-RequiredValue -Values $envValues -Key "VM1_HOST" } else { $Vm1Ip }
$vm2Host = if ([string]::IsNullOrWhiteSpace($Vm2Ip)) { Get-RequiredValue -Values $envValues -Key "VM2_HOST" } else { $Vm2Ip }
$vm3Host = if ([string]::IsNullOrWhiteSpace($Vm3Ip)) { Get-RequiredValue -Values $envValues -Key "VM3_HOST" } else { $Vm3Ip }
$frontendPort = Get-ValueOrDefault -Values $envValues -Key "FRONTEND_PORT" -DefaultValue "80"
$gatewayPort = Get-ValueOrDefault -Values $envValues -Key "GATEWAY_PORT" -DefaultValue "8080"
$milvusPort = Get-ValueOrDefault -Values $envValues -Key "MILVUS_PORT" -DefaultValue "19530"
$sentinelDashboardPort = Get-ValueOrDefault -Values $envValues -Key "SENTINEL_DASHBOARD_PORT" -DefaultValue "8858"
$gatewayBaseUrl = "http://${vm1Host}:${gatewayPort}"

$composeEnvironment = @{}
foreach ($entry in $envValues.GetEnumerator()) {
    if (-not [string]::IsNullOrWhiteSpace($entry.Key) -and $null -ne $entry.Value) {
        $composeEnvironment[$entry.Key] = [string]$entry.Value
    }
}
$composeEnvironment["VM1_HOST"] = $vm1Host
$composeEnvironment["VM2_HOST"] = $vm2Host
$composeEnvironment["VM3_HOST"] = $vm3Host
$composeEnvironment["FRONTEND_PORT"] = $frontendPort
$composeEnvironment["GATEWAY_PORT"] = $gatewayPort
$composeEnvironment["MILVUS_PORT"] = $milvusPort
$composeEnvironment["SENTINEL_DASHBOARD_PORT"] = $sentinelDashboardPort

Write-Host "Using env file: $EnvFile"
Write-Host "Checking VM1=$vm1Host VM2=$vm2Host VM3=$vm3Host"
if ($DryRun) {
    Write-Host "Dry-run enabled; external checks will be reported as skipped." -ForegroundColor Yellow
}

$envDetail = if ($envFileWasFallback) { "example fallback" } else { "configured" }
Add-CheckResult -Category "config" -Name "env file" -Target $EnvFile -Status "PASS" -Detail $envDetail

$defaultVmrunCandidates = @()
$programFilesX86 = [Environment]::GetEnvironmentVariable("ProgramFiles(x86)")
$programFiles = [Environment]::GetEnvironmentVariable("ProgramFiles")
if (-not [string]::IsNullOrWhiteSpace($programFilesX86)) {
    $defaultVmrunCandidates += (Join-Path $programFilesX86 "VMware\VMware Workstation\vmrun.exe")
}
if (-not [string]::IsNullOrWhiteSpace($programFiles)) {
    $defaultVmrunCandidates += (Join-Path $programFiles "VMware\VMware Workstation\vmrun.exe")
}

$resolvedVmrunPath = Resolve-ToolPath -ExplicitPath $VmrunPath -CommandName "vmrun" -CandidatePaths $defaultVmrunCandidates
$vmrunAvailable = Test-Vmrun -ResolvedVmrunPath $resolvedVmrunPath
$runningVmxPaths = if ($vmrunAvailable) { Get-RunningVmxPaths -ResolvedVmrunPath $resolvedVmrunPath } else { @() }

$vm1VmxResolved = Resolve-VmxPath -Name "VM1" -ExplicitPath $Vm1Vmx -DefaultDirectory "D:\Virtual_Machines\Ubuntu18_64_2"
$vm2VmxResolved = Resolve-VmxPath -Name "VM2" -ExplicitPath $Vm2Vmx -DefaultDirectory "D:\Virtual_Machines\ai-recruit-vm2"
$vm3VmxResolved = Resolve-VmxPath -Name "VM3" -ExplicitPath $Vm3Vmx -DefaultDirectory "D:\Virtual_Machines\ai-recruit-vm3"

$vms = @(
    [pscustomobject]@{ Name = "VM1"; Ip = $vm1Host; Vmx = $vm1VmxResolved; ComposeFile = "deploy/docker-compose.vm1.yml"; ComposePath = Join-Path $Root "deploy\docker-compose.vm1.yml" },
    [pscustomobject]@{ Name = "VM2"; Ip = $vm2Host; Vmx = $vm2VmxResolved; ComposeFile = "deploy/docker-compose.vm2.yml"; ComposePath = Join-Path $Root "deploy\docker-compose.vm2.yml" },
    [pscustomobject]@{ Name = "VM3"; Ip = $vm3Host; Vmx = $vm3VmxResolved; ComposeFile = "deploy/docker-compose.vm3.yml"; ComposePath = Join-Path $Root "deploy\docker-compose.vm3.yml" }
)

foreach ($vm in $vms) {
    Test-VmRunning -Name $vm.Name -VmxPath $vm.Vmx -RunningVmxPaths $runningVmxPaths -VmrunAvailable $vmrunAvailable
}

$composeCommand = if ($DryRun) { $null } else { Get-ComposeCommand }
foreach ($vm in $vms) {
    Test-ComposeConfig -Name $vm.Name -ComposeFile $vm.ComposePath -ComposeCommand $composeCommand -ComposeEnvironment $composeEnvironment
}

foreach ($vm in $vms) {
    Test-Ping -Name $vm.Name -IpAddress $vm.Ip
}

Test-HttpEndpoint -Name "VM1 frontend" -Url "http://${vm1Host}:${frontendPort}/"
Test-HttpEndpoint -Name "VM1 gateway health" -Url "http://${vm1Host}:${gatewayPort}/actuator/health"
$studentToken = Get-GatewayToken -GatewayBaseUrl $gatewayBaseUrl -Username $DemoUsername -Password $DemoPassword
Test-HttpEndpoint -Name "VM1 frontend api proxy" -Url "http://${vm1Host}:${frontendPort}/api/ai/status" -BearerToken $studentToken -RequiresToken $true
Test-HttpEndpoint -Name "VM1 gateway ai route" -Url "http://${vm1Host}:${gatewayPort}/api/ai/status" -BearerToken $studentToken -RequiresToken $true
Test-HttpEndpoint -Name "VM1 nacos console" -Url "http://${vm1Host}:8848/nacos/"
Test-TcpPort -Name "VM1 nacos grpc" -HostName $vm1Host -Port 9848
Test-HttpEndpoint -Name "VM1 sentinel dashboard" -Url "http://${vm1Host}:${sentinelDashboardPort}/"

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
Test-HttpEndpoint -Name "VM3 ai RAG vector status" -Url "http://${vm3Host}:8106/api/ai/knowledge/vector/status"
Test-TcpPort -Name "VM3 mysql" -HostName $vm3Host -Port 3306
Test-TcpPort -Name "VM3 redis" -HostName $vm3Host -Port 6379
Test-HttpEndpoint -Name "VM3 minio api" -Url "http://${vm3Host}:9000/minio/health/ready"
Test-TcpPort -Name "VM3 minio console" -HostName $vm3Host -Port 9001
Test-TcpPort -Name "VM3 rocketmq namesrv" -HostName $vm3Host -Port 9876
Test-TcpPort -Name "VM3 rocketmq broker listen" -HostName $vm3Host -Port 10911
Test-TcpPort -Name "VM3 rocketmq broker vip" -HostName $vm3Host -Port 10909
Test-TcpPort -Name "VM3 milvus grpc/rest" -HostName $vm3Host -Port ([int]$milvusPort)

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportPath = Join-Path $ReportDirectory ("three-vm-smoke-{0}.md" -f $timestamp)
$report = Write-Report -Path $reportPath -Vms $vms -EnvFilePath $EnvFile -UsedFallbackEnv $envFileWasFallback

Write-Host ("Report written: {0}" -f $report.Path) -ForegroundColor Cyan
Write-Host ("Summary: PASS={0}, FAIL={1}, SKIPPED={2}" -f $report.Pass, $report.Fail, $report.Skipped)

if ($report.Fail -gt 0) {
    exit 1
}

exit 0
