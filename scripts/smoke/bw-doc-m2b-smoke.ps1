# Wave 5.75 M2 剩余功能冒烟：页面级权限 / 标签 / 反向链接 / 动态 / 最近访问 /
# ngram 全文检索 / 评论未读 / M4 受限页下线
# 前置：
#   1) gateway(8080)/auth(8081)/whale-doc(8095) 就绪；MySQL 与 Redis 可用；
#   2) bluewhale_doc 已执行 init.sql 与 migration-5.75-doc-m2b.sql；
#      bluewhale_auth 已执行 migration-5.75-doc-m2b.sql（权限 278/279 种子）；
#   3) 全文检索依赖 MySQL ngram 全文索引（init.sql 或迁移已创建）。
# 用法：pwsh -File scripts/smoke/bw-doc-m2b-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本会创建用户、空间与页面，并在结束时归档空间；不删除数据，便于排查残留。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$docService = 'http://127.0.0.1:8095'
$failures = 0

function Json($value) {
    return ($value | ConvertTo-Json -Depth 30 -Compress)
}

function Invoke-Api($method, $uri, $body = $null, $headers = $null) {
    $params = @{ Method = $method; Uri = $uri; Headers = ($(if ($headers) { $headers } else { $script:h })) }
    if ($null -ne $body) {
        $params.ContentType = 'application/json'
        $params.Body = Json $body
    }
    return Invoke-RestMethod @params
}

# 1) 管理员登录与租户
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body '{"username":"admin","password":"123456"}'
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$meta = Invoke-Api Get "$base/doc/meta"
Assert '2.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
$tenantId = $meta.data.tenantId
$adminId = [long]$meta.data.userId
$internalHeaders = @{ 'X-Tenant-Id' = "$tenantId"; 'X-User-Id' = "$adminId"; 'X-Username' = 'admin' }

# 2) 创建协作用户并授予 doc_member，用于 ACL 可见性验证
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$username = "m2b_$stamp"
$userCreate = Invoke-Api Post "$base/user" @{
    username = $username; password = '123456'; nickname = 'M2B User'; status = 1
}
Assert '3.userCreate' ($userCreate.code -eq 0 -and $userCreate.data.id) "(code=$($userCreate.code), msg=$($userCreate.message))"
$readerId = [long]$userCreate.data.id

$bindTenant = Invoke-Api Post "$base/user/$readerId/tenants" @{ tenantId = $tenantId }
Assert '4.userBindTenant' ($bindTenant.code -eq 0) "(code=$($bindTenant.code), msg=$($bindTenant.message))"
$assignRole = Invoke-Api Post "$base/user/$readerId/roles" @{ roleIds = @(25102) }
Assert '5.userAssignDocMember' ($assignRole.code -eq 0) "(code=$($assignRole.code), msg=$($assignRole.message))"

$readerLogin = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body (Json @{ username = $username; password = '123456' })
Assert '6.readerLogin' ($readerLogin.code -eq 0 -and $readerLogin.data.accessToken) "(code=$($readerLogin.code))"
$readerHeaders = @{ Authorization = "Bearer $($readerLogin.data.accessToken)" }

