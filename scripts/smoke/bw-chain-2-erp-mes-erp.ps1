# ============================================================
# M1 链路冒烟 2/3：ERP 生产订单 → MES 派工报工 → ERP 完工入库
#
# 覆盖：
#   1) ERP 物料 / 仓库 / 库存准备（领料仓备料）
#   2) ERP 建生产订单 → 下达（自动下发 MES）
#   3) ERP 领料（订单转生产中）
#   4) MES 拆分 → 派工 → 开工 → 报工 → 质检（IPQC）→ 完工
#   5) MES 完工发件箱回传 ERP → 完工入库，数量与计划一致
#
# 前置：
#   1) gateway(8080)/auth(8081)/whale-erp(9001)/whale-mes(9002) 就绪，ERP 能通过注册中心发现 MES；
#   2) whale-mes：WHALE_MES_OUTBOX_ENABLED=true 且 WHALE_ERP_URL 指向 ERP（完工自动回传）；
#   3) 管理员账号口令经 SMOKE_PASSWORD 提供。
#
# 用法：pwsh -File scripts/smoke/bw-chain-2-erp-mes-erp.ps1 [-BaseUri http://127.0.0.1:8080]
# 退出码：0 通过 / 0（SKIP 且已标注）/ 1 断言失败。
# ============================================================

param(
    [string]$BaseUri = '',
    [string]$StateFile = '',
    [int]$WaitSeconds = 0
)

if (-not $BaseUri) {
    $BaseUri = if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }
}
if ($WaitSeconds -le 0) {
    $WaitSeconds = if ($env:CHAIN_WAIT_SECONDS) { [int]$env:CHAIN_WAIT_SECONDS } else { 300 }
}

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')
$statePath = Get-SmokeChainStatePath -Override $StateFile

# 1) 登录
$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/v1/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

# 2) 环境预检：MES 不可达时显式跳过
try {
    $mesCheck = Invoke-SmokeApi -Method Get -Path '/mes/v1/work-order/page?current=1&size=1'
    if ($mesCheck.code -ne 0) {
        Skip-Smoke "whale-mes 不可用（code=$($mesCheck.code) $($mesCheck.message)）"
    }
} catch {
    Skip-Smoke "whale-mes 不可达：$($_.Exception.Message)"
}

$stamp = Get-Date -Format 'yyyyMMddHHmmss'
$chain2StartedAt = (Get-Date).ToString('yyyy-MM-ddTHH:mm:ss')
$productCode = "SMOKE-PROD-$stamp"
$materialCode = "SMOKE-MAT-$stamp"
$plannedQuantity = 10

# 3) ERP 仓库（优先复用启用仓）
$warehouses = Invoke-SmokeApi -Method Get -Path '/erp/v1/master-data/warehouse/page?current=1&size=50'
Check '2.warehousePage' $warehouses
$warehouse = $warehouses.data.records | Where-Object { $_.status -eq 'ENABLED' } | Select-Object -First 1
if (-not $warehouse) {
    $createdWarehouse = Invoke-SmokeApi -Method Post -Path '/erp/v1/master-data/warehouse' -Body @{
        code = "SMOKE-WH2-$stamp"
        name = '链路冒烟仓（生产）'
        status = 'ENABLED'
        remark = 'M1 链路冒烟自动创建'
    }
    Check '3.createWarehouse' $createdWarehouse
    $warehouse = $createdWarehouse.data
}

# 4) 物料主数据：产出品 + 用料
$product = Invoke-SmokeApi -Method Post -Path '/erp/v1/item' -Body @{
    code = $productCode
    name = '链路冒烟产出品'
    status = 'ENABLED'
    remark = 'M1 链路冒烟自动创建'
}
Check '4.createProductItem' $product
$material = Invoke-SmokeApi -Method Post -Path '/erp/v1/item' -Body @{
    code = $materialCode
    name = '链路冒烟用料'
    status = 'ENABLED'
    remark = 'M1 链路冒烟自动创建'
}
Check '5.createMaterialItem' $material

# 5) 领料仓备料（库存调整入库）
$stock = Invoke-SmokeApi -Method Post -Path '/erp/v1/scm/stock/adjust' -Body @{
    itemCode = $materialCode
    warehouseId = $warehouse.id
    quantity = 100
    remark = 'M1 链路冒烟备料'
}
Check '6.stockAdjust' $stock

# 6) ERP 生产订单 → 下达
$today = (Get-Date).ToString('yyyy-MM-dd')
$production = Invoke-SmokeApi -Method Post -Path '/erp/v1/mfg/production' -Body @{
    itemCode = $productCode
    plannedQuantity = $plannedQuantity
    issueWarehouseId = $warehouse.id
    targetWarehouseId = $warehouse.id
    planStartDate = $today
    planEndDate = $today
    remark = 'M1 链路冒烟'
    materials = @(
        @{
            itemCode = $materialCode
            requiredQuantity = $plannedQuantity
            uom = 'PCS'
            backflush = $false
        }
    )
}
Check '7.createProductionOrder' $production
$productionId = $production.data.id
$productionNo = $production.data.orderNo
Assert '7a.productionDraft' ($production.data.status -eq 'DRAFT') "status=$($production.data.status)"

