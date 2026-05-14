# start.ps1 – One-command startup for Snickr (Project 2)
# Run from the repo root: .\start.ps1

Write-Host "Starting Snickr..." -ForegroundColor Cyan

# 1. Start Postgres via Docker Compose
Write-Host "`n[1/3] Starting PostgreSQL..." -ForegroundColor Yellow
docker compose up -d
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Docker Compose failed. Make sure Docker Desktop is running." -ForegroundColor Red
    exit 1
}

# 2. Wait for Postgres to be ready
Write-Host "[2/3] Waiting for PostgreSQL to be ready..." -ForegroundColor Yellow
$retries = 15
for ($i = 0; $i -lt $retries; $i++) {
    $result = docker compose exec -T db pg_isready -U snickr_user -d snickr 2>&1
    if ($result -match "accepting connections") {
        Write-Host "PostgreSQL is ready!" -ForegroundColor Green
        break
    }
    Write-Host "  Waiting... ($($i+1)/$retries)"
    Start-Sleep -Seconds 2
}

# 3. Start Node.js app
Write-Host "[3/3] Starting Node.js app..." -ForegroundColor Yellow
Set-Location app
npm start
