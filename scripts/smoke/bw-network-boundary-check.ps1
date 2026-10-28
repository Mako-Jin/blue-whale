# S-06 上线网络边界检查（部署侧必检）
#
# 目标：从办公网/公网视角确认业务端口未暴露，且网关会拒绝伪造身份头。
# 该脚本无法替代安全组/防火墙配置，只用于上线前后验收取证。
#
# 用法：
#   pwsh -NoProfile -File scripts/smoke/bw-network-boundary-check.ps1 `
#     -PublicHost app.example.com `
#     -GatewayBaseUrl https://api.example.com/api
#
# 参数说明：
#   -PublicHost      部署后的公网/办公网可达主机名或 IP（禁止 localhost/回环）
#   -BusinessPorts   预期不可达的后端业务端口；默认 8081-8094 / 9001-9006 / 9101-9103 / 9201
#   -GatewayBaseUrl  网关对外基址（含 /api 前缀）；提供后追加 /auth/me 做伪造头检查
#   -SkipCertificateCheck  自签证书环境使用（生产验收不要开启）
#
# 退出码：0 通过；1 存在 FAIL；2 参数/前置错误。

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$PublicHost,
    [int[]]$BusinessPorts = @(
        8081..8094
        9001..9006
        9101..9103
        9201
    ),
    [string]$GatewayBaseUrl,
    [int]$TimeoutMs = 3000,
    [switch]$SkipCertificateCheck
)

$ErrorActionPreference = 'Stop'
$script:failed = $false
$script:warned = $false

function Assert-Boundary {
    param(
        [string]$Label,
        [bool]$Condition,
        [string]$Detail = ''
    )
    if ($Condition) {
        Write-Host "OK   $Label $Detail"
        return
    }
    Write-Host "FAIL $Label $Detail"
    $script:failed = $true
}

function Test-TcpPort {
    param(
        [string]$TargetHost,
        [int]$Port,
        [int]$Timeout
    )
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($Timeout)) {
            return $false
        }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

$hostValue = $PublicHost.Trim()
if ($hostValue -in @('localhost', '127.0.0.1', '::1')) {
    Write-Host 'FAIL -PublicHost 不能是 localhost/回环地址；本检查必须从部署外部视角执行'
    exit 2
}

Write-Host "检查目标：$hostValue；业务端口 $($BusinessPorts -join ',')"
$openPorts = @()
foreach ($port in ($BusinessPorts | Sort-Object -Unique)) {
    if (Test-TcpPort -TargetHost $hostValue -Port $port -Timeout $TimeoutMs) {
        $openPorts += $port
    }
}
Assert-Boundary '业务端口均不可从外部直连' ($openPorts.Count -eq 0) `
    $(if ($openPorts.Count -eq 0) { '' } else { "reachable=$($openPorts -join ',')" })

if ([string]::IsNullOrWhiteSpace($GatewayBaseUrl)) {
    $gatewayReachable = Test-TcpPort -TargetHost $hostValue -Port 443 -Timeout $TimeoutMs
    Assert-Boundary '网关 443 可达' $gatewayReachable
    Write-Host 'SKIP 伪造身份头检查：未提供 -GatewayBaseUrl'
} else {
    $base = $GatewayBaseUrl.TrimEnd('/')
    try {
        $probe = Invoke-WebRequest -Uri "$base/auth/me" `
            -Headers @{
                'X-User-Id'   = '1'
                'X-Tenant-Id' = '1'
                'X-Roles'     = 'admin'
            } `
            -SkipHttpErrorCheck `
            -SkipCertificateCheck:$SkipCertificateCheck `
            -TimeoutSec 10
        $status = [int]$probe.StatusCode
        $body = [string]$probe.Content
        if ($status -eq 404) {
            Write-Host "WARN 网关伪造头检查无结论：$base/auth/me 返回 404，请按实际鉴权路径调整"
            $script:warned = $true
        } else {
            $accepted = ($status -eq 200 -and $body -match '"code"\s*:\s*0')
            Assert-Boundary '无令牌 + 伪造 X-User-Id/X-Tenant-Id/X-Roles 不得通过' `
                (-not $accepted) "status=$status"
        }
    } catch {
        Write-Host "FAIL 网关请求失败：$($_.Exception.Message)"
        $script:failed = $true
    }
}

if ($script:failed) {
    Write-Host 'RESULT: FAILED'
    exit 1
}
if ($script:warned) {
    Write-Host 'RESULT: PASSED (with warnings)'
    exit 0
}
Write-Host 'RESULT: PASSED'
exit 0
