# Wave 5.74 M2 文档服务冒烟（回收站 + 实时协作 Yjs）
# 前置：
#   1) gateway(8080)/auth(8081)/whale-doc(8095) 就绪，MySQL 与 Redis 可用；
#   2) bluewhale_doc 已执行 migration-5.74-doc.sql 与 migration-5.74-doc-m2.sql；
#   3) whale-doc 启动时设置 WHALE_DOC_COLLAB_ENABLED=true（否则协作断言会失败并提示）。
# 用法：pwsh -File scripts/smoke/bw-doc-m2-smoke.ps1
# 注意：WS 断言建议使用 PowerShell 7（pwsh）。Windows PowerShell 5.1 的
#       ClientWebSocket 会拒绝 Tomcat 返回的 `Connection: upgrade, keep-alive` 头，
#       浏览器客户端不受影响。
# 退出码：0 全部通过；1 断言失败。
# 说明：脚本会创建并归档一个 PUBLIC 空间；不删除空间，便于排查残留数据。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$docService = 'http://127.0.0.1:8095'
$docWs = 'ws://127.0.0.1:8095/doc/ws'
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

function New-CollabSocket($ticket, $clientId) {
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $uri = [Uri]"$docWs`?ticket=$([Uri]::EscapeDataString($ticket))&clientId=$clientId"
    # ConnectAsync 的 GetResult() 可能返回 VoidTaskResult，必须抑制，避免污染函数返回值
    [void]$socket.ConnectAsync($uri, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
    return $socket
}

function Send-Frame($socket, $payload) {
    $bytes = [Text.Encoding]::UTF8.GetBytes((Json $payload))
    $segment = [ArraySegment[byte]]::new($bytes)
    $socket.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true,
        [Threading.CancellationToken]::None).GetAwaiter().GetResult() | Out-Null
}

function Receive-Frame($socket, $timeoutMs = 6000) {
    $buffer = [byte[]]::new(65536)
    $segment = [ArraySegment[byte]]::new($buffer)
    $task = $socket.ReceiveAsync($segment, [Threading.CancellationToken]::None)
    if (-not $task.Wait($timeoutMs)) {
        throw 'WS 接收超时'
    }
    $result = $task.GetAwaiter().GetResult()
    if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
        return @{ type = 'close'; count = $result.Count }
    }
    $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
    return ($text | ConvertFrom-Json)
}

function Receive-Until($socket, $type, $timeoutMs = 6000) {
    $deadline = (Get-Date).AddMilliseconds($timeoutMs)
    while ((Get-Date) -lt $deadline) {
        $frame = Receive-Frame $socket 1000
        if ($frame.type -eq $type) {
            return $frame
        }
    }
    throw "WS 未收到 $type 帧"
}

# 1) 登录与协作开关
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:h = @{ Authorization = "Bearer $($login.data.accessToken)" }

$meta = Invoke-Api Get "$base/doc/meta"
Assert '2.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(service=$($meta.data.service))"
Assert '3.collabEnabled' ($meta.data.collabEnabled -eq $true) `
    '(请以 WHALE_DOC_COLLAB_ENABLED=true 启动 whale-doc)'

# 2) 空间与子树
$stamp = [DateTime]::Now.ToString('yyyyMMddHHmmss')
$space = Invoke-Api Post "$base/doc/space" @{
    name        = "M2 Smoke $stamp"
    description = 'M2 recycle + collab smoke space'
    icon        = 'book'
    visibility  = 'PUBLIC'
}
Assert '4.spaceCreate' ($space.code -eq 0 -and $null -ne $space.data.id) "(code=$($space.code), msg=$($space.message))"
$spaceId = $space.data.id

$folder = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = 0; title = 'M2 Folder'; pageType = 'FOLDER'
}
Assert '5.folderCreate' ($folder.code -eq 0 -and $folder.data.id) "(code=$($folder.code))"
$folderId = $folder.data.id
$folderTitle = $folder.data.title

$pageA = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = $folderId; title = 'M2 Page A'; pageType = 'PAGE'
}
Assert '6.pageCreateA' ($pageA.code -eq 0 -and $pageA.data.id) "(code=$($pageA.code), slug=$($pageA.data.slug))"
$pageAId = $pageA.data.id
$pageASlug = $pageA.data.slug

$pageB = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = $pageAId; title = 'M2 Page B'; pageType = 'PAGE'
}
Assert '7.pageCreateB' ($pageB.code -eq 0 -and $pageB.data.id) "(code=$($pageB.code))"
$pageBId = $pageB.data.id

# 3) 回收站：整棵子树软删除、列表、恢复、彻底清除
$deleteA = Invoke-Api Delete "$base/doc/page/$pageAId"
Assert '8.subtreeTrash' ($deleteA.code -eq 0) "(code=$($deleteA.code))"

$treeAfterTrash = Invoke-Api Get "$base/doc/space/$spaceId/tree"
$folderNode = @($treeAfterTrash.data | Where-Object { $_.id -eq $folderId })[0]
Assert '9.treeOmitsTrash' (@($folderNode.children).Count -eq 0) "(children=$(@($folderNode.children).Count))"
Assert '10.trashDetailGone' ((Invoke-Api Get "$base/doc/page/$pageAId").code -eq 40401)
Assert '11.trashChildGone' ((Invoke-Api Get "$base/doc/page/$pageBId").code -eq 40401)

$recycle = Invoke-Api Get "$base/doc/space/$spaceId/recycle/page?current=1&size=20"
$trashRoot = @($recycle.data.records | Where-Object { $_.pageId -eq $pageAId })[0]
Assert '12.recycleRoot' ($recycle.code -eq 0 -and $null -ne $trashRoot -and $trashRoot.childCount -eq 1) `
    "(total=$($recycle.data.total), childCount=$($trashRoot.childCount))"
