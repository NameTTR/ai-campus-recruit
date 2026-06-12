<#
.SYNOPSIS
Backs up VM3 data stores for the three-VM deployment.

.DESCRIPTION
Creates a timestamped archive containing:
- MySQL logical dump from recruit-vm3-mysql
- Redis /data snapshot files from recruit-vm3-redis
- MinIO /data object files from recruit-vm3-minio

SSH passwords are not accepted as arguments. Use an SSH key or interactive
OpenSSH prompt.

.EXAMPLE
.\scripts\backup-three-vm-data.ps1 -Vm3Host 192.168.6.142 -SshUser namettr -IdentityFile C:\Users\G5080\.ssh\id_ed25519
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "",
    [string]$Vm3Host = "192.168.6.142",
    [string]$SshUser = "ubuntu",
    [string]$IdentityFile = "",
    [string]$RemotePath = "",
    [Alias("OutputDir")]
    [string]$BackupDirectory = ".\backups",
    [int]$TimeoutSeconds = 20
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

if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    $envPath = Resolve-ProjectPath $EnvFile
    $values = Import-DotEnvMap -Path $envPath
    if (-not $PSBoundParameters.ContainsKey("Vm3Host") -and -not [string]::IsNullOrWhiteSpace($values.VM3_HOST)) {
        $Vm3Host = $values.VM3_HOST
    }
}

if ([string]::IsNullOrWhiteSpace($RemotePath)) {
    $RemotePath = "/home/$SshUser/ai-campus-recruit"
}

function Get-SshOptions {
    $options = @("-o", "ConnectTimeout=$TimeoutSeconds", "-o", "StrictHostKeyChecking=accept-new")
    if (-not [string]::IsNullOrWhiteSpace($IdentityFile)) {
        $options += @("-i", (Resolve-Path -LiteralPath $IdentityFile).Path)
    }
    return $options
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$remoteWork = "/tmp/ai-campus-backup-$timestamp"
$remoteArchive = "$remoteWork.tar.gz"
$localDirectory = if ([System.IO.Path]::IsPathRooted($BackupDirectory)) {
    $BackupDirectory
} else {
    Resolve-ProjectPath $BackupDirectory
}
$localArchive = Join-Path $localDirectory "ai-campus-three-vm-$timestamp.tar.gz"
New-Item -ItemType Directory -Force -Path $localDirectory | Out-Null

$remoteScript = @"
set -euo pipefail
cd "$RemotePath"
rm -rf "$remoteWork"
mkdir -p "$remoteWork/payload"
docker exec recruit-vm3-mysql sh -lc 'mysqldump -uroot -p"`$MYSQL_ROOT_PASSWORD" --single-transaction --routines --events "`$MYSQL_DATABASE"' > "$remoteWork/payload/mysql.sql"
docker exec recruit-vm3-redis redis-cli BGSAVE >/dev/null || true
sleep 2
docker cp recruit-vm3-redis:/data "$remoteWork/payload/redis-data"
docker cp recruit-vm3-minio:/data "$remoteWork/payload/minio-data"
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps > "$remoteWork/payload/vm3-compose-ps.txt"
date -Is > "$remoteWork/payload/backup-created-at.txt"
tar -C "$remoteWork/payload" -czf "$remoteArchive" .
rm -rf "$remoteWork"
printf '%s\n' "$remoteArchive"
"@

$options = Get-SshOptions
Write-Host "Creating VM3 backup on $Vm3Host..." -ForegroundColor Cyan
$createdArchive = $remoteScript | & ssh @options "$SshUser@$Vm3Host" bash -s
if ($LASTEXITCODE -ne 0) {
    throw "Remote backup failed."
}
$remoteArchivePath = ($createdArchive | Select-Object -Last 1).Trim()
Write-Host "Downloading backup archive..." -ForegroundColor Cyan
& scp @options ("{0}@{1}:{2}" -f $SshUser, $Vm3Host, $remoteArchivePath) $localArchive
if ($LASTEXITCODE -ne 0) {
    throw "Backup download failed."
}

"rm -f '$remoteArchivePath'" | & ssh @options "$SshUser@$Vm3Host" bash -s | Out-Null
Write-Host "Backup written: $localArchive" -ForegroundColor Green
