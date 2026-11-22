# V-14：网关熔断半开运行态取证（真实故障注入）
#
# 前置：
#   - 网关 8080 已启动，whale-ops-audit 路由参与熔断（默认 apply-to-routes 包含 whale-ops-audit）；
#   - whale-ops-audit 服务未注册/不可用，用于制造真实下游失败；
#   - admin 凭据经 SMOKE_PASSWORD 环境变量提供且可登录；
#   - 网关日志已落盘，默认 logs/gateway.e2e.out.log（建议用 `docker logs -f bw-gateway` 持续追加）。
#
# 覆盖：
#   1) 连续失败达到窗口 20 / 失败率 100% → CLOSED -> OPEN；
#   2) 打开后请求被短路，返回 503「服务暂时不可用」，并抓取 prometheus 状态指标；
#   3) 等待 10s 后首个探测请求 → OPEN -> HALF_OPEN；
#   4) 探测仍失败 → HALF_OPEN -> OPEN；
#   5)（-Recovery）下游恢复后轮询等待 → 半开探测成功 → HALF_OPEN -> CLOSED，且后续请求 200。
#
# 用法：
#   powershell -File scripts/smoke/bw-gateway-circuit-breaker-smoke.ps1 -GatewayLog logs/gateway.e2e.out.log
#   # 恢复路径：先在另一终端启动 whale-ops-audit，再带 -Recovery 运行（脚本会轮询等待其可用）
#
# 退出码：0 通过；1 失败；2 参数/前置缺失。

param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$GatewayLog = 'logs/gateway.e2e.out.log',
    [int]$FailureCalls = 20,
    [int]$OpenWaitSeconds = 11,
    [switch]$Recovery,
    [int]$RecoveryTimeoutSeconds = 240
)
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"

# Windows PowerShell 5.1 默认不加载 System.Net.Http（Call-Audit 的 HttpClient 需要）
if (-not ('System.Net.Http.HttpClient' -as [type])) {
    Add-Type -AssemblyName System.Net.Http
}


$ErrorActionPreference = 'Stop'
$script:failed = $false

if (-not (Test-Path $GatewayLog)) {
    Write-Host "MISSING: 网关日志不存在：$GatewayLog"
    exit 2
}

function Login {
    $cred = Get-SmokeAdminCredential
    $body = $cred.JsonBody
    # auth URL 已版本化（/auth/v1/**），旧路径 /auth/login 会被网关 404
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/v1/login" -ContentType 'application/json; charset=utf-8' -Body $body
    if ($response.code -ne 0 -or -not $response.data.accessToken) {
        throw "登录失败：$($response.message)"
    }
    return $response.data.accessToken
}

function Call-Audit($token) {
    # 用 .NET HttpClient：非 2xx 不抛异常，且不受 5.1 Invoke-WebRequest「异常响应流已被释放」影响
    $client = New-Object System.Net.Http.HttpClient
    try {
        $client.DefaultRequestHeaders.Authorization =
            New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
        $resp = $client.GetAsync("$BaseUrl/audit/log/page?current=1&size=1").GetAwaiter().GetResult()
        $body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        return [pscustomobject]@{ Status = [int]$resp.StatusCode; Body = $body }
    } finally {
        $client.Dispose()
    }
}

function Get-CircuitMetrics {
    # 从 prometheus 端点抓取熔断指标（缺失/未暴露时返回空串，不阻断主流程）
    try {
        $prom = (Invoke-WebRequest -Uri "$BaseUrl/actuator/prometheus" -UseBasicParsing).Content
        $lines = @($prom -split "`n" | Where-Object { $_ -match '^gateway_circuit_(state|short_circuits_total|calls_total)\{[^}]*route="whale-ops-audit"' })
        return ($lines | ForEach-Object { $_.Trim() }) -join "`n"
    } catch {
        return ''
    }
}

function Has-Log($pattern) {
    return [bool](Select-String -Path $GatewayLog -Pattern $pattern -Quiet)
}

try {
    $token = Login
    Write-Host "OK   登录成功"
} catch {
    Write-Host "FAIL 登录失败：$($_.Exception.Message)"
    exit 2
}

# 1) 真实下游失败：whale-ops-audit 无实例，连续 20 次请求均走降级
$statuses = @()
for ($i = 1; $i -le $FailureCalls; $i++) {
    $result = Call-Audit $token
    $statuses += $result.Status
}
$non503 = @($statuses | Where-Object { $_ -ne 503 })
Assert "连续 $FailureCalls 次下游失败均返回 503" ($non503.Count -eq 0) "statuses=$($statuses -join ',')"
Assert "CLOSED -> OPEN 迁移日志" (Has-Log 'route=whale-ops-audit, CLOSED -> OPEN')

# 2) 打开后请求应被短路，返回统一降级提示
$shortCircuit = Call-Audit $token
Assert "熔断打开后短路返回 503" ($shortCircuit.Status -eq 503) "status=$($shortCircuit.Status)"
Assert "短路降级文案" ($shortCircuit.Body -match '服务暂时不可用') $shortCircuit.Body
$metrics = Get-CircuitMetrics
Write-Host "INFO 熔断指标（OPEN 期）:"
if ($metrics) { Write-Host $metrics } else { Write-Host "WARN prometheus 指标不可用，跳过" }

# 3) 等待打开的等待期后，首个探测请求进入 HALF_OPEN
Start-Sleep -Seconds $OpenWaitSeconds
$probe = Call-Audit $token
Assert "半开探测请求仍返回 503" ($probe.Status -eq 503) "status=$($probe.Status)"
Assert "OPEN -> HALF_OPEN 迁移日志" (Has-Log 'route=whale-ops-audit, OPEN -> HALF_OPEN')

# 4) 探测失败后重新打开
Start-Sleep -Seconds 2
Assert "HALF_OPEN -> OPEN 迁移日志" (Has-Log 'route=whale-ops-audit, HALF_OPEN -> OPEN')

# 5) 恢复路径（-Recovery）：下游恢复后轮询等待半开探测成功 → HALF_OPEN -> CLOSED
if ($Recovery) {
    Write-Host "等待下游恢复并放行半开探测（最长 $RecoveryTimeoutSeconds 秒）..."
    $deadline = (Get-Date).AddSeconds($RecoveryTimeoutSeconds)
    $recovered = $false
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 5
        $probe2 = Call-Audit $token
        if ($probe2.Status -eq 200) {
            $recovered = $true
            break
        }
        # 503 = 熔断打开短路或探测失败再打开，等下一轮探测即可
    }
    Assert "下游恢复后半开探测成功（请求 200）" $recovered "lastStatus=$($probe2.Status)"
    Assert "HALF_OPEN -> CLOSED 迁移日志" (Has-Log 'route=whale-ops-audit, HALF_OPEN -> CLOSED')
    $after = Call-Audit $token
    Assert "恢复闭合后请求正常 200" ($after.Status -eq 200) "status=$($after.Status) body=$($after.Body)"
    $metricsClosed = Get-CircuitMetrics
    Write-Host "INFO 熔断指标（CLOSED 期）:"
    if ($metricsClosed) { Write-Host $metricsClosed }
}

if ($script:failed) {
    Write-Host "RESULT: FAIL"
    exit 1
}
Write-Host "RESULT: PASS"
exit 0
