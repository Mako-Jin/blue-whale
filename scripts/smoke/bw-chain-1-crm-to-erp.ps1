# ============================================================
# M1 链路冒烟 1/3：CRM 销售订单 → ERP 销售订单
#
# 覆盖：
#   1) ERP 主数据准备（仓库 / 物料 / 客户）
#   2) CRM 建单 → 提交审批 → workflow 审批通过
#   3) CRM 审批对账定时任务回填 APPROVED → outbox 下发 ERP
#   4) ERP 销售订单按 crmOrderNo 幂等落地，数量/物料一致
#
# 前置：
#   1) gateway(8080)/auth(8081)/workflow(8083)/whale-crm(9007)/whale-erp(9001) 就绪；
#   2) whale-crm：WHALE_CRM_ORDER_DEFINITION_ID 已配置，WHALE_CRM_ERP_ENABLED=true；
#   3) 管理员账号口令经 SMOKE_PASSWORD 提供。
#
# 用法：pwsh -File scripts/smoke/bw-chain-1-crm-to-erp.ps1 [-BaseUri http://127.0.0.1:8080]
# 退出码：0 通过 / 0（SKIP 且已在输出与 Step Summary 标注）/ 1 断言失败。
# ============================================================

param(
    [string]$BaseUri = $(if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }),
    [string]$StateFile = '',
    [int]$WaitSeconds = $(if ($env:CHAIN_WAIT_SECONDS) { [int]$env:CHAIN_WAIT_SECONDS } else { 300 })
)

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')
$statePath = Get-SmokeChainStatePath -Override $StateFile

# 1) 登录
$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

