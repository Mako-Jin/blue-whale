# 压测公共工具（M1 压测基线）
#
# 口径统一：
#   - 延迟只统计单次 HTTP 往返（Stopwatch 包裹 Invoke-SmokeApi），不含脚本启动与登录；
#   - 分位数用 nearest-rank（P95 = 第 ceil(0.95*N) 个样本），与接口监控口径一致；
#   - 失败请求计入 errors，延迟样本只保留成功请求，避免超时把 P95 拉偏。

<#
.SYNOPSIS
执行 N 次调用并统计延迟分位数。
.PARAMETER Label
样本标签
.PARAMETER Call
单次调用脚本块（必须返回业务码为 0 的 Result 信封）
.PARAMETER Iterations
调用次数
.OUTPUTS
Hashtable：@{ Label; Count; Errors; P50; P95; P99; Min; Max; Mean }
#>
function Invoke-PerfLoop {
    param(
        [string]$Label,
        [scriptblock]$Call,
        [int]$Iterations = 50
    )

    $samples = New-Object System.Collections.Generic.List[double]
    $errors = 0
    for ($i = 0; $i -lt $Iterations; $i++) {
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            $result = & $Call
            $watch.Stop()
            if ($result -and $result.code -eq 0) {
                $samples.Add($watch.Elapsed.TotalMilliseconds)
            } else {
                $errors++
            }
        } catch {
            $watch.Stop()
            $errors++
        }
    }
    return New-PerfStat -Label $Label -Samples $samples -Errors $errors
}

<#
.SYNOPSIS
由延迟样本生成统计行。
#>
function New-PerfStat {
    param(
        [string]$Label,
        [System.Collections.Generic.List[double]]$Samples,
        [int]$Errors = 0
    )

    $sorted = @($Samples | Sort-Object)
    $count = $sorted.Count
    $stat = @{
        Label  = $Label
        Count  = $count
        Errors = $Errors
        P50    = 0
        P95    = 0
        P99    = 0
        Min    = 0
        Max    = 0
        Mean   = 0
    }
    if ($count -eq 0) {
        return $stat
    }
    $stat.Min = $sorted[0]
    $stat.Max = $sorted[$count - 1]
    $stat.Mean = ($sorted | Measure-Object -Average).Average
    $stat.P50 = $sorted[[Math]::Max(0, [Math]::Ceiling(0.50 * $count) - 1)]
    $stat.P95 = $sorted[[Math]::Max(0, [Math]::Ceiling(0.95 * $count) - 1)]
    $stat.P99 = $sorted[[Math]::Max(0, [Math]::Ceiling(0.99 * $count) - 1)]
    return $stat
}

<#
.SYNOPSIS
以 Markdown 表格行输出一条统计结果（可直接粘进 docs/ops）。
#>
function Write-PerfRow {
    param([hashtable]$Stat)

    $p50 = '{0:N1}' -f $Stat.P50
    $p95 = '{0:N1}' -f $Stat.P95
    $p99 = '{0:N1}' -f $Stat.P99
    $mean = '{0:N1}' -f $Stat.Mean
    Write-Host ("| {0} | {1} | {2} | {3} | {4} | {5} | {6} |" -f `
        $Stat.Label, $Stat.Count, $Stat.Errors, $p50, $p95, $p99, $mean)
}
