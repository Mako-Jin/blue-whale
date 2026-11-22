# Wave 5.75 M4 文档知识源冒烟（whale-doc → whale-agent 知识库）
# 前置：
#   1) gateway(8080)/auth(8081)/whale-doc(8095)/whale-agent(8090) 就绪；
#   2) bluewhale_agent 已执行 init.sql 与 migration-5.75-agent-doc.sql；
#   3) whale-agent 启动参数建议：
#        WHALE_AGENT_BUILD_ON_STARTUP=false
#        WHALE_AGENT_SCAN_INTERVAL_SECONDS=0
#        WHALE_AGENT_KNOWLEDGE_ENABLED=false
#        WHALE_AGENT_KNOWLEDGE_DOC_ENABLED=true
#        WHALE_AGENT_KNOWLEDGE_DOC_SYNC_INTERVAL_SECONDS=0
#        WHALE_AGENT_INDEX_DIR=<临时目录>
#   4) whale-agent 需能直连 whale-doc（WHALE_AGENT_DOC_URL，默认 127.0.0.1:8095）。
# 用法：pwsh -File scripts/smoke/bw-agent-doc-m4-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本创建公开/私有两个空间并归档收尾，不删除数据，便于排查。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$docService = 'http://127.0.0.1:8095'
$failures = 0

function Sync-Docs() {
    return Invoke-Api Post "$base/agent/knowledge/doc/v1/sync"
}

# 1) 登录与租户
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$meta = Invoke-Api Get "$base/doc/v1/meta"
Assert '2.docMeta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
$tenantId = $meta.data.tenantId
if ($null -eq $tenantId) { $tenantId = 1 }
$internalHeaders = @{ 'X-Tenant-Id' = "$tenantId"; 'X-User-Id' = '1'; 'X-Username' = 'admin' }

# 2) 公开空间 + 已发布页面（唯一关键词用于检索断言）
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$keyword = "M4DOC$stamp"

