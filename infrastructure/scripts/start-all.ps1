# ============================================================
# 启动所有 Docker 依赖服务 (Windows PowerShell)
# ============================================================

$ComposeDir = Join-Path $PSScriptRoot '..' 'docker' | Resolve-Path
$EnvFile    = Join-Path $ComposeDir '.env'

if (-not (Test-Path $EnvFile)) {
    Write-Host "[WARN] $EnvFile 不存在, 请先复制 .env.example" -ForegroundColor Yellow
    Write-Host "       cp infrastructure/docker/.env.example infrastructure/docker/.env"
}

Push-Location $ComposeDir
try {
    Write-Host "启动开发环境依赖..." -ForegroundColor Blue
    docker compose up -d --build
    Write-Host ""
    docker compose ps
} finally { Pop-Location }
