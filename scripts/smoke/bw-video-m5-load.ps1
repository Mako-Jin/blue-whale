# ============================================================
# Wave 5.77 M5-6 性能与容量冒烟（本机口径）
#
# 覆盖：
#   0) 服务就绪（whale-video + whale-file）
#   1) 造数：上传含 ftyp 的假 MP4 → 签署声明 → 建短视频 → 发布 → 申请播放票据
#   2) 预热：顺序请求 + 每路径一轮并发（数据丢弃，避免首轮建链 / JIT 假象）
#   3) 顺序时延 P95：/video/meta（200ms）、/video/home（300ms）、/video/content/{id}（250ms）
#   4) 50 并发：首页 / 详情 / 流式首字节 P95（300 / 250 / 400ms），无 5xx
#   5) 指标直方图桶：四类 uri 可被 Prometheus 查询
#   6) 清理：内容与文件
#
# 阈值（环境变量可覆盖，单位毫秒）：
#   META_P95_MS=200、HOME_P95_MS=300、DETAIL_P95_MS=250、STREAM_TTFB_P95_MS=400、
#   CONCURRENCY=50、PERF_ITERATIONS=20、PERF_ROUNDS=3
# 测量口径（沿用音乐 M5-6 踩坑结论）：
#   1. 并发探针内嵌 C#「共享 HttpClient + TaskCompletionSource 异步栅栏 + 每请求独立 Stopwatch」；
#      每个请求在栅栏放行后独立计时，完成「响应头到达」即停表，等价于首字节时延。
#   2. 每个路径先跑一轮并发预热并丢弃结果；轮次之间留 1 秒，避免排队效应跨轮累积。
#   3. 并发指标取多轮 P95 的中位数，避免单轮抖动决定结论。
# 说明：脚本直连 whale-video(8098)，不经过网关与前端，口径偏乐观；
#       线上 P95 以 Prometheus 直方图桶 + Grafana 面板（SLO-9/10/11）为准。
# 用法：powershell -File scripts/smoke/bw-video-m5-load.ps1
# 前置：whale-video(8098)、whale-file(8085)、MySQL、Redis、Nacos 已启动。
# ============================================================

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
. "$PSScriptRoot/lib/video-common.ps1"
$ErrorActionPreference = 'Stop'

$videoBase = Get-VideoBase
$fileBase = Get-FileBase
$userId = Get-VideoUserId
$tenantId = Get-VideoTenantId
$stamp = Get-Date -Format 'MMddHHmmss'
$metaThreshold = if ($env:META_P95_MS) { [double]$env:META_P95_MS } else { 200 }
$homeThreshold = if ($env:HOME_P95_MS) { [double]$env:HOME_P95_MS } else { 300 }
$detailThreshold = if ($env:DETAIL_P95_MS) { [double]$env:DETAIL_P95_MS } else { 250 }
$streamThreshold = if ($env:STREAM_TTFB_P95_MS) { [double]$env:STREAM_TTFB_P95_MS } else { 400 }
$concurrency = if ($env:CONCURRENCY) { [int]$env:CONCURRENCY } else { 50 }
$iterations = if ($env:PERF_ITERATIONS) { [int]$env:PERF_ITERATIONS } else { 20 }
$rounds = if ($env:PERF_ROUNDS) { [int]$env:PERF_ROUNDS } else { 3 }

$h = New-VideoHeaders
$mp4Path = Join-Path $env:TEMP ('bw-video-m5-load-' + $stamp + '.mp4')
$payloadBytes = 4096
[byte[]]$payload = New-Object byte[] $payloadBytes
[BitConverter]::GetBytes([int]0x18).CopyTo($payload, 0)
[System.Text.Encoding]::ASCII.GetBytes('ftyp').CopyTo($payload, 4)
[System.Text.Encoding]::ASCII.GetBytes('mp42').CopyTo($payload, 8)
[System.IO.File]::WriteAllBytes($mp4Path, $payload)

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(5)
[System.Net.ServicePointManager]::DefaultConnectionLimit = [Math]::Max(
    [System.Net.ServicePointManager]::DefaultConnectionLimit, $concurrency + 20)

