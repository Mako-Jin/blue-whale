# ============================================================
# Wave 5.79 M5-B 综合看板收口冒烟：
#   综合看板聚合（生产/质量/设备/人员四域 + 口径一致性）
#   SSE 实时推送（POST text/event-stream）
#   CSV 导出（独立权限码 + Content-Disposition + UTF-8 BOM）
#   网关鉴权（无令牌 401/403）
#
# 前置：
#   1) gateway(8080)/auth(8081)/whale-mes(9002) 就绪；MySQL 与 Redis 可用；
#   2) bluewhale_auth 已执行 migration-5.79-mes-m5b.sql（权限 1381–1383 / 菜单 288）；
#   3) 管理员账号（凭据经 SMOKE_PASSWORD 环境变量提供）。
#
# 用法：pwsh -File scripts/smoke/bw-mes-m5b-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# ============================================================

$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$failures = 0

# 1) 登录
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }
$script:gateway = $base
$token = $login.data.accessToken

# 2) 综合看板聚合：四域齐全
$dash = Invoke-SmokeApi -Method Get -Path '/mes/dashboard'
Check '2.dashboard' $dash
$payload = $dash.data
Assert '2a.fourDomains' (
    $null -ne $payload.production -and $null -ne $payload.quality -and
    $null -ne $payload.equipment -and $null -ne $payload.workforce) "boardDate=$($payload.boardDate)"

# 3) 口径一致性：生产域与独立生产看板同源
$board = Invoke-SmokeApi -Method Get -Path '/mes/dispatch/board'
Check '3.dispatchBoard' $board
Assert '3a.productionConsistent' (
    [string]$payload.production.completedQuantity -eq [string]$board.data.completedQuantity -and
    [string]$payload.production.targetQuantity -eq [string]$board.data.targetQuantity) `
    "dashboard=$($payload.production.completedQuantity) board=$($board.data.completedQuantity)"

# 4) 质量域与质量看板同源
$qboard = Invoke-SmokeApi -Method Get -Path '/mes/quality/board'
Check '4.qualityBoard' $qboard
Assert '4a.qualityConsistent' (
    [string]$payload.quality.inspectionTaskCount -eq [string]$qboard.data.inspectionTaskCount) `
    "dashboard=$($payload.quality.inspectionTaskCount) board=$($qboard.data.inspectionTaskCount)"

# 5) 设备域计数自洽
Assert '5.equipmentCounts' (
    $payload.equipment.totalEquipmentCount -ge $payload.equipment.enabledEquipmentCount) `
    "total=$($payload.equipment.totalEquipmentCount) enabled=$($payload.equipment.enabledEquipmentCount)"

# 6) 人员域锁定进度自洽
Assert '6.workforceLock' (
    $payload.workforce.monthlyLockedCount -le $payload.workforce.monthlyStatCount) `
    "locked=$($payload.workforce.monthlyLockedCount) total=$($payload.workforce.monthlyStatCount)"

# 7) SSE 推送：POST text/event-stream，首个 dashboard 事件可读
Add-Type -AssemblyName System.Net.Http
$http = [System.Net.Http.HttpClient]::new()
$http.Timeout = [TimeSpan]::FromSeconds(30)
try {
    $req = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post, "$base/mes/dashboard/stream")
    $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    $req.Content = [System.Net.Http.StringContent]::new('{"intervalSeconds":5}',
        [System.Text.Encoding]::UTF8, 'application/json')
    $resp = $http.SendAsync($req, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    Assert '7.sseStatus' ([int]$resp.StatusCode -eq 200) "status=$([int]$resp.StatusCode)"
    $contentType = $resp.Content.Headers.ContentType.MediaType
    Assert '7a.sseContentType' ($contentType -eq 'text/event-stream') "contentType=$contentType"

    $stream = $resp.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
    $reader = [System.IO.StreamReader]::new($stream)
    # 服务端建立连接后立即推送一次快照（initialDelay=0），直接同步读行即可
    $eventLine = ''
    $dataLine = ''
    for ($i = 0; $i -lt 30; $i++) {
        $line = $reader.ReadLine()
        if ($null -eq $line) { break }
        if ($line.StartsWith('event:')) { $eventLine = $line }
        if ($line.StartsWith('data:')) { $dataLine = $line; break }
    }
    Assert '7b.sseEvent' ($eventLine -match 'dashboard') "event=$eventLine"
    Assert '7c.sseData' ($dataLine -match '"production"' -and $dataLine -match '"workforce"') `
        "dataLen=$($dataLine.Length)"
} finally {
    $http.CancelPendingRequests()
    $http.Dispose()
}

# 8) CSV 导出：权限码放行 + Content-Disposition + UTF-8 BOM + 四域标题
$exportReq = [System.Net.Http.HttpRequestMessage]::new(
    [System.Net.Http.HttpMethod]::Get, "$base/mes/dashboard/export")
$exportReq.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
$http2 = [System.Net.Http.HttpClient]::new()
try {
    $exportResp = $http2.SendAsync($exportReq).GetAwaiter().GetResult()
    Assert '8.exportStatus' ([int]$exportResp.StatusCode -eq 200) "status=$([int]$exportResp.StatusCode)"
    $disposition = [string]$exportResp.Content.Headers.ContentDisposition
    Assert '8a.attachment' ($disposition -match 'attachment') "disposition=$disposition"
    $bytes = $exportResp.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
    $csv = [System.Text.Encoding]::UTF8.GetString($bytes)
    Assert '8b.bom' ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) "head=$($bytes[0]),$($bytes[1]),$($bytes[2])"
    Assert '8c.domains' ($csv.Contains('生产域') -and $csv.Contains('质量域') -and $csv.Contains('设备域') -and $csv.Contains('人员域')) "len=$($bytes.Length)"
} finally {
    $http2.Dispose()
}

# 9) 网关鉴权：无令牌访问导出应被拒绝
$denied = $false
try {
    Invoke-RestMethod -Method Get -Uri "$base/mes/dashboard/export" | Out-Null
} catch {
    $denied = $true
    $status = $_.Exception.Response.StatusCode.value__
    Assert '9.anonymousDenied' ($status -eq 401 -or $status -eq 403) "status=$status"
}
Assert '9a.deniedRaised' $denied 'anonymous export should not succeed'

Exit-Smoke
