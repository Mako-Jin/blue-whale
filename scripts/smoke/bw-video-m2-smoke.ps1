﻿# Wave 5.77 M2 短视频闭环冒烟（强断言）
#   覆盖：服务就绪 / meta 契约 / 权限拒绝（基线）
#         短视频 feed 键集游标（首屏缓存、跨页不重复、HOT 排序、分类筛选、非法游标 400）、
#         我的短视频 / 视频分页、播放票据（单张 / 批量 / 拒绝清单 / 跨用户拒绝）、
#         详情、点赞收藏幂等、进度上报与最近观看、评论闭环、
#         流式 Range（206 / 416、缓存与安全响应头）、下架后旧票据失效。
#   可选：SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 追加「创建私密短视频 → 他人不可见 → 删除」闭环；
#         SMOKE_VIDEO_CONTENT_ID=<内容ID> 追加「运营下架 → 旧票据失效 → 重新上架」闭环。
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 就绪，
#       bluewhale_video 已执行 V1__baseline.sql，auth 已执行 migration-5.77-video.sql。
# 说明：脚本直连服务，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-video-m2-smoke.ps1
#   VIDEO_BASE / FILE_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID / VIDEO_PERMISSION_CODES
#   / SMOKE_VIDEO_FILE_ID / SMOKE_VIDEO_CONTENT_ID 可覆盖。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
构造「同租户其他普通用户」的身份头，用于私密内容 / 票据跨用户拒绝断言。
#>
function New-VideoOtherUserHeaders {
    $otherUserId = [long](Get-VideoUserId) + 1
    return @{
        'X-User-Id'          = [string]$otherUserId
        'X-Tenant-Id'        = Get-VideoTenantId
        'X-Permission-Codes' = Get-VideoPermissionCodes
    }
}

<#
.SYNOPSIS
判断 feed 中是否包含指定内容 ID。
.PARAMETER Feed
Result.data（FeedView）。
.PARAMETER ContentId
目标内容 ID。
#>
function Test-VideoFeedContains {
    param([object]$Feed, [long]$ContentId)

    foreach ($record in @($Feed.records)) {
        if ($null -ne $record -and [long]$record.id -eq $ContentId) {
            return $true
        }
    }
    return $false
}

<#
.SYNOPSIS
断言 feed 键集续读：逐页拉取直到无下一页或达到页数上限，校验形状、游标与跨页不重复。
.DESCRIPTION
- 每页断言「键集信封 + 不含文件 ID + 内容类型为 SHORT_VIDEO + ID 为正」；
- 跨页 ID 不得重复，验证 (排序锚点, id) 键集在发布 / 下架变化下不漏不重；
- hasMore=true 时必须携带可解析的「锚点|内容ID」游标。
.PARAMETER Label
断言标签前缀。
.PARAMETER Sort
排序：LATEST / HOT。
.PARAMETER Size
每批条数。
.PARAMETER MaxPages
最多翻页数。
#>
function Assert-VideoFeedContinuity {
    param([string]$Label, [string]$Sort = 'LATEST', [int]$Size = 3, [int]$MaxPages = 3)

    $seen = New-Object 'System.Collections.Generic.List[long]'
    $cursor = ''
    $pageNo = 0
    $hasMore = $true
    while ($hasMore -and $pageNo -lt $MaxPages) {
        $pageNo++
        $tag = $Label + '#' + $pageNo
        $path = "/video/shorts/feed?size=$Size&sort=$Sort"
        if (-not [string]::IsNullOrWhiteSpace($cursor)) {
            $path = $path + '&cursor=' + [uri]::EscapeDataString($cursor)
        }
        $feed = Assert-VideoOk $tag (Send-VideoProbe -Path $path)
        Assert-VideoFeedShape $tag $feed $Size
        Assert-VideoNoFileId $tag (@($feed.records))
        foreach ($record in @($feed.records)) {
            if ($null -eq $record) {
                continue
            }
            $recordId = [long]$record.id
            Assert "$tag.id" ($recordId -gt 0) "id=$recordId"
            Assert "$tag.type" ($record.contentType -eq 'SHORT_VIDEO') "type=$($record.contentType)"
            Assert "$tag.unique" (-not $seen.Contains($recordId)) "跨页重复 id=$recordId"
            $seen.Add($recordId)
        }
        $hasMore = [bool]$feed.hasMore
        $cursor = [string]$feed.nextCursor
        if ($hasMore) {
            Assert "$tag.hasMoreCursor" (-not [string]::IsNullOrWhiteSpace($cursor)) 'hasMore=true 但游标为空'
        }
    }
    Write-Host "OK   $Label.pages=$pageNo records=$($seen.Count)"
}

