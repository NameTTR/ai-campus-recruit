$Root = Split-Path -Parent $PSScriptRoot
$Backend = Join-Path $Root "backend"
$Frontend = Join-Path $Root "frontend"

$services = @(
    "ai-service",
    "auth-service",
    "user-service",
    "resume-service",
    "job-service",
    "match-service",
    "delivery-service",
    "gateway-service"
)

foreach ($service in $services) {
    Start-Process powershell -WindowStyle Hidden -ArgumentList @(
        "-NoExit",
        "-Command",
        "Set-Location '$Backend'; mvn -s settings.xml.example -pl $service -am spring-boot:run"
    )
}

Start-Process powershell -WindowStyle Hidden -ArgumentList @(
    "-NoExit",
    "-Command",
    "Set-Location '$Frontend'; npm run dev"
)

Write-Host "Local dev processes started."
Write-Host "Frontend: http://localhost:5173"
Write-Host "Gateway:  http://localhost:8080"
