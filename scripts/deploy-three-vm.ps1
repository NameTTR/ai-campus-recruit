<#
.SYNOPSIS
Deploys the committed project revision to three Ubuntu VMs over SSH.

.DESCRIPTION
Discovers VMware guest IPs when possible, creates a temporary deployment env
without printing secrets, archives the committed Git revision, uploads it to
each VM, starts VM1 Nacos first, starts Compose in VM3 -> VM2 -> VM1 order,
then runs the three-VM health smoke and distributed AI flow checks.

SSH passwords are never accepted as command arguments. Configure an SSH key or
run the script in an interactive PowerShell window so OpenSSH can prompt.

.EXAMPLE
.\scripts\deploy-three-vm.ps1 -SshUser ubuntu -IdentityFile $HOME\.ssh\id_ed25519

.EXAMPLE
.\scripts\deploy-three-vm.ps1 -SshUser ubuntu -NonInteractive -DryRun
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [string]$Vm1Ip = "",
    [string]$Vm2Ip = "",
    [string]$Vm3Ip = "",
    [string]$SshUser = "ubuntu",
    [string]$IdentityFile = "",
    [string]$RemotePath = "",
    [string]$DemoPassword = "",
    [int]$TimeoutSeconds = 8,
    [switch]$SkipSync,
    [switch]$SkipBuild,
    [switch]$KeepExisting,
    [switch]$SkipSmoke,
    [switch]$SkipDistributedAiFlow,
    [switch]$NonInteractive,
    [switch]$DisableConnectionSharing,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $PSScriptRoot
$VmrunDefault = "D:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe"
$SshControlDirectory = ([System.IO.Path]::GetTempPath()).TrimEnd("\").Replace("\", "/")
$SshControlPath = $SshControlDirectory + "/ai-campus-ssh-" + [guid]::NewGuid().ToString("N") + "-%r@%h-%p"
$VmDefinitions = @(
    [pscustomobject]@{ Name = "VM1"; Directory = "D:\Virtual_Machines\Ubuntu18_64_2"; Compose = "deploy/docker-compose.vm1.yml" },
    [pscustomobject]@{ Name = "VM2"; Directory = "D:\Virtual_Machines\ai-recruit-vm2"; Compose = "deploy/docker-compose.vm2.yml" },
    [pscustomobject]@{ Name = "VM3"; Directory = "D:\Virtual_Machines\ai-recruit-vm3"; Compose = "deploy/docker-compose.vm3.yml" }
)

function Import-DotEnvMap {
    param([string]$Path)

    $values = [ordered]@{}
    if (-not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path -LiteralPath $Path)) {
        foreach ($line in Get-Content -LiteralPath $Path) {
            if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith("#") -or -not $line.Contains("=")) {
                continue
            }
            $parts = $line.Split("=", 2)
            $key = $parts[0].Trim()
            if (-not [string]::IsNullOrWhiteSpace($key)) {
                $values[$key] = $parts[1]
            }
        }
    }
    return $values
}

function Merge-DotEnvMap {
    param(
        [System.Collections.IDictionary]$Target,
        [System.Collections.IDictionary]$Source
    )

    foreach ($key in $Source.Keys) {
        $Target[$key] = $Source[$key]
    }
}

function Get-GuestIp {
    param(
        [string]$Directory,
        [string]$Fallback
    )

    if (-not [string]::IsNullOrWhiteSpace($Fallback)) {
        return $Fallback
    }
    if ($DryRun -or -not (Test-Path -LiteralPath $VmrunDefault) -or -not (Test-Path -LiteralPath $Directory -PathType Container)) {
        return ""
    }
    $vmx = Get-ChildItem -LiteralPath $Directory -Filter "*.vmx" -File -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName |
        Select-Object -First 1
    if ($null -eq $vmx) {
        return ""
    }
    $output = & $VmrunDefault getGuestIPAddress $vmx.FullName -wait 2>$null
    if ($LASTEXITCODE -eq 0) {
        return ([string]$output).Trim()
    }
    return ""
}