Assert '13.recycleParent' ($trashRoot.parentTitle -eq $folderTitle) "(parentTitle=$($trashRoot.parentTitle))"

$restoreChild = Invoke-Api Post "$base/doc/page/$pageBId/trash/restore"
Assert '14.restoreNonRoot' ($restoreChild.code -eq 40008) "(code=$($restoreChild.code))"

$restoreRoot = Invoke-Api Post "$base/doc/page/$pageAId/trash/restore"
Assert '15.restoreRoot' ($restoreRoot.code -eq 0) "(code=$($restoreRoot.code))"
$treeAfterRestore = Invoke-Api Get "$base/doc/space/$spaceId/tree"
$folderAfter = @($treeAfterRestore.data | Where-Object { $_.id -eq $folderId })[0]
$nodeA = @($folderAfter.children | Where-Object { $_.id -eq $pageAId })[0]
Assert '16.treeRestored' ($null -ne $nodeA -and @($nodeA.children | Where-Object { $_.id -eq $pageBId }).Count -eq 1)
$detailA = Invoke-Api Get "$base/doc/page/$pageAId"
Assert '17.slugRestored' ($detailA.code -eq 0 -and $detailA.data.slug -eq $pageASlug) `
    "(slug=$($detailA.data.slug))"

$deleteA2 = Invoke-Api Delete "$base/doc/page/$pageAId"
Assert '18.trashAgain' ($deleteA2.code -eq 0)
$purgeA = Invoke-Api Delete "$base/doc/page/$pageAId/purge"
Assert '19.purgeSubtree' ($purgeA.code -eq 0) "(code=$($purgeA.code))"
$recycleAfterPurge = Invoke-Api Get "$base/doc/space/$spaceId/recycle/page?current=1&size=20"
Assert '20.recycleEmptyAfterPurge' (@($recycleAfterPurge.data.records | Where-Object { $_.pageId -eq $pageAId }).Count -eq 0) `
    "(total=$($recycleAfterPurge.data.total))"
Assert '21.purgedGone' ((Invoke-Api Get "$base/doc/page/$pageAId").code -eq 40401)

# 4) 实时协作：ticket、ready、leader 快照、协作期 REST 拦截、断线回退
$collabPage = Invoke-Api Post "$base/doc/page" @{
    spaceId  = $spaceId; parentId = 0; title = 'M2 Collab Page'; pageType = 'PAGE'
}
Assert '22.collabPageCreate' ($collabPage.code -eq 0 -and $collabPage.data.id) "(code=$($collabPage.code))"
$collabPageId = $collabPage.data.id
# 后端 Long 序列化为字符串，比较前统一转 [long]
$baseRevision = [long]$collabPage.data.revision
Assert '23.collabRevisionZero' ($baseRevision -eq 0) "(revision=$baseRevision)"

$ticket = Invoke-Api Post "$base/doc/ws-ticket" @{ pageId = $collabPageId }
Assert '24.wsTicket' ($ticket.code -eq 0 -and $ticket.data.ticket) "(code=$($ticket.code), msg=$($ticket.message))"
Assert '25.wsTicketTtl' ($ticket.data.expiresIn -eq 60) "(expiresIn=$($ticket.data.expiresIn))"
$wsTicket = $ticket.data.ticket

