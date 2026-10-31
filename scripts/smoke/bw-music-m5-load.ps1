# Wave 5.75 M5-6 性能与容量冒烟（本机口径）：
#   首页 /music/home、详情 /music/public/song/{id} 的时延 P95；
#   流式 /music/stream/{songId} 的 50 并发首字节（TTFB）P95。
# 阈值（环境变量可覆盖，单位毫秒）：
#   HOME_P95_MS=300、DETAIL_P95_MS=200、STREAM_TTFB_P95_MS=300、
#   CONCURRENCY=50、PERF_ITERATIONS=20、PERF_ROUNDS=5
# 测量口径（重要，踩过的坑）：
#   1. 并发探针内嵌 C# 「共享 HttpClient + TaskCompletionSource 异步栅栏 + 每请求独立 Stopwatch」。
#      早期版本用 Runspace / 每线程新建 HttpClient / 逐条顺序 SendAsync，客户端自身发起开销
#      会把 50 并发抬到 400~700ms（对照组 /actuator/health 同样 300ms 即可证伪），属测量假象。
#   2. 每个路径先跑一轮并发预热并丢弃结果：首次代理探测、连接建链、JIT 会让首轮明显偏高。
#   3. 并发指标取多轮 P95 的中位数（默认 3 轮），避免单轮抖动决定结论。
# 说明：脚本直连 whale-music(8096)，不经过网关与前端，口径偏乐观；
#       线上 P95 以 Prometheus 直方图桶 + Grafana 面板（SLO-6/7/8）为准。
# 用法：powershell -File scripts/smoke/bw-music-m5-load.ps1
# 前置：whale-music(8096)、whale-file(8085)、MySQL、Redis、Nacos 已启动，且已跑过 M5 冒烟造过账号。

$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

$musicBase = if ($env:MUSIC_BASE) { $env:MUSIC_BASE } else { 'http://127.0.0.1:8096' }
$fileBase = if ($env:FILE_BASE) { $env:FILE_BASE } else { 'http://127.0.0.1:8085' }
$tenantId = if ($env:SMOKE_TENANT_ID) { $env:SMOKE_TENANT_ID } else { '1' }
$userId = if ($env:SMOKE_USER_ID) { $env:SMOKE_USER_ID } else { '1' }
$homeThreshold = if ($env:HOME_P95_MS) { [double]$env:HOME_P95_MS } else { 300 }
$detailThreshold = if ($env:DETAIL_P95_MS) { [double]$env:DETAIL_P95_MS } else { 200 }
$streamThreshold = if ($env:STREAM_TTFB_P95_MS) { [double]$env:STREAM_TTFB_P95_MS } else { 300 }
$concurrency = if ($env:CONCURRENCY) { [int]$env:CONCURRENCY } else { 50 }
$iterations = if ($env:PERF_ITERATIONS) { [int]$env:PERF_ITERATIONS } else { 20 }
$rounds = if ($env:PERF_ROUNDS) { [int]$env:PERF_ROUNDS } else { 5 }
$stamp = Get-Date -Format 'MMddHHmmss'

$h = @{
    'X-User-Id'          = $userId
    'X-Tenant-Id'        = $tenantId
    'X-Permission-Codes' = 'sys:file:upload,sys:file:delete,sys:music:artist:add,' +
    'sys:music:artist:delete,sys:music:song:add,sys:music:song:edit,sys:music:song:publish,' +
    'sys:music:song:delete,sys:music:song:list,sys:music:browse,sys:music:upload,' +
    'sys:music:comment:create,sys:music:comment:audit,sys:music:playlist:own,sys:music:like'
}

$audioPath = Join-Path $env:TEMP ('bw-music-m5-load-' + $stamp + '.mp3')
$audioBytes = 4096
[byte[]]$payload = New-Object byte[] $audioBytes
if ($audioBytes -gt 44) {
    [System.Text.Encoding]::ASCII.GetBytes('RIFF').CopyTo($payload, 0)
    [BitConverter]::GetBytes([int]($audioBytes - 8)).CopyTo($payload, 4)
    [System.Text.Encoding]::ASCII.GetBytes('WAVEfmt ').CopyTo($payload, 8)
    [BitConverter]::GetBytes([int]16).CopyTo($payload, 16)
    [BitConverter]::GetBytes([int16]1).CopyTo($payload, 20)
    [BitConverter]::GetBytes([int16]1).CopyTo($payload, 22)
    [BitConverter]::GetBytes([int]8000).CopyTo($payload, 24)
    [BitConverter]::GetBytes([int]8000).CopyTo($payload, 28)
    [BitConverter]::GetBytes([int16]1).CopyTo($payload, 32)
    [BitConverter]::GetBytes([int16]8).CopyTo($payload, 34)
    [System.Text.Encoding]::ASCII.GetBytes('data').CopyTo($payload, 36)
    [BitConverter]::GetBytes([int]($audioBytes - 44)).CopyTo($payload, 40)
}
[System.IO.File]::WriteAllBytes($audioPath, $payload)

