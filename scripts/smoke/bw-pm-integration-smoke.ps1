$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Show($label, $obj) {
    Write-Host "== $label =="
    Write-Host ($obj | ConvertTo-Json -Depth 8 -Compress)
    Write-Host ""
}

function ExpectReject($label, [scriptblock]$action) {
    try {
        $result = & $action
        Show "$label.unexpectedSuccess" $result
    } catch {
        Write-Host "== $label =="
        Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
        Write-Host ""
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw "login failed" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 建项目（自动初始化默认状态与流转）
$key = 'M5' + (Get-Date -Format 'HHmmss')
$projectBody = @{
    key         = $key
    name        = '集成冒烟项目'
    projectType = 'SCRUM'
    visibility  = 'TEAM'
    leadUserId  = 1
} | ConvertTo-Json -Compress
$project = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json' -Body $projectBody
Show '1.createProject' $project
$projectId = $project.data.id

# 2) 读取状态
$statuses = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/status" -Headers $h
Show '2.listStatuses' $statuses
$todoStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'TODO' }).id)
$doingStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'IN_PROGRESS' }).id)

# 3) 创建工作项并指派给其他用户（站内通知默认关闭，不应产生副作用）
$itemBody = @{
    projectId    = [long]$projectId
    type         = 'STORY'
    title        = '集成冒烟故事'
    priority     = 'HIGH'
    assigneeId   = 2
    storyPoints  = 3
} | ConvertTo-Json -Compress
$item = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json' -Body $itemBody
Show '3.createAssignedItem' $item
$itemId = $item.data.id

# 4) 新增评论（评论通知默认关闭，不影响主事务）
$commentBody = @{ content = '集成冒烟评论' } | ConvertTo-Json -Compress
$comment = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/comment" -Headers $h -ContentType 'application/json' -Body $commentBody
Show '4.addComment' $comment

# 5) 状态流转（流转通知默认关闭）
$transitionBody = @{ targetStatusId = [long]$doingStatusId } | ConvertTo-Json -Compress
$transitioned = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$itemId/transition" -Headers $h -ContentType 'application/json' -Body $transitionBody
Show '5.transitionItem' $transitioned

# 6) 动态时间线应记录创建 / 评论 / 流转
$activities = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/activity" -Headers $h
Show '6.listActivities' $activities

# 7) 负向：审批默认关闭，发起审批应返回业务提示且不产生外部调用
ExpectReject '7.negative.approvalDisabled' {
    Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/approval" -Headers $h
}

# 8) 未开启通知时的读接口不受影响
$detail = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId" -Headers $h
Show '8.itemDetail' $detail

# 9) 清理：删除工作项与项目
$deletedItem = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$itemId" -Headers $h
Show '9.deleteItem' $deletedItem
$deletedProject = Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h
Show '10.deleteProject' $deletedProject

Write-Host "集成冒烟完成：通知/审批默认关闭、主链路无副作用。"
