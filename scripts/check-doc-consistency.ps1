# ============================================================
# 文档一致性门禁（平台评审 P0-4）
# 以 backend/whale-parent/pom.xml 为框架版本唯一事实源：
#   1) 现行事实文档（README / architecture / service-catalog）不得残留过期版本标记；
#   2) 文档中若显式声明 Spring Boot / Cloud / Alibaba 版本，必须与 POM 一致。
# 历史记录（docs/devlog、docs/reviews、docs/*/originals）为归档原文，不参与版本校验。
# 用法：pwsh -File scripts/check-doc-consistency.ps1
# 约定：通过输出 DOC-CONSISTENCY-PASS 且退出码 0；不一致输出 DOC-CONSISTENCY-FAIL 且退出码 1。
# ============================================================

[CmdletBinding()]
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'

$pomPath = Join-Path $RepoRoot 'backend/whale-parent/pom.xml'
if (-not (Test-Path -LiteralPath $pomPath)) {
    Write-Host "[FAIL] 未找到父 POM：$pomPath" -ForegroundColor Red
    exit 1
}

[xml]$pom = Get-Content -LiteralPath $pomPath -Raw

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
    $content = Get-Content -LiteralPath $path -Raw
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
    $content = Get-Content -LiteralPath $path -Raw
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
exit 0
