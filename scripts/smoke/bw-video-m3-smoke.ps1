# Wave 5.77 M3 视频 / 系列闭环冒烟（强断言）
#   覆盖：服务就绪 / meta 契约 / 权限拒绝（基线）；
#         视频列表 / 关键词搜索 / 视频详情 / 系列筛选 / 运营内容分页；
#         断点续播进度上报（合法进度幂等、超时长拒绝、最近观看回填、删除记录）；
#         系列运营闭环（草稿 → 编辑 → 上架 → 用户可见 → 下架 → 重新上架 → 删除）；
#         系列分页只返回已上架系列（忽略 publishState 过滤）、封面 404 语义；
#         系列排序（sortNo 升序、同序按 ID 倒序）；
#         发布 / 下架后用户侧视频列表与首页缓存即时失效。
#   可选：SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 追加系列加集闭环：
#         集数唯一 40902、集数非法 40013、系列不存在 40404、同一内容重复提交幂等、
#         私密集数不进系列页、集数计数随上下架 / 删除刷新、有集数系列删除拒绝 40901。
#   可选：SMOKE_VIDEO_CONTENT_ID=<已发布视频内容ID> 指定视频详情 / 进度断言对象
#         （缺省取视频列表首条；无已发布视频时跳过）。
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 就绪，
#       bluewhale_video 已执行 V1__baseline.sql，auth 已执行 migration-5.77-video.sql
#       （314 权限需包含 /video/series/page 与 /video/series/{id}）。
# 说明：脚本直连服务，使用与网关注入一致的身份头
#       （X-User-Id / X-Tenant-Id / X-Permission-Codes），不经过网关鉴权链路。
# 用法：powershell -File scripts/smoke/bw-video-m3-smoke.ps1
#   VIDEO_BASE / FILE_BASE / SMOKE_USER_ID / SMOKE_TENANT_ID / VIDEO_PERMISSION_CODES
#   / SMOKE_VIDEO_FILE_ID / SMOKE_VIDEO_CONTENT_ID 可覆盖。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

