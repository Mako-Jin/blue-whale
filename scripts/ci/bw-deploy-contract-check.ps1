# 部署契约静态检查（V-08 / V-09）
#
# 目的：在不依赖 Docker 守护进程的情况下，提前拦住三类部署回归：
#   1) 前端镜像与同名后端服务共用 GHCR package / cache scope；
#   2) 生产 Nginx 缺少 qiankun 子应用前缀，或仍保留旧 /sub-app 示例；
#   3) 生产 compose 覆盖层缺少 TLS 证书挂载或 read-only 加固。
#
# 用法（PowerShell 7 / GitHub Actions pwsh）：
#   pwsh -NoProfile -File scripts/ci/bw-deploy-contract-check.ps1
#
# 退出码：0 通过；1 失败；2 文件缺失。

$ErrorActionPreference = 'Stop'
$script:failed = $false

function Assert-Contract {
    param(
        [string]$Label,
        [bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "OK   $Label $Detail"
        return
    }
    Write-Host "FAIL $Label $Detail"
    $script:failed = $true
}

function Read-Required {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        Write-Host "MISSING $Path"
        exit 2
    }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8
}

$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$workflowPath = Join-Path $root '.github/workflows/docker-deploy.yml'
$nginxPath = Join-Path $root 'infrastructure/docker/nginx/conf.d/micro-frontend.conf'
$prodComposePath = Join-Path $root 'infrastructure/docker/docker-compose.prod.yml'
$frontendDockerfilePath = Join-Path $root 'deploy/frontend/Dockerfile'

$workflow = Read-Required $workflowPath
$nginx = Read-Required $nginxPath
$prodCompose = Read-Required $prodComposePath
$frontendDockerfile = Read-Required $frontendDockerfilePath

# ---- 1. 镜像矩阵与命名空间 ----
$backendSection = $null
$frontendSection = $null
if ($workflow -match '(?ms)^\s*backend:\s*(?<body>.*?)^\s*frontend:\s*(?<frontend>.*?)^\s*steps:\s*') {
    $backendSection = $Matches['body']
    $frontendSection = $Matches['frontend']
} else {
    Write-Host 'FAIL 无法解析 docker-deploy.yml 的 backend/frontend 矩阵'
    $script:failed = $true
}

$backendServices = @()
$frontendImages = @()
$frontendBases = @{}
$frontendNamespaces = @{}
if ($backendSection) {
    $backendServices = [regex]::Matches($backendSection, '^\s*-\s*\{\s*service:\s*([a-z0-9-]+)', 'Multiline') |
        ForEach-Object { $_.Groups[1].Value }
    $backendServices = @($backendServices | Sort-Object -Unique)
}
if ($frontendSection) {
    $frontendRows = [regex]::Matches(
        $frontendSection,
        '^\s*-\s*\{\s*image:\s*([a-z0-9-]+).*?\bbase:\s*([^,]+),\s*namespace:\s*([a-z]+)',
        'Multiline')
    $frontendImages = @($frontendRows | ForEach-Object { $_.Groups[1].Value } | Sort-Object -Unique)
    foreach ($row in $frontendRows) {
        $frontendBases[$row.Groups[1].Value] = $row.Groups[2].Value.Trim()
        $frontendNamespaces[$row.Groups[1].Value] = $row.Groups[3].Value.Trim()
    }
}

Assert-Contract '后端镜像 23 项' ($backendServices.Count -eq 23) "count=$($backendServices.Count)"
Assert-Contract '前端镜像 16 项' ($frontendImages.Count -eq 16) "count=$($frontendImages.Count)"
Assert-Contract '前端按冲突项使用 frontend/ 命名空间' `
    ($workflow.Contains("matrix.namespace == 'frontend'") -and $workflow.Contains("'frontend/'"))
Assert-Contract '后端 cache scope 使用 be- 前缀' `
    ($workflow.Contains('scope=be-${{ matrix.service }}'))
Assert-Contract '前端 cache scope 使用 fe- 前缀' `
    ($workflow.Contains('scope=fe-${{ matrix.image }}'))
Assert-Contract '旧前端 cache scope 已移除' `
    (-not $workflow.Contains('scope=${{ matrix.image }}'))

