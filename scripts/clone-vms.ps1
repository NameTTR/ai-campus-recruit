param(
    [string]$VmrunPath = "D:\Program Files (x86)\VMware\VMware Workstation\vmrun.exe",
    [string]$SourceDir = "D:\Virtual_Machines\Ubuntu18_64_2",
    [string]$SourceVmx = "",
    [string]$TargetRoot = "D:\Virtual_Machines"
)

if (-not (Test-Path -LiteralPath $VmrunPath)) {
    throw "vmrun.exe not found: $VmrunPath"
}

if ([string]::IsNullOrWhiteSpace($SourceVmx)) {
    $SourceVmx = Get-ChildItem -LiteralPath $SourceDir -Filter *.vmx | Select-Object -First 1 -ExpandProperty FullName
}

if (-not $SourceVmx -or -not (Test-Path -LiteralPath $SourceVmx)) {
    throw "source vmx not found: $SourceVmx"
}

$targets = @(
    @{ Name = "ai-recruit-vm2"; Path = Join-Path $TargetRoot "ai-recruit-vm2\ai-recruit-vm2.vmx" },
    @{ Name = "ai-recruit-vm3"; Path = Join-Path $TargetRoot "ai-recruit-vm3\ai-recruit-vm3.vmx" }
)

foreach ($target in $targets) {
    $targetDir = Split-Path -Parent $target.Path
    if (Test-Path -LiteralPath $targetDir) {
        Write-Host "Skip existing VM directory: $targetDir"
        continue
    }
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    & $VmrunPath clone $SourceVmx $target.Path full "-cloneName=$($target.Name)"
}