Write-Host "== M2 短视频闭环冒烟 用户=$(Get-VideoUserId) 租户=$(Get-VideoTenantId) =="
Assert-VideoBaseline -Milestone 'm2' -IncludeFile

# ---------------------------------------------------------------
# 3 短视频 feed 键集游标
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 3 短视频 feed =='
$firstFeed = Assert-VideoOk '3.feedFirst' (Send-VideoProbe -Path '/video/shorts/feed?size=5')
Assert-VideoFeedShape '3.feedFirst' $firstFeed 5
Assert-VideoNoFileId '3.feedFirst' (@($firstFeed.records))
$firstIds = (@($firstFeed.records) | Where-Object { $null -ne $_ } | ForEach-Object { [long]$_.id }) -join ','

# 首屏缓存：30 秒内重复请求应返回同一批内容
$repeatFeed = Assert-VideoOk '3.feedCached' (Send-VideoProbe -Path '/video/shorts/feed?size=5')
$repeatIds = (@($repeatFeed.records) | Where-Object { $null -ne $_ } | ForEach-Object { [long]$_.id }) -join ','
Assert '3.feedCacheStable' ($repeatIds -eq $firstIds) "first=$firstIds repeat=$repeatIds"

# 最新流：连续两页键集续读不重复
Assert-VideoFeedContinuity -Label '3.feedLatest' -Sort 'LATEST' -Size 3 -MaxPages 2
# 热门流：连续两页键集续读不重复（热度分锚点由服务端生成）
Assert-VideoFeedContinuity -Label '3.feedHot' -Sort 'HOT' -Size 3 -MaxPages 2

# 非法游标统一返回 400（不泄露内部解析细节）
Assert-VideoBizError '3.feedCursorIllegal' (Send-VideoProbe -Path '/video/shorts/feed?cursor=abc%7C1') 400
Assert-VideoBizError '3.feedCursorAnchor' (Send-VideoProbe -Path '/video/shorts/feed?cursor=1.5%7C1') 400

# 分类筛选：分类 ID 必须作用于短视频（ALL / SHORT_VIDEO 范围）
$categories = Assert-VideoOk '3.categories' (Send-VideoProbe -Path '/video/categories')
$feedCategory = $null
foreach ($category in @($categories)) {
    if ($null -ne $category -and (@('ALL', 'SHORT_VIDEO') -contains $category.contentTypeScope)) {
        $feedCategory = $category
        break
    }
}
if ($null -ne $feedCategory) {
    $categoryFeed = Assert-VideoOk '3.feedCategory' `
        (Send-VideoProbe -Path ("/video/shorts/feed?size=5&categoryId=" + [long]$feedCategory.id))
    Assert-VideoFeedShape '3.feedCategory' $categoryFeed 5
    foreach ($record in @($categoryFeed.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert '3.feedCategoryMatch' ([long]$record.categoryId -eq [long]$feedCategory.id) `
            "expect=$($feedCategory.id) actual=$($record.categoryId)"
    }
} else {
    Write-Host 'SKIP 3 分类 feed（当前库无 ALL / SHORT_VIDEO 范围分类）'
}