<#
.SYNOPSIS
构造「同租户其他普通用户」的身份头，用于私密内容 / 跨用户断言。
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
生成本次运行唯一标识，隔离冒烟创建的系列 / 内容，避免与历史数据混淆。
#>
function New-VideoRunToken {
    return [Guid]::NewGuid().ToString('N').Substring(0, 8)
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
断言视频列表分页形状：PageResult + 不含文件 ID + 类型与系列字段契约。
.PARAMETER Label
断言标签。
.PARAMETER Page
Result.data。
.PARAMETER ExpectSize
期望每页条数，默认 5。
#>
function Assert-VideoVideoPage {
    param([string]$Label, [object]$Page, [int]$ExpectSize = 5)

    Assert-VideoPageShape $Label $Page 1 $ExpectSize
    Assert-VideoNoFileId $Label (@($Page.records))
    foreach ($record in @($Page.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert "$Label.type" ($record.contentType -eq 'VIDEO') "type=$($record.contentType)"
        $names = @($record.PSObject.Properties.Name)
        Assert "$Label.seriesField" ($names -contains 'seriesId') '缺少 seriesId 字段'
        Assert "$Label.episodeField" ($names -contains 'episodeNo') '缺少 episodeNo 字段'
    }
}

<#
.SYNOPSIS
断言用户侧系列分页形状：只返回已上架视频系列，且不暴露文件 ID。
.PARAMETER Label
断言标签。
.PARAMETER Page
Result.data。
.PARAMETER ExpectSize
期望每页条数，默认 5。
#>
function Assert-VideoSeriesPage {
    param([string]$Label, [object]$Page, [int]$ExpectSize = 5)

    Assert-VideoPageShape $Label $Page 1 $ExpectSize
    Assert-VideoNoFileId $Label (@($Page.records))
    foreach ($record in @($Page.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert "$Label.id" ([long]$record.id -gt 0) "id=$($record.id)"
        Assert "$Label.type" ($record.contentType -eq 'VIDEO') "type=$($record.contentType)"
        Assert "$Label.publishedOnly" ($record.publishState -eq 'PUBLISHED') `
            "state=$($record.publishState)"
        Assert "$Label.episodeCount" ([int]$record.episodeCount -ge 0) `
            "episodeCount=$($record.episodeCount)"
    }
}

<#
.SYNOPSIS
断言用户侧系列详情形状：集数列表非空属性、episodeCount 与实际集数一致。
.PARAMETER Label
断言标签。
.PARAMETER Detail
Result.data（SeriesDetailView）。
#>
function Assert-VideoSeriesDetailShape {
    param([string]$Label, [object]$Detail)

    Assert "$Label.id" ($null -ne $Detail.id -and [long]$Detail.id -gt 0) "id=$($Detail.id)"
    Assert "$Label.type" ($Detail.contentType -eq 'VIDEO') "type=$($Detail.contentType)"
    Assert "$Label.state" ($Detail.publishState -eq 'PUBLISHED') "state=$($Detail.publishState)"
    Assert "$Label.episodes" ($null -ne $Detail.episodes) 'episodes 缺失'
    $count = @($Detail.episodes).Count
    Assert "$Label.episodeCount" ([int]$Detail.episodeCount -eq $count) `
        "episodeCount=$($Detail.episodeCount) actual=$count"
    Assert-VideoNoFileId "$Label" (@($Detail.episodes))
}

Write-Host "== M3 视频 / 系列闭环冒烟 用户=$(Get-VideoUserId) 租户=$(Get-VideoTenantId) =="
Assert-VideoBaseline -Milestone 'm3' -IncludeFile

# ---------------------------------------------------------------
# 3 视频列表 / 搜索 / 详情 / 进度
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 3 视频列表 / 搜索 / 详情 =='
$videoPage = Assert-VideoOk '3.videoPage' `
    (Send-VideoProbe -Path '/video/videos/page?pageNum=1&pageSize=5')
Assert-VideoVideoPage '3.videoPage' $videoPage 5

# 空关键词返回稳定空分页（不查库、不报错）
$emptySearch = Assert-VideoOk '3.searchEmpty' `
    (Send-VideoProbe -Path '/video/search?keyword=&pageNum=1&pageSize=5')
Assert '3.searchEmptyTotal' ([long]$emptySearch.total -eq 0) "total=$($emptySearch.total)"

$adminContentPage = Assert-VideoOk '3.adminPage' `
    (Send-VideoProbe -Path '/video/admin/content/page?pageNum=1&pageSize=5')
Assert-VideoPageShape '3.adminPage' $adminContentPage 1 5

# 浏览 / 运营权限隔离：缺少对应权限码一律 403
Assert-VideoBizError '3.videoPageDenied' `
    (Send-VideoProbe -Path '/video/videos/page' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:like')) 403
Assert-VideoBizError '3.seriesPageDenied' `
    (Send-VideoProbe -Path '/video/series/page' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:like')) 403
Assert-VideoBizError '3.adminPageDenied' `
    (Send-VideoProbe -Path '/video/admin/content/page' `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:browse')) 403
Assert-VideoBizError '3.videoUnknown' `
    (Send-VideoProbe -Path '/video/videos/999999999') 40401

$videoId = 0L
$videoFromPage = $false
if ($env:SMOKE_VIDEO_CONTENT_ID) {
    $videoId = [long]$env:SMOKE_VIDEO_CONTENT_ID
}
if ($videoId -le 0) {
    foreach ($record in @($videoPage.records)) {
        if ($null -ne $record -and [long]$record.id -gt 0) {
            $videoId = [long]$record.id
            $videoFromPage = $true
            break
        }
    }
}

if ($videoId -gt 0) {
    Write-Host "== 3 视频详情 / 进度 contentId=$videoId =="
    $firstVideo = $null
    foreach ($record in @($videoPage.records)) {
        if ($null -ne $record -and [long]$record.id -eq $videoId) {
            $firstVideo = $record
            break
        }
    }

    # 关键词搜索命中标题；系列筛选只返回同系列视频
    if ($null -ne $firstVideo) {
        $keyword = [string]$firstVideo.title
        $searchPage = Assert-VideoOk '3.searchTitle' `
            (Send-VideoProbe -Path ('/video/search?keyword=' + [uri]::EscapeDataString($keyword) +
                '&pageNum=1&pageSize=5'))
        Assert-VideoPageShape '3.searchTitle' $searchPage 1 5
        Assert '3.searchTitleHit' (Test-VideoListContains @($searchPage.records) $videoId) `
            "搜索未命中 id=$videoId keyword=$keyword"

        $seriesFilterId = [long]$firstVideo.seriesId
        if ($seriesFilterId -gt 0) {
            $seriesVideoPage = Assert-VideoOk '3.seriesFilter' `
                (Send-VideoProbe -Path ('/video/videos/page?seriesId=' + $seriesFilterId +
                    '&pageNum=1&pageSize=5'))
            Assert-VideoPageShape '3.seriesFilter' $seriesVideoPage 1 5
            Assert '3.seriesFilterHit' (Test-VideoListContains @($seriesVideoPage.records) $videoId) `
                "系列筛选未命中 id=$videoId seriesId=$seriesFilterId"
            foreach ($record in @($seriesVideoPage.records)) {
                if ($null -eq $record) {
                    continue
                }
                Assert '3.seriesFilterMatch' ([long]$record.seriesId -eq $seriesFilterId) `
                    "expect=$seriesFilterId actual=$($record.seriesId)"
            }
        }
    }

    $detail = Assert-VideoOk '3.videoDetail' (Send-VideoProbe -Path ('/video/videos/' + $videoId))
    Assert '3.videoDetailId' ([long]$detail.id -eq $videoId) "id=$($detail.id)"
    Assert '3.videoDetailTicket' ([string]$detail.ticketUrl -eq '/video/play/ticket') `
        "ticketUrl=$($detail.ticketUrl)"
    $detailNames = @($detail.PSObject.Properties.Name)
    Assert '3.videoDetailSeries' ($detailNames -contains 'seriesId') '详情缺少 seriesId'
    Assert '3.videoDetailEpisode' ($detailNames -contains 'episodeNo') '详情缺少 episodeNo'
    Assert '3.videoDetailNoFileId' `
        ((@($detailNames | Where-Object { $_ -match 'fileId' }).Count) -eq 0) '详情泄露文件 ID'
    if ($videoFromPage) {
        Assert '3.videoDetailType' ($detail.contentType -eq 'VIDEO') "type=$($detail.contentType)"
        Assert '3.videoDetailPlayable' ($detail.playable -eq $true) `
            "playable=$($detail.playable)"
    }

    # 断点续播：合法进度重复上报幂等，最近观看回填同一进度
    $duration = [long]$detail.durationMs
    $progressMs = 1000L
    if ($duration -gt 0 -and $progressMs -gt $duration) {
        $progressMs = [long]([math]::Floor($duration / 2))
    }
    $progressBody = @{ contentId = $videoId; progressMs = $progressMs; source = 'SERIES'; device = 'WEB' }
    $null = Assert-VideoOk '3.progress' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Body $progressBody)
    $null = Assert-VideoOk '3.progressAgain' `
        (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' -Body $progressBody)
    $history = Assert-VideoOk '3.history' (Send-VideoProbe -Path '/video/history?pageNum=1&pageSize=5')
    Assert-VideoPageShape '3.history' $history 1 5
    $historyRecord = @($history.records | Where-Object { $null -ne $_ -and [long]$_.contentId -eq $videoId })
    Assert '3.historyContains' ($historyRecord.Count -ge 1) '最近观看未包含本次视频'
    if ($historyRecord.Count -ge 1) {
        Assert '3.historyProgress' ([long]$historyRecord[0].progressMs -eq $progressMs) `
            "expect=$progressMs actual=$($historyRecord[0].progressMs)"
    }
    if ($duration -gt 0) {
        Assert-VideoBizError '3.progressOverflow' `
            (Send-VideoProbe -Method 'Post' -Path '/video/play/progress' `
                -Body @{ contentId = $videoId; progressMs = ($duration + 1000) }) 40016
    }
    $null = Assert-VideoOk '3.historyDelete' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/history/' + $videoId))
} else {
    Write-Host 'SKIP 3 视频详情与进度（当前库无已发布视频，插入视频或设置 SMOKE_VIDEO_CONTENT_ID 后重跑）'
}

# ---------------------------------------------------------------
# 4 系列运营闭环（草稿 → 编辑 → 上架 → 下架 → 删除）
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 4 系列运营闭环 =='
$seriesToken = 'M3S' + (New-VideoRunToken)
$seriesCreateBody = @{
    title       = 'M3 冒烟系列 ' + $seriesToken
    description = 'M3 冒烟系列简介'
    sortNo      = 5
    categoryId  = 0
    creatorId   = 0
}
$draftSeries = Assert-VideoOk '4.create' `
    (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' -Body $seriesCreateBody)
$seriesId = [long]$draftSeries.id
Assert '4.createId' ($seriesId -gt 0) "id=$seriesId"
Assert '4.createState' ($draftSeries.publishState -eq 'DRAFT') `
    "state=$($draftSeries.publishState)"
Assert '4.createCount' ([int]$draftSeries.episodeCount -eq 0) `
    "episodeCount=$($draftSeries.episodeCount)"
Assert '4.createType' ($draftSeries.contentType -eq 'VIDEO') `
    "type=$($draftSeries.contentType)"
Assert '4.createCover' ([string]::IsNullOrWhiteSpace([string]$draftSeries.coverUrl)) `
    "coverUrl=$($draftSeries.coverUrl)"

$adminSeriesPage = Assert-VideoOk '4.adminPage' `
    (Send-VideoProbe -Path ('/video/admin/series/page?keyword=' + $seriesToken + '&size=10'))
Assert-VideoPageShape '4.adminPage' $adminSeriesPage 1 10
Assert '4.adminPageContains' (Test-VideoPageContainsId $adminSeriesPage $seriesId) `
    '运营系列分页未包含本次草稿'

# 未上架：用户侧详情 / 封面统一按不存在处理，分页不可见
Assert-VideoBizError '4.draftDetailHidden' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId)) 40404
Assert-VideoBizError '4.draftCoverHidden' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId + '/cover')) 40404
$draftHiddenPage = Assert-VideoOk '4.draftPageHidden' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $seriesToken + '&size=10'))
Assert-VideoPageShape '4.draftPageHidden' $draftHiddenPage 1 10
Assert '4.draftPageTotal' ([long]$draftHiddenPage.total -eq 0) `
    "total=$($draftHiddenPage.total)"

# 编辑：标题 / 简介 / 运营排序均可更新，状态保持草稿
$editedTitle = 'M3 冒烟系列 ' + $seriesToken + ' v2'
$editedSeries = Assert-VideoOk '4.update' `
    (Send-VideoProbe -Method 'Put' -Path ('/video/admin/series/' + $seriesId) `
        -Body @{
            title       = $editedTitle
            description = 'M3 冒烟系列简介 v2'
            sortNo      = 7
            categoryId  = 0
            creatorId   = 0
        })
Assert '4.updateTitle' ([string]$editedSeries.title -eq $editedTitle) `
    "title=$($editedSeries.title)"
Assert '4.updateSort' ([int]$editedSeries.sortNo -eq 7) "sortNo=$($editedSeries.sortNo)"
Assert '4.updateState' ($editedSeries.publishState -eq 'DRAFT') `
    "state=$($editedSeries.publishState)"

# 上架：用户侧立即可见，分页强制只返回已上架（忽略 publishState=DRAFT 过滤）
$publishedSeries = Assert-VideoOk '4.publish' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + $seriesId + '/publish'))
Assert '4.publishState' ($publishedSeries.publishState -eq 'PUBLISHED') `
    "state=$($publishedSeries.publishState)"
Assert '4.publishCount' ([int]$publishedSeries.episodeCount -eq 0) `
    "episodeCount=$($publishedSeries.episodeCount)"
Assert '4.publishAt' ($null -ne $publishedSeries.publishedAt) 'publishedAt 为空'

$visibleSeriesPage = Assert-VideoOk '4.visiblePage' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $seriesToken + '&size=10'))
Assert-VideoSeriesPage '4.visiblePage' $visibleSeriesPage 10
Assert '4.visiblePageContains' (Test-VideoPageContainsId $visibleSeriesPage $seriesId) `
    '用户侧系列分页未包含已上架系列'

# 反向过滤：客户端传 DRAFT，用户侧仍只返回已上架系列
$forcedPublishedPage = Assert-VideoOk '4.publishedOnly' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $seriesToken +
        '&publishState=DRAFT&size=10'))
