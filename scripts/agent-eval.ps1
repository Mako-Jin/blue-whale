# =====================================================================
# BlueWhale 知识 Agent 黄金集评测脚本（Wave 5.47 轨 E）
# =====================================================================
# 用法：
#   pwsh -File scripts/agent-eval.ps1
#   pwsh -File scripts/agent-eval.ps1 -BaseUrl http://localhost:8080 -Username admin -Password 123456
#
# 流程：登录 → 逐题 POST /agent/chat（SSE）→ 解析 meta/delta/done → 按黄金集断言
#       → 计算总体/结构化/引用有效率 → 写 docs/reviews/ 下的 Markdown 报告。
# 阈值：总体 ≥80%、结构化（structured+schema）≥95%、引用有效率 100%，任一不达标 exit 1。
# 依赖：PowerShell 5.1+ / .NET HttpClient（无需额外模块）。
# =====================================================================

param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'admin',
    [string]$Password = '123456',
    [string]$GoldenSet = 'docs/agent/golden-set.json',
    [string]$OutFile = 'docs/reviews/2026-10-22-wave5.47-agent-eval.md',
    [int]$TimeoutSec = 120
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# 仓库根目录（scripts 的上级）
$RepoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($GoldenSet)) { $GoldenSet = Join-Path $RepoRoot $GoldenSet }
if (-not [System.IO.Path]::IsPathRooted($OutFile)) { $OutFile = Join-Path $RepoRoot $OutFile }

if (-not (Test-Path $GoldenSet)) { throw "黄金集不存在：$GoldenSet" }
$suite = Get-Content -Path $GoldenSet -Raw -Encoding UTF8 | ConvertFrom-Json
$cases = $suite.cases
$thresholds = $suite.thresholds

Write-Host "== BlueWhale Agent 黄金集评测 ==" -ForegroundColor Cyan
Write-Host "BaseUrl   : $BaseUrl"
Write-Host "黄金集    : $GoldenSet（$($cases.Count) 题）"

# ---------------------------------------------------------------------
# 1. 登录
# ---------------------------------------------------------------------
$loginJson = @{ username = $Username; password = $Password } | ConvertTo-Json -Compress
try {
    $login = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post `
        -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($loginJson)) -TimeoutSec 30
} catch {
    throw "登录失败：$($_.Exception.Message)"
}
if (-not $login.data.accessToken) { throw "登录响应缺少 accessToken" }
$token = $login.data.accessToken
Write-Host "登录成功：$($login.data.username)（roles=$($login.data.roles -join ','))" -ForegroundColor Green

# ---------------------------------------------------------------------
# 2. 索引状态（报告参考信息）
# ---------------------------------------------------------------------
$indexStatus = $null
try {
    $statusResp = Invoke-RestMethod -Uri "$BaseUrl/agent/index/status" -Method Get `
        -Headers @{ Authorization = "Bearer $token" } -TimeoutSec 30
    $indexStatus = $statusResp.data
} catch {
    Write-Warning "索引状态查询失败：$($_.Exception.Message)"
}

# ---------------------------------------------------------------------
# 3. SSE 调用
# ---------------------------------------------------------------------
$handler = New-Object System.Net.Http.HttpClientHandler
$client = New-Object System.Net.Http.HttpClient($handler)
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSec)

