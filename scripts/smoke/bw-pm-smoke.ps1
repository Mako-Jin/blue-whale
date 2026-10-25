$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Show($label, $obj) {
    Write-Host "== $label =="
    Write-Host ($obj | ConvertTo-Json -Depth 8 -Compress)
    Write-Host ""
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw "login failed" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 新增项目（项目键 PM+时分秒，满足 2–10 位大写字母数字）
$key = 'PM' + (Get-Date -Format 'HHmmss')
$createBody = @{
    key         = $key
    name        = '冒烟项目'
    projectType = 'SCRUM'
    visibility  = 'TEAM'
    leadUserId  = 1
    description = 'Wave 5.55 M1 冒烟'
} | ConvertTo-Json -Compress
$created = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json' -Body $createBody
Show '1.createProject' $created
$projectId = $created.data.id

# 2) 分页
$page = Invoke-RestMethod -Method Get -Uri "$base/pm/project/page?current=1&size=10&keyword=$key" -Headers $h
Show '2.pageProject' $page

# 3) 详情
$detail = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId" -Headers $h
Show '3.projectDetail' $detail

# 4) 更新
$updateBody = @{
    key         = $key
    name        = '冒烟项目（改）'
    projectType = 'SCRUM'
    visibility  = 'PRIVATE'
    leadUserId  = 1
    description = '已更新'
} | ConvertTo-Json -Compress
$updated = Invoke-RestMethod -Method Put -Uri "$base/pm/project/$projectId" -Headers $h -ContentType 'application/json' -Body $updateBody
Show '4.updateProject' $updated

# 5) 成员列表（负责人应自动登记为 ADMIN）
$members = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/member" -Headers $h
Show '5.listMembers' $members

# 6) 新增成员（userId=2）
$addMemberBody = @{ userId = 2; projectRole = 'MEMBER' } | ConvertTo-Json -Compress
$member = Invoke-RestMethod -Method Post -Uri "$base/pm/project/$projectId/member" -Headers $h -ContentType 'application/json' -Body $addMemberBody
Show '6.addMember' $member
$memberId = $member.data.id

# 7) 变更成员角色
$roleBody = @{ projectRole = 'VIEWER' } | ConvertTo-Json -Compress
$roleChanged = Invoke-RestMethod -Method Put -Uri "$base/pm/member/$memberId" -Headers $h -ContentType 'application/json' -Body $roleBody
Show '7.updateMemberRole' $roleChanged

# 8) 移除成员
$removed = Invoke-RestMethod -Method Delete -Uri "$base/pm/member/$memberId" -Headers $h
Show '8.removeMember' $removed

# 9) 状态列表（应含 TODO / IN_PROGRESS / DONE 三个默认状态）
$statuses = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/status" -Headers $h
Show '9.listStatuses' $statuses
$doneStatusId = ($statuses.data | Where-Object { $_.code -eq 'DONE' }).id

# 10) 新增状态 REVIEW
$statusBody = @{ code = 'REVIEW'; name = '评审中'; category = 'IN_PROGRESS'; sortNo = 4 } | ConvertTo-Json -Compress
$statusAdded = Invoke-RestMethod -Method Post -Uri "$base/pm/project/$projectId/status" -Headers $h -ContentType 'application/json' -Body $statusBody
Show '10.addStatus' $statusAdded
$reviewStatusId = $statusAdded.data.id

# 11) 更新状态
$statusUpdateBody = @{ code = 'REVIEW'; name = '待评审'; category = 'IN_PROGRESS'; sortNo = 4 } | ConvertTo-Json -Compress
$statusUpdated = Invoke-RestMethod -Method Put -Uri "$base/pm/status/$reviewStatusId" -Headers $h -ContentType 'application/json' -Body $statusUpdateBody
Show '11.updateStatus' $statusUpdated

# 12) 流转列表（应含 4 条默认流转）
$transitions = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId/transition" -Headers $h
Show '12.listTransitions' $transitions

# 13) 新增流转 REVIEW -> DONE
$transitionBody = @{ fromStatusId = [long]$reviewStatusId; toStatusId = [long]$doneStatusId; name = '评审通过'; requireComment = $false } | ConvertTo-Json -Compress
$transition = Invoke-RestMethod -Method Post -Uri "$base/pm/project/$projectId/transition" -Headers $h -ContentType 'application/json' -Body $transitionBody
Show '13.addTransition' $transition
$transitionId = $transition.data.id

# 14) 删除流转
$transitionDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/transition/$transitionId" -Headers $h
Show '14.deleteTransition' $transitionDeleted

# 15) 删除状态
$statusDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/status/$reviewStatusId" -Headers $h
Show '15.deleteStatus' $statusDeleted

# 16) 归档项目
$archiveBody = @{ status = 'ARCHIVED' } | ConvertTo-Json -Compress
$archived = Invoke-RestMethod -Method Put -Uri "$base/pm/project/$projectId/status" -Headers $h -ContentType 'application/json' -Body $archiveBody
Show '16.archiveProject' $archived

# 17) 负向：归档后新增状态应被拒绝
try {
    $negStatusBody = @{ code = 'BLOCKED'; name = '阻塞'; category = 'IN_PROGRESS' } | ConvertTo-Json -Compress
    $neg = Invoke-RestMethod -Method Post -Uri "$base/pm/project/$projectId/status" -Headers $h -ContentType 'application/json' -Body $negStatusBody
    Show '17.negative.archivedAddStatus' $neg
} catch {
    Write-Host "== 17.negative.archivedAddStatus =="
    Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}

# 18) 负向：重复项目键应被拒绝
try {
    $dup = Invoke-RestMethod -Method Post -Uri "$base/pm/project" -Headers $h -ContentType 'application/json' -Body $createBody
    Show '18.negative.duplicateKey' $dup
} catch {
    Write-Host "== 18.negative.duplicateKey =="
    Write-Host "expected rejection: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}

# 19) 删除项目
$projectDeleted = Invoke-RestMethod -Method Delete -Uri "$base/pm/project/$projectId" -Headers $h
Show '19.deleteProject' $projectDeleted

# 20) 负向：删除后详情应 404
try {
    $after = Invoke-RestMethod -Method Get -Uri "$base/pm/project/$projectId" -Headers $h
    Show '20.negative.detailAfterDelete' $after
} catch {
    Write-Host "== 20.negative.detailAfterDelete =="
    Write-Host "expected 404: $($_.Exception.Response.StatusCode.value__)"
    Write-Host ""
}