$expectedCollisions = @(
    'whale-agent'
    'whale-contract'
    'whale-doc'
    'whale-erp'
    'whale-im'
    'whale-mes'
    'whale-music'
    'whale-oa'
    'whale-pdm'
    'whale-pm'
) | Sort-Object
$actualCollisions = @($frontendImages | Where-Object { $backendServices -contains $_ } | Sort-Object)
$collisionDiff = @(Compare-Object -ReferenceObject $actualCollisions -DifferenceObject $expectedCollisions)
Assert-Contract '前端/后端同名项为预期 10 项' `
    ($actualCollisions.Count -eq 10 -and $collisionDiff.Count -eq 0) `
    "actual=$($actualCollisions -join ',')"
foreach ($image in $frontendImages) {
    $namespace = if ($frontendNamespaces.ContainsKey($image)) { $frontendNamespaces[$image] } else { '<missing>' }
    $expectedNamespace = if ($backendServices -contains $image) { 'frontend' } else { 'root' }
    Assert-Contract "前端命名空间 $image" ($namespace -eq $expectedNamespace) `
        "actual=$namespace expected=$expectedNamespace"
}

$expectedBases = @{
    'whale-portal'   = '/portal'
    'whale-contract' = '/contract'
    'whale-erp'      = '/erp'
    'whale-oa'       = '/oa'
    'whale-mes'      = '/mes'
    'whale-pdm'      = '/pdm'
    'whale-pm'       = '/pm'
    'whale-im'       = '/im'
    'whale-doc'      = '/doc'
    'whale-music'    = '/music'
}
foreach ($app in $expectedBases.Keys | Sort-Object) {
    $actual = if ($frontendBases.ContainsKey($app)) { $frontendBases[$app] } else { '<missing>' }
    Assert-Contract "前端 base 映射 $app" ($actual -eq $expectedBases[$app]) "actual=$actual"
}

# ---- 2. Nginx 子应用前缀 ----
$feAppMatch = [regex]::Match($nginx, '\(\?<fe_app>([^)]+)\)')
$nginxApps = @()
if ($feAppMatch.Success) {
    $nginxApps = @($feAppMatch.Groups[1].Value -split '\|' | Sort-Object)
}
$expectedNginxApps = @($expectedBases.Values | ForEach-Object { $_.TrimStart('/') } | Sort-Object)
$nginxDiff = @(Compare-Object -ReferenceObject $nginxApps -DifferenceObject $expectedNginxApps)
Assert-Contract 'Nginx 覆盖 10 个 qiankun 子应用' `
    ($nginxApps.Count -eq 10 -and $nginxDiff.Count -eq 0) `
    "actual=$($nginxApps -join ',')"
Assert-Contract 'Nginx 使用变量延迟解析前端上游' `
    ($nginx.Contains('proxy_pass $fe_upstream;') -and $nginx.Contains('whale-fe-$fe_app:80'))
Assert-Contract 'Nginx 使用 Docker 内置 DNS resolver' `
    ($nginx.Contains('resolver 127.0.0.11'))
Assert-Contract '旧 /sub-app 示例已移除' `
    (-not $nginx.Contains('/sub-app/'))

# ---- 3. 生产 compose 覆盖层 ----
Assert-Contract '生产 compose 挂载 Let''s Encrypt fullchain' `
    ($prodCompose.Contains('/etc/letsencrypt/live/${TLS_SERVER_NAME}/fullchain.pem:/etc/nginx/ssl/fullchain.pem:ro'))
Assert-Contract '生产 compose 挂载 Let''s Encrypt privkey' `
    ($prodCompose.Contains('/etc/letsencrypt/live/${TLS_SERVER_NAME}/privkey.pem:/etc/nginx/ssl/privkey.pem:ro'))
Assert-Contract 'Nginx 容器 read-only + tmpfs 加固' `
    ($prodCompose.Contains('read_only: true') -and $prodCompose.Contains('/var/cache/nginx'))

# ---- 4. 前端镜像运行时约定 ----
Assert-Contract '前端镜像暴露 80 端口' ($frontendDockerfile.Contains('EXPOSE 80'))
Assert-Contract '前端镜像接收 APP_BASE' ($frontendDockerfile.Contains('ARG APP_BASE=/'))

if ($script:failed) {
    Write-Host 'RESULT: FAILED'
    exit 1
}
Write-Host 'RESULT: PASSED'
exit 0
