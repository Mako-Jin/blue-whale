# Wave 5.75 M5 安全与上线硬化冒烟：
#   上传白名单（音频 / 封面 / 大小 / 文件可用性）→ 版权来源必填与取值校验 →
#   协议签署前置 → 越权矩阵（跨租户 / 自听作品 / 私有歌单 / 评论 / 附件）→
#   播放票据绑定（缺票 / 换用户 / 换歌曲）→ 版权下架与留痕 →
#   版权申诉闭环（提交 / 重复提交 / 越权 / 通过恢复 / 驳回）→ 清理。
# 前置：whale-music(8096)、whale-file(8085)、MySQL、Redis、Nacos 就绪。
# 说明：本机无登录口令，脚本与两个服务同网段直连，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-music-m5-smoke.ps1
#   MUSIC_BASE / FILE_BASE / GATEWAY_BASE 可用环境变量覆盖；
#   MUSIC_UPLOAD_MAX_BYTES 与服务启动值一致时启用「超限 40018」断言（默认跳过）；
#   GATEWAY_BASE 设置时启用「网关内网接口 403」断言。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$musicBase = if ($env:MUSIC_BASE) { $env:MUSIC_BASE } else { 'http://127.0.0.1:8096' }
$fileBase = if ($env:FILE_BASE) { $env:FILE_BASE } else { 'http://127.0.0.1:8085' }
$gatewayBase = if ($env:GATEWAY_BASE) { $env:GATEWAY_BASE } else { '' }
$userId = if ($env:SMOKE_USER_ID) { $env:SMOKE_USER_ID } else { '1' }
$tenantId = if ($env:SMOKE_TENANT_ID) { $env:SMOKE_TENANT_ID } else { '1' }
$uploadLimit = if ($env:MUSIC_UPLOAD_MAX_BYTES) { [long]$env:MUSIC_UPLOAD_MAX_BYTES } else { 0 }
$stamp = Get-Date -Format 'MMddHHmmss'

# 运营视角：文件上传 / 曲库维护 / 审核 / 歌单
$h = @{
    'X-User-Id'          = $userId
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:artist:add,' +
    'sys:music:artist:delete,sys:music:song:add,sys:music:song:edit,sys:music:song:publish,' +
    'sys:music:song:delete,sys:music:song:list,sys:music:browse,sys:music:upload,' +
    'sys:music:comment:create,sys:music:comment:audit,sys:music:playlist:own,sys:music:like,' +
    'sys:music:appeal:audit'
}
# 创作者（用户 3）：上传 + 浏览 + 歌单 + 评论
$creatorHeaders = @{
    'X-User-Id'          = '3'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:upload,sys:music:browse,' +
    'sys:music:playlist:own,sys:music:comment:create,sys:music:like'
}
# 普通读者（用户 2）：浏览 + 歌单 + 评论
$readerHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:music:browse,sys:music:playlist:own,sys:music:comment:create,' +
    'sys:music:like,sys:music:upload'
}
# 跨租户视角（用户 2 / 租户 2）
$otherTenantHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = '2'
    'X-Permission-Codes' = 'sys:music:browse,sys:music:playlist:own,sys:music:comment:create,sys:music:like'
}

$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromMinutes(5)
$testAudioBytes = 4096
$oversizeBytes = 16384
$audioPath = Join-Path $env:TEMP ('bw-music-m5-' + $stamp + '.mp3')
$wavPath = Join-Path $env:TEMP ('bw-music-m5-' + $stamp + '.wav')
$txtPath = Join-Path $env:TEMP ('bw-music-m5-' + $stamp + '.txt')
$bigPath = Join-Path $env:TEMP ('bw-music-m5-big-' + $stamp + '.mp3')
$uploadedFiles = New-Object System.Collections.Generic.List[object]
$createdSongs = New-Object System.Collections.Generic.List[long]

<#
.SYNOPSIS
生成指定长度的静音音频字节。
说明：Wave 5.75 M5-2 起音乐服务按 MIME 白名单校验（audio/mpeg / audio/aac / audio/flac），
     脚本以 .mp3 + audio/mpeg 上传；WAV / 文本文件用于「非法类型」断言。
