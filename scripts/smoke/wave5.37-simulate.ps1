
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
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
    if ($null -ne $json) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
        return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers `
            -ContentType 'application/json;charset=utf-8' -Body $bytes
    }
    return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers
}

function StepKeys($simulate) {
    return ($simulate.steps | ForEach-Object { $_.nodeKey }) -join '>'
}

# ---------- 1. 登录 ----------
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json;charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes($cred.JsonBody))
$script:token = $login.data.accessToken
Write-Host "OK   登录获取令牌"

$dual = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT' },
        @{ key = 'gw'; type = 'EXCLUSIVE_GATEWAY' },
        @{ key = 'high'; type = 'USER_TASK'; name = '高额审批'; assignee = 'manager' },
        @{ key = 'low'; type = 'USER_TASK'; name = '低额审批'; assignee = 'clerk' },
        @{ key = 'end'; type = 'END_EVENT' }
    )
    flows = @(
        @{ key = 'f0'; from = 'start'; to = 'gw' },
        @{ key = 'fHigh'; from = 'gw'; to = 'high'; condition = 'amount > 100' },
        @{ key = 'fLow'; from = 'gw'; to = 'low'; isDefault = $true },
        @{ key = 'fh'; from = 'high'; to = 'end' },
        @{ key = 'fl'; from = 'low'; to = 'end' }
    )
}

# ---------- 2. 条件命中高额分支 ----------
$high = Api 'Post' '/workflow/definition/simulate' @{
    definitionJson = ($dual | ConvertTo-Json -Depth 20 -Compress)
    variables      = @{ amount = 200 }
}
Check '模拟：高额分支返回成功' $high
Assert '模拟：高额分支 valid=true' ($high.data.valid -eq $true)
Assert '模拟：高额分支抵达结束事件' ($high.data.reachedEnd -eq $true)
Assert '模拟：高额分支路径 start>gw>high>end' ((StepKeys $high.data) -eq 'start>gw>high>end')
$highStep = $high.data.steps | Where-Object { $_.nodeKey -eq 'high' }
Assert '模拟：高额分支入边 fHigh 且携带条件' ($highStep.flowKey -eq 'fHigh' -and $highStep.condition -eq 'amount > 100')
Assert '模拟：高额分支审批人 manager' ($highStep.assignee -eq 'manager')

# ---------- 3. 条件未命中走默认流 ----------
$low = Api 'Post' '/workflow/definition/simulate' @{
    definitionJson = ($dual | ConvertTo-Json -Depth 20 -Compress)
    variables      = @{ amount = 10 }
}
Check '模拟：默认分支返回成功' $low
Assert '模拟：默认分支路径 start>gw>low>end' ((StepKeys $low.data) -eq 'start>gw>low>end')
$lowStep = $low.data.steps | Where-Object { $_.nodeKey -eq 'low' }
Assert '模拟：默认分支入边 fLow 且 isDefault=true' ($lowStep.flowKey -eq 'fLow' -and $lowStep.isDefault -eq $true)

# ---------- 4. 条件均未命中且无默认流 ----------
$ambiguous = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT' },
        @{ key = 't1'; type = 'USER_TASK'; name = '审批'; assignee = 'admin' },
        @{ key = 'a'; type = 'USER_TASK'; name = 'A'; assignee = 'alice' },
        @{ key = 'b'; type = 'USER_TASK'; name = 'B'; assignee = 'bob' },
        @{ key = 'end'; type = 'END_EVENT' }
    )
    flows = @(
        @{ key = 'f0'; from = 'start'; to = 't1' },
        @{ key = 'fa'; from = 't1'; to = 'a'; condition = 'amount > 100' },
        @{ key = 'fb'; from = 't1'; to = 'b'; condition = "level == 'VIP'" },
        @{ key = 'fa2'; from = 'a'; to = 'end' },
        @{ key = 'fb2'; from = 'b'; to = 'end' }
    )
}
$none = Api 'Post' '/workflow/definition/simulate' @{
    definitionJson = ($ambiguous | ConvertTo-Json -Depth 20 -Compress)
    variables      = @{ amount = 10; level = 'NORMAL' }
}
Check '模拟：无匹配分支返回成功（错误落 VO）' $none
Assert '模拟：无匹配分支 valid=true' ($none.data.valid -eq $true)
Assert '模拟：无匹配分支 errorCode=NO_MATCHING_FLOW' ($none.data.errorCode -eq 'NO_MATCHING_FLOW')
Assert '模拟：无匹配分支路径停在 start>t1' ((StepKeys $none.data) -eq 'start>t1')

# ---------- 5. 结构非法定义 ----------
$invalid = Api 'Post' '/workflow/definition/simulate' @{ definitionJson = 'not-a-json' }
Check '模拟：非法 JSON 返回成功（valid=false）' $invalid
Assert '模拟：非法 JSON valid=false' ($invalid.data.valid -eq $false)
Assert '模拟：非法 JSON 无路径' ($invalid.data.steps.Count -eq 0)

Write-Host 'SMOKE-ALL-PASS'
