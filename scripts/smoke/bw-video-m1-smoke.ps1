# Wave 5.77 M1 共享媒体底座冒烟（强断言）
#   覆盖：服务就绪 / meta 契约 / 权限拒绝（基线）
#         首页聚合、分类标签、上传声明、内容分页与搜索、
#         详情 / 播放票据 / 评论 / 点赞收藏 / 观看历史、
#         运营分页与越权拒绝、上传文件校验拒绝。
#   可选：设置 SMOKE_VIDEO_CONTENT_ID=<内容ID> 追加运营发布 / 下架闭环断言。
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 就绪，
#       bluewhale_video 已执行 V1__baseline.sql，auth 已执行 migration-5.77-video.sql。
# 说明：脚本直连服务，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-video-m1-smoke.ps1
#   VIDEO_BASE / FILE_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID / VIDEO_PERMISSION_CODES
#   / SMOKE_VIDEO_CONTENT_ID 可覆盖。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

Write-Host "== M1 共享媒体底座冒烟 用户=$(Get-VideoUserId) 租户=$(Get-VideoTenantId) =="
Assert-VideoBaseline -Milestone 'm1' -IncludeFile

# ---------------------------------------------------------------
# 3 首页聚合
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 3 首页聚合 =='
$homeAgg = Assert-VideoOk '3.home' (Send-VideoProbe -Path '/video/home')
Assert '3.homeSections' ($null -ne $homeAgg.shorts -and $null -ne $homeAgg.videos -and $null -ne $homeAgg.movies) `
    "shorts=$(@($homeAgg.shorts).Count) videos=$(@($homeAgg.videos).Count) movies=$(@($homeAgg.movies).Count)"
Assert '3.homeCategories' (@($homeAgg.categories).Count -ge 1) "count=$(@($homeAgg.categories).Count)"
Assert '3.homeTags' (@($homeAgg.tags).Count -ge 1) "count=$(@($homeAgg.tags).Count)"
Assert-VideoNoFileId '3.home' (@($homeAgg.shorts) + @($homeAgg.videos) + @($homeAgg.movies))

# ---------------------------------------------------------------
# 4 分类与标签
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 4 分类与标签 =='
$categories = Assert-VideoOk '4.categories' (Send-VideoProbe -Path '/video/categories')
Assert '4.categoriesSeeded' (@($categories).Count -ge 1) "count=$(@($categories).Count)"
if (@($categories).Count -ge 1) {
    $firstCategory = @($categories)[0]
    Assert '4.categoryShape' ($null -ne $firstCategory.id -and $null -ne $firstCategory.code `
        -and $null -ne $firstCategory.name) "id=$($firstCategory.id) code=$($firstCategory.code)"
    Assert '4.categoryScope' (@('ALL', 'SHORT_VIDEO', 'VIDEO', 'MOVIE') -contains $firstCategory.contentTypeScope) `
        "scope=$($firstCategory.contentTypeScope)"
}

$tags = Assert-VideoOk '4.tags' (Send-VideoProbe -Path '/video/tags')
Assert '4.tagsSeeded' (@($tags).Count -ge 1) "count=$(@($tags).Count)"
if (@($tags).Count -ge 1) {
    $firstTag = @($tags)[0]
    Assert '4.tagShape' ($null -ne $firstTag.id -and $null -ne $firstTag.name) "id=$($firstTag.id) name=$($firstTag.name)"
    Assert '4.tagType' (@('TOPIC', 'GENRE', 'LANGUAGE') -contains $firstTag.tagType) "type=$($firstTag.tagType)"
}

# ---------------------------------------------------------------
# 5 上传声明：查询 → 签署（幂等）
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 5 上传声明 =='
$declaration = Assert-VideoOk '5.declaration' (Send-VideoProbe -Path '/video/declaration/current')
Assert '5.declarationVersion' ($declaration.version -eq 'V1') "version=$($declaration.version)"
Assert '5.declarationTitle' (-not [string]::IsNullOrWhiteSpace($declaration.title)) "title=$($declaration.title)"
$declarationText = [string]$declaration.text
Assert '5.declarationText' ($declarationText.Length -gt 50) "length=$($declarationText.Length)"

$acceptBody = @{ version = $declaration.version; contentSource = 'ORIGINAL' }
$accepted = Assert-VideoOk '5.accept' (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' -Body $acceptBody)
Assert '5.acceptStatus' ($accepted.accepted -eq $true) "accepted=$($accepted.accepted)"
Assert '5.acceptSource' ($accepted.contentSource -eq 'ORIGINAL') "source=$($accepted.contentSource)"

$again = Assert-VideoOk '5.acceptAgain' (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' -Body $acceptBody)
Assert '5.acceptIdempotent' ($again.accepted -eq $true) "accepted=$($again.accepted)"
Assert-VideoBizError '5.acceptVersionMismatch' `
    (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' `
        -Body @{ version = 'V0'; contentSource = 'ORIGINAL' }) 40008

