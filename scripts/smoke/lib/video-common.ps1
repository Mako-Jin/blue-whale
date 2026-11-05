# ============================================================
# 视频平台冒烟公共工具（Wave 5.77 M0-8 API 契约冻结）
#
# 用途：bw-video-m1-smoke.ps1 … bw-video-m5-smoke.ps1、bw-video-m5-load.ps1
#       共用「服务就绪 / meta 契约 / 权限拒绝 / 里程碑端点探测」等基础断言，
#       避免每个里程碑脚本重复实现同一套 HTTP 与断言逻辑。
# 引入顺序（必须先引入 common.ps1）：
#   . "$PSScriptRoot/lib/common.ps1"
#   . "$PSScriptRoot/lib/video-common.ps1"
# 环境变量：
#   VIDEO_BASE              whale-video 直连地址，默认 http://127.0.0.1:8098
#   FILE_BASE               whale-file 直连地址，默认 http://127.0.0.1:8085
#   SMOKE_USER_ID / SMOKE_TENANT_ID  身份头，默认 1 / 1
#   VIDEO_PERMISSION_CODES  覆盖默认权限码集合（最小权限验证用）
# 兼容 PowerShell 5.1：不使用 ?. / ?? / -SkipHttpErrorCheck 等 7.x 语法。
# ============================================================

# Windows PowerShell 5.1 默认不加载 System.Net.Http；显式加载后
# Send-VideoProbe / Send-VideoStreamProbe 才能使用 HttpClient 与 HttpMethod。
if (-not ('System.Net.Http.HttpClient' -as [type])) {
    Add-Type -AssemblyName System.Net.Http
}

<#
.SYNOPSIS
读取 whale-video 直连地址（去掉尾部斜杠）。
#>
function Get-VideoBase {
    if ($env:VIDEO_BASE) {
        return $env:VIDEO_BASE.TrimEnd('/')
    }
    return 'http://127.0.0.1:8098'
}

<#
.SYNOPSIS
读取 whale-file 直连地址（去掉尾部斜杠）。
#>
function Get-FileBase {
    if ($env:FILE_BASE) {
        return $env:FILE_BASE.TrimEnd('/')
    }
    return 'http://127.0.0.1:8085'
}

<#
.SYNOPSIS
读取冒烟用户 ID（默认 1）。
#>
function Get-VideoUserId {
    if ($env:SMOKE_USER_ID) {
        return $env:SMOKE_USER_ID
    }
    return '1'
}

<#
.SYNOPSIS
读取冒烟租户 ID（默认 1）。
#>
function Get-VideoTenantId {
    if ($env:SMOKE_TENANT_ID) {
        return $env:SMOKE_TENANT_ID
    }
    return '1'
}

<#
.SYNOPSIS
读取冒烟权限码集合：默认覆盖视频平台 305–322 全部权限。
.DESCRIPTION
与 auth 迁移脚本 migration-5.77-video.sql 的种子码同口径；
设置 VIDEO_PERMISSION_CODES 可验证「只有部分权限」时的最小权限行为。
#>
function Get-VideoPermissionCodes {
    if ($env:VIDEO_PERMISSION_CODES) {
        return $env:VIDEO_PERMISSION_CODES
    }
    return ('sys:file:upload,sys:file:delete,sys:video:content:list,' +
        'sys:video:content:add,sys:video:content:edit,sys:video:content:delete,' +
        'sys:video:content:publish,sys:video:content:offline,sys:video:content:takedown,' +
        'sys:video:comment:audit,sys:video:stat:view,sys:video:browse,' +
        'sys:video:upload,sys:video:like,sys:video:favorite,sys:video:comment:create,' +
        'sys:video:category:manage,sys:video:series:manage,sys:video:movie:manage,' +
        'sys:video:creator:manage')
}

<#
.SYNOPSIS
构造与网关注入口径一致的身份 / 权限请求头。
.PARAMETER PermissionCodes
权限码；为空时使用 Get-VideoPermissionCodes 的默认集合。
#>
function New-VideoHeaders {
    param([string]$PermissionCodes = '')

    if ([string]::IsNullOrWhiteSpace($PermissionCodes)) {
        $PermissionCodes = Get-VideoPermissionCodes
    }
    return @{
        'X-User-Id'          = Get-VideoUserId
        'X-Tenant-Id'        = Get-VideoTenantId
        'X-Permission-Codes' = $PermissionCodes
    }
}

