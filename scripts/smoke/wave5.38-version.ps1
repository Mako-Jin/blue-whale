
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
$code = 'smoke_ver_538_' + $stamp
Write-Host "OK   登录获取令牌"

$v1Model = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT' },
        @{ key = 't1'; type = 'USER_TASK'; name = '一级审批'; assignee = 'admin' },
        @{ key = 'end'; type = 'END_EVENT' }
    )
    flows = @(
        @{ key = 'f1'; from = 'start'; to = 't1' },
        @{ key = 'f2'; from = 't1'; to = 'end' }
    )
}

$v2Model = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT' },
        @{ key = 't1'; type = 'USER_TASK'; name = '一级审批V2'; assignee = 'admin' },
        @{ key = 't2'; type = 'USER_TASK'; name = '二级审批'; assignee = 'user' },
        @{ key = 'end'; type = 'END_EVENT' }
    )
    flows = @(
        @{ key = 'f1'; from = 'start'; to = 't1' },
        @{ key = 'f2'; from = 't1'; to = 't2' },
        @{ key = 'f3'; from = 't2'; to = 'end' }
    )
}

# ---------- 2. 创建定义（自动写 v1 已发布快照） ----------
$create = Api 'Post' '/workflow/definition' @{
    code           = $code
    name           = '版本快照冒烟'
    definitionJson = ($v1Model | ConvertTo-Json -Depth 20 -Compress)
}
Check '创建流程定义 v1' $create
$defId = $create.data.id
Assert '创建后版本为 1' ($create.data.version -eq 1)

$versions1 = Api 'Get' "/workflow/definition/$defId/versions" $null
Check '查询首次版本快照' $versions1
Assert '首次仅 1 个版本' ($versions1.data.Count -eq 1)
Assert '首版为已发布' ($versions1.data[0].publishStatus -eq 1)

# ---------- 3. 发起实例 A（锁定 v1） ----------
$instA = Api 'Post' '/workflow/instance' @{ definitionId = $defId; title = '版本迁移实例A' }
Check '发起实例 A' $instA
$detailA = Api 'Get' "/workflow/instance/$($instA.data.id)" $null
Check '查询实例 A' $detailA
Assert '实例 A 锁定版本 1' ($detailA.data.definitionVersion -eq 1)
Assert '实例 A 当前节点 t1' ($detailA.data.currentNodeKey -eq 't1')

# ---------- 4. 更新定义：JSON 变更 -> 版本自增 2 且为草稿 ----------
$update = Api 'Put' "/workflow/definition/$defId" @{
    code           = $code
    name           = '版本快照冒烟'
    definitionJson = ($v2Model | ConvertTo-Json -Depth 20 -Compress)
}
Check '更新定义写入 v2' $update
Assert '更新后版本自增为 2' ($update.data.version -eq 2)

$versions2 = Api 'Get' "/workflow/definition/$defId/versions" $null
Check '查询两次保存后的版本快照' $versions2
Assert '共 2 个版本' ($versions2.data.Count -eq 2)
$v2Row = $versions2.data | Where-Object { $_.version -eq 2 }
$v1Row = $versions2.data | Where-Object { $_.version -eq 1 }
Assert 'v2 为草稿' ($v2Row.publishStatus -eq 0)
Assert 'v1 仍为已发布' ($v1Row.publishStatus -eq 1)

# ---------- 5. 未发布时新实例仍用 v1 ----------
$instB = Api 'Post' '/workflow/instance' @{ definitionId = $defId; title = '版本迁移实例B' }
Check '发起实例 B（v2 未发布）' $instB
$detailB = Api 'Get' "/workflow/instance/$($instB.data.id)" $null
Check '查询实例 B' $detailB
Assert '未发布 v2 时新实例仍锁定 v1' ($detailB.data.definitionVersion -eq 1)

# ---------- 6. 发布 v2 后新实例用 v2 ----------
$publish = Api 'Post' "/workflow/definition/$defId/publish" @{ version = 2 }
Check '发布版本 2' $publish
Assert '发布后定义版本为 2' ($publish.data.version -eq 2)

$instC = Api 'Post' '/workflow/instance' @{ definitionId = $defId; title = '版本迁移实例C' }
Check '发起实例 C（v2 已发布）' $instC
$detailC = Api 'Get' "/workflow/instance/$($instC.data.id)" $null
Check '查询实例 C' $detailC
Assert '发布后新实例锁定 v2' ($detailC.data.definitionVersion -eq 2)

# ---------- 7. 实例 A 迁移到 v2 ----------
$migrate = Api 'Post' "/workflow/definition/$defId/migrate" @{
    targetVersion = 2
    instanceIds   = @($instA.data.id)
}
Check '迁移实例 A 到 v2' $migrate
Assert '迁移成功 1 个' ($migrate.data.migrated -eq 1)
Assert '无迁移失败' ($migrate.data.failed -eq 0)

$detailA2 = Api 'Get' "/workflow/instance/$($instA.data.id)" $null
Check '查询迁移后实例 A' $detailA2
Assert '实例 A 版本已为 2' ($detailA2.data.definitionVersion -eq 2)
$pendingA = @($detailA2.data.tasks | Where-Object { $_.status -eq 0 })
$pendingName = [System.Text.Encoding]::UTF8.GetString([System.Text.Encoding]::GetEncoding(28591).GetBytes($pendingA[0].nodeName))
Assert '实例 A 待办节点同步为 v2' ($pendingA.Count -eq 1 -and $pendingA[0].nodeKey -eq 't1' -and $pendingA[0].nodeIndex -eq 1 -and $pendingName -eq '一级审批V2')

# ---------- 8. 迁移到不存在版本 ----------
$missing = Api 'Post' "/workflow/definition/$defId/migrate" @{ targetVersion = 99 }
Check '迁移到不存在版本返回参数错误' $missing 400

# ---------- 9. 清理 ----------
foreach ($id in @($instA.data.id, $instB.data.id, $instC.data.id)) {
    try { Api 'Post' "/workflow/instance/$id/cancel" $null | Out-Null } catch { }
}
try { Api 'Delete' "/workflow/definition/$defId" $null | Out-Null } catch { }

Write-Host 'SMOKE-ALL-PASS'
