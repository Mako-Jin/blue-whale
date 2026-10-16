# ============================================================
# 全量构建 (Windows PowerShell)
# 后端 Maven 打包 + 前端 turbo build
# ============================================================

param(
    [ValidateSet('all','backend','frontend')]
    [string]$Target = 'all',
    [switch]$SkipTests,
    [switch]$DockerBuild
)

$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent

function Write-Step($title) {
    Write-Host ""
    Write-Host "===== $title =====" -ForegroundColor Blue
}

# ---- 后端 ----
if ($Target -in 'all','backend') {
    Write-Step "后端 Maven 构建"
    Push-Location "$Root\backend"
    try {
        $args = @('clean','package')
        if ($SkipTests) { $args += '-DskipTests' }
        & mvn @args
        if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
        Write-Host "后端构建完成" -ForegroundColor Green
    } finally { Pop-Location }
}

# ---- 前端 ----
if ($Target -in 'all','frontend') {
    Write-Step "前端 pnpm + turbo 构建"
    Push-Location "$Root\frontend"
    try {
        pnpm install
        if ($LASTEXITCODE -ne 0) { throw "pnpm install failed" }
        pnpm turbo run build
        if ($LASTEXITCODE -ne 0) { throw "turbo build failed" }
        Write-Host "前端构建完成" -ForegroundColor Green
    } finally { Pop-Location }
}

# ---- Docker ----
if ($DockerBuild) {
    Write-Step "Docker 镜像构建"
    Push-Location "$Root\infrastructure\docker"
    try {
        docker compose build
        if ($LASTEXITCODE -ne 0) { throw "docker compose build failed" }
    } finally { Pop-Location }
}

Write-Host ""
Write-Host "全部完成 ✓" -ForegroundColor Green
