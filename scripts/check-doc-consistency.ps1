# ============================================================
# 文档一致性门禁（平台评审 P0-4）
# 以 whale-backend/whale-parent/pom.xml 为框架版本唯一事实源：
#   1) 现行事实文档（README / architecture / service-catalog）不得残留过期版本标记；
#   2) 文档中若显式声明 Spring Boot / Cloud / Alibaba 版本，必须与 POM 一致。
# 历史记录（docs/devlog、docs/reviews、docs/*/originals）为归档原文，不参与版本校验。
# 用法：pwsh -File scripts/check-doc-consistency.ps1
# 约定：通过输出 DOC-CONSISTENCY-PASS 且退出码 0；不一致输出 DOC-CONSISTENCY-FAIL 且退出码 1。
# ============================================================

[CmdletBinding()]
param(
    [string]$RepoRoot = $(if ($PSScriptRoot) { Split-Path -Parent $PSScriptRoot } else { (Get-Location).Path })
)

$ErrorActionPreference = 'Stop'

$pomPath = Join-Path $RepoRoot 'whale-backend/whale-parent/pom.xml'
if (-not (Test-Path -LiteralPath $pomPath)) {
    Write-Host "[FAIL] 未找到父 POM：$pomPath" -ForegroundColor Red
    exit 1
}

[xml]$pom = Get-Content -LiteralPath $pomPath -Raw -Encoding UTF8

function Get-PomProperty([string]$name) {
    $node = $pom.SelectSingleNode("/*[local-name()='project']/*[local-name()='properties']/*[local-name()='$name']")
    if (-not $node -or [string]::IsNullOrWhiteSpace($node.InnerText)) {
        Write-Host "[FAIL] 父 POM 缺少版本属性：$name" -ForegroundColor Red
        exit 1
    }
    return $node.InnerText.Trim()
}

$expected = @{
    'spring-boot.version'          = Get-PomProperty 'spring-boot.version'
    'spring-cloud.version'         = Get-PomProperty 'spring-cloud.version'
    'spring-cloud-alibaba.version' = Get-PomProperty 'spring-cloud-alibaba.version'
}

$truthDocs = @('README.md', 'docs/architecture.md', 'docs/service-catalog.md')
# 已知过期标记：Boot/Cloud 里程碑版本与旧 Alibaba 版本
$staleMarkers = @('4.0.0-M3', '2024.0.0-M3', '2023.0.3.2')
$errors = New-Object System.Collections.Generic.List[string]

foreach ($relative in $truthDocs) {
    $path = Join-Path $RepoRoot $relative
    if (-not (Test-Path -LiteralPath $path)) {
        $errors.Add("$relative 不存在")
        continue
    }
    $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    foreach ($marker in $staleMarkers) {
        if ($content -match [regex]::Escape($marker)) {
            $errors.Add("$relative 含过期版本标记：$marker")
        }
    }
}

# 仅校验「显式版本声明」，文档改为链接 POM/清单时不产生误报
$claims = @(
    @{
        Name     = 'Spring Boot'
        Property = 'spring-boot.version'
        Pattern  = 'Spring Boot\s+v?(\d+(?:\.\d+)+(?:-[A-Za-z0-9.]+)?)'
    },
    @{
        Name     = 'Spring Cloud Alibaba'
        Property = 'spring-cloud-alibaba.version'
        Pattern  = 'Spring Cloud Alibaba\s+v?(\d+(?:\.\d+)+(?:-[A-Za-z0-9.]+)?)'
    },
    @{
        Name     = 'Spring Cloud'
        Property = 'spring-cloud.version'
        Pattern  = 'Spring Cloud(?!\s+Alibaba)\s+v?(\d+(?:\.\d+)+(?:-[A-Za-z0-9.]+)?)'
    }
)

foreach ($relative in @('README.md', 'docs/architecture.md')) {
    $path = Join-Path $RepoRoot $relative
    if (-not (Test-Path -LiteralPath $path)) {
        continue
    }
    $content = Get-Content -LiteralPath $path -Raw -Encoding UTF8
    foreach ($claim in $claims) {
        foreach ($match in [regex]::Matches($content, $claim.Pattern, 'IgnoreCase')) {
            $actual = $match.Groups[1].Value
            $wanted = $expected[$claim.Property]
            if ($actual -ne $wanted) {
                $errors.Add("$relative 声明 $($claim.Name) $actual，与父 POM 的 $wanted 不一致")
            }
        }
    }
}

# ============================================================
# 状态对账检查（2026-11-06 新增，防「文档滞后于代码」复发）
#   A) service-catalog 迁移覆盖对账：每个 whale-apps 服务的 Flyway 迁移中，
#      凡含 CREATE TABLE 的版本号（V2..Vn）必须在 service-catalog 该服务行中出现；
#      V1 可由「基线/存量/baseline」字样代指。漏记即报错（MES 曾漏记 V4–V8）。
#   B) 门禁骨架声明对账：service-catalog 声明「门禁骨架」的服务，控制器数不得 > 3，
#      否则视为状态失真（CRM 曾以「仅 /crm/meta」描述 11 个控制器的现实）。
#   C) 端口/库名对账（DOC-10）：服务 application.yml 的默认端口与数据源库名
#      必须与 service-catalog 主表行一致；
#   D) 登记对账（DOC-10）：服务必须已在 naming-conventions.md 登记。
# ============================================================

$catalogPath = Join-Path $RepoRoot 'docs/service-catalog.md'
$serviceRoots = @(
    (Join-Path $RepoRoot 'whale-backend/whale-apps'),
    (Join-Path $RepoRoot 'whale-backend/whale-services')
) | Where-Object { Test-Path -LiteralPath $_ }