<#
.SYNOPSIS
以 multipart 上传假 MP4，返回文件 ID。
#>
function Send-VideoLoadUpload {
    $stream = New-Object System.IO.FileStream($mp4Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('video/mp4')
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add($fileContent, 'file', [System.IO.Path]::GetFileName($mp4Path))
        $request = New-Object System.Net.Http.HttpRequestMessage(
            [System.Net.Http.HttpMethod]::Post, ($fileBase + '/file/upload'))
        $request.Content = $form
        foreach ($key in $h.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $h[$key])
        }
        $response = $client.SendAsync($request).Result
        $result = $response.Content.ReadAsStringAsync().Result | ConvertFrom-Json
        Check '1.upload' $result
        return [long]$result.data.id
    } finally {
        $stream.Dispose()
    }
}

<#
.SYNOPSIS
计算分位数（nearest-rank）。
#>
function Get-VideoPercentile {
    param($Values, [double]$P)

    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $index = [int][Math]::Ceiling($P * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    return [double]$sorted[$index]
}

<#
.SYNOPSIS
计算中位数（多轮 P95 聚合用）。
#>
function Get-VideoMedian {
    param($Values)

    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

<#
.SYNOPSIS
顺序采样一个 GET 端点，返回每次耗时（毫秒，含读响应体）。
.PARAMETER ExtraHeaders
额外请求头（流式请求需要 Range）。
#>
function Measure-VideoSequential {
    param([string]$Path, [int]$Count, [hashtable]$ExtraHeaders = $null)

    $durations = New-Object System.Collections.Generic.List[double]
    for ($i = 0; $i -lt $Count; $i++) {
        $request = New-Object System.Net.Http.HttpRequestMessage(
            [System.Net.Http.HttpMethod]::Get, ($videoBase + $Path))
        foreach ($key in $h.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $h[$key])
        }
        if ($ExtraHeaders) {
            foreach ($key in $ExtraHeaders.Keys) {
                $null = $request.Headers.TryAddWithoutValidation($key, $ExtraHeaders[$key])
            }
        }
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
        $null = $response.Content.ReadAsByteArrayAsync().Result
        $watch.Stop()
        $status = [int]$response.StatusCode
        $request.Dispose()
        $response.Dispose()
        if ($status -ne 200 -and $status -ne 206) {
            throw "性能采样失败: $Path status=$status"
        }
        $durations.Add($watch.Elapsed.TotalMilliseconds)
    }
    return $durations
}

# 并发探针：共享 HttpClient + 无代理 + 异步栅栏；完成「响应头到达」即停表（首字节口径）
$probeSource = @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net.Http;
using System.Threading;
using System.Threading.Tasks;

public class VideoProbeResult
{
    public double[] Samples;
    public int Errors;
}

public class VideoConcurrentProbe
{
    private static HttpClient _client;

    public static void Init(int concurrency)
    {
        var handler = new HttpClientHandler();
        handler.UseProxy = false;
        handler.MaxConnectionsPerServer = concurrency + 10;
        _client = new HttpClient(handler);
        _client.Timeout = TimeSpan.FromMinutes(2);
        int workerMin;
        int ioMin;
        ThreadPool.GetMinThreads(out workerMin, out ioMin);
        ThreadPool.SetMinThreads(Math.Max(workerMin, concurrency + 20), Math.Max(ioMin, 100));
    }

    public static void Shutdown()
    {
        if (_client != null) { _client.Dispose(); _client = null; }
    }

    public static VideoProbeResult Run(string url, Dictionary<string, string> headers, int count)
    {
        var samples = new double[count];
        var statuses = new int[count];
        var gate = new TaskCompletionSource<bool>(TaskCreationOptions.RunContinuationsAsynchronously);
        var ready = new CountdownEvent(count);
        var tasks = new Task[count];
        for (int i = 0; i < count; i++)
        {
            int idx = i;
            tasks[i] = Task.Run(async () =>
            {
                ready.Signal();
                await gate.Task.ConfigureAwait(false);
                using (var request = new HttpRequestMessage(HttpMethod.Get, url))
                {
                    foreach (var pair in headers) { request.Headers.TryAddWithoutValidation(pair.Key, pair.Value); }
                    var watch = Stopwatch.StartNew();
                    try
                    {
                        using (var response = await _client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead).ConfigureAwait(false))
                        {
                            watch.Stop();
                            statuses[idx] = (int)response.StatusCode;
                            samples[idx] = watch.Elapsed.TotalMilliseconds;
                            await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
                        }
                    }
                    catch (Exception)
                    {
                        statuses[idx] = -1;
                    }
                }
            });
        }
        ready.Wait(TimeSpan.FromSeconds(30));
        gate.SetResult(true);
        Task.WaitAll(tasks);
        var result = new VideoProbeResult();
        result.Samples = samples;
        int errors = 0;
        for (int i = 0; i < count; i++) { if (statuses[i] != 200 && statuses[i] != 206) { errors++; } }
        result.Errors = errors;
        return result;
    }
}
'@

