# Wave 5.77 M4 电影 / 发现 / 运营冒烟（强断言）
#   覆盖：服务就绪 / meta 契约 / 权限拒绝（基线）；
#         电影列表 / 详情 / 榜单（热门 + 新片、类型过滤、条数上限、非法类型拒绝）；
#         片单分页 / 详情（草稿 40405、封面 404 语义）、跨类型搜索命中电影；
#         电影运营闭环（入库 → 授权校验 → 发布 → 用户可见 → 片单加片 → 下架 →
#         重新授权再发布 → 版权下架 → 删除）；
#         加片幂等、非电影加片拒绝、场次分页只返回已发布电影。
#   可选：SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 才执行电影入库闭环
#         （电影必须登记真实视频资产，缺省时跳过并在末尾输出 SKIP）。
#   可选：SMOKE_VIDEO_CONTENT_ID=<已发布视频/短视频内容ID> 用于「非电影加片」反例。
# 说明：授权到期自动下线由定时任务与单测覆盖，冒烟不等待任务窗口；
#       入库时授权已过期 / 来源非法分别断言 40011。
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 就绪，
#       bluewhale_video 已执行 V1__baseline.sql / V2__series.sql / V3__movie_collection.sql，
#       auth 已执行 migration-5.77-video.sql（305–322 权限）。
# 用法：powershell -File scripts/smoke/bw-video-m4-smoke.ps1
#   VIDEO_BASE / FILE_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID / VIDEO_PERMISSION_CODES
#   / SMOKE_VIDEO_FILE_ID / SMOKE_VIDEO_CONTENT_ID 可覆盖。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
生成本次运行唯一标识，隔离冒烟创建的电影与片单。
#>
function New-VideoRunToken {
    return [Guid]::NewGuid().ToString('N').Substring(0, 8)
}

<#
.SYNOPSIS
生成后端可解析的 ISO 日期时间（yyyy-MM-ddTHH:mm:ss）。
.PARAMETER AddMinutes
相对当前时间的偏移分钟数（负数表示过去）。
#>
function Get-VideoIsoDateTime {
    param([int]$AddMinutes = 0)

    return ([DateTime]::Now.AddMinutes($AddMinutes)).ToString('yyyy-MM-ddTHH:mm:ss')
}

<#
.SYNOPSIS
生成后端可解析的日期（yyyy-MM-dd）。
.PARAMETER AddDays
相对当前时间的偏移天数（负数表示过去）。
#>
function Get-VideoIsoDate {
    param([int]$AddDays = 0)

    return ([DateTime]::Now.AddDays($AddDays)).ToString('yyyy-MM-dd')
}

<#
.SYNOPSIS
判断分页结果中是否包含指定 ID。
.PARAMETER Page
Result.data（PageResult）。
.PARAMETER Id
目标 ID。
#>
function Test-VideoPageContainsId {
    param([object]$Page, [long]$Id)

    if ($null -eq $Page -or $null -eq $Page.records) {
        return $false
    }
    foreach ($record in @($Page.records)) {
        if ($null -ne $record -and [long]$record.id -eq $Id) {
            return $true
        }
    }
    return $false
}

<#
.SYNOPSIS
判断列表中是否包含指定内容 ID。
.PARAMETER Items
视图数组。
.PARAMETER ContentId
目标内容 ID。
#>
function Test-VideoListContains {
    param([object[]]$Items, [long]$ContentId)

    foreach ($item in $Items) {
        if ($null -ne $item -and [long]$item.id -eq $ContentId) {
            return $true
        }
    }
    return $false
}