# 3) 空间与页面
$space = Invoke-Api Post "$base/doc/space" @{
    name        = "M2B Smoke $stamp"
    description = 'M2 remaining features smoke space'
    icon        = 'book'
    visibility  = 'PUBLIC'
}
Assert '7.spaceCreate' ($space.code -eq 0 -and $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id

$member = Invoke-Api Post "$base/doc/space/$spaceId/member" @{ userIds = @($readerId); role = 'READER' }
Assert '8.readerMember' ($member.code -eq 0 -and @($member.data).Count -eq 1) "(code=$($member.code))"

$pageA = Invoke-Api Post "$base/doc/page" @{
    spaceId = $spaceId; parentId = 0; title = "M2B Page A $stamp"; pageType = 'PAGE'
}
Assert '9.pageACreate' ($pageA.code -eq 0 -and $pageA.data.id) "(code=$($pageA.code))"
$pageAId = $pageA.data.id

$pageB = Invoke-Api Post "$base/doc/page" @{
    spaceId = $spaceId; parentId = 0; title = "M2B Page B $stamp"; pageType = 'PAGE'
}
Assert '10.pageBCreate' ($pageB.code -eq 0 -and $pageB.data.id) "(code=$($pageB.code))"
$pageBId = $pageB.data.id

$pageC = Invoke-Api Post "$base/doc/page" @{
    spaceId = $spaceId; parentId = 0; title = "M2B Page C $stamp"; pageType = 'PAGE'
}
Assert '11.pageCCreate' ($pageC.code -eq 0 -and $pageC.data.id) "(code=$($pageC.code))"
$pageCId = $pageC.data.id

# 4) 正文：A 引用 B 并写入中文关键词；C 写入受限关键词
$keyword = "M2BDOC$stamp"
$secret = "M2BSECRET$stamp"
$saveA = Invoke-Api Put "$base/doc/page/$pageAId" @{
    baseRevision = $pageA.data.revision
    title        = "M2B Page A $stamp"
    contentJson  = @{
        type    = 'doc'
        content = @(
            @{
                type    = 'paragraph'
                content = @(
                    @{ type = 'text'; text = "$keyword 数据库检索样例 " },
                    @{
                        type  = 'text'
                        text  = '跳转到页面B'
                        marks = @(@{ type = 'link'; attrs = @{ href = "/doc/page/$pageBId" } })
                    }
                )
            }
        )
    }
}
Assert '12.pageASave' ($saveA.code -eq 0) "(code=$($saveA.code), msg=$($saveA.message))"

$saveC = Invoke-Api Put "$base/doc/page/$pageCId" @{
    baseRevision = $pageC.data.revision
    title        = "M2B Page C $stamp"
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = $secret }) })
    }
}
Assert '13.pageCSave' ($saveC.code -eq 0) "(code=$($saveC.code))"

$publishA = Invoke-Api Post "$base/doc/page/$pageAId/publish"
$publishB = Invoke-Api Post "$base/doc/page/$pageBId/publish"
$publishC = Invoke-Api Post "$base/doc/page/$pageCId/publish"
Assert '14.publish' ($publishA.code -eq 0 -and $publishB.code -eq 0 -and $publishC.code -eq 0) `
    "(a=$($publishA.code), b=$($publishB.code), c=$($publishC.code))"

# 5) 标签：创建、绑定、计数、筛选检索
$tag = Invoke-Api Post "$base/doc/space/$spaceId/tag" @{ name = "M2B-$stamp"; color = '#1677ff' }
Assert '15.tagCreate' ($tag.code -eq 0 -and $tag.data.id) "(code=$($tag.code), msg=$($tag.message))"
$tagId = $tag.data.id

$pageTags = Invoke-Api Put "$base/doc/page/$pageAId/tag" @{ tagIds = @($tagId) }
Assert '16.pageTagAssign' ($pageTags.code -eq 0 -and @($pageTags.data).Count -eq 1) "(code=$($pageTags.code))"

$tagList = Invoke-Api Get "$base/doc/space/$spaceId/tag"
$tagRow = @($tagList.data | Where-Object { $_.id -eq $tagId })[0]
Assert '17.tagPageCount' ($tagList.code -eq 0 -and $null -ne $tagRow -and $tagRow.pageCount -ge 1) `
    "(pageCount=$($tagRow.pageCount))"
$tagsOfPage = Invoke-Api Get "$base/doc/page/$pageAId/tag"
Assert '18.pageTags' ($tagsOfPage.code -eq 0 -and @($tagsOfPage.data | Where-Object { $_.id -eq $tagId }).Count -eq 1) `
    "(count=$(@($tagsOfPage.data).Count))"

$tagSearch = Invoke-Api Get "$base/doc/search?keyword=$keyword&current=1&size=10&tagId=$tagId"
$tagHit = @($tagSearch.data.records | Where-Object { [long]$_.pageId -eq [long]$pageAId })[0]
Assert '19.tagSearch' ($tagSearch.code -eq 0 -and $null -ne $tagHit) "(total=$($tagSearch.data.total))"

# 6) ngram 全文检索（中文两字词与四字词）
$fulltext = Invoke-Api Get "$base/doc/search?keyword=%E6%95%B0%E6%8D%AE%E5%BA%93&current=1&size=10"
$fulltextHit = @($fulltext.data.records | Where-Object { [long]$_.pageId -eq [long]$pageAId })[0]
Assert '20.fulltextSearch' ($fulltext.code -eq 0 -and $null -ne $fulltextHit) `
    "(total=$($fulltext.data.total), snippet=$($fulltextHit.snippet))"