function Invoke-AgentChat {
    param([string]$Question)

    $payload = @{ question = $Question; stream = $true } | ConvertTo-Json -Compress
    $request = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Post, "$BaseUrl/agent/chat")
    $request.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
    $request.Headers.Accept.Add((New-Object System.Net.Http.Headers.MediaTypeWithQualityHeaderValue('text/event-stream')))
    $request.Content = New-Object System.Net.Http.StringContent(
        $payload, [System.Text.Encoding]::UTF8, 'application/json')

    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
    if (-not $response.IsSuccessStatusCode) {
        $code = [int]$response.StatusCode
        $body = $response.Content.ReadAsStringAsync().Result
        throw "HTTP $code：$body"
    }

    $stream = $response.Content.ReadAsStreamAsync().Result
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    $builder = New-Object System.Text.StringBuilder
    $meta = $null
    $done = $null
    $errorMessage = $null
    $eventName = ''
    $dataLines = @()

    try {
        while ($null -ne ($line = $reader.ReadLine())) {
            if ($line -eq '') {
                if ($dataLines.Count -gt 0) {
                    $raw = ($dataLines -join "`n")
                    $obj = $null
                    try { $obj = $raw | ConvertFrom-Json } catch { }
                    switch ($eventName) {
                        'meta' { if ($null -ne $obj) { $meta = $obj } }
                        'delta' { if ($null -ne $obj -and $null -ne $obj.text) { [void]$builder.Append($obj.text) } }
                        'done' { if ($null -ne $obj) { $done = $obj } }
                        'error' {
                            if ($null -ne $obj) { $errorMessage = [string]$obj.message }
                        }
                    }
                }
                $eventName = ''
                $dataLines = @()
            } elseif ($line.StartsWith('event:')) {
                $eventName = $line.Substring(6).Trim()
            } elseif ($line.StartsWith('data:')) {
                $dataLines += $line.Substring(5).TrimStart()
            }
        }
    } finally {
        $reader.Dispose()
        $stream.Dispose()
        $response.Dispose()
        $request.Dispose()
    }
    $watch.Stop()

    return [pscustomobject]@{
        Answer       = $builder.ToString()
        Meta         = $meta
        Done         = $done
        ErrorMessage = $errorMessage
        ElapsedMs    = $watch.ElapsedMilliseconds
    }
}

# ---------------------------------------------------------------------
# 4. 断言
# ---------------------------------------------------------------------
function Test-CitationPath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { return $false }
    # git / runtime 为虚拟来源（与 CitationValidator 约定一致），不做落盘校验
    if ($Path.StartsWith('git:') -or $Path.StartsWith('runtime:')) { return $true }
    $candidate = Join-Path $RepoRoot ($Path -replace '/', [System.IO.Path]::DirectorySeparatorChar)
    return (Test-Path -LiteralPath $candidate)
}

function Evaluate-Case {
    param($Case, $Result)

    $failures = New-Object System.Collections.Generic.List[string]
    $citations = @()
    if ($null -ne $Result.Done -and $null -ne $Result.Done.citations) { $citations = @($Result.Done.citations) }
    elseif ($null -ne $Result.Meta -and $null -ne $Result.Meta.citations) { $citations = @($Result.Meta.citations) }

    $answerLower = ([string]$Result.Answer).ToLowerInvariant()
    $expect = $Case.expect

    if ($null -ne $Result.ErrorMessage) {
        $failures.Add("流式错误：$($Result.ErrorMessage)")
    }
    if ([string]::IsNullOrWhiteSpace($Result.Answer)) {
        $failures.Add('回答为空')
    }
    if ($null -ne $expect.intent -and $null -ne $Result.Meta) {
        if ([string]$Result.Meta.intent -ne [string]$expect.intent) {
            $failures.Add("意图不符：期望 $($expect.intent)，实际 $($Result.Meta.intent)")
        }
    }

    if ($null -ne $expect.minCitations -and $citations.Count -lt [int]$expect.minCitations) {
        $failures.Add("引用不足：期望 ≥$($expect.minCitations)，实际 $($citations.Count)")
    }

    foreach ($factExpect in @($expect.facts)) {
        if ($null -eq $factExpect) { continue }
        $hit = $false
        foreach ($citation in $citations) {
            if ([string]$citation.kind -ne 'fact') { continue }
            if (-not ([string]$citation.factType).Equals([string]$factExpect.type, [System.StringComparison]::OrdinalIgnoreCase)) { continue }
            if (-not ([string]$citation.factKey).ToLowerInvariant().Contains(([string]$factExpect.key).ToLowerInvariant())) { continue }
            $haystack = (([string]$citation.snippet) + " " + ([string]$citation.factKey) + " " + ([string]$citation.title)).ToLowerInvariant()
            if ($haystack.Contains(([string]$factExpect.contains).ToLowerInvariant())) { $hit = $true; break }
        }
        if (-not $hit) { $failures.Add("事实未命中：$($factExpect.type) $($factExpect.key) 包含 '$($factExpect.contains)'") }
    }

    if ($null -ne $expect.keywords) {
        $keywordHit = $false
        foreach ($keyword in @($expect.keywords)) {
            if ($answerLower.Contains(([string]$keyword).ToLowerInvariant())) { $keywordHit = $true; break }
        }
        if (-not $keywordHit) { $failures.Add("关键词未命中：$((@($expect.keywords)) -join ' / ')") }
    }

    if ($null -ne $expect.pathsAny) {
        $pathHit = $false
        foreach ($citation in $citations) {
            foreach ($pattern in @($expect.pathsAny)) {
                if (([string]$citation.path).ToLowerInvariant().Contains(([string]$pattern).ToLowerInvariant())) { $pathHit = $true; break }
            }
            if ($pathHit) { break }
        }
        if (-not $pathHit) { $failures.Add("证据路径未命中：$((@($expect.pathsAny)) -join ' / ')") }
    }

    $invalidPaths = @()
    foreach ($citation in $citations) {
        if (-not (Test-CitationPath ([string]$citation.path))) { $invalidPaths += [string]$citation.path }
    }

    return [pscustomobject]@{
        Passed       = ($failures.Count -eq 0)
        Failures     = $failures
        Citations    = $citations
        InvalidPaths = $invalidPaths
    }
}