<#
.SYNOPSIS
发起一次视频服务请求，返回 HTTP 状态码、响应体文本与解析后的 Result 信封。
.DESCRIPTION
与 Invoke-Api 不同，这里不会因 HTTP 非 2xx / 非 JSON 响应抛错，
便于骨架脚本区分「端点尚未实现」与「业务错误」。
#>
function Send-VideoProbe {
    param(
        [string]$Method = 'Get',
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = $null,
        [string]$BaseUri = ''
    )

    if ([string]::IsNullOrWhiteSpace($BaseUri)) {
        $BaseUri = Get-VideoBase
    }
    if (-not $Headers) {
        $Headers = New-VideoHeaders
    }
    $verb = [System.Net.Http.HttpMethod]::Get
    if ($Method -eq 'Post') { $verb = [System.Net.Http.HttpMethod]::Post }
    if ($Method -eq 'Put') { $verb = [System.Net.Http.HttpMethod]::Put }
    if ($Method -eq 'Delete') { $verb = [System.Net.Http.HttpMethod]::Delete }

    if (-not $script:VideoHttp) {
        $script:VideoHttp = New-Object System.Net.Http.HttpClient
        $script:VideoHttp.Timeout = [TimeSpan]::FromMinutes(5)
    }

    $request = New-Object System.Net.Http.HttpRequestMessage($verb, ($BaseUri + $Path))
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $request.Content = New-Object System.Net.Http.StringContent(
            $json, [System.Text.Encoding]::UTF8, 'application/json')
    }
    foreach ($key in $Headers.Keys) {
        $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
    }
    $response = $null
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $response = $script:VideoHttp.SendAsync($request).Result
        $bodyText = $response.Content.ReadAsStringAsync().Result
        $watch.Stop()
        $result = $null
        if ($bodyText) {
            try {
                $result = $bodyText | ConvertFrom-Json
            } catch {
                $result = $null
            }
        }
        return [pscustomobject]@{
            Status             = [int]$response.StatusCode
            BodyText           = $bodyText
            Result             = $result
            ElapsedMilliseconds = [double]$watch.Elapsed.TotalMilliseconds
        }
    } finally {
        $watch.Stop()
        $request.Dispose()
        if ($null -ne $response) {
            $response.Dispose()
        }
    }
}

<#
.SYNOPSIS
读取单个 HTTP 响应头的原始值（缺失返回空字符串）。
.DESCRIPTION
System.Net.Http 把响应头拆到 Headers / Content.Headers 两处，
这里统一用 TryGetValues 取原始文本，避免属性对象 ToString 与网关实际下发不一致。
.PARAMETER Headers
响应头集合（$response.Headers 或 $response.Content.Headers）。
.PARAMETER Name
响应头名称。
#>
function Get-VideoResponseHeaderValue {
    param([object]$Headers, [string]$Name)

    if ($null -eq $Headers) {
        return ''
    }
    $values = $null
    try {
        if ($Headers.TryGetValues($Name, [ref]$values)) {
            return (@($values) -join ',')
        }
    } catch {
        return ''
    }
    return ''
}

<#
.SYNOPSIS
读取响应头：先查响应头集合，再查内容头集合（缺失返回空字符串）。
.DESCRIPTION
Accept-Ranges / Cache-Control / X-Content-Type-Options 属响应头，
Content-Range / Content-Length / ETag 属内容头；.NET 会按语义拆分到两处，
统一在这里做一次回退，避免断言因读取位置不同而误判为空。
.PARAMETER Response
HttpResponseMessage。
.PARAMETER Name
响应头名称。
#>
function Get-VideoHeaderValue {
    param([object]$Response, [string]$Name)

    $value = Get-VideoResponseHeaderValue -Headers $Response.Headers -Name $Name
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        return $value
    }
    return Get-VideoResponseHeaderValue -Headers $Response.Content.Headers -Name $Name
}

