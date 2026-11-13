# Wave 5.75 M4 互动冒烟：
#   上传测试音频 → 建歌手 / 歌曲 → 上架 → 发表评论 / 回复 → 分页（顶级 + 回复分组）→
#   正文与父评论校验 → 审核队列 → 隐藏（立即不可见 + 计数回退）→ 通过（恢复可见）→
#   越权断言（无审核权限 403）→ 清理。
# 前置：whale-music(8096)、whale-file(8085)、MySQL、Redis、Nacos 就绪。
# 说明：本机无登录口令，脚本与两个服务同网段直连，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-music-m4-smoke.ps1
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

# 运营视角：建库 + 评论发表 + 评论审核权限
$h = @{
    'X-User-Id'          = $userId
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:artist:add,' +
    'sys:music:song:add,sys:music:song:publish,sys:music:song:delete,sys:music:song:list,' +
    'sys:music:browse,sys:music:comment:create,sys:music:comment:audit'
}
# 普通用户视角：浏览 + 发表评论（无审核权限）
$readerHeaders = @{
    'X-User-Id'          = '2'
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:music:browse,sys:music:comment:create'
}

$http = New-Object System.Net.Http.HttpClient
$http.Timeout = [TimeSpan]::FromMinutes(5)
$testAudioBytes = 4096
$audioPath = Join-Path $env:TEMP ('bw-music-m4-' + $stamp + '.mp3')

<#
.SYNOPSIS
生成指定长度的静音音频字节。
说明：Wave 5.75 M5-2 起音乐服务按 MIME 白名单校验（audio/mpeg / audio/aac / audio/flac），
     脚本以 .mp3 + audio/mpeg 上传。
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

New-SmokeAudio -Path $audioPath -Length $testAudioBytes
Write-Host "== M4 互动冒烟 用户=$userId 租户=$tenantId =="

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
$upload = Send-Upload -Uri "$fileBase/file/v1/upload" -Path $audioPath -ContentType 'audio/mpeg'
Check '1.upload' $upload
$fileId = [long]$upload.data.id

# 2) 建歌手 → 建歌曲 → 上架
$artist = Invoke-MusicApi -Method 'Post' -Path '/music/artist' -Body @{ name = "M4冒烟歌手-$stamp"; region = 'CN' }
Check '2.artistCreate' $artist
$artistId = [long]$artist.data.id
$songBody = @{
    artistId    = $artistId
    title       = "M4冒烟歌曲-$stamp"
    subtitle    = '互动冒烟'
    audioFileId = $fileId
    durationMs  = 30000
    visibility  = 'PUBLIC'
    language    = 'ZH'
}
$song = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body $songBody
Check '2.songCreate' $song
$songId = [long]$song.data.id
$published = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/publish"
Check '2.songPublish' $published
Assert '2.songPlayable' ($published.data.publishState -eq 'PUBLISHED' -and $published.data.playable)

# 3) 发表顶级评论（先发后审：立即可见 + 计数 +1）
$comment = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" `
    -Body @{ content = "M4冒烟评论-$stamp"; parentId = 0 }
Check '3.commentCreate' $comment
$commentId = [long]$comment.data.id
Assert '3.commentVisible' ($comment.data.status -eq 'VISIBLE' -and [long]$comment.data.parentId -eq 0) `
    "status=$($comment.data.status)"
$songView = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId"
Assert '3.commentCount' ([long]$songView.data.commentCount -eq 1) "commentCount=$($songView.data.commentCount)"

# 4) 普通用户回复该评论
$reply = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" `
    -Body @{ content = "M4冒烟回复-$stamp"; parentId = $commentId } -Headers $readerHeaders
Check '4.replyCreate' $reply
$replyId = [long]$reply.data.id
Assert '4.replyParent' ([long]$reply.data.parentId -eq $commentId) "parentId=$($reply.data.parentId)"

# 5) 用户侧分页：顶级评论倒序并携带回复；HTML 标签按纯文本存储
$xss = '<b>M4</b><script>alert(1)</script>'
$xssComment = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" `
    -Body @{ content = $xss; parentId = 0 }
Check '5.commentPlainText' $xssComment
Assert '5.xssStoredAsText' ($xssComment.data.content -eq $xss) "content=$($xssComment.data.content)"
$page = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId/comment/page?current=1&size=10" -Headers $readerHeaders
Check '5.commentPage' $page
Assert '5.pageTotal' ([long]$page.data.total -eq 2) "total=$($page.data.total)"
$topHit = @($page.data.records | Where-Object { [long]$_.id -eq $commentId })
Assert '5.replyGrouped' ($topHit.Count -eq 1 -and @($topHit[0].replies).Count -eq 1 -and
    [long]@($topHit[0].replies)[0].id -eq $replyId) "replies=$(@($topHit[0].replies).Count)"