if (-not ('VideoConcurrentProbe' -as [type])) {
    Add-Type -TypeDefinition $probeSource -ReferencedAssemblies 'System.Net.Http', 'System.Diagnostics.Debug' -ErrorAction Stop
}
[VideoConcurrentProbe]::Init($concurrency)

<#
.SYNOPSIS
并发压测一个路径，返回该轮的样本数 / 错误数 / P50 / P95 / Max。
.PARAMETER ExtraHeaders
额外请求头（流式请求需要 Range）。
#>
function Measure-VideoConcurrent {
    param([string]$Path, [int]$Count, [hashtable]$ExtraHeaders = $null)

    $headerDictionary = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    foreach ($key in $h.Keys) { $headerDictionary[$key] = [string]$h[$key] }
    if ($ExtraHeaders) {
        foreach ($key in $ExtraHeaders.Keys) { $headerDictionary[$key] = [string]$ExtraHeaders[$key] }
    }
    $probe = [VideoConcurrentProbe]::Run(($videoBase + $Path), $headerDictionary, $Count)
    $samples = @($probe.Samples)
    return [pscustomobject]@{
        Count  = $samples.Count
        Errors = $probe.Errors
        P50    = Get-VideoPercentile -Values $samples -P 0.50
        P95    = Get-VideoPercentile -Values $samples -P 0.95
        Max    = Get-VideoPercentile -Values $samples -P 1.0
    }
}

Write-Host "== M5 性能与容量冒烟 并发=$concurrency 顺序采样=$iterations 并发轮次=$rounds =="

# 0) 服务就绪
Assert-VideoHealth -Label '0' -IncludeFile

# 1) 造数：签署声明 → 上传 → 建短视频 → 发布 → 申请票据
$declaration = Send-VideoProbe -Method 'Get' -Path '/video/declaration/current'
$declarationView = Assert-VideoOk -Label '1.declarationCurrent' -Probe $declaration
$accept = Send-VideoProbe -Method 'Post' -Path '/video/declaration/accept' `
    -Body @{ version = $declarationView.version; contentSource = 'ORIGINAL' }
$null = Assert-VideoOk -Label '1.declarationAccept' -Probe $accept
$fileId = Send-VideoLoadUpload
$create = Send-VideoProbe -Method 'Post' -Path '/video/user/shorts' -Body @{
    title = "M5 压测短视频-$stamp"; videoFileId = $fileId; durationMs = 5000; width = 720; height = 1280;
    visibility = 'PUBLIC'; source = 'ORIGINAL'
}
$contentId = Assert-VideoOk -Label '1.contentCreate' -Probe $create
$publish = Send-VideoProbe -Method 'Post' -Path "/video/user/shorts/$contentId/publish"
$null = Assert-VideoOk -Label '1.publish' -Probe $publish
$ticket = Send-VideoProbe -Method 'Post' -Path '/video/play/ticket' -Body @{ contentId = $contentId }
$ticketView = Assert-VideoOk -Label '1.ticketIssue' -Probe $ticket

$homePath = '/video/home'
$detailPath = "/video/content/$contentId"
$streamPath = [string]$ticketView.streamUrl
$streamHeaders = @{ Range = 'bytes=0-1023' }

# 2) 预热：顺序 + 每路径一轮并发（结果丢弃）
$null = Measure-VideoSequential -Path $homePath -Count 3
$null = Measure-VideoSequential -Path $detailPath -Count 3
$null = Measure-VideoSequential -Path $streamPath -Count 3 -ExtraHeaders $streamHeaders
$warmHome = Measure-VideoConcurrent -Path $homePath -Count $concurrency
$warmDetail = Measure-VideoConcurrent -Path $detailPath -Count $concurrency
$warmStream = Measure-VideoConcurrent -Path $streamPath -Count $concurrency -ExtraHeaders $streamHeaders
Write-Host ('   预热(丢弃): home P95={0:N1}ms detail P95={1:N1}ms stream P95={2:N1}ms' -f `
    $warmHome.P95, $warmDetail.P95, $warmStream.P95)