$clientId = "smoke-$stamp"
$socket = New-CollabSocket $wsTicket $clientId
$ready = Receive-Until $socket 'ready'
Assert '26.wsReady' ($ready.pageId -eq $collabPageId -and @($ready.updates).Count -eq 0) `
    "(pageId=$($ready.pageId), updates=$(@($ready.updates).Count))"
Assert '27.wsLeader' ($ready.leader -eq $true) "(leader=$($ready.leader))"
Assert '28.wsSnapshotSeconds' ($ready.snapshotSeconds -gt 0) "(snapshotSeconds=$($ready.snapshotSeconds))"

$snapshotTitle = 'M2 collab snapshot'
Send-Frame $socket @{
    type        = 'snapshot'
    title       = $snapshotTitle
    revision    = $ready.revision
    contentJson = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'M2 collab content' }) })
    }
}
$ack = Receive-Until $socket 'snapshot-ack'
$ackRevision = [long]$ack.revision
Assert '29.snapshotAck' ($ack.skipped -eq $false -and $ackRevision -eq ([long]$ready.revision + 1)) `
    "(skipped=$($ack.skipped), readyRevision=$($ready.revision), ackRevision=$ackRevision)"

$collabDetail = Invoke-Api Get "$base/doc/page/$collabPageId"
Assert '30.snapshotPersisted' (([long]$collabDetail.data.revision) -eq $ackRevision -and $collabDetail.data.title -eq $snapshotTitle) `
    "(revision=$($collabDetail.data.revision), title=$($collabDetail.data.title))"
Assert '31.snapshotContent' ($collabDetail.data.contentJson -match 'M2 collab content')

$restSave = Invoke-Api Put "$base/doc/page/$collabPageId" @{
    baseRevision = $ack.revision
    title        = 'M2 rest while collab'
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'should be blocked' }) })
    }
}
Assert '32.restBlockedByCollab' ($restSave.code -eq 40902) "(code=$($restSave.code), msg=$($restSave.message))"

# ticket 已被消费，复用应被握手拒绝
$reuseRejected = $false
try {
    $reuseSocket = New-CollabSocket $wsTicket "reuse-$stamp"
    $reuseSocket.Dispose()
} catch {
    $reuseRejected = $true
}
Assert '33.wsTicketSingleUse' $reuseRejected

$socket.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done',
    [Threading.CancellationToken]::None).Wait(3000) | Out-Null
$socket.Dispose()
Start-Sleep -Milliseconds 800

$afterClose = Invoke-Api Put "$base/doc/page/$collabPageId" @{
    baseRevision = $ack.revision
    title        = 'M2 rest after ws'
    contentJson  = @{
        type    = 'doc'
        content = @(@{ type = 'paragraph'; content = @(@{ type = 'text'; text = 'M2 rest after ws content' }) })
    }
}
Assert '34.restAfterDisconnect' ($afterClose.code -eq 0 -and ([long]$afterClose.data.revision) -eq ($ackRevision + 1)) `
    "(code=$($afterClose.code), revision=$($afterClose.data.revision), expect=$($ackRevision + 1))"

# 5) 回收站：清空空间
$deleteCollab = Invoke-Api Delete "$base/doc/page/$collabPageId"
Assert '35.trashForClear' ($deleteCollab.code -eq 0)
$recycleBeforeClear = Invoke-Api Get "$base/doc/space/$spaceId/recycle/page?current=1&size=20"
Assert '36.recycleHasItem' ($recycleBeforeClear.data.total -eq 1) "(total=$($recycleBeforeClear.data.total))"
$clear = Invoke-Api Delete "$base/doc/space/$spaceId/recycle"
Assert '37.clearRecycle' ($clear.code -eq 0) "(code=$($clear.code))"
$recycleAfterClear = Invoke-Api Get "$base/doc/space/$spaceId/recycle/page?current=1&size=20"
Assert '38.recycleEmpty' ($recycleAfterClear.data.total -eq 0) "(total=$($recycleAfterClear.data.total))"
Assert '39.clearedGone' ((Invoke-Api Get "$base/doc/page/$collabPageId").code -eq 40401)

# 6) 空间归档
$spaceUpdate = Invoke-Api Put "$base/doc/space/$spaceId" @{
    name = "M2 Smoke $stamp updated"; description = 'updated'; visibility = 'PUBLIC'; icon = 'book'
}
Assert '40.spaceUpdate' ($spaceUpdate.code -eq 0 -and $spaceUpdate.data.name -match 'updated')
$spaceArchive = Invoke-Api Delete "$base/doc/space/$spaceId"
Assert '41.spaceArchive' ($spaceArchive.code -eq 0)

if ($failures -gt 0) {
    Write-Host "RESULT: FAILED ($failures assertions failed)"
    exit 1
}
Write-Host 'RESULT: PASSED'