$released = Invoke-SmokeApi -Method Post -Path "/erp/v1/mfg/production/$productionId/release"
Check '8.release' $released
Assert '8a.released' ($released.data.status -eq 'RELEASED') "status=$($released.data.status)"

# 7) 领料（RELEASED → IN_PROGRESS；倒冲料也在此阶段出库）
$issued = Invoke-SmokeApi -Method Post -Path "/erp/v1/mfg/production/$productionId/issue" -Body @{
    lines = @(@{ lineNo = 1; quantity = $plannedQuantity })
}
Check '9.issue' $issued
Assert '9a.inProgress' ($issued.data.status -eq 'IN_PROGRESS') "status=$($issued.data.status)"

# 8) 等待 ERP 下发 MES（事务提交后推送）
$mesOrder = Wait-SmokeUntil -Label "10.mesWorkOrder" -TimeoutSeconds $WaitSeconds -IntervalSeconds 5 -Probe {
    $page = Invoke-SmokeApi -Method Get -Path "/mes/v1/work-order/page?erpOrderNo=$productionNo&current=1&size=10"
    if ($page.code -ne 0) { return $null }
    return $page.data.records | Select-Object -First 1
}
$mesOrderId = $mesOrder.id
Assert '10a.mesOrderItem' ($mesOrder.itemCode -eq $productCode) "itemCode=$($mesOrder.itemCode)"
Assert '10b.mesPlanned' ([decimal]$mesOrder.plannedQuantity -eq [decimal]$plannedQuantity) `
    "planned=$($mesOrder.plannedQuantity)"

# 9) MES 拆分（显式单工序，避免依赖工艺路线种子）
$split = Invoke-SmokeApi -Method Post -Path "/mes/v1/dispatch/work-order/$mesOrderId/split" -Body @{
    items = @(
        @{
            seqNo = 1
            operationCode = 'OP-CHAIN'
            operationName = '链路冒烟工序'
            plannedQuantity = $plannedQuantity
        }
    )
}
Check '11.split' $split
$taskId = $split.data[0].id
Assert '11a.taskId' ($null -ne $taskId) "taskId=$taskId"

# 10) MES 产线（无启用产线时创建车间 + 产线）
$lines = Invoke-SmokeApi -Method Get -Path '/mes/v1/master-data/line/options'
Check '12.lineOptions' $lines
$line = @($lines.data) | Select-Object -First 1
if (-not $line) {
    $workshop = Invoke-SmokeApi -Method Post -Path '/mes/v1/master-data/workshop' -Body @{
        code = "SMOKE-WS-$stamp"
        name = '链路冒烟车间'
        status = 'ENABLED'
    }
    Check '13.createWorkshop' $workshop
    $createdLine = Invoke-SmokeApi -Method Post -Path '/mes/v1/master-data/line' -Body @{
        code = "SMOKE-LINE-$stamp"
        name = '链路冒烟产线'
        status = 'ENABLED'
        workshopId = $workshop.data.id
    }
    Check '13a.createLine' $createdLine
    $line = $createdLine.data
}

# 11) 派工 → 开工 → 报工 → 完工
$dispatch = Invoke-SmokeApi -Method Post -Path '/mes/v1/dispatch/dispatch' -Body @{
    taskIds = @($taskId)
    lineId = $line.id
    remark = 'M1 链路冒烟派工'
}
Check '14.dispatch' $dispatch
Assert '14a.dispatchNo' ($null -ne $dispatch.data) "dispatchNo=$($dispatch.data)"

$started = Invoke-SmokeApi -Method Post -Path "/mes/v1/execution/task/$taskId/start" -Body @{}
Check '15.start' $started
Assert '15a.inProgress' ($started.data.status -eq 'IN_PROGRESS') "status=$($started.data.status)"

$report = Invoke-SmokeApi -Method Post -Path '/mes/v1/execution/report' -Body @{
    taskId = $taskId
    reportQuantity = $plannedQuantity
    qualifiedQuantity = $plannedQuantity
    defectQuantity = 0
    reporter = 'M1-chain-smoke'
    remark = 'M1 链路冒烟报工'
}
Check '16.report' $report

# 11.1) 质检（蓝图五环节之一）：建检验计划 → 建 IPQC 任务 → 录结果 → 判定合格
$qcPlan = Invoke-SmokeApi -Method Post -Path '/mes/v1/quality/inspection-plan' -Body @{
    planName = "链路冒烟检验计划-$stamp"
    inspectionType = 'IPQC'
    itemCode = $productCode
    operationCode = 'OP-CHAIN'
    sampleSize = 1
    status = 'ENABLED'
    remark = 'M1 链路冒烟自动创建'
    items = @(
        @{
            itemNo = 1
            itemName = '外观检查'
            inspectionMethod = 'VISUAL'
            standardText = '无划伤、无变形'
            criticalFlag = 1
        }
    )
}
Check '16a.qcPlan' $qcPlan
$qcPlanId = $qcPlan.data.id
$qcPlanItemId = @($qcPlan.data.items)[0].id
Assert '16b.qcPlanItem' ($null -ne $qcPlanItemId) "planId=$qcPlanId"

$qcTask = Invoke-SmokeApi -Method Post -Path '/mes/v1/quality/inspection/task' -Body @{
    taskType = 'IPQC'
    planId = $qcPlanId
    workOrderId = $mesOrderId
    workOrderCode = $mesOrder.code
    execTaskId = $taskId
    operationCode = 'OP-CHAIN'
    sampleSize = 1
    inspector = 'M1-chain-smoke'
    batchNo = "QC-$stamp"
}
Check '16c.qcTask' $qcTask
$qcTaskId = $qcTask.data.id

$qcResults = Invoke-SmokeApi -Method Post -Path "/mes/v1/quality/inspection/task/$qcTaskId/results" -Body @{
    inspector = 'M1-chain-smoke'
    remark = 'M1 链路冒烟质检'
    results = @(
        @{
            planItemId = $qcPlanItemId
            itemNo = 1
            itemName = '外观检查'
            sampleNo = 1
            measuredValue = 'OK'
            result = 'OK'
        }
    )
}
Check '16d.qcResults' $qcResults

$qcJudged = Invoke-SmokeApi -Method Post -Path "/mes/v1/quality/inspection/task/$qcTaskId/judge" -Body @{
    judge = 'M1-chain-smoke'
    conclusion = '合格'
}
Check '16e.qcJudge' $qcJudged
Assert '16f.qcPassed' ($qcJudged.data.status -eq 'PASSED') "status=$($qcJudged.data.status)"

$completed = Invoke-SmokeApi -Method Post -Path "/mes/v1/execution/task/$taskId/complete" -Body @{}
Check '17.completeTask' $completed
Assert '17a.taskCompleted' ($completed.data.status -eq 'COMPLETED') "status=$($completed.data.status)"

$mesCompleted = Wait-SmokeUntil -Label "18.mesOrderCompleted" -TimeoutSeconds 60 -IntervalSeconds 3 -Probe {
    $detail = Invoke-SmokeApi -Method Get -Path "/mes/v1/work-order/$mesOrderId"
    if ($detail.code -eq 0 -and $detail.data.status -eq 'COMPLETED') { return $detail.data }
    return $null
}
Assert '18a.mesCompleted' ($mesCompleted.status -eq 'COMPLETED') "status=$($mesCompleted.status)"

# 12) 等待 MES 完工回传 ERP 并入库
$erpCompleted = Wait-SmokeUntil -Label "19.erpCompleted" -TimeoutSeconds $WaitSeconds -IntervalSeconds 5 -Probe {
    $detail = Invoke-SmokeApi -Method Get -Path "/erp/v1/mfg/production/$productionId"
    if ($detail.code -ne 0) { return $null }
    if ($detail.data.status -eq 'COMPLETED') { return $detail.data }
    return $null
}
Assert '19a.completedQuantity' ([decimal]$erpCompleted.completedQuantity -eq [decimal]$plannedQuantity) `
    "completed=$($erpCompleted.completedQuantity) planned=$plannedQuantity"

