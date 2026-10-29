
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function ExpectReject($label, [scriptblock]$action) {
    try {
        $result = & $action
        # 业务异常以 HTTP 200 + Result.code!=0 返回，兼容两种拒绝形态
        if ($null -ne $result -and $result.code -ne 0) {
            Write-Host "== $label =="
            Write-Host "expected business rejection: code=$($result.code) message=$($result.message)"
            Write-Host ""
            return
        }
        Show "$label.unexpectedSuccess" $result
    } catch {
        Write-Host "== $label =="
        Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
        Write-Host ""
    }
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json; charset=utf-8' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw "login failed" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 建项目（自动初始化默认状态与流转）
$key = 'SB' + (Get-Date -Format 'HHmmss')
$projectBody = @{
    key         = $key
    name        = '迭代看板冒烟项目'
    projectType = 'SCRUM'
    visibility  = 'TEAM'
    leadUserId  = 1
} | ConvertTo-Json -Compress
$project = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $projectBody
Show '1.createProject' $project
$projectId = $project.data.id

# 2) 读取状态
$statuses = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/status" -Headers $h
Show '2.listStatuses' $statuses
$todoStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'TODO' }).id)
$doingStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'IN_PROGRESS' }).id)
$doneStatusId = [long](($statuses.data | Where-Object { $_.code -eq 'DONE' }).id)

# 3) 创建迭代 1
$sprintBody = @{
    projectId = [long]$projectId
    name      = 'Sprint 冒烟 1'
    goal      = '验证迭代生命周期'
    startDate = '2026-11-01'
    endDate   = '2026-11-15'
} | ConvertTo-Json -Compress
$sprint = Invoke-RestMethod -Method Post -Uri "$base/pm/sprint" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $sprintBody
Show '3.createSprint' $sprint
$sprintId = $sprint.data.id

# 4) 分页查询迭代
$sprintPage = Invoke-RestMethod -Method Get -Uri "$base/pm/sprint/page?current=1&size=10&projectId=$projectId" -Headers $h
Show '4.pageSprints' $sprintPage

# 5) 创建两个工作项
$item1Body = @{ projectId = [long]$projectId; type = 'STORY'; title = '迭代故事一'; priority = 'HIGH' } | ConvertTo-Json -Compress
$item1 = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $item1Body
$item2Body = @{ projectId = [long]$projectId; type = 'TASK'; title = '迭代任务二'; priority = 'MEDIUM' } | ConvertTo-Json -Compress
$item2 = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $item2Body
Show '5.createItems' @{ item1 = $item1.data; item2 = $item2.data }
$item1Id = $item1.data.id
$item2Id = $item2.data.id

# 6) 批量加入迭代
$addBody = @{ itemIds = @([long]$item1Id, [long]$item2Id) } | ConvertTo-Json -Compress
$added = Invoke-RestMethod -Method Post -Uri "$base/pm/sprint/$sprintId/item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $addBody
Show '6.addSprintItems' $added

# 7) 查询迭代内工作项
$sprintItems = Invoke-RestMethod -Method Get -Uri "$base/pm/sprint/$sprintId/item" -Headers $h
Show '7.listSprintItems' $sprintItems

# 8) 启动迭代
$started = Invoke-RestMethod -Method Put -Uri "$base/pm/sprint/$sprintId/start" -Headers $h
Show '8.startSprint' $started

# 9) 负向：同项目再启动一个迭代
$sprint2Body = @{
    projectId = [long]$projectId
    name      = 'Sprint 冒烟 2'
    startDate = '2026-11-16'
    endDate   = '2026-11-30'
} | ConvertTo-Json -Compress
$sprint2 = Invoke-RestMethod -Method Post -Uri "$base/pm/sprint" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $sprint2Body
$sprint2Id = $sprint2.data.id
ExpectReject '9.negative.secondActiveSprint' {
    Invoke-RestMethod -Method Put -Uri "$base/pm/sprint/$sprint2Id/start" -Headers $h
}