function Invoke-External {
    param(
        [string]$Name,
        [scriptblock]$Command
    )

    Write-Host "==> $Name" -ForegroundColor Cyan
    if ($DryRun) {
        return
    }
    & $Command
    if ($LASTEXITCODE -ne 0) {
        throw "$Name failed with exit code $LASTEXITCODE."
    }
}

function Get-SshOptions {
    $options = @(
        "-o", "ConnectTimeout=$TimeoutSeconds",
        "-o", "StrictHostKeyChecking=accept-new"
    )
    if ($NonInteractive) {
        $options += @("-o", "BatchMode=yes")
    }
    if (-not [string]::IsNullOrWhiteSpace($IdentityFile)) {
        $resolvedIdentity = (Resolve-Path -LiteralPath $IdentityFile).Path
        $options += @("-i", $resolvedIdentity)
    }
    if (-not $DisableConnectionSharing) {
        $options += @(
            "-o", "ControlMaster=auto",
            "-o", "ControlPersist=10m",
            "-o", "ControlPath=$SshControlPath"
        )
    }
    return $options
}

function Invoke-Ssh {
    param(
        [string]$HostName,
        [string]$Name,
        [string]$RemoteCommand
    )

    $options = Get-SshOptions
    Invoke-External -Name $Name -Command { & ssh @options "$SshUser@$HostName" $RemoteCommand }
}

function Wait-HttpEndpoint {
    param(
        [string]$Name,
        [string]$Url,
        [int]$Seconds
    )

    Write-Host "==> Wait for $Name" -ForegroundColor Cyan
    if ($DryRun) {
        return
    }

    $deadline = (Get-Date).AddSeconds($Seconds)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSeconds
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 300) {
                return
            }
            $lastError = "HTTP $($response.StatusCode)"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 5
    }

    throw "$Name did not become ready at $Url within $Seconds seconds. Last error: $lastError"
}

function Invoke-Scp {
    param(
        [string]$HostName,
        [string]$Name,
        [string]$LocalPath,
        [string]$RemoteFile
    )

    $options = Get-SshOptions
    Invoke-External -Name $Name -Command { & scp @options $LocalPath ("{0}@{1}:{2}" -f $SshUser, $HostName, $RemoteFile) }
}

if ($SshUser -notmatch "^[A-Za-z0-9._-]+$") {
    throw "SshUser contains unsupported characters."
}
if ([string]::IsNullOrWhiteSpace($RemotePath)) {
    $RemotePath = "/home/$SshUser/ai-campus-recruit"
}
if ($RemotePath -notmatch "^/[A-Za-z0-9._/-]+$" -or $RemotePath.Length -lt 12) {
    throw "RemotePath must be a safe absolute path."
}

$exampleValues = Import-DotEnvMap -Path (Join-Path $Root "deploy\three-vm.env.example")
$rootValues = Import-DotEnvMap -Path (Join-Path $Root ".env")
$configuredValues = if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    [ordered]@{}
} else {
    Import-DotEnvMap -Path (Resolve-Path -LiteralPath $EnvFile).Path
}
$values = [ordered]@{}
Merge-DotEnvMap -Target $values -Source $exampleValues
Merge-DotEnvMap -Target $values -Source $rootValues
Merge-DotEnvMap -Target $values -Source $configuredValues