#>
function New-SmokeAudio {
    param([string]$Path, [int]$Length)

    [byte[]]$wav = New-Object byte[] $Length
    if ($Length -gt 44) {
        [System.Text.Encoding]::ASCII.GetBytes('RIFF').CopyTo($wav, 0)
        [BitConverter]::GetBytes([int]($Length - 8)).CopyTo($wav, 4)
        [System.Text.Encoding]::ASCII.GetBytes('WAVEfmt ').CopyTo($wav, 8)
        [BitConverter]::GetBytes([int]16).CopyTo($wav, 16)
        [BitConverter]::GetBytes([int16]1).CopyTo($wav, 20)
        [BitConverter]::GetBytes([int16]1).CopyTo($wav, 22)
        [BitConverter]::GetBytes([int]8000).CopyTo($wav, 24)
        [BitConverter]::GetBytes([int]8000).CopyTo($wav, 28)
        [BitConverter]::GetBytes([int16]1).CopyTo($wav, 32)
        [BitConverter]::GetBytes([int16]8).CopyTo($wav, 34)
        [System.Text.Encoding]::ASCII.GetBytes('data').CopyTo($wav, 36)
        [BitConverter]::GetBytes([int]($Length - 44)).CopyTo($wav, 40)
    }
    [System.IO.File]::WriteAllBytes($Path, $wav)
}

<#
.SYNOPSIS
以 multipart 方式上传文件，返回 Result 信封。
#>
function Send-Upload {
    param([string]$Path, [string]$ContentType, [hashtable]$Headers = $h)

    $stream = New-Object System.IO.FileStream($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add($fileContent, 'file', [System.IO.Path]::GetFileName($Path))
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$fileBase/file/v1/upload")
        $request.Content = $form
        foreach ($key in $Headers.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
        }
        $response = $http.SendAsync($request).Result
        return $response.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    } finally {
        $stream.Dispose()
    }
}

<#
.SYNOPSIS
上传并登记文件 ID，便于末尾统一清理。
#>
function Send-TrackedUpload {
    param([string]$Path, [string]$ContentType, [hashtable]$Headers = $h)

    $result = Send-Upload -Path $Path -ContentType $ContentType -Headers $Headers
    Check "upload[$([System.IO.Path]::GetFileName($Path))]" $result
    $fileId = [long]$result.data.id
    # 记录上传身份，清理时按原上传者删除（文件服务只允许本人删除）
    $uploadedFiles.Add([pscustomobject]@{ Id = $fileId; Headers = $Headers })
    return $fileId
}

<#
.SYNOPSIS
调用音乐服务 JSON 接口，返回 Result 信封。
#>
function Invoke-MusicApi {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = $null)
    if (-not $Headers) {
        $Headers = $h
    }
    return Invoke-Api -Method $Method -Uri ($musicBase + $Path) -Body $Body -Headers $Headers
}

<#
.SYNOPSIS
调用接口并返回 HTTP 状态码、响应体与内容类型（用于断言 403 / 429 / 二进制流）。
#>
function Send-Probe {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = $null,
          [hashtable]$ExtraHeaders = $null, [string]$Base = $musicBase)

    if (-not $Headers) {
        $Headers = $h
    }
    $verb = [System.Net.Http.HttpMethod]::Get
    if ($Method -eq 'Post') { $verb = [System.Net.Http.HttpMethod]::Post }
    if ($Method -eq 'Put') { $verb = [System.Net.Http.HttpMethod]::Put }
    if ($Method -eq 'Delete') { $verb = [System.Net.Http.HttpMethod]::Delete }
    $request = New-Object System.Net.Http.HttpRequestMessage($verb, ($Base + $Path))
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Content = New-Object System.Net.Http.StringContent(
            $json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    foreach ($key in $Headers.Keys) {
        $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
    }
    if ($ExtraHeaders) {
        foreach ($key in $ExtraHeaders.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $ExtraHeaders[$key])
        }
    }
    $response = $http.SendAsync($request).Result
    $body = $response.Content.ReadAsStringAsync().Result
    $contentType = ''
    if ($response.Content.Headers.ContentType) {
        $contentType = $response.Content.Headers.ContentType.MediaType
    }
    return [pscustomobject]@{
        Status      = [int]$response.StatusCode
        BodyText    = $body
        ContentType = $contentType
    }
}

