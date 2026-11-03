﻿# ============================================================
# Wave 5.77 M5 安全与上线硬化冒烟
#
# 覆盖：
#   0) 服务就绪 / meta 契约 / 权限拒绝 / 内网接口不可达
#   1) 声明签署前置 + 上传 → 建短视频 → 发布（造数，供后续断言）
#   2) 上传安全：MIME 白名单 / magic bytes 一致性 / 封面白名单 / 文件不存在 / 时长越界
#   3) 播放票据：TTL 300s + 刷新 240s + streamUrl 无文件 ID；缺失票据 / 跨用户 / 跨内容 40302
#   4) 流式 Range：206 + Content-Range + Accept-Ranges + private,no-store + nosniff；越界 416
#   5) 越权矩阵：他人私密 40401、可见非本人 40301、跨租户 40401、跨租户封面拒绝
#   6) 互动与进度：点赞 / 收藏幂等、进度上报、最近观看与删除
#   7) 指标直方图：meta / home / content 详情 / stream 四类 uri 桶
#   8) 可选网关段：/video/internal/** 403、伪造身份头 401（需 GATEWAY_BASE）
#   9) 清理：内容 + 文件
#
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 就绪。
# 说明：本机无登录口令，脚本与两个服务同网段直连，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-video-m5-smoke.ps1
#   VIDEO_BASE / FILE_BASE / GATEWAY_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID /
#   VIDEO_PERMISSION_CODES 可用环境变量覆盖。
# ============================================================

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

$videoBase = Get-VideoBase
$fileBase = Get-FileBase
$gatewayBase = if ($env:GATEWAY_BASE) { $env:GATEWAY_BASE.TrimEnd('/') } else { '' }
$userId = Get-VideoUserId
$tenantId = Get-VideoTenantId
$otherTenantId = if ($tenantId -eq '2') { '1' } else { '2' }
$stamp = Get-Date -Format 'MMddHHmmss'

$h = New-VideoHeaders
$ownerHeaders = @{
    'X-User-Id'          = '3'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:video:upload,sys:video:browse'
}
$readerHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:video:browse,sys:video:like,sys:video:favorite,sys:video:comment:create'
}
$otherTenantHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = $otherTenantId
    'X-Permission-Codes' = 'sys:video:browse,sys:video:like,sys:video:favorite'
}

$payloadBytes = 4096
$mp4Path = Join-Path $env:TEMP ('bw-video-m5-' + $stamp + '.mp4')
$webmPath = Join-Path $env:TEMP ('bw-video-m5-' + $stamp + '.webm')
$txtPath = Join-Path $env:TEMP ('bw-video-m5-' + $stamp + '.txt')
$pngPath = Join-Path $env:TEMP ('bw-video-m5-' + $stamp + '.png')
$uploadedFiles = New-Object System.Collections.Generic.List[object]
$createdContents = New-Object System.Collections.Generic.List[object]

<#
.SYNOPSIS
生成带 MP4 ftyp 头的假视频字节（VideoUploadPolicy 只读前 12 字节）。
.PARAMETER Path
目标文件路径。
.PARAMETER Length
文件字节数。
.PARAMETER WebmMagic
为 $true 时写入 WebM magic（用于「声明 video/mp4 但文件头是 WebM」的一致性断言）。
#>
function New-SmokeVideoFile {
    param([string]$Path, [int]$Length, [switch]$WebmMagic)

    [byte[]]$bytes = New-Object byte[] $Length
    if ($WebmMagic) {
        $bytes[0] = 0x1A
        $bytes[1] = 0x45
        $bytes[2] = 0xDF
        $bytes[3] = 0xA3
    } elseif ($Length -ge 12) {
        [BitConverter]::GetBytes([int]0x18).CopyTo($bytes, 0)
        [System.Text.Encoding]::ASCII.GetBytes('ftyp').CopyTo($bytes, 4)
        [System.Text.Encoding]::ASCII.GetBytes('mp42').CopyTo($bytes, 8)
    }
    [System.IO.File]::WriteAllBytes($Path, $bytes)
}

