﻿# Wave 5.75 M3 歌单与发现冒烟：
#   上传测试音频 → 建歌手 / 歌曲 → 上架 → 首页聚合 → 热门 / 新歌榜 → 搜索 →
#   用户歌单（私有越权 / 加歌幂等 / 重排 / 移除）→ 官方歌单（运营加歌 / 用户无权维护）→ 清理。
# 前置：whale-music(8096)、whale-file(8085)、MySQL、Redis、Nacos 就绪。
# 说明：本机无登录口令，脚本与两个服务同网段直连，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-music-m3-smoke.ps1
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

# 运营视角：M3 全部权限码 + M1 建库所需权限
$h = @{
    'X-User-Id'          = $userId
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:artist:add,' +
    'sys:music:song:add,sys:music:song:publish,sys:music:song:delete,sys:music:song:list,' +
    'sys:music:browse,sys:music:like,sys:music:playlist:own,sys:music:playlist:manage'
}
# 普通用户视角：只有浏览与自建歌单权限，用于越权断言
$readerHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:music:browse,sys:music:playlist:own'
}

$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromMinutes(5)
$testAudioBytes = 4096
$audioPath = Join-Path $env:TEMP ('bw-music-m3-' + $stamp + '.wav')

<#
.SYNOPSIS
生成指定长度的合法 PCM WAV（单声道 8kHz 8bit 静音）。
#>
function New-SmokeWav {
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
调用接口并返回 HTTP 状态码与响应体（用于断言 403 等非业务异常）。
#>
function Send-Probe {
    param([string]$Method, [string]$Path, [object]$Body = $null, [hashtable]$Headers = $null)

    if (-not $Headers) {
        $Headers = $h
    }
    $verb = [System.Net.Http.HttpMethod]::Get
    if ($Method -eq 'Post') { $verb = [System.Net.Http.HttpMethod]::Post }
    if ($Method -eq 'Put') { $verb = [System.Net.Http.HttpMethod]::Put }
    if ($Method -eq 'Delete') { $verb = [System.Net.Http.HttpMethod]::Delete }
    $request = New-Object System.Net.Http.HttpRequestMessage($verb, ($musicBase + $Path))
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Content = New-Object System.Net.Http.StringContent(
            $json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    foreach ($key in $Headers.Keys) {
        $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
    }
    $response = $http.SendAsync($request).Result
    $body = $response.Content.ReadAsStringAsync().Result
    return [pscustomobject]@{ Status = [int]$response.StatusCode; BodyText = $body }
}

New-SmokeWav -Path $audioPath -Length $testAudioBytes
Write-Host "== M3 歌单与发现冒烟 用户=$userId 租户=$tenantId =="

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

# 1) 上传测试音频
$upload = Send-Upload -Uri "$fileBase/file/upload" -Path $audioPath -ContentType 'audio/wav'
Check '1.upload' $upload
$fileId = [long]$upload.data.id

# 2) 建歌手
$artist = Invoke-MusicApi -Method 'Post' -Path '/music/artist' -Body @{ name = "M3冒烟歌手-$stamp"; region = 'CN' }
Check '2.artistCreate' $artist
$artistId = [long]$artist.data.id

# 3) 建歌曲（草稿）→ 上架
$songTitle = "M3冒烟歌曲-$stamp"
$songBody = @{
    artistId    = $artistId
    title       = $songTitle
    subtitle    = '歌单与发现冒烟'
    audioFileId = $fileId
    durationMs  = 30000
    visibility  = 'PUBLIC'
    language    = 'ZH'
}
$song = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body $songBody
Check '3.songCreate' $song
$songId = [long]$song.data.id
$published = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/publish"
Check '3.songPublish' $published
Assert '3.songPlayable' ($published.data.publishState -eq 'PUBLISHED' -and $published.data.playable)

# 4) 首页聚合（热门 / 新歌 / 官方歌单 / 标签）
$homeView = Invoke-MusicApi -Method 'Get' -Path '/music/home'
Check '4.home' $homeView
Assert '4.homeShape' ($null -ne $homeView.data.hotSongs -and $null -ne $homeView.data.newSongs -and
    $null -ne $homeView.data.officialPlaylists -and $null -ne $homeView.data.tags) `
    "hot=$($homeView.data.hotSongs.Count) new=$($homeView.data.newSongs.Count) tags=$($homeView.data.tags.Count)"
Assert '4.homeNewSong' (@($homeView.data.newSongs | Where-Object { [long]$_.id -eq $songId }).Count -ge 1) `
    "songId=$songId"
# 第二次命中缓存（序列化 / 反序列化链路可用）
$homeAgain = Invoke-MusicApi -Method 'Get' -Path '/music/home'
Check '4.homeCached' $homeAgain

# 5) 热门 / 新歌榜
$hot = Invoke-MusicApi -Method 'Get' -Path '/music/chart/hot?current=1&size=10'
Check '5.chartHot' $hot
Assert '5.chartHotTotal' ([long]$hot.data.total -ge 1) "total=$($hot.data.total)"
$new = Invoke-MusicApi -Method 'Get' -Path '/music/chart/new?current=1&size=10'
Check '5.chartNew' $new
Assert '5.chartNewSong' (@($new.data.records | Where-Object { [long]$_.id -eq $songId }).Count -ge 1) `
    "songId=$songId"

# 6) 搜索：关键词命中 / 空关键词稳定空结果
$search = Invoke-MusicApi -Method 'Get' -Path ("/music/search?keyword=" + [uri]::EscapeDataString($songTitle) + '&type=SONG')
Check '6.searchSong' $search
Assert '6.searchHit' ([long]$search.data.songTotal -ge 1 -and
    @($search.data.songs | Where-Object { [long]$_.id -eq $songId }).Count -ge 1) `
    "total=$($search.data.songTotal)"
$empty = Invoke-MusicApi -Method 'Get' -Path '/music/search?keyword=&type=ALL'
Check '6.searchEmpty' $empty
Assert '6.searchEmptyShape' ([long]$empty.data.total -eq 0 -and $null -ne $empty.data.songs) `
    "total=$($empty.data.total)"

# 7) 用户歌单：私有可见性 + 越权
$playlist = Invoke-MusicApi -Method 'Post' -Path '/music/playlist' `
    -Body @{ title = "M3私有歌单-$stamp"; description = 'M3 冒烟'; visibility = 'PRIVATE' }
Check '7.playlistCreate' $playlist
$playlistId = [long]$playlist.data.id
Assert '7.playlistPrivate' ($playlist.data.visibility -eq 'PRIVATE' -and [long]$playlist.data.ownerId -eq [long]$userId)
$detail = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/$playlistId"
Check '7.playlistDetailOwner' $detail
$otherDetail = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/$playlistId" -Headers $readerHeaders
Assert '7.playlistDetailOther' ($otherDetail.code -eq 40405) "code=$($otherDetail.code)"
$mine = Invoke-MusicApi -Method 'Get' -Path '/music/playlist/page?current=1&size=10&mine=true' -Headers $readerHeaders
Check '7.playlistPageMine' $mine

# 8) 加歌幂等 / 重排 / 移除
$added = Invoke-MusicApi -Method 'Post' -Path "/music/playlist/$playlistId/song" -Body @{ songId = $songId }
Check '8.playlistAddSong' $added
Assert '8.playlistAddCount' ([int]$added.data.songCount -eq 1 -and @($added.data.songs).Count -eq 1) `
    "songCount=$($added.data.songCount)"
$addedAgain = Invoke-MusicApi -Method 'Post' -Path "/music/playlist/$playlistId/song" -Body @{ songId = $songId }
Check '8.playlistAddIdempotent' $addedAgain
Assert '8.playlistAddNoDup' ([int]$addedAgain.data.songCount -eq 1) "songCount=$($addedAgain.data.songCount)"
$sorted = Invoke-MusicApi -Method 'Put' -Path "/music/playlist/$playlistId/sort" -Body @{ songIds = @($songId) }
Check '8.playlistSort' $sorted
Assert '8.playlistSortNo' (@($sorted.data.songs)[0].sortNo -eq 0) "sortNo=$(@($sorted.data.songs)[0].sortNo)"
$removed = Invoke-MusicApi -Method 'Delete' -Path "/music/playlist/$playlistId/song/$songId"
Check '8.playlistRemoveSong' $removed
$afterRemove = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/$playlistId"
Assert '8.playlistRemoveCount' ([int]$afterRemove.data.songCount -eq 0) "songCount=$($afterRemove.data.songCount)"

# 9) 官方歌单：运营可维护，普通用户只读
$official = Invoke-MusicApi -Method 'Post' -Path '/music/playlist/official' `
    -Body @{ title = "M3官方歌单-$stamp"; description = 'M3 冒烟官方歌单' }
Check '9.officialCreate' $official
$officialId = [long]$official.data.id
Assert '9.officialShape' ($official.data.playlistType -eq 'OFFICIAL' -and
    $official.data.visibility -eq 'PUBLIC' -and [long]$official.data.ownerId -eq 0)
$officialAdd = Invoke-MusicApi -Method 'Post' -Path "/music/playlist/official/$officialId/song" -Body @{ songId = $songId }
Check '9.officialAddSong' $officialAdd
Assert '9.officialAddCount' ([int]$officialAdd.data.songCount -eq 1) "songCount=$($officialAdd.data.songCount)"
$officialDetail = Invoke-MusicApi -Method 'Get' -Path "/music/playlist/official/page?current=1&size=10"
Check '9.officialPage' $officialDetail
# 普通用户（无 manage 权限）不能维护官方歌单曲目
$forbidden = Send-Probe -Method 'Post' -Path "/music/playlist/official/$officialId/song" `
    -Body @{ songId = $songId } -Headers $readerHeaders
Assert '9.officialForbidden' ($forbidden.Status -eq 403 -or $forbidden.BodyText -match '"code":403') `
    "status=$($forbidden.Status) body=$($forbidden.BodyText)"
$userReadonly = Send-Probe -Method 'Post' -Path "/music/playlist/$officialId/song" `
    -Body @{ songId = $songId } -Headers $readerHeaders
Assert '9.officialReadonly' ($userReadonly.Status -eq 403 -or $userReadonly.BodyText -match '"code":40304') `
    "status=$($userReadonly.Status)"

# 10) 清理：官方歌单 / 用户歌单 / 歌曲 / 文件
$delOfficial = Invoke-MusicApi -Method 'Delete' -Path "/music/playlist/official/$officialId"
Check '10.deleteOfficial' $delOfficial
$delPlaylist = Invoke-MusicApi -Method 'Delete' -Path "/music/playlist/$playlistId"
Check '10.deletePlaylist' $delPlaylist
$delSong = Invoke-MusicApi -Method 'Delete' -Path "/music/song/$songId"
Check '10.deleteSong' $delSong
$delFile = Invoke-Api -Method 'Delete' -Uri "$fileBase/file/$fileId" -Headers $h
Assert '10.deleteFile' ($delFile.code -eq 0 -or $delFile.code -eq 404) "code=$($delFile.code)"

Remove-Item -Path $audioPath -ErrorAction SilentlyContinue
Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