# 模块目录名与服务名已统一为 whale-service-* / whale-ops-*，无需别名映射
$catalogAliases = @{}

if ((Test-Path -LiteralPath $catalogPath) -and $serviceRoots.Count -gt 0) {
    $catalogLines = Get-Content -LiteralPath $catalogPath -Encoding UTF8
    $namingPath = Join-Path $RepoRoot 'docs/naming-conventions.md'
    $namingContent = if (Test-Path -LiteralPath $namingPath) {
        Get-Content -LiteralPath $namingPath -Raw -Encoding UTF8
    } else {
        ''
    }

    foreach ($svcRoot in $serviceRoots) {
    foreach ($svcDir in (Get-ChildItem -LiteralPath $svcRoot -Directory)) {
        $svcName = $svcDir.Name                          # 例：whale-mes
        $catalogName = if ($catalogAliases.ContainsKey($svcName)) { $catalogAliases[$svcName] } else { $svcName }
        $migrationDir = Join-Path $svcDir.FullName 'src/main/resources/db/migration'

        # 定位 service-catalog 中该服务的主表行（形如「| 9 | `whale-mes` | …」，
        # 排除文件头部「最后更新」汇总长行与前端表行——那两处混有其他服务的 V 号，会造成误放行）。
        # 注意：不能用 -like "*$needle*"——通配符模式会把反引号当转义符，含反引号的 needle 永远失配。
        $needle = '`' + $catalogName + '`'
        $row = $catalogLines |
            Where-Object { $_.Contains($needle) -and $_ -match '^\|\s*\d+\s*\|' } |
            Select-Object -First 1
        if (-not $row) { continue }

        if ($svcRoot -match 'whale-apps' -and (Test-Path -LiteralPath $migrationDir)) {
            # 检查 A：迁移版本覆盖
            foreach ($sqlFile in (Get-ChildItem -LiteralPath $migrationDir -Filter 'V*.sql')) {
                if ($sqlFile.BaseName -notmatch '^V(\d+)__') { continue }
                $versionNo = $Matches[1]
                $hasTable = (Select-String -LiteralPath $sqlFile.FullName -Pattern 'CREATE TABLE' -AllMatches) -ne $null
                if (-not $hasTable) { continue }
                if ($versionNo -eq '1' -and $row -match '基线|存量|baseline') { continue }
                if ($row -notmatch ('V' + [regex]::Escape($versionNo) + '\b')) {
                    $errors.Add("service-catalog.md $svcName 缺少迁移 V$versionNo 的记录（$($sqlFile.Name) 含建表）")
                }
            }
        }

        # 检查 B：门禁骨架声明不得与大量控制器并存
        if ($row -match '门禁骨架') {
            $controllerDir = Join-Path $svcDir.FullName 'src/main/java'
            $controllerCount = 0
            if (Test-Path -LiteralPath $controllerDir) {
                $controllerCount = (Get-ChildItem -LiteralPath $controllerDir -Recurse -Filter '*Controller.java' -ErrorAction SilentlyContinue).Count
            }
            if ($controllerCount -gt 3) {
                $errors.Add("service-catalog.md $svcName 仍声明「门禁骨架」，但该服务已有 $controllerCount 个控制器，状态失真")
            }
        }

        # 检查 C（DOC-10）：端口与库名必须与 service-catalog 主表行一致
        $cells = $row -split '\|'
        if ($cells.Count -gt 8) {
            $catalogPort = ($cells[5] -replace '[^\d]', '')
            $declaredPort = $null
            $declaredDb = $null
            $ymlFiles = Get-ChildItem -LiteralPath $svcDir.FullName -Recurse -Filter 'application.yml' -File -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '\\target\\' }
            foreach ($yml in $ymlFiles) {
                $text = Get-Content -LiteralPath $yml.FullName -Raw -Encoding UTF8
                if (-not $declaredPort -and $text -match 'SERVER_PORT:(\d{2,5})') {
                    $declaredPort = $Matches[1]
                }
                if (-not $declaredDb -and $text -match '/(bluewhale_[a-z0-9_]+)') {
                    $declaredDb = $Matches[1]
                }
            }
            if ($declaredPort -and $catalogPort -and $declaredPort -ne $catalogPort) {
                $errors.Add("service-catalog.md $svcName 端口 $catalogPort 与 application.yml 默认端口 $declaredPort 不一致")
            }
            if ($declaredDb -and -not $row.Contains($declaredDb)) {
                $errors.Add("service-catalog.md $svcName 未记录数据源库名 $declaredDb")
            }
        }

        # 检查 D（DOC-10）：服务必须已在 naming-conventions.md 登记
        if ($namingContent -and -not $namingContent.Contains($catalogName)) {
            $errors.Add("docs/naming-conventions.md 未登记服务 $catalogName")
        }
    }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "DOC-CONSISTENCY-FAIL（$($errors.Count) 项）" -ForegroundColor Red
    foreach ($item in $errors) {
        Write-Host "  - $item" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'DOC-CONSISTENCY-PASS' -ForegroundColor Green
Write-Host "  父 POM 版本：spring-boot=$($expected['spring-boot.version']) spring-cloud=$($expected['spring-cloud.version']) spring-cloud-alibaba=$($expected['spring-cloud-alibaba.version'])"
Write-Host "  已校验文档：$($truthDocs -join ', ')"
Write-Host "  已对账：端口 / 数据源库名 / naming-conventions 登记（whale-apps + whale-services）"
exit 0
