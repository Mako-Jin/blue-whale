# Wave 5.74 M1 文档服务业务冒烟（空间/页面/版本/附件/评论/搜索/收藏/内网同步）
# 前置：gateway(8080)/auth(8081)/whale-doc(8095)/whale-file(8085) 就绪，
#       bluewhale_doc、bluewhale_file 与 migration-5.74-doc.sql 已执行。
# 用法：powershell -File scripts/smoke/bw-doc-m1-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本会创建并归档一个 PUBLIC 空间；不删除空间，便于排查残留数据。

$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$docService = 'http://127.0.0.1:8095'
$failures = 0

function Assert($label, $cond, $detail = '') {
    if (-not $cond) {
        $script:failures++
        Write-Host "FAIL $label $detail"
        return
    }
    Write-Host "OK   $label $detail"
}

function Json($value) {
    return ($value | ConvertTo-Json -Depth 20 -Compress)
}

function Invoke-Api($method, $uri, $body = $null) {
    $params = @{ Method = $method; Uri = $uri; Headers = $script:h }
    if ($null -ne $body) {
        $params.ContentType = 'application/json'
        $params.Body = Json $body
    }
    return Invoke-RestMethod @params
}

# 1) 登录与基础连通性
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body '{"username":"admin","password":"123456"}'
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }
$token = $login.data.accessToken

$meta = Invoke-Api Get "$base/doc/meta"
Assert '2.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
$tenantId = $meta.data.tenantId
Assert '3.tenant' ($null -ne $tenantId) "(tenantId=$tenantId)"

try {
    Invoke-RestMethod -Method Get -Uri "$base/doc/internal/page/changes" -Headers $h | Out-Null
    Assert '4.internalDeny' $false '(expected 403)'
} catch {
    Assert '4.internalDeny' ($_.Exception.Response.StatusCode.value__ -eq 403)
}
try {
    Invoke-RestMethod -Method Get -Uri "$base/doc/meta" | Out-Null
    Assert '5.unauthenticatedDenied' $false '(expected 401/403)'
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Assert '5.unauthenticatedDenied' ($status -eq 401 -or $status -eq 403) "(http=$status)"
}

# 2) 空间与成员
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$space = Invoke-Api Post "$base/doc/space" @{
    name        = "M1 Smoke $stamp"
    description = 'M1 business smoke space'
    icon        = 'book'
    visibility  = 'PUBLIC'
}
Assert '6.spaceCreate' ($space.code -eq 0 -and $null -ne $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id
Assert '7.spaceOwner' ($space.data.memberRole -eq 'OWNER' -and $space.data.spaceKey) "(key=$($space.data.spaceKey))"

$spacePage = Invoke-Api Get "$base/doc/space/page?current=1&size=20&keyword=$stamp"
Assert '8.spacePage' ($spacePage.code -eq 0 -and @($spacePage.data.records | Where-Object { $_.id -eq $spaceId }).Count -eq 1) `
    "(total=$($spacePage.data.total))"
$mySpaces = Invoke-Api Get "$base/doc/space/my"
Assert '9.spaceMy' ($mySpaces.code -eq 0 -and @($mySpaces.data | Where-Object { $_.id -eq $spaceId }).Count -eq 1)

$member = Invoke-Api Post "$base/doc/space/$spaceId/member" @{ userIds = @(2); role = 'READER' }
Assert '10.memberAdd' ($member.code -eq 0 -and @($member.data).Count -eq 1) "(code=$($member.code))"
$memberId = @($member.data)[0].id
$memberList = Invoke-Api Get "$base/doc/space/$spaceId/member"
$memberCount = @($memberList.data).Count
Assert '11.memberList' ($memberList.code -eq 0 -and @($memberList.data | Where-Object { $_.userId -eq 2 }).Count -eq 1) `
    "(count=$memberCount, data=$(Json $memberList.data))"
$memberUpdate = Invoke-Api Put "$base/doc/space/$spaceId/member/$memberId" @{ role = 'EDITOR' }
Assert '12.memberUpdate' ($memberUpdate.code -eq 0 -and $memberUpdate.data.memberRole -eq 'EDITOR')
$memberRemove = Invoke-Api Delete "$base/doc/space/$spaceId/member/$memberId"
Assert '13.memberRemove' ($memberRemove.code -eq 0)

# 3) 页面树与正文保存
$folder = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = 0; title = 'M1 Folder'; pageType = 'FOLDER'
}
Assert '14.folderCreate' ($folder.code -eq 0 -and $folder.data.id) "(code=$($folder.code))"
$folderId = $folder.data.id