$space = Invoke-Api Post "$base/doc/v1/space" @{
    name        = "M4 Doc Knowledge $stamp"
    description = 'M4 doc knowledge smoke space'
    icon        = 'book'
    visibility  = 'PUBLIC'
}
Assert '3.spaceCreate' ($space.code -eq 0 -and $null -ne $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id

$page = Invoke-Api Post "$base/doc/v1/page" @{
    spaceId  = $spaceId; parentId = 0; title = "M4 Knowledge Page $stamp"; pageType = 'PAGE'
}
Assert '4.pageCreate' ($page.code -eq 0 -and $null -ne $page.data.id) "(code=$($page.code))"
$pageId = $page.data.id

$save = Invoke-Api Put "$base/doc/v1/page/$pageId" @{
    baseRevision = 0
    title        = "M4 Knowledge Page $stamp"
    contentJson  = @{
        type    = 'doc'
        content = @(@{
            type    = 'paragraph'
            content = @(@{ type = 'text'; text = "文档知识库唯一标记 $keyword 用于验证检索与下线。" })
        })
    }
}
Assert '5.pageSave' ($save.code -eq 0 -and $save.data.revision -eq 1) "(revision=$($save.data.revision))"

$publish = Invoke-Api Post "$base/doc/v1/page/$pageId/publish"
Assert '6.publish' ($publish.code -eq 0 -and $publish.data.status -eq 'PUBLISHED') "(status=$($publish.data.status))"

# 3) 变更流：公开已发布页面可采集
$changes = Invoke-RestMethod -Method Get -Uri `
    "$docService/doc/v1/internal/page/changes?current=1&size=200" -Headers $internalHeaders
$change = @($changes.data.records | Where-Object { [long]$_.pageId -eq [long]$pageId })[0]
Assert '7.changeCollectable' ($changes.code -eq 0 -and $null -ne $change -and $change.status -eq 'PUBLISHED' `
        -and $change.deleted -eq $false -and $change.spaceVisibility -eq 'PUBLIC' -and $change.spaceActive -eq $true) `
    "(status=$($change.status), visibility=$($change.spaceVisibility), active=$($change.spaceActive))"

# 4) 触发同步 → 台账 INDEXED → 检索命中并带页面 URL
$sync = Sync-Docs
Assert '8.docSync' ($sync.code -eq 0 -and $null -ne $sync.data) "(indexed=$($sync.data.indexed), failed=$($sync.data.failed))"

$ledger = Invoke-Api Get "$base/agent/knowledge/doc/v1/page?keyword=$stamp&current=1&size=20"
$row = @($ledger.data.records | Where-Object { [long]$_.pageId -eq [long]$pageId })[0]
Assert '9.ledgerIndexed' ($ledger.code -eq 0 -and $null -ne $row -and $row.indexStatus -eq 'INDEXED' `
        -and [int]$row.chunkCount -gt 0) `
    "(status=$($row.indexStatus), chunks=$($row.chunkCount))"
$expectedUrl = '/doc/space/' + $spaceId + '?pageId=' + $pageId
Assert '10.ledgerUrl' ($row.url -eq $expectedUrl) "(url=$($row.url))"

$hits = Invoke-Api Get "$base/agent/search?q=$keyword&kind=doc_page&topK=10"
$hit = @($hits.data | Where-Object { $_.path -eq "docpage:$pageId" })[0]
Assert '11.searchHit' ($hits.code -eq 0 -and $null -ne $hit) "(hits=$(@($hits.data).Count), code=$($hits.code))"
Assert '12.searchUrl' ($hit.url -eq $expectedUrl) "(url=$($hit.url))"
Assert '13.searchKind' ($hit.kind -eq 'doc_page') "(kind=$($hit.kind))"

# 5) 归档（不再可采集）→ 同步后索引下线
$archive = Invoke-Api Post "$base/doc/v1/page/$pageId/archive"
Assert '14.archive' ($archive.code -eq 0 -and $archive.data.status -eq 'ARCHIVED') "(status=$($archive.data.status))"
$syncArchived = Sync-Docs
Assert '15.syncArchived' ($syncArchived.code -eq 0) "(code=$($syncArchived.code))"
$ledgerArchived = @((Invoke-Api Get "$base/agent/knowledge/doc/v1/page?keyword=$stamp&current=1&size=20").data.records `
        | Where-Object { [long]$_.pageId -eq [long]$pageId })[0]
Assert '16.ledgerSkipped' ($ledgerArchived.indexStatus -eq 'SKIPPED' -and [int]$ledgerArchived.chunkCount -eq 0) `
    "(status=$($ledgerArchived.indexStatus), chunks=$($ledgerArchived.chunkCount))"
$hitsArchived = Invoke-Api Get "$base/agent/search?q=$keyword&kind=doc_page&topK=10"
Assert '17.searchRemovedAfterArchive' (@($hitsArchived.data | Where-Object { $_.path -eq "docpage:$pageId" }).Count -eq 0) `
    "(hits=$(@($hitsArchived.data).Count))"

# 6) 恢复 → 重新发布 → 同步重建；停用/启用
$restore = Invoke-Api Post "$base/doc/v1/page/$pageId/restore"
Assert '18.restore' ($restore.code -eq 0 -and $restore.data.status -eq 'DRAFT') "(status=$($restore.data.status))"
$publishAgain = Invoke-Api Post "$base/doc/v1/page/$pageId/publish"
Assert '19.publishAgain' ($publishAgain.code -eq 0) "(code=$($publishAgain.code))"
Sync-Docs | Out-Null
$hitsAgain = Invoke-Api Get "$base/agent/search?q=$keyword&kind=doc_page&topK=10"
Assert '20.searchBackAfterRepublish' (@($hitsAgain.data | Where-Object { $_.path -eq "docpage:$pageId" }).Count -ge 1) `
    "(hits=$(@($hitsAgain.data).Count))"

$disable = Invoke-Api Put "$base/agent/knowledge/doc/v1/$pageId/enabled?enabled=false"
Assert '21.disable' ($disable.code -eq 0) "(code=$($disable.code))"
$hitsDisabled = Invoke-Api Get "$base/agent/search?q=$keyword&kind=doc_page&topK=10"
Assert '22.searchEmptyWhenDisabled' (@($hitsDisabled.data | Where-Object { $_.path -eq "docpage:$pageId" }).Count -eq 0)

$enable = Invoke-Api Put "$base/agent/knowledge/doc/v1/$pageId/enabled?enabled=true"
Assert '23.enable' ($enable.code -eq 0) "(code=$($enable.code))"
$hitsEnabled = Invoke-Api Get "$base/agent/search?q=$keyword&kind=doc_page&topK=10"
Assert '24.searchBackWhenEnabled' (@($hitsEnabled.data | Where-Object { $_.path -eq "docpage:$pageId" }).Count -ge 1) `
    "(hits=$(@($hitsEnabled.data).Count))"

