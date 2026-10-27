# Wave 5.65 业务权限角色授予校验（只读）
# 前置：执行 backend/whale-services/whale-service-auth/whale-service-auth-starter/src/main/resources/db/
#       migration-5.46/5.53/5.54/5.58~5.65 后运行；本脚本不写库。
# 用法：$env:MYSQL_PASSWORD='***'; powershell -File scripts/smoke/bw-auth-business-role-grant-smoke.ps1
# 退出码：0 全部通过；1 存在缺失/数量不符；未设置 MYSQL_PASSWORD 时跳过并返回 0。

param(
    [string]$MysqlUser = 'root',
    [string]$Database = 'bluewhale_auth',
    [string]$MysqlHost = '127.0.0.1',
    [int]$MysqlPort = 3306
)

$ErrorActionPreference = 'Stop'

if (-not $env:MYSQL_PASSWORD) {
    Write-Host 'SKIP: 未设置 MYSQL_PASSWORD，跳过数据库授权校验'
    exit 0
}
$env:MYSQL_PWD = $env:MYSQL_PASSWORD

function Invoke-Query([string]$sql) {
    # 必须给连接参数加引号：Windows PowerShell 不会在 `-P$var` 这类令牌内展开变量
    $rows = & mysql "-h$MysqlHost" "-P$MysqlPort" "-u$MysqlUser" -N -B -e $sql $Database 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL 查询失败: $rows"
    }
    return @($rows | Where-Object { $_ -ne '' })
}

function Assert($label, $cond, $detail = '') {
    if (-not $cond) {
        Write-Host "FAIL $label $detail"
        $script:failed = $true
        return
    }
    Write-Host "OK   $label $detail"
}

$script:failed = $false
$roleCodes = @(
    'archive_manager', 'archive_member', 'oa_manager', 'oa_member',
    'erp_manager', 'erp_member', 'mes_manager', 'mes_member',
    'pdm_manager', 'pdm_member', 'contract_manager', 'contract_member', 'contract_signatory'
)
$roleCodeSql = ($roleCodes | ForEach-Object { "'$_'" }) -join ','

# 1) 权限点齐全（46 项业务权限：档案 12 + OA 4 + ERP/MES/PDM/合同/印章 30）
$seedCount = [int](Invoke-Query @"
SELECT COUNT(*) FROM sys_permission
WHERE deleted = 0 AND status = 1
  AND (id BETWEEN 140 AND 151 OR id BETWEEN 168 AND 171 OR id BETWEEN 203 AND 232);
"@)
Assert '1.permissionSeeds=46' ($seedCount -eq 46) "(actual=$seedCount，若不足请先执行 5.46/5.53/5.58~5.64)"

# 2) 目标租户范围：平台租户（角色模板）+ 全部业务租户（角色按租户复制）
$tenantRows = Invoke-Query @"
SELECT tenant_code FROM sys_tenant
WHERE deleted = 0 AND status = 1 AND (id = 1 OR tenant_type = 'TENANT')
ORDER BY id;
"@
$tenantCodes = @($tenantRows | ForEach-Object { ($_ -split "`t")[0] })
Assert '2.targetTenantsNotEmpty' ($tenantCodes.Count -gt 0) "(tenants=$($tenantCodes -join ','))"

# 3) 每个目标租户的 13 个业务角色授权数量
$expected = [ordered]@{
    archive_manager     = 12
    archive_member      = 4
    oa_manager          = 4
    oa_member           = 2
    erp_manager         = 4
    erp_member          = 3
    mes_manager         = 4
    mes_member          = 3
    pdm_manager         = 4
    pdm_member          = 3
    contract_manager    = 19
    contract_member     = 9
    contract_signatory  = 3
}

$grantRows = Invoke-Query @"
SELECT t.tenant_code, r.role_code, COUNT(rp.permission_id)
FROM sys_tenant t
JOIN sys_role r ON r.tenant_id = t.id AND r.deleted = 0
LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
WHERE t.deleted = 0 AND t.status = 1 AND (t.id = 1 OR t.tenant_type = 'TENANT')
  AND r.role_code IN ($roleCodeSql)
GROUP BY t.tenant_code, r.role_code
ORDER BY t.tenant_code, r.role_code;
"@

$actual = @{}
foreach ($row in $grantRows) {
    $parts = $row -split "`t"
    if ($parts.Count -ge 3) { $actual["$($parts[0])|$($parts[1])"] = [int]$parts[2] }
}
foreach ($tenant in $tenantCodes) {
    foreach ($code in $expected.Keys) {
        $want = $expected[$code]
        $key = "$tenant|$code"
        $got = if ($actual.ContainsKey($key)) { $actual[$key] } else { -1 }
        Assert "3.$tenant.$code=$want" ($got -eq $want) "(actual=$got)"
    }
}

# 4) 每项业务权限至少被一个启用角色持有
$unassigned = Invoke-Query @"
SELECT p.id
FROM sys_permission p
LEFT JOIN sys_role_permission rp ON rp.permission_id = p.id AND rp.deleted = 0
LEFT JOIN sys_role r ON r.id = rp.role_id AND r.deleted = 0 AND r.status = 1
WHERE p.deleted = 0 AND p.status = 1
  AND (p.id BETWEEN 140 AND 151 OR p.id BETWEEN 168 AND 171 OR p.id BETWEEN 203 AND 232)
  AND r.id IS NULL
ORDER BY p.id;
"@
Assert '4.allBusinessPermissionsAssigned' ($unassigned.Count -eq 0) "(unassigned=$($unassigned -join ','))"

# 5) tenant_admin 覆盖 46 项业务权限（workflow 44 另行授予，不计入 46）
$adminRows = Invoke-Query @"
SELECT r.tenant_id, COUNT(DISTINCT rp.permission_id)
FROM sys_role r
JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.deleted = 0
JOIN sys_permission p ON p.id = rp.permission_id AND p.deleted = 0 AND p.status = 1
WHERE r.role_code = 'tenant_admin' AND r.deleted = 0 AND r.status = 1
  AND (p.id BETWEEN 140 AND 151 OR p.id BETWEEN 168 AND 171 OR p.id BETWEEN 203 AND 232)
GROUP BY r.tenant_id;
"@
foreach ($row in $adminRows) {
    $parts = $row -split "`t"
    $tenant = $parts[0]
    $count = [int]$parts[1]
    Assert "5.tenantAdmin[$tenant]=46" ($count -ge 46) "(actual=$count)"
}
if ($adminRows.Count -eq 0) {
    Write-Host 'INFO: 未发现 tenant_admin 角色（平台租户可忽略）'
}

if ($script:failed) {
    Write-Host 'RESULT: FAILED'
    exit 1
}
Write-Host 'RESULT: PASSED'
