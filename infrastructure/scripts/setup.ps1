# ============================================================
# 蓝鲸系统 - 本地环境初始化 (Windows PowerShell)
# 检查并提示需要安装的依赖
# ============================================================

param(
    [switch]$AutoInstall
)

$ErrorActionPreference = 'Continue'

function Write-Ok   ($msg) { Write-Host "[OK]   " -ForegroundColor Green -NoNewline; Write-Host $msg }
function Write-Warn ($msg) { Write-Host "[WARN] " -ForegroundColor Yellow -NoNewline; Write-Host $msg }
function Write-Err  ($msg) { Write-Host "[ERR]  " -ForegroundColor Red -NoNewline; Write-Host $msg }
function Write-Hint ($msg) { Write-Host "[HINT] " -ForegroundColor Cyan -NoNewline; Write-Host $msg }

function Test-Command($name) {
    return [bool](Get-Command $name -ErrorAction SilentlyContinue)
}

function Test-Package($id) {
    # winget 检查
    if (Test-Command 'winget') {
        try {
            winget list --id $id --source winget 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { return $true }
        } catch {}
    }
    return $false
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Blue
Write-Host "  BlueWhale 本地环境初始化 (Windows)" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

$missing = @()

# ---- Java ----
Write-Host "Java (JDK 21):" -ForegroundColor Magenta
if (Test-Command 'java') {
    $ver = (& java -version 2>&1 | Select-Object -First 1)
    Write-Ok "已安装: $ver"
} else {
    Write-Warn "未检测到 java"
    $missing += 'OpenJDK 21 (https://adoptium.net/)'
}

# ---- Maven ----
Write-Host "Maven:" -ForegroundColor Magenta
if (Test-Command 'mvn') {
    $ver = (& mvn -v 2>&1 | Select-Object -First 1)
    Write-Ok "已安装: $ver"
} else {
    Write-Warn "未检测到 mvn"
    $missing += 'Apache Maven 3.9+ (https://maven.apache.org/)'
}

# ---- Node.js ----
Write-Host "Node.js:" -ForegroundColor Magenta
if (Test-Command 'node') {
    $ver = node -v
    Write-Ok "已安装: $ver"
} else {
    Write-Warn "未检测到 node"
    $missing += 'Node.js 24 LTS (https://nodejs.org/), 或使用 fnm/nvm'
}

# ---- pnpm ----
Write-Host "pnpm:" -ForegroundColor Magenta
if (Test-Command 'pnpm') {
    $ver = pnpm -v
    Write-Ok "已安装: v$ver"
} elseif (Test-Command 'corepack') {
    Write-Warn "pnpm 未激活, 可用: corepack enable; corepack prepare pnpm@latest --activate"
    $missing += 'pnpm 已随 Node 安装, 需执行 corepack enable'
} else {
    Write-Warn "未检测到 pnpm"
    $missing += 'pnpm 8+ (npm i -g pnpm)'
}

# ---- Docker ----
Write-Host "Docker Desktop:" -ForegroundColor Magenta
if (Test-Command 'docker') {
    $ver = docker --version
    Write-Ok "已安装: $ver"
    if (Test-Command 'docker-compose') {
        Write-Ok "docker-compose 可用"
    } elseif (docker compose version 2>$null) {
        Write-Ok "docker compose (plugin) 可用"
    } else {
        Write-Warn "未检测到 docker compose 插件"
    }
} else {
    Write-Warn "未检测到 docker"
    $missing += 'Docker Desktop for Windows (https://www.docker.com/products/docker-desktop/)'
}

# ---- MySQL 客户端 ----
Write-Host "MySQL Client:" -ForegroundColor Magenta
if (Test-Command 'mysql') {
    Write-Ok "已安装: $(& mysql --version)"
} else {
    Write-Hint "未检测到 mysql 客户端 (非必须, 开发可使用 Navicat/DBeaver)"
}

# ---- Git ----
Write-Host "Git:" -ForegroundColor Magenta
if (Test-Command 'git') {
    Write-Ok "已安装: $(& git --version)"
} else {
    Write-Warn "未检测到 git"
    $missing += 'Git for Windows (https://git-scm.com/)'
}

Write-Host ""
if ($missing.Count -eq 0) {
    Write-Ok "所有必需依赖已就绪 ✓"
} else {
    Write-Host "----------------------------------------" -ForegroundColor Yellow
    Write-Warn "需安装/配置 $($missing.Count) 项依赖:"
    Write-Host "----------------------------------------" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host "  - $_" }
    Write-Host ""

    if ($AutoInstall -and (Test-Command 'winget')) {
        Write-Host "尝试使用 winget 自动安装..." -ForegroundColor Cyan
        winget install EclipseAdoptium.Temurin.21.JDK   --silent --accept-package-agreements --accept-source-agreements
        winget install Apache.Maven                      --silent --accept-package-agreements --accept-source-agreements
        winget install OpenJS.NodeJS.LTS                 --silent --accept-package-agreements --accept-source-agreements
        winget install Git.Git                           --silent --accept-package-agreements --accept-source-agreements
        winget install Docker.DockerDesktop              --silent --accept-package-agreements --accept-source-agreements
    } else {
        Write-Hint "提示: 以管理员身份运行 $PSCommandPath -AutoInstall 可使用 winget 自动安装"
    }
}

Write-Host ""
Write-Host "后续步骤:" -ForegroundColor Cyan
Write-Host "  1. cp infrastructure/docker/.env.example infrastructure/docker/.env   # 修改敏感信息"
Write-Host "  2. cd infrastructure/docker"
Write-Host "  3. docker compose up -d"
Write-Host ""