# feed 需要 sys:video:browse，最小权限集合应被拒绝
Assert-VideoBizError '3.feedDenied' `
    (Send-VideoProbe -Path '/video/shorts/feed' -Headers (New-VideoHeaders -PermissionCodes 'sys:video:like')) 403

# ---------------------------------------------------------------
# 4 我的作品分页
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 4 我的作品 =='
$myShorts = Assert-VideoOk '4.myShorts' (Send-VideoProbe -Path '/video/my/shorts?pageNum=1&pageSize=5')
Assert-VideoPageShape '4.myShorts' $myShorts 1 5
Assert-VideoNoFileId '4.myShorts' (@($myShorts.records))
$myShortsAlias = Assert-VideoOk '4.myShortsAlias' (Send-VideoProbe -Path '/video/my/shorts?current=1&size=5')
Assert-VideoPageShape '4.myShortsAlias' $myShortsAlias 1 5

$myVideos = Assert-VideoOk '4.myVideos' (Send-VideoProbe -Path '/video/my/videos?pageNum=1&pageSize=5')
Assert-VideoPageShape '4.myVideos' $myVideos 1 5
Assert-VideoNoFileId '4.myVideos' (@($myVideos.records))

# 我的作品需要 sys:video:upload，仅浏览权限应被拒绝
Assert-VideoBizError '4.myShortsDenied' `
    (Send-VideoProbe -Path '/video/my/shorts?pageNum=1&pageSize=5' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:browse')) 403

# ---------------------------------------------------------------
# 5 播放票据与流式鉴权
# ---------------------------------------------------------------
$contentId = 0L
if ($env:SMOKE_VIDEO_CONTENT_ID) {
    $contentId = [long]$env:SMOKE_VIDEO_CONTENT_ID
}
if ($contentId -le 0) {
    foreach ($record in @($firstFeed.records)) {
        if ($null -ne $record -and [long]$record.id -gt 0) {
            $contentId = [long]$record.id
            break
        }
    }
}

$ticket = $null
Write-Host ''
if ($contentId -gt 0) {
    Write-Host "== 5 播放票据 contentId=$contentId =="
    $ticket = Assert-VideoOk '5.ticket' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Body @{ contentId = $contentId })
    Assert '5.ticketToken' (-not [string]::IsNullOrWhiteSpace([string]$ticket.ticket)) '票据为空'
    Assert '5.ticketTtl' ([int]$ticket.expiresIn -eq 300 -and [int]$ticket.refreshIn -eq 240) `
        "expiresIn=$($ticket.expiresIn) refreshIn=$($ticket.refreshIn)"
    Assert '5.ticketStreamUrl' ([string]$ticket.streamUrl -match ('^/video/stream/' + $contentId + '\?ticket=')) `
        "streamUrl=$($ticket.streamUrl)"
    Assert '5.ticketNoFileId' ((@($ticket.PSObject.Properties.Name) | Where-Object { $_ -match 'fileId' }).Count -eq 0) `
        '票据泄露文件 ID'

    # 批量票据：不可播 / 不存在的内容进入 rejectedContentIds，不阻断其他内容
    $batch = Assert-VideoOk '5.batch' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/tickets' `
            -Body @{ contentIds = @($contentId, 999999999) })
    Assert '5.batchCount' (@($batch.tickets).Count -eq 1) "tickets=$(@($batch.tickets).Count)"
    $batchTicket = @($batch.tickets)[0]
    Assert '5.batchContent' ([long]$batchTicket.contentId -eq $contentId) "contentId=$($batchTicket.contentId)"
    Assert '5.batchRejected' (@($batch.rejectedContentIds) -contains 999999999) `
        "rejected=$(@($batch.rejectedContentIds) -join ',')"
    Assert-VideoNoFileId '5.batch' (@($batch.tickets))

    # 缺参数与空列表由 Bean Validation 拦截（code=400）
    Assert-VideoBizError '5.batchEmpty' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/tickets' -Body @{ contentIds = @() }) 400

    # 票据绑定三要素：缺失 / 伪造 / 跨用户一律 40302，且不暴露文件服务错误
    Assert-VideoBizError '5.streamNoTicket' (Send-VideoProbe -Path ("/video/stream/" + $contentId)) 40302
    Assert-VideoBizError '5.streamBadTicket' `
        (Send-VideoProbe -Path ("/video/stream/" + $contentId + '?ticket=smoke-invalid')) 40302
    Assert-VideoBizError '5.streamOtherUser' `
        (Send-VideoProbe -Path $ticket.streamUrl -Headers (New-VideoOtherUserHeaders)) 40302
} else {
    Write-Host 'SKIP 5 播放票据（当前库无已发布短视频，插入内容或设置 SMOKE_VIDEO_CONTENT_ID 后重跑）'
}

# ---------------------------------------------------------------
# 6 详情与互动
# ---------------------------------------------------------------
Write-Host ''
if ($contentId -gt 0) {
    Write-Host "== 6 详情与互动 contentId=$contentId =="
    $detail = Assert-VideoOk '6.detail' (Send-VideoProbe -Path ("/video/shorts/" + $contentId))
    Assert '6.detailShape' ($null -ne $detail.id -and [string]$detail.ticketUrl -eq '/video/play/ticket') `
        "id=$($detail.id) ticketUrl=$($detail.ticketUrl)"
    Assert '6.detailType' ($detail.contentType -eq 'SHORT_VIDEO') "type=$($detail.contentType)"
    Assert '6.detailPlayable' ($detail.playable -eq $true) "playable=$($detail.playable)"
    Assert '6.detailNoFileId' `
        ((@($detail.PSObject.Properties.Name) | Where-Object { $_ -match 'fileId' }).Count -eq 0) '详情泄露文件 ID'

    # 点赞 / 收藏幂等：重复请求仍返回成功，计数不重复累加
    $null = Assert-VideoOk '6.like' (Send-VideoProbe -Method 'Post' -Path ("/video/content/" + $contentId + '/like'))
    $null = Assert-VideoOk '6.likeAgain' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/content/" + $contentId + '/like'))
    $likePage = Assert-VideoOk '6.myLikes' (Send-VideoProbe -Path '/video/my/likes?pageNum=1&pageSize=5')
    Assert-VideoPageShape '6.myLikes' $likePage 1 5
    Assert '6.myLikesContains' (@($likePage.records | Where-Object { [long]$_.id -eq $contentId }).Count -ge 1) `
        '我的点赞未包含本次内容'
    $null = Assert-VideoOk '6.unlike' `
        (Send-VideoProbe -Method 'Delete' -Path ("/video/content/" + $contentId + '/like'))

    $null = Assert-VideoOk '6.favorite' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/content/" + $contentId + '/favorite'))
    $null = Assert-VideoOk '6.favoriteAgain' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/content/" + $contentId + '/favorite'))
    $favoritePage = Assert-VideoOk '6.myFavorites' (Send-VideoProbe -Path '/video/my/favorites?pageNum=1&pageSize=5')
    Assert-VideoPageShape '6.myFavorites' $favoritePage 1 5
    Assert '6.myFavoritesContains' `
        (@($favoritePage.records | Where-Object { [long]$_.id -eq $contentId }).Count -ge 1) `
        '我的收藏未包含本次内容'
    $null = Assert-VideoOk '6.unfavorite' `
        (Send-VideoProbe -Method 'Delete' -Path ("/video/content/" + $contentId + '/favorite'))

    # 进度上报：超过时长的进度必须拒绝，合法进度重复上报幂等
    $duration = [long]$detail.durationMs
    $progressMs = 1000L
    if ($duration -gt 0 -and $progressMs -gt $duration) {
        $progressMs = [long]([math]::Floor($duration / 2))
    }
    $progressBody = @{ contentId = $contentId; progressMs = $progressMs; source = 'FEED'; device = 'WEB' }
    $null = Assert-VideoOk '6.progress' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Body $progressBody)
    $null = Assert-VideoOk '6.progressAgain' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Body $progressBody)
    $history = Assert-VideoOk '6.history' (Send-VideoProbe -Path '/video/history?pageNum=1&pageSize=5')
    Assert-VideoPageShape '6.history' $history 1 5
    $historyRecord = @($history.records | Where-Object { [long]$_.contentId -eq $contentId })
    Assert '6.historyContains' ($historyRecord.Count -ge 1) '最近观看未包含本次内容'
    Assert '6.historyProgress' ([long]$historyRecord[0].progressMs -eq $progressMs) `
        "expect=$progressMs actual=$($historyRecord[0].progressMs)"
    if ($duration -gt 0) {
        Assert-VideoBizError '6.progressOverflow' `
            (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' `
                -Body @{ contentId = $contentId; progressMs = ($duration + 1000) }) 40016
    }
    $null = Assert-VideoOk '6.historyDelete' `
        (Send-VideoProbe -Method 'Delete' -Path ("/video/history/" + $contentId))

    # 评论闭环：公开已发布内容可评论；私密内容 commentable=false 时跳过
    if ($detail.commentable -eq $true) {
        $commentPage = Assert-VideoOk '6.commentPage' `
            (Send-VideoProbe -Path ("/video/content/" + $contentId + '/comment/page?pageNum=1&pageSize=5'))
        Assert-VideoPageShape '6.commentPage' $commentPage 1 5

        $commentText = 'M2 冒烟评论 ' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
        $comment = Assert-VideoOk '6.commentSubmit' `
            (Send-VideoProbe -Method 'Post' -Path ("/video/content/" + $contentId + '/comment') `
                -Body @{ content = $commentText })
        Assert '6.commentShape' ($null -ne $comment.id -and [string]$comment.content -eq $commentText) `
            "id=$($comment.id)"
        $null = Assert-VideoOk '6.commentDelete' `
            (Send-VideoProbe -Method 'Delete' -Path ("/video/comment/" + $comment.id))
    } else {
        Write-Host 'SKIP 6 评论闭环（当前内容不可评论：私密或已下架）'
    }
} else {
    Write-Host 'SKIP 6 详情与互动（无可用内容）'
}