# 7) 私有空间页面不可采集
$privateSpace = Invoke-Api Post "$base/doc/v1/space" @{
    name        = "M4 Private $stamp"
    description = 'M4 private space should not be collected'
    icon        = 'lock'
    visibility  = 'MEMBER'
}
$privateSpaceId = $privateSpace.data.id
$privatePage = Invoke-Api Post "$base/doc/v1/page" @{
    spaceId  = $privateSpaceId; parentId = 0; title = "M4 Private Page $stamp"; pageType = 'PAGE'
}
$privatePageId = $privatePage.data.id
$privateKeyword = "M4PRIV$stamp"
Invoke-Api Put "$base/doc/v1/page/$privatePageId" @{
    baseRevision = 0
    title        = "M4 Private Page $stamp"
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = "私有内容 $privateKeyword" }) })
    }
} | Out-Null
$privatePublish = Invoke-Api Post "$base/doc/v1/page/$privatePageId/publish"
Assert '25.privatePublish' ($privatePublish.code -eq 0) "(code=$($privatePublish.code))"

$privateSync = Sync-Docs
Assert '26.privateSync' ($privateSync.code -eq 0) "(code=$($privateSync.code))"
$privateHits = Invoke-Api Get "$base/agent/search?q=$privateKeyword&kind=doc_page&topK=10"
Assert '27.privateNotIndexed' (@($privateHits.data | Where-Object { $_.path -eq "docpage:$privatePageId" }).Count -eq 0) `
    "(hits=$(@($privateHits.data).Count))"
$privateChanges = Invoke-RestMethod -Method Get -Uri `
    "$docService/doc/v1/internal/page/changes?current=1&size=200" -Headers $internalHeaders
$privateChange = @($privateChanges.data.records | Where-Object { [long]$_.pageId -eq [long]$privatePageId })[0]
Assert '28.privateChangeMarked' ($null -ne $privateChange -and $privateChange.spaceVisibility -eq 'MEMBER') `
    "(visibility=$($privateChange.spaceVisibility))"

# 8) 统计可观测
$stats = Invoke-Api Get "$base/agent/knowledge/stats"
Assert '29.statsDoc' ($stats.code -eq 0 -and [long]$stats.data.docIndexed -ge 1 `
        -and [long]$stats.data.docChunkCount -ge 1) `
    "(indexed=$($stats.data.docIndexed), chunks=$($stats.data.docChunkCount))"
Assert '30.statsLastRun' ($null -ne $stats.data.docLastRunAt -and $null -ne $stats.data.docLastResult) `
    "(lastRunAt=$($stats.data.docLastRunAt), result=$($stats.data.docLastResult))"

# 9) 收尾：删除页面并归档空间（数据保留便于排查）
Invoke-Api Delete "$base/doc/v1/page/$pageId" | Out-Null
Invoke-Api Delete "$base/doc/v1/page/$privatePageId" | Out-Null
Invoke-Api Delete "$base/doc/v1/space/$privateSpaceId" | Out-Null
Invoke-Api Delete "$base/doc/v1/space/$spaceId" | Out-Null

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions)"
    exit 1
}
Write-Host 'RESULT: PASSED'