$fulltextShort = Invoke-Api Get "$base/doc/search?keyword=%E6%95%B0%E6%8D%AE&current=1&size=10"
Assert '21.fulltextShortKeyword' ($fulltextShort.code -eq 0 -and $fulltextShort.data.total -ge 1) `
    "(total=$($fulltextShort.data.total))"

# 7) 页面级权限：B 授权 READ → 只读可评论；提升 EDIT → 可编辑
$aclBRead = Invoke-Api Put "$base/doc/page/$pageBId/acl" @{
    restricted = $true
    items      = @(@{ userId = $readerId; aclRole = 'READ' })
}
Assert '22.aclBRead' ($aclBRead.code -eq 0 -and $aclBRead.data.restricted -eq $true -and @($aclBRead.data.items).Count -eq 1) `
    "(code=$($aclBRead.code), items=$(@($aclBRead.data.items).Count))"

$detailBReader = Invoke-Api Get "$base/doc/page/$pageBId" $null $readerHeaders
Assert '23.readerReadRestricted' ($detailBReader.code -eq 0 -and $detailBReader.data.restricted -eq $true `
        -and $detailBReader.data.canEdit -eq $false -and $detailBReader.data.canComment -eq $true) `
    "(code=$($detailBReader.code), canEdit=$($detailBReader.data.canEdit), canComment=$($detailBReader.data.canComment))"

$saveBReader = Invoke-Api Put "$base/doc/page/$pageBId" @{
    baseRevision = $detailBReader.data.revision
    title        = "M2B Page B $stamp"
    contentJson  = @{ type = 'doc'; content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'reader edit' }) }) }
} $readerHeaders
Assert '24.readerEditDenied' ($saveBReader.code -eq 40301) "(code=$($saveBReader.code))"

$aclBEdit = Invoke-Api Put "$base/doc/page/$pageBId/acl" @{
    restricted = $true
    items      = @(@{ userId = $readerId; aclRole = 'EDIT' })
}
Assert '25.aclBEdit' ($aclBEdit.code -eq 0 -and @($aclBEdit.data.items)[0].aclRole -eq 'EDIT') "(code=$($aclBEdit.code))"

$detailBEdit = Invoke-Api Get "$base/doc/page/$pageBId" $null $readerHeaders
$saveBEdit = Invoke-Api Put "$base/doc/page/$pageBId" @{
    baseRevision = $detailBEdit.data.revision
    title        = "M2B Page B $stamp"
    contentJson  = @{ type = 'doc'; content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'reader edited ok' }) }) }
} $readerHeaders
Assert '26.readerEditAllowed' ($saveBEdit.code -eq 0) "(code=$($saveBEdit.code), msg=$($saveBEdit.message))"

# 8) 页面级权限：C 仅管理员可见 → 读者不可见、不出现在树/检索
$aclC = Invoke-Api Put "$base/doc/page/$pageCId/acl" @{ restricted = $true; items = @() }
Assert '27.aclCAdminOnly' ($aclC.code -eq 0 -and $aclC.data.restricted -eq $true) "(code=$($aclC.code))"

$detailCReader = Invoke-Api Get "$base/doc/page/$pageCId" $null $readerHeaders
Assert '28.readerCannotSeeRestricted' ($detailCReader.code -eq 40401) "(code=$($detailCReader.code))"

$treeReader = Invoke-Api Get "$base/doc/space/$spaceId/tree" $null $readerHeaders
$nodeC = @($treeReader.data | Where-Object { [long]$_.id -eq [long]$pageCId })[0]
Assert '29.treeOmitsRestricted' ($treeReader.code -eq 0 -and $null -eq $nodeC) "(node=$($null -eq $nodeC))"

$secretSearch = Invoke-Api Get "$base/doc/search?keyword=$secret&current=1&size=10" $null $readerHeaders
$secretHit = @($secretSearch.data.records | Where-Object { [long]$_.pageId -eq [long]$pageCId }).Count
Assert '30.searchOmitsRestricted' ($secretSearch.code -eq 0 -and $secretHit -eq 0) `
    "(hit=$secretHit, total=$($secretSearch.data.total))"

$detailCAdmin = Invoke-Api Get "$base/doc/page/$pageCId"
Assert '31.ownerSeesRestricted' ($detailCAdmin.code -eq 0 -and $detailCAdmin.data.restricted -eq $true) `
    "(code=$($detailCAdmin.code))"

