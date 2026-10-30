# 文件知识桶白名单冒烟（default-deny）
# 前置：
#   1) gateway(8080)/auth(8081)/whale-file(8085)/whale-agent(8090) 就绪；
#   2) whale-agent 使用默认白名单：
#        WHALE_AGENT_KNOWLEDGE_INCLUDED_BUCKETS=bluewhale,whale-archive
#        WHALE_AGENT_KNOWLEDGE_EXCLUDED_BUCKETS=whale-doc
#   3) 知识调度可关闭（本脚本通过手工 reindex 触发，不依赖定时任务）：
#        WHALE_AGENT_KNOWLEDGE_ENABLED=false
# 用法：pwsh -File scripts/smoke/bw-agent-knowledge-bucket-smoke.ps1
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本上传两个临时文本文件（白名单内/外各一个），断言白名单外文件不入库、
#       白名单内文件可检索；收尾删除文件元数据，不清理 agent 台账，便于排查。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$fileService = 'http://127.0.0.1:8085'
$failures = 0

function Json($value) {
    return ($value | ConvertTo-Json -Depth 30 -Compress)
}

function Invoke-Api($method, $uri, $body = $null) {
    $params = @{ Method = $method; Uri = $uri; Headers = $script:h }
    if ($null -ne $body) {
        $params.ContentType = 'application/json'
        $params.Body = Json $body
    }
    return Invoke-RestMethod @params
}

function Find-LedgerRow($keyword, $fileId) {
    $ledger = Invoke-Api Get "$base/agent/knowledge/files/page?keyword=$keyword&current=1&size=20"
    return @($ledger.data.records | Where-Object { [long]$_.fileId -eq [long]$fileId })[0]
}

# 1) 登录与租户上下文
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$me = Invoke-Api Get "$base/auth/me"
Assert '2.me' ($me.code -eq 0 -and $null -ne $me.data.tenantId) "(tenantId=$($me.data.tenantId))"
$tenantId = $me.data.tenantId
$internalHeaders = @{
    'X-Tenant-Id' = "$tenantId"
    'X-User-Id'   = "$($me.data.userId)"
    'X-Username'  = "$($me.data.username)"
}

# 2) 准备白名单外/内两个文本文件
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$outsideKeyword = "KBOUT$stamp"
$insideKeyword = "KBIN$stamp"
$tmpDir = Join-Path $env:TEMP "bw-kb-bucket-$stamp"
New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null
$outsidePath = Join-Path $tmpDir "kb-outside-$stamp.txt"
$insidePath = Join-Path $tmpDir "kb-inside-$stamp.txt"
Set-Content -LiteralPath $outsidePath -Encoding utf8 `
    -Value "知识库白名单验证 $outsideKeyword 该文件位于白名单外的桶，不应进入公开知识库。"
Set-Content -LiteralPath $insidePath -Encoding utf8 `
    -Value "知识库白名单验证 $insideKeyword 该文件位于默认业务桶，应进入公开知识库并可检索。"

# 3) 上传到白名单外的桶（default-deny 应跳过）
$outsideBucket = "kb-private-$stamp"
$outsideUpload = Invoke-RestMethod -Method Post -Uri "$fileService/file/internal/upload" `
    -Headers $internalHeaders -Form @{ file = Get-Item -LiteralPath $outsidePath; bucket = $outsideBucket }
Assert '3.uploadOutside' ($outsideUpload.code -eq 0 -and $null -ne $outsideUpload.data.id `
        -and $outsideUpload.data.bucket -eq $outsideBucket) `
    "(code=$($outsideUpload.code), bucket=$($outsideUpload.data.bucket))"
$outsideFileId = $outsideUpload.data.id

$outsideReindex = Invoke-Api Post "$base/agent/knowledge/files/$outsideFileId/reindex"
Assert '4.reindexOutsideSkipped' ($outsideReindex.code -eq 0 -and $outsideReindex.data -eq $false) `
    "(code=$($outsideReindex.code), data=$($outsideReindex.data))"
$outsideRow = Find-LedgerRow $outsideKeyword $outsideFileId
Assert '5.ledgerOutsideSkipped' ($null -ne $outsideRow -and $outsideRow.indexStatus -eq 'SKIPPED' `
        -and [int]$outsideRow.chunkCount -eq 0) `
    "(status=$($outsideRow.indexStatus), chunks=$($outsideRow.chunkCount))"
Assert '6.ledgerOutsideReason' ($outsideRow.lastError -eq '文件桶未纳入知识库白名单') `
    "(lastError=$($outsideRow.lastError))"
$outsideHits = Invoke-Api Get "$base/agent/search?q=$outsideKeyword&kind=file&topK=10"
Assert '7.searchOutsideEmpty' (@($outsideHits.data | Where-Object { $_.path -eq "file:$outsideFileId" }).Count -eq 0) `
    "(hits=$(@($outsideHits.data).Count))"

# 4) 上传到白名单内的默认业务桶（应入库并可检索）
$insideUpload = Invoke-RestMethod -Method Post -Uri "$fileService/file/internal/upload" `
    -Headers $internalHeaders -Form @{ file = Get-Item -LiteralPath $insidePath; bucket = 'bluewhale' }
Assert '8.uploadInside' ($insideUpload.code -eq 0 -and $null -ne $insideUpload.data.id `
        -and $insideUpload.data.bucket -eq 'bluewhale') `
    "(code=$($insideUpload.code), bucket=$($insideUpload.data.bucket))"
$insideFileId = $insideUpload.data.id

$insideReindex = Invoke-Api Post "$base/agent/knowledge/files/$insideFileId/reindex"
Assert '9.reindexInside' ($insideReindex.code -eq 0 -and $insideReindex.data -eq $true) `
    "(code=$($insideReindex.code), data=$($insideReindex.data))"
$insideRow = Find-LedgerRow $insideKeyword $insideFileId
Assert '10.ledgerInsideIndexed' ($null -ne $insideRow -and $insideRow.indexStatus -eq 'INDEXED' `
        -and [int]$insideRow.chunkCount -gt 0) `
    "(status=$($insideRow.indexStatus), chunks=$($insideRow.chunkCount))"
$insideHits = Invoke-Api Get "$base/agent/search?q=$insideKeyword&kind=file&topK=10"
$insideHit = @($insideHits.data | Where-Object { $_.path -eq "file:$insideFileId" })[0]
Assert '11.searchInsideHit' ($insideHits.code -eq 0 -and $null -ne $insideHit) `
    "(hits=$(@($insideHits.data).Count))"
Assert '12.searchInsideKind' ($insideHit.kind -eq 'file') "(kind=$($insideHit.kind))"

# 5) 幂等：白名单外文件重复重建仍应跳过
$outsideAgain = Invoke-Api Post "$base/agent/knowledge/files/$outsideFileId/reindex"
Assert '13.reindexOutsideIdempotent' ($outsideAgain.code -eq 0 -and $outsideAgain.data -eq $false) `
    "(data=$($outsideAgain.data))"

# 6) 收尾：删除文件元数据（失败不影响断言）
foreach ($fileId in @($outsideFileId, $insideFileId)) {
    try {
        Invoke-Api Delete "$base/file/$fileId" | Out-Null
    } catch {
        Write-Host "WARN cleanup file $fileId failed: $($_.Exception.Message)"
    }
}
Remove-Item -LiteralPath $tmpDir -Recurse -Force -ErrorAction SilentlyContinue

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions)"
    exit 1
}
Write-Host 'RESULT: PASSED'
