
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function AssertSuccess($label, $result) {
    Show $label $result
    if ($result.code -ne 0) { throw "$label failed: code=$($result.code) message=$($result.message)" }
}

function AssertBusinessReject($label, $result) {
    Show "$label.businessCode" $result
    if ($result.code -eq 0) { throw "$label should be rejected by business code" }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json; charset=utf-8' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw 'login failed' }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 建项目
$key = 'RP' + (Get-Date -Format 'HHmmss')
$projectBody = @{
    key         = $key
    name        = '报表冒烟项目'
    projectType = 'SCRUM'
    visibility  = 'PRIVATE'
    leadUserId  = 1
} | ConvertTo-Json -Compress
$project = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $projectBody
AssertSuccess '1.createProject' $project
$projectId = $project.data.id

# 2) 状态
$statuses = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/status" -Headers $h
$todoStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'TODO' }).id)
$doingStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'IN_PROGRESS' }).id)
$doneStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'DONE' }).id)
Show '2.listStatuses' $statuses

# 3) 建迭代并启动（区间覆盖今天前后）
$today = Get-Date
$sprintBody = @{
    projectId = [long]$projectId
    name      = 'Sprint 报表'
    goal      = '验证燃尽与速率'
    startDate = $today.AddDays(-2).ToString('yyyy-MM-dd')
    endDate   = $today.AddDays(3).ToString('yyyy-MM-dd')
} | ConvertTo-Json -Compress
$sprint = Invoke-RestMethod -Method Post -Uri "$base/pm/sprint" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $sprintBody
AssertSuccess '3.createSprint' $sprint
$sprintId = $sprint.data.id
$started = Invoke-RestMethod -Method Put -Uri "$base/pm/sprint/$sprintId/start" -Headers $h
AssertSuccess '4.startSprint' $started

# 5) 建两个工作项（带故事点）并加入迭代
$item1 = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' `
    -Body (@{ projectId = [long]$projectId; type = 'STORY'; title = '报表故事一'; priority = 'HIGH'; storyPoints = 3 } | ConvertTo-Json -Compress)
$item2 = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' `
    -Body (@{ projectId = [long]$projectId; type = 'TASK'; title = '报表任务二'; priority = 'MEDIUM'; storyPoints = 5 } | ConvertTo-Json -Compress)
Show '5.createItems' @{ item1 = $item1.data; item2 = $item2.data }
if ($item1.code -ne 0 -or $item2.code -ne 0) { throw "5.createItems failed: code1=$($item1.code) code2=$($item2.code)" }
$item1Id = [long]$item1.data.id
$item2Id = [long]$item2.data.id
$addBody = @{ itemIds = @($item1Id, $item2Id) } | ConvertTo-Json -Compress
$added = Invoke-RestMethod -Method Post -Uri "$base/pm/sprint/$sprintId/item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $addBody
AssertSuccess '6.addSprintItems' $added

# 7) 燃尽（应有 3 个数据点：今天-2、今天-1、今天）
$burndown = Invoke-RestMethod -Method Get -Uri "$base/pm/report/burndown?projectId=$projectId" -Headers $h
AssertSuccess '7.burndown' $burndown
if ($burndown.data.totalPoints -ne 8) { throw "burndown totalPoints expected 8, actual $($burndown.data.totalPoints)" }
if ($burndown.data.points.Count -lt 1) { throw 'burndown points should not be empty' }

# 8) 速率（未关闭迭代应为空）
$velocity = Invoke-RestMethod -Method Get -Uri "$base/pm/report/velocity?projectId=$projectId" -Headers $h
AssertSuccess '8.velocityBeforeClose' $velocity
if ($velocity.data.sprints.Count -ne 0) { throw 'velocity should be empty before sprint closed' }

# 9) 累积流（默认区间 + 指定区间）
$flowDefault = Invoke-RestMethod -Method Get -Uri "$base/pm/report/cumulative-flow?projectId=$projectId&sprintId=$sprintId" -Headers $h
AssertSuccess '9.cumulativeFlowDefault' $flowDefault
$flowRange = Invoke-RestMethod -Method Get -Uri "$base/pm/report/cumulative-flow?projectId=$projectId&startDate=$($today.AddDays(-2).ToString('yyyy-MM-dd'))&endDate=$($today.ToString('yyyy-MM-dd'))" -Headers $h
AssertSuccess '10.cumulativeFlowRange' $flowRange
if ($flowRange.data.dates.Count -ne 3) { throw "cumulative flow dates expected 3, actual $($flowRange.data.dates.Count)" }