Assert '4.publishedOnlyTotal' ([long]$forcedPublishedPage.total -eq 1) `
    "total=$($forcedPublishedPage.total)"
Assert '4.publishedOnlyContains' (Test-VideoPageContainsId $forcedPublishedPage $seriesId) `
    '用户侧系列分页未强制已上架'

$seriesDetail = Assert-VideoOk '4.detail' (Send-VideoProbe -Path ('/video/series/' + $seriesId))
Assert-VideoSeriesDetailShape '4.detail' $seriesDetail
Assert '4.detailTitle' ([string]$seriesDetail.title -eq $editedTitle) `
    "title=$($seriesDetail.title)"

# 已上架但无封面：代理返回 404，不暴露文件服务内部错误
Assert-VideoBizError '4.coverNoFile' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId + '/cover')) 404

# 下架：用户侧详情 / 封面 / 分页立即不可见
$offlinedSeries = Assert-VideoOk '4.offline' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + $seriesId + '/offline'))
Assert '4.offlineState' ($offlinedSeries.publishState -eq 'OFFLINE') `
    "state=$($offlinedSeries.publishState)"
Assert-VideoBizError '4.offlineDetailHidden' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId)) 40404
Assert-VideoBizError '4.offlineCoverHidden' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId + '/cover')) 40404
$offlineVisiblePage = Assert-VideoOk '4.offlinePage' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $seriesToken + '&size=10'))
Assert '4.offlinePageTotal' ([long]$offlineVisiblePage.total -eq 0) `
    "total=$($offlineVisiblePage.total)"

# 重新上架后删除（无集数允许删除），运营与用户侧同时消失
$null = Assert-VideoOk '4.republish' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + $seriesId + '/publish'))
$null = Assert-VideoOk '4.delete' `
    (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + $seriesId))
