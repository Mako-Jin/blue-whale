﻿# ============================================================
# 鲸音乐（whale-music）备份 / 恢复演练脚本（Wave 5.75 M5-7）
#
# 用法：
#   $env:MYSQL_PASSWORD='***'
#   powershell -File scripts/ops/music-backup-restore.ps1
#   powershell -File scripts/ops/music-backup-restore.ps1 -Database bluewhale_music -KeepRestoreDatabase
#
# 说明：
#   - 使用 mysqldump --single-transaction 生成一致性快照（InnoDB）。
#   - 恢复到独立的临时库（默认 {Database}_restore_check），校验表数量、
#     近 7 天热榜索引、申诉表结构、核心表行数后删除临时库。
#   - 禁止把临时库名设置为源库名，避免误覆盖生产数据。
#   - MySQL 客户端目录可用 MYSQL_BIN_DIR 覆盖（默认 D:\mysql8\bin）。
#   - 退出码：0 通过；1 失败。
# ============================================================
param(
    [string]$Database = 'bluewhale_music',
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

# 音乐库核心表（14 张，含 M5 新增 mu_song_appeal）
$coreTables = @(
    'mu_artist', 'mu_album', 'mu_song', 'mu_song_tag', 'mu_tag', 'mu_playlist', 'mu_playlist_song',
    'mu_song_comment', 'mu_song_like', 'mu_play_history', 'mu_copyright_agreement',
    'mu_song_appeal', 'audit_log', 'api_log'
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
    # M5 近 7 天热榜依赖 (tenant_id, played_at, song_id) 复合索引
    $hotIndex = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema='$VerifyDatabase' AND table_name='mu_play_history' AND index_name='idx_mu_history_played';"
    if ([int]$hotIndex -lt 1) { throw '恢复库缺少 idx_mu_history_played 索引（近 7 天热榜将退化为全表扫描）' }
    # M5-3 申诉表关键列：status / review_opinion / reviewer_id
    $appealColumns = Invoke-Mysql $null "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='$VerifyDatabase' AND table_name='mu_song_appeal' AND column_name IN ('status','review_opinion','reviewer_id');"
    if ([int]$appealColumns -ne 3) { throw '恢复库缺少 mu_song_appeal 的 status / review_opinion / reviewer_id 列' }

    $rowChecks = @(
        @{ table = 'mu_song'; label = '歌曲' },
        @{ table = 'mu_song_appeal'; label = '申诉' },
        @{ table = 'mu_playlist'; label = '歌单' },
        @{ table = 'mu_play_history'; label = '播放历史' }
    )
    foreach ($item in $rowChecks) {
        $sourceRows = Invoke-Mysql $Database "SELECT COUNT(*) FROM $($item.table);"
        $restoredRows = Invoke-Mysql $VerifyDatabase "SELECT COUNT(*) FROM $($item.table);"
        if ([int]$sourceRows -ne [int]$restoredRows) {
            throw "$($item.label)行数不一致（$($item.table)）：源 $sourceRows / 恢复 $restoredRows"
        }
    }

    $songRows = Invoke-Mysql $VerifyDatabase 'SELECT COUNT(*) FROM mu_song;'
    Write-Host "[5/5] 校验通过：表 $($coreTables.Count)、idx_mu_history_played 1、申诉列 3、mu_song $songRows 行"
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