# ---------------------------------------------------------------
# 6 内容分页 / feed / 搜索
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 6 内容分页与搜索 =='
$videoPage = Assert-VideoOk '6.videos' (Send-VideoProbe -Path '/video/videos/page?pageNum=1&pageSize=5')
Assert-VideoPageShape '6.videos' $videoPage 1 5
Assert-VideoNoFileId '6.videos' @($videoPage.records)

$moviePage = Assert-VideoOk '6.movies' (Send-VideoProbe -Path '/video/movies/page?current=1&size=5')
Assert-VideoPageShape '6.movies' $moviePage 1 5
Assert-VideoNoFileId '6.movies' @($moviePage.records)

$feedPage = Assert-VideoOk '6.shortsFeed' (Send-VideoProbe -Path '/video/shorts/feed?size=5')
Assert-VideoFeedShape '6.shortsFeed' $feedPage 5
Assert-VideoNoFileId '6.shortsFeed' @($feedPage.records)

Assert-VideoPageShape '6.search' `
    (Assert-VideoOk '6.search' (Send-VideoProbe -Path '/video/search?keyword=smoke&pageNum=1&pageSize=5')) 1 5

# ---------------------------------------------------------------
# 7 内容闭环（仅当列表中存在内容时执行）
# ---------------------------------------------------------------
$contentId = 0
foreach ($item in (@($videoPage.records) + @($moviePage.records) + @($feedPage.records))) {
    if ($null -ne $item -and [long]$item.id -gt 0) {
        $contentId = [long]$item.id
        break
    }
}

Write-Host ''
if ($contentId -gt 0) {
    Write-Host "== 7 内容闭环 contentId=$contentId =="
    $detail = Assert-VideoOk '7.detail' (Send-VideoProbe -Path "/video/content/$contentId")
    Assert '7.detailShape' ($null -ne $detail.id -and $detail.ticketUrl -eq '/video/play/ticket') `
        "id=$($detail.id) ticketUrl=$($detail.ticketUrl)"
    Assert '7.detailPlayable' ($detail.playable -eq $true) "playable=$($detail.playable)"
    Assert '7.detailNoFileId' ((@($detail.PSObject.Properties.Name) | Where-Object { $_ -match 'fileId' }).Count -eq 0) `
        '详情泄露文件 ID'

    $ticket = Assert-VideoOk '7.ticket' (Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' `
        -Body @{ contentId = $contentId })
    Assert '7.ticketToken' (-not [string]::IsNullOrWhiteSpace($ticket.ticket)) '票据为空'
    Assert '7.ticketTtl' ([int]$ticket.expiresIn -eq 300 -and [int]$ticket.refreshIn -eq 240) `
        "expiresIn=$($ticket.expiresIn) refreshIn=$($ticket.refreshIn)"
    Assert '7.ticketStreamUrl' ($ticket.streamUrl.StartsWith(('/video/stream/' + $contentId + '?ticket='))) `
        "streamUrl=$($ticket.streamUrl)"

    $commentPage = Assert-VideoOk '7.commentPage' `
        (Send-VideoProbe -Path "/video/content/$contentId/comment/page?pageNum=1&pageSize=5")
    Assert-VideoPageShape '7.commentPage' $commentPage 1 5

    $commentText = '冒烟评论 ' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
    $comment = Assert-VideoOk '7.commentSubmit' (Send-VideoProbe -Method 'Post' `
        -Path "/video/content/$contentId/comment" -Body @{ content = $commentText })
    Assert '7.commentShape' ($null -ne $comment.id -and $comment.content -eq $commentText) `
        "id=$($comment.id)"
    $null = Assert-VideoOk '7.commentDelete' (Send-VideoProbe -Method 'Delete' `
        -Path ("/video/comment/" + $comment.id))

    $null = Assert-VideoOk '7.like' (Send-VideoProbe -Method 'Post' -Path "/video/content/$contentId/like")
    $null = Assert-VideoOk '7.unlike' (Send-VideoProbe -Method 'Delete' -Path "/video/content/$contentId/like")
    $null = Assert-VideoOk '7.favorite' (Send-VideoProbe -Method 'Post' -Path "/video/content/$contentId/favorite")
    $null = Assert-VideoOk '7.unfavorite' (Send-VideoProbe -Method 'Delete' `
        -Path "/video/content/$contentId/favorite")

    $progress = @{ contentId = $contentId; progressMs = 1000; source = 'MY'; device = 'WEB' }
    $null = Assert-VideoOk '7.progress' (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Body $progress)

    $history = Assert-VideoOk '7.history' (Send-VideoProbe -Path '/video/history?pageNum=1&pageSize=5')
    Assert-VideoPageShape '7.history' $history 1 5
    $historyAgain = Assert-VideoOk '7.historyAgain' (Send-VideoProbe -Path '/video/history?current=1&size=5')
    Assert '7.historyShaped' ($null -ne $historyAgain.records) '历史分页缺少 records'
    $null = Assert-VideoOk '7.historyDelete' (Send-VideoProbe -Method 'Delete' -Path "/video/history/$contentId")

    Assert-VideoPageShape '7.myLikes' `
        (Assert-VideoOk '7.myLikes' (Send-VideoProbe -Path '/video/my/likes?pageNum=1&pageSize=5')) 1 5
    Assert-VideoPageShape '7.myFavorites' `
        (Assert-VideoOk '7.myFavorites' (Send-VideoProbe -Path '/video/my/favorites?pageNum=1&pageSize=5')) 1 5
} else {
    Write-Host 'SKIP 7 内容闭环（当前库无已发布内容，插入内容后重跑可覆盖）'
}

# ---------------------------------------------------------------
# 8 运营分页与越权拒绝
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 8 运营接口与权限 =='
$adminContent = Assert-VideoOk '8.adminContent' (Send-VideoProbe -Path '/video/admin/content/page?pageNum=1&pageSize=5')
Assert-VideoPageShape '8.adminContent' $adminContent 1 5

$adminComment = Assert-VideoOk '8.adminComment' (Send-VideoProbe -Path '/video/admin/comment/page?pageNum=1&pageSize=5')
Assert-VideoPageShape '8.adminComment' $adminComment 1 5

$browseOnly = New-VideoHeaders -PermissionCodes 'sys:video:browse'
Assert-VideoBizError '8.adminDenied' `
    (Send-VideoProbe -Path '/video/admin/content/page?pageNum=1&pageSize=5' -Headers $browseOnly) 403
Assert-VideoBizError '8.homeDenied' `
    (Send-VideoProbe -Path '/video/home' -Headers (New-VideoHeaders -PermissionCodes 'sys:video:upload')) 403

# ---------------------------------------------------------------
# 9 上传校验拒绝（未知文件 ID）
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 9 上传校验 =='
$createBody = @{
    title         = '冒烟视频'
    fileId        = 999999999
    durationMs    = 1000
    contentSource = 'ORIGINAL'
}
Assert-VideoBizError '9.createRejectsUnknownFile' `
    (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $createBody) 40006
# 缺少 fileId 先被 @NotNull 拦截，全局异常处理器返回 400（参数错误）
Assert-VideoBizError '9.createMissingFile' `
    (Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Body @{ title = '冒烟短视频' }) 400

# ---------------------------------------------------------------
# 10 可选：运营发布 / 下架闭环（需显式指定已存在内容 ID）
# ---------------------------------------------------------------
if ($env:SMOKE_VIDEO_CONTENT_ID) {
    $managedId = [long]$env:SMOKE_VIDEO_CONTENT_ID
    Write-Host ''
    Write-Host "== 10 运营发布 / 下架闭环 contentId=$managedId =="
    $published = Assert-VideoOk '10.publish' (Send-VideoProbe -Method 'Post' `
        -Path "/video/admin/content/$managedId/publish")
    Assert '10.publishState' ($published.publishState -eq 'PUBLISHED') "state=$($published.publishState)"

    $null = Assert-VideoOk '10.offline' (Send-VideoProbe -Method 'Post' `
        -Path "/video/admin/content/$managedId/offline")
    Assert-VideoBizError '10.offlineHidden' (Send-VideoProbe -Path "/video/content/$managedId") 40401

    $null = Assert-VideoOk '10.republish' (Send-VideoProbe -Method 'Post' `
        -Path "/video/admin/content/$managedId/publish")
    $detail = Assert-VideoOk '10.republishDetail' (Send-VideoProbe -Path "/video/content/$managedId")
    Assert '10.republishPlayable' ($detail.playable -eq $true) "playable=$($detail.playable)"

    # 空白原因先被 @NotBlank 拦截，全局异常处理器返回 400（参数错误）
    Assert-VideoBizError '10.takedownReasonRequired' `
        (Send-VideoProbe -Method 'Post' -Path "/video/admin/content/$managedId/takedown" -Body @{ reason = '  ' }) 400
}

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