Assert-VideoBizError '4.deletedDetailHidden' `
    (Send-VideoProbe -Path ('/video/series/' + $seriesId)) 40404
$adminAfterDelete = Assert-VideoOk '4.adminAfterDelete' `
    (Send-VideoProbe -Path ('/video/admin/series/page?keyword=' + $seriesToken + '&size=10'))
Assert '4.adminAfterDeleteGone' (-not (Test-VideoPageContainsId $adminAfterDelete $seriesId)) `
    '删除后运营系列分页仍包含该系列'

# 系列管理权限隔离
Assert-VideoBizError '4.createDenied' `
    (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' -Body $seriesCreateBody `
        -Headers (New-VideoHeaders -PermissionCodes 'sys:video:browse')) 403

# ---------------------------------------------------------------
# 5 系列排序（sortNo 升序，同序按 ID 倒序）
# ---------------------------------------------------------------
Write-Host ''
Write-Host '== 5 系列排序 =='
$sortToken = 'M3O' + (New-VideoRunToken)
$seriesA = Assert-VideoOk '5.createA' `
    (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' `
        -Body @{ title = 'M3 冒烟排序A ' + $sortToken; description = '排序 A'; sortNo = 20 })
$seriesB = Assert-VideoOk '5.createB' `
    (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' `
        -Body @{ title = 'M3 冒烟排序B ' + $sortToken; description = '排序 B'; sortNo = 2 })