# ---------------------------------------------------------------
# 7 流式 Range（206 / 416）
# ---------------------------------------------------------------
Write-Host ''
if ($null -ne $ticket -and -not [string]::IsNullOrWhiteSpace([string]$ticket.streamUrl)) {
    Write-Host '== 7 流式 Range =='
    $rangeProbe = Send-VideoStreamProbe -Path ([string]$ticket.streamUrl) -Range 'bytes=0-1023'
    Assert '7.rangeStatus' ([int]$rangeProbe.Status -eq 206) "status=$($rangeProbe.Status)"
    Assert '7.rangeAcceptRanges' ([string]$rangeProbe.AcceptRanges -eq 'bytes') `
        "acceptRanges=$($rangeProbe.AcceptRanges)"
    # HttpClient 会把 Cache-Control 拆成指令集合（顺序可能变化），按指令包含判断
    Assert '7.rangeNoStore' ([string]$rangeProbe.CacheControl -match 'no-store' -and `
        [string]$rangeProbe.CacheControl -match 'private') "cacheControl=$($rangeProbe.CacheControl)"
    Assert '7.rangeNoSniff' ([string]$rangeProbe.NoSniff -eq 'nosniff') "nosniff=$($rangeProbe.NoSniff)"
    Assert '7.rangeContentType' ([string]$rangeProbe.ContentType -match '^video/') `
        "contentType=$($rangeProbe.ContentType)"
    Assert '7.rangeContentRange' ([string]$rangeProbe.ContentRange -match '^bytes 0-\d+/\d+$') `
        "contentRange=$($rangeProbe.ContentRange)"
    Assert '7.rangeBytes' ([long]$rangeProbe.BytesRead -gt 0 -and [long]$rangeProbe.BytesRead -le 1024) `
        "bytes=$($rangeProbe.BytesRead)"
    Assert '7.rangeLength' ([long]$rangeProbe.BytesRead -eq [long]$rangeProbe.ContentLength) `
        "read=$($rangeProbe.BytesRead) length=$($rangeProbe.ContentLength)"

    # 越界区间：语法合法但起点等于总大小，服务端返回 416 与 bytes */size
    $totalMatch = [regex]::Match([string]$rangeProbe.ContentRange, '/(\d+)$')
    $total = 0L
    if ($totalMatch.Success) {
        $total = [long]$totalMatch.Groups[1].Value
    }
    Assert '7.rangeTotal' ($total -gt 0) "total=$total"
    if ($total -gt 0) {
        $refused = Send-VideoStreamProbe -Path ([string]$ticket.streamUrl) -Range ('bytes=' + $total + '-')
        Assert '7.refusedStatus' ([int]$refused.Status -eq 416) "status=$($refused.Status)"
        Assert '7.refusedContentRange' ([string]$refused.ContentRange -eq ('bytes */' + $total)) `
            "contentRange=$($refused.ContentRange)"
        Assert '7.refusedBytes' ([long]$refused.BytesRead -eq 0) "bytes=$($refused.BytesRead)"
        Assert '7.refusedNoStore' ([string]$refused.CacheControl -match 'no-store' -and `
            [string]$refused.CacheControl -match 'private') "cacheControl=$($refused.CacheControl)"
    }
} else {
    Write-Host 'SKIP 7 流式 Range（未取得有效播放票据）'
}