<#
.SYNOPSIS
断言用户侧电影分页形状：PageResult + 仅电影 + 不暴露文件 ID + 含电影元数据字段。
.PARAMETER Label
断言标签。
.PARAMETER Page
Result.data。
.PARAMETER ExpectSize
期望每页条数，默认 5。
#>
function Assert-VideoMoviePage {
    param([string]$Label, [object]$Page, [int]$ExpectSize = 5)

    Assert-VideoPageShape $Label $Page 1 $ExpectSize
    Assert-VideoNoFileId $Label (@($Page.records))
    foreach ($record in @($Page.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert "$Label.type" ($record.contentType -eq 'MOVIE') "type=$($record.contentType)"
        $names = @($record.PSObject.Properties.Name)
        Assert "$Label.posterField" ($names -contains 'posterUrl') '缺少 posterUrl 字段'
        Assert "$Label.ratingField" ($names -contains 'rating') '缺少 rating 字段'
        Assert "$Label.playableField" ($names -contains 'playable') '缺少 playable 字段'
    }
}

<#
.SYNOPSIS
断言用户侧电影详情形状：电影元数据 + 收录片单 + 不暴露文件 ID。
.PARAMETER Label
断言标签。
.PARAMETER Detail
Result.data（MovieDetailView）。
.PARAMETER ExpectId
期望内容 ID。
#>
function Assert-VideoMovieDetailShape {
    param([string]$Label, [object]$Detail, [long]$ExpectId)

    Assert "$Label.id" ([long]$Detail.id -eq $ExpectId) "expect=$ExpectId actual=$($Detail.id)"
    Assert "$Label.type" ($Detail.contentType -eq 'MOVIE') "type=$($Detail.contentType)"
    Assert "$Label.ticket" ([string]$Detail.ticketUrl -eq '/video/v1/play/ticket') `
        "ticketUrl=$($Detail.ticketUrl)"
    $names = @($Detail.PSObject.Properties.Name)
    Assert "$Label.collections" ($names -contains 'collections') '详情缺少 collections'
    Assert "$Label.originalTitle" ($names -contains 'originalTitle') '详情缺少 originalTitle'
    Assert "$Label.posterUrl" ($names -contains 'posterUrl') '详情缺少 posterUrl'
    Assert "$Label.noFileId" `
        ((@($names | Where-Object { $_ -match 'fileId' }).Count) -eq 0) '详情泄露文件 ID'
}

<#
.SYNOPSIS
断言用户侧片单分页形状：PageResult + 仅已上架 + 不暴露文件 ID。
.PARAMETER Label
断言标签。
.PARAMETER Page
Result.data。
.PARAMETER ExpectSize
期望每页条数，默认 5。
#>
function Assert-VideoCollectionPage {
    param([string]$Label, [object]$Page, [int]$ExpectSize = 5)

    Assert-VideoPageShape $Label $Page 1 $ExpectSize
    Assert-VideoNoFileId $Label (@($Page.records))
    foreach ($record in @($Page.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert "$Label.id" ([long]$record.id -gt 0) "id=$($record.id)"
        Assert "$Label.publishedOnly" ($record.publishState -eq 'PUBLISHED') `
            "state=$($record.publishState)"
        Assert "$Label.itemCount" ([int]$record.itemCount -ge 0) `
            "itemCount=$($record.itemCount)"
    }
}

<#
.SYNOPSIS
断言片单详情形状：条目字段存在且不暴露文件 ID。
.PARAMETER Label
断言标签。
.PARAMETER Detail
Result.data（MovieCollectionDetailView）。
.PARAMETER ExpectItems
期望条目数。
#>
function Assert-VideoCollectionDetail {
    param([string]$Label, [object]$Detail, [int]$ExpectItems)

    Assert "$Label.id" ($null -ne $Detail.id -and [long]$Detail.id -gt 0) "id=$($Detail.id)"
    Assert "$Label.publishedOnly" ($Detail.publishState -eq 'PUBLISHED') `
        "state=$($Detail.publishState)"
    Assert "$Label.items" ($null -ne $Detail.items) 'items 缺失'
    $count = @($Detail.items).Count
    Assert "$Label.itemCount" ($count -eq $ExpectItems) "expect=$ExpectItems actual=$count"
    Assert-VideoNoFileId "$Label" (@($Detail.items))
}

Write-Host "== M4 电影 / 发现 / 运营冒烟 用户=$(Get-VideoUserId) 租户=$(Get-VideoTenantId) =="
Assert-VideoBaseline -Milestone 'm4' -IncludeFile

# ---------------------------------------------------------------
# 3 用户侧电影 / 片单 / 榜单 / 跨类型搜索
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 3 用户侧电影 / 片单 / 榜单 / 搜索 =='
$moviePage = Assert-VideoOk '3.moviePage' `
    (Send-VideoProbe -Path '/video/v1/movies/page?pageNum=1&pageSize=5')
Assert-VideoMoviePage '3.moviePage' $moviePage 5

$collectionPage = Assert-VideoOk '3.collectionPage' `
    (Send-VideoProbe -Path '/video/v1/collections/page?pageNum=1&pageSize=5')
Assert-VideoCollectionPage '3.collectionPage' $collectionPage 5

# 跨类型搜索：空白关键词返回稳定空分页
$emptySearch = Assert-VideoOk '3.searchEmpty' `
    (Send-VideoProbe -Path '/video/v1/search?keyword=&contentType=MOVIE&pageNum=1&pageSize=5')
Assert '3.searchEmptyTotal' ([long]$emptySearch.total -eq 0) "total=$($emptySearch.total)"

$rankings = Assert-VideoOk '3.rankings' (Send-VideoProbe -Path '/video/v1/rankings?type=MOVIE&size=10')
Assert '3.rankingsHot' ($null -ne $rankings.hot) 'hot 缺失'
Assert '3.rankingsLatest' ($null -ne $rankings.latest) 'latest 缺失'
Assert '3.rankingsHotSize' (@($rankings.hot).Count -le 10) "count=$(@($rankings.hot).Count)"
Assert '3.rankingsLatestSize' (@($rankings.latest).Count -le 10) "count=$(@($rankings.latest).Count)"
Assert '3.rankingsType' ($rankings.contentType -eq 'MOVIE') "type=$($rankings.contentType)"
Assert-VideoNoFileId '3.rankingsHot' (@($rankings.hot))
Assert-VideoNoFileId '3.rankingsLatest' (@($rankings.latest))
foreach ($record in @($rankings.hot)) {
    if ($null -eq $record) {
        continue
    }
    Assert '3.rankingsHotType' ($record.contentType -eq 'MOVIE') "type=$($record.contentType)"
}

# 榜单条数上限 50；非法类型按参数错误拒绝
$bigRankings = Assert-VideoOk '3.rankingsClamp' `
    (Send-VideoProbe -Path '/video/v1/rankings?size=100')
Assert '3.rankingsClampHot' (@($bigRankings.hot).Count -le 50) `
    "count=$(@($bigRankings.hot).Count)"
Assert '3.rankingsClampLatest' (@($bigRankings.latest).Count -le 50) `
    "count=$(@($bigRankings.latest).Count)"
Assert-VideoBizError '3.rankingsInvalidType' `
    (Send-VideoProbe -Path '/video/v1/rankings?type=unknown&size=5') 400

# 浏览 / 运营权限隔离与不存在语义
Assert-VideoBizError '3.moviePageDenied' `
    (Send-VideoProbe -Path '/video/v1/movies/page' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:like')) 403
Assert-VideoBizError '3.rankingsDenied' `
    (Send-VideoProbe -Path '/video/v1/rankings' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:like')) 403
Assert-VideoBizError '3.movieUnknown' (Send-VideoProbe -Path '/video/v1/movies/999999999') 40401
Assert-VideoBizError '3.collectionUnknown' `
    (Send-VideoProbe -Path '/video/v1/collections/999999999') 40405
Assert-VideoBizError '3.adminMoviePageDenied' `
    (Send-VideoProbe -Path '/video/v1/admin/movies/page' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:browse')) 403

# ---------------------------------------------------------------
# 4 电影运营闭环（需 SMOKE_VIDEO_FILE_ID）
# ---------------------------------------------------------------
Write-Host ''
if ($env:SMOKE_VIDEO_FILE_ID) {
    $movieFileId = [long]$env:SMOKE_VIDEO_FILE_ID
    $movieToken = 'M4M' + (New-VideoRunToken)
    Write-Host "== 4 电影运营闭环 fileId=$movieFileId =="

    $createBody = @{
        title           = 'M4 冒烟电影 ' + $movieToken
        subtitle        = 'M4 冒烟副标题'
        description     = 'M4 冒烟电影简介'
        videoFileId     = $movieFileId
        durationMs      = 1000
        licenseSource   = 'LICENSED'
        licenseNo       = 'L-M4-' + $movieToken
        licenseExpireAt = (Get-VideoIsoDateTime -AddMinutes 43200)
        originalTitle   = 'M4 Smoke Movie'
        director        = '冒烟导演'
        actors          = '冒烟主演甲,冒烟主演乙'
        region          = '中国大陆'
        language        = '普通话'
        releaseDate     = (Get-VideoIsoDate -AddDays -30)
        rating          = 8.2
    }
    $draftMovie = Assert-VideoOk '4.create' `
        (Send-VideoProbe -Method 'Post' -Path '/video/v1/admin/movies' -Body $createBody)
    $movieId = [long]$draftMovie.id
    Assert '4.createId' ($movieId -gt 0) "id=$movieId"
    Assert '4.createState' ($draftMovie.publishState -eq 'DRAFT') `
        "state=$($draftMovie.publishState)"
    Assert '4.createLicenseSource' ($draftMovie.licenseSource -eq 'LICENSED') `
        "source=$($draftMovie.licenseSource)"
    Assert '4.createLicenseNo' ([string]$draftMovie.licenseNo -eq $createBody.licenseNo) `
        "licenseNo=$($draftMovie.licenseNo)"
    Assert '4.createLicenseExpire' ($null -ne $draftMovie.licenseExpireAt) `
        'licenseExpireAt 为空'
    Assert '4.createPoster' ([string]::IsNullOrWhiteSpace([string]$draftMovie.posterUrl)) `
        "posterUrl=$($draftMovie.posterUrl)"
    Assert '4.createRating' ([decimal]$draftMovie.rating -eq [decimal]8.2) `
        "rating=$($draftMovie.rating)"

    # 入库校验：授权来源非法 / 到期时间已过一律 40011
    $invalidSourceBody = $createBody.Clone()
    $invalidSourceBody['title'] = 'M4 冒烟电影 ' + $movieToken + ' X'
    $invalidSourceBody['licenseSource'] = 'UNKNOWN'
    Assert-VideoBizError '4.createInvalidSource' `
        (Send-VideoProbe -Method 'Post' -Path '/video/v1/admin/movies' -Body $invalidSourceBody) 40011
    $expiredBody = $createBody.Clone()
    $expiredBody['title'] = 'M4 冒烟电影 ' + $movieToken + ' E'
    $expiredBody['licenseExpireAt'] = (Get-VideoIsoDateTime -AddMinutes -60)
    Assert-VideoBizError '4.createExpiredLicense' `
        (Send-VideoProbe -Method 'Post' -Path '/video/v1/admin/movies' -Body $expiredBody) 40011

    $adminMoviePage = Assert-VideoOk '4.adminPage' `
        (Send-VideoProbe -Path ('/video/v1/admin/movies/page?keyword=' + $movieToken + '&size=10'))
    Assert-VideoPageShape '4.adminPage' $adminMoviePage 1 10
    Assert '4.adminPageContains' (Test-VideoPageContainsId $adminMoviePage $movieId) `
        '运营电影分页未包含本次草稿'

    # 草稿：用户侧列表 / 详情 / 搜索均不可见
    $draftHiddenPage = Assert-VideoOk '4.draftPageHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/page?keyword=' + $movieToken + '&pageNum=1&pageSize=10'))
    Assert-VideoMoviePage '4.draftPageHidden' $draftHiddenPage 10
    Assert '4.draftPageHiddenGone' (-not (Test-VideoPageContainsId $draftHiddenPage $movieId)) `
        '用户侧电影列表包含草稿'
    Assert-VideoBizError '4.draftDetailHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId)) 40401

    # 发布：授权有效即可上架
    $publishedMovie = Assert-VideoOk '4.publish' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/movies/' + $movieId + '/publish'))
    Assert '4.publishState' ($publishedMovie.publishState -eq 'PUBLISHED') `
        "state=$($publishedMovie.publishState)"
    Assert '4.publishAt' ($null -ne $publishedMovie.publishedAt) 'publishedAt 为空'

    $visiblePage = Assert-VideoOk '4.visiblePage' `
        (Send-VideoProbe -Path ('/video/v1/movies/page?keyword=' + $movieToken + '&pageNum=1&pageSize=10'))
    Assert-VideoMoviePage '4.visiblePage' $visiblePage 10
    Assert '4.visiblePageContains' (Test-VideoPageContainsId $visiblePage $movieId) `
        '用户侧电影列表未包含已发布电影'

    $movieDetail = Assert-VideoOk '4.detail' (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId))
    Assert-VideoMovieDetailShape '4.detail' $movieDetail $movieId
    Assert '4.detailPlayable' ($movieDetail.playable -eq $true) "playable=$($movieDetail.playable)"
    Assert '4.detailRating' ([decimal]$movieDetail.rating -eq [decimal]8.2) `
        "rating=$($movieDetail.rating)"
    Assert '4.detailNoPoster' ([string]::IsNullOrWhiteSpace([string]$movieDetail.posterUrl)) `
        "posterUrl=$($movieDetail.posterUrl)"
    Assert '4.detailCollectionsEmpty' (@($movieDetail.collections).Count -eq 0) `
        "count=$(@($movieDetail.collections).Count)"

    # 未设置海报：代理返回 404，不暴露文件服务内部错误
    Assert-VideoBizError '4.posterNoFile' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId + '/poster')) 404

    # 跨类型搜索命中已发布电影
    $searchPage = Assert-VideoOk '4.searchHit' `
        (Send-VideoProbe -Path ('/video/v1/search?keyword=' + $movieToken + '&pageNum=1&pageSize=10'))
    Assert-VideoPageShape '4.searchHit' $searchPage 1 10
    Assert '4.searchHitContains' (Test-VideoListContains @($searchPage.records) $movieId) `
        "搜索未命中 id=$movieId"

    # -----------------------------------------------------------
    # 5 片单闭环（草稿 → 加片幂等 → 上架 → 用户可见 → 移除）
    # -----------------------------------------------------------
    Write-Host ''
    Write-Host '== 5 片单闭环 =='
    $collectionToken = 'M4C' + (New-VideoRunToken)
    $draftCollection = Assert-VideoOk '5.create' `
        (Send-VideoProbe -Method 'Post' -Path '/video/v1/admin/collections' `
            -Body @{ title = 'M4 冒烟片单 ' + $collectionToken; description = 'M4 冒烟片单简介'; sortNo = 3 })
    $collectionId = [long]$draftCollection.id
    Assert '5.createId' ($collectionId -gt 0) "id=$collectionId"
    Assert '5.createState' ($draftCollection.publishState -eq 'DRAFT') `
        "state=$($draftCollection.publishState)"
    Assert '5.createItemCount' ([int]$draftCollection.itemCount -eq 0) `
        "itemCount=$($draftCollection.itemCount)"

    $collectionAdminPage = Assert-VideoOk '5.adminPage' `
        (Send-VideoProbe -Path ('/video/v1/admin/collections/page?keyword=' + $collectionToken + '&size=10'))
    Assert-VideoPageShape '5.adminPage' $collectionAdminPage 1 10
    Assert '5.adminPageContains' (Test-VideoPageContainsId $collectionAdminPage $collectionId) `
        '运营片单分页未包含本次草稿'

    $added = Assert-VideoOk '5.addItem' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/collections/' + $collectionId + '/item') `
            -Body @{ contentId = $movieId })
    Assert '5.addItemCount' ([int]$added.itemCount -eq 1) "itemCount=$($added.itemCount)"
    Assert '5.addItemContains' (Test-VideoListContains @($added.items) $movieId) `
        '片单详情未包含本次电影'

    # 重复加片幂等：条目数不增长
    $addedAgain = Assert-VideoOk '5.addItemAgain' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/collections/' + $collectionId + '/item') `
            -Body @{ contentId = $movieId })
    Assert '5.addItemAgainCount' ([int]$addedAgain.itemCount -eq 1) `
        "itemCount=$($addedAgain.itemCount)"
    Assert '5.addItemAgainItems' (@($addedAgain.items).Count -eq 1) `
        "count=$(@($addedAgain.items).Count)"

    # 非电影内容不允许加片
    if ($env:SMOKE_VIDEO_CONTENT_ID) {
        $nonMovieId = [long]$env:SMOKE_VIDEO_CONTENT_ID
        Assert-VideoBizError '5.addNonMovie' `
            (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/collections/' + $collectionId + '/item') `
                -Body @{ contentId = $nonMovieId }) 40401
    } else {
        Write-Host 'SKIP 5.addNonMovie（设置 SMOKE_VIDEO_CONTENT_ID 覆盖非电影加片反例）'
    }

    # 草稿片单：用户侧详情按不存在处理
    Assert-VideoBizError '5.draftDetailHidden' `
        (Send-VideoProbe -Path ('/video/v1/collections/' + $collectionId)) 40405

    # 上架片单：用户侧分页与详情立即可见，片单内电影按内容可见性过滤
    $null = Assert-VideoOk '5.publish' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/collections/' + $collectionId + '/publish'))
    $visibleCollectionPage = Assert-VideoOk '5.visiblePage' `
        (Send-VideoProbe -Path ('/video/v1/collections/page?keyword=' + $collectionToken + '&pageNum=1&pageSize=10'))
    Assert-VideoCollectionPage '5.visiblePage' $visibleCollectionPage 10
    Assert '5.visiblePageContains' (Test-VideoPageContainsId $visibleCollectionPage $collectionId) `
        '用户侧片单分页未包含已上架片单'
    $collectionDetail = Assert-VideoOk '5.detail' `
        (Send-VideoProbe -Path ('/video/v1/collections/' + $collectionId))
    Assert-VideoCollectionDetail '5.detail' $collectionDetail 1
    Assert '5.detailContains' (Test-VideoListContains @($collectionDetail.items) $movieId) `
        '用户侧片单详情未包含电影'

    # 电影详情收录片单回填
    $detailWithCollection = Assert-VideoOk '5.movieDetailCollections' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId))
    Assert '5.movieDetailCollectionHit' (Test-VideoListContains @($detailWithCollection.collections) $collectionId) `
        '电影详情未收录该片单'

    # -----------------------------------------------------------
    # 6 下架 / 重新授权 / 版权下架 / 删除
    # -----------------------------------------------------------
    Write-Host ''
    Write-Host '== 6 下架 / 重新授权 / 版权下架 / 删除 =='
    $null = Assert-VideoOk '6.offline' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/movies/' + $movieId + '/offline'))
    Assert-VideoBizError '6.offlineDetailHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId)) 40401
    $offlinePage = Assert-VideoOk '6.offlinePageHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/page?keyword=' + $movieToken + '&pageNum=1&pageSize=10'))
    Assert '6.offlinePageGone' (-not (Test-VideoPageContainsId $offlinePage $movieId)) `
        '下架后用户侧电影列表仍包含该电影'
    $collectionAfterOffline = Assert-VideoOk '6.collectionAfterOffline' `
        (Send-VideoProbe -Path ('/video/v1/collections/' + $collectionId))
    Assert-VideoCollectionDetail '6.collectionAfterOffline' $collectionAfterOffline 0

    # 重新授权：到期时间改为过去 → 40011；改为未来 → 可再次发布
    $expiredUpdateBody = $createBody.Clone()
    $expiredUpdateBody['licenseExpireAt'] = (Get-VideoIsoDateTime -AddMinutes -60)
    Assert-VideoBizError '6.updateExpiredLicense' `
        (Send-VideoProbe -Method 'Put' -Path ('/video/v1/admin/movies/' + $movieId) `
            -Body $expiredUpdateBody) 40011

    $renewedUpdateBody = $createBody.Clone()
    $renewedUpdateBody['licenseExpireAt'] = (Get-VideoIsoDateTime -AddMinutes 43200)
    $renewed = Assert-VideoOk '6.updateRenewed' `
        (Send-VideoProbe -Method 'Put' -Path ('/video/v1/admin/movies/' + $movieId) `
            -Body $renewedUpdateBody)
    Assert '6.updateRenewedState' ($renewed.publishState -eq 'OFFLINE') `
        "state=$($renewed.publishState)"
    $republished = Assert-VideoOk '6.republish' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/movies/' + $movieId + '/publish'))
    Assert '6.republishState' ($republished.publishState -eq 'PUBLISHED') `
        "state=$($republished.publishState)"

    # 版权下架：原因为空按参数错误拒绝；填写原因后立即对用户不可见
    Assert-VideoBizError '6.takedownBlankReason' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/movies/' + $movieId + '/takedown') `
            -Body @{ reason = '' }) 400
    $takedown = Assert-VideoOk '6.takedown' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/v1/admin/movies/' + $movieId + '/takedown') `
            -Body @{ reason = 'M4 冒烟版权下架' })
    Assert '6.takedownCopyright' ($takedown.copyrightState -eq 'TAKEDOWN') `
        "copyright=$($takedown.copyrightState)"
    Assert-VideoBizError '6.takedownDetailHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId)) 40401
    Assert-VideoBizError '6.takedownPosterHidden' `
        (Send-VideoProbe -Path ('/video/v1/movies/' + $movieId + '/poster')) 40401

    # 清理：先移除片单内电影并删除片单，再删除电影
    $removed = Assert-VideoOk '6.removeItem' `
        (Send-VideoProbe -Method 'Delete' `
            -Path ('/video/v1/admin/collections/' + $collectionId + '/item/' + $movieId))
    Assert '6.removeItemCount' ([int]$removed.itemCount -eq 0) "itemCount=$($removed.itemCount)"
    $null = Assert-VideoOk '6.deleteCollection' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/v1/admin/collections/' + $collectionId))
    Assert-VideoBizError '6.deletedCollectionHidden' `
        (Send-VideoProbe -Path ('/video/v1/collections/' + $collectionId)) 40405
    $null = Assert-VideoOk '6.deleteMovie' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/v1/admin/movies/' + $movieId))
    $adminAfterDelete = Assert-VideoOk '6.adminAfterDelete' `
        (Send-VideoProbe -Path ('/video/v1/admin/movies/page?keyword=' + $movieToken + '&size=10'))
    Assert '6.adminAfterDeleteGone' (-not (Test-VideoPageContainsId $adminAfterDelete $movieId)) `
        '删除后运营电影分页仍包含该电影'

    # 电影运营权限隔离：缺少 content:add 时入库拒绝
    Assert-VideoBizError '6.createDenied' `
        (Send-VideoProbe -Method 'Post' -Path '/video/v1/admin/movies' -Body $createBody `
            -Headers (New-VideoHeaders -PermissionCodes 'sys:video:browse')) 403
} else {
    Write-Host 'SKIP 4-6 电影 / 片单运营闭环（设置 SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 后重跑可覆盖）'
}

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