$seriesC = Assert-VideoOk '5.createC' `
    (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' `
        -Body @{ title = 'M3 冒烟排序C ' + $sortToken; description = '排序 C'; sortNo = 2 })
$null = Assert-VideoOk '5.publishA' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + [long]$seriesA.id + '/publish'))
$null = Assert-VideoOk '5.publishB' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + [long]$seriesB.id + '/publish'))
$null = Assert-VideoOk '5.publishC' `
    (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + [long]$seriesC.id + '/publish'))

$sortPage = Assert-VideoOk '5.page' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $sortToken + '&size=10'))
Assert-VideoSeriesPage '5.page' $sortPage 10
$sortRecords = @($sortPage.records)
Assert '5.pageCount' ($sortRecords.Count -eq 3) "count=$($sortRecords.Count)"
if ($sortRecords.Count -eq 3) {
    Assert '5.orderSortNo' ([int]$sortRecords[0].sortNo -le [int]$sortRecords[1].sortNo -and `
        [int]$sortRecords[1].sortNo -le [int]$sortRecords[2].sortNo) `
        "sortNo=$(@($sortRecords | ForEach-Object { $_.sortNo }) -join ',')"
    # sortNo=2 的两条按 ID 倒序：后创建的 C 在前
    Assert '5.orderTie' ([long]$sortRecords[0].id -eq [long]$seriesC.id -and `
        [long]$sortRecords[1].id -eq [long]$seriesB.id) `
        "first=$($sortRecords[0].id) second=$($sortRecords[1].id)"
    Assert '5.orderLast' ([long]$sortRecords[2].id -eq [long]$seriesA.id) `
        "last=$($sortRecords[2].id)"
}

# 清理排序用例（无集数，可直接删除）
$null = Assert-VideoOk '5.deleteA' `
    (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + [long]$seriesA.id))
$null = Assert-VideoOk '5.deleteB' `
    (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + [long]$seriesB.id))
$null = Assert-VideoOk '5.deleteC' `
    (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + [long]$seriesC.id))
$sortPageAfterDelete = Assert-VideoOk '5.pageAfterDelete' `
    (Send-VideoProbe -Path ('/video/series/page?keyword=' + $sortToken + '&size=10'))