$page = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = $folderId; title = 'M1 Page'; pageType = 'PAGE'
}
Assert '15.pageCreate' ($page.code -eq 0 -and $page.data.id) "(code=$($page.code), slug=$($page.data.slug))"
$pageId = $page.data.id

$tree = Invoke-Api Get "$base/doc/space/$spaceId/tree"
Assert '16.pageTree' ($tree.code -eq 0 -and @($tree.data | Where-Object { $_.id -eq $folderId }).Count -eq 1) `
    "(roots=$(@($tree.data).Count))"
$folderNode = @($tree.data | Where-Object { $_.id -eq $folderId })[0]
Assert '17.pageTreeChild' (@($folderNode.children | Where-Object { $_.id -eq $pageId }).Count -eq 1)

$detail = Invoke-Api Get "$base/doc/page/$pageId"
Assert '18.pageDetail' ($detail.code -eq 0 -and $detail.data.revision -eq 0 -and $detail.data.canEdit -and $detail.data.canManage) `
    "(revision=$($detail.data.revision), canEdit=$($detail.data.canEdit), canManage=$($detail.data.canManage))"
Assert '19.pageBreadcrumb' (@($detail.data.breadcrumb).Count -eq 2) "(breadcrumb=$(@($detail.data.breadcrumb).Count))"

$body1 = @{
    baseRevision = 0
    title        = 'M1 Page v1'
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'BlueWhale document smoke content' }) })
    }
}
$save1 = Invoke-Api Put "$base/doc/page/$pageId" $body1
Assert '20.pageSave' ($save1.code -eq 0 -and $save1.data.revision -eq 1 -and $null -ne $save1.data.savedAt) `
    "(revision=$($save1.data.revision), savedAt=$($save1.data.savedAt))"

$conflict = Invoke-Api Put "$base/doc/page/$pageId" @{ baseRevision = 0; title = 'M1 conflict' }
Assert '21.pageConflict' ($conflict.code -eq 40901) "(code=$($conflict.code), msg=$($conflict.message))"

$manual1 = Invoke-Api Post "$base/doc/page/$pageId/version" @{ changeSummary = 'M1 manual snapshot' }
Assert '22.versionManual' ($manual1.code -eq 0 -and $manual1.data.versionKind -eq 'MANUAL' -and $manual1.data.revisionNo -eq 1) `
    "(kind=$($manual1.data.versionKind), revision=$($manual1.data.revisionNo))"

$save2 = Invoke-Api Put "$base/doc/page/$pageId" @{
    baseRevision = 1
    title        = 'M1 Page v2'
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'BlueWhale document smoke content v2' }) })
    }
}
Assert '23.pageSaveV2' ($save2.code -eq 0 -and $save2.data.revision -eq 2) "(revision=$($save2.data.revision))"

# 4) 发布 / 版本 / 恢复
$publish = Invoke-Api Post "$base/doc/page/$pageId/publish"
Assert '24.publish' ($publish.code -eq 0 -and $publish.data.status -eq 'PUBLISHED' -and $publish.data.revision -eq 2) `
    "(status=$($publish.data.status), revision=$($publish.data.revision))"

$pageVersion = Invoke-Api Get "$base/doc/page/$pageId/version/page?current=1&size=20"
$kinds = @($pageVersion.data.records | ForEach-Object { $_.versionKind })
Assert '25.versionList' ($pageVersion.code -eq 0 -and $kinds -contains 'PUBLISH' -and $kinds -contains 'MANUAL') `
    "(kinds=$($kinds -join ','))"

