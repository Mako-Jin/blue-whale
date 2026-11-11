# P0 外部接入预检：合同电子签渠道 / CA / TSA / 实名意愿 / 告警短信 / 邮件。
#
# 目标：在拿到供应商沙箱或正式凭据后，先确认环境变量、证书文件、回调地址与网络可达性，
#       避免把“缺凭据”的失败混进业务冒烟。脚本只输出变量是否设置，绝不回显密钥、口令或手机号。
#
# 用法（PowerShell 7）：
#   pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1
#   pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1 -EnvFile .env.external
#   pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1 -SkipNetwork
#   pwsh -NoProfile -File scripts/smoke/bw-external-integration-preflight.ps1 -Json
#
# 退出码：0=全部就绪（允许 WARN）；2=存在 BLOCKED。SKIP 表示本轮不需要该项。

[CmdletBinding()]
param(
    [string]$EnvFile,
    [switch]$SkipNetwork,
    [switch]$Json
)

$ErrorActionPreference = 'Stop'
$script:Quiet = [bool]$Json
$script:Results = [System.Collections.Generic.List[object]]::new()

function Add-Result {
    param(
        [string]$Area,
        [string]$Item,
        [ValidateSet('READY', 'WARN', 'BLOCKED', 'SKIP')]
        [string]$Status,
        [string]$Detail
    )
    $script:Results.Add([pscustomobject]@{
            Area   = $Area
            Item   = $Item
            Status = $Status
            Detail = $Detail
        })
    if (-not $script:Quiet) {
        Write-Host ("{0,-7} [{1}] {2}: {3}" -f $Status, $Area, $Item, $Detail)
    }
}