Assert '5.pageAfterDeleteTotal' ([long]$sortPageAfterDelete.total -eq 0) `
    "total=$($sortPageAfterDelete.total)"

# ---------------------------------------------------------------
# 6 系列加集闭环（需 SMOKE_VIDEO_FILE_ID）
# ---------------------------------------------------------------
Write-Host ''
if ($env:SMOKE_VIDEO_FILE_ID) {
    $episodeFileId = [long]$env:SMOKE_VIDEO_FILE_ID
    $episodeToken = 'M3E' + (New-VideoRunToken)
    Write-Host "== 6 系列加集闭环 fileId=$episodeFileId =="

    # 上传前须签署当前版本声明
    $declaration = Assert-VideoOk '6.declaration' `
        (Send-VideoProbe -Path '/video/declaration/current')
    $null = Assert-VideoOk '6.accept' `
        (Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' `
            -Body @{ version = $declaration.version; contentSource = 'ORIGINAL' })

    $episodeSeries = Assert-VideoOk '6.createSeries' `
        (Send-VideoProbe -Method 'Post' -Path '/video/admin/series' `
            -Body @{ title = 'M3 冒烟集数 ' + $episodeToken; description = '系列加集冒烟'; sortNo = 1 })
    $episodeSeriesId = [long]$episodeSeries.id
    Assert '6.seriesId' ($episodeSeriesId -gt 0) "id=$episodeSeriesId"
    $null = Assert-VideoOk '6.publishSeries' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/admin/series/' + $episodeSeriesId + '/publish'))

    $episodeOneBody = @{
        title         = 'M3 冒烟视频 ' + $episodeToken + ' E1'
        fileId        = $episodeFileId
        durationMs    = 1000
        contentSource = 'ORIGINAL'
        visibility    = 'PUBLIC'
        publishNow    = $true
        seriesId      = $episodeSeriesId
        episodeNo     = 1
    }
    $episodeOneId = [long](Assert-VideoOk '6.addEpisode1' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $episodeOneBody))
    Assert '6.episodeOneId' ($episodeOneId -gt 0) "id=$episodeOneId"

    $ownerEpisodeOne = Assert-VideoOk '6.ownerEpisode1' `
        (Send-VideoProbe -Path ('/video/user/videos/' + $episodeOneId))
    Assert '6.ownerSeries' ([long]$ownerEpisodeOne.seriesId -eq $episodeSeriesId) `
        "seriesId=$($ownerEpisodeOne.seriesId)"
    Assert '6.ownerEpisodeNo' ([int]$ownerEpisodeOne.episodeNo -eq 1) `
        "episodeNo=$($ownerEpisodeOne.episodeNo)"
    Assert '6.ownerState' ($ownerEpisodeOne.publishState -eq 'PUBLISHED') `
        "state=$($ownerEpisodeOne.publishState)"

    # 同系列同集数重复提交拒绝；集数非法 / 系列不存在分别返回对应业务码
    Assert-VideoBizError '6.duplicateEpisode1' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $episodeOneBody) 40902
    $zeroEpisodeBody = $episodeOneBody.Clone()
    $zeroEpisodeBody['title'] = 'M3 冒烟视频 ' + $episodeToken + ' E0'
    $zeroEpisodeBody['episodeNo'] = 0
    Assert-VideoBizError '6.episodeZero' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $zeroEpisodeBody) 40013
    $unknownSeriesBody = $episodeOneBody.Clone()
    $unknownSeriesBody['title'] = 'M3 冒烟视频 ' + $episodeToken + ' EX'
    $unknownSeriesBody['seriesId'] = 999999999
    $unknownSeriesBody['episodeNo'] = 2
    Assert-VideoBizError '6.unknownSeries' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $unknownSeriesBody) 40404

    # 同一内容重复提交同一集数：排除自身后不冲突，更新幂等
    $null = Assert-VideoOk '6.updateSameEpisode' `
        (Send-VideoProbe -Method 'Put' -Path ('/video/user/videos/' + $episodeOneId) `
            -Body $episodeOneBody)
    $ownerAfterUpdate = Assert-VideoOk '6.ownerAfterUpdate' `
        (Send-VideoProbe -Path ('/video/user/videos/' + $episodeOneId))
    Assert '6.ownerAfterUpdateEpisode' ([int]$ownerAfterUpdate.episodeNo -eq 1) `
        "episodeNo=$($ownerAfterUpdate.episodeNo)"

    # 第 3 集（公开）与第 2 集（私密）：私密集只对本人可见，不进系列页
    $episodeThreeBody = $episodeOneBody.Clone()
    $episodeThreeBody['title'] = 'M3 冒烟视频 ' + $episodeToken + ' E3'
    $episodeThreeBody['episodeNo'] = 3
    $episodeThreeId = [long](Assert-VideoOk '6.addEpisode3' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $episodeThreeBody))
    $episodeTwoBody = $episodeOneBody.Clone()
    $episodeTwoBody['title'] = 'M3 冒烟视频 ' + $episodeToken + ' E2'
    $episodeTwoBody['episodeNo'] = 2
    $episodeTwoBody['visibility'] = 'PRIVATE'
    $episodeTwoId = [long](Assert-VideoOk '6.addPrivateEpisode2' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $episodeTwoBody))
    Assert-VideoBizError '6.duplicatePrivateEpisode2' `
        (Send-VideoProbe -Method 'Post' -Path '/video/user/videos' -Body $episodeTwoBody) 40902
    Assert-VideoBizError '6.otherUserPrivateEpisode' `
        (Send-VideoProbe -Path ('/video/videos/' + $episodeTwoId) `
            -Headers (New-VideoOtherUserHeaders)) 40401

    # 系列详情：只列已发布公开集，按集数升序
    $seriesDetail = Assert-VideoOk '6.seriesDetail' `
        (Send-VideoProbe -Path ('/video/series/' + $episodeSeriesId))
    Assert-VideoSeriesDetailShape '6.seriesDetail' $seriesDetail
    $episodes = @($seriesDetail.episodes)
    Assert '6.seriesDetailCount' ([int]$seriesDetail.episodeCount -eq 2) `
        "episodeCount=$($seriesDetail.episodeCount)"
    if ($episodes.Count -eq 2) {
        Assert '6.episodeOrderFirst' ([long]$episodes[0].id -eq $episodeOneId -and `
            [int]$episodes[0].episodeNo -eq 1) "first=$($episodes[0].episodeNo)"
        Assert '6.episodeOrderSecond' ([long]$episodes[1].id -eq $episodeThreeId -and `
            [int]$episodes[1].episodeNo -eq 3) "second=$($episodes[1].episodeNo)"
    }
    Assert '6.privateEpisodeHidden' (-not (Test-VideoListContains $episodes $episodeTwoId)) `
        '系列页包含私密集数'

    # 系列筛选视频列表：同系列、集数有效
    $seriesVideoPage = Assert-VideoOk '6.seriesVideoPage' `
        (Send-VideoProbe -Path ('/video/videos/page?seriesId=' + $episodeSeriesId +
            '&pageNum=1&pageSize=10'))
    Assert-VideoPageShape '6.seriesVideoPage' $seriesVideoPage 1 10
    Assert '6.seriesVideoPageTotal' ([long]$seriesVideoPage.total -eq 2) `
        "total=$($seriesVideoPage.total)"
    foreach ($record in @($seriesVideoPage.records)) {
        if ($null -eq $record) {
            continue
        }
        Assert '6.seriesVideoPageMatch' ([long]$record.seriesId -eq $episodeSeriesId) `
            "seriesId=$($record.seriesId)"
        Assert '6.seriesVideoPageEpisode' ([int]$record.episodeNo -gt 0) `
            "episodeNo=$($record.episodeNo)"
    }

    # 我的视频（含私密）按关键词命中三集
    $myVideoPage = Assert-VideoOk '6.myVideoPage' `
        (Send-VideoProbe -Path ('/video/my/videos?keyword=' + $episodeToken +
            '&pageNum=1&pageSize=10'))
    Assert-VideoPageShape '6.myVideoPage' $myVideoPage 1 10
    Assert '6.myVideoPageTotal' ([long]$myVideoPage.total -eq 3) `
        "total=$($myVideoPage.total)"

    # 下架第 1 集：集数计数、系列页、视频列表与首页缓存即时刷新
    $null = Assert-VideoOk '6.offlineEpisode1' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/user/videos/' + $episodeOneId + '/offline'))
    $detailAfterOffline = Assert-VideoOk '6.detailAfterOffline' `
        (Send-VideoProbe -Path ('/video/series/' + $episodeSeriesId))
    Assert '6.countAfterOffline' ([int]$detailAfterOffline.episodeCount -eq 1) `
        "episodeCount=$($detailAfterOffline.episodeCount)"
    Assert '6.episodesAfterOffline' (@($detailAfterOffline.episodes).Count -eq 1) `
        "count=$(@($detailAfterOffline.episodes).Count)"
    Assert '6.offlineEpisodeHidden' `
        (-not (Test-VideoListContains @($detailAfterOffline.episodes) $episodeOneId)) `
        '系列页仍包含已下架集数'

    $seriesPageAfterOffline = Assert-VideoOk '6.seriesPageAfterOffline' `
        (Send-VideoProbe -Path ('/video/series/page?keyword=' + $episodeToken + '&size=10'))
    Assert-VideoPageShape '6.seriesPageAfterOffline' $seriesPageAfterOffline 1 10
    $seriesRecords = @($seriesPageAfterOffline.records)
    Assert '6.seriesPageCountAfterOffline' ($seriesRecords.Count -eq 1) `
        "count=$($seriesRecords.Count)"
    if ($seriesRecords.Count -eq 1) {
        Assert '6.seriesViewCountAfterOffline' ([int]$seriesRecords[0].episodeCount -eq 1) `
            "episodeCount=$($seriesRecords[0].episodeCount)"
    }

    $videoPageAfterOffline = Assert-VideoOk '6.videoPageAfterOffline' `
        (Send-VideoProbe -Path ('/video/videos/page?keyword=' + $episodeToken +
            '&pageNum=1&pageSize=10'))
    Assert '6.videoPageEvicted' `
        (-not (Test-VideoListContains @($videoPageAfterOffline.records) $episodeOneId)) `
        '下架后视频列表仍包含该集'

    $homeAfterOffline = Assert-VideoOk '6.homeAfterOffline' `
        (Send-VideoProbe -Path '/video/home')
    Assert '6.homeEvicted' `
        (-not (Test-VideoListContains @($homeAfterOffline.videos) $episodeOneId)) `
        '下架后首页仍包含该集'

    # 重新上架：集数计数恢复，列表与首页缓存重建后可见
    $null = Assert-VideoOk '6.republishEpisode1' `
        (Send-VideoProbe -Method 'Post' -Path ('/video/user/videos/' + $episodeOneId + '/publish'))
    $detailAfterPublish = Assert-VideoOk '6.detailAfterPublish' `
        (Send-VideoProbe -Path ('/video/series/' + $episodeSeriesId))
    Assert '6.countAfterPublish' ([int]$detailAfterPublish.episodeCount -eq 2) `
        "episodeCount=$($detailAfterPublish.episodeCount)"
    Assert '6.episodeRestored' `
        (Test-VideoListContains @($detailAfterPublish.episodes) $episodeOneId) `
        '重新上架后系列页未恢复该集'

    $videoPageAfterPublish = Assert-VideoOk '6.videoPageAfterPublish' `
        (Send-VideoProbe -Path ('/video/videos/page?keyword=' + $episodeToken +
            '&pageNum=1&pageSize=10'))
    Assert '6.videoPageRestored' `
        (Test-VideoListContains @($videoPageAfterPublish.records) $episodeOneId) `
        '重新上架后视频列表未恢复该集'

    $homeAfterPublish = Assert-VideoOk '6.homeAfterPublish' `
        (Send-VideoProbe -Path '/video/home')
    Assert '6.homeRestored' `
        (Test-VideoListContains @($homeAfterPublish.videos) $episodeOneId) `
        '重新上架后首页未恢复该集'

    # 有集数的系列不允许删除（需先移除 / 下架集数）
    Assert-VideoBizError '6.deleteSeriesConflict' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + $episodeSeriesId)) 40901

    # 清理：先删集数再删系列，验证集数计数归零后可删除
    $null = Assert-VideoOk '6.deleteEpisode1' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/user/videos/' + $episodeOneId))
    $null = Assert-VideoOk '6.deleteEpisode3' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/user/videos/' + $episodeThreeId))
    $null = Assert-VideoOk '6.deleteEpisode2' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/user/videos/' + $episodeTwoId))
    $null = Assert-VideoOk '6.deleteSeries' `
        (Send-VideoProbe -Method 'Delete' -Path ('/video/admin/series/' + $episodeSeriesId))
    Assert-VideoBizError '6.deletedSeriesHidden' `
        (Send-VideoProbe -Path ('/video/series/' + $episodeSeriesId)) 40404
} else {
    Write-Host 'SKIP 6 系列加集闭环（设置 SMOKE_VIDEO_FILE_ID=<已上传视频文件ID> 后重跑可覆盖）'
}

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