$versionDetail = Invoke-Api Get "$base/doc/page/$pageId/version/1"
Assert '26.versionDetail' ($versionDetail.code -eq 0 -and $null -ne $versionDetail.data.contentJson -and $versionDetail.data.title -eq 'M1 Page v1') `
    "(title=$($versionDetail.data.title))"

$restore = Invoke-Api Post "$base/doc/page/$pageId/version/1/restore"
Assert '27.versionRestore' ($restore.code -eq 0 -and $restore.data.revision -eq 3) "(revision=$($restore.data.revision))"
$afterRestore = Invoke-Api Get "$base/doc/page/$pageId"
Assert '28.restoreContent' ($afterRestore.data.title -eq 'M1 Page v1' -and $afterRestore.data.contentJson -match 'smoke content') `
    "(title=$($afterRestore.data.title))"

# 5) 附件（经 whale-file 内网通道）
$tempFile = Join-Path $env:TEMP "doc-m1-$stamp.txt"
Set-Content -Path $tempFile -Value 'M1 attachment content' -Encoding UTF8
$uploadJson = & curl.exe -s -X POST "$base/doc/page/$pageId/attachment" `
    -H "Authorization: Bearer $token" -F "file=@$tempFile" -F 'usageType=ATTACHMENT'
$uploadRes = $uploadJson | ConvertFrom-Json
Assert '29.attachmentUpload' ($uploadRes.code -eq 0 -and $uploadRes.data.id) "(code=$($uploadRes.code), msg=$($uploadRes.message))"
$attachmentId = $uploadRes.data.id
$fileId = $uploadRes.data.fileId

$attachments = Invoke-Api Get "$base/doc/page/$pageId/attachment"
Assert '30.attachmentList' ($attachments.code -eq 0 -and @($attachments.data | Where-Object { $_.id -eq $attachmentId }).Count -eq 1)

$downloadFile = Join-Path $env:TEMP "doc-m1-$stamp.out.txt"
$headersFile = Join-Path $env:TEMP "doc-m1-$stamp.headers.txt"
& curl.exe -s -D $headersFile -o $downloadFile -H "Authorization: Bearer $token" `
    "$base/doc/page/$pageId/attachment/$attachmentId/content"
$downloaded = Get-Content -Raw $downloadFile
$headerText = Get-Content -Raw $headersFile
Assert '31.attachmentContent' ($downloaded -match 'M1 attachment content') "(bytes=$($downloaded.Length))"
Assert '32.attachmentNosniff' ($headerText -match 'x-content-type-options: nosniff') `
    "(noSniff=$($headerText -match 'nosniff'))"

$attachmentDelete = Invoke-Api Delete "$base/doc/page/$pageId/attachment/$attachmentId"
Assert '33.attachmentDelete' ($attachmentDelete.code -eq 0)
Assert '34.attachmentGone' ((Invoke-Api Get "$base/doc/page/$pageId/attachment").data.Count -eq 0)

# 6) 评论
$comment = Invoke-Api Post "$base/doc/page/$pageId/comment" @{ content = 'M1 comment' }
Assert '35.commentCreate' ($comment.code -eq 0 -and $comment.data.id) "(code=$($comment.code))"
$commentId = $comment.data.id
$reply = Invoke-Api Post "$base/doc/page/$pageId/comment" @{ content = 'M1 reply'; parentId = $commentId }
Assert '36.commentReply' ($reply.code -eq 0 -and $reply.data.parentId -eq $commentId)

$comments = Invoke-Api Get "$base/doc/page/$pageId/comment"
$main = @($comments.data | Where-Object { $_.id -eq $commentId })[0]
Assert '37.commentList' ($comments.code -eq 0 -and @($main.replies).Count -eq 1)

$resolve = Invoke-Api Put "$base/doc/comment/$commentId/resolve?resolved=true"
Assert '38.commentResolve' ($resolve.code -eq 0 -and $resolve.data.resolved)
$reopen = Invoke-Api Put "$base/doc/comment/$commentId/resolve?resolved=false"
Assert '39.commentReopen' ($reopen.code -eq 0 -and -not $reopen.data.resolved)
$commentDelete = Invoke-Api Delete "$base/doc/comment/$commentId"
Assert '40.commentDelete' ($commentDelete.code -eq 0)
Assert '41.commentCascade' ((Invoke-Api Get "$base/doc/page/$pageId/comment").data.Count -eq 0)

$commentCreate2 = Invoke-Api Post "$base/doc/page/$pageId/comment" @{ content = 'M1 comment before archive' }
Assert '42.commentSeed' ($commentCreate2.code -eq 0)

# 7) 搜索与收藏
$search = Invoke-Api Get "$base/doc/search?keyword=BlueWhale&current=1&size=10"
Assert '43.search' ($search.code -eq 0 -and @($search.data.records | Where-Object { $_.pageId -eq $pageId }).Count -eq 1) `
    "(total=$($search.data.total))"