# 6) 正文与父评论校验：超长 / 空白 / 父评论不存在 / 歌曲不存在
$tooLong = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" `
    -Body @{ content = ('好' * 501); parentId = 0 }
Assert '6.tooLong' ($tooLong.code -eq 40014) "code=$($tooLong.code)"
$blank = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" -Body @{ content = '   '; parentId = 0 }
Assert '6.blank' ($blank.code -eq 40014) "code=$($blank.code)"
$badParent = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/comment" `
    -Body @{ content = '回复不存在的评论'; parentId = 999999 }
Assert '6.badParent' ($badParent.code -eq 40015) "code=$($badParent.code)"
$badSong = Invoke-MusicApi -Method 'Post' -Path '/music/song/999999/comment' -Body @{ content = '不存在歌曲'; parentId = 0 }
Assert '6.badSong' ($badSong.code -eq 40403) "code=$($badSong.code)"

# 7) 审核队列：运营可见（含歌曲标题），普通用户 403
# 说明：PS5.1 的 Invoke-RestMethod 对无 charset 的 JSON 响应按 ISO-8859-1 解码，
#       中文标题会乱码；审核页含中文，改用 HttpClient 探测（UTF-8 解码）后解析。
$auditRaw = Send-Probe -Method 'Get' -Path "/music/comment/page?current=1&size=10&songId=$songId&status=VISIBLE"
$audit = $auditRaw.BodyText | ConvertFrom-Json
Check '7.auditPage' $audit
Assert '7.auditTotal' ([long]$audit.data.total -ge 3) "total=$($audit.data.total)"
$auditHit = @($audit.data.records | Where-Object { [long]$_.id -eq $commentId })
Assert '7.auditSongTitle' ($auditHit.Count -eq 1 -and $auditHit[0].songTitle -eq "M4冒烟歌曲-$stamp") `
    "songTitle=$($auditHit[0].songTitle)"
$auditForbidden = Send-Probe -Method 'Get' -Path "/music/comment/page?current=1&size=10" -Headers $readerHeaders
Assert '7.auditForbidden' ($auditForbidden.Status -eq 403 -or $auditForbidden.BodyText -match '"code":403') `
    "status=$($auditForbidden.Status)"

# 8) 隐藏：立即对用户不可见 + 计数 -1（回复仍可见）
$hidden = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$commentId/hide"
Check '8.hide' $hidden
Assert '8.hiddenStatus' ($hidden.data.status -eq 'HIDDEN') "status=$($hidden.data.status)"
$pageAfterHide = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId/comment/page?current=1&size=10" -Headers $readerHeaders
Assert '8.hideInvisible' (@($pageAfterHide.data.records | Where-Object { [long]$_.id -eq $commentId }).Count -eq 0 -and
    [long]$pageAfterHide.data.total -eq 1) "total=$($pageAfterHide.data.total)"
$songAfterHide = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId"
Assert '8.hideCount' ([long]$songAfterHide.data.commentCount -eq 2) `
    "commentCount=$($songAfterHide.data.commentCount)"

# 9) 通过：恢复可见 + 计数回升
$approved = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$commentId/approve"
Check '9.approve' $approved
Assert '9.approveStatus' ($approved.data.status -eq 'VISIBLE') "status=$($approved.data.status)"
$pageAfterApprove = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId/comment/page?current=1&size=10" -Headers $readerHeaders
Assert '9.approveVisible' ([long]$pageAfterApprove.data.total -eq 2) "total=$($pageAfterApprove.data.total)"
# 重复通过幂等：状态不变、计数不重复
$approvedAgain = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$commentId/approve"
Check '9.approveIdempotent' $approvedAgain
$songFinal = Invoke-MusicApi -Method 'Get' -Path "/music/song/$songId"
Assert '9.finalCount' ([long]$songFinal.data.commentCount -eq 3) "commentCount=$($songFinal.data.commentCount)"

# 10) 清理：隐藏测试评论 / 删除歌曲 / 文件
$null = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$commentId/hide"
$null = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$replyId/hide"
$null = Invoke-MusicApi -Method 'Post' -Path "/music/comment/$($xssComment.data.id)/hide"
$delSong = Invoke-MusicApi -Method 'Delete' -Path "/music/song/$songId"
Check '10.deleteSong' $delSong
$delFile = Invoke-Api -Method 'Delete' -Uri "$fileBase/file/v1/$fileId" -Headers $h
Assert '10.deleteFile' ($delFile.code -eq 0 -or $delFile.code -eq 404) "code=$($delFile.code)"

Remove-Item -Path $audioPath -ErrorAction SilentlyContinue
Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