# 11) 区间超过 1 年应被业务拒绝
$overRange = Invoke-RestMethod -Method Get -Uri "$base/pm/report/cumulative-flow?projectId=$projectId&startDate=$($today.AddDays(-400).ToString('yyyy-MM-dd'))&endDate=$($today.ToString('yyyy-MM-dd'))" -Headers $h
AssertBusinessReject '11.cumulativeFlowOverRange' $overRange

# 12) 成员负载
$workload = Invoke-RestMethod -Method Get -Uri "$base/pm/report/workload?projectId=$projectId" -Headers $h
AssertSuccess '12.workload' $workload

# 13) 完成一个工作项后燃尽剩余点应下降（默认流转链：TODO -> IN_PROGRESS -> DONE）
$toDoing = @{ targetStatusId = [long]$doingStatusId; comment = '报表冒烟开始' } | ConvertTo-Json -Compress
$movedToDoing = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$item1Id/transition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $toDoing
AssertSuccess '13a.startItem' $movedToDoing
$transition = @{ targetStatusId = $doneStatusId; comment = '报表冒烟完成' } | ConvertTo-Json -Compress
$moved = Invoke-RestMethod -Method Put -Uri "$base/pm/work-item/$item1Id/transition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $transition
AssertSuccess '13b.completeItem' $moved
$burndown2 = Invoke-RestMethod -Method Get -Uri "$base/pm/report/burndown?projectId=$projectId" -Headers $h
AssertSuccess '14.burndownAfterDone' $burndown2
$lastRemaining = $burndown2.data.points[$burndown2.data.points.Count - 1].remainingPoints
# 报表按设计缓存 5 分钟（plan §报表），命中缓存时允许看到变更前快照
if ($lastRemaining -ne 5) {
    if ($lastRemaining -eq 8) {
        Write-Host 'burndown 命中 5 分钟缓存，按缓存语义接受变更前快照'
    } else {
        throw "burndown last remaining expected 5 or cached 8, actual $lastRemaining"
    }
}
# 14b) 用未缓存的区间验证 DONE 归类已即时生效
$freshFlow = Invoke-RestMethod -Method Get -Uri "$base/pm/report/cumulative-flow?projectId=$projectId&startDate=$($today.AddDays(-1).ToString('yyyy-MM-dd'))&endDate=$($today.ToString('yyyy-MM-dd'))" -Headers $h
AssertSuccess '14b.cumulativeFlowFresh' $freshFlow
$doneSeries = $freshFlow.data.series | Where-Object { $_.category -eq 'DONE' }
if (-not $doneSeries) { throw 'cumulative flow missing DONE series' }
$doneToday = $doneSeries.values[$doneSeries.values.Count - 1]
if ($doneToday -ne 1) { throw "cumulative flow DONE today expected 1, actual $doneToday" }

# 15) 关闭迭代后速率应有 1 条记录（limit=6 走新缓存键，避免默认键命中旧值）
$closed = Invoke-RestMethod -Method Put -Uri "$base/pm/sprint/$sprintId/close" -Headers $h
AssertSuccess '15.closeSprint' $closed
$velocityAfter = Invoke-RestMethod -Method Get -Uri "$base/pm/report/velocity?projectId=$projectId&limit=6" -Headers $h
AssertSuccess '16.velocityAfterClose' $velocityAfter
if ($velocityAfter.data.sprints.Count -ne 1) { throw "velocity expected 1 sprint, actual $($velocityAfter.data.sprints.Count)" }
if ($velocityAfter.data.sprints[0].committedPoints -ne 8) { throw 'velocity committedPoints expected 8' }
if ($velocityAfter.data.sprints[0].completedPoints -ne 3) { throw 'velocity completedPoints expected 3' }

# 16) 清理
$removed = Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h
AssertSuccess '17.deleteProject' $removed
Write-Host 'report smoke passed'