$star = Invoke-Api Post "$base/doc/page/$pageId/star"
Assert '44.starAdd' ($star.code -eq 0 -and $star.data.starred)
$favorites = Invoke-Api Get "$base/doc/page/star/page?current=1&size=10"
Assert '45.favoriteList' ($favorites.code -eq 0 -and @($favorites.data.records | Where-Object { $_.pageId -eq $pageId }).Count -eq 1)
$unstar = Invoke-Api Post "$base/doc/page/$pageId/star"
Assert '46.starRemove' ($unstar.code -eq 0 -and -not $unstar.data.starred)

# 8) 内网增量同步（服务直连，网关路径不可达已在上方校验）
$internalHeaders = @{ 'X-Tenant-Id' = "$tenantId"; 'X-User-Id' = '1'; 'X-Username' = 'admin' }
$changes = Invoke-RestMethod -Method Get -Uri "$docService/doc/internal/page/changes?current=1&size=50" `
    -Headers $internalHeaders
Assert '47.internalChanges' ($changes.code -eq 0) "(code=$($changes.code))"
$changePublished = @($changes.data.records | Where-Object { $_.pageId -eq $pageId -and -not $_.deleted })[0]
if ($null -eq $changePublished) {
    # 库中历史冒烟数据可能超过一页（变更流按 update_time 升序），回退到最后一页查找
    $lastPage = [Math]::Max(1, [Math]::Ceiling([long]$changes.data.total / 50))
    $changes = Invoke-RestMethod -Method Get -Uri `
        "$docService/doc/internal/page/changes?current=$lastPage&size=50" -Headers $internalHeaders
    $changePublished = @($changes.data.records | Where-Object { $_.pageId -eq $pageId -and -not $_.deleted })[0]
}
Assert '48.internalPublished' ($null -ne $changePublished) "(found=$($null -ne $changePublished), total=$($changes.data.total))"
$internalDetail = Invoke-RestMethod -Method Get -Uri "$docService/doc/internal/page/$pageId" -Headers $internalHeaders
Assert '49.internalDetail' ($internalDetail.code -eq 0 -and $internalDetail.data.contentText -match 'smoke content')
Assert '50.internalDetailNoJson' ($null -eq $internalDetail.data.contentJson)