function Import-EnvFile {
    param([string]$Path)
    $full = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Add-Result '环境' 'EnvFile' 'BLOCKED' "文件不存在: $full"
        return
    }
    $count = 0
    foreach ($line in Get-Content -LiteralPath $full) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith('#')) {
            continue
        }
        if ($trimmed.StartsWith('export ')) {
            $trimmed = $trimmed.Substring(7).Trim()
        }
        $index = $trimmed.IndexOf('=')
        if ($index -le 0) {
            continue
        }
        $name = $trimmed.Substring(0, $index).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            continue
        }
        $value = $trimmed.Substring($index + 1).Trim()
        if ($value.Length -ge 2) {
            $quoted = ($value.StartsWith('"') -and $value.EndsWith('"')) `
                -or ($value.StartsWith("'") -and $value.EndsWith("'"))
            if ($quoted) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }
        [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        $count++
    }
    Add-Result '环境' 'EnvFile' 'READY' "已加载 $count 项（值已隐藏）"
}

function Test-EnvPresent {
    param(
        [string]$Area,
        [string]$Name,
        [bool]$Required = $true
    )
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($value)) {
        if ($Required) {
            Add-Result $Area $Name 'BLOCKED' '未设置'
        } else {
            Add-Result $Area $Name 'SKIP' '未设置（可选）'
        }
        return $null
    }
    Add-Result $Area $Name 'READY' '已设置（值已隐藏）'
    return $value.Trim()
}

function Test-HttpUrl {
    param(
        [string]$Area,
        [string]$Item,
        [string]$Value,
        [bool]$Required = $true
    )
    if ([string]::IsNullOrWhiteSpace($Value)) {
        if ($Required) {
            Add-Result $Area $Item 'BLOCKED' '地址为空'
        } else {
            Add-Result $Area $Item 'SKIP' '未配置（可选）'
        }
        return $null
    }
    $uri = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$uri)) {
        Add-Result $Area $Item 'BLOCKED' "不是绝对 URL: $Value"
        return $null
    }
    if ($uri.Scheme -notin @('http', 'https')) {
        Add-Result $Area $Item 'BLOCKED' "协议必须为 http/https: $($uri.Scheme)"
        return $null
    }
    Add-Result $Area $Item 'READY' "$($uri.Scheme)://$($uri.Host):$($uri.Port)"
    return $uri
}

function Test-PublicHttpsCallback {
    param(
        [string]$Area,
        [string]$Item,
        [string]$Value
    )
    $uri = Test-HttpUrl $Area $Item $Value $true
    if ($null -eq $uri) {
        return
    }
    if ($uri.Scheme -ne 'https') {
        Add-Result $Area $Item 'BLOCKED' '回调地址必须为 https'
        return
    }
    $hostName = $uri.Host.ToLowerInvariant()
    $privateHost = $hostName -in @('localhost', '127.0.0.1', '::1') `
        -or $hostName.EndsWith('.local') `
        -or $hostName -match '^10\.' `
        -or $hostName -match '^192\.168\.' `
        -or $hostName -match '^172\.(1[6-9]|2[0-9]|3[0-1])\.'
    if ($privateHost) {
        Add-Result $Area $Item 'BLOCKED' '回调地址不能是 localhost/内网地址，供应商无法访问'
        return
    }
    Add-Result $Area $Item 'READY' '公网 https 回调地址格式正确'
}

function Test-TcpReachable {
    param(
        [string]$Area,
        [string]$Item,
        [Uri]$Uri
    )
    if ($null -eq $Uri) {
        return
    }
    if ($SkipNetwork) {
        Add-Result $Area $Item 'SKIP' '已指定 -SkipNetwork，未做 TCP 可达性检查'
        return
    }
    $port = if ($Uri.IsDefaultPort) { if ($Uri.Scheme -eq 'https') { 443 } else { 80 } } else { $Uri.Port }
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($Uri.Host, $port)
        if (-not $task.Wait([TimeSpan]::FromSeconds(5))) {
            Add-Result $Area $Item 'BLOCKED' "TCP 连接超时: $($Uri.Host):$port"
            return
        }
        Add-Result $Area $Item 'READY' "TCP 可达: $($Uri.Host):$port"
    }
    catch {
        Add-Result $Area $Item 'BLOCKED' "TCP 连接失败: $($Uri.Host):$port（$($_.Exception.Message)）"
    }
    finally {
        $client.Dispose()
    }
}

function Test-CaTrustRoot {
    $area = '合同 CA'
    $path = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_CA_CERT_PATH')
    if ([string]::IsNullOrWhiteSpace($path)) {
        Add-Result $area 'CA_CERT_PATH' 'BLOCKED' '未配置持牌 CA 信任根 PEM'
        return
    }
    $full = [System.IO.Path]::GetFullPath($path.Trim())
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Add-Result $area 'CA_CERT_PATH' 'BLOCKED' "文件不存在: $full"
        return
    }
    try {
        $certs = [System.Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
        $certs.ImportFromPemFile($full)
        if ($certs.Count -eq 0) {
            Add-Result $area 'CA_CERT_PATH' 'BLOCKED' 'PEM 中未解析到证书'
            return
        }
        $expired = @($certs | Where-Object { $_.NotAfter -le (Get-Date) })
        if ($expired.Count -gt 0) {
            Add-Result $area 'CA_CERT_PATH' 'BLOCKED' "有 $($expired.Count) 张证书已过期"
            return
        }
        Add-Result $area 'CA_CERT_PATH' 'READY' "解析到 $($certs.Count) 张证书，均未过期"
    }
    catch {
        Add-Result $area 'CA_CERT_PATH' 'BLOCKED' "PEM 解析失败: $($_.Exception.Message)"
    }
    $crl = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_CA_CRL_PATH')
    if ([string]::IsNullOrWhiteSpace($crl)) {
        Add-Result $area 'CA_CRL_PATH' 'WARN' '未配置 CRL/OCSP，生产建议提供吊销数据'
        return
    }
    $crlFull = [System.IO.Path]::GetFullPath($crl.Trim())
    if (-not (Test-Path -LiteralPath $crlFull -PathType Leaf)) {
        Add-Result $area 'CA_CRL_PATH' 'BLOCKED' "文件不存在: $crlFull"
        return
    }
    if ((Get-Item -LiteralPath $crlFull).Length -le 0) {
        Add-Result $area 'CA_CRL_PATH' 'BLOCKED' 'CRL 文件为空'
        return
    }
    Add-Result $area 'CA_CRL_PATH' 'READY' 'CRL 文件存在且非空'
}

function Test-P12Certificate {
    $area = '合同 P12'
    $path = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_P12_PATH')
    if ([string]::IsNullOrWhiteSpace($path)) {
        Add-Result $area 'P12_PATH' 'SKIP' '未配置（三方渠道由供应商签署，可选）'
        return
    }
    $full = [System.IO.Path]::GetFullPath($path.Trim())
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        Add-Result $area 'P12_PATH' 'BLOCKED' "文件不存在: $full"
        return
    }
    $password = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_P12_PASSWORD')
    if ([string]::IsNullOrWhiteSpace($password)) {
        Add-Result $area 'P12_PASSWORD' 'BLOCKED' '未设置 PKCS#12 口令'
        return
    }
    try {
        $cert = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new(
            $full,
            $password,
            [System.Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet
        )
        if (-not $cert.HasPrivateKey) {
            Add-Result $area 'P12_PATH' 'BLOCKED' 'PKCS#12 中未找到私钥'
            return
        }
        if ($cert.NotAfter -le (Get-Date)) {
            Add-Result $area 'P12_PATH' 'BLOCKED' "证书已过期: $($cert.NotAfter.ToString('yyyy-MM-dd'))"
            return
        }
        Add-Result $area 'P12_PATH' 'READY' "证书可加载且含私钥，有效期至 $($cert.NotAfter.ToString('yyyy-MM-dd'))"
    }
    catch {
        Add-Result $area 'P12_PATH' 'BLOCKED' "PKCS#12 加载失败（口令错误或文件损坏）: $($_.Exception.Message)"
    }
}

function Test-Tsa {
    $area = '合同 TSA'
    $required = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_TSA_REQUIRED')
    $url = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_TSA_URL')
    if ([string]::IsNullOrWhiteSpace($url)) {
        if ($required -eq 'true') {
            Add-Result $area 'TSA_URL' 'BLOCKED' 'TSA_REQUIRED=true 但未配置 TSA 地址'
        } else {
            Add-Result $area 'TSA_URL' 'BLOCKED' '未配置 RFC 3161 时间戳服务，生产合规不允许无时间戳签署'
        }
        return
    }
    $uri = Test-HttpUrl $area 'TSA_URL' $url $true
    Test-TcpReachable $area 'TSA_URL' $uri
    $user = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_TSA_USERNAME')
    $password = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_TSA_PASSWORD')
    if ([string]::IsNullOrWhiteSpace($user) -ne [string]::IsNullOrWhiteSpace($password)) {
        Add-Result $area 'TSA_AUTH' 'BLOCKED' 'TSA 用户名与口令必须同时配置'
    } elseif ([string]::IsNullOrWhiteSpace($user)) {
        Add-Result $area 'TSA_AUTH' 'WARN' '未配置 TSA Basic 认证（若供应商要求则补齐）'
    } else {
        Add-Result $area 'TSA_AUTH' 'READY' '已配置 TSA Basic 认证（值已隐藏）'
    }
}

if (-not [string]::IsNullOrWhiteSpace($EnvFile)) {
    Import-EnvFile $EnvFile
}

# ==================== 1. 合同电子签渠道 ====================
$provider = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_PROVIDER')
$providerArea = '合同渠道'
if ([string]::IsNullOrWhiteSpace($provider) -or $provider.Trim().ToLowerInvariant() -eq 'local') {
    Add-Result $providerArea 'CONTRACT_ESIGN_PROVIDER' 'BLOCKED' '仍为空或 local；非 dev 环境必须切换 esignbao/fadada/ssq'
} else {
    $providerName = $provider.Trim().ToLowerInvariant()
    Add-Result $providerArea 'CONTRACT_ESIGN_PROVIDER' 'READY' "已选择 $providerName"
    $prefix = 'CONTRACT_ESIGN_' + $providerName.ToUpperInvariant()
    $baseUrl = Test-EnvPresent $providerArea "${prefix}_BASE_URL"
    $null = Test-EnvPresent $providerArea "${prefix}_APP_ID"
    $null = Test-EnvPresent $providerArea "${prefix}_APP_SECRET"
    $notifyUrl = [Environment]::GetEnvironmentVariable("${prefix}_NOTIFY_URL")
    Test-PublicHttpsCallback $providerArea "${prefix}_NOTIFY_URL" $notifyUrl
    if (-not [string]::IsNullOrWhiteSpace($baseUrl)) {
        $baseUri = Test-HttpUrl $providerArea "${prefix}_BASE_URL" $baseUrl $true
        Test-TcpReachable $providerArea "${prefix}_BASE_URL" $baseUri
    }
    $signUrl = [Environment]::GetEnvironmentVariable("${prefix}_SIGN_URL")
    if (-not [string]::IsNullOrWhiteSpace($signUrl)) {
        $null = Test-HttpUrl $providerArea "${prefix}_SIGN_URL" $signUrl $false
    }
}

Test-CaTrustRoot
Test-P12Certificate
Test-Tsa

$callbackSecret = [Environment]::GetEnvironmentVariable('CONTRACT_ESIGN_CALLBACK_SECRET')
if ([string]::IsNullOrWhiteSpace($callbackSecret) -or $callbackSecret -eq 'bluewhale-esign-dev-callback-secret') {
    Add-Result '合同回调' 'CALLBACK_SECRET' 'BLOCKED' '仍为空或默认开发值'
} else {
    Add-Result '合同回调' 'CALLBACK_SECRET' 'READY' '已设置（值已隐藏）'
}
Add-Result '合同实名' 'REAL_NAME_PROVIDER' 'BLOCKED' '仓库尚无供应商实名协议；需接口文档、回调证据字段与测试账号'
Add-Result '合同实名' 'WILLINGNESS_PROVIDER' 'BLOCKED' '仓库尚无供应商意愿认证协议；需确认 SMS/FACE/VIDEO 方式与证据编号规则'

# ==================== 2. 告警短信网关 ====================
$smsArea = '告警短信'
$smsEnabled = [Environment]::GetEnvironmentVariable('ALERT_SMS_ENABLED')
if ($smsEnabled -ne 'true') {
    Add-Result $smsArea 'ALERT_SMS_ENABLED' 'WARN' '未启用；F-03b 仍不能闭环'
} else {
    Add-Result $smsArea 'ALERT_SMS_ENABLED' 'READY' '已启用'
    $smsUrl = Test-EnvPresent $smsArea 'ALERT_SMS_URL'
    $null = Test-EnvPresent $smsArea 'ALERT_SMS_TOKEN'
    $null = Test-EnvPresent $smsArea 'ALERT_SMS_SIGN_NAME'
    $null = Test-EnvPresent $smsArea 'ALERT_SMS_TEMPLATE_CODE'
    if (-not [string]::IsNullOrWhiteSpace($smsUrl)) {
        $smsUri = Test-HttpUrl $smsArea 'ALERT_SMS_URL' $smsUrl $true
        Test-TcpReachable $smsArea 'ALERT_SMS_URL' $smsUri
    }
    $phones = [Environment]::GetEnvironmentVariable('ALERT_SMS_TO')
    if ([string]::IsNullOrWhiteSpace($phones)) {
        Add-Result $smsArea 'ALERT_SMS_TO' 'BLOCKED' '未配置接收手机号'
    } else {
        $invalid = @($phones -split '[,;]' | ForEach-Object { $_.Trim() } |
                Where-Object { $_ -notmatch '^1[3-9]\d{9}$' })
        if ($invalid.Count -gt 0) {
            Add-Result $smsArea 'ALERT_SMS_TO' 'BLOCKED' "有 $($invalid.Count) 个号码格式不正确"
        } else {
            Add-Result $smsArea 'ALERT_SMS_TO' 'READY' '手机号格式校验通过（值已隐藏）'
        }
    }
}

# ==================== 3. 告警邮件 ====================
$mailArea = '告警邮件'
$emailEnabled = [Environment]::GetEnvironmentVariable('ALERT_EMAIL_ENABLED')
if ($emailEnabled -ne 'true') {
    Add-Result $mailArea 'ALERT_EMAIL_ENABLED' 'WARN' '未启用；V-06 至少需要邮件或短信一个真实通道'
} else {
    Add-Result $mailArea 'ALERT_EMAIL_ENABLED' 'READY' '已启用'
    $mailHost = Test-EnvPresent $mailArea 'SPRING_MAIL_HOST'
    $null = Test-EnvPresent $mailArea 'SPRING_MAIL_USERNAME'
    $null = Test-EnvPresent $mailArea 'SPRING_MAIL_PASSWORD'
    $null = Test-EnvPresent $mailArea 'ALERT_EMAIL_FROM'
    $null = Test-EnvPresent $mailArea 'ALERT_EMAIL_TO'
    if (-not [string]::IsNullOrWhiteSpace($mailHost)) {
        $mailPort = [Environment]::GetEnvironmentVariable('SPRING_MAIL_PORT')
        if ([string]::IsNullOrWhiteSpace($mailPort)) { $mailPort = '587' }
        if ($SkipNetwork) {
            Add-Result $mailArea 'SMTP' 'SKIP' '已指定 -SkipNetwork，未做 TCP 可达性检查'
        } else {
            $client = [System.Net.Sockets.TcpClient]::new()
            try {
                $task = $client.ConnectAsync($mailHost, [int]$mailPort)
                if (-not $task.Wait([TimeSpan]::FromSeconds(5))) {
                    Add-Result $mailArea 'SMTP' 'BLOCKED' "TCP 连接超时: $mailHost`:$mailPort"
                } else {
                    Add-Result $mailArea 'SMTP' 'READY' "TCP 可达: $mailHost`:$mailPort"
                }
            }
            catch {
                Add-Result $mailArea 'SMTP' 'BLOCKED' "TCP 连接失败: $mailHost`:$mailPort（$($_.Exception.Message)）"
            }
            finally {
                $client.Dispose()
            }
        }
    }
}