<#
.SYNOPSIS
断言 Result 信封中的业务错误码（HTTP 统一 200 + body.code）。
#>
function Assert-Code {
    param([string]$Label, $Result, [int]$Expect)

    if ($null -eq $Result.code) {
        throw "ASSERT FAIL: $Label 未返回 Result 信封"
    }
    Assert $Label ([int]$Result.code -eq $Expect) "code=$($Result.code) expect=$Expect message=$($Result.message)"
}

<#
.SYNOPSIS
创建并上架一首运营歌曲（默认公开），返回歌曲 ID。
#>
function New-PublishedSong {
    param([long]$ArtistId, [long]$AudioFileId, [string]$Title, [string]$Source = $null,
          [string]$Visibility = 'PUBLIC', [hashtable]$Headers = $h)

    $body = @{
        artistId    = $ArtistId
        title       = $Title
        subtitle    = 'M5 硬化冒烟'
        audioFileId = $AudioFileId
        durationMs  = 30000
        visibility  = $Visibility
        language    = 'ZH'
    }
    if ($Source) { $body['source'] = $Source }
    $song = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body $body -Headers $Headers
    Check "songCreate[$Title]" $song
    $createdSongs.Add([long]$song.data.id)
    if ($Headers -eq $h) {
        $published = Invoke-MusicApi -Method 'Post' -Path "/music/song/$($song.data.id)/publish"
        Check "songPublish[$Title]" $published
    }
    return [long]$song.data.id
}

New-SmokeAudio -Path $audioPath -Length $testAudioBytes
New-SmokeAudio -Path $bigPath -Length $oversizeBytes
New-SmokeAudio -Path $wavPath -Length $testAudioBytes
Set-Content -Path $txtPath -Value 'not an image' -Encoding UTF8
Write-Host "== M5 安全硬化冒烟 运营=$userId 创作者=3 读者=2 租户=$tenantId =="

# 0) 两个服务就绪
foreach ($pair in @(@{ n = 'music'; u = "$musicBase/actuator/health" },
                    @{ n = 'file'; u = "$fileBase/actuator/health" })) {
    try {
        $health = Invoke-RestMethod -Uri $pair.u -TimeoutSec 5
        Assert "0.health[$($pair.n)]" ($health.status -eq 'UP')
    } catch {
        Assert "0.health[$($pair.n)]" $false $_.Exception.Message
    }
}

# 1) 运营闭环：上传音频 → 建歌手 → 建歌（source=ORIGINAL）→ 上架
$audioFileId = Send-TrackedUpload -Path $audioPath -ContentType 'audio/mpeg'
$artist = Invoke-MusicApi -Method 'Post' -Path '/music/artist' -Body @{ name = "M5冒烟歌手-$stamp"; region = 'CN' }
Check '1.artistCreate' $artist
$artistId = [long]$artist.data.id
$adminSongId = New-PublishedSong -ArtistId $artistId -AudioFileId $audioFileId `
    -Title "M5运营歌曲-$stamp" -Source 'ORIGINAL'
$adminDetail = Invoke-MusicApi -Method 'Get' -Path "/music/song/$adminSongId"
Check '1.adminDetail' $adminDetail
Assert '1.sourcePersisted' ("$($adminDetail.data.source)" -eq 'ORIGINAL') "source=$($adminDetail.data.source)"
$publicDetail = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$adminSongId" -Headers $readerHeaders
Check '1.publicDetail' $publicDetail
Assert '1.noAudioFileId' ($null -eq $publicDetail.data.audioFileId -and $null -eq $publicDetail.data.lyricFileId) `
    "audioFileId=$($publicDetail.data.audioFileId) lyricFileId=$($publicDetail.data.lyricFileId)"

