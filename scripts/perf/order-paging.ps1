# ============================================================
# M1 压测基线 2/3：订单列表分页 P95
#
# 口径：ERP 销售订单分页在读多页场景下的 HTTP 往返延迟，分别测 10/50/100 条/页，
#       页码在 1~5 之间轮转，覆盖首页与浅分页；均为已登录管理员（含权限判定）。
#
# 用法：
#   pwsh -File scripts/perf/order-paging.ps1 [-Iterations 100]
# 前置：gateway + whale-erp 在线且已有订单数据，SMOKE_PASSWORD 提供管理员口令。
# 输出：Markdown 表格行，粘进 docs/ops/m1-perf-baseline.md。
# ============================================================

param(
    [string]$BaseUri = '',
    [int]$Iterations = 100,
    [int]$Warmup = 5
)

if (-not $BaseUri) {
    $BaseUri = if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }
}

. "$PSScriptRoot/lib/perf.ps1"
. "$PSScriptRoot/../smoke/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')

$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/v1/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
if ($login.code -ne 0 -or $null -eq $login.data.accessToken) {
    throw "登录失败：code=$($login.code) message=$($login.message)"
}
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

Write-Host "| 场景 | 成功样本 | 失败 | P50(ms) | P95(ms) | P99(ms) | 均值(ms) |"
Write-Host "| --- | --- | --- | --- | --- | --- | --- |"
foreach ($size in @(10, 50, 100)) {
    for ($i = 0; $i -lt $Warmup; $i++) {
        Invoke-SmokeApi -Method Get -Path "/erp/scm/sales-order/page?current=1&size=$size" | Out-Null
    }
    # 预生成页码序列（1~5 轮转），用队列避免脚本块内变量作用域不回写
    $paths = New-Object 'System.Collections.Generic.Queue[string]'
    for ($i = 0; $i -lt $Iterations; $i++) {
        $paths.Enqueue("/erp/scm/sales-order/page?current=$((($i % 5) + 1))&size=$size")
    }
    $stat = Invoke-PerfLoop -Label "销售订单分页 size=$size" -Iterations $Iterations -Call {
        Invoke-SmokeApi -Method Get -Path $paths.Dequeue()
    }
    Write-PerfRow -Stat $stat
    if ($stat.Errors -gt 0) {
        Write-Host "WARN size=$size 失败请求 $($stat.Errors) 次，P95 仅供参考"
    }
}