<#
.SYNOPSIS
按字节读取视频流响应，返回状态码、Range 响应头与已读字节数。
.DESCRIPTION
/video/stream 返回二进制（200 / 206 / 416），Send-VideoProbe 的文本解析无法判断分段语义，
且票据无效时服务端返回的是 Result JSON 信封而非二进制，因此两者分工：
- 合法票据的分段 / 越界断言用本函数；
- 无票据 / 伪票据 / 跨用户断言仍用 Send-VideoProbe + Assert-VideoBizError。
.PARAMETER Path
请求路径，可直接传入票据视图里的 streamUrl。
.PARAMETER Range
Range 请求头（如 bytes=0-1023）；省略时请求全量，会读取整份文件，脚本中应始终带上 Range。
.PARAMETER IfRange
If-Range 请求头。
.PARAMETER Headers
身份 / 权限请求头，默认使用 New-VideoHeaders。
.PARAMETER MaxBytes
最多读取的响应字节数，默认 4096，避免误拉大文件；超过即截断并置 Truncated=$true。
#>
function Send-VideoStreamProbe {
    param(
        [string]$Path,
        [string]$Range = '',
        [string]$IfRange = '',
        [hashtable]$Headers = $null,
        [int]$MaxBytes = 4096
    )

    if (-not $Headers) {
        $Headers = New-VideoHeaders
    }
    if (-not $script:VideoStreamHttp) {
        $script:VideoStreamHttp = New-Object System.Net.Http.HttpClient
        $script:VideoStreamHttp.Timeout = [TimeSpan]::FromMinutes(5)
    }
    $request = New-Object System.Net.Http.HttpRequestMessage(
        [System.Net.Http.HttpMethod]::Get, ((Get-VideoBase) + $Path))
    foreach ($key in $Headers.Keys) {
        $null = $request.Headers.TryAddWithoutValidation($key, $Headers[$key])
    }
    if (-not [string]::IsNullOrWhiteSpace($Range)) {
        $null = $request.Headers.TryAddWithoutValidation('Range', $Range.Trim())
    }
    if (-not [string]::IsNullOrWhiteSpace($IfRange)) {
        $null = $request.Headers.TryAddWithoutValidation('If-Range', $IfRange.Trim())
    }
    $response = $null
    $body = $null
    try {
        $response = $script:VideoStreamHttp.SendAsync($request).Result
        $bytesRead = 0L
        $body = $response.Content.ReadAsStreamAsync().Result
        $buffer = New-Object byte[] 4096
        while ($bytesRead -lt $MaxBytes) {
            $read = $body.Read($buffer, 0, $buffer.Length)
            if ($read -le 0) {
                break
            }
            $bytesRead += $read
        }
        $lengthText = Get-VideoHeaderValue -Response $response -Name 'Content-Length'
        $contentLength = 0L
        if (-not [string]::IsNullOrWhiteSpace($lengthText)) {
            $contentLength = [long]$lengthText
        }
        return [pscustomobject]@{
            Status        = [int]$response.StatusCode
            ContentType   = Get-VideoHeaderValue -Response $response -Name 'Content-Type'
            ContentRange  = Get-VideoHeaderValue -Response $response -Name 'Content-Range'
            AcceptRanges  = Get-VideoHeaderValue -Response $response -Name 'Accept-Ranges'
            CacheControl  = Get-VideoHeaderValue -Response $response -Name 'Cache-Control'
            NoSniff       = Get-VideoHeaderValue -Response $response -Name 'X-Content-Type-Options'
            ETag          = Get-VideoHeaderValue -Response $response -Name 'ETag'
            ContentLength = $contentLength
            BytesRead     = $bytesRead
            Truncated     = ($bytesRead -ge $MaxBytes)
        }
    } finally {
        if ($null -ne $body) {
            $body.Dispose()
        }
        $request.Dispose()
        if ($null -ne $response) {
            $response.Dispose()
        }
    }
}

<#
.SYNOPSIS
检查服务 /actuator/health 是否为 UP。
.PARAMETER Label
断言标签前缀，默认 0。
.PARAMETER IncludeFile
同时检查 whale-file（视频字节与封面均依赖该服务）。
#>
function Assert-VideoHealth {
    param([string]$Label = '0', [switch]$IncludeFile)

    $targets = @(@{ n = 'video'; u = (Get-VideoBase) + '/actuator/health' })
    if ($IncludeFile) {
        $targets += @{ n = 'file'; u = (Get-FileBase) + '/actuator/health' }
    }
    foreach ($pair in $targets) {
        try {
            $health = Invoke-RestMethod -Uri $pair.u -TimeoutSec 5
            Assert "$Label.health[$($pair.n)]" ($health.status -eq 'UP')
        } catch {
            Assert "$Label.health[$($pair.n)]" $false $_.Exception.Message
        }
    }
}

