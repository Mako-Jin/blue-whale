# Wave 5.61 M1 合同管理冒烟：登录 → 列表/详情 → 新建/编辑 → 提交审批 → 审批通过同步 → 终止；
# 以及 撤回 → 修订（版本+1）→ 重新提交；另含 APPROVED 状态禁止编辑的负向用例。
# 前置：gateway(8080)/auth(8081)/workflow(8083)/whale-contract(9006) 就绪，
#       whale-contract 以 CONTRACT_APPROVAL_DEFINITION_ID=<已存在流程定义> 启动。
# 用法：powershell -File scripts/smoke/bw-contract-m1-smoke.ps1


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $cred.JsonBody
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }
Assert '0.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken)

# 1) 列表与详情（种子数据）
$page = Invoke-RestMethod -Method Get -Uri "$base/contract/page?current=1&size=10" -Headers $h
Assert '1.page' ($page.code -eq 0 -and $page.data.total -ge 2)
$detail = Invoke-RestMethod -Method Get -Uri "$base/contract/1" -Headers $h
Assert '2.detailSeed' ($detail.code -eq 0 -and $detail.data.parties.Count -ge 2 -and $detail.data.versions.Count -ge 1)

# 2) 新建合同（含相对方）
$createBody = @{
    title          = 'M1 冒烟采购合同'
    category       = 'PURCHASE'
    amount         = 88000.00
    currency       = 'CNY'
    ownerId        = 1
    deptId         = 1
    effectiveDate  = '2026-11-01'
    expireDate     = '2027-10-31'
    remark         = 'M1 冒烟'
    parties        = @(
        @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; contact = '张三'; phone = '13800000001'; signerUserId = 1; signerName = '张三' },
        @{ partyType = 'B'; name = '冒烟供应商有限公司'; creditCode = '91310000MA1K000099'; contact = '李四'; phone = '13800000002'; signerName = '李四' }
    )
} | ConvertTo-Json -Depth 5 -Compress
$created = Invoke-RestMethod -Method Post -Uri "$base/contract" -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($createBody))
Assert '3.createDraft' ($created.code -eq 0 -and $created.data.status -eq 'DRAFT' -and $created.data.code -like 'HT-CG-2026-*' -and $created.data.currentVersion -eq 1)
$cid = $created.data.id

# 3) 编辑草稿
$updateBody = @{
    title         = 'M1 冒烟采购合同（修订）'
    category      = 'PURCHASE'
    amount        = 99000.00
    ownerId       = 1
    deptId        = 1
    effectiveDate = '2026-11-01'
    expireDate    = '2027-10-31'
    parties       = @(@{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; signerUserId = 1; signerName = '张三' })
} | ConvertTo-Json -Depth 5 -Compress
$updated = Invoke-RestMethod -Method Put -Uri "$base/contract/$cid" -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($updateBody))
Assert '4.updateDraft' ($updated.code -eq 0 -and $updated.data.title -like '*修订*' -and $updated.data.amount -eq 99000)

# 4) 提交审批 → 审批通过 → 详情回读同步为 APPROVED
$submit = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid/submit" -Headers $h
Assert '5.submit' ($submit.code -eq 0 -and $submit.data.status -eq 'APPROVING')
$afterSubmit = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid" -Headers $h
Assert '6.flowLinked' ($afterSubmit.data.flows.Count -ge 1 -and $null -ne $afterSubmit.data.flows[0].processInstanceId)
$instId = [long]$afterSubmit.data.flows[0].processInstanceId
$tasks = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instId" -Headers $h
Assert '7.taskCreated' ($tasks.code -eq 0 -and $tasks.data.records.Count -ge 1)
$taskId = $tasks.data.records[0].id
$approve = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskId/approve" -Headers $h -ContentType 'application/json' -Body '{"comment":"M1 冒烟同意"}'
Assert '8.approve' ($approve.code -eq 0)
$afterApprove = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid" -Headers $h
Assert '9.statusApproved' ($afterApprove.data.status -eq 'APPROVED' -and $afterApprove.data.flows[0].status -eq 'APPROVED')

# 5) 负向：APPROVED 不可编辑
$negUpdate = Invoke-RestMethod -Method Put -Uri "$base/contract/$cid" -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($updateBody))
Assert '10.negativeEditApproved' ($negUpdate.code -ne 0)

# 6) 终止合同
$terminated = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid/terminate" -Headers $h
Assert '11.terminate' ($terminated.code -eq 0 -and $terminated.data.status -eq 'TERMINATED')

# 7) 撤回 → 修订（版本+1）→ 重新提交
$create2 = Invoke-RestMethod -Method Post -Uri "$base/contract" -Headers $h -ContentType 'application/json; charset=utf-8' -Body ([System.Text.Encoding]::UTF8.GetBytes($createBody))
Assert '12.createSecond' ($create2.code -eq 0)
$cid2 = $create2.data.id
$sub2 = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/submit" -Headers $h
Assert '13.submitSecond' ($sub2.code -eq 0 -and $sub2.data.status -eq 'APPROVING')
$wd = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/withdraw" -Headers $h
Assert '14.withdraw' ($wd.code -eq 0 -and $wd.data.status -eq 'WITHDRAWN')
$ver = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/versions" -Headers $h -ContentType 'application/json' -Body '{"changeNote":"冒烟修订"}'
Assert '15.createVersion' ($ver.code -eq 0 -and $ver.data.versionNo -eq 2 -and $ver.data.status -eq 'DRAFT')
$versions = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2/versions" -Headers $h
Assert '16.versionsListed' ($versions.code -eq 0 -and $versions.data.Count -eq 2 -and $versions.data[0].versionNo -eq 2)
$resub = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/submit" -Headers $h
Assert '17.resubmit' ($resub.code -eq 0 -and $resub.data.status -eq 'APPROVING' -and $resub.data.currentVersion -eq 2)

Write-Host '合同 M1 冒烟完成：CRUD → 审批 → 同步 → 终止 / 撤回 → 修订 → 重提 全通过。'
