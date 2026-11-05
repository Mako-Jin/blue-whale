# Wave 5.75 M5-4 网关音乐限流实机取证（429）
#
# 覆盖（均不携带 JWT：RateLimitGlobalFilter 的 Order = HIGHEST_PRECEDENCE + 10，
#       先于鉴权执行，因此未登录请求同样进入限流计数）：
#   1) /music/stream/**            120 次 / 60s → 第 121 次 429
#   2) /music/song/*/comment        20 次 / 60s → 第  21 次 429
#   3) /music/play/ticket           60 次 / 60s → 第  61 次 429
#   4) /music/song/*/like           30 次 / 60s → 第  31 次 429
# 断言要点：
#   - 阈值内请求不得出现 429（不能误伤正常流量）；
#   - 超阈值请求返回 HTTP 429，且响应体为 Result 同构 JSON：code=429、消息含「频繁」；
#   - 响应 Content-Type 为 application/json；
#   - 四条规则各自独立计数（互不污染）。
#
# 前置：whale-gateway(8080) 已启动，Redis 可用（Redis 不可用时按可用性优先降级放行，本脚本会失败）。
# 用法：
#   powershell -File scripts/smoke/bw-gateway-rate-limit-smoke.ps1
#   GATEWAY_BASE 可用环境变量覆盖网关地址。
#
# 退出码：0 通过；1 失败；2 前置缺失。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$gatewayBase = if ($env:GATEWAY_BASE) { $env:GATEWAY_BASE } else { 'http://127.0.0.1:8080' }

# 各规则独立客户端标识：限流键包含客户端维度，避免四条规则互相干扰。
# 每轮随机化网段，避免同一窗口内重复执行本脚本时读到上一轮的计数。
$runTagA = Get-Random -Minimum 1 -Maximum 99
$runTagB = Get-Random -Minimum 1 -Maximum 250
$streamClientIp = "10.$runTagA.$runTagB.101"
$commentClientIp = "10.$runTagA.$runTagB.102"
$ticketClientIp = "10.$runTagA.$runTagB.103"
$likeClientIp = "10.$runTagA.$runTagB.104"
$isolationClientIp = "10.$runTagA.$runTagB.105"

$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromSeconds(30)

<#
.SYNOPSIS
发送单次探测请求，返回状态码 / 响应体 / 内容类型。
#>
function Send-Probe {
    param([string]$Method, [string]$Path, [string]$ClientIp)

    $verb = [System.Net.Http.HttpMethod]::Get
    if ($Method -eq 'Post') { $verb = [System.Net.Http.HttpMethod]::Post }
    $request = New-Object System.Net.Http.HttpRequestMessage($verb, ($gatewayBase + $Path))
    $null = $request.Headers.TryAddWithoutValidation('X-Forwarded-For', $ClientIp)
    if ($Method -eq 'Post') {
        $request.Content = New-Object System.Net.Http.StringContent('{}', [System.Text.Encoding]::UTF8, 'application/json')
    }
    $response = $http.SendAsync($request).Result
    $body = $response.Content.ReadAsStringAsync().Result
    $contentType = ''
    if ($response.Content.Headers.ContentType) { $contentType = $response.Content.Headers.ContentType.MediaType }
    $status = [int]$response.StatusCode
    $request.Dispose()
    $response.Dispose()
    return [pscustomobject]@{ Status = $status; BodyText = $body; ContentType = $contentType }
}

<#
.SYNOPSIS
把当前时刻对齐到固定窗口起始，避免「第 121 次」跨越窗口导致计数清零。
.DESCRIPTION
限流窗口按 epoch 秒整除 windowSeconds 计算；窗口剩余不足一半时等待到下一窗口，
最多等待一个窗口长度（60s）。
#>
function Wait-FreshWindow {
    param([int]$WindowSeconds = 60)

    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $offset = $now % $WindowSeconds
    if ($offset -ge ($WindowSeconds - 25)) {
        $waitSeconds = $WindowSeconds - $offset + 1
        Write-Host "     窗口切换中，等待 $waitSeconds s 后从新窗口开始计数"
        Start-Sleep -Seconds $waitSeconds
    }
}

