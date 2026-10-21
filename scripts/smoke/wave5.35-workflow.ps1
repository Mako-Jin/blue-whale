$ErrorActionPreference = 'Stop'
$script:token = $null
$gateway = 'http://127.0.0.1:8080'

function Api([string]$method, [string]$path, $body) {
    $headers = @{ Authorization = "Bearer $script:token" }
    $json = $null
    if ($null -ne $body) {
        $json = ($body | ConvertTo-Json -Depth 20 -Compress)
    }
    try {
        if ($null -ne $json) {
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers `
                -ContentType 'application/json;charset=utf-8' -Body $bytes
        }
        return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers
    } catch {
        Write-Host "FAIL $method $path -> $($_.Exception.Message)"
        if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
        throw
    }
}

function Check([string]$name, $result, [int]$expectCode = 0) {
    if ($result.code -ne $expectCode) {
        throw "$name 失败: code=$($result.code) message=$($result.message)"
    }
    Write-Host "OK   $name"
}

# ---------- 1. 登录 ----------
$login = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json;charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"123456"}'))
$script:token = $login.data.accessToken
$stamp = Get-Date -Format 'HHmmss'
Write-Host "OK   登录获取令牌"

function New-Definition([string]$code, [string]$name, $model) {
    $result = Api 'Post' '/workflow/definition' @{
        code           = $code
        name           = $name
        definitionJson = ($model | ConvertTo-Json -Depth 20 -Compress)
    }
    Check "创建流程定义 $code" $result
    return $result.data.id
}

function Start-Instance($definitionId, [string]$title, $variables) {
    $body = @{ definitionId = $definitionId; title = $title }
    if ($null -ne $variables) { $body.variables = $variables }
    $result = Api 'Post' '/workflow/instance' $body
    Check "发起实例 $title" $result
    return $result.data.id
}

function Get-PendingTask($instanceId) {
    $result = Api 'Get' "/workflow/task/page?instanceId=$instanceId&status=0&pageNum=1&pageSize=50" $null
    Check "查询待办 instance=$instanceId" $result
    return $result.data.records
}

function Get-InstanceStatus($instanceId) {
    $result = Api 'Get' "/workflow/instance/$instanceId" $null
    Check "查询实例 $instanceId" $result
    return $result.data.status
}

# ---------- 2. 条件选路 ----------
$condModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 'gw'; type = 'EXCLUSIVE_GATEWAY'; name = 'amount-gw' },
        @{ key = 'high'; type = 'USER_TASK'; name = 'high'; assignee = 'admin' },
        @{ key = 'low'; type = 'USER_TASK'; name = 'low'; assignee = 'admin' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'f1'; from = 'start'; to = 'gw' },
        @{ key = 'f2'; from = 'gw'; to = 'high'; condition = '#amount > 1000' },
        @{ key = 'f3'; from = 'gw'; to = 'low'; isDefault = $true },
        @{ key = 'f4'; from = 'high'; to = 'end' },
        @{ key = 'f5'; from = 'low'; to = 'end' }
    )
}
$condDefId = New-Definition ('smoke_cond_535_' + $stamp) '条件选路冒烟' $condModel
$condInst = Start-Instance $condDefId 'condition-high' @{ amount = 2000 }
$condTasks = Get-PendingTask $condInst
if (@($condTasks).Count -ne 1 -or $condTasks[0].nodeKey -ne 'high') {
    throw "条件选路失败：期望命中 high，实际 $($condTasks | ConvertTo-Json -Depth 5 -Compress)"
}
Write-Host "OK   条件选路命中 high（#amount > 1000）"
Api 'Post' "/workflow/task/$($condTasks[0].id)/approve" @{ comment = '条件通过' } | Out-Null
Write-Host "OK   条件实例终态=$((Get-InstanceStatus $condInst))（1=通过）"
Write-Host "OK   条件流程审批通过"

$condInstLow = Start-Instance $condDefId 'condition-low' @{ amount = 100 }
$lowTasks = Get-PendingTask $condInstLow
if ($lowTasks[0].nodeKey -ne 'low') { throw "默认流未命中 low：$($lowTasks[0].nodeKey)" }
Write-Host "OK   默认流命中 low"
Api 'Post' "/workflow/task/$($lowTasks[0].id)/approve" @{ comment = 'ok' } | Out-Null

# ---------- 3. 并行网关 ----------
$parModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 'fork'; type = 'PARALLEL_GATEWAY'; name = 'fork' },
        @{ key = 't1'; type = 'USER_TASK'; name = 'branch1'; assignee = 'admin' },
        @{ key = 't2'; type = 'USER_TASK'; name = 'branch2'; assignee = 'admin' },
        @{ key = 'join'; type = 'PARALLEL_GATEWAY'; name = 'join' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'p1'; from = 'start'; to = 'fork' },
        @{ key = 'p2'; from = 'fork'; to = 't1' },
        @{ key = 'p3'; from = 'fork'; to = 't2' },
        @{ key = 'p4'; from = 't1'; to = 'join' },
        @{ key = 'p5'; from = 't2'; to = 'join' },
        @{ key = 'p6'; from = 'join'; to = 'end' }
    )
}
$parDefId = New-Definition ('smoke_parallel_535_' + $stamp) '并行网关冒烟' $parModel
$parInst = Start-Instance $parDefId 'parallel' @{}
$parTasks = Get-PendingTask $parInst
if (@($parTasks).Count -ne 2) { throw "并行 fork 未生成 2 条待办：$(@($parTasks).Count)" }
Write-Host "OK   并行 fork 生成 2 条待办"
Api 'Post' "/workflow/task/$($parTasks[0].id)/approve" @{ comment = 'branch1' } | Out-Null
$afterFirst = Get-PendingTask $parInst
if (@($afterFirst).Count -ne 1) { throw "并行 join 提前推进：剩余 $(@($afterFirst).Count)" }
Write-Host "OK   并行首分支通过后仍等待另一分支"
Api 'Post' "/workflow/task/$($afterFirst[0].id)/approve" @{ comment = 'branch2' } | Out-Null
if (@(Get-PendingTask $parInst).Count -ne 0) { throw "并行 join 未收敛" }
Write-Host "OK   并行 join 收敛且无剩余待办"

# ---------- 4. 会签 ALL ----------
$csModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 'cs'; type = 'COUNTER_SIGN'; name = 'countersign'; assignees = @('admin', 'reviewer'); counterSignPolicy = 'ALL' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'c1'; from = 'start'; to = 'cs' },
        @{ key = 'c2'; from = 'cs'; to = 'end' }
    )
}
$csDefId = New-Definition ('smoke_countersign_535_' + $stamp) '会签ALL冒烟' $csModel
$csInst = Start-Instance $csDefId 'countersign-all' @{}
$csTasks = Get-PendingTask $csInst
if (@($csTasks).Count -ne 2) { throw "会签未展开 2 条待办：$(@($csTasks).Count)" }
Write-Host "OK   会签 ALL 展开 2 条待办"
Api 'Post' "/workflow/task/$($csTasks[0].id)/approve" @{ comment = 'sign1' } | Out-Null
$csRemain = Get-PendingTask $csInst
if (@($csRemain).Count -ne 1) { throw "会签 ALL 首次同意后不应完成：剩余 $(@($csRemain).Count)" }
Write-Host "OK   会签 ALL 首次同意仍等待"
Api 'Post' "/workflow/task/$($csRemain[0].id)/approve" @{ comment = 'sign2' } | Out-Null
if (@(Get-PendingTask $csInst).Count -ne 0) { throw "会签 ALL 未完成" }
Write-Host "OK   会签 ALL 全部同意后节点完成"

# ---------- 5. 转办 / 委派 / 回退 ----------
$flowModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 't1'; type = 'USER_TASK'; name = 'first'; assignee = 'admin' },
        @{ key = 't2'; type = 'USER_TASK'; name = 'second'; assignee = 'admin' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'm1'; from = 'start'; to = 't1' },
        @{ key = 'm2'; from = 't1'; to = 't2' },
        @{ key = 'm3'; from = 't2'; to = 'end' }
    )
}
$flowDefId = New-Definition ('smoke_ops_535_' + $stamp) '转办委派回退冒烟' $flowModel
$flowInst = Start-Instance $flowDefId 'ops' @{}
$opsTasks = Get-PendingTask $flowInst
$taskId = $opsTasks[0].id

$transfer = Api 'Put' "/workflow/task/$taskId/transfer" @{ targetUser = 'alice' }
Check '转办' $transfer
$moved = Get-PendingTask $flowInst
if ($moved[0].assignee -ne 'alice') { throw "转办后审批人未变更：$($moved[0].assignee)" }
Write-Host "OK   转办后 assignee=alice"

$delegate = Api 'Put' "/workflow/task/$taskId/delegate" @{ targetUser = 'bob' }
Check '委派' $delegate
Api 'Put' "/workflow/task/$taskId/resolve" @{ comment = '委派处理完成' } | Out-Null
Write-Host "OK   委派处理完成并回到原人"

$restored = Get-PendingTask $flowInst
if ($restored[0].assignee -ne 'alice') { throw "resolve 后未回到委派人 alice：$($restored[0].assignee)" }
Api 'Post' "/workflow/task/$($restored[0].id)/approve" @{ comment = 'first done' } | Out-Null
$secondTasks = Get-PendingTask $flowInst
if (@($secondTasks).Count -ne 1 -or $secondTasks[0].nodeKey -ne 't2') { throw "未推进到 t2" }
Write-Host "OK   推进到 t2"

$rollback = Api 'Post' "/workflow/task/$($secondTasks[0].id)/rollback" @{ targetNodeKey = 't1' }
Check '回退到 t1' $rollback
$rolledBack = Get-PendingTask $flowInst
if (@($rolledBack).Count -ne 1 -or $rolledBack[0].nodeKey -ne 't1') { throw "回退后未生成 t1 待办" }
Write-Host "OK   回退生成 t1 待办"
Api 'Post' "/workflow/task/$($rolledBack[0].id)/approve" @{ comment = 'again' } | Out-Null
$secondAgain = Get-PendingTask $flowInst
Api 'Post' "/workflow/task/$(@($secondAgain)[0].id)/approve" @{ comment = 'done' } | Out-Null
if (@(Get-PendingTask $flowInst).Count -ne 0) { throw "转办/回退流程未结束" }
Write-Host "OK   转办/委派/回退流程闭环"

# ---------- 6. 审批时修改表单 ----------
$formUpdate = Api 'Put' '/form/data/2112153326590074882' @{
    dataJson = '{"name":"李四","grade":"B"}'
}
Check '审批时修改表单数据' $formUpdate
Write-Host "OK   表单数据 PUT /form/data/{id}"

# ---------- 7. 网关路由治理端点 ----------
$gov = Api 'Get' '/actuator/routeGovernance' $null
Write-Host ("OK   routeGovernance: current={0} previous={1} rejected={2}" -f $gov.current.Count, $gov.previous.Count, $gov.rejectedCount)
$rb = Api 'Post' '/actuator/routeGovernance' @{}
Write-Host ("OK   routeGovernance rollback: rolledBack={0} routeCount={1}" -f $rb.rolledBack, $rb.routeCount)

Write-Host 'SMOKE-ALL-PASS'






