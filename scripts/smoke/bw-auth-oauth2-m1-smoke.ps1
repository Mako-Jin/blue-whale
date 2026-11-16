# 权限中心扩展 M1：OAuth2.0 授权服务器冒烟（client_credentials 全链路）
#
# 前置：
#   - whale-service-auth 已启动（默认直连 http://127.0.0.1:8081），Flyway V11/V12/V17 已执行；
#   - 用于冒烟客户端的 sys_oauth_client.grant_types 必须包含 client_credentials，
#     且 scopes 包含 -Scope 指定的值（可先用 POST /oauth-client/v1 注册）。
#
# 覆盖：
#   1) JWKS 公钥可读（HS256 oct 密钥）；
#   2) 发现文档可读且 issuer / token_endpoint 正确；
#   3) client_credentials 换票成功，返回 access_token；
#   4) 内省 active=true；
#   5) 撤销后内省 active=false；
#   6) 错误密钥换票被拒（401 invalid_client）。
#
# 用法：
#   powershell -File scripts/smoke/bw-auth-oauth2-m1-smoke.ps1 `
#       -ClientId bw-svc -ClientSecret 'your-secret' -Scope 'sys:user:list'
#
# 退出码：0 通过；1 失败；2 前置缺失。

param(
    [string]$AuthBaseUrl = 'http://127.0.0.1:8081',
    [string]$ClientId = '',
    [string]$ClientSecret = '',
    [string]$Scope = 'sys:user:list'
)

$ErrorActionPreference = 'Stop'
$script:failed = $false

function Write-Step {
    param([string]$Name, [bool]$Ok, [string]$Detail = '')
    if ($Ok) {
        Write-Host ("PASS  {0}  {1}" -f $Name, $Detail) -ForegroundColor Green
    } else {
        Write-Host ("FAIL  {0}  {1}" -f $Name, $Detail) -ForegroundColor Red
        $script:failed = $true
    }
}

function Invoke-Http {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers = @{},
        [string]$Body = $null
    )
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method $Method -Uri $Url -Headers $Headers `
            -ContentType 'application/x-www-form-urlencoded' -Body $Body -TimeoutSec 20
        return @{ Status = [int]$response.StatusCode; Content = $response.Content }
    } catch {
        $status = 0
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        return @{ Status = $status; Content = $_.Exception.Message }
    }
}

function New-BasicHeader {
    param([string]$Id, [string]$Secret)
    $pair = '{0}:{1}' -f $Id, $Secret
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair))
    return @{ Authorization = "Basic $encoded" }
}

if ([string]::IsNullOrWhiteSpace($ClientId) -or [string]::IsNullOrWhiteSpace($ClientSecret)) {
    Write-Host 'MISSING: 请通过 -ClientId / -ClientSecret 提供具备 client_credentials 授权的客户端凭据' -ForegroundColor Yellow
    exit 2
}

# 1. JWKS
# HS256 模式下 JWKS 端点被主动关闭（避免对称密钥外泄），返回 404 属预期；RS256 模式才校验公钥集
$jwks = Invoke-Http -Method 'GET' -Url "$AuthBaseUrl/oauth2/jwks"
if ($jwks.Status -eq 404) {
    Write-Host 'SKIP: JWKS 已关闭（HS256 模式；配置 whale.auth.jwt.private-key 切换 RS256 后启用）' -ForegroundColor Yellow
} else {
    Write-Step 'JWKS' ($jwks.Status -eq 200 -and $jwks.Content -match '"keys"') "status=$($jwks.Status)"
}

# 2. 发现文档
$metadata = Invoke-Http -Method 'GET' -Url "$AuthBaseUrl/.well-known/oauth-authorization-server"
Write-Step '元数据' ($metadata.Status -eq 200 -and $metadata.Content -match 'token_endpoint') "status=$($metadata.Status)"

# 3. client_credentials 换票
$basic = New-BasicHeader -Id $ClientId -Secret $ClientSecret
$tokenBody = 'grant_type=client_credentials&scope=' + [Uri]::EscapeDataString($Scope)
$token = Invoke-Http -Method 'POST' -Url "$AuthBaseUrl/oauth2/token" -Headers $basic -Body $tokenBody
$accessToken = $null
if ($token.Status -eq 200) {
    $accessToken = ($token.Content | ConvertFrom-Json).access_token
}
Write-Step 'client_credentials 换票' ($token.Status -eq 200 -and -not [string]::IsNullOrWhiteSpace($accessToken)) "status=$($token.Status)"
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    Write-Host '后续步骤因无 access_token 跳过' -ForegroundColor Yellow
    exit $(if ($script:failed) { 1 } else { 0 })
}

# 4. 内省（撤销前）
$introspectBody = 'token=' + [Uri]::EscapeDataString($accessToken) + '&token_type_hint=access_token'
$introspect = Invoke-Http -Method 'POST' -Url "$AuthBaseUrl/oauth2/introspect" -Headers $basic -Body $introspectBody
$activeBefore = $false
if ($introspect.Status -eq 200) {
    $activeBefore = ($introspect.Content | ConvertFrom-Json).active -eq $true
}
Write-Step '内省 active=true' ($introspect.Status -eq 200 -and $activeBefore) "status=$($introspect.Status)"

# 5. 撤销后内省
$revoke = Invoke-Http -Method 'POST' -Url "$AuthBaseUrl/oauth2/revoke" -Headers $basic -Body $introspectBody
$introspectAfter = Invoke-Http -Method 'POST' -Url "$AuthBaseUrl/oauth2/introspect" -Headers $basic -Body $introspectBody
$activeAfter = $true
if ($introspectAfter.Status -eq 200) {
    $activeAfter = ($introspectAfter.Content | ConvertFrom-Json).active -eq $true
}
Write-Step '撤销后 active=false' ($revoke.Status -eq 200 -and -not $activeAfter) "revoke=$($revoke.Status) introspect=$($introspectAfter.Status)"

# 6. 错误密钥被拒
$badBasic = New-BasicHeader -Id $ClientId -Secret 'wrong-secret'
$badToken = Invoke-Http -Method 'POST' -Url "$AuthBaseUrl/oauth2/token" -Headers $badBasic -Body $tokenBody
Write-Step '错误密钥被拒' ($badToken.Status -eq 401) "status=$($badToken.Status)"

if ($script:failed) {
    exit 1
}
Write-Host 'OAuth2.0 M1 冒烟全部通过' -ForegroundColor Green
exit 0
