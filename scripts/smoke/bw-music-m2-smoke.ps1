# Wave 5.75 M2 播放与音频流冒烟：
#   上传测试音频 → 建歌手 / 歌曲 → 上架 → 申请播放票据 → Range 200 / 206 / 416 →
#   票据绑定校验（无票据 / 换歌 / 换用户）→ 进度与收藏幂等 → 版权下架后拒绝播放。
# 前置：whale-music(8096)、whale-file(8085)、MySQL、Redis、Nacos 就绪（whale-file 已建库）。
# 说明：本机无登录口令，脚本与两个服务同网段直连，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-music-m2-smoke.ps1
#   MUSIC_BASE / FILE_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID 可用环境变量覆盖。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$musicBase = if ($env:MUSIC_BASE) { $env:MUSIC_BASE } else { 'http://127.0.0.1:8096' }
$fileBase = if ($env:FILE_BASE) { $env:FILE_BASE } else { 'http://127.0.0.1:8085' }
$userId = if ($env:SMOKE_USER_ID) { $env:SMOKE_USER_ID } else { '1' }
$tenantId = if ($env:SMOKE_TENANT_ID) { $env:SMOKE_TENANT_ID } else { '1' }
$stamp = Get-Date -Format 'MMddHHmmss'

# 覆盖 M2 全部接口所需权限码（与迁移脚本 seed 的 sys:music:* / sys:file:* 同口径）
$h = @{
    'X-User-Id'         = $userId
    'X-Tenant-Id'       = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:artist:add,' +
    'sys:music:artist:delete,sys:music:song:add,sys:music:song:publish,' +
    'sys:music:song:delete,sys:music:song:list,sys:music:browse,sys:music:like'
}

$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromMinutes(5)
$testAudioBytes = 4096
$audioPath = Join-Path $env:TEMP ('bw-music-m2-' + $stamp + '.mp3')

<#
.SYNOPSIS
生成指定长度的静音音频字节，用于 Range 分段断言。
说明：Wave 5.75 M5-2 起音乐服务按 MIME 白名单校验（audio/mpeg / audio/aac / audio/flac），
     脚本以 .mp3 + audio/mpeg 上传，文件内容本身仅用于二进制约分段校验。
#>
function New-SmokeAudio {
    param([string]$Path, [int]$Length)

    [byte[]]$wav = New-Object byte[] $Length
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
    [System.IO.File]::WriteAllBytes($Path, $wav)
}

<#
.SYNOPSIS
以 multipart 方式上传文件，返回 Result 信封。
#>
function Send-Upload {
    param([string]$Uri, [string]$Path, [string]$ContentType)

    $stream = New-Object System.IO.FileStream($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse($ContentType)
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add($fileContent, 'file', [System.IO.Path]::GetFileName($Path))
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, $Uri)
        $request.Content = $form
        foreach ($key in $h.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $h[$key])
        }
        $response = $http.SendAsync($request).Result
        return $response.Content.ReadAsStringAsync().Result | ConvertFrom-Json
    } finally {
        $stream.Dispose()
    }
}

<#
.SYNOPSIS
发起流式 GET（可带 Range），返回状态码、关键响应头与响应体。
#>
function Send-StreamProbe {
    param([string]$Uri, [string]$Range)

    $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, $Uri)
    $headers = @{}
    foreach ($key in $h.Keys) {
        $headers[$key] = $h[$key]
    }
    foreach ($key in $headers.Keys) {
        $null = $request.Headers.TryAddWithoutValidation($key, $headers[$key])
    }
    if ($Range) {
        $null = $request.Headers.TryAddWithoutValidation('Range', $Range)
    }
    $response = $http.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
    $bytes = $response.Content.ReadAsByteArrayAsync().Result
    # 响应头与内容头合并成小写名映射，避免依赖各 .NET 版本的头属性位置
    $responseHeaders = @{}
    foreach ($item in $response.Headers.GetEnumerator()) {
        $responseHeaders[$item.Key.ToLowerInvariant()] = ($item.Value -join ',')
    }
    foreach ($item in $response.Content.Headers.GetEnumerator()) {
        $responseHeaders[$item.Key.ToLowerInvariant()] = ($item.Value -join ',')
    }
    return [pscustomobject]@{
        Status       = [int]$response.StatusCode
        ContentRange = $responseHeaders['content-range']
        ContentLength = $responseHeaders['content-length']
        AcceptRanges = $responseHeaders['accept-ranges']
        CacheControl = $responseHeaders['cache-control']
        ETag         = $responseHeaders['etag']
        BodyLength   = $bytes.Length
        BodyText     = [System.Text.Encoding]::UTF8.GetString($bytes)
    }
}

<#
.SYNOPSIS
调用音乐服务 JSON 接口（GET / POST / DELETE），返回 Result 信封。
#>
function Invoke-MusicApi {
    param([string]$Method, [string]$Path, [object]$Body = $null)
    return Invoke-Api -Method $Method -Uri ($musicBase + $Path) -Body $Body -Headers $h
}