# 9) 归档 / 取消归档 / 移动 / 删除
$archive = Invoke-Api Post "$base/doc/page/$pageId/archive"
Assert '51.archive' ($archive.code -eq 0 -and $archive.data.status -eq 'ARCHIVED')
$archiveSave = Invoke-Api Put "$base/doc/page/$pageId" @{ baseRevision = 3; title = 'should fail' }
Assert '52.archivedReadonly' ($archiveSave.code -ne 0) "(code=$($archiveSave.code))"
$unarchive = Invoke-Api Post "$base/doc/page/$pageId/restore"
Assert '53.unarchive' ($unarchive.code -eq 0 -and $unarchive.data.status -eq 'DRAFT')

$move = Invoke-Api Post "$base/doc/page/$pageId/move" @{ parentId = 0 }
$movedDetail = Invoke-Api Get "$base/doc/page/$pageId"
Assert '54.pageMove' ($move.code -eq 0 -and $movedDetail.data.parentId -eq 0) `
    "(parentId=$($movedDetail.data.parentId))"
$cycle = Invoke-Api Post "$base/doc/page/$folderId/move" @{ parentId = $folderId }
Assert '55.treeCycle' ($cycle.code -eq 40002) "(code=$($cycle.code))"

# 重新发布后删除，内网应收到 deleted 墓碑
$publishAgain = Invoke-Api Post "$base/doc/page/$pageId/publish"
Assert '56.publishAgain' ($publishAgain.code -eq 0 -and $publishAgain.data.status -eq 'PUBLISHED')
$deletePage = Invoke-Api Delete "$base/doc/page/$pageId"
Assert '57.pageDelete' ($deletePage.code -eq 0)
Assert '58.pageGone' ((Invoke-Api Get "$base/doc/page/$pageId").code -eq 40401)
$changesAfterDelete = Invoke-RestMethod -Method Get -Uri `
    "$docService/doc/internal/page/changes?since=2000-01-01T00:00:00&current=1&size=50" -Headers $internalHeaders
$changeTombstone = @($changesAfterDelete.data.records | Where-Object { $_.pageId -eq $pageId -and $_.deleted })[0]
if ($null -eq $changeTombstone) {
    $lastPage = [Math]::Max(1, [Math]::Ceiling([long]$changesAfterDelete.data.total / 50))
    $changesAfterDelete = Invoke-RestMethod -Method Get -Uri `
        "$docService/doc/internal/page/changes?since=2000-01-01T00:00:00&current=$lastPage&size=50" `
        -Headers $internalHeaders
    $changeTombstone = @($changesAfterDelete.data.records | Where-Object { $_.pageId -eq $pageId -and $_.deleted })[0]
}
Assert '59.internalTombstone' ($null -ne $changeTombstone) "(found=$($null -ne $changeTombstone), total=$($changesAfterDelete.data.total))"
Assert '60.internalDetailGone' ((Invoke-RestMethod -Method Get -Uri "$docService/doc/internal/page/$pageId" `
    -Headers $internalHeaders).code -eq 40401)

$folderDelete = Invoke-Api Delete "$base/doc/page/$folderId"
Assert '61.folderDelete' ($folderDelete.code -eq 0)
$treeAfter = Invoke-Api Get "$base/doc/space/$spaceId/tree"
Assert '62.treeEmpty' (@($treeAfter.data).Count -eq 0)

$spaceUpdate = Invoke-Api Put "$base/doc/space/$spaceId" @{
    name = "M1 Smoke $stamp updated"; description = 'updated'; visibility = 'PUBLIC'; icon = 'book'
}
Assert '63.spaceUpdate' ($spaceUpdate.code -eq 0 -and $spaceUpdate.data.name -match 'updated')
$spaceArchive = Invoke-Api Delete "$base/doc/space/$spaceId"
Assert '64.spaceArchive' ($spaceArchive.code -eq 0)
Assert '65.spaceArchived' ((Invoke-Api Get "$base/doc/space/$spaceId").data.status -eq 0)

Remove-Item -Force $tempFile, $downloadFile, $headersFile -ErrorAction SilentlyContinue

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions failed)"
    exit 1
}
Write-Host 'RESULT: PASSED'