# 2) 上传白名单：非法音频 / 非法封面 / 文件不存在 / 超限（可选）
$wavFileId = Send-TrackedUpload -Path $wavPath -ContentType 'audio/wav'
$wavSong = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body @{
    artistId = $artistId; title = "M5非法音频-$stamp"; audioFileId = $wavFileId; durationMs = 30000
}
Assert-Code '2.audioFormatRejected' $wavSong 40016
$txtFileId = Send-TrackedUpload -Path $txtPath -ContentType 'text/plain'
$coverSong = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body @{
    artistId = $artistId; title = "M5非法封面-$stamp"; audioFileId = $audioFileId;
    coverFileId = $txtFileId; durationMs = 30000
}
Assert-Code '2.coverFormatRejected' $coverSong 40017
$missingFileSong = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body @{
    artistId = $artistId; title = "M5文件不存在-$stamp"; audioFileId = 999999999; durationMs = 30000
}
Assert-Code '2.fileMissingRejected' $missingFileSong 40019
if ($uploadLimit -gt 0 -and $uploadLimit -lt $oversizeBytes) {
    $bigFileId = Send-TrackedUpload -Path $bigPath -ContentType 'audio/mpeg'
    $bigSong = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body @{
        artistId = $artistId; title = "M5超限-$stamp"; audioFileId = $bigFileId; durationMs = 30000
    }
    Assert-Code '2.uploadTooLarge' $bigSong 40018
} else {
    Write-Host "SKIP 2.uploadTooLarge（MUSIC_UPLOAD_MAX_BYTES 未设置或不小于 $oversizeBytes）"
}

# 3) 版权合规：协议签署前置 + 来源必填 / 取值合法
#    协议前置用独立账号（用户 4）验证，避免与创作者账号的历史签署状态耦合（脚本可重复执行）
$gateHeaders = @{
    'X-User-Id'          = '4'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:upload,sys:music:browse'
}
$agreement = Invoke-MusicApi -Method 'Get' -Path '/music/copyright/agreement/current' -Headers $gateHeaders
Check '3.agreementCurrent' $agreement
if ($agreement.data.accepted -eq $false) {
    $gateAudioId = Send-TrackedUpload -Path $audioPath -ContentType 'audio/mpeg' -Headers $gateHeaders
    $noAgreement = Invoke-MusicApi -Method 'Post' -Path '/music/user/song' -Headers $gateHeaders -Body @{
        artistId = $artistId; title = "M5未签协议-$stamp"; audioFileId = $gateAudioId; durationMs = 30000;
        source = 'ORIGINAL'
    }
    Assert-Code '3.agreementRequired' $noAgreement 40006
} else {
    Write-Host 'SKIP 3.agreementRequired（用户 4 历史上已签署，本轮只验证签署通过路径）'
}
$accepted = Invoke-MusicApi -Method 'Post' -Path '/music/copyright/agreement/accept' -Headers $gateHeaders `
    -Body @{ agreementVersion = $agreement.data.agreementVersion; agreed = $true }
Check '3.agreementAccept' $accepted
Assert '3.agreementAccepted' ($accepted.data.accepted -eq $true -and $null -ne $accepted.data.acceptedAt -and
    $accepted.data.agreementVersion -eq $agreement.data.agreementVersion) `
    "version=$($accepted.data.agreementVersion) acceptedAt=$($accepted.data.acceptedAt)"