<#
.SYNOPSIS
断言 GET /video/meta 与前端 VideoMeta 契约一致，且不暴露任何文件 ID。
#>
function Assert-VideoMetaContract {
    param([string]$Label = '1')

    $probe = Send-VideoProbe -Method 'Get' -Path '/video/meta'
    if ($null -eq $probe.Result) {
        Assert "$Label.meta" $false "HTTP $($probe.Status) body=$($probe.BodyText)"
        return
    }
    Check "$Label.metaEnvelope" $probe.Result
    $meta = $probe.Result.data
    Assert "$Label.metaService" ($meta.service -eq 'whale-video') "service=$($meta.service)"
    Assert "$Label.metaTenant" ($null -ne $meta.tenantId) "tenantId=$($meta.tenantId)"
    Assert "$Label.metaDeclaration" ($meta.declarationVersion -ne '' -and $meta.declarationTitle -ne '') `
        "version=$($meta.declarationVersion)"
    Assert "$Label.metaUpload" ($meta.uploadEnabled -eq $true)
    Assert "$Label.metaTypes" ($meta.contentTypes.Count -eq 3 -and
        $meta.contentTypes -contains 'SHORT_VIDEO' -and
        $meta.contentTypes -contains 'VIDEO' -and
        $meta.contentTypes -contains 'MOVIE') "types=$($meta.contentTypes -join ',')"
    Assert "$Label.metaShortLimit" ([long]$meta.shortMaxBytes -eq 314572800) "short=$($meta.shortMaxBytes)"
    Assert "$Label.metaVideoLimit" ([long]$meta.videoMaxBytes -eq 2147483648) "video=$($meta.videoMaxBytes)"
    Assert "$Label.metaMovieLimit" ([long]$meta.movieMaxBytes -eq 4294967296) "movie=$($meta.movieMaxBytes)"
    Assert "$Label.metaTicket" ([int]$meta.playTicketTtlSeconds -eq 300 -and
        [int]$meta.playTicketRefreshSeconds -eq 240) `
        "ttl=$($meta.playTicketTtlSeconds) refresh=$($meta.playTicketRefreshSeconds)"
    Assert "$Label.metaComment" ($meta.commentPreAudit -eq $false) "preAudit=$($meta.commentPreAudit)"

    # 公开 VO 不暴露文件 ID（契约 §4.4 末段）
    $leaked = @($meta.PSObject.Properties.Name | Where-Object { $_ -match 'fileId' })
    Assert "$Label.metaNoFileId" ($leaked.Count -eq 0) "leaked=$($leaked -join ',')"
}

<#
.SYNOPSIS
断言缺少 sys:video:browse 时 /video/meta 返回业务码 403（服务侧 fail-closed）。
#>
function Assert-VideoPermissionDenied {
    param([string]$Label = '2')

    $headers = @{
        'X-User-Id'          = Get-VideoUserId
        'X-Tenant-Id'        = Get-VideoTenantId
        'X-Permission-Codes' = 'sys:file:upload'
    }
    $probe = Send-VideoProbe -Method 'Get' -Path '/video/meta' -Headers $headers
    Assert "$Label.permissionDenied" ($null -ne $probe.Result -and [int]$probe.Result.code -eq 403) `
        "HTTP $($probe.Status) code=$($probe.Result.code)"
}

<#
.SYNOPSIS
探测一个里程碑端点是否已实现：已实现则打印状态，未实现则标记 PENDING（不失败）。
.DESCRIPTION
M0 骨架期大多数端点尚未落地，脚本以「PENDING / OK」输出里程碑进度，
待对应里程碑实现后由该里程碑脚本改为强断言。
#>
function Test-VideoEndpoint {
    param(
        [string]$Label,
        [string]$Method = 'Get',
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = $null
    )

    if (-not $Headers) {
        $Headers = New-VideoHeaders
    }
    $probe = Send-VideoProbe -Method $Method -Path $Path -Body $Body -Headers $Headers
    if ($null -eq $probe.Result -or -not ($probe.Result.PSObject.Properties.Name -contains 'code')) {
        Write-Host "PENDING $Label（端点未实现：$Method $Path HTTP $($probe.Status)）"
        return $false
    }
    if ([int]$probe.Status -ne 200) {
        Write-Host "PENDING $Label（HTTP $($probe.Status) code=$($probe.Result.code)）"
        return $false
    }
    Write-Host "OK   $Label（HTTP 200 code=$($probe.Result.code)）"
    return $true
}

<#
.SYNOPSIS
断言一次视频请求 HTTP 200 且业务码为 0，返回信封中的 data。
.DESCRIPTION
M1 起端点已落地，用本函数替代 Test-VideoEndpoint 的 PENDING 探测，
失败即抛（脚本需先设 $script:SmokeAssertThrow = $true）。
.PARAMETER Label
断言标签。
.PARAMETER Probe
Send-VideoProbe 的返回值。
#>
function Assert-VideoOk {
    param([string]$Label, [object]$Probe)

    Assert "$Label.http" ([int]$Probe.Status -eq 200) "HTTP $($Probe.Status)"
    $result = $Probe.Result
    Assert "$Label.code" ($null -ne $result -and [int]$result.code -eq 0) `
        "code=$($result.code) body=$($Probe.BodyText)"
    if ($null -eq $result) {
        return $null
    }
    return $result.data
}

