<#
.SYNOPSIS
Restores a VM3 data backup created by backup-three-vm-data.ps1.

.DESCRIPTION
Uploads a backup archive to VM3 and restores MySQL, Redis, and MinIO data into
the current containers. This is destructive and requires -Force.

.EXAMPLE
.\scripts\restore-three-vm-data.ps1 -Archive .\backups\ai-campus-three-vm-20260612-011500.tar.gz -Vm3Host 192.168.6.142 -SshUser namettr -IdentityFile C:\Users\G5080\.ssh\id_ed25519 -Force
#>
[CmdletBinding()]
param(
    [string]$Archive = "",
    [Alias("BackupDirectory")]
    [string]$BackupDir = "",
    [string]$EnvFile = "",
    [string]$Vm3Host = "192.168.6.142",
    [string]$SshUser = "ubuntu",
    [string]$IdentityFile = "",
    [string]$RemotePath = "",
    [int]$TimeoutSeconds = 20,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

if (-not $Force) {
    throw "Restore overwrites VM3 data. Re-run with -Force after confirming the target VM and archive."
}

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

if ([string]::IsNullOrWhiteSpace($Archive) -and -not [string]::IsNullOrWhiteSpace($BackupDir)) {
    $backupPath = Resolve-ProjectPath $BackupDir
    $latest = Get-ChildItem -LiteralPath $backupPath -Filter "ai-campus-three-vm-*.tar.gz" -File -ErrorAction Stop |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -ne $latest) {
        $Archive = $latest.FullName
    }
}

if ([string]::IsNullOrWhiteSpace($Archive)) {
    throw "Archive is required. Pass -Archive <file> or -BackupDir <directory>."
}
if (-not (Test-Path -LiteralPath $Archive -PathType Leaf)) {
    throw "Archive not found: $Archive"
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

$options = Get-SshOptions
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$remoteArchive = "/tmp/ai-campus-restore-$timestamp.tar.gz"
$remoteWork = "/tmp/ai-campus-restore-$timestamp"

Write-Host "Uploading restore archive to $Vm3Host..." -ForegroundColor Cyan
& scp @options (Resolve-Path -LiteralPath $Archive).Path ("{0}@{1}:{2}" -f $SshUser, $Vm3Host, $remoteArchive)
if ($LASTEXITCODE -ne 0) {
    throw "Archive upload failed."
}

$remoteScript = @"
set -euo pipefail
cd "$RemotePath"
rm -rf "$remoteWork"
mkdir -p "$remoteWork"
tar -xzf "$remoteArchive" -C "$remoteWork"
test -s "$remoteWork/mysql.sql"
test -d "$remoteWork/minio-data"
test -d "$remoteWork/redis-data"

docker exec -i recruit-vm3-mysql sh -lc 'mysql -uroot -p"`$MYSQL_ROOT_PASSWORD" "`$MYSQL_DATABASE"' < "$remoteWork/mysql.sql"
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml stop redis minio
docker cp "$remoteWork/redis-data/." recruit-vm3-redis:/data
docker cp "$remoteWork/minio-data/." recruit-vm3-minio:/data
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d redis minio
docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps redis minio mysql
rm -rf "$remoteWork" "$remoteArchive"
"@

Write-Host "Restoring VM3 data..." -ForegroundColor Cyan
$remoteScript | & ssh @options "$SshUser@$Vm3Host" bash -s
if ($LASTEXITCODE -ne 0) {
    throw "Remote restore failed."
}
Write-Host "Restore completed." -ForegroundColor Green