New-SmokeAudio -Path $audioPath -Length $testAudioBytes
Write-Host "== M2 播放与音频流冒烟 用户=$userId 租户=$tenantId =="

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

# 1) 上传测试音频（M2 流式内容的真实来源）
$upload = Send-Upload -Uri "$fileBase/file/v1/upload" -Path $audioPath -ContentType 'audio/mpeg'
Check '1.upload' $upload
$fileId = [long]$upload.data.id
Assert '1.uploadSize' ([long]$upload.data.fileSize -eq $testAudioBytes) "size=$($upload.data.fileSize)"

# 2) 建歌手
$artist = Invoke-MusicApi -Method 'Post' -Path '/music/v1/artist' -Body @{ name = "M2冒烟歌手-$stamp"; region = 'CN' }
Check '2.artistCreate' $artist
$artistId = [long]$artist.data.id
Assert '2.artistId' ($artistId -gt 0) "id=$artistId"

# 3) 建歌曲（草稿）→ 上架
$songBody = @{
    artistId    = $artistId
    title       = "M2冒烟歌曲-$stamp"
    subtitle    = 'Range 与票据冒烟'
    audioFileId = $fileId
    durationMs  = 30000
    visibility  = 'PUBLIC'
    language    = 'ZH'
    lyricText   = "[00:00.00]M2 冒烟测试`n[00:10.00]第二行歌词"
}
$song = Invoke-MusicApi -Method 'Post' -Path '/music/v1/song' -Body $songBody
Check '3.songCreate' $song
$songId = [long]$song.data.id
Assert '3.songDraft' ($song.data.publishState -eq 'DRAFT' -and -not $song.data.playable) `
    "publishState=$($song.data.publishState)"

$published = Invoke-MusicApi -Method 'Post' -Path "/music/v1/song/$songId/publish"
Check '3.songPublish' $published
Assert '3.songPlayable' ($published.data.publishState -eq 'PUBLISHED' -and $published.data.playable)

# 4) 申请播放票据（绑定 tenantId + userId + songId，TTL 120 秒）
$ticket = Invoke-MusicApi -Method 'Post' -Path '/music/v1/play/ticket' -Body @{ songId = $songId }
Check '4.ticket' $ticket
$ticketValue = [string]$ticket.data.ticket
$expiresIn = [long]$ticket.data.expiresIn
Assert '4.ticketShape' ($ticketValue.Length -eq 32 -and $expiresIn -eq 120) "len=$($ticketValue.Length) ttl=$expiresIn"
Assert '4.streamUrl' ($ticket.data.streamUrl -eq "/music/stream/${songId}?ticket=$ticketValue") $ticket.data.streamUrl
Assert '4.noFileId' (-not $ticket.data.streamUrl.Contains([string]$fileId)) '流式地址不暴露 file_id'
Assert '4.lyric' ($null -ne $ticket.data.lyricText) '票据视图携带歌词'

# 4.1 无票据拒绝
$noTicket = Send-StreamProbe -Uri "$musicBase/music/stream/$songId"
Assert '4.noTicket' ($noTicket.BodyText -match '"code":40302') "status=$($noTicket.Status) body=$($noTicket.BodyText)"

# 4.2 票据换歌拒绝
$otherSong = Send-StreamProbe -Uri "$musicBase/music/stream/$($songId + 1)?ticket=$ticketValue"
Assert '4.wrongSong' ($otherSong.BodyText -match '"code":40302') $otherSong.BodyText

# 4.3 票据换用户拒绝
$otherUserHeaders = @{ 'X-User-Id' = '2'; 'X-Tenant-Id' = $tenantId;
    'X-Permission-Codes' = 'sys:music:browse' }
$otherUser = Invoke-Api -Method 'Get' -Uri "$musicBase/music/stream/${songId}?ticket=$ticketValue" `
    -Headers $otherUserHeaders
Assert '4.wrongUser' ($otherUser.code -eq 40302) "code=$($otherUser.code)"

# 5) Range 分段：206
$partial = Send-StreamProbe -Uri "$musicBase/music/stream/${songId}?ticket=$ticketValue" -Range 'bytes=0-1023'
Assert '5.range206' ($partial.Status -eq 206 -and $partial.BodyLength -eq 1024) `
    "status=$($partial.Status) body=$($partial.BodyLength)"
Assert '5.contentRange' ($partial.ContentRange -eq "bytes 0-1023/$testAudioBytes") $partial.ContentRange
Assert '5.rangeHeaders' ($partial.AcceptRanges -eq 'bytes' -and
    $partial.CacheControl -match 'no-store' -and $partial.CacheControl -match 'private') `
    "accept=$($partial.AcceptRanges) cache=$($partial.CacheControl)"