# ---------------------------------------------------------------------
# 5. 逐题执行
# ---------------------------------------------------------------------
$results = New-Object System.Collections.Generic.List[object]
$invalidPathDetail = New-Object System.Collections.Generic.List[object]
$totalCitations = 0
$invalidCitations = 0
$degradedCount = 0
$startedAt = Get-Date

foreach ($case in $cases) {
    Write-Host ("[{0}] {1}" -f $case.id, $case.question) -ForegroundColor Yellow
    $result = $null
    $evaluation = $null
    try {
        $result = Invoke-AgentChat -Question $case.question
        $evaluation = Evaluate-Case -Case $case -Result $result
    } catch {
        $evaluation = [pscustomobject]@{
            Passed       = $false
            Failures     = @("调用异常：$($_.Exception.Message)")
            Citations    = @()
            InvalidPaths = @()
        }
        $result = [pscustomobject]@{ Answer = ''; ElapsedMs = 0; Done = $null; Meta = $null }
    }

    $citationCount = @($evaluation.Citations).Count
    $totalCitations += $citationCount
    $invalidCitations += @($evaluation.InvalidPaths).Count
    foreach ($invalidPath in @($evaluation.InvalidPaths)) {
        $invalidPathDetail.Add([pscustomobject]@{ Id = $case.id; Path = $invalidPath })
    }
    if ($null -ne $result.Done -and $result.Done.degraded) { $degradedCount++ }

    $results.Add([pscustomobject]@{
        Id          = $case.id
        Category    = $case.category
        Question    = $case.question
        Passed      = $evaluation.Passed
        Failures    = @($evaluation.Failures)
        CitationCnt = $citationCount
        InvalidCnt  = @($evaluation.InvalidPaths).Count
        ElapsedMs   = $result.ElapsedMs
    })

    if ($evaluation.Passed) {
        Write-Host ("    PASS（引用 {0}，{1} ms）" -f $citationCount, $result.ElapsedMs) -ForegroundColor Green
    } else {
        Write-Host ("    FAIL：{0}" -f ((@($evaluation.Failures)) -join '；')) -ForegroundColor Red
    }
}

$client.Dispose()

# ---------------------------------------------------------------------
# 6. 指标与报告
# ---------------------------------------------------------------------
$total = $results.Count
$passed = @($results | Where-Object { $_.Passed }).Count
$structuredCases = @($results | Where-Object { $_.Category -eq 'structured' -or $_.Category -eq 'schema' })
$structuredPassed = @($structuredCases | Where-Object { $_.Passed }).Count
$overallRate = if ($total -gt 0) { $passed / $total } else { 0 }
$structuredRate = if ($structuredCases.Count -gt 0) { $structuredPassed / $structuredCases.Count } else { 0 }
$citationValidRate = if ($totalCitations -gt 0) { ($totalCitations - $invalidCitations) / $totalCitations } else { 0 }

$overallThreshold = [double]$thresholds.overall
$structuredThreshold = [double]$thresholds.structured
$citationThreshold = [double]$thresholds.citationValidRate

