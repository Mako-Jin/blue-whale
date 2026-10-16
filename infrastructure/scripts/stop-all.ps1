# ============================================================
# 停止所有 Docker 依赖服务 (Windows PowerShell)
# ============================================================

$ComposeDir = Join-Path $PSScriptRoot '..' 'docker' | Resolve-Path

Push-Location $ComposeDir
try {
    Write-Host "停止开发环境依赖..." -ForegroundColor Blue
    docker compose down
    Write-Host "已停止 ✓" -ForegroundColor Green
    Write-Host "如需清空数据卷: docker compose down -v" -ForegroundColor DarkGray
} finally { Pop-Location }
