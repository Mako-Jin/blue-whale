# ============================================================
# 鲸视频（whale-video）备份 / 恢复演练脚本（Wave 5.77 M5-8）
#
# 用法：
#   $env:MYSQL_PASSWORD='***'
#   powershell -File scripts/ops/video-backup-restore.ps1
#   powershell -File scripts/ops/video-backup-restore.ps1 -Database bluewhale_video -KeepRestoreDatabase
#
# 说明：
#   - 使用 mysqldump --single-transaction 生成一致性快照（InnoDB）。
#   - 恢复到独立的临时库（默认 {Database}_restore_check），校验表数量、
#     观看历史清理索引（V4）、版权下架关键列、片单关系表与核心表行数后删除临时库。
#   - 禁止把临时库名设置为源库名，避免误覆盖生产数据。
#   - MySQL 客户端目录可用 MYSQL_BIN_DIR 覆盖（默认 D:\mysql8\bin）。
#   - 退出码：0 通过；1 失败。
# ============================================================
param(
    [string]$Database = 'bluewhale_video',
    [string]$VerifyDatabase = '',
    [string]$BackupDir = 'logs/backup',
    [string]$MysqlBin = $(if ($env:MYSQL_BIN_DIR) { $env:MYSQL_BIN_DIR } else { 'D:\mysql8\bin' }),
    [switch]$KeepRestoreDatabase
)

$ErrorActionPreference = 'Stop'
$mysql = Join-Path $MysqlBin 'mysql.exe'
$dump = Join-Path $MysqlBin 'mysqldump.exe'
foreach ($tool in @($mysql, $dump)) {
    if (-not (Test-Path $tool)) { throw "未找到 MySQL 客户端：$tool（可用 MYSQL_BIN_DIR 指定目录）" }
}
if (-not $env:MYSQL_PASSWORD) { throw '未设置 MYSQL_PASSWORD，无法执行备份/恢复演练' }
$dbUser = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { 'root' }

if (-not $VerifyDatabase) { $VerifyDatabase = "${Database}_restore_check" }
if ($VerifyDatabase -eq $Database) { throw '恢复校验库不能与源库同名' }

# 视频库核心表（19 张 = V1 基线 16 + V2 vd_series + V3 vd_collection / vd_collection_item）
$coreTables = @(
    'vd_content', 'vd_short_video', 'vd_video', 'vd_movie', 'vd_category', 'vd_tag',
    'vd_content_tag', 'vd_creator', 'vd_upload_declaration', 'vd_like', 'vd_favorite',
    'vd_watch_history', 'vd_comment', 'vd_comment_like', 'vd_series', 'vd_collection',
    'vd_collection_item', 'audit_log', 'api_log'
)

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
    $argv = @('--host=127.0.0.1', '--port=3306', "--user=$dbUser", '--default-character-set=utf8mb4', '-N', '-B')
    if ($db) { $argv += @('-D', $db) }
    $argv += @('-e', $sql)
    $out = & $mysql @argv 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL 执行失败：$sql`n$out" }
    return ($out | Out-String).Trim()
}

try {
    Write-Host "[1/5] 备份 $Database -> $dumpFile"
    & $dump --host=127.0.0.1 --port=3306 "--user=$dbUser" --single-transaction --no-tablespaces `
        --default-character-set=utf8mb4 --set-gtid-purged=OFF --skip-add-drop-table -r $dumpFile $Database 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'mysqldump 执行失败' }
    if (-not (Test-Path $dumpFile) -or (Get-Item $dumpFile).Length -eq 0) { throw '备份文件为空' }

    Write-Host "[2/5] 创建校验库 $VerifyDatabase"
    Invoke-Mysql $null "DROP DATABASE IF EXISTS ``$VerifyDatabase``; CREATE DATABASE ``$VerifyDatabase`` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" | Out-Null

    Write-Host "[3/5] 恢复备份到 $VerifyDatabase"
    $restoreArgs = @('--host=127.0.0.1', '--port=3306', "--user=$dbUser", '--default-character-set=utf8mb4', '-D', $VerifyDatabase)
    Get-Content -LiteralPath $dumpFile -Raw | & $mysql @restoreArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw '恢复导入失败' }

    Write-Host '[4/5] 校验结构与数据'
    $inList = ($coreTables | ForEach-Object { "'$_'" }) -join ','
    $tableCount = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$VerifyDatabase' AND table_name IN ($inList);"
    if ([int]$tableCount -ne $coreTables.Count) {
        throw "核心表数量不符：期望 $($coreTables.Count)，实际 $tableCount"
    }
    # M5-7 观看历史保留期清理依赖 (tenant_id, watched_at) 复合索引（V4）
    $historyIndex = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$VerifyDatabase' AND table_name='vd_watch_history' AND index_name='idx_vd_history_watched';"
    if ([int]$historyIndex -lt 1) { throw '恢复库缺少 idx_vd_history_watched 索引（观看历史清理将退化为全表扫描）' }
    # M5-3 版权下架留痕关键列：copyright_status / takedown_reason / takedown_at
    $contentColumns = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$VerifyDatabase' AND table_name='vd_content' AND column_name IN ('copyright_status','takedown_reason','takedown_at');"
    if ([int]$contentColumns -ne 3) { throw '恢复库缺少 vd_content 的 copyright_status / takedown_reason / takedown_at 列' }
    # M4 片单条目唯一约束依赖 collection_item 的 content_id 列
    $itemColumns = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$VerifyDatabase' AND table_name='vd_collection_item' AND column_name IN ('collection_id','content_id','sort_no');"
    if ([int]$itemColumns -ne 3) { throw '恢复库缺少 vd_collection_item 的 collection_id / content_id / sort_no 列' }

    $rowChecks = @(
        @{ table = 'vd_content'; label = '内容' },
        @{ table = 'vd_comment'; label = '评论' },
        @{ table = 'vd_watch_history'; label = '观看历史' },
        @{ table = 'vd_collection_item'; label = '片单条目' }
    )
    foreach ($item in $rowChecks) {
        $sourceRows = Invoke-Mysql $Database "SELECT COUNT(*) FROM $($item.table);"
        $restoredRows = Invoke-Mysql $VerifyDatabase "SELECT COUNT(*) FROM $($item.table);"
        if ([int]$sourceRows -ne [int]$restoredRows) {
            throw "$($item.label)行数不一致（$($item.table)）：源 $sourceRows / 恢复 $restoredRows"
        }
    }

    $contentRows = Invoke-Mysql $VerifyDatabase 'SELECT COUNT(*) FROM vd_content;'
    Write-Host "[5/5] 校验通过：表 $($coreTables.Count)、idx_vd_history_watched 1、版权列 3、片单列 3、vd_content $contentRows 行"
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
