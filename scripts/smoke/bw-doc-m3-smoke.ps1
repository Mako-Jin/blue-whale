# Wave 5.75 M3 文档服务冒烟（模板/复制/导入导出/分享/审批/归档/通知）
# 前置：
#   1) gateway(8080)/auth(8081)/whale-doc(8095) 就绪，MySQL 可用；
#   2) bluewhale_doc 已执行 migration-5.75-doc-m3.sql，bluewhale_auth 已执行 migration-5.75-doc-m3.sql；
#   3) 本脚本默认验证「审批/通知/归档降级关闭」路径，无需额外开关。
# 可选：以 WHALE_DOC_APPROVAL_ENABLED=true 启动并配置流程定义后，可手工验证待审批流程；
#      本脚本第 15 项会自动识别开关并分别断言。
# 用法：pwsh -File scripts/smoke/bw-doc-m3-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本创建并归档一个 PUBLIC 空间；不删除空间，便于排查残留数据。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$docService = 'http://127.0.0.1:8095'
$failures = 0

function New-Page($spaceId, $parentId, $title) {
    return Invoke-Api Post "$base/doc/v1/page" @{
        spaceId = $spaceId; parentId = $parentId; title = $title; pageType = 'PAGE'
    }
}

# 1) 登录与元信息
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$meta = Invoke-Api Get "$base/doc/v1/meta"
Assert '2.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
$approvalEnabled = [bool]$meta.data.approvalEnabled

# 2) 空间与页面
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$space = Invoke-Api Post "$base/doc/v1/space" @{
    name = "M3 Smoke $stamp"; description = 'M3 template/copy/transfer/share smoke space'
    icon = 'book'; visibility = 'PUBLIC'
}
Assert '3.spaceCreate' ($space.code -eq 0 -and $null -ne $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id

$pageA = New-Page $spaceId 0 'M3 Page A'
Assert '4.pageCreateA' ($pageA.code -eq 0 -and $null -ne $pageA.data.id) "(code=$($pageA.code))"
$pageAId = $pageA.data.id

$saveA = Invoke-Api Put "$base/doc/v1/page/$pageAId" @{
    baseRevision = 0; title = 'M3 Page A'
    contentJson = @{
        type = 'doc'
        content = @(
            @{ type = 'heading'; attrs = @{ level = 1 }; content = @(@{ type = 'text'; text = 'M3 export title' }) },
            @{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'M3 export body' }) }
        )
    }
}
Assert '5.pageSaveA' ($saveA.code -eq 0 -and ([long]$saveA.data.revision) -eq 1) "(revision=$($saveA.data.revision))"

$pageB = New-Page $spaceId $pageAId 'M3 Page B'
Assert '6.pageCreateB' ($pageB.code -eq 0) "(code=$($pageB.code))"
$pageBId = $pageB.data.id

# 3) 模板：创建、列表、应用
$template = Invoke-Api Post "$base/doc/v1/template" @{
    spaceId = $spaceId; name = "M3 Template $stamp"; description = 'smoke template'
    pageType = 'PAGE'; icon = 'doc'
    contentJson = @{
        type = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'template body' }) })
    }
}
Assert '7.templateCreate' ($template.code -eq 0 -and $null -ne $template.data.id) "(code=$($template.code), msg=$($template.message))"
$templateId = $template.data.id