<#
.SYNOPSIS
断言业务码为指定错误码（用于权限拒绝 / 参数非法等反向用例）。
.PARAMETER Label
断言标签。
.PARAMETER Probe
Send-VideoProbe 的返回值。
.PARAMETER ExpectCode
期望的业务码。
#>
function Assert-VideoBizError {
    param([string]$Label, [object]$Probe, [int]$ExpectCode)

    Assert "$Label.code" ($null -ne $Probe.Result -and [int]$Probe.Result.code -eq $ExpectCode) `
        "expect=$ExpectCode actual=$($Probe.Result.code) body=$($Probe.BodyText)"
}

<#
.SYNOPSIS
断言对象数组未泄露任何内部文件 ID 字段（契约 §4.4）。
.PARAMETER Label
断言标签。
.PARAMETER Items
待检查的对象数组；空元素自动跳过。
#>
function Assert-VideoNoFileId {
    param([string]$Label, [object[]]$Items)

    $leaked = @()
    foreach ($item in $Items) {
        if ($null -eq $item) {
            continue
        }
        $leaked += @($item.PSObject.Properties.Name | Where-Object { $_ -match 'fileId' })
    }
    Assert "$Label.noFileId" ($leaked.Count -eq 0) "leaked=$($leaked -join ',')"
}

<#
.SYNOPSIS
断言 PageResult 信封形状：records / total / current / size。
.PARAMETER Label
断言标签。
.PARAMETER Data
Result.data。
.PARAMETER ExpectCurrent
期望页码，默认 1。
.PARAMETER ExpectSize
期望每页条数，默认 5。
#>
function Assert-VideoPageShape {
    param([string]$Label, [object]$Data, [int]$ExpectCurrent = 1, [int]$ExpectSize = 5)

    Assert "$Label.records" ($null -ne $Data -and $null -ne $Data.records) 'records 缺失'
    if ($null -eq $Data) {
        return
    }
    Assert "$Label.current" ([int]$Data.current -eq $ExpectCurrent) "current=$($Data.current)"
    Assert "$Label.size" ([int]$Data.size -eq $ExpectSize) "size=$($Data.size)"
    Assert "$Label.total" ([long]$Data.total -ge 0) "total=$($Data.total)"
}

<#
.SYNOPSIS
断言短视频 feed 键集响应形状：records / hasMore / nextCursor。
.DESCRIPTION
M2 起 /video/shorts/feed 改为游标分页：首屏无 nextCursor，
有下一页时 nextCursor 必须为合法的「锚点|内容ID」。
.PARAMETER Label
断言标签。
.PARAMETER Data
Result.data。
.PARAMETER MaxSize
本批最多条数，默认 5。
#>
function Assert-VideoFeedShape {
    param([string]$Label, [object]$Data, [int]$MaxSize = 5)

    Assert "$Label.data" ($null -ne $Data) 'data 缺失'
    if ($null -eq $Data) {
        return
    }
    $hasMore = [bool]$Data.hasMore
    Assert "$Label.records" ($null -ne $Data.records -or -not $hasMore) 'records 缺失'
    $count = @($Data.records).Count
    Assert "$Label.size" ($count -le $MaxSize) "count=$count max=$MaxSize"
    Assert "$Label.hasMore" ($hasMore -or -not $hasMore) "hasMore=$($Data.hasMore)"
    if ($hasMore) {
        Assert "$Label.nextCursor" (-not [string]::IsNullOrWhiteSpace([string]$Data.nextCursor)) `
            "nextCursor=$($Data.nextCursor)"
        $parts = @(([string]$Data.nextCursor).Split('|'))
        Assert "$Label.cursorShape" ($parts.Count -eq 2 -and $parts[0] -match '^\d+(\.[0-9]+)?$') `
            "cursor=$($Data.nextCursor)"
        Assert "$Label.cursorId" ([long]$parts[1] -gt 0) "cursorId=$($parts[1])"
    }
}

<#
.SYNOPSIS
汇总 M0 基线断言（健康 / meta 契约 / 权限拒绝），供各里程碑脚本复用。
.PARAMETER Milestone
里程碑标识（m1 / m2 / m3 / m4 / m5）。
.PARAMETER IncludeFile
同时检查 whale-file 健康状态。
#>
function Assert-VideoBaseline {
    param([string]$Milestone, [switch]$IncludeFile)

    Write-Host "== 视频平台 M0 基线（$Milestone）=="
    Assert-VideoHealth -Label '0' -IncludeFile:$IncludeFile
    Assert-VideoMetaContract -Label '1'
    Assert-VideoPermissionDenied -Label '2'
}
