# Wave 5.55 P2-5 回归：PM 工作项审批「开启态」联调冒烟
# 前置：gateway(8080)/auth(8081)/workflow(8083) 就绪；whale-pm(9005) 以
#   PM_APPROVAL_ENABLED=true / PM_APPROVAL_DEFINITION_ID=<本脚本自动创建的定义> 启动。
# 用法：powershell -File scripts/smoke/bw-pm-approval-smoke.ps1
# 说明：脚本自行创建专用流程定义，因此 whale-pm 需先以「任一已存在 definitionId」开启审批；
#   若要严格对齐，可用脚本输出的 definitionId 重启 PM 后复跑（首轮即全通过则无需）。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $cred.JsonBody
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }
Assert 'login' ($null -ne $login.data.accessToken)

# 1) 创建专用审批流程定义（单节点：负责人审批）
$code = 'pm-approval-smoke-' + (Get-Date -Format 'HHmmss')
$nodes = '{"nodes":[{"key":"n1","name":"负责人审批","assignee":"admin"}]}'
$defBody = @{ code = $code; name = 'PM审批冒烟流程'; definitionJson = $nodes; version = 1; status = 1 } | ConvertTo-Json -Compress
$def = Invoke-RestMethod -Method Post -Uri "$base/workflow/definition" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $defBody
Assert '1.createDefinition' ($def.code -eq 0)
$defId = $def.data.id
Write-Host "     definitionId=$defId（whale-pm 的 PM_APPROVAL_DEFINITION_ID 应指向已存在定义）"

# 2) 建项目与工作项
$pkey = 'AP' + (Get-Date -Format 'HHmmss')
$project = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json; charset=utf-8' -Body (@{ key = $pkey; name = '审批冒烟项目'; projectType = 'SCRUM'; visibility = 'TEAM'; leadUserId = 1 } | ConvertTo-Json -Compress)
Assert '2.createProject' ($project.code -eq 0)
$projectId = $project.data.id

$item = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item" -Headers $h -ContentType 'application/json; charset=utf-8' -Body (@{ projectId = [long]$projectId; type = 'STORY'; title = '审批冒烟工作项'; priority = 'HIGH'; assigneeId = 1 } | ConvertTo-Json -Compress)
Assert '3.createWorkItem' ($item.code -eq 0)
$itemId = $item.data.id

try {
    # 3) 发起审批（开启态）：返回流程实例ID
    $ap = Invoke-RestMethod -Method Post -Uri "$base/pm/work-item/$itemId/approval" -Headers $h
    Assert '4.submitApproval' ($ap.code -eq 0 -and $null -ne $ap.data)
    $instId = $ap.data
    Write-Host "     instanceId=$instId"

    # 4) 工作项动态记录审批实例ID
    $acts = Invoke-RestMethod -Method Get -Uri "$base/pm/work-item/$itemId/activity" -Headers $h
    $apAct = $acts.data | Where-Object { $_.field -eq 'approval' }
    Assert '5.activityRecorded' ($null -ne $apAct -and $apAct.newValue -eq $instId)

    # 5) workflow 实例与 PM 侧对账：businessKey=工作项ID
    $inst = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/$instId" -Headers $h
    Assert '6.instanceLinked' ($inst.code -eq 0 -and $inst.data.businessKey -eq $itemId -and $inst.data.status -eq 0)

    # 6) 审批通过 → 实例完结
    $tasks = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instId" -Headers $h
    $task = $tasks.data.records[0]
    $apr = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$($task.id)/approve" -Headers $h -ContentType 'application/json' -Body '{"comment":"同意"}'
    Assert '7.approve' ($apr.code -eq 0)
    $inst2 = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/$instId" -Headers $h
    Assert '8.instanceCompleted' ($inst2.data.status -eq 1)
}
finally {
    # 7) 清理
    Invoke-RestMethod -Method Delete -Uri "$base/pm/work-item/$itemId" -Headers $h | Out-Null
    Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h | Out-Null
    Write-Host "OK   9.cleanup"
}

Write-Host "审批开启态冒烟完成：发起→动态记录→实例对账→审批完结 全通过。"