# 10) 创建看板
$boardBody = @{ projectId = [long]$projectId; name = '冒烟看板'; boardType = 'SCRUM' } | ConvertTo-Json -Compress
$board = Invoke-RestMethod -Method Post -Uri "$base/pm/board" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $boardBody
Show '10.createBoard' $board
$boardId = $board.data.id

# 11) 查询看板列表
$boards = Invoke-RestMethod -Method Get -Uri "$base/pm/board?projectId=$projectId" -Headers $h
Show '11.listBoards' $boards

# 12) 新增三列（进行中 WIP 上限 1，用于验证超限）
$columns = @(
    @{ statusId = [long]$todoStatusId; name = '待办'; sortNo = 1; wipLimit = 0 },
    @{ statusId = [long]$doingStatusId; name = '进行中'; sortNo = 2; wipLimit = 1 },
    @{ statusId = [long]$doneStatusId; name = '已完成'; sortNo = 3; wipLimit = 0 }
)
$columnIds = @()
foreach ($column in $columns) {
    $columnBody = $column | ConvertTo-Json -Compress
    $created = Invoke-RestMethod -Method Post -Uri "$base/pm/board/$boardId/column" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $columnBody
    $columnIds += $created.data.id
}
Show '12.addColumns' $columnIds

# 13) 看板详情
$boardDetail = Invoke-RestMethod -Method Get -Uri "$base/pm/board/$boardId" -Headers $h
Show '13.boardDetail' $boardDetail

# 14) 看板执行视图
$boardView = Invoke-RestMethod -Method Get -Uri "$base/pm/board/$boardId/view" -Headers $h
Show '14.boardView' $boardView

# 15) 拖拽：TODO -> 进行中
$drag1Body = @{ workItemId = [long]$item1Id; targetStatusId = [long]$doingStatusId } | ConvertTo-Json -Compress
$dragged = Invoke-RestMethod -Method Put -Uri "$base/pm/board/$boardId/drag" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $drag1Body
Show '15.dragItem1' $dragged

# 16) 负向：第二项拖入进行中触发 WIP 上限
ExpectReject '16.negative.wipExceeded' {
    $drag2Body = @{ workItemId = [long]$item2Id; targetStatusId = [long]$doingStatusId } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Put -Uri "$base/pm/board/$boardId/drag" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $drag2Body
}

# 17) 将第二项移出迭代
$removed = Invoke-RestMethod -Method Delete -Uri "$base/pm/sprint/$sprintId/item/$item2Id" -Headers $h
Show '17.removeSprintItem' $removed

# 18) 关闭迭代
$closed = Invoke-RestMethod -Method Put -Uri "$base/pm/sprint/$sprintId/close" -Headers $h
Show '18.closeSprint' $closed

# 19) 负向：已关闭迭代禁止加入工作项
ExpectReject '19.negative.addItemToClosedSprint' {
    $addAgain = @{ itemIds = @([long]$item2Id) } | ConvertTo-Json -Compress
    Invoke-RestMethod -Method Post -Uri "$base/pm/sprint/$sprintId/item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $addAgain
}

# 20) 负向：非未启动迭代不可删除
ExpectReject '20.negative.deleteClosedSprint' {
    Invoke-RestMethod -Method Delete -Uri "$base/pm/sprint/$sprintId" -Headers $h
}

# 21) 清理：删除 FUTURE 迭代、看板、工作项与项目
$deletedSprint = Invoke-RestMethod -Method Delete -Uri "$base/pm/sprint/$sprint2Id" -Headers $h
Show '21.deleteFutureSprint' $deletedSprint
$deletedBoard = Invoke-RestMethod -Method Delete -Uri "$base/pm/board/$boardId" -Headers $h
Show '22.deleteBoard' $deletedBoard
$deletedItem1 = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$item1Id" -Headers $h
Show '23.deleteItem1' $deletedItem1
$deletedItem2 = Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$item2Id" -Headers $h
Show '24.deleteItem2' $deletedItem2
$deletedProject = Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h
Show '25.deleteProject' $deletedProject