# 9) 反向链接、动态、最近访问
$backlinks = Invoke-Api Get "$base/doc/page/$pageBId/backlink"
$backlink = @($backlinks.data | Where-Object { [long]$_.pageId -eq [long]$pageAId })[0]
Assert '32.backlink' ($backlinks.code -eq 0 -and $null -ne $backlink -and $backlink.linkText -eq '跳转到页面B') `
    "(count=$(@($backlinks.data).Count), linkText=$($backlink.linkText))"

$activities = Invoke-Api Get "$base/doc/page/$pageAId/activity?current=1&size=20"
Assert '33.activity' ($activities.code -eq 0 -and @($activities.data.records | Where-Object { $_.action -eq 'TAG' }).Count -ge 1 `
        -and @($activities.data.records | Where-Object { $_.action -eq 'PUBLISH' }).Count -ge 1) `
    "(total=$($activities.data.total))"

$recentReader = Invoke-Api Get "$base/doc/recent?limit=10" $null $readerHeaders
Assert '34.recent' ($recentReader.code -eq 0 -and @($recentReader.data | Where-Object { [long]$_.pageId -eq [long]$pageBId }).Count -eq 1) `
    "(count=$(@($recentReader.data).Count))"

# 10) 评论未读：读者评论 → 管理员未读 1 → 标记已读后 0
$comment = Invoke-Api Post "$base/doc/page/$pageBId/comment" @{
    content = "@$adminId 请查看受限页面评论"
} $readerHeaders
Assert '35.commentCreate' ($comment.code -eq 0 -and $comment.data.id) "(code=$($comment.code), msg=$($comment.message))"

$unreadAdmin = Invoke-Api Get "$base/doc/page/$pageBId/comment/unread"
Assert '36.unreadAdmin' ($unreadAdmin.code -eq 0 -and $unreadAdmin.data.unread -ge 1) "(unread=$($unreadAdmin.data.unread))"

$markRead = Invoke-Api Post "$base/doc/page/$pageBId/comment/read"
Assert '37.markRead' ($markRead.code -eq 0 -and $markRead.data.unread -eq 0) `
    "(code=$($markRead.code), unread=$($markRead.data.unread))"

# 11) M4 内网：受限页不可读取快照，变更流标记 restricted
$internalChanges = Invoke-RestMethod -Method Get -Uri `
    "$docService/doc/internal/page/changes?current=1&size=200" -Headers $internalHeaders
$changeC = @($internalChanges.data.records | Where-Object { [long]$_.pageId -eq [long]$pageCId })[0]
if ($null -eq $changeC) {
    $total = [long]$internalChanges.data.total
    $lastPage = [Math]::Max(1, [Math]::Ceiling($total / 200))
    $internalChanges = Invoke-RestMethod -Method Get -Uri `
        "$docService/doc/internal/page/changes?current=$lastPage&size=200" -Headers $internalHeaders
    $changeC = @($internalChanges.data.records | Where-Object { [long]$_.pageId -eq [long]$pageCId })[0]
}
Assert '38.internalChangeRestricted' ($null -ne $changeC -and $changeC.restricted -eq $true) `
    "(restricted=$($changeC.restricted))"

$internalDetail = Invoke-Api Get "$docService/doc/internal/page/$pageCId" $null $internalHeaders
Assert '39.internalDetailExcludesRestricted' ($internalDetail.code -eq 40401) "(code=$($internalDetail.code))"

# 12) 标签删除清理与空间归档
$tagDelete = Invoke-Api Delete "$base/doc/tag/$tagId"
Assert '40.tagDelete' ($tagDelete.code -eq 0) "(code=$($tagDelete.code))"
$tagListAfter = Invoke-Api Get "$base/doc/space/$spaceId/tag"
Assert '41.tagDeleted' (@($tagListAfter.data | Where-Object { $_.id -eq $tagId }).Count -eq 0) `
    "(count=$(@($tagListAfter.data).Count))"

$spaceArchive = Invoke-Api Delete "$base/doc/space/$spaceId"
Assert '42.spaceArchive' ($spaceArchive.code -eq 0) "(code=$($spaceArchive.code))"

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions failed)"
    exit 1
}
Write-Host 'RESULT: PASSED'