# ---------------------------------------------------------------
# 8 可选：创建私密短视频 → 他人不可见 → 删除（需 SMOKE_VIDEO_FILE_ID）
# ---------------------------------------------------------------
Write-Host ''
if ($env:SMOKE_VIDEO_FILE_ID) {
    $privateFileId = [long]$env:SMOKE_VIDEO_FILE_ID
    Write-Host "== 8 私密短视频闭环 fileId=$privateFileId =="
    $declaration = Assert-VideoOk '8.declaration' (Send-VideoProbe -Path '/video/declaration/current')
    $null = Assert-VideoOk '8.accept' `
        (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' `
            -Body @{ version = $declaration.version; contentSource = 'ORIGINAL' })

    $createBody = @{
        title         = 'M2 私密短视频冒烟'
        fileId        = $privateFileId
        durationMs    = 1000
        contentSource = 'ORIGINAL'
        visibility    = 'PRIVATE'
        publishNow    = $true
    }
    $privateId = [long](Assert-VideoOk '8.create' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Body $createBody))
    Assert '8.createdId' ($privateId -gt 0) "id=$privateId"

    $ownerDetail = Assert-VideoOk '8.ownerDetail' (Send-VideoProbe -Path ("/video/shorts/" + $privateId))
    Assert '8.ownerPlayable' ($ownerDetail.playable -eq $true) "playable=$($ownerDetail.playable)"
    Assert '8.privateVisibility' ($ownerDetail.visibility -eq 'PRIVATE') `
        "visibility=$($ownerDetail.visibility)"
    Assert '8.privateNotCommentable' ($ownerDetail.commentable -eq $false) `
        "commentable=$($ownerDetail.commentable)"

    # 他人视角：详情按不存在处理，feed / 搜索不出现私密作品
    Assert-VideoBizError '8.otherUserDetail' `
        (Send-VideoProbe -Path ("/video/shorts/" + $privateId) -Headers (New-VideoOtherUserHeaders)) 40401
    $otherFeed = Assert-VideoOk '8.otherUserFeed' `
        (Send-VideoProbe -Path '/video/shorts/feed?size=10' -Headers (New-VideoOtherUserHeaders))
    Assert-VideoFeedShape '8.otherUserFeed' $otherFeed 10
    Assert '8.otherFeedHidden' ((-not (Test-VideoFeedContains $otherFeed $privateId))) `
        "私密内容出现在他人 feed: id=$privateId"

    # 我的作品含私密作品；下架后用户侧不可见但仍留在我的作品
    $myShortsAfterCreate = Assert-VideoOk '8.myShorts' (Send-VideoProbe -Path '/video/my/shorts?pageNum=1&pageSize=5')
    Assert '8.myShortsContains' `
        (@($myShortsAfterCreate.records | Where-Object { [long]$_.id -eq $privateId }).Count -ge 1) `
        '我的作品未包含新建私密短视频'
    $null = Assert-VideoOk '8.ownerOffline' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/user/shorts/" + $privateId + '/offline'))
    Assert-VideoBizError '8.offlineOwnerHidden' (Send-VideoProbe -Path ("/video/shorts/" + $privateId)) 40401
    $myShortsAfterOffline = Assert-VideoOk '8.myShortsOffline' `
        (Send-VideoProbe -Path '/video/my/shorts?pageNum=1&pageSize=5')
    Assert '8.myShortsKeepsOffline' `
        (@($myShortsAfterOffline.records | Where-Object { [long]$_.id -eq $privateId }).Count -ge 1) `
        '我的作品应保留已下架作品'

    $null = Assert-VideoOk '8.ownerRepublish' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/user/shorts/" + $privateId + '/publish'))
    $republishedDetail = Assert-VideoOk '8.republishedDetail' `
        (Send-VideoProbe -Path ("/video/shorts/" + $privateId))
    Assert '8.republishedPlayable' ($republishedDetail.playable -eq $true) `
        "playable=$($republishedDetail.playable)"

    $null = Assert-VideoOk '8.delete' `
        (Send-VideoProbe -Method 'Delete' -Path ("/video/user/shorts/" + $privateId))
    Assert-VideoBizError '8.deletedHidden' (Send-VideoProbe -Path ("/video/shorts/" + $privateId)) 40401
    $myShortsAfterDelete = Assert-VideoOk '8.myShortsDeleted' `
        (Send-VideoProbe -Path '/video/my/shorts?pageNum=1&pageSize=5')
    Assert '8.myShortsDeleteGone' `
        ((@($myShortsAfterDelete.records | Where-Object { [long]$_.id -eq $privateId }).Count -eq 0)) `
        '我的作品仍包含已删除作品'
} else {
    Write-Host 'SKIP 8 私密短视频闭环（设置 SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 后重跑可覆盖）'
}