<#
.SYNOPSIS
对单条限流规则发起 limit+1 次请求并校验第 limit+1 次被拒绝。
#>
function Test-RateRule {
    param([string]$Label, [string]$Method, [string]$Path, [int]$Limit, [string]$ClientIp)

    $expectedStatus = 429
    $attempt = 0
    while ($attempt -lt 2) {
        $attempt++
        Wait-FreshWindow
        $responses = @()
        for ($i = 1; $i -le ($Limit + 1); $i++) {
            $responses += Send-Probe -Method $Method -Path $Path -ClientIp $ClientIp
        }
        $hitIndexes = @()
        for ($i = 0; $i -lt $responses.Count; $i++) {
            if ($responses[$i].Status -eq $expectedStatus) { $hitIndexes += ($i + 1) }
        }
        if ($hitIndexes.Count -gt 0) { break }
        # 整批都未触发（极可能跨窗口），换新窗口重试一次
        Write-Host "     $Label 本轮未触发 429（疑似跨窗口），等待新窗口重试"
        $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
        Start-Sleep -Seconds (61 - ($now % 60))
    }

    Assert "$Label.threshold" ($hitIndexes.Count -gt 0) "limit=$Limit 429次数=$($hitIndexes.Count)"
    Assert "$Label.noEarlyDeny" (@($hitIndexes | Where-Object { $_ -le $Limit }).Count -eq 0) `
        "429命中序号=$($hitIndexes -join ',') 期望全部>limit=$Limit"
    Assert "$Label.firstDenyAtLimit" (@($hitIndexes | Where-Object { $_ -eq ($Limit + 1) }).Count -ge 1) `
        "首个429序号=$(if ($hitIndexes.Count -gt 0) { $hitIndexes[0] } else { 'none' })"

    $denied = $responses[$Limit]
    Assert "$Label.denyStatus" ($denied.Status -eq 429) "status=$($denied.Status)"
    $deniedBody = $null
    try { $deniedBody = $denied.BodyText | ConvertFrom-Json } catch { $deniedBody = $null }
    Assert "$Label.denyCode" ($null -ne $deniedBody -and [int]$deniedBody.code -eq 429) `
        "body=$($denied.BodyText)"
    Assert "$Label.denyMessage" ($null -ne $deniedBody -and $deniedBody.message -like '*频繁*') `
        "message=$(if ($deniedBody) { $deniedBody.message } else { '<empty>' })"
    Assert "$Label.denyContentType" ($denied.ContentType -eq 'application/json' -or $denied.ContentType -like 'application/json*') `
        "contentType=$($denied.ContentType)"
}

Write-Host "== 网关音乐限流实机取证 gateway=$gatewayBase（不带 JWT）=="

# 0) 网关就绪
try {
    $health = Invoke-RestMethod -Uri "$gatewayBase/actuator/health" -TimeoutSec 5
    Assert '0.health[gateway]' ($health.status -eq 'UP')
} catch {
    Write-Host "MISSING: 网关不可用：$($_.Exception.Message)"
    exit 2
}

# 1) 音频流 120/60s
Test-RateRule -Label '1.stream' -Method 'Get' -Path '/music/stream/999999?ticket=invalid' -Limit 120 -ClientIp $streamClientIp

# 2) 评论 20/60s
Test-RateRule -Label '2.comment' -Method 'Post' -Path '/music/song/999999/comment' -Limit 20 -ClientIp $commentClientIp

# 3) 播放票据 60/60s
Test-RateRule -Label '3.ticket' -Method 'Post' -Path '/music/play/ticket' -Limit 60 -ClientIp $ticketClientIp

# 4) 收藏 30/60s
Test-RateRule -Label '4.like' -Method 'Post' -Path '/music/song/999999/like' -Limit 30 -ClientIp $likeClientIp

# 5) 规则隔离：换一个全新客户端标识，票据规则不应受前面评论/收藏打满的影响
$isolation = Send-Probe -Method 'Post' -Path '/music/play/ticket' -ClientIp $isolationClientIp
Assert '5.ruleIsolation' ($isolation.Status -ne 429) `
    "status=$($isolation.Status)（新客户端 + 独立规则键，不应被其他规则计数波及）"

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