$client = New-Object System.Net.Http.HttpClient
$client.Timeout = [TimeSpan]::FromMinutes(5)
[System.Net.ServicePointManager]::DefaultConnectionLimit = [Math]::Max([System.Net.ServicePointManager]::DefaultConnectionLimit, $concurrency + 20)

<#
.SYNOPSIS
上传音频文件并返回文件 ID。
#>
function Send-AudioUpload {
    $stream = New-Object System.IO.FileStream($audioPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read)
    try {
        $fileContent = New-Object System.Net.Http.StreamContent($stream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse('audio/mpeg')
        $form = New-Object System.Net.Http.MultipartFormDataContent
        $form.Add($fileContent, 'file', [System.IO.Path]::GetFileName($audioPath))
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Post, "$fileBase/file/upload")
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
调用音乐服务 JSON 接口。
#>
function Invoke-MusicApi {
    param([string]$Method, [string]$Path, [object]$Body = $null)

    return Invoke-Api -Method $Method -Uri ($musicBase + $Path) -Body $Body -Headers $h
}

<#
.SYNOPSIS
计算分位数（nearest-rank）。
#>
function Get-Percentile {
    param($Values, [double]$P)

    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $index = [Math]::Ceiling($P * $sorted.Count) - 1
    if ($index -lt 0) { $index = 0 }
    return [double]$sorted[$index]
}

<#
.SYNOPSIS
计算中位数（多轮 P95 聚合用）。
#>
function Get-Median {
    param($Values)

    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) { return 0 }
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) { return [double]$sorted[$middle] }
    return ([double]$sorted[$middle - 1] + [double]$sorted[$middle]) / 2.0
}

