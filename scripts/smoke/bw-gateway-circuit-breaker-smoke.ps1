# V-14：网关熔断半开运行态取证（真实故障注入）
#
# 前置：
#   - 网关 8080 已启动，whale-audit 路由参与熔断（默认 apply-to-routes 包含 whale-audit）；
#   - whale-audit 服务未注册/不可用，用于制造真实下游失败；
#   - admin/123456 可登录；
#   - 网关日志已落盘，默认 logs/gateway.e2e.out.log。
#
# 覆盖：
#   1) 连续失败达到窗口 20 / 失败率 100% → CLOSED -> OPEN；
#   2) 打开后请求被短路，返回 503「服务暂时不可用」；
#   3) 等待 10s 后首个探测请求 → OPEN -> HALF_OPEN；
#   4) 探测仍失败 → HALF_OPEN -> OPEN。
#
# 用法：
#   powershell -File scripts/smoke/bw-gateway-circuit-breaker-smoke.ps1 -GatewayLog logs/gateway.e2e.out.log
#
# 退出码：0 通过；1 失败；2 参数/前置缺失。

param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$GatewayLog = 'logs/gateway.e2e.out.log',
    [int]$FailureCalls = 20,
    [int]$OpenWaitSeconds = 11
)

$ErrorActionPreference = 'Stop'
$script:failed = $false

function Assert($label, $cond, $detail = '') {
    if (-not $cond) {
        Write-Host "FAIL $label $detail"
        $script:failed = $true
        return
    }
    Write-Host "OK   $label $detail"
}

if (-not (Test-Path $GatewayLog)) {
    Write-Host "MISSING: 网关日志不存在：$GatewayLog"
    exit 2
}

function Login {
    $body = '{"username":"admin","password":"123456"}'
    $response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType 'application/json; charset=utf-8' -Body $body
    if ($response.code -ne 0 -or -not $response.data.accessToken) {
        throw "登录失败：$($response.message)"
    }
    return $response.data.accessToken
}

function Call-Audit($token) {
    $response = Invoke-WebRequest -Method Get -Uri "$BaseUrl/audit/log/page?current=1&size=1" `
        -Headers @{ Authorization = "Bearer $token" } -SkipHttpErrorCheck
    return [pscustomobject]@{ Status = [int]$response.StatusCode; Body = [string]$response.Content }
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

# 1) 真实下游失败：whale-audit 无实例，连续 20 次请求均走降级
$statuses = @()
for ($i = 1; $i -le $FailureCalls; $i++) {
    $result = Call-Audit $token
    $statuses += $result.Status
}
$non503 = @($statuses | Where-Object { $_ -ne 503 })
Assert "连续 $FailureCalls 次下游失败均返回 503" ($non503.Count -eq 0) "statuses=$($statuses -join ',')"
Assert "CLOSED -> OPEN 迁移日志" (Has-Log 'route=whale-audit, CLOSED -> OPEN')

# 2) 打开后请求应被短路，返回统一降级提示
$shortCircuit = Call-Audit $token
Assert "熔断打开后短路返回 503" ($shortCircuit.Status -eq 503) "status=$($shortCircuit.Status)"
Assert "短路降级文案" ($shortCircuit.Body -match '服务暂时不可用') $shortCircuit.Body

# 3) 等待打开的等待期后，首个探测请求进入 HALF_OPEN
Start-Sleep -Seconds $OpenWaitSeconds
$probe = Call-Audit $token
Assert "半开探测请求仍返回 503" ($probe.Status -eq 503) "status=$($probe.Status)"
Assert "OPEN -> HALF_OPEN 迁移日志" (Has-Log 'route=whale-audit, OPEN -> HALF_OPEN')

# 4) 探测失败后重新打开
Start-Sleep -Seconds 2
Assert "HALF_OPEN -> OPEN 迁移日志" (Has-Log 'route=whale-audit, HALF_OPEN -> OPEN')

if ($script:failed) {
    Write-Host "RESULT: FAIL"
    exit 1
}
Write-Host "RESULT: PASS"
exit 0