<#
.SYNOPSIS
以 multipart 上传文件，返回 Result 信封。
.PARAMETER Path
本地文件路径。
.PARAMETER ContentType
声明的 MIME 类型（用于验证服务侧白名单与 magic bytes 一致性）。
.PARAMETER Headers
身份请求头，默认使用脚本主身份。
#>
function Send-VideoUpload {
    param([string]$Path, [string]$ContentType, [hashtable]$Headers = $h)

    $stream = New-Object System.IO.FileStream($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add($fileContent, 'file', [System.IO.Path]::GetFileName($Path))
        $request = New-Object System.Net.Http.HttpRequestMessage(
            [System.Net.Http.HttpMethod]::Post, ($fileBase + '/file/upload'))
        $request.Content = $form
        foreach ($key in $Headers.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
        }
        if (-not $script:VideoHttp) {
            $script:VideoHttp = New-Object System.Net.Http.HttpClient
            $script:VideoHttp.Timeout = [TimeSpan]::FromMinutes(5)
        }
        $response = $script:VideoHttp.SendAsync($request).Result
        $bodyText = $response.Content.ReadAsStringAsync().Result
        $result = $null
        if ($bodyText) {
            try { $result = $bodyText | ConvertFrom-Json } catch { $result = $null }
        }
        return [pscustomobject]@{ Status = [int]$response.StatusCode; BodyText = $bodyText; Result = $result }
    } finally {
        $stream.Dispose()
    }
}

<#
.SYNOPSIS
上传并登记文件 ID，便于末尾按原上传者统一清理。
#>
function Send-TrackedVideoUpload {
    param([string]$Path, [string]$ContentType, [hashtable]$Headers = $h)

    $probe = Send-VideoUpload -Path $Path -ContentType $ContentType -Headers $Headers
    $data = Assert-VideoOk -Label "upload[$([System.IO.Path]::GetFileName($Path))]" -Probe $probe
    $fileId = [long]$data.id
    $uploadedFiles.Add([pscustomobject]@{ Id = $fileId; Headers = $Headers })
    return $fileId
}

