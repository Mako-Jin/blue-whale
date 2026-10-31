# 冒烟脚本公共工具（审计 P2）
#
# 背景：scripts/smoke 下 32 个脚本各自复制 Assert / Check / Show / Invoke-Api 等
# 辅助函数（Assert 多达 18 份、Invoke-Api 7 份），修一处要改十几份。
# 现统一收口到本文件，脚本用 `. "$PSScriptRoot/lib/common.ps1"` 引入。
#
# 失败语义兼容历史三派写法（不做行为变更）：
#   - throw 型：脚本原先 Assert 失败即抛 → 引入后设 $script:SmokeAssertThrow = $true
#   - 累积型：累加 $script:failures，脚本末尾按失败数退出 → 默认行为
#   - 标记型：只置 $script:failed = $true → 默认行为同时会置位

if (-not (Get-Variable -Name SmokeAssertThrow -Scope Script -ErrorAction SilentlyContinue)) {
    $script:SmokeAssertThrow = $false
}

<#
.SYNOPSIS
获取冒烟脚本的管理员登录凭据（T-03：仓库不再硬编码默认口令）。
.DESCRIPTION
用户名默认取 SMOKE_USERNAME（缺省 admin）；口令必须来自环境变量 SMOKE_PASSWORD，
缺失即抛出明确提示。口令来源：auth 服务启动日志的 admin 初始随机口令（轮换器
AdminPasswordInitializer 打印一次），或首启前设置 WHALE_AUTH_ADMIN_INIT_PASSWORD。
.OUTPUTS
Hashtable：@{ Username; Password; JsonBody }，JsonBody 为登录请求 JSON 体。
#>
function Get-SmokeAdminCredential {
    $username = if ($env:SMOKE_USERNAME) { $env:SMOKE_USERNAME } else { 'admin' }
    if (-not $env:SMOKE_PASSWORD) {
        throw '仓库不再硬编码默认口令：请设置 SMOKE_PASSWORD（admin 初始随机口令见 auth 服务启动日志，或首启前设 WHALE_AUTH_ADMIN_INIT_PASSWORD）'
    }
    # JSON 转义：口令由操作者提供，避免反斜杠与引号破坏请求体
    $escapedPassword = $env:SMOKE_PASSWORD.Replace('\', '\\').Replace('"', '\"')
    return @{
        Username = $username
        Password = $env:SMOKE_PASSWORD
        JsonBody = '{"username":"' + $username + '","password":"' + $escapedPassword + '"}'
    }
}

<#
.SYNOPSIS
断言：条件不成立时记录失败并输出 FAIL。
.DESCRIPTION
累积模式下不中断脚本（供末尾统一汇总）；$script:SmokeAssertThrow 为 $true 时立即抛出。
#>
function Assert($label, $cond, $detail = '') {
    if ($cond) {
        Write-Host "OK   $label $detail"
        return
    }
    $script:failures = 1 + [int]$script:failures
    $script:failed = $true
    Write-Host "FAIL $label $detail"
    if ($script:SmokeAssertThrow) {
        throw "ASSERT FAIL: $label $detail"
    }
}

<#
.SYNOPSIS
校验后端 Result 信封的业务码，不符即抛。
#>
function Check([string]$name, $result, [int]$expectCode = 0) {
    if ($result.code -ne $expectCode) {
        throw "$name 失败: code=$($result.code) message=$($result.message)"
    }
    Write-Host "OK   $name"
}

<#
.SYNOPSIS
打印对象 JSON 便于人工核对。
#>
function Show($label, $obj) {
    Write-Host "== $label =="
    Write-Host ($obj | ConvertTo-Json -Depth 8 -Compress)
    Write-Host ""
}

<#
.SYNOPSIS
调用网关 API，自动带上 Bearer 令牌与 UTF-8 JSON 体。
.PARAMETER BaseUri
网关地址；默认取脚本作用域的 $gateway（各脚本既有约定）。
.PARAMETER Token
令牌；默认取 $script:token。
#>
function Invoke-SmokeApi {
    param(
        [string]$Method = 'GET',
        [string]$Path,
        [object]$Body = $null,
        [string]$BaseUri = $script:gateway,
        [string]$Token = $script:token,
        [hashtable]$Headers = $null
    )

    if (-not $Headers) {
        $Headers = @{}
    }
    if ($Token) {
        $Headers['Authorization'] = "Bearer $Token"
    }

    $params = @{
        Method  = $Method
        Uri     = ($BaseUri.TrimEnd('/') + $Path)
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 20 -Compress
        $params['ContentType'] = 'application/json;charset=utf-8'
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}

<#
.SYNOPSIS
按累积失败数退出（0 通过 / 1 失败），供脚本末尾统一收口。
#>
function Exit-Smoke {
    param([int]$Failures = $script:failures)

    if ([int]$Failures -gt 0) {
        Write-Host "SMOKE FAILED: $Failures 项失败"
        exit 1
    }
    Write-Host "SMOKE PASSED"
    exit 0
}

<#
.SYNOPSIS
调用网关 API（收口各脚本各自的本地 Invoke-Api 实现）。
.DESCRIPTION
- Headers 为空时用 $script:h（脚本登录后写入的 @{ Authorization = "Bearer <token>" }）。
- Body 非空时序列化为 JSON 作为请求体，ContentType 统一为 'application/json'。
- 序列化深度取 30：兼容 doc 页面 contentJson 等深层嵌套请求体（历史脚本用 20/30 两派，
  统一到较深的 30 不改变任何请求内容）。
.PARAMETER Method
HTTP 方法，默认 GET；与既有调用 `Invoke-Api Get/Put/Post/Delete <uri> <body>` 的位置参数一致。
.PARAMETER Headers
显式请求头（如 m2b/m5 以普通用户身份访问时传入的 $readerHeaders）；为空则回落到 $script:h。
#>
function Invoke-Api {
    param(
        [string]$Method = 'GET',
        [string]$Uri,
        [object]$Body = $null,
        [hashtable]$Headers = $null
    )

    if (-not $Headers) {
        $Headers = $script:h
    }
    $params = @{ Method = $Method; Uri = $Uri }
    if ($Headers) {
        $params.Headers = $Headers
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 30 -Compress
        $params.ContentType = 'application/json;charset=utf-8'
        $params.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
    }
    return Invoke-RestMethod @params
}
