# ============================================================
# M1 链路冒烟 3/3：全链状态与留痕核对
#
# 覆盖：
#   1) 读取 chain-1 / chain-2 写入的单号（或显式传参）
#   2) CRM 订单状态机时间戳（submittedAt → approvedAt → pushedAt）齐备
#   3) ERP 销售订单 / 生产订单终态（COMPLETED）与完工数量一致
#   4) MES 工单终态（COMPLETED）
#   5) whale-audit 中按模块可查到链上关键操作（谁、何时、改了什么）
#
# 前置：
#   1) 已执行 bw-chain-1-crm-to-erp.ps1 与 bw-chain-2-erp-mes-erp.ps1（同一状态文件）；
#   2) whale-audit 就绪，各服务已开启审计转发（whale.audit.forward.enabled=true）；
#   3) 管理员账号口令经 SMOKE_PASSWORD 提供。
#
# 用法：pwsh -File scripts/smoke/bw-chain-3-status-audit.ps1 [-StateFile <path>]
# 退出码：0 通过 / 0（SKIP 且已标注）/ 1 断言失败。
# ============================================================

param(
    [string]$BaseUri = $(if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }),
    [string]$StateFile = '',
    [string]$CrmOrderId = '',
    [string]$ProductionOrderId = ''
)

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')
$statePath = Get-SmokeChainStatePath -Override $StateFile

$state = Read-SmokeChainState -Path $statePath
if (-not $state -and -not ($CrmOrderId -and $ProductionOrderId)) {
    Skip-Smoke "未找到链路状态文件 $statePath，且未传 -CrmOrderId/-ProductionOrderId：请先执行 chain-1 与 chain-2"
}

$crmId = if ($CrmOrderId) { [long]$CrmOrderId } else { [long]$state.crmOrderId }
$productionId = if ($ProductionOrderId) { [long]$ProductionOrderId } else { [long]$state.productionOrderId }
$startedAt = if ($state.chain2StartedAt -and $state.startedAt) {
    if ([datetime]$state.chain2StartedAt -lt [datetime]$state.startedAt) { $state.chain2StartedAt } else { $state.startedAt }
} elseif ($state.startedAt) { $state.startedAt } else { $state.chain2StartedAt }
if (-not $startedAt) {
    # 无状态文件时按最近 2 小时窗口核对审计留痕
    $startedAt = (Get-Date).AddHours(-2).ToString('yyyy-MM-ddTHH:mm:ss')
}

# 1) 登录
$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

# 2) CRM 状态机留痕
$crmDetail = Invoke-SmokeApi -Method Get -Path "/crm/order/$crmId"
Check '2.crmOrder' $crmDetail
$crm = $crmDetail.data
Assert '2a.crmPushed' ($null -ne $crm.erpOrderNo) "erpOrderNo=$($crm.erpOrderNo)"
Assert '2b.submittedAt' ($null -ne $crm.submittedAt) "submittedAt=$($crm.submittedAt)"
Assert '2c.approvedAt' ($null -ne $crm.approvedAt) "approvedAt=$($crm.approvedAt)"
Assert '2d.pushedAt' ($null -ne $crm.pushedAt) "pushedAt=$($crm.pushedAt)"
Assert '2e.timeOrder' (
    [datetime]$crm.submittedAt -le [datetime]$crm.approvedAt -and
    [datetime]$crm.approvedAt -le [datetime]$crm.pushedAt) `
    "submitted=$($crm.submittedAt) approved=$($crm.approvedAt) pushed=$($crm.pushedAt)"

# 3) ERP 销售订单与生产订单终态
$erpSales = Invoke-SmokeApi -Method Get -Path "/erp/scm/sales-order/$($crm.erpOrderId)"
Check '3.erpSalesOrder' $erpSales
Assert '3a.crmOrderNo' ($erpSales.data.crmOrderNo -eq $crm.orderNo) `
    "erp=$($erpSales.data.crmOrderNo) crm=$($crm.orderNo)"

$production = Invoke-SmokeApi -Method Get -Path "/erp/mfg/production/$productionId"
Check '4.productionOrder' $production
Assert '4a.productionCompleted' ($production.data.status -eq 'COMPLETED') "status=$($production.data.status)"
Assert '4b.completedQuantity' (
    [decimal]$production.data.completedQuantity -eq [decimal]$production.data.plannedQuantity) `
    "completed=$($production.data.completedQuantity) planned=$($production.data.plannedQuantity)"

# 4) MES 工单终态
$mesOrderId = if ($state -and $state.mesWorkOrderId) { $state.mesWorkOrderId } else { $null }
if (-not $mesOrderId) {
    $mesPage = Invoke-SmokeApi -Method Get -Path "/mes/work-order/page?erpOrderNo=$($production.data.orderNo)&current=1&size=10"
    $mesOrderId = ($mesPage.data.records | Select-Object -First 1).id
}
Assert '5.mesOrderId' ($null -ne $mesOrderId) "mesWorkOrderId=$mesOrderId"
$mesOrder = Invoke-SmokeApi -Method Get -Path "/mes/work-order/$mesOrderId"
Check '5a.mesOrder' $mesOrder
Assert '5b.mesCompleted' ($mesOrder.data.status -eq 'COMPLETED') "status=$($mesOrder.data.status)"
Assert '5c.mesErpOrderNo' ($mesOrder.data.erpOrderNo -eq $production.data.orderNo) `
    "mes=$($mesOrder.data.erpOrderNo) erp=$($production.data.orderNo)"

# 5) 审计留痕：按服务模块核对关键操作
try {
    $auditProbe = Invoke-SmokeApi -Method Get -Path '/audit/log/page?current=1&size=1'
    if ($auditProbe.code -ne 0) {
        Skip-Smoke "whale-audit 不可用（code=$($auditProbe.code) $($auditProbe.message)）：无法核对留痕"
    }
} catch {
    Skip-Smoke "whale-audit 不可达：$($_.Exception.Message)"
}

$startIso = ([datetime]$startedAt).ToString('yyyy-MM-ddTHH:mm:ss')
$endIso = (Get-Date).AddMinutes(5).ToString('yyyy-MM-ddTHH:mm:ss')
$expected = [ordered]@{
    'whale-crm' = @('新建商务订单', '提交商务订单审批')
    'whale-erp' = @('新增生产订单', '下达生产订单', '生产领料')
    'whale-mes' = @('MES 工单拆分', 'MES 批量派工', 'MES 生产报工', 'MES 检验计划保存',
        'MES 检验任务创建', 'MES 检验结果录入', 'MES 检验判定', 'MES 任务完工')
}
foreach ($module in $expected.Keys) {
    $uri = "/audit/log/page?module=$module&current=1&size=200&startTime=$startIso&endTime=$endIso"
    $records = Invoke-SmokeApi -Method Get -Path $uri
    Check "6.audit[$module]" $records
    $ops = @($records.data.records | ForEach-Object { $_.operation })
    foreach ($op in $expected[$module]) {
        Assert "6a[$module].$op" ($ops -contains $op) "ops=$($ops -join ',')"
    }
    # 链上关键操作必须能回答「谁、对什么单据」：用户与对象类型不得为空
    $chainRecords = @($records.data.records | Where-Object { $expected[$module] -contains $_.operation })
    $missingActor = @($chainRecords | Where-Object {
        [string]::IsNullOrWhiteSpace($_.username) -or [string]::IsNullOrWhiteSpace($_.targetType)
    })
    Assert "6b[$module].actor" ($missingActor.Count -eq 0) "missing=$($missingActor.Count)"
}

Write-Host "链上留痕窗口：$startIso ~ $endIso"
Exit-Smoke