# ==================== 4. Alertmanager / 出站 Webhook ====================
$alertArea = '告警链路'
$alertToken = [Environment]::GetEnvironmentVariable('ALERT_WEBHOOK_TOKEN')
if ([string]::IsNullOrWhiteSpace($alertToken) -or $alertToken -eq 'bluewhale-alert-token') {
    Add-Result $alertArea 'ALERT_WEBHOOK_TOKEN' 'BLOCKED' '仍为空或默认值；Prometheus/Alertmanager 入站不可上线'
} else {
    Add-Result $alertArea 'ALERT_WEBHOOK_TOKEN' 'READY' '已设置（值已隐藏）'
}
if ([Environment]::GetEnvironmentVariable('ALERT_WEBHOOK_ENABLED') -eq 'true') {
    $webhookUrl = Test-EnvPresent $alertArea 'ALERT_WEBHOOK_URL'
    if (-not [string]::IsNullOrWhiteSpace($webhookUrl)) {
        $webhookUri = Test-HttpUrl $alertArea 'ALERT_WEBHOOK_URL' $webhookUrl $true
        Test-TcpReachable $alertArea 'ALERT_WEBHOOK_URL' $webhookUri
    }
}

# ==================== 汇总 ====================
$blocked = @($script:Results | Where-Object { $_.Status -eq 'BLOCKED' })
$warnings = @($script:Results | Where-Object { $_.Status -eq 'WARN' })
$ready = @($script:Results | Where-Object { $_.Status -eq 'READY' })

if ($Json) {
    [pscustomobject]@{
        generatedAt = (Get-Date).ToString('o')
        skipNetwork = [bool]$SkipNetwork
        summary     = @{ ready = $ready.Count; warn = $warnings.Count; blocked = $blocked.Count }
        results     = $script:Results
    } | ConvertTo-Json -Depth 5
} else {
    Write-Host ''
    Write-Host ("SUMMARY ready={0} warn={1} blocked={2}" -f $ready.Count, $warnings.Count, $blocked.Count)
    if ($blocked.Count -gt 0) {
        Write-Host '未就绪项：'
        $blocked | ForEach-Object { Write-Host ("  - [{0}] {1}: {2}" -f $_.Area, $_.Item, $_.Detail) }
    }
}

if ($blocked.Count -gt 0) {
    exit 2
}
exit 0