$creatorAgreement = Invoke-MusicApi -Method 'Get' -Path '/music/copyright/agreement/current' -Headers $creatorHeaders
Check '3.creatorAgreementCurrent' $creatorAgreement
if ($creatorAgreement.data.accepted -eq $false) {
    $creatorAccepted = Invoke-MusicApi -Method 'Post' -Path '/music/copyright/agreement/accept' -Headers $creatorHeaders `
        -Body @{ agreementVersion = $creatorAgreement.data.agreementVersion; agreed = $true }
    Check '3.creatorAgreementAccept' $creatorAccepted
}
$creatorAudioId = Send-TrackedUpload -Path $audioPath -ContentType 'audio/mpeg' -Headers $creatorHeaders
$withoutSource = Invoke-MusicApi -Method 'Post' -Path '/music/user/song' -Headers $creatorHeaders -Body @{
    artistId = $artistId; title = "M5缺来源-$stamp"; audioFileId = $creatorAudioId; durationMs = 30000
}
Assert-Code '3.sourceRequired' $withoutSource 40020
$badSource = Invoke-MusicApi -Method 'Post' -Path '/music/user/song' -Headers $creatorHeaders -Body @{
    artistId = $artistId; title = "M5来源非法-$stamp"; audioFileId = $creatorAudioId; durationMs = 30000;
    source = 'BOUGHT'
}
Assert-Code '3.sourceInvalid' $badSource 40021
$creatorAudio2 = Send-TrackedUpload -Path $audioPath -ContentType 'audio/mpeg' -Headers $creatorHeaders
$creatorSong = Invoke-MusicApi -Method 'Post' -Path '/music/user/song' -Headers $creatorHeaders -Body @{
    artistId = $artistId; title = "M5创作者公开-$stamp"; audioFileId = $creatorAudio2; durationMs = 30000;
    visibility = 'PUBLIC'; source = 'AUTHORIZED'
}
Check '3.creatorUpload' $creatorSong
$creatorSongId = [long]$creatorSong.data.id
$createdSongs.Add($creatorSongId)
Assert '3.creatorPublished' ($creatorSong.data.playable -eq $true -and $null -ne $creatorSong.data.publishedAt -and
    $creatorSong.data.visibility -eq 'PUBLIC') `
    "visibility=$($creatorSong.data.visibility) playable=$($creatorSong.data.playable) publishedAt=$($creatorSong.data.publishedAt)"
$creatorAudio3 = Send-TrackedUpload -Path $audioPath -ContentType 'audio/mpeg' -Headers $creatorHeaders
$privateSong = Invoke-MusicApi -Method 'Post' -Path '/music/user/song' -Headers $creatorHeaders -Body @{
    artistId = $artistId; title = "M5创作者自听-$stamp"; audioFileId = $creatorAudio3; durationMs = 30000;
    visibility = 'PRIVATE'; source = 'ROYALTY_FREE'
}
Check '3.privateUpload' $privateSong
$privateSongId = [long]$privateSong.data.id
$createdSongs.Add($privateSongId)

# 4) 越权矩阵：跨租户 / 自听作品 / 私有歌单 / 评论 / 作品编辑
$selfDetail = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$privateSongId" -Headers $creatorHeaders
Check '4.privateDetailOwner' $selfDetail
$otherPrivate = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$privateSongId" -Headers $readerHeaders
Assert-Code '4.privateDetailOther' $otherPrivate 40403
$otherComment = Invoke-MusicApi -Method 'Post' -Path "/music/song/$privateSongId/comment" `
    -Headers $readerHeaders -Body @{ content = '越权评论'; parentId = 0 }
Assert-Code '4.privateCommentOther' $otherComment 40403
$otherUpdate = Invoke-MusicApi -Method 'Put' -Path "/music/user/song/$privateSongId" `
    -Headers $readerHeaders -Body @{ artistId = $artistId; title = '越权改名'; audioFileId = $creatorAudio3; durationMs = 30000 }
Assert-Code '4.privateUpdateOther' $otherUpdate 40403
$publicUpdateOther = Invoke-MusicApi -Method 'Put' -Path "/music/user/song/$creatorSongId" `
    -Headers $readerHeaders -Body @{ artistId = $artistId; title = '越权改名'; audioFileId = $creatorAudio2; durationMs = 30000 }
Assert-Code '4.publicUpdateOther' $publicUpdateOther 40301
$crossTenantDetail = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$creatorSongId" -Headers $otherTenantHeaders
Assert-Code '4.crossTenantDetail' $crossTenantDetail 40403
$crossTenantTicket = Invoke-MusicApi -Method 'Post' -Path '/music/play/ticket' -Headers $otherTenantHeaders `
    -Body @{ songId = $creatorSongId }
Assert-Code '4.crossTenantTicket' $crossTenantTicket 40403
$crossTenantLike = Invoke-MusicApi -Method 'Post' -Path "/music/song/$creatorSongId/like" -Headers $otherTenantHeaders
Assert-Code '4.crossTenantLike' $crossTenantLike 40403

$playlist = Invoke-MusicApi -Method 'Post' -Path '/music/playlist' -Headers $creatorHeaders `
    -Body @{ title = "M5私有歌单-$stamp"; description = 'M5 越权冒烟'; visibility = 'PRIVATE' }
Check '4.playlistCreate' $playlist
$playlistId = [long]$playlist.data.id
$ownerPlaylist = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/$playlistId" -Headers $creatorHeaders
Check '4.playlistOwnerDetail' $ownerPlaylist
$otherPlaylist = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/$playlistId" -Headers $readerHeaders
Assert-Code '4.privatePlaylistDetail' $otherPlaylist 40405
$otherPlaylistUpdate = Invoke-MusicApi -Method 'Put' -Path "/music/playlist/$playlistId" -Headers $readerHeaders `
    -Body @{ title = '越权改名'; description = '越权' }
Assert-Code '4.privatePlaylistUpdate' $otherPlaylistUpdate 40405
$otherPlaylistAdd = Invoke-MusicApi -Method 'Post' -Path "/music/playlist/$playlistId/song" -Headers $readerHeaders `
    -Body @{ songId = $creatorSongId }
Assert-Code '4.privatePlaylistAddSong' $otherPlaylistAdd 40405

if ($gatewayBase) {
    $internalProbe = Send-Probe -Method 'Get' -Path "/file/v1/internal/$audioFileId" -Base $gatewayBase
    Assert '4.internalDeny' ($internalProbe.Status -eq 403) "status=$($internalProbe.Status)"
} else {
    Write-Host 'SKIP 4.internalDeny（未设置 GATEWAY_BASE，/file/v1/internal 由网关注入 403）'
}

# 5) 播放票据：绑定租户 / 用户 / 歌曲，流式每次复核可见性与版权状态
$ticket = Invoke-MusicApi -Method 'Post' -Path '/music/play/ticket' -Headers $creatorHeaders `
    -Body @{ songId = $creatorSongId }
Check '5.ticketIssue' $ticket
$ticketValue = $ticket.data.ticket
Assert '5.ticketStreamUrl' ($ticket.data.streamUrl -match "ticket=$ticketValue") "streamUrl=$($ticket.data.streamUrl)"
$noTicket = Invoke-MusicApi -Method 'Get' -Path "/music/stream/$creatorSongId" -Headers $creatorHeaders
Assert-Code '5.streamWithoutTicket' $noTicket 40302
$otherUserStream = Invoke-MusicApi -Method 'Get' -Path "/music/stream/$creatorSongId`?ticket=$ticketValue" -Headers $readerHeaders
Assert-Code '5.streamOtherUser' $otherUserStream 40302
$otherSongStream = Invoke-MusicApi -Method 'Get' -Path "/music/stream/$adminSongId`?ticket=$ticketValue" -Headers $creatorHeaders
Assert-Code '5.streamOtherSong' $otherSongStream 40302
$stream = Send-Probe -Method 'Get' -Path "/music/stream/$creatorSongId`?ticket=$ticketValue" -Headers $creatorHeaders
Assert '5.streamOk' ($stream.Status -eq 200 -and $stream.ContentType -eq 'audio/mpeg') `
    "status=$($stream.Status) contentType=$($stream.ContentType)"
$rangeStream = Send-Probe -Method 'Get' -Path "/music/stream/$creatorSongId`?ticket=$ticketValue" `
    -Headers $creatorHeaders -ExtraHeaders @{ Range = 'bytes=0-1023' }
Assert '5.streamRange' ($rangeStream.Status -eq 206) "status=$($rangeStream.Status)"

# 6) 版权下架：原因必填、下架后不可见 / 不可播放 / 已签发票据失效
$adminTicket = Invoke-MusicApi -Method 'Post' -Path '/music/play/ticket' -Body @{ songId = $adminSongId }
Check '6.adminTicketIssue' $adminTicket
$emptyReason = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/takedown" `
    -Body @{ reason = '   ' }
# 空白原因先被入参校验拦下（code=400），领域规则另有 40005（SongTest/SongAppServiceImplTest 覆盖）
Assert '6.takedownReasonRequired' (([int]$emptyReason.code -eq 40005 -or [int]$emptyReason.code -eq 400) -and
    $null -eq $emptyReason.data) "code=$($emptyReason.code) message=$($emptyReason.message)"
# 下架原因留痕：Windows PowerShell 5.1 的 Invoke-RestMethod 对无 charset 的 JSON 响应按本地码页解码，
# 中文断言会误判；此处用 ASCII 原因验证「写入即回读一致」，中文留痕由后端单测覆盖
$takedownReason = "M5 takedown: no authorization proof $stamp"
$takedown = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/takedown" `
    -Body @{ reason = $takedownReason }
Check '6.takedown' $takedown
Assert '6.takedownState' ($takedown.data.copyrightState -eq 'TAKEDOWN' -and
    $takedown.data.publishState -eq 'OFFLINE') "state=$($takedown.data.copyrightState)/$($takedown.data.publishState)"
Assert '6.takedownReasonTrace' ($takedown.data.takedownReason -eq $takedownReason) `
    "reason=$($takedown.data.takedownReason)"
$afterTakedownDetail = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$adminSongId" -Headers $readerHeaders
Assert-Code '6.takedownInvisible' $afterTakedownDetail 40403
$afterTakedownTicket = Invoke-MusicApi -Method 'Post' -Path '/music/play/ticket' -Headers $readerHeaders `
    -Body @{ songId = $adminSongId }
Assert-Code '6.takedownTicketDenied' $afterTakedownTicket 40403
$expiredTicketStream = Invoke-MusicApi -Method 'Get' `
    -Path "/music/stream/$adminSongId`?ticket=$($adminTicket.data.ticket)" -Headers $h
Assert-Code '6.takedownStreamDenied' $expiredTicketStream 40403

# 6.1) 版权申诉（M5-3）：本人提交 → 重复提交拒绝 → 非本人不泄露 → 运营分页 → 通过 → 版权恢复且保持下架
$appealReason = "M5 appeal: original work, please restore $stamp"
$appeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/appeal" -Headers $h `
    -Body @{ reason = $appealReason }
Check '6.1.appealSubmit' $appeal
$appealId = [long]$appeal.data.id
Assert '6.1.appealPending' ($appeal.data.status -eq 'PENDING' -and $appeal.data.applicantId -eq [long]$userId) `
    "status=$($appeal.data.status) applicant=[$($appeal.data.applicantId)] expect=[$userId]"
$dupAppeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/appeal" -Headers $h `
    -Body @{ reason = $appealReason }
Assert-Code '6.1.appealDuplicate' $dupAppeal 40902
# 非上传者提交：下架作品对他人不可见，按不存在处理（不泄露存在性）
$otherAppeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/appeal" -Headers $readerHeaders `
    -Body @{ reason = 'other user appeal' }
Assert-Code '6.1.appealNotOwner' $otherAppeal 40403
# 上传者对未下架作品提交：状态不允许（40903）
$notAllowedAppeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$creatorSongId/appeal" -Headers $creatorHeaders `
    -Body @{ reason = 'song is not taken down' }
Assert-Code '6.1.appealNotAllowed' $notAllowedAppeal 40903
# 理由为空：40022
$blankAppeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/appeal" -Headers $h `
    -Body @{ reason = '   ' }
Assert-Code '6.1.appealReasonRequired' $blankAppeal 40022

$appealPage = Invoke-MusicApi -Method 'Get' -Path "/music/appeal/page?status=PENDING&songId=$adminSongId&current=1&size=10"
Check '6.1.appealPagePending' $appealPage
Assert '6.1.appealPageContains' (@($appealPage.data.records | Where-Object { [long]$_.id -eq $appealId }).Count -ge 1) `
    "total=$($appealPage.data.total) records=$(@($appealPage.data.records).Count)"

$approve = Invoke-MusicApi -Method 'Post' -Path "/music/appeal/$appealId/approve" -Headers $h `
    -Body @{ opinion = 'approved: original work' }
Check '6.1.appealApprove' $approve
Assert '6.1.appealApproved' ($approve.data.status -eq 'APPROVED' -and $null -ne $approve.data.reviewedAt -and
    $approve.data.reviewerId -eq [long]$userId) `
    "status=$($approve.data.status) reviewer=$($approve.data.reviewerId) reviewedAt=$($approve.data.reviewedAt)"
$restored = Invoke-MusicApi -Method 'Get' -Path "/music/song/$adminSongId"
Check '6.1.restoredDetail' $restored
Assert '6.1.restoredCopyright' ($restored.data.copyrightState -eq 'VALID' -and
    $restored.data.publishState -eq 'OFFLINE' -and $null -eq $restored.data.takedownReason -and
    $restored.data.playable -eq $false) `
    "copyright=$($restored.data.copyrightState) publish=$($restored.data.publishState) playable=$($restored.data.playable)"
$restoredPublic = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$adminSongId" -Headers $readerHeaders
Assert-Code '6.1.restoredStillOffline' $restoredPublic 40403
$secondApprove = Invoke-MusicApi -Method 'Post' -Path "/music/appeal/$appealId/approve" -Headers $h `
    -Body @{ opinion = '再次复核' }
Assert-Code '6.1.appealReviewedOnce' $secondApprove 40902
$republish = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/publish"
Check '6.1.republish' $republish
Assert '6.1.republishPlayable' ($republish.data.publishState -eq 'PUBLISHED' -and $republish.data.playable -eq $true) `
    "publish=$($republish.data.publishState) playable=$($republish.data.playable)"

# 6.2) 申诉驳回：歌曲保持版权下架且不可播
$takedownAgain = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/takedown" `
    -Body @{ reason = "M5 reject path $stamp" }
Check '6.2.takedownAgain' $takedownAgain
$rejectAppeal = Invoke-MusicApi -Method 'Post' -Path "/music/song/$adminSongId/appeal" -Headers $h `
    -Body @{ reason = 'second appeal for reject path' }
Check '6.2.appealSubmit' $rejectAppeal
$rejectId = [long]$rejectAppeal.data.id
$reject = Invoke-MusicApi -Method 'Post' -Path "/music/appeal/$rejectId/reject" -Headers $h `
    -Body @{ opinion = 'rejected: insufficient proof' }
Check '6.2.appealReject' $reject
Assert '6.2.appealRejected' ($reject.data.status -eq 'REJECTED' -and $null -ne $reject.data.reviewedAt) `
    "status=$($reject.data.status)"
$afterReject = Invoke-MusicApi -Method 'Get' -Path "/music/song/$adminSongId"
Check '6.2.afterRejectDetail' $afterReject
Assert '6.2.afterRejectState' ($afterReject.data.copyrightState -eq 'TAKEDOWN' -and
    $afterReject.data.publishState -eq 'OFFLINE' -and $afterReject.data.playable -eq $false) `
    "copyright=$($afterReject.data.copyrightState) publish=$($afterReject.data.publishState)"

# 7) 清理：歌单 / 歌曲 / 歌手 / 文件
$delPlaylist = Invoke-MusicApi -Method 'Delete' -Path "/music/playlist/$playlistId" -Headers $creatorHeaders
Check '7.deletePlaylist' $delPlaylist
foreach ($songId in $createdSongs) {
    $deleted = Invoke-MusicApi -Method 'Delete' -Path "/music/song/$songId"
    Check "7.deleteSong[$songId]" $deleted
}
$delArtist = Invoke-MusicApi -Method 'Delete' -Path "/music/artist/$artistId"
Check '7.deleteArtist' $delArtist
foreach ($file in $uploadedFiles) {
    $delFile = Invoke-Api -Method 'Delete' -Uri "$fileBase/file/v1/$($file.Id)" -Headers $file.Headers
    Assert "7.deleteFile[$($file.Id)]" ($delFile.code -eq 0 -or $delFile.code -eq 404) "code=$($delFile.code)"
}

Remove-Item -Path $audioPath, $wavPath, $txtPath, $bigPath -ErrorAction SilentlyContinue
Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