# 13) 入库落账：产成品余额 ≥ 完工数量
$balance = Wait-SmokeUntil -Label "20.stockBalance" -TimeoutSeconds 60 -IntervalSeconds 3 -Probe {
    $page = Invoke-SmokeApi -Method Get -Path "/erp/v1/scm/stock/balance/page?itemId=$($erpCompleted.itemId)&warehouseId=$($warehouse.id)&current=1&size=10"
    if ($page.code -ne 0) { return $null }
    $row = $page.data.records | Where-Object { "$($_.warehouseId)" -eq "$($warehouse.id)" } | Select-Object -First 1
    if ($row -and [decimal]$row.quantity -ge [decimal]$plannedQuantity) { return $row }
    return $null
}
Assert '20a.inboundQuantity' ([decimal]$balance.quantity -ge [decimal]$plannedQuantity) "quantity=$($balance.quantity)"

Save-SmokeChainState -Path $statePath -Values @{
    chain2StartedAt = $chain2StartedAt
    productionOrderId = $productionId
    productionOrderNo = $productionNo
    mesWorkOrderId = $mesOrderId
    mesWorkOrderCode = $mesOrder.code
    chain2ItemCode = $productCode
    chain2WarehouseId = $warehouse.id
}

Write-Host "STATE $statePath"
Exit-Smoke