$templateList = Invoke-Api Get "$base/doc/v1/template?spaceId=$spaceId"
Assert '8.templateList' ($templateList.code -eq 0 -and @($templateList.data | Where-Object { $_.id -eq $templateId }).Count -eq 1) `
    "(count=$(@($templateList.data).Count))"

$apply = Invoke-Api Post "$base/doc/v1/template/$templateId/apply" @{
    spaceId = $spaceId; parentId = 0; title = 'M3 From Template'
}
Assert '9.templateApply' ($apply.code -eq 0 -and $null -ne $apply.data.id) "(code=$($apply.code), msg=$($apply.message))"
$appliedId = $apply.data.id
$appliedDetail = Invoke-Api Get "$base/doc/v1/page/$appliedId"
Assert '10.templateContent' ($appliedDetail.code -eq 0 -and $appliedDetail.data.contentJson -match 'template body')

# 4) 页面复制（含子树）
$duplicate = Invoke-Api Post "$base/doc/v1/page/$pageAId/duplicate" @{
    spaceId = $spaceId; parentId = 0; includeChildren = $true
}
Assert '11.duplicateRoot' ($duplicate.code -eq 0 -and $null -ne $duplicate.data.id) "(code=$($duplicate.code), msg=$($duplicate.message))"
$duplicateId = $duplicate.data.id
Assert '12.duplicateTitle' ($duplicate.data.title -match '副本') "(title=$($duplicate.data.title))"
Assert '13.duplicateSlug' ($duplicate.data.slug -ne $pageA.data.slug) "(slug=$($duplicate.data.slug))"

$tree = Invoke-Api Get "$base/doc/v1/space/$spaceId/tree"
$dupNode = @($tree.data | Where-Object { $_.id -eq $duplicateId })[0]
Assert '14.duplicateSubtree' ($null -ne $dupNode -and @($dupNode.children).Count -eq 1) `
    "(children=$(@($dupNode.children).Count))"

# 5) 导出 Markdown / HTML
$md = Invoke-WebRequest -Method Get -Uri "$base/doc/v1/page/$pageAId/export?format=md" -Headers $script:h
Assert '15.exportMarkdown' ($md.StatusCode -eq 200 -and $md.Content -match '# M3 export title' -and $md.Content -match 'M3 export body')
$html = Invoke-WebRequest -Method Get -Uri "$base/doc/v1/page/$pageAId/export?format=html" -Headers $script:h
Assert '16.exportHtml' ($html.StatusCode -eq 200 -and $html.Content -match '<h1>M3 export title</h1>')

# 6) 导入 Markdown 覆盖正文
$tmpFile = Join-Path $env:TEMP "bw-doc-m3-import-$stamp.md"
Set-Content -Path $tmpFile -Value "# Imported heading`n`nImported body text" -Encoding UTF8
$import = Invoke-RestMethod -Method Post -Uri "$base/doc/v1/page/$pageAId/import" -Headers $script:h `
    -Form @{ file = Get-Item $tmpFile; format = 'md' }
Assert '17.importMarkdown' ($import.code -eq 0 -and ([long]$import.data.revision) -eq 2) `
    "(code=$($import.code), revision=$($import.data.revision), msg=$($import.message))"
$imported = Invoke-Api Get "$base/doc/v1/page/$pageAId"
Assert '18.importContent' ($imported.data.contentJson -match 'Imported body text')
Remove-Item $tmpFile -Force -ErrorAction SilentlyContinue

# 7) 登录内分享：创建、列表、令牌读取、撤销即失效
$share = Invoke-Api Post "$base/doc/v1/page/$pageAId/share" @{ expireDays = 0 }
Assert '19.shareCreate' ($share.code -eq 0 -and $share.data.shareToken) "(code=$($share.code), msg=$($share.message))"
$shareId = $share.data.id
$shareToken = $share.data.shareToken
$shareList = Invoke-Api Get "$base/doc/v1/page/$pageAId/share"
Assert '20.shareList' ($shareList.code -eq 0 -and @($shareList.data | Where-Object { $_.id -eq $shareId }).Count -eq 1) `
    "(count=$(@($shareList.data).Count))"
$resolve = Invoke-Api Get "$base/doc/v1/share/$shareToken"
Assert '21.shareResolve' ($resolve.code -eq 0 -and $resolve.data.canEdit -eq $false -and $resolve.data.id -eq $pageAId) `
    "(code=$($resolve.code), canEdit=$($resolve.data.canEdit))"
