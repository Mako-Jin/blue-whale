# ============================================================
# M1 压测基线 3/3：并发报工
#
# 口径：对同一个已派工的 MES 任务，按 1 / 5 / 20 三档并发持续调用
#       POST /mes/execution/report（每档 20 次/并发），统计单次 HTTP 往返 P50/P95/P99
#       与失败数。任务需满足：已派工且累计可报数量 ≥ 并发数 × 每并发次数 × 单次报工数量。
#
# 用法：
#   pwsh -File scripts/perf/report-concurrent.ps1 -TaskId 123 -QuantityPerReport 1
#   （先用 bw-chain-2 或页面准备一张已派工任务，再取任务 ID）
# 前置：gateway + whale-mes 在线，SMOKE_PASSWORD 提供管理员口令。
# 输出：Markdown 表格行，粘进 docs/ops/m1-perf-baseline.md。
# ============================================================

param(
    [string]$BaseUri = $(if ($env:CHAIN_SMOKE_BASE) { $env:CHAIN_SMOKE_BASE } else { 'http://127.0.0.1:8080' }),
    [long]$TaskId = 0,
    [decimal]$QuantityPerReport = 1,
    [int[]]$ConcurrencyLevels = @(1, 5, 20),
    [int]$RunsPerWorker = 20
)

. "$PSScriptRoot/lib/perf.ps1"
. "$PSScriptRoot/../smoke/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$script:gateway = $BaseUri.TrimEnd('/')

if ($TaskId -le 0) {
    Write-Host 'SKIP 未提供 -TaskId。请先准备一张已派工的 MES 任务（可用 bw-chain-2 生成），再传入任务 ID。'
    exit 0
}
$required = ($ConcurrencyLevels | Measure-Object -Maximum).Maximum * $RunsPerWorker * $QuantityPerReport
Write-Host "任务 $TaskId 需要累计可报数量不少于 $required（超报会按失败计入 errors）"

$cred = Get-SmokeAdminCredential
$login = Invoke-SmokeApi -Method Post -Path '/auth/login' -Body @{
    username = $cred.Username
    password = $cred.Password
}
if ($login.code -ne 0 -or $null -eq $login.data.accessToken) {
    throw "登录失败：code=$($login.code) message=$($login.message)"
}
$script:token = $login.data.accessToken
$script:h = @{ Authorization = "Bearer $($script:token)" }

Write-Host "| 场景 | 成功样本 | 失败 | P50(ms) | P95(ms) | P99(ms) | 均值(ms) |"
Write-Host "| --- | --- | --- | --- | --- | --- | --- |"

foreach ($level in $ConcurrencyLevels) {
    $jobs = @()
    for ($worker = 1; $worker -le $level; $worker++) {
        $jobs += Start-Job -ArgumentList @(
            $script:gateway, $script:token, $TaskId, $RunsPerWorker, $QuantityPerReport, $worker
        ) -ScriptBlock {
            param($baseUri, $token, $taskId, $runs, $qty, $worker)
            $samples = New-Object System.Collections.Generic.List[double]
            $errors = 0
            $headers = @{ Authorization = "Bearer $token" }
            for ($i = 0; $i -lt $runs; $i++) {
                $body = @{
                    taskId = $taskId
                    reportQuantity = $qty
                    qualifiedQuantity = $qty
                    defectQuantity = 0
                    reporter = "perf-$worker"
                } | ConvertTo-Json -Compress
                $watch = [System.Diagnostics.Stopwatch]::StartNew()
                try {
                    $result = Invoke-RestMethod -Method Post `
                        -Uri "$baseUri/mes/execution/report" `
                        -Headers $headers `
                        -ContentType 'application/json;charset=utf-8' `
                        -Body ([System.Text.Encoding]::UTF8.GetBytes($body))
                    $watch.Stop()
                    if ($result.code -eq 0) {
                        $samples.Add($watch.Elapsed.TotalMilliseconds)
                    } else {
                        $errors++
                    }
                } catch {
                    $watch.Stop()
                    $errors++
                }
            }
            [pscustomobject]@{ Samples = $samples; Errors = $errors }
        }
    }
    $outputs = @($jobs | Wait-Job | Receive-Job)
    $jobs | Remove-Job

    $allSamples = New-Object System.Collections.Generic.List[double]
    $errors = 0
    foreach ($output in $outputs) {
        foreach ($sample in $output.Samples) {
            $allSamples.Add([double]$sample)
        }
        $errors += [int]$output.Errors
    }
    $stat = New-PerfStat -Label "并发报工 concurrency=$level" -Samples $allSamples -Errors $errors
    Write-PerfRow -Stat $stat
}
