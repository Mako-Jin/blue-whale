# ============================================================
# 蓝鲸在线文档（whale-doc）备份 / 恢复演练脚本（M5）
#
# 用法：
#   $env:MYSQL_PASSWORD='***'
#   powershell -File scripts/ops/doc-backup-restore.ps1
#   powershell -File scripts/ops/doc-backup-restore.ps1 -Database bluewhale_doc -KeepRestoreDatabase
#
# 说明：
#   - 使用 mysqldump --single-transaction 生成一致性快照（InnoDB）。
#   - 恢复到独立的临时库（默认 {Database}_restore_check），校验表数量、全文索引与页面行数后删除临时库。
#   - 禁止把临时库名设置为源库名，避免误覆盖生产数据。
#   - 退出码：0 通过；1 失败。
# ============================================================
param(
    [string]$Database = 'bluewhale_doc',
    [string]$VerifyDatabase = '',
    [string]$BackupDir = 'logs/backup',
    [string]$MysqlBin = $(if ($env:MYSQL_BIN_DIR) { $env:MYSQL_BIN_DIR } else { 'D:\tools\installer\mysql\bin' }),
    [switch]$KeepRestoreDatabase
)

$ErrorActionPreference = 'Stop'
$mysql = Join-Path $MysqlBin 'mysql.exe'
$dump = Join-Path $MysqlBin 'mysqldump.exe'
foreach ($tool in @($mysql, $dump)) {
    if (-not (Test-Path $tool)) { throw "未找到 MySQL 客户端：$tool" }
}
if (-not $env:MYSQL_PASSWORD) { throw '未设置 MYSQL_PASSWORD，无法执行备份/恢复演练' }

if (-not $VerifyDatabase) { $VerifyDatabase = "${Database}_restore_check" }
if ($VerifyDatabase -eq $Database) { throw '恢复校验库不能与源库同名' }

$backupRoot = $null
$resolved = Resolve-Path -LiteralPath (Join-Path (Get-Location) $BackupDir) -ErrorAction SilentlyContinue
if ($resolved) {
    $backupRoot = [string]$resolved
} else {
    $backupRoot = [string](New-Item -ItemType Directory -Force -Path (Join-Path (Get-Location) $BackupDir))
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$dumpFile = Join-Path $backupRoot "$Database-$stamp.sql"

$oldPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $env:MYSQL_PASSWORD

function Invoke-Mysql([string]$db, [string]$sql) {
    $argv = @('--host=127.0.0.1', '--port=3306', '--user=root', '--default-character-set=utf8mb4', '-N', '-B')
    if ($db) { $argv += @('-D', $db) }
    $argv += @('-e', $sql)
    $out = & $mysql @argv 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL 执行失败：$sql`n$out" }
    return ($out | Out-String).Trim()
}

try {
    Write-Host "[1/5] 备份 $Database -> $dumpFile"
    & $dump --host=127.0.0.1 --port=3306 --user=root --single-transaction --no-tablespaces --default-character-set=utf8mb4 `
        --set-gtid-purged=OFF --skip-add-drop-table -r $dumpFile $Database 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump 执行失败' }
    if (-not (Test-Path $dumpFile) -or (Get-Item $dumpFile).Length -eq 0) { throw '备份文件为空' }

    Write-Host "[2/5] 创建校验库 $VerifyDatabase"
    Invoke-Mysql $null "DROP DATABASE IF EXISTS ``$VerifyDatabase``; CREATE DATABASE ``$VerifyDatabase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null

    Write-Host "[3/5] 恢复备份到 $VerifyDatabase"
    $restoreArgs = @('--host=127.0.0.1', '--port=3306', '--user=root', '--default-character-set=utf8mb4', '-D', $VerifyDatabase)
    Get-Content -LiteralPath $dumpFile -Raw | & $mysql @restoreArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '恢复导入失败' }

    Write-Host '[4/5] 校验结构与数据'
    $tableCount = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$VerifyDatabase' AND table_name IN ('doc_space','doc_page','doc_page_version','doc_page_attachment','doc_comment','doc_page_acl','doc_tag','doc_page_tag','doc_page_link','doc_page_activity','doc_page_recent','doc_comment_read','doc_template','doc_page_share');"
    if ([int]$tableCount -ne 14) { throw "核心表数量不符：期望 14，实际 $tableCount" }
    $fulltext = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$VerifyDatabase' AND table_name='doc_page' AND index_name='ft_doc_page_text';"
    if ([int]$fulltext -lt 1) { throw '恢复库缺少 ft_doc_page_text 全文索引' }
    $restrictedColumn = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$VerifyDatabase' AND table_name='doc_page' AND column_name='is_restricted';"
    if ([int]$restrictedColumn -ne 1) { throw '恢复库缺少 doc_page.is_restricted 列' }
    $sourcePages = Invoke-Mysql $Database 'SELECT COUNT(*) FROM doc_page;'
    $restoredPages = Invoke-Mysql $VerifyDatabase 'SELECT COUNT(*) FROM doc_page;'
    if ([int]$sourcePages -ne [int]$restoredPages) { throw "页面行数不一致：源 $sourcePages / 恢复 $restoredPages" }

    Write-Host "[5/5] 校验通过：表 14、全文索引 1、is_restricted 1、doc_page $restoredPages 行"
    if (-not $KeepRestoreDatabase) {
        Invoke-Mysql $null "DROP DATABASE IF EXISTS ``$VerifyDatabase``;" | Out-Null
        Write-Host "已删除校验库 $VerifyDatabase；备份文件保留：$dumpFile"
    } else {
        Write-Host "已保留校验库 $VerifyDatabase"
    }
    Write-Host 'RESULT: PASSED'
    exit 0
} catch {
    Write-Host "RESULT: FAILED - $($_.Exception.Message)"
    if (-not $KeepRestoreDatabase) {
        try { Invoke-Mysql $null "DROP DATABASE IF EXISTS ``$VerifyDatabase``;" | Out-Null } catch { }
    }
    exit 1
} finally {
    if ($null -eq $oldPwd) { Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue } else { $env:MYSQL_PWD = $oldPwd }
}