$Vm1Ip = Get-GuestIp -Directory $VmDefinitions[0].Directory -Fallback $Vm1Ip
$Vm2Ip = Get-GuestIp -Directory $VmDefinitions[1].Directory -Fallback $Vm2Ip
$Vm3Ip = Get-GuestIp -Directory $VmDefinitions[2].Directory -Fallback $Vm3Ip
if ([string]::IsNullOrWhiteSpace($Vm1Ip)) { $Vm1Ip = $values["VM1_HOST"] }
if ([string]::IsNullOrWhiteSpace($Vm2Ip)) { $Vm2Ip = $values["VM2_HOST"] }
if ([string]::IsNullOrWhiteSpace($Vm3Ip)) { $Vm3Ip = $values["VM3_HOST"] }
if ([string]::IsNullOrWhiteSpace($Vm1Ip) -or [string]::IsNullOrWhiteSpace($Vm2Ip) -or [string]::IsNullOrWhiteSpace($Vm3Ip)) {
    throw "Unable to resolve all three VM IP addresses. Provide -Vm1Ip, -Vm2Ip, and -Vm3Ip."
}
$values["VM1_HOST"] = $Vm1Ip
$values["VM2_HOST"] = $Vm2Ip
$values["VM3_HOST"] = $Vm3Ip

$tempDirectory = Join-Path ([System.IO.Path]::GetTempPath()) ("ai-campus-deploy-" + [guid]::NewGuid().ToString("N"))
$archivePath = Join-Path $tempDirectory "ai-campus-recruit.tar.gz"
$normalizedEnvPath = Join-Path $tempDirectory "three-vm.env"
New-Item -ItemType Directory -Force -Path $tempDirectory | Out-Null