Assert '2.concurrentWarmup' (($warmHome.Errors + $warmDetail.Errors + $warmStream.Errors) -eq 0) `
    "errors=$($warmHome.Errors + $warmDetail.Errors + $warmStream.Errors)"

# 3) 顺序时延：meta / 首页 / 详情
$metaSamples = Measure-VideoSequential -Path '/video/meta' -Count $iterations
$homeSamples = Measure-VideoSequential -Path $homePath -Count $iterations
$detailSamples = Measure-VideoSequential -Path $detailPath -Count $iterations
$metaP95 = Get-VideoPercentile -Values $metaSamples -P 0.95
$homeP95 = Get-VideoPercentile -Values $homeSamples -P 0.95
$detailP95 = Get-VideoPercentile -Values $detailSamples -P 0.95
Write-Host ('   meta   P50={0:N1}ms P95={1:N1}ms max={2:N1}ms（阈值 {3}ms）' -f `
    (Get-VideoPercentile -Values $metaSamples -P 0.50), $metaP95, `
    (Get-VideoPercentile -Values $metaSamples -P 1.0), $metaThreshold)
Write-Host ('   home   P50={0:N1}ms P95={1:N1}ms max={2:N1}ms（阈值 {3}ms）' -f `
    (Get-VideoPercentile -Values $homeSamples -P 0.50), $homeP95, `
    (Get-VideoPercentile -Values $homeSamples -P 1.0), $homeThreshold)
Write-Host ('   detail P50={0:N1}ms P95={1:N1}ms max={2:N1}ms（阈值 {3}ms）' -f `
    (Get-VideoPercentile -Values $detailSamples -P 0.50), $detailP95, `
    (Get-VideoPercentile -Values $detailSamples -P 1.0), $detailThreshold)
Assert '3.metaP95' ($metaP95 -le $metaThreshold) ('P95={0:N1}ms' -f $metaP95)
Assert '3.homeP95' ($homeP95 -le $homeThreshold) ('P95={0:N1}ms' -f $homeP95)
Assert '3.detailP95' ($detailP95 -le $detailThreshold) ('P95={0:N1}ms' -f $detailP95)
Assert '3.samples' ($metaSamples.Count -eq $iterations -and $homeSamples.Count -eq $iterations -and
    $detailSamples.Count -eq $iterations) "count=$($metaSamples.Count)/$($homeSamples.Count)/$($detailSamples.Count)"

# 4) 并发时延：首页 / 详情 / 流式首字节（多轮取 P95 中位数）
$homeRounds = @()
$detailRounds = @()
$streamRounds = @()
for ($round = 1; $round -le $rounds; $round++) {
    Start-Sleep -Milliseconds 1000
    $rHome = Measure-VideoConcurrent -Path $homePath -Count $concurrency
    $rDetail = Measure-VideoConcurrent -Path $detailPath -Count $concurrency
    $rStream = Measure-VideoConcurrent -Path $streamPath -Count $concurrency -ExtraHeaders $streamHeaders
    $homeRounds += $rHome
    $detailRounds += $rDetail
    $streamRounds += $rStream
    Write-Host ('   第 {0} 轮: home P95={1:N1}ms detail P95={2:N1}ms stream P95={3:N1}ms（错误 {4}/{5}/{6}）' -f `
        $round, $rHome.P95, $rDetail.P95, $rStream.P95, $rHome.Errors, $rDetail.Errors, $rStream.Errors)
}