<#
.SYNOPSIS
顺序压测：返回每次请求耗时（毫秒，含读响应体）。
#>
function Measure-Sequential {
    param([string]$Path, [int]$Count)

    $durations = New-Object System.Collections.Generic.List[double]
    for ($i = 0; $i -lt $Count; $i++) {
        $request = New-Object System.Net.Http.HttpRequestMessage([System.Net.Http.HttpMethod]::Get, ($musicBase + $Path))
        foreach ($key in $h.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($key, $h[$key])
        }
        $watch = [System.Diagnostics.Stopwatch]::StartNew()
        $response = $client.SendAsync($request, [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead).Result
        $null = $response.Content.ReadAsStringAsync().Result
        $watch.Stop()
        if ([int]$response.StatusCode -ne 200) {
            throw "性能采样失败: $Path status=$([int]$response.StatusCode)"
        }
        $durations.Add($watch.Elapsed.TotalMilliseconds)
        $request.Dispose()
        $response.Dispose()
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

public class MusicProbeResult
{
    public double[] Samples;
    public int Errors;
}

public class MusicConcurrentProbe
{
    private static HttpClient _client;

    public static void Init(int concurrency)
    {
        var handler = new HttpClientHandler();
        handler.UseProxy = false;
        handler.MaxConnectionsPerServer = concurrency + 10;
        _client = new HttpClient(handler);
        _client.Timeout = TimeSpan.FromMinutes(2);
        // 线程池最小线程不足时，50 个等待栅栏的任务要排队拿到线程，
        // 会在客户侧凭空叠加约 100~200ms（对照组 /actuator/health 同样偏高即可证伪）
        int workerMin;
        int ioMin;
        ThreadPool.GetMinThreads(out workerMin, out ioMin);
        ThreadPool.SetMinThreads(Math.Max(workerMin, concurrency + 20), Math.Max(ioMin, 100));
    }

    public static void Shutdown()
    {
        if (_client != null) { _client.Dispose(); _client = null; }
    }

    public static MusicProbeResult Run(string url, Dictionary<string, string> headers, int count)
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
        var result = new MusicProbeResult();
        result.Samples = samples;
        int errors = 0;
        for (int i = 0; i < count; i++) { if (statuses[i] != 200) { errors++; } }
        result.Errors = errors;
        return result;
    }
}
'@

if (-not ('MusicConcurrentProbe' -as [type])) {
    Add-Type -TypeDefinition $probeSource -ReferencedAssemblies 'System.Net.Http', 'System.Diagnostics.Debug' -ErrorAction Stop
}
[MusicConcurrentProbe]::Init($concurrency)

<#
.SYNOPSIS
并发压测一个路径，返回该轮的样本数 / 错误数 / P50 / P95 / Max。
.DESCRIPTION
每个请求在栅栏放行后独立计时，完成「响应头到达」即停表，等价于首字节时延。
#>
function Measure-Concurrent {
    param([string]$Path, [int]$Count)

    $headerDictionary = New-Object 'System.Collections.Generic.Dictionary[string,string]'
    foreach ($key in $h.Keys) { $headerDictionary[$key] = [string]$h[$key] }
    $probe = [MusicConcurrentProbe]::Run(($musicBase + $Path), $headerDictionary, $Count)
    $samples = @($probe.Samples)
    return [pscustomobject]@{
        Count  = $samples.Count
        Errors = $probe.Errors
        P50    = Get-Percentile -Values $samples -P 0.50
        P95    = Get-Percentile -Values $samples -P 0.95
        Max    = Get-Percentile -Values $samples -P 1.0
    }
}

Write-Host "== M5 性能与容量冒烟 并发=$concurrency 顺序采样=$iterations 并发轮次=$rounds =="

# 0) 服务就绪
try {
    $health = Invoke-RestMethod -Uri "$musicBase/actuator/health" -TimeoutSec 5
    Assert '0.health' ($health.status -eq 'UP')
} catch {
    Assert '0.health' $false $_.Exception.Message
}

# 1) 造数：上传音频 → 歌手 → 公开歌曲上架
$fileId = Send-AudioUpload
$artist = Invoke-MusicApi -Method 'Post' -Path '/music/artist' -Body @{ name = "M5压测歌手-$stamp"; region = 'CN' }
Check '1.artistCreate' $artist
$artistId = [long]$artist.data.id
$song = Invoke-MusicApi -Method 'Post' -Path '/music/song' -Body @{
    artistId = $artistId; title = "M5压测歌曲-$stamp"; audioFileId = $fileId; durationMs = 30000;
    visibility = 'PUBLIC'; source = 'ORIGINAL'
}
Check '1.songCreate' $song
$songId = [long]$song.data.id
$published = Invoke-MusicApi -Method 'Post' -Path "/music/song/$songId/publish"
Check '1.songPublish' $published

# 2) 预热：顺序（填首页缓存 / JIT）+ 票据 + 每路径一轮并发（数据丢弃，避免首轮建链假象）
$warmup = Measure-Sequential -Path '/music/home' -Count 3
$null = Invoke-MusicApi -Method 'Get' -Path "/music/public/song/$songId"
$ticket = Invoke-MusicApi -Method 'Post' -Path '/music/play/ticket' -Body @{ songId = $songId }
Check '2.ticketIssue' $ticket
$streamPath = "/music/stream/$songId`?ticket=$($ticket.data.ticket)"
$detailPath = "/music/public/song/$songId"
$warmHome = Measure-Concurrent -Path '/music/home' -Count $concurrency
$warmDetail = Measure-Concurrent -Path $detailPath -Count $concurrency
$warmStream = Measure-Concurrent -Path $streamPath -Count $concurrency
Write-Host ('   预热(丢弃): home P95={0:N1}ms detail P95={1:N1}ms stream P95={2:N1}ms' -f `
    $warmHome.P95, $warmDetail.P95, $warmStream.P95)
Assert '2.concurrentWarmup' (($warmHome.Errors + $warmDetail.Errors + $warmStream.Errors) -eq 0) `
    "errors=$($warmHome.Errors + $warmDetail.Errors + $warmStream.Errors)"

# 3) 顺序时延：首页 / 详情（单用户口径）
$homeSamples = Measure-Sequential -Path '/music/home' -Count $iterations
$detailSamples = Measure-Sequential -Path $detailPath -Count $iterations
$homeP50 = Get-Percentile -Values $homeSamples -P 0.50
$homeP95 = Get-Percentile -Values $homeSamples -P 0.95
$homeMax = Get-Percentile -Values $homeSamples -P 1.0
$detailP50 = Get-Percentile -Values $detailSamples -P 0.50
$detailP95 = Get-Percentile -Values $detailSamples -P 0.95
$detailMax = Get-Percentile -Values $detailSamples -P 1.0
Write-Host ('   home   P50={0:N1}ms P95={1:N1}ms max={2:N1}ms (阈值 {3}ms)' -f $homeP50, $homeP95, $homeMax, $homeThreshold)
Write-Host ('   detail P50={0:N1}ms P95={1:N1}ms max={2:N1}ms (阈值 {3}ms)' -f $detailP50, $detailP95, $detailMax, $detailThreshold)
Assert '3.homeP95' ($homeP95 -le $homeThreshold) ('P95={0:N1}ms' -f $homeP95)
Assert '3.detailP95' ($detailP95 -le $detailThreshold) ('P95={0:N1}ms' -f $detailP95)

# 4) 并发时延：首页 / 详情 / 流式首字节（多轮取 P95 中位数）
$streamBaseline = Measure-Sequential -Path $streamPath -Count 3
$streamBaselineP95 = Get-Percentile -Values $streamBaseline -P 0.95
Write-Host ('   stream 单路 P95={0:N1}ms（基线，用于区分服务端饱和与客户端排队）' -f $streamBaselineP95)

$homeRounds = @()
$detailRounds = @()
$streamRounds = @()
for ($round = 1; $round -le $rounds; $round++) {
    # 轮次之间留 1 秒：本机同机承载 6 个 JVM + MySQL + Redis + Nacos，
    # 无间隔连续压测会让排队效应跨轮累积，测出来的不是单轮容量而是持续性过载。
    Start-Sleep -Milliseconds 1000
    $rHome = Measure-Concurrent -Path '/music/home' -Count $concurrency
    $rDetail = Measure-Concurrent -Path $detailPath -Count $concurrency
    $rStream = Measure-Concurrent -Path $streamPath -Count $concurrency
    $homeRounds += $rHome
    $detailRounds += $rDetail
    $streamRounds += $rStream
    Write-Host ('   第 {0} 轮: home P95={1:N1}ms detail P95={2:N1}ms stream P95={3:N1}ms（错误 {4}/{5}/{6}）' -f `
        $round, $rHome.P95, $rDetail.P95, $rStream.P95, $rHome.Errors, $rDetail.Errors, $rStream.Errors)
}

$homeConcurrentP95 = Get-Median -Values @($homeRounds | ForEach-Object { $_.P95 })
$detailConcurrentP95 = Get-Median -Values @($detailRounds | ForEach-Object { $_.P95 })
$streamConcurrentP95 = Get-Median -Values @($streamRounds | ForEach-Object { $_.P95 })
$streamRoundsMax = Get-Percentile -Values @($streamRounds | ForEach-Object { $_.P95 }) -P 1.0
Write-Host ('   home   {0} 并发 P95 中位={1:N1}ms（阈值 {2}ms）' -f $concurrency, $homeConcurrentP95, $homeThreshold)
Write-Host ('   detail {0} 并发 P95 中位={1:N1}ms（阈值 {2}ms）' -f $concurrency, $detailConcurrentP95, $detailThreshold)
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

# 5) 抓取指标可用性（M5-5：直方图桶 → 线上 SLO 面板 / SLO-6/7/8）
$metrics = Invoke-WebRequest -Uri "$musicBase/actuator/prometheus" -UseBasicParsing
$metricLines = $metrics.Content -split "`n"
foreach ($uriPattern in @('/music/home', '/music/public/song/{id}', '/music/stream/{songId}')) {
    $escaped = [regex]::Escape($uriPattern)
    $bucket = $metricLines | Where-Object { $_ -match ('http_server_requests_seconds_bucket\{[^}]*uri="' + $escaped + '"') }
    $label = '5.histogram' + $uriPattern.Replace('/', '_')
    Assert $label ($bucket.Count -gt 0) "uri=$uriPattern bucketLines=$($bucket.Count)"
}
[MusicConcurrentProbe]::Shutdown()

# 6) 清理
$deleted = Invoke-MusicApi -Method 'Delete' -Path "/music/song/$songId"
Check '6.deleteSong' $deleted
$delArtist = Invoke-MusicApi -Method 'Delete' -Path "/music/artist/$artistId"
Check '6.deleteArtist' $delArtist
$delFile = Invoke-Api -Method 'Delete' -Uri "$fileBase/file/$fileId" -Headers $h
Assert '6.deleteFile' ($delFile.code -eq 0 -or $delFile.code -eq 404) "code=$($delFile.code)"
Remove-Item -Path $audioPath -ErrorAction SilentlyContinue

Write-Host ''
Write-Host 'SMOKE-ALL-PASS'
exit 0