$revoke = Invoke-Api Delete "$base/doc/v1/page/$pageAId/share/$shareId"
Assert '22.shareRevoke' ($revoke.code -eq 0) "(code=$($revoke.code))"
$resolveAfterRevoke = Invoke-Api Get "$base/doc/v1/share/$shareToken"
Assert '23.shareRevokedInvalid' ($resolveAfterRevoke.code -eq 40403) "(code=$($resolveAfterRevoke.code))"

# 8) 发布审批：开关关闭时提交即发布；开启时进入待审批
$submit = Invoke-Api Post "$base/doc/v1/page/$pageAId/submit-approval"
if ($approvalEnabled) {
    Assert '24.submitPending' ($submit.code -eq 0 -and $submit.data.status -eq 'PENDING_APPROVAL') `
        "(code=$($submit.code), status=$($submit.data.status), msg=$($submit.message))"
    $approval = Invoke-Api Get "$base/doc/v1/page/$pageAId/approval"
    Assert '25.approvalPending' ($approval.code -eq 0 -and $approval.data.approvalStatus -eq 'PENDING' -and $approval.data.approvalEnabled -eq $true) `
        "(approvalStatus=$($approval.data.approvalStatus))"
} else {
    Assert '24.submitPublishes' ($submit.code -eq 0 -and $submit.data.status -eq 'PUBLISHED') `
        "(code=$($submit.code), status=$($submit.data.status), msg=$($submit.message))"
    $approval = Invoke-Api Get "$base/doc/v1/page/$pageAId/approval"
    Assert '25.approvalDisabled' ($approval.code -eq 0 -and $approval.data.approvalEnabled -eq $false) `
        "(approvalEnabled=$($approval.data.approvalEnabled))"
}

# 9) 审批内网回调幂等（不存在的实例返回 false，不产生副作用）
$callbackBody = '{"instanceId":999999999,"status":1}'
$callback = Invoke-RestMethod -Method Post -Uri "$docService/doc/v1/internal/approval/callback" `
    -Headers @{ 'X-Tenant-Id' = '1'; 'X-User-Id' = '1' } -ContentType 'application/json' -Body $callbackBody
Assert '26.callbackIdempotent' ($callback.code -eq 0 -and $callback.data -eq $false) `
    "(code=$($callback.code), data=$($callback.data), msg=$($callback.message))"

# 10) 通知降级：评论 @ 提及时消息开关关闭，不影响评论创建
$comment = Invoke-Api Post "$base/doc/v1/page/$pageAId/comment" @{
    content = '@1 M3 mention smoke'; anchorText = 'M3 mention'
}
Assert '27.commentMentionDegrade' ($comment.code -eq 0 -and $null -ne $comment.data.id) `
    "(code=$($comment.code), msg=$($comment.message))"

# 11) 归档：归档页面；归档开关关闭时不产生入档副作用
$archive = Invoke-Api Post "$base/doc/v1/page/$pageAId/archive"
Assert '28.archivePage' ($archive.code -eq 0 -and $archive.data.status -eq 'ARCHIVED') `
    "(code=$($archive.code), status=$($archive.data.status))"
$archivedDetail = Invoke-Api Get "$base/doc/v1/page/$pageAId"
Assert '29.archiveNoItemWhenDisabled' ($archivedDetail.data.archiveItemId -eq $null) `
    "(archiveItemId=$($archivedDetail.data.archiveItemId))"
$restore = Invoke-Api Post "$base/doc/v1/page/$pageAId/restore"
Assert '30.restorePage' ($restore.code -eq 0 -and $restore.data.status -eq 'DRAFT') `
    "(code=$($restore.code), status=$($restore.data.status))"

# 12) 空间归档收尾
$spaceArchive = Invoke-Api Delete "$base/doc/v1/space/$spaceId"
Assert '31.spaceArchive' ($spaceArchive.code -eq 0)

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions failed)"
    exit 1
}
Write-Host 'RESULT: PASSED'
