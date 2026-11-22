# ============================================================
# M1 对账：3D 看板数量 == ERP 数据库数量
#
# 口径：
#   1) 3D 看板（/scene/board/manufacturing）逐张生产订单比对
#      ERP 生产订单的 plannedQuantity / completedQuantity / status；
#   2) 3D 看板库存余额逐条比对 ERP 库存余额（warehouseId + itemCode + quantity）。
#   任一字段不一致立即失败（退出码 1），数据源降级同样按失败处理。
#
# 前置：
#   1) gateway / whale-scene / whale-erp 在线，scene 能访问 ERP 内网；
#   2) 管理员账号口令经 SMOKE_PASSWORD 提供。
#
# 用法：pwsh -File scripts/verify/chain-reconcile.ps1 [-BaseUri http://127.0.0.1:8080]
# ============================================================

param(
    [string]$BaseUri = '',
    [int]$Limit = 200
)

if (-not $BaseUri) {
    $BaseUri = if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }
}

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/../smoke/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')

# 1) 登录
$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/v1/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
Assert '1.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken) "(code=$($login.code))"
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

# 2) 看板数据：降级即失败（对账前提是看板与数据库同源可用）
$board = Invoke-SmokeApi -Method Get -Path "/scene/board/manufacturing?limit=$Limit"
Check '2.board' $board
Assert '2a.notDegraded' (-not $board.data.degraded) "message=$($board.data.message)"
$boardOrders = @($board.data.orders)
$boardStocks = @($board.data.stocks)
Write-Host "看板：生产订单 $($boardOrders.Count) 张，库存余额 $($boardStocks.Count) 条"

# 3) ERP 数据
$erpOrdersPage = Invoke-SmokeApi -Method Get -Path "/erp/v1/mfg/production/page?current=1&size=$Limit"
Check '3.erpProductionPage' $erpOrdersPage
$erpOrders = @{}
foreach ($row in @($erpOrdersPage.data.records)) {
    $erpOrders["$($row.orderNo)"] = $row
}

$erpStocksPage = Invoke-SmokeApi -Method Get -Path "/erp/v1/scm/stock/balance/page?current=1&size=$Limit"
Check '4.erpStockPage' $erpStocksPage
$erpStocks = @{}
foreach ($row in @($erpStocksPage.data.records)) {
    $erpStocks["$($row.warehouseId)|$($row.itemCode)"] = $row
}

# 4) 生产订单逐张比对
$mismatch = 0
foreach ($line in $boardOrders) {
    $key = "$($line.orderNo)"
    if (-not $erpOrders.ContainsKey($key)) {
        Write-Host "FAIL 生产订单在看板存在、ERP 不存在：$key"
        $mismatch++
        continue
    }
    $erp = $erpOrders[$key]
    if ([decimal]$line.plannedQuantity -ne [decimal]$erp.plannedQuantity) {
        Write-Host "FAIL 计划数量不一致 $key：board=$($line.plannedQuantity) erp=$($erp.plannedQuantity)"
        $mismatch++
    }
    if ([decimal]$line.completedQuantity -ne [decimal]$erp.completedQuantity) {
        Write-Host "FAIL 完工数量不一致 $key：board=$($line.completedQuantity) erp=$($erp.completedQuantity)"
        $mismatch++
    }
    if ("$($line.status)" -ne "$($erp.status)") {
        Write-Host "FAIL 状态不一致 $key：board=$($line.status) erp=$($erp.status)"
        $mismatch++
    }
}
Assert '5.orderQuantityMatches' ($mismatch -eq 0) "mismatches=$mismatch"

# 5) 库存余额逐条比对
$stockMismatch = 0
foreach ($line in $boardStocks) {
    $key = "$($line.warehouseId)|$($line.itemCode)"
    if (-not $erpStocks.ContainsKey($key)) {
        Write-Host "FAIL 库存余额在看板存在、ERP 不存在：$key"
        $stockMismatch++
        continue
    }
    $erp = $erpStocks[$key]
    if ([decimal]$line.quantity -ne [decimal]$erp.quantity) {
        Write-Host "FAIL 库存数量不一致 $key：board=$($line.quantity) erp=$($erp.quantity)"
        $stockMismatch++
    }
}
Assert '6.stockQuantityMatches' ($stockMismatch -eq 0) "mismatches=$stockMismatch"

Exit-Smoke
