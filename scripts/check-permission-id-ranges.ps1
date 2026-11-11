# 权限 / 菜单号段占用扫描：检测 sys_permission / sys_menu 的真实撞号。
# 判定口径：
#   - 同一 table + ID 被不同权限码占用 => 真实冲突（重复登记同一权限码视为幂等副本，不报）。
#   - sys_permission 中同一权限码映射到不同 ID => 真实冲突。
# 用法：powershell -ExecutionPolicy Bypass -File scripts/check-permission-id-ranges.ps1 [-RepoRoot <仓库根>]
# 退出码：0 = 无真实撞号；1 = 存在真实撞号。

param(
    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
)

$ErrorActionPreference = 'Stop'

$insertRe = '^\s*INSERT\s+INTO\s+(sys_permission|sys_menu)\s*\('
$selectLineRe = '^\s*SELECT\s+(\d+)\s*,'
$valuesLineRe = '^\s*\(\s*(\d+)\s*,'
$sameLineSelectRe = 'SELECT\s+(\d+)\s*,'
$sameLineValuesRe = 'VALUES\s*\(\s*(\d+)\s*,'
$permCodeRe = "'(sys:[A-Za-z0-9:._*-]+)'"

$records = @()
$sqlFiles = Get-ChildItem -Path (Join-Path $RepoRoot 'backend') -Filter '*.sql' -Recurse |
    Where-Object { $_.FullName -notmatch '\\target\\' }

foreach ($file in $sqlFiles) {
    $relative = $file.FullName.Substring($RepoRoot.Length).TrimStart('\', '/')
    $lines = Get-Content -LiteralPath $file.FullName -Encoding UTF8
    $table = $null
    $ids = @()
    $codes = @()

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $lineNo = $i + 1

        if ($line -match $insertRe) {
            $table = $Matches[1]
            $ids = @()
            $codes = @()
            if ($line -match $sameLineSelectRe) { $ids += [pscustomobject]@{ Id = [int]$Matches[1]; Line = $lineNo } }
            elseif ($line -match $sameLineValuesRe) { $ids += [pscustomobject]@{ Id = [int]$Matches[1]; Line = $lineNo } }
            if ($line -match $permCodeRe) { $codes += [pscustomobject]@{ Code = $Matches[1]; Line = $lineNo } }
            continue
        }

        if ($null -eq $table) { continue }

        if ($line -match $selectLineRe -or $line -match $valuesLineRe) {
            $ids += [pscustomobject]@{ Id = [int]$Matches[1]; Line = $lineNo }
        }
        if ($line -match $permCodeRe) { $codes += [pscustomobject]@{ Code = $Matches[1]; Line = $lineNo } }

        if ($line.TrimEnd().EndsWith(';')) {
            foreach ($id in $ids) {
                $code = $null
                $after = $codes | Where-Object { $_.Line -ge $id.Line } | Sort-Object Line | Select-Object -First 1
                if ($after) {
                    $code = $after.Code
                } else {
                    $before = $codes | Where-Object { $_.Line -lt $id.Line } | Sort-Object Line -Descending | Select-Object -First 1
                    if ($before) { $code = $before.Code }
                }
                $records += [pscustomobject]@{ Table = $table; Id = $id.Id; Code = $code; File = $relative; Line = $id.Line }
            }
            $table = $null
            $ids = @()
            $codes = @()
        }
    }
}

$permCount = ($records | Where-Object { $_.Table -eq 'sys_permission' }).Count
$menuCount = ($records | Where-Object { $_.Table -eq 'sys_menu' }).Count

$idGroups = $records | Group-Object Table, Id
$conflicts = @()
foreach ($group in $idGroups) {
    $parts = $group.Name -split ', '
    $distinctCodes = @($group.Group | Where-Object { $null -ne $_.Code } | Select-Object -ExpandProperty Code -Unique)
    if ($distinctCodes.Count -gt 1) {
        $conflicts += [pscustomobject]@{ Kind = 'ID'; Table = $parts[0]; Value = $parts[1]; Codes = $distinctCodes; Records = $group.Group }
    }
}

$codeGroups = $records | Where-Object { $null -ne $_.Code -and $_.Table -eq 'sys_permission' } | Group-Object Table, Code
foreach ($group in $codeGroups) {
    $distinctIds = @($group.Group | Select-Object -ExpandProperty Id -Unique)
    if ($distinctIds.Count -gt 1) {
        $parts = $group.Name -split ', '
        $conflicts += [pscustomobject]@{ Kind = 'CODE'; Table = $parts[0]; Value = $parts[1]; Ids = $distinctIds; Records = $group.Group }
    }
}

$baselinePath = Join-Path $PSScriptRoot 'permission-id-baseline.txt'
$baseline = @{}
if (Test-Path -LiteralPath $baselinePath) {
    foreach ($line in Get-Content -LiteralPath $baselinePath -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -and -not $trimmed.StartsWith('#')) { $baseline[$trimmed] = $true }
    }
}

$newConflicts = @()
$knownCount = 0
foreach ($conflict in $conflicts) {
    $key = if ($conflict.Kind -eq 'ID') { "id:$($conflict.Table):$($conflict.Value)" } else { "code:$($conflict.Table):$($conflict.Value)" }
    if ($baseline.ContainsKey($key)) {
        $knownCount++
    } else {
        $newConflicts += $conflict
    }
}

Write-Host ("扫描 SQL 文件：{0}；sys_permission ID {1} 条，sys_menu ID {2} 条；基线已知撞号 {3} 处" -f `
        $sqlFiles.Count, $permCount, $menuCount, $knownCount)
$records | Group-Object Table | ForEach-Object {
    $max = ($_.Group | Measure-Object -Property Id -Maximum).Maximum
    Write-Host ("  {0} 最大 ID：{1}" -f $_.Name, $max)
}

foreach ($conflict in $newConflicts) {
    if ($conflict.Kind -eq 'ID') {
        Write-Host ("撞号：[{0}] ID {1} 被不同权限码占用（{2}）" -f $conflict.Table, $conflict.Value, ($conflict.Codes -join ' / ')) -ForegroundColor Red
    } else {
        Write-Host ("撞号：[{0}] 权限码 {1} 映射到多个 ID（{2}）" -f $conflict.Table, $conflict.Value, ($conflict.Ids -join ' / ')) -ForegroundColor Red
    }
    foreach ($record in $conflict.Records) {
        Write-Host ("    {0}:{1}" -f $record.File, $record.Line)
    }
}

if ($newConflicts.Count -gt 0) {
    Write-Host ("PERMISSION-ID-CHECK-FAIL（新增撞号 {0} 处）" -f $newConflicts.Count)
    exit 1
}

Write-Host 'PERMISSION-ID-CHECK-PASS'
exit 0