try {
    $envLines = New-Object System.Collections.Generic.List[string]
    foreach ($key in $exampleValues.Keys) {
        $envLines.Add(("{0}={1}" -f $key, $values[$key]))
    }
    Set-Content -LiteralPath $normalizedEnvPath -Value $envLines -Encoding UTF8

    Write-Host "VM1=$Vm1Ip VM2=$Vm2Ip VM3=$Vm3Ip"
    Write-Host "Remote path: $RemotePath"
    Write-Host "Deployment env generated without printing secret values."

    if (-not $SkipSync) {
        Invoke-External -Name "Archive committed Git revision" -Command {
            & git -C $Root archive --format=tar.gz -o $archivePath HEAD
        }
    }

    $deploymentTargets = @(
        [pscustomobject]@{ Name = "VM1"; Host = $Vm1Ip; Compose = "deploy/docker-compose.vm1.yml" },
        [pscustomobject]@{ Name = "VM2"; Host = $Vm2Ip; Compose = "deploy/docker-compose.vm2.yml" },
        [pscustomobject]@{ Name = "VM3"; Host = $Vm3Ip; Compose = "deploy/docker-compose.vm3.yml" }
    )

    foreach ($vm in $deploymentTargets) {
        Invoke-Ssh -HostName $vm.Host -Name "$($vm.Name) prerequisites" -RemoteCommand `
            "set -e; command -v docker >/dev/null; docker compose version >/dev/null; mkdir -p '$RemotePath'"

        if (-not $SkipSync) {
            Invoke-Scp -HostName $vm.Host -Name "$($vm.Name) upload project archive" -LocalPath $archivePath -RemoteFile "/tmp/ai-campus-recruit.tar.gz"
            Invoke-Scp -HostName $vm.Host -Name "$($vm.Name) upload deployment env" -LocalPath $normalizedEnvPath -RemoteFile "/tmp/ai-campus-three-vm.env"
            Invoke-Ssh -HostName $vm.Host -Name "$($vm.Name) extract project" -RemoteCommand `
                "set -e; rm -rf '$RemotePath'; mkdir -p '$RemotePath'; tar -xzf /tmp/ai-campus-recruit.tar.gz -C '$RemotePath'; mkdir -p '$RemotePath/deploy'; install -m 600 /tmp/ai-campus-three-vm.env '$RemotePath/deploy/three-vm.env'; if [ -f '$RemotePath/deploy/monitoring/prometheus.yml.template' ]; then sed -e 's#__VM1_HOST__#$Vm1Ip#g' -e 's#__VM2_HOST__#$Vm2Ip#g' -e 's#__VM3_HOST__#$Vm3Ip#g' '$RemotePath/deploy/monitoring/prometheus.yml.template' > '$RemotePath/deploy/monitoring/prometheus.yml'; fi"
        }

        if (-not $KeepExisting) {
            Invoke-Ssh -HostName $vm.Host -Name "$($vm.Name) remove stale recruit containers" -RemoteCommand `
                "docker ps -aq --filter name=recruit- | xargs -r docker rm -f"
        }
    }

    Invoke-Ssh -HostName $Vm1Ip -Name "VM1 start Nacos bootstrap" -RemoteCommand `
        "set -e; cd '$RemotePath'; docker compose --env-file deploy/three-vm.env -f 'deploy/docker-compose.vm1.yml' up -d nacos; docker compose --env-file deploy/three-vm.env -f 'deploy/docker-compose.vm1.yml' ps nacos"

    Wait-HttpEndpoint -Name "VM1 Nacos" -Url "http://${Vm1Ip}:8848/nacos/" -Seconds ([Math]::Max(180, $TimeoutSeconds * 9))

    $deploymentOrder = @(
        [pscustomobject]@{ Name = "VM3"; Host = $Vm3Ip; Compose = "deploy/docker-compose.vm3.yml" },
        [pscustomobject]@{ Name = "VM2"; Host = $Vm2Ip; Compose = "deploy/docker-compose.vm2.yml" },
        [pscustomobject]@{ Name = "VM1"; Host = $Vm1Ip; Compose = "deploy/docker-compose.vm1.yml" }
    )

    foreach ($vm in $deploymentOrder) {
        $buildFlag = if ($SkipBuild) { "" } else { "--build" }
        Invoke-Ssh -HostName $vm.Host -Name "$($vm.Name) compose up" -RemoteCommand `
            "set -e; cd '$RemotePath'; docker compose --env-file deploy/three-vm.env -f '$($vm.Compose)' up -d $buildFlag --remove-orphans; docker compose --env-file deploy/three-vm.env -f '$($vm.Compose)' ps"
    }

    Wait-HttpEndpoint -Name "VM1 gateway" -Url "http://${Vm1Ip}:8080/actuator/health" -Seconds ([Math]::Max(180, $TimeoutSeconds * 9))
    Wait-HttpEndpoint -Name "VM3 AI service" -Url "http://${Vm3Ip}:8106/actuator/health" -Seconds ([Math]::Max(180, $TimeoutSeconds * 9))

    if (-not $SkipSmoke) {
        $smokeArgs = @{
            EnvFile = $normalizedEnvPath
            Vm1Ip = $Vm1Ip
            Vm2Ip = $Vm2Ip
            Vm3Ip = $Vm3Ip
            SshUser = $SshUser
            TimeoutSeconds = $TimeoutSeconds
        }
        if (-not [string]::IsNullOrWhiteSpace($DemoPassword)) {
            $smokeArgs.DemoPassword = $DemoPassword
        }
        if ($DryRun) {
            $smokeArgs.DryRun = $true
        }
        & (Join-Path $PSScriptRoot "check-three-vm-health.ps1") @smokeArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Three-VM health smoke failed."
        }
    }

    if (-not $SkipDistributedAiFlow -and -not $DryRun) {
        $flowArgs = @{
            BaseUrl = "http://${Vm1Ip}:8080"
            AiBaseUrl = "http://${Vm3Ip}:8106"
            TimeoutSeconds = [Math]::Max(15, $TimeoutSeconds)
        }
        if (-not [string]::IsNullOrWhiteSpace($DemoPassword)) {
            $flowArgs.DemoPassword = $DemoPassword
        }
        & (Join-Path $PSScriptRoot "check-distributed-ai-flow.ps1") @flowArgs
        if ($LASTEXITCODE -ne 0) {
            throw "Distributed AI flow check failed."
        }
    }

    Write-Host "Three-VM deployment completed." -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $tempDirectory -Recurse -Force -ErrorAction SilentlyContinue
}