$homeConcurrentP95 = Get-VideoMedian -Values @($homeRounds | ForEach-Object { $_.P95 })
$detailConcurrentP95 = Get-VideoMedian -Values @($detailRounds | ForEach-Object { $_.P95 })
$streamConcurrentP95 = Get-VideoMedian -Values @($streamRounds | ForEach-Object { $_.P95 })
$streamRoundsMax = Get-VideoPercentile -Values @($streamRounds | ForEach-Object { $_.P95 }) -P 1.0
Write-Host ('   home   {0} 并发 P95 中位={1:N1}ms（阈值 {2}ms）' -f `
    $concurrency, $homeConcurrentP95, $homeThreshold)
Write-Host ('   detail {0} 并发 P95 中位={1:N1}ms（阈值 {2}ms）' -f `
    $concurrency, $detailConcurrentP95, $detailThreshold)
Write-Host ('   stream {0} 并发首字节 P95 中位={1:N1}ms 最差轮={2:N1}ms（阈值 {3}ms）' -f `
    $concurrency, $streamConcurrentP95, $streamRoundsMax, $streamThreshold)

$allRounds = @($homeRounds) + @($detailRounds) + @($streamRounds)
$totalErrors = ($allRounds | Measure-Object -Property Errors -Sum).Sum
Assert '4.concurrentNoError' ($totalErrors -eq 0) "errors=$totalErrors"
Assert '4.concurrentSamples' (($allRounds | Where-Object { $_.Count -ne $concurrency }).Count -eq 0) `
    "rounds=$($allRounds.Count) expected=$concurrency"
Assert '4.homeConcurrentP95' ($homeConcurrentP95 -le $homeThreshold) ('P95={0:N1}ms' -f $homeConcurrentP95)
Assert '4.detailConcurrentP95' ($detailConcurrentP95 -le $detailThreshold) ('P95={0:N1}ms' -f $detailConcurrentP95)
Assert '4.streamTtfbP95' ($streamConcurrentP95 -le $streamThreshold) ('P95={0:N1}ms' -f $streamConcurrentP95)

# 5) 指标直方图（M5-5：SLO-9/10/11 数据来源）
$metrics = Invoke-WebRequest -Uri ($videoBase + '/actuator/prometheus') -UseBasicParsing
$metricLines = $metrics.Content -split "`n"
foreach ($uriPattern in @('/video/meta', '/video/home', '/video/content/{id}', '/video/stream/{contentId}')) {
    $escaped = [regex]::Escape($uriPattern)
    $buckets = $metricLines | Where-Object {
        $_ -match ('http_server_requests_seconds_bucket\{[^}]*uri="' + $escaped + '"')
    }
    Assert ('5.histogram' + $uriPattern.Replace('/', '_')) ($buckets.Count -gt 0) `
        "uri=$uriPattern bucketLines=$($buckets.Count)"
}
[VideoConcurrentProbe]::Shutdown()

# 6) 清理：内容 → 文件
$deleted = Send-VideoProbe -Method 'Delete' -Path "/video/user/shorts/$contentId"
$null = Assert-VideoOk -Label '6.deleteContent' -Probe $deleted
$delFile = Send-VideoProbe -Method 'Delete' -Path "/file/$fileId" -BaseUri $fileBase
Assert '6.deleteFile' ($null -ne $delFile.Result -and
    ([int]$delFile.Result.code -eq 0 -or [int]$delFile.Result.code -eq 404)) `
    "code=$($delFile.Result.code) body=$($delFile.BodyText)"
Remove-Item -Path $mp4Path -ErrorAction SilentlyContinue

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
