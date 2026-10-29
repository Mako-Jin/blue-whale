# ============================================================
# Wave 5.74 M5 上线硬化冒烟：
#   导出 XSS 过滤 / 附件下载安全头 / 跨页附件 IDOR / 20 并发保存乐观锁 /
#   树-详情-搜索时延 / Prometheus 指标 / 孤儿附件引用清理
#
# 前置：
#   1) gateway(8080)/auth(8081)/whale-doc(8095)/whale-file(8085) 就绪；MySQL 与 Redis 可用；
#   2) bluewhale_doc 已执行 init.sql 与 M2b/M3/M5 迁移；
#   3) 需要 sys:doc:* 权限的管理员账号（默认 admin/123456）。
#
# 用法：pwsh -File scripts/smoke/bw-doc-m5-smoke.ps1
# 退出码：0 全部通过；1 断言失败；未设置 MYSQL_PASSWORD 时跳过孤儿清理数据库断言。
# ============================================================


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

# 1) 登录与空间 / 页面准备
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body '{"username":"admin","password":"123456"}'
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }
$token = $login.data.accessToken

$meta = Invoke-Api Get "$base/doc/meta"
Assert '2.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
$tenantId = $meta.data.tenantId

$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$space = Invoke-Api Post "$base/doc/space" @{
    name = "M5 Hardening $stamp"; description = 'M5 hardening smoke space'; icon = 'book'; visibility = 'PUBLIC'
}
Assert '3.spaceCreate' ($space.code -eq 0 -and $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id

$pageA = Invoke-Api Post "$base/doc/page" @{
    spaceId = $spaceId; parentId = 0; title = "M5 Page A $stamp"; pageType = 'PAGE'
}
Assert '4.pageACreate' ($pageA.code -eq 0 -and $pageA.data.id) "(code=$($pageA.code))"
$pageAId = $pageA.data.id

$pageB = Invoke-Api Post "$base/doc/page" @{
    spaceId = $spaceId; parentId = 0; title = "M5 Page B $stamp"; pageType = 'PAGE'
}
Assert '5.pageBCreate' ($pageB.code -eq 0 -and $pageB.data.id) "(code=$($pageB.code))"
$pageBId = $pageB.data.id

# 2) 正文含脚本文本与 javascript: 链接，验证导出不产生可执行内容
$keyword = "M5SEARCH$stamp"
$maliciousText = "<script>alert('xss')</script> $keyword"
$saveA = Invoke-Api Put "$base/doc/page/$pageAId" @{
    baseRevision = $pageA.data.revision
    title = "M5 Page A $stamp"
    contentJson = @{
        type = 'doc'
        content = @(
            @{
                type = 'paragraph'
                content = @(
                    @{ type = 'text'; text = $maliciousText }
                )
            },
            @{
                type = 'paragraph'
                content = @(
                    @{
                        type = 'text'
                        text = 'executable link'
                        marks = @(@{ type = 'link'; attrs = @{ href = 'javascript:alert(1)'; target = '_blank' } })
                    }
                )
            }
        )
    }
}
Assert '6.pageSaveXss' ($saveA.code -eq 0 -and $saveA.data.revision -eq 1) "(revision=$($saveA.data.revision))"
$revisionAfterSave = $saveA.data.revision

$exportHtml = Invoke-WebRequest -Uri "$base/doc/page/$pageAId/export?format=html" -Headers $script:h
$html = [string]$exportHtml.Content
Assert '7.exportHtmlEscapesScript' ($html -notmatch '<script>' -and $html -match '&lt;script&gt;') "(len=$($html.Length))"
Assert '8.exportHtmlDropsJavascriptHref' ($html -notmatch 'javascript:') ''
$exportMd = Invoke-WebRequest -Uri "$base/doc/page/$pageAId/export?format=md" -Headers $script:h
$markdown = [string]$exportMd.Content
Assert '9.exportMarkdownDropsJavascriptHref' ($markdown -notmatch 'javascript:') ''

# 3) SVG 附件：禁止 inline、nosniff、跨页 IDOR 拒绝
$svgPath = Join-Path $env:TEMP "m5-smoke-$stamp.svg"
Set-Content -LiteralPath $svgPath -Value '<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"></svg>' -Encoding UTF8
$upload = Invoke-RestMethod -Method Post -Uri "$base/doc/page/$pageAId/attachment" -Headers $script:h `
    -Form @{ file = Get-Item -LiteralPath $svgPath; usageType = 'ATTACHMENT' }
Assert '10.attachmentUpload' ($upload.code -eq 0 -and $upload.data.id) "(code=$($upload.code), msg=$($upload.message))"
$attachmentId = $upload.data.id
Remove-Item -LiteralPath $svgPath -ErrorAction SilentlyContinue

$download = Invoke-WebRequest -Uri "$base/doc/page/$pageAId/attachment/$attachmentId/content" -Headers $script:h
Assert '11.attachmentDisposition' ([string]$download.Headers['Content-Disposition'] -match 'attachment') `
    "($($download.Headers['Content-Disposition']))"
Assert '12.attachmentNosniff' ([string]$download.Headers['X-Content-Type-Options'] -eq 'nosniff') ''
Assert '13.attachmentSvgNotInline' ([string]$download.Headers['Content-Type'] -notmatch 'inline') `
    "($($download.Headers['Content-Type']))"

$idor = Invoke-WebRequest -SkipHttpErrorCheck -Uri "$base/doc/page/$pageBId/attachment/$attachmentId/content" -Headers $script:h
$idorBody = $null
try { $idorBody = $idor.Content | ConvertFrom-Json } catch { }
Assert '14.crossPageAttachmentDenied' ($null -ne $idorBody -and [int]$idorBody.code -ne 0) `
    "(status=$($idor.StatusCode), code=$($idorBody.code))"

# 4) 乐观锁：陈旧 baseRevision 拒绝
$stale = Invoke-Api Put "$base/doc/page/$pageAId" @{ baseRevision = 0; title = "M5 stale $stamp" }
Assert '15.staleSaveConflict' ($stale.code -eq 40901) "(code=$($stale.code))"

$detail = Invoke-Api Get "$base/doc/page/$pageAId"
Assert '16.detailRevision' ($detail.code -eq 0 -and $detail.data.revision -eq $revisionAfterSave) `
    "(revision=$($detail.data.revision))"
$baseRevision = $detail.data.revision

# 5) 20 路并发保存同一 baseRevision：只允许 1 个成功，其余 40901
$jobs = 20
$worker = {
    param($uri, $token, $baseRevision, $title)
    $headers = @{ Authorization = "Bearer $token" }
    $payload = @{
        baseRevision = $baseRevision
        title = $title
        contentJson = @{
            type = 'doc'
            content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = $title }) })
        }
    } | ConvertTo-Json -Depth 10 -Compress
    $resp = Invoke-WebRequest -SkipHttpErrorCheck -Method Put -Uri $uri -Headers $headers `
        -ContentType 'application/json' -Body $payload
    try { return [int]($resp.Content | ConvertFrom-Json).code } catch { return -1 }
}
$pool = [runspacefactory]::CreateRunspacePool(1, $jobs)
$pool.Open()
$runs = @()
for ($i = 0; $i -lt $jobs; $i++) {
    $ps = [powershell]::Create()
    $ps.RunspacePool = $pool
    [void]$ps.AddScript($worker).AddArgument("$base/doc/page/$pageAId").AddArgument($token).AddArgument($baseRevision).AddArgument("M5 concurrent $i $stamp")
    $runs += @{ P = $ps; H = $ps.BeginInvoke() }
}
$codes = @()
foreach ($run in $runs) {
    $out = @($run.P.EndInvoke($run.H))
    $codes += [int]$out[-1]
    $run.P.Dispose()
}
$pool.Close()
$pool.Dispose()
$success = @($codes | Where-Object { $_ -eq 0 }).Count
$conflict = @($codes | Where-Object { $_ -eq 40901 }).Count
Assert '17.concurrentSaveSingleWinner' ($success -eq 1) "(success=$success, total=$jobs)"
Assert '18.concurrentSaveConflicts' ($conflict -eq $jobs - 1) "(conflict=$conflict, codes=$($codes -join ','))"
$afterConcurrent = Invoke-Api Get "$base/doc/page/$pageAId"
Assert '19.concurrentRevisionPlusOne' (([long]$afterConcurrent.data.revision) -eq ([long]$baseRevision + 1)) `
    "(revision=$($afterConcurrent.data.revision))"

# 6) 发布后检查树 / 详情 / 搜索时延与全文检索
$publish = Invoke-Api Post "$base/doc/page/$pageAId/publish"
Assert '20.publish' ($publish.code -eq 0) "(code=$($publish.code))"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$tree = Invoke-Api Get "$base/doc/space/$spaceId/tree"
$sw.Stop()
Assert '21.treeLatency' ($tree.code -eq 0 -and $sw.ElapsedMilliseconds -lt 1500) "($($sw.ElapsedMilliseconds)ms)"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$pageDetail = Invoke-Api Get "$base/doc/page/$pageAId"
$sw.Stop()
Assert '22.detailLatency' ($pageDetail.code -eq 0 -and $sw.ElapsedMilliseconds -lt 1000) "($($sw.ElapsedMilliseconds)ms)"

$sw = [System.Diagnostics.Stopwatch]::StartNew()
$search = Invoke-Api Get "$base/doc/search?keyword=$keyword&spaceId=$spaceId&current=1&size=20"
$sw.Stop()
Assert '23.searchLatency' ($search.code -eq 0 -and $sw.ElapsedMilliseconds -lt 1500) "($($sw.ElapsedMilliseconds)ms)"
Assert '24.searchFulltextHit' ($search.code -eq 0 -and [int]$search.data.total -ge 1) "(total=$($search.data.total))"

# 7) Prometheus 指标暴露
$prom = Invoke-WebRequest -Uri "$docService/actuator/prometheus"
$promText = [string]$prom.Content
Assert '25.prometheusEndpoint' ($prom.StatusCode -eq 200 -and $promText.Length -gt 1000) "(status=$($prom.StatusCode))"
Assert '26.metricSaveConflict' ($promText -match 'doc_page_save_conflict_total') ''
Assert '27.metricSearchTimer' ($promText -match 'doc_search_seconds_count' -and $promText -match 'doc_search_seconds_bucket') ''
Assert '28.metricOrphanCleanup' ($promText -match 'doc_orphan_attachment_removed_total') ''

# 8) 孤儿附件引用清理（需要 MYSQL_PASSWORD）
$mysqlBin = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'D:\tools\installer\mysql\bin\mysql.exe' }
if ($env:MYSQL_PASSWORD -and (Test-Path $mysqlBin)) {
    $env:MYSQL_PWD = $env:MYSQL_PASSWORD
    function Invoke-Sql($sql) {
        $out = & $mysqlBin --host=127.0.0.1 --port=3306 --user=root -N -B --default-character-set=utf8mb4 -D bluewhale_doc -e $sql 2>&1
        if ($LASTEXITCODE -ne 0) { throw "MySQL 执行失败：$out" }
        return ($out | Out-String).Trim()
    }
    $orphanId = [long]$stamp
    $orphanFileId = $orphanId + 100000000
    Invoke-Sql "INSERT INTO doc_page_attachment (id, tenant_id, page_id, file_id, original_name, content_type, file_size, usage_type, sort_no, create_time, update_time, deleted) VALUES ($orphanId, $tenantId, 777777777, $orphanFileId, 'orphan.svg', 'image/svg+xml', 10, 'ATTACHMENT', 0, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY, 0);" | Out-Null
    $cleanup = Invoke-RestMethod -Method Post -Uri "$docService/doc/internal/ops/orphan-attachment-cleanup"
    Assert '29.orphanCleanupTrigger' ($cleanup.code -eq 0 -and [int]$cleanup.data.removed -ge 1) `
        "(removed=$($cleanup.data.removed))"
    $orphanLeft = Invoke-Sql "SELECT COUNT(*) FROM doc_page_attachment WHERE id=$orphanId;"
    Assert '30.orphanCleanupRemoved' ([int]$orphanLeft -eq 0) "(left=$orphanLeft)"
} else {
    Write-Host 'SKIP: 未设置 MYSQL_PASSWORD 或未找到 mysql 客户端，跳过孤儿附件清理数据库断言'
}

Write-Host ''
if ($failures -eq 0) {
    Write-Host 'RESULT: PASSED'
    exit 0
}
Write-Host "RESULT: FAILED ($failures)"
exit 1
