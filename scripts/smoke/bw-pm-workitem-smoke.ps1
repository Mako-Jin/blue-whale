$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Show($label, $obj) {
    Write-Host "== $label =="
    Write-Host ($obj | ConvertTo-Json -Depth 8 -Compress)
    Write-Host ""
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json; charset=utf-8' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw "login failed" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 建项目（为工作项提供状态与流转配置）
$key = 'WI' + (Get-Date -Format 'HHmmss')
$projectBody = @{
    key         = $key
    name        = '工作项冒烟项目'
    projectType = 'SCRUM'
    visibility  = 'TEAM'
    leadUserId  = 1
    description = 'Wave 5.55 M2 冒烟'
} | ConvertTo-Json -Compress
$project = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $projectBody
Show '1.createProject' $project
$projectId = $project.data.id

# 2) 读取状态（TODO 默认 / DONE 终态）
$statuses = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/status" -Headers $h
Show '2.listStatuses' $statuses
$doneStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'DONE' }).id)

# 3) 创建工作项（TASK，clientRequestId 幂等）
$idem = 'smoke-' + (Get-Date -Format 'HHmmssfff')
$itemBody = @{
    projectId       = [long]$projectId
    type            = 'TASK'
    title           = '冒烟任务'
    description     = 'Wave 5.55 M2 冒烟'
    priority        = 'HIGH'
    assigneeId      = 2
    storyPoints     = 3.5
    dueDate         = '2026-11-30'
    labels          = 'smoke,m2'
    clientRequestId = $idem
} | ConvertTo-Json -Compress
$created = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $itemBody
Show '3.createItem' $created
$itemId = $created.data.id
$itemKey = $created.data.key
$itemVersion = $created.data.version

# 4) 幂等重放：同一 clientRequestId 返回同一条
$replayed = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $itemBody
Show '4.idempotentReplay' $replayed

# 5) 第二个工作项（用于关联）
$bugBody = @{
    projectId = [long]$projectId
    type      = 'BUG'
    title     = '冒烟缺陷'
    priority  = 'MEDIUM'
} | ConvertTo-Json -Compress
$bug = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $bugBody
Show '5.createBug' $bug
$bugId = $bug.data.id

# 6) 分页查询
$page = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/page?current=1&size=10&projectId=$projectId&keyword=冒烟" -Headers $h
Show '6.pageItems' $page

# 7) 详情
$detail = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId" -Headers $h
Show '7.itemDetail' $detail

# 8) 更新（带乐观锁版本号）
$updateBody = @{
    projectId   = [long]$projectId
    type        = 'TASK'
    title       = '冒烟任务（改）'
    description = '已更新描述'
    priority    = 'HIGHEST'
    assigneeId  = 2
    version     = [int]$itemVersion
} | ConvertTo-Json -Compress
$updated = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $updateBody
Show '8.updateItem' $updated

# 9) 新增评论
$commentBody = @{ content = '冒烟评论：进展顺利' } | ConvertTo-Json -Compress
$comment = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/comment" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $commentBody
Show '9.addComment' $comment
$commentId = $comment.data.id

# 10) 评论列表
$comments = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/comment" -Headers $h
Show '10.listComments' $comments

# 11) 登记附件（仅 file_id 引用）
$attachBody = @{ fileId = 900000001; fileName = '冒烟附件.txt' } | ConvertTo-Json -Compress
$attach = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/attachment" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $attachBody
Show '11.addAttachment' $attach
$attachmentId = $attach.data.id

# 12) 附件列表
$attachments = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/attachment" -Headers $h
Show '12.listAttachments' $attachments

# 13) 新增关联
$linkBody = @{ targetItemId = [long]$bugId; linkType = 'RELATES' } | ConvertTo-Json -Compress
$link = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/link" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $linkBody
Show '13.addLink' $link
$linkId = $link.data.id

# 14) 关联列表
$links = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/link" -Headers $h
Show '14.listLinks' $links

# 15) 状态流转 TODO -> IN_PROGRESS -> DONE（默认流转链：开始 / 完成，不允许跳过中间态）
$doingStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'IN_PROGRESS' }).id)
$movedBody = @{ targetStatusId = [long]$doingStatusId; comment = '冒烟开始' } | ConvertTo-Json -Compress
$moved = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId/transition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $movedBody
Show '15a.transitionToInProgress' $moved
$transitionBody = @{ targetStatusId = [long]$doneStatusId; comment = '冒烟完成' } | ConvertTo-Json -Compress
$transitioned = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId/transition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $transitionBody
Show '15b.transitionToDone' $transitioned

# 16) 负向：目标状态不存在
try {
    $badTransition = @{ targetStatusId = 999999999 } | ConvertTo-Json -Compress
    $bad = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId/transition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $badTransition
    Show '16.negative.transitionUnknownStatus' $bad
} catch {
    Write-Host "== 16.negative.transitionUnknownStatus =="
    Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}

# 17) 排序：移动到缺陷之前
$rankBody = @{ afterItemId = [long]$bugId } | ConvertTo-Json -Compress
$ranked = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId/rank" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $rankBody
Show '17.rank' $ranked

# 18) 工作项动态
$activities = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/activity" -Headers $h
Show '18.listActivities' $activities

# 19) 项目动态
$projectActivities = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/activity" -Headers $h
Show '19.listProjectActivities' $projectActivities

# 20) 负向：直接改状态被拒绝（应走流转接口）
try {
    $directBody = @{
        projectId = [long]$projectId
        type      = 'TASK'
        title     = '冒烟任务（改）'
        statusId  = [long]$doneStatusId
    } | ConvertTo-Json -Compress
    $direct = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $directBody
    Show '20.negative.directStatusUpdate' $direct
} catch {
    Write-Host "== 20.negative.directStatusUpdate =="
    Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}

# 21) 删除评论
$commentDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/comment/$commentId" -Headers $h
Show '21.deleteComment' $commentDeleted

# 22) 解除附件
$attachDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/attachment/$attachmentId" -Headers $h
Show '22.deleteAttachment' $attachDeleted

# 23) 删除关联
$linkDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/link/$linkId" -Headers $h
Show '23.deleteLink' $linkDeleted

# 24) 删除工作项
$itemDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$itemId" -Headers $h
Show '24.deleteItem' $itemDeleted
$bugDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$bugId" -Headers $h
Show '25.deleteBug' $bugDeleted

# 26) 删除项目
$projectDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h
Show '26.deleteProject' $projectDeleted

# 27) 负向：工作项详情 404
try {
    $after = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId" -Headers $h
    Show '27.negative.detailAfterDelete' $after
} catch {
    Write-Host "== 27.negative.detailAfterDelete =="
    Write-Host "expected 404: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}
