
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
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

# ---------- 1. 登录 ----------
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json;charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($cred.JsonBody))
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

function Start-Instance($definitionId, [string]$title) {
    $result = Api 'Post' '/workflow/instance' @{ definitionId = $definitionId; title = $title }
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

# ---------- 2. 加签：普通审批节点 ----------
$signModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 't1'; type = 'USER_TASK'; name = 'single'; assignee = 'admin' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'a1'; from = 'start'; to = 't1' },
        @{ key = 'a2'; from = 't1'; to = 'end' }
    )
}
$signDefId = New-Definition ('smoke_addsign_536_' + $stamp) '加签冒烟' $signModel
$signInst = Start-Instance $signDefId 'add-sign'
$signTasks = Get-PendingTask $signInst
if (@($signTasks).Count -ne 1 -or $signTasks[0].assignee -ne 'admin') {
    throw "初始待办异常：$($signTasks | ConvertTo-Json -Depth 5 -Compress)"
}
$mainTaskId = $signTasks[0].id
Write-Host "OK   初始待办 assignee=admin"

$duplicated = Api 'Post' "/workflow/task/$mainTaskId/add-sign" @{ assignees = @('admin') }
Check '重复加签人被拒绝' $duplicated 400
Write-Host "OK   重复加签人返回 400"

$added = Api 'Post' "/workflow/task/$mainTaskId/add-sign" @{ assignees = @('reviewer', ' reviewer ') }
Check '加签 reviewer' $added
$afterAdd = Get-PendingTask $signInst
if (@($afterAdd).Count -ne 2) { throw "加签后待办数异常：$(@($afterAdd).Count)" }
$signer = @($afterAdd | Where-Object { $_.assignee -eq 'reviewer' })
if ($signer.Count -ne 1 -or $signer[0].signType -ne 'ADD_SIGN' -or $signer[0].ownerId -ne 'admin') {
    throw "加签待办字段异常：$($signer | ConvertTo-Json -Depth 5 -Compress)"
}
Write-Host "OK   加签生成 ADD_SIGN 待办，ownerId=admin，同节点去重"

Api 'Post' "/workflow/task/$mainTaskId/approve" @{ comment = 'main approved' } | Out-Null
$waiting = Get-PendingTask $signInst
if (@($waiting).Count -ne 1 -or $waiting[0].assignee -ne 'reviewer') {
    throw "加签未完成时提前推进：$($waiting | ConvertTo-Json -Depth 5 -Compress)"
}
if ((Get-InstanceStatus $signInst) -ne 0) { throw "加签未完成时实例不应结束" }
Write-Host "OK   原审批人同意后等待加签人，实例仍运行中"

Api 'Post' "/workflow/task/$($waiting[0].id)/approve" @{ comment = 'sign approved' } | Out-Null
if (@(Get-PendingTask $signInst).Count -ne 0) { throw "加签人同意后仍有待办" }
if ((Get-InstanceStatus $signInst) -ne 1) { throw "加签全部完成后实例应通过" }
Write-Host "OK   加签人同意后节点完成，实例通过"

# ---------- 3. 加签：ANY 会签节点拒绝 ----------
$anyModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; name = 'start' },
        @{ key = 'cs'; type = 'COUNTER_SIGN'; name = 'any-countersign'; assignees = @('admin', 'reviewer'); counterSignPolicy = 'ANY' },
        @{ key = 'end'; type = 'END_EVENT'; name = 'end' }
    )
    flows = @(
        @{ key = 'b1'; from = 'start'; to = 'cs' },
        @{ key = 'b2'; from = 'cs'; to = 'end' }
    )
}
$anyDefId = New-Definition ('smoke_addsign_any_536_' + $stamp) 'ANY会签加签拒绝冒烟' $anyModel
$anyInst = Start-Instance $anyDefId 'add-sign-any'
$anyTasks = Get-PendingTask $anyInst
$rejected = Api 'Post' "/workflow/task/$($anyTasks[0].id)/add-sign" @{ assignees = @('lisi') }
Check 'ANY 会签节点拒绝加签' $rejected 400
Write-Host "OK   ANY 会签节点拒绝加签"

Write-Host 'SMOKE-ALL-PASS'