$thresholdOk = ($overallRate -ge $overallThreshold) -and
               ($structuredRate -ge $structuredThreshold) -and
               ($citationValidRate -ge $citationThreshold)

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('# Wave 5.47 知识 Agent 黄金集评测报告')
[void]$sb.AppendLine('')
[void]$sb.AppendLine("- 评测时间：$($startedAt.ToString('yyyy-MM-dd HH:mm:ss'))")
[void]$sb.AppendLine("- 目标地址：$BaseUrl")
[void]$sb.AppendLine("- 黄金集：``$((Split-Path -Leaf $GoldenSet))``（$total 题）")
[void]$sb.AppendLine("- 降级答题：$degradedCount / $total（未配置模型时走检索摘要，属预期降级）")
if ($null -ne $indexStatus) {
    [void]$sb.AppendLine("- 索引状态：$($indexStatus.status)；文件 $($indexStatus.fileCount)；知识块 $($indexStatus.chunkCount)；版本 $($indexStatus.indexVersion)")
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 一、指标总览')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| 指标 | 实际 | 阈值 | 结论 |')
[void]$sb.AppendLine('|---|---|---|---|')
[void]$sb.AppendLine(("| 总体正确率 | {0}/{1}（{2:P1}） | ≥{3:P0} | {4} |" -f $passed, $total, $overallRate, $overallThreshold, $(if ($overallRate -ge $overallThreshold) { 'PASS' } else { 'FAIL' })))
[void]$sb.AppendLine(("| 结构化正确率（structured+schema） | {0}/{1}（{2:P1}） | ≥{3:P0} | {4} |" -f $structuredPassed, $structuredCases.Count, $structuredRate, $structuredThreshold, $(if ($structuredRate -ge $structuredThreshold) { 'PASS' } else { 'FAIL' })))
[void]$sb.AppendLine(("| 引用有效率 | {0}/{1}（{2:P1}） | ={3:P0} | {4} |" -f ($totalCitations - $invalidCitations), $totalCitations, $citationValidRate, $citationThreshold, $(if ($citationValidRate -ge $citationThreshold) { 'PASS' } else { 'FAIL' })))
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 二、逐题结果')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('| # | 类别 | 问题 | 结果 | 引用 | 耗时(ms) | 未通过原因 |')
[void]$sb.AppendLine('|---|---|---|---|---|---|---|')
foreach ($row in $results) {
    $reason = if ($row.Passed) { '' } else { ($row.Failures -join '；').Replace('|', '\|') }
    [void]$sb.AppendLine(("| {0} | {1} | {2} | {3} | {4} | {5} | {6} |" -f `
        $row.Id, $row.Category, $row.Question.Replace('|', '\|'), $(if ($row.Passed) { 'PASS' } else { 'FAIL' }), `
        $row.CitationCnt, $row.ElapsedMs, $reason))
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 三、无效引用明细')
[void]$sb.AppendLine('')
if ($invalidPathDetail.Count -eq 0) {
    [void]$sb.AppendLine('无（全部引用路径均存在，git:/runtime: 虚拟来源按约定视为有效）。')
} else {
    [void]$sb.AppendLine('| 题号 | 无效路径 |')
    [void]$sb.AppendLine('|---|---|')
    foreach ($item in $invalidPathDetail) {
        [void]$sb.AppendLine(("| {0} | {1} |" -f $item.Id, ([string]$item.Path).Replace('|', '\|')))
    }
}
[void]$sb.AppendLine('')
[void]$sb.AppendLine('## 四、结论')
[void]$sb.AppendLine('')
if ($thresholdOk) {
    [void]$sb.AppendLine('全部阈值达标，知识 Agent 问答质量满足 Wave 5.47 验收口径。')
} else {
    [void]$sb.AppendLine('存在未达标阈值，需按「逐题结果」中的未通过原因优化检索、事实采集或提示词。')
}

$report = $sb.ToString()
$outDir = Split-Path -Parent $OutFile
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }
[System.IO.File]::WriteAllText($OutFile, $report, (New-Object System.Text.UTF8Encoding($false)))

Write-Host ''
Write-Host ("总体：{0}/{1}（{2:P1}）  结构化：{3}/{4}（{5:P1}）  引用有效：{6:P1}" -f `
    $passed, $total, $overallRate, $structuredPassed, $structuredCases.Count, $structuredRate, $citationValidRate)
Write-Host "报告：$OutFile"

if (-not $thresholdOk) { exit 1 }
exit 0
