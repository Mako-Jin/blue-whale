# ============================================================
# M1 压测基线 1/3：权限判定 P95
#
# 口径：带 @RequirePermission 的读接口，网关注入 X-Permission-Codes 后，
#       service 侧权限拦截器完成判定的整条 HTTP 往返延迟。
#       默认取 ERP 生产订单分页（sys:erp:mfg:list）。
#
# 用法：
#   pwsh -File scripts/perf/perm-decision.ps1 [-Iterations 200] [-Path '/erp/v1/mfg/production/page?current=1&size=10']
# 前置：gateway + 目标服务在线，SMOKE_PASSWORD 提供管理员口令。
# 输出：Markdown 表格行，粘进 docs/ops/m1-perf-baseline.md。
# ============================================================

param(
    [string]$BaseUri = '',
    [string]$Path = '/erp/v1/mfg/production/page?current=1&size=10',
    [int]$Iterations = 200,
    [int]$Warmup = 10
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

# 预热：排除连接建立与 JIT 对首个样本的影响
for ($i = 0; $i -lt $Warmup; $i++) {
    Invoke-SmokeApi -Method Get -Path $Path | Out-Null
}

Write-Host "| 场景 | 成功样本 | 失败 | P50(ms) | P95(ms) | P99(ms) | 均值(ms) |"
Write-Host "| --- | --- | --- | --- | --- | --- | --- |"
$stat = Invoke-PerfLoop -Label "权限判定 $Path" -Iterations $Iterations -Call {
    Invoke-SmokeApi -Method Get -Path $Path
}
Write-PerfRow -Stat $stat

if ($stat.Errors -gt 0) {
    Write-Host "WARN 失败请求 $($stat.Errors) 次，P95 仅供参考"
}