Assert '5.etag' ($partial.ETag -ne '') "etag=$($partial.ETag)"

# 6) Range 越界：416
$refused = Send-StreamProbe -Uri "$musicBase/music/stream/${songId}?ticket=$ticketValue" -Range 'bytes=99999-'
Assert '6.range416' ($refused.Status -eq 416 -and $refused.BodyLength -eq 0) `
    "status=$($refused.Status) body=$($refused.BodyLength)"
Assert '6.contentRange' ($refused.ContentRange -eq "bytes */$testAudioBytes") $refused.ContentRange

# 7) 无 Range：200 全量
$full = Send-StreamProbe -Uri "$musicBase/music/stream/${songId}?ticket=$ticketValue"
Assert '7.range200' ($full.Status -eq 200 -and $full.BodyLength -eq $testAudioBytes) `
    "status=$($full.Status) body=$($full.BodyLength)"
Assert '7.noContentRange' ([string]::IsNullOrEmpty($full.ContentRange)) "contentRange=$($full.ContentRange)"

# 8) 播放进度幂等（同一用户同一歌曲仅一条记录）
Invoke-MusicApi -Method 'Post' -Path '/music/v1/play/progress' `
    -Body @{ songId = $songId; progressMs = 12000; source = 'MY'; device = 'WEB' } | Out-Null
Invoke-MusicApi -Method 'Post' -Path '/music/v1/play/progress' `
    -Body @{ songId = $songId; progressMs = 18000; source = 'MY'; device = 'WEB' } | Out-Null
$history = Invoke-MusicApi -Method 'Get' -Path '/music/v1/play/history?current=1&size=20'
Check '8.history' $history
$matched = @($history.data.records | Where-Object { [long]$_.songId -eq $songId })
Assert '8.historyIdempotent' ($matched.Count -eq 1 -and [long]$matched[0].progressMs -eq 18000) `
    "count=$($matched.Count) progress=$($matched[0].progressMs)"

# 9) 收藏幂等（重复收藏只计一次，取消后消失）
Invoke-MusicApi -Method 'Post' -Path "/music/v1/song/$songId/like" | Out-Null
Invoke-MusicApi -Method 'Post' -Path "/music/v1/song/$songId/like" | Out-Null
$likes = Invoke-MusicApi -Method 'Get' -Path '/music/v1/my/likes?current=1&size=20'
Check '9.myLikes' $likes
Assert '9.likeIdempotent' (@($likes.data.records | Where-Object { [long]$_.id -eq $songId }).Count -eq 1)
Assert '9.likeNoFileId' (-not ($likes.data.records[0].PSObject.Properties.Name -contains 'audioFileId')) '收藏视图不暴露 file_id'

Invoke-MusicApi -Method 'Delete' -Path "/music/v1/song/$songId/like" | Out-Null
Invoke-MusicApi -Method 'Delete' -Path "/music/v1/song/$songId/like" | Out-Null
$likesAfter = Invoke-MusicApi -Method 'Get' -Path '/music/v1/my/likes?current=1&size=20'
Assert '9.unlikeIdempotent' (@($likesAfter.data.records | Where-Object { [long]$_.id -eq $songId }).Count -eq 0)

# 10) 版权下架后拒绝播放（票据仍在有效期内也应立即失效）
$takedown = Invoke-MusicApi -Method 'Post' -Path "/music/v1/song/$songId/takedown" `
    -Body @{ reason = 'M2 冒烟版权下架' }
Check '10.takedown' $takedown
Assert '10.takedownState' ($takedown.data.publishState -eq 'OFFLINE' -and
    $takedown.data.copyrightState -eq 'TAKEDOWN' -and -not $takedown.data.playable)

$blocked = Send-StreamProbe -Uri "$musicBase/music/stream/${songId}?ticket=$ticketValue" -Range 'bytes=0-1023'
Assert '10.streamBlocked' ($blocked.BodyText -match '"code":40403') "status=$($blocked.Status) body=$($blocked.BodyText)"

$reticket = Invoke-MusicApi -Method 'Post' -Path '/music/v1/play/ticket' -Body @{ songId = $songId }
Assert '10.ticketBlocked' ($reticket.code -eq 40403) "code=$($reticket.code)"

# 11) 清理（歌曲 / 歌手逻辑删除，文件物理删除）
Invoke-MusicApi -Method 'Delete' -Path "/music/v1/song/$songId" | Out-Null
Invoke-MusicApi -Method 'Delete' -Path "/music/v1/artist/$artistId" | Out-Null
$fileDelete = Invoke-Api -Method 'Delete' -Uri "$fileBase/file/v1/$fileId" -Headers $h
Check '11.fileDelete' $fileDelete
Remove-Item $audioPath -Force -ErrorAction SilentlyContinue

Assert '11.songGone' ((Invoke-MusicApi -Method 'Get' -Path "/music/v1/song/$songId").code -eq 40403)

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