<#
.SYNOPSIS
签署当前版本上传声明（同一版本重复签署幂等）。
.PARAMETER Headers
身份请求头。
.PARAMETER Label
断言标签前缀。
#>
function Confirm-VideoDeclaration {
    param([hashtable]$Headers, [string]$Label = '1')

    $current = Send-VideoProbe -Method 'Get' -Path '/video/declaration/current' -Headers $Headers
    $view = Assert-VideoOk -Label "$Label.declarationCurrent" -Probe $current
    $accept = Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' -Headers $Headers `
        -Body @{ version = $view.version; contentSource = 'ORIGINAL' }
    $accepted = Assert-VideoOk -Label "$Label.declarationAccept" -Probe $accept
    Assert "$Label.declarationAccepted" ($accepted.accepted -eq $true -and $accepted.version -eq $view.version) `
        "version=$($accepted.version) accepted=$($accepted.accepted)"
}

<#
.SYNOPSIS
创建一条内容草稿并返回内容 ID。
.PARAMETER Headers
身份请求头。
.PARAMETER Body
保存命令请求体。
.PARAMETER Path
创建路径（/video/user/shorts 或 /video/user/videos）。
#>
function New-VideoContent {
    param([hashtable]$Headers, [object]$Body, [string]$Path = '/video/user/shorts')

    $probe = Send-VideoProbe -Method 'Post' -Path $Path -Headers $Headers -Body $Body
    $id = Assert-VideoOk -Label '1.contentCreate' -Probe $probe
    $createdContents.Add([pscustomobject]@{ Id = [long]$id; Headers = $Headers })
    return [long]$id
}

New-SmokeVideoFile -Path $mp4Path -Length $payloadBytes
New-SmokeVideoFile -Path $webmPath -Length $payloadBytes -WebmMagic
Set-Content -Path $txtPath -Value 'not a video' -Encoding UTF8
$pngBase64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=='
[System.IO.File]::WriteAllBytes($pngPath, [Convert]::FromBase64String($pngBase64))

Write-Host "== M5 安全与上线硬化冒烟 运营=$userId 创作者=3 读者=2 租户=$tenantId =="
Assert-VideoBaseline -Milestone 'm5' -IncludeFile

# 0) 内网接口不经网关不可直接触达（服务侧未暴露 internal 控制器时返回 404）
$internal = Send-VideoProbe -Method 'Get' -Path '/video/internal/meta'
Assert '0.internalNotExposed' ($internal.Status -eq 404) "HTTP $($internal.Status)"

# 1) 造数：签署声明 → 上传 MP4 + PNG 封面 → 建短视频 → 发布
Confirm-VideoDeclaration -Headers $h -Label '1'
Confirm-VideoDeclaration -Headers $ownerHeaders -Label '1.owner'
$mp4FileId = Send-TrackedVideoUpload -Path $mp4Path -ContentType 'video/mp4'
$coverFileId = Send-TrackedVideoUpload -Path $pngPath -ContentType 'image/png'
$shortId = New-VideoContent -Headers $h -Body @{
    title = "M5 短视频-$stamp"; subtitle = 'M5 硬化冒烟'; videoFileId = $mp4FileId;
    coverFileId = $coverFileId; durationMs = 5000; width = 720; height = 1280;
    visibility = 'PUBLIC'; source = 'ORIGINAL'
}
$publish = Send-VideoProbe -Method 'Post' -Path "/video/user/shorts/$shortId/publish" -Headers $h
$published = Assert-VideoOk -Label '1.publish' -Probe $publish
Assert '1.publishedState' ($published.publishState -eq 'PUBLISHED' -and $published.playable -eq $true) `
    "state=$($published.publishState) playable=$($published.playable)"

$detail = Send-VideoProbe -Method 'Get' -Path "/video/content/$shortId" -Headers $readerHeaders
$detailView = Assert-VideoOk -Label '1.detail' -Probe $detail
Assert '1.detailPlayable' ($detailView.playable -eq $true -and $detailView.coverUrl -ne '') `
    "playable=$($detailView.playable) coverUrl=$($detailView.coverUrl)"
Assert-VideoNoFileId -Label '1.detail' -Items @($detailView)

# 2) 上传安全（M5-2）
$webmFileId = Send-TrackedVideoUpload -Path $webmPath -ContentType 'video/mp4'
$mismatch = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Headers $h -Body @{
    title = "M5 伪装格式-$stamp"; videoFileId = $webmFileId; durationMs = 5000; source = 'ORIGINAL'
}
Assert-VideoBizError -Label '2.magicBytesMismatch' -Probe $mismatch -ExpectCode 40003

$txtFileId = Send-TrackedVideoUpload -Path $txtPath -ContentType 'text/plain'
$badMime = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Headers $h -Body @{
    title = "M5 非法视频类型-$stamp"; videoFileId = $txtFileId; durationMs = 5000; source = 'ORIGINAL'
}
Assert-VideoBizError -Label '2.mimeNotAllowed' -Probe $badMime -ExpectCode 40003

$badCover = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Headers $h -Body @{
    title = "M5 非法封面-$stamp"; videoFileId = $mp4FileId; coverFileId = $txtFileId;
    durationMs = 5000; source = 'ORIGINAL'
}
Assert-VideoBizError -Label '2.coverMimeNotAllowed' -Probe $badCover -ExpectCode 40004

$missingFile = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Headers $h -Body @{
    title = "M5 文件不存在-$stamp"; videoFileId = 999999999; durationMs = 5000; source = 'ORIGINAL'
}
Assert-VideoBizError -Label '2.fileMissing' -Probe $missingFile -ExpectCode 40006

$badDuration = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Headers $h -Body @{
    title = "M5 时长越界-$stamp"; videoFileId = $mp4FileId; durationMs = 301000; source = 'ORIGINAL'
}
Assert-VideoBizError -Label '2.durationTooLong' -Probe $badDuration -ExpectCode 40002

# 3) 播放票据（M5-4）
$ticketProbe = Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Headers $h `
    -Body @{ contentId = $shortId }
$ticket = Assert-VideoOk -Label '3.ticketIssue' -Probe $ticketProbe
Assert '3.ticketTtl' ([int]$ticket.expiresIn -eq 300 -and [int]$ticket.refreshIn -eq 240) `
    "expiresIn=$($ticket.expiresIn) refreshIn=$($ticket.refreshIn)"
Assert '3.ticketStreamUrl' ($ticket.streamUrl -eq "/video/stream/$shortId`?ticket=$($ticket.ticket)") `
    "streamUrl=$($ticket.streamUrl)"
Assert-VideoNoFileId -Label '3.ticket' -Items @($ticket)
$streamPath = [string]$ticket.streamUrl

$noTicket = Send-VideoProbe -Method 'Get' -Path "/video/stream/$shortId" -Headers $h
Assert-VideoBizError -Label '3.streamWithoutTicket' -Probe $noTicket -ExpectCode 40302
$otherUserStream = Send-VideoProbe -Method 'Get' -Path $streamPath -Headers $readerHeaders
Assert-VideoBizError -Label '3.streamOtherUser' -Probe $otherUserStream -ExpectCode 40302
$otherContentStream = Send-VideoProbe -Method 'Get' `
    -Path "/video/stream/999999999`?ticket=$($ticket.ticket)" -Headers $h
Assert-VideoBizError -Label '3.streamOtherContent' -Probe $otherContentStream -ExpectCode 40302

# 4) 流式 Range（M5-4 / 验收「50 并发无 5xx、分段正确」）
$rangeProbe = Send-VideoStreamProbe -Path $streamPath -Range 'bytes=0-1023' -Headers $h
Assert '4.rangeStatus' ($rangeProbe.Status -eq 206) "status=$($rangeProbe.Status)"
Assert '4.rangeContentRange' ($rangeProbe.ContentRange -match '^bytes 0-1023/[0-9]+$') `
    "contentRange=$($rangeProbe.ContentRange)"
Assert '4.rangeAcceptRanges' ($rangeProbe.AcceptRanges -eq 'bytes') "acceptRanges=$($rangeProbe.AcceptRanges)"
Assert '4.rangeCacheControl' ($rangeProbe.CacheControl -eq 'private, no-store') `
    "cacheControl=$($rangeProbe.CacheControl)"
Assert '4.rangeNoSniff' ($rangeProbe.NoSniff -eq 'nosniff') "nosniff=$($rangeProbe.NoSniff)"
Assert '4.rangeContentType' ($rangeProbe.ContentType -eq 'video/mp4') "contentType=$($rangeProbe.ContentType)"
Assert '4.rangeBytes' ($rangeProbe.BytesRead -eq 1024) "bytesRead=$($rangeProbe.BytesRead)"

$totalText = ($rangeProbe.ContentRange -split '/')[-1]
$beyondProbe = Send-VideoStreamProbe -Path $streamPath -Range ('bytes={0}-' -f ([long]$totalText + 1024)) -Headers $h
Assert '4.rangeBeyondStatus' ($beyondProbe.Status -eq 416) "status=$($beyondProbe.Status)"
Assert '4.rangeBeyondContentRange' ($beyondProbe.ContentRange -eq "bytes */$totalText") `
    "contentRange=$($beyondProbe.ContentRange)"

# 5) 越权矩阵（M5-1：不可见内容一律 40401，可见非本人 40301）
$privateId = New-VideoContent -Headers $ownerHeaders -Body @{
    title = "M5 创作者私密-$stamp"; videoFileId = $mp4FileId; durationMs = 5000;
    visibility = 'PRIVATE'; source = 'ORIGINAL'
}
$privatePublish = Send-VideoProbe -Method 'Post' -Path "/video/user/shorts/$privateId/publish" -Headers $ownerHeaders
$null = Assert-VideoOk -Label '5.privatePublish' -Probe $privatePublish
$ownerDetail = Send-VideoProbe -Method 'Get' -Path "/video/content/$privateId" -Headers $ownerHeaders
$null = Assert-VideoOk -Label '5.privateDetailOwner' -Probe $ownerDetail
$otherPrivateDetail = Send-VideoProbe -Method 'Get' -Path "/video/content/$privateId" -Headers $readerHeaders
Assert-VideoBizError -Label '5.privateDetailOther' -Probe $otherPrivateDetail -ExpectCode 40401
$otherPrivateOwnerView = Send-VideoProbe -Method 'Get' -Path "/video/user/shorts/$privateId" -Headers $readerHeaders
Assert-VideoBizError -Label '5.privateOwnerViewOther' -Probe $otherPrivateOwnerView -ExpectCode 40401
$otherPrivateUpdate = Send-VideoProbe -Method 'Put' -Path "/video/user/shorts/$privateId" -Headers $readerHeaders `
    -Body @{ title = '越权改名'; videoFileId = $mp4FileId; durationMs = 5000 }
Assert-VideoBizError -Label '5.privateUpdateOther' -Probe $otherPrivateUpdate -ExpectCode 40401
$publicUpdateOther = Send-VideoProbe -Method 'Put' -Path "/video/user/shorts/$shortId" -Headers $readerHeaders `
    -Body @{ title = '越权改名'; videoFileId = $mp4FileId; durationMs = 5000 }
Assert-VideoBizError -Label '5.publicUpdateOther' -Probe $publicUpdateOther -ExpectCode 40301
$publicOwnerViewOther = Send-VideoProbe -Method 'Get' -Path "/video/user/shorts/$shortId" -Headers $readerHeaders
Assert-VideoBizError -Label '5.publicOwnerViewOther' -Probe $publicOwnerViewOther -ExpectCode 40301
$crossTenantDetail = Send-VideoProbe -Method 'Get' -Path "/video/content/$shortId" -Headers $otherTenantHeaders
Assert-VideoBizError -Label '5.crossTenantDetail' -Probe $crossTenantDetail -ExpectCode 40401
$crossTenantTicket = Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Headers $otherTenantHeaders `
    -Body @{ contentId = $shortId }
Assert-VideoBizError -Label '5.crossTenantTicket' -Probe $crossTenantTicket -ExpectCode 40401
$crossTenantCover = Send-VideoProbe -Method 'Get' -Path "/video/content/$shortId/cover" -Headers $otherTenantHeaders
Assert-VideoBizError -Label '5.crossTenantCover' -Probe $crossTenantCover -ExpectCode 40401

# 6) 互动与进度（幂等 + 最近观看）
$like = Send-VideoProbe -Method 'Post' -Path "/video/content/$shortId/like" -Headers $readerHeaders
$null = Assert-VideoOk -Label '6.like' -Probe $like
$likeAgain = Send-VideoProbe -Method 'Post' -Path "/video/content/$shortId/like" -Headers $readerHeaders
$null = Assert-VideoOk -Label '6.likeIdempotent' -Probe $likeAgain
$favorite = Send-VideoProbe -Method 'Post' -Path "/video/content/$shortId/favorite" -Headers $readerHeaders
$null = Assert-VideoOk -Label '6.favorite' -Probe $favorite
$progress = Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Headers $readerHeaders `
    -Body @{ contentId = $shortId; progressMs = 2000; source = 'MY' }
$null = Assert-VideoOk -Label '6.progress' -Probe $progress
$history = Send-VideoProbe -Method 'Get' -Path '/video/history?pageNum=1&pageSize=10' -Headers $readerHeaders
$historyPage = Assert-VideoOk -Label '6.history' -Probe $history
$historyHit = @($historyPage.records | Where-Object { [long]$_.contentId -eq $shortId })
Assert '6.historyContains' ($historyHit.Count -ge 1) "total=$($historyPage.total)"
$delHistory = Send-VideoProbe -Method 'Delete' -Path "/video/history/$shortId" -Headers $readerHeaders
$null = Assert-VideoOk -Label '6.deleteHistory' -Probe $delHistory

# 7) 指标直方图（M5-5）
$null = Send-VideoProbe -Method 'Get' -Path '/video/home' -Headers $h
$metrics = Invoke-WebRequest -Uri ($videoBase + '/actuator/prometheus') -UseBasicParsing
$metricLines = $metrics.Content -split "`n"
foreach ($uriPattern in @('/video/meta', '/video/home', '/video/content/{id}', '/video/stream/{contentId}')) {
    $escaped = [regex]::Escape($uriPattern)
    $buckets = $metricLines | Where-Object {
        $_ -match ('http_server_requests_seconds_bucket\{[^}]*uri="' + $escaped + '"')
    }
    Assert ('7.histogram' + $uriPattern.Replace('/', '_')) ($buckets.Count -gt 0) `
        "uri=$uriPattern bucketLines=$($buckets.Count)"
}

# 8) 可选网关段：内网接口 403、伪造身份头 401
if ($gatewayBase) {
    $internalProbe = Send-VideoProbe -Method 'Get' -Path '/video/internal/meta' -Headers $h -BaseUri $gatewayBase
    Assert '8.gatewayInternalDeny' ($internalProbe.Status -eq 403) "status=$($internalProbe.Status)"
    $forgedProbe = Send-VideoProbe -Method 'Get' -Path '/video/meta' -Headers $otherTenantHeaders -BaseUri $gatewayBase
    Assert '8.gatewayForgedHeader' ($forgedProbe.Status -eq 401) "status=$($forgedProbe.Status) body=$($forgedProbe.BodyText)"
} else {
    Write-Host 'SKIP 8.gateway（未设置 GATEWAY_BASE，网关内网 403 / 伪造头 401 由部署环境验证）'
}

# 9) 清理：内容 → 文件（文件服务只允许上传者本人删除）
foreach ($content in $createdContents) {
    $deleted = Send-VideoProbe -Method 'Delete' -Path "/video/user/shorts/$($content.Id)" -Headers $content.Headers
    Assert "9.deleteContent[$($content.Id)]" ($null -ne $deleted.Result -and [int]$deleted.Result.code -eq 0) `
        "code=$($deleted.Result.code) body=$($deleted.BodyText)"
}
foreach ($file in $uploadedFiles) {
    $delFile = Send-VideoProbe -Method 'Delete' -Path "/file/$($file.Id)" -Headers $file.Headers -BaseUri $fileBase
    Assert "9.deleteFile[$($file.Id)]" ($null -ne $delFile.Result -and
        ([int]$delFile.Result.code -eq 0 -or [int]$delFile.Result.code -eq 404)) `
        "code=$($delFile.Result.code) body=$($delFile.BodyText)"
}

Remove-Item -Path $mp4Path, $webmPath, $txtPath, $pngPath -ErrorAction SilentlyContinue
Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