# ---------------------------------------------------------------
# 9 可选：运营下架 → 旧票据失效 → 重新上架（需 SMOKE_VIDEO_CONTENT_ID）
# ---------------------------------------------------------------
Write-Host ''
if ($env:SMOKE_VIDEO_CONTENT_ID) {
    $managedId = [long]$env:SMOKE_VIDEO_CONTENT_ID
    Write-Host "== 9 运营下架闭环 contentId=$managedId =="
    $published = Assert-VideoOk '9.publish' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/admin/content/" + $managedId + '/publish'))
    Assert '9.publishState' ($published.publishState -eq 'PUBLISHED') "state=$($published.publishState)"

    $managedTicket = Assert-VideoOk '9.ticket' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Body @{ contentId = $managedId })
    $before = Send-VideoStreamProbe -Path ([string]$managedTicket.streamUrl) -Range 'bytes=0-1023'
    Assert '9.beforeOfflineRange' ([int]$before.Status -eq 206) "status=$($before.Status)"

    $offline = Assert-VideoOk '9.offline' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/admin/content/" + $managedId + '/offline'))
    Assert '9.offlineState' ($offline.publishState -eq 'OFFLINE') "state=$($offline.publishState)"

    # 下架立即生效：旧票据（未过期）也不能继续读取，详情按不存在处理
    Assert-VideoBizError '9.oldTicketRejected' `
        (Send-VideoProbe -Path ([string]$managedTicket.streamUrl)) 40401
    Assert-VideoBizError '9.detailHidden' (Send-VideoProbe -Path ("/video/shorts/" + $managedId)) 40401
    $feedAfterOffline = Assert-VideoOk '9.feedAfterOffline' (Send-VideoProbe -Path '/video/shorts/feed?size=10')
    Assert '9.feedEvicted' ((-not (Test-VideoFeedContains $feedAfterOffline $managedId))) `
        "下架后 feed 仍含 id=$managedId"

    $republished = Assert-VideoOk '9.republish' `
        (Send-VideoProbe -Method 'Post' -Path ("/video/admin/content/" + $managedId + '/publish'))
    Assert '9.republishState' ($republished.publishState -eq 'PUBLISHED') `
        "state=$($republished.publishState)"
    $newTicket = Assert-VideoOk '9.newTicket' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Body @{ contentId = $managedId })
    $after = Send-VideoStreamProbe -Path ([string]$newTicket.streamUrl) -Range 'bytes=0-1023'
    Assert '9.afterRepublishRange' ([int]$after.Status -eq 206) "status=$($after.Status)"
} else {
    Write-Host 'SKIP 9 运营下架闭环（设置 SMOKE_VIDEO_CONTENT_ID=<可下架内容ID> 后重跑可覆盖）'
}

# ---------------------------------------------------------------
# 10 上传校验拒绝
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 10 上传校验 =='
$declaration = Assert-VideoOk '10.declaration' (Send-VideoProbe -Path '/video/declaration/current')
$null = Assert-VideoOk '10.accept' `
    (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' `
        -Body @{ version = $declaration.version; contentSource = 'ORIGINAL' })

$createBody = @{
    title         = 'M2 冒烟短视频'
    fileId        = 999999999
    durationMs    = 1000
    contentSource = 'ORIGINAL'
}
Assert-VideoBizError '10.createUnknownFile' `
    (Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Body $createBody) 40006
# 缺少 fileId 先被 @NotNull 拦截，全局异常处理器返回 400（参数错误）
Assert-VideoBizError '10.createMissingFile' `
    (Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Body @{ title = 'M2 冒烟短视频' }) 400
# 未知内容 ID 的点赞按不存在处理，不创建脏数据
Assert-VideoBizError '10.likeUnknownContent' `
    (Send-VideoProbe -Method 'Post' -Path '/video/content/999999999/like') 40401

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