# 2) 环境预检：workflow 不可达时显式跳过，避免把环境缺失当成链路故障
try {
    $wf = Invoke-SmokeApi -Method Get -Path '/workflow/definition/page?current=1&size=1'
    if ($wf.code -ne 0) {
        Skip-Smoke "whale-workflow 不可用（code=$($wf.code) $($wf.message)）：无法执行审批环节"
    }
} catch {
    Skip-Smoke "whale-workflow 不可达：$($_.Exception.Message)"
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$startedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
$warehouseCode = "SMOKE-WH-$stamp"
$itemCode = "SMOKE-ITEM-$stamp"
$customerCode = "SMOKE-CUST-$stamp"

# 3) ERP 主数据：仓库（优先复用启用仓）→ 物料 → 客户
$warehouses = Invoke-SmokeApi -Method Get -Path '/erp/master-data/warehouse/page?current=1&size=50'
Check '2.warehousePage' $warehouses
$warehouse = $warehouses.data.records | Where-Object { $_.status -eq 'ENABLED' } | Select-Object -First 1
if (-not $warehouse) {
    $createdWarehouse = Invoke-SmokeApi -Method Post -Path '/erp/master-data/warehouse' -Body @{
        code = $warehouseCode
        name = '链路冒烟仓'
        status = 'ENABLED'
        remark = 'M1 链路冒烟自动创建'
    }
    Check '3.createWarehouse' $createdWarehouse
    $warehouse = $createdWarehouse.data
}
Assert '3a.warehouseReady' ($null -ne $warehouse.id) "warehouseId=$($warehouse.id)"

$item = Invoke-SmokeApi -Method Post -Path '/erp/item' -Body @{
    code = $itemCode
    name = '链路冒烟物料'
    status = 'ENABLED'
    remark = 'M1 链路冒烟自动创建'
}
Check '4.createItem' $item

$customer = Invoke-SmokeApi -Method Post -Path '/erp/master-data/customer' -Body @{
    code = $customerCode
    name = "链路冒烟客户 $stamp"
    status = 'ENABLED'
    remark = 'M1 链路冒烟自动创建'
}
Check '5.createErpCustomer' $customer

$crmCustomer = Invoke-SmokeApi -Method Post -Path '/crm/customer' -Body @{
    customerCode = $customerCode
    name = "链路冒烟客户 $stamp"
}
Check '6.createCrmCustomer' $crmCustomer

# 4) CRM 建单
$order = Invoke-SmokeApi -Method Post -Path '/crm/order' -Body @{
    sourceType = 'MANUAL'
    customerId = $crmCustomer.data.id
    title = "M1 链路冒烟 $stamp"
    currency = 'CNY'
    remark = 'M1 链路冒烟自动创建'
    lines = @(
        @{
            itemCode = $itemCode
            itemName = '链路冒烟物料'
            uom = 'PCS'
            quantity = 10
            unitPrice = 100
        }
    )
}
Check '7.createCrmOrder' $order
$crmOrderId = $order.data.id
$crmOrderNo = $order.data.orderNo
Assert '7a.crmOrderDraft' ($order.data.status -eq 'DRAFT') "status=$($order.data.status)"

# 5) 提交审批（未配置审批定义时显式跳过）
$submit = $null
try {
    $submit = Invoke-SmokeApi -Method Post -Path "/crm/order/$crmOrderId/submit"
} catch {
    Skip-Smoke "CRM 提交审批失败（可能未配置 WHALE_CRM_ORDER_DEFINITION_ID）：$($_.Exception.Message)"
}
if ($submit.code -ne 0 -and "$($submit.message)" -match '审批') {
    Skip-Smoke "CRM 商务订单审批不可用：$($submit.message)"
}
Check '8.submit' $submit
$instanceId = $submit.data.approvalInstanceId
Assert '8a.approvalInstance' ($null -ne $instanceId) "instanceId=$instanceId"

# 6) workflow 审批（可能存在多级节点，逐级通过，最多 10 轮）
for ($round = 1; $round -le 10; $round++) {
    $tasks = Invoke-SmokeApi -Method Get -Path "/workflow/task/page?instanceId=$instanceId&current=1&size=50"
    Check "9.$round.taskPage" $tasks
    $pending = @($tasks.data.records | Where-Object { $_.status -eq 0 -or $_.status -eq 'PENDING' -or $_.status -eq 'WAITING' })
    if ($pending.Count -eq 0) {
        break
    }
    foreach ($task in $pending) {
        $approved = Invoke-SmokeApi -Method Post -Path "/workflow/task/$($task.id)/approve" -Body @{ comment = 'M1 链路冒烟同意' }
        Check "10.$round.approve" $approved
    }
}
$instance = Invoke-SmokeApi -Method Get -Path "/workflow/instance/$instanceId"
Check '11.instance' $instance
# workflow 实例状态：0 运行中 / 1 已完结
Assert '11a.instanceFinished' ($instance.data.status -eq 1) "status=$($instance.data.status)"

# 7) 等待审批对账任务回填 APPROVED（crm.approval.reconcile 默认每 2 分钟）
$approvedOrder = Wait-SmokeUntil -Label "12.crmApproved" -TimeoutSeconds $WaitSeconds -IntervalSeconds 5 -Probe {
    $detail = Invoke-SmokeApi -Method Get -Path "/crm/order/$crmOrderId"
    if ($detail.code -ne 0) { return $null }
    if ($detail.data.status -eq 'APPROVED' -or $detail.data.status -eq 'PUSHED') { return $detail.data }
    if ($detail.data.status -eq 'REJECTED') {
        throw "CRM 订单被驳回：$($detail.data.status)"
    }
    return $null
}
Assert '12a.approvedAt' ($null -ne $approvedOrder.approvedAt) "approvedAt=$($approvedOrder.approvedAt)"

# 8) 等待 outbox 下发 ERP（crm.order.push 默认每分钟）
$pushedOrder = Wait-SmokeUntil -Label "13.erpOrderPushed" -TimeoutSeconds $WaitSeconds -IntervalSeconds 5 -Probe {
    $detail = Invoke-SmokeApi -Method Get -Path "/crm/order/$crmOrderId"
    if ($detail.code -ne 0) { return $null }
    if ($detail.data.erpOrderNo) { return $detail.data }
    if ($detail.data.lastPushError -and "$($detail.data.lastPushError)" -match '未启用') {
        throw "CRM→ERP 下发未启用（WHALE_CRM_ERP_ENABLED=false）：$($detail.data.lastPushError)"
    }
    return $null
}
Assert '13a.erpOrderId' ($null -ne $pushedOrder.erpOrderId) "erpOrderId=$($pushedOrder.erpOrderId)"

# 9) ERP 侧核对：crmOrderNo / 物料 / 数量
$erpOrder = Invoke-SmokeApi -Method Get -Path "/erp/scm/sales-order/$($pushedOrder.erpOrderId)"
Check '14.erpSalesOrder' $erpOrder
Assert '14a.crmOrderNo' ($erpOrder.data.crmOrderNo -eq $crmOrderNo) "erp=$($erpOrder.data.crmOrderNo) crm=$crmOrderNo"
Assert '14b.itemCode' ($erpOrder.data.lines[0].itemCode -eq $itemCode) "itemCode=$($erpOrder.data.lines[0].itemCode)"
Assert '14c.quantity' ([decimal]$erpOrder.data.lines[0].quantity -eq [decimal]10) "quantity=$($erpOrder.data.lines[0].quantity)"

# 10) 幂等：重试下发不重复建单
$retry = Invoke-SmokeApi -Method Post -Path "/crm/order/$crmOrderId/retry"
if ($retry.code -eq 0) {
    $afterRetry = Invoke-SmokeApi -Method Get -Path "/crm/order/$crmOrderId"
    Assert '15.idempotentErpOrderNo' ($afterRetry.data.erpOrderNo -eq $pushedOrder.erpOrderNo) `
        "before=$($pushedOrder.erpOrderNo) after=$($afterRetry.data.erpOrderNo)"
} else {
    Write-Host "SKIP 15.retry 幂等重推（当前状态不允许重试：$($retry.message)）"
}

Save-SmokeChainState -Path $statePath -Values @{
    startedAt = $startedAt
    crmOrderId = $crmOrderId
    crmOrderNo = $crmOrderNo
    erpSalesOrderId = $pushedOrder.erpOrderId
    erpSalesOrderNo = $pushedOrder.erpOrderNo
    chainItemCode = $itemCode
    chainWarehouseId = $warehouse.id
}

Write-Host "STATE $statePath"
Exit-Smoke
