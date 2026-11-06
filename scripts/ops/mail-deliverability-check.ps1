# ============================================================
# 邮件可送达性自检（DNS / PTR / 端口 / 证书）
#
# 用法：
#   powershell -File scripts/ops/mail-deliverability-check.ps1 -Domain example.com
#   powershell -File scripts/ops/mail-deliverability-check.ps1 -Domain example.com -MailHost mail.example.com
#   powershell -File scripts/ops/mail-deliverability-check.ps1 -Domain example.com -SkipPorts   # 只查 DNS
#
# 说明：
#   - 只读检查，不发送任何邮件、不修改任何记录。
#   - 邮件发出后进垃圾箱，九成原因集中在本脚本覆盖的 6 项里：
#     MX 缺失、SPF 多条/漏项、DKIM 未签名、DMARC 策略过严、
#     PTR 与 A 记录不一致、25 端口不通。
#   - 退出码：0 全部通过（允许 WARN）；1 存在 FAIL。
# ============================================================
param(
    [Parameter(Mandatory = $true)][string]$Domain,
    [string]$MailHost = '',
    [string]$DkimSelector = 'dkim',
    # 指定递归解析器（如 8.8.8.8）。留空用本机配置——注意本机若走内网 DNS，
    # 可能有缓存或分流，判断「公网是否可见」时建议显式指定公共 DNS 复核一次
    [string]$Server = '',
    [switch]$SkipPorts,
    [switch]$SkipPtr
)

$ErrorActionPreference = 'Stop'
$script:results = @()

function Add-Result {
    param([string]$Item, [string]$Status, [string]$Detail)
    $script:results += [pscustomobject]@{ Item = $Item; Status = $Status; Detail = $Detail }
}

function Query-Dns {
    param([string]$Name, [string]$Type)
    $params = @{ Name = $Name; Type = $Type; ErrorAction = 'Stop' }
    if ($Server) { $params['Server'] = $Server }
    try { return @(Resolve-DnsName @params) } catch { return @() }
}

function Get-TxtStrings {
    param([string]$Name)
    $records = Query-Dns -Name $Name -Type TXT
    if (-not $records) { return @() }
    # 长 TXT 会被拆成多段字符串，必须拼回一条再判断关键字
    return @($records | Where-Object { $_.Strings } | ForEach-Object { ($_.Strings -join '') })
}

function Test-Port {
    param([string]$Target, [int]$Port, [int]$TimeoutMs = 4000)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($Target, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($TimeoutMs)) { return $false }
        $client.EndConnect($iar)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

Write-Host "== 邮件可送达性自检：$Domain ==" -ForegroundColor Cyan

# ---- 1. MX ----------------------------------------------------
$mxRecords = Query-Dns -Name $Domain -Type MX
$mxRaw = @($mxRecords | Where-Object { $_.NameExchange } | Sort-Object Preference | ForEach-Object { $_.NameExchange.TrimEnd('.') })
# RFC 7505 的 null MX（"0 ."）表示该域明确声明不收邮件
$nullMx = @($mxRaw | Where-Object { $_ -eq '' })
$mxHosts = @($mxRaw | Where-Object { $_ -ne '' })
if ($mxRaw.Count -eq 0) {
    Add-Result 'MX' 'FAIL' "$Domain 无 MX 记录，外部邮件全部无法投递"
} elseif ($nullMx.Count -gt 0) {
    Add-Result 'MX' 'FAIL' "$Domain 设置了 null MX（0 .），明确声明不接收邮件；若本意是收信，必须删掉这条"
} else {
    Add-Result 'MX' 'PASS' ($mxHosts -join ', ')
    if (-not $MailHost) { $MailHost = $mxHosts[0] }
}
if (-not $MailHost) { $MailHost = "mail.$Domain" }
Write-Host "邮件主机推断为：$MailHost" -ForegroundColor DarkGray

# ---- 2. A 记录 ------------------------------------------------
$aRecords = Query-Dns -Name $MailHost -Type A
$ipv4 = @($aRecords | Where-Object { $_.IPAddress } | ForEach-Object { $_.IPAddress })
if ($ipv4.Count -eq 0) {
    Add-Result 'A 记录' 'FAIL' "$MailHost 无法解析 A 记录"
} else {
    Add-Result 'A 记录' 'PASS' ($ipv4 -join ', ')
}
$aaaaRecords = Query-Dns -Name $MailHost -Type AAAA
if (@($aaaaRecords | Where-Object { $_.IPAddress }).Count -gt 0) {
    Add-Result 'AAAA 记录' 'WARN' '存在 IPv6 记录；若主机未配 IPv6 默认路由会导致部分收件方连接超时，建议先删除'
}

# ---- 3. SPF ---------------------------------------------------
$spfAll = Get-TxtStrings -Name $Domain
$spf = @($spfAll | Where-Object { $_ -match '^v=spf1(\s|$)' })
if ($spf.Count -eq 0) {
    Add-Result 'SPF' 'FAIL' '无 v=spf1 记录，DMARC 对齐无依据，绝大多数收件方会降权'
} elseif ($spf.Count -gt 1) {
    Add-Result 'SPF' 'FAIL' "存在 $($spf.Count) 条 SPF 记录，RFC 7208 判定 permerror，必须合并为一条"
} elseif ($spf[0] -notmatch '(-all|~all)') {
    Add-Result 'SPF' 'WARN' "SPF 未以 -all/~all 收尾：$($spf[0])"
} else {
    Add-Result 'SPF' 'PASS' $spf[0]
}

# ---- 4. DKIM --------------------------------------------------
$dkimName = "$DkimSelector._domainkey.$Domain"
$dkimAll = Get-TxtStrings -Name $dkimName
$dkim = @($dkimAll | Where-Object { $_ -match 'v=DKIM1' -or $_ -match 'p=' })
if ($dkim.Count -eq 0) {
    Add-Result 'DKIM' 'FAIL' "$dkimName 无记录（selector 不对或未发布）。未签名邮件在 DMARC reject 下必被拒收"
} elseif ($dkim[0] -notmatch 'p=[A-Za-z0-9+/]{20,}') {
    Add-Result 'DKIM' 'FAIL' "$dkimName 的公钥 p= 为空或过短，等同于撤销"
} else {
    Add-Result 'DKIM' 'PASS' "selector=$DkimSelector，公钥已发布（$($dkim[0].Length) 字节）"
}

# ---- 5. DMARC -------------------------------------------------
$dmarcAll = Get-TxtStrings -Name "_dmarc.$Domain"
$dmarc = @($dmarcAll | Where-Object { $_ -match '^v=DMARC1' })
if ($dmarc.Count -eq 0) {
    Add-Result 'DMARC' 'WARN' '无 DMARC 记录；Gmail/Yahoo 对批量发信方要求必须有，缺失会直接影响投递'
} elseif ($dmarc.Count -gt 1) {
    Add-Result 'DMARC' 'FAIL' '存在多条 DMARC 记录，解析行为未定义'
} else {
    $policy = 'none'
    if ($dmarc[0] -match 'p\s*=\s*(none|quarantine|reject)') { $policy = $Matches[1] }
    if ($policy -eq 'none') {
        Add-Result 'DMARC' 'WARN' 'p=none 仅观察；验证通过后应推进到 quarantine → reject'
    } else {
        Add-Result 'DMARC' 'PASS' "p=$policy"
    }
    if ($dmarc[0] -notmatch 'rua=') {
        Add-Result 'DMARC 报告' 'WARN' '未配置 rua=，拿不到聚合报告就无法定位伪造来源'
    }
}

# ---- 6. PTR ---------------------------------------------------
if ($SkipPtr) {
    Add-Result 'PTR' 'SKIP' '已跳过'
} elseif ($ipv4.Count -eq 0) {
    Add-Result 'PTR' 'SKIP' '无 A 记录，无法反查'
} else {
    foreach ($ip in $ipv4) {
        $octets = $ip.Split('.')
        [array]::Reverse($octets)
        $arpa = ($octets -join '.') + '.in-addr.arpa'
        $ptrRecords = Query-Dns -Name $arpa -Type PTR
        $names = @($ptrRecords | Where-Object { $_.NameHost } | ForEach-Object { $_.NameHost.TrimEnd('.') })
        if ($names.Count -eq 0) {
            Add-Result 'PTR' 'FAIL' "$ip 无反解记录。反解需向云厂商申请，通常 1–3 个工作日"
        } elseif ($names -notcontains $MailHost) {
            Add-Result 'PTR' 'FAIL' "$ip 反解为 $($names -join ',')，与 $MailHost 不一致"
        } else {
            Add-Result 'PTR' 'PASS' "$ip → $MailHost"
        }
    }
}

# ---- 7. 端口 --------------------------------------------------
if ($SkipPorts) {
    Add-Result '端口' 'SKIP' '已跳过'
} else {
    $ports = @(
        @{ Port = 25;  Name = '入站 SMTP'; Required = $true },
        @{ Port = 465; Name = 'SMTPS';     Required = $true },
        @{ Port = 587; Name = '提交';       Required = $true },
        @{ Port = 993; Name = 'IMAPS';     Required = $true }
    )
    foreach ($p in $ports) {
        if (Test-Port -Target $MailHost -Port $p.Port) {
            Add-Result "端口 $($p.Port)" 'PASS' "$($p.Name) 可达"
        } elseif ($p.Required) {
            Add-Result "端口 $($p.Port)" 'FAIL' "$($p.Name) 不可达（安全组 / 云防火墙 / 服务未起）"
        } else {
            Add-Result "端口 $($p.Port)" 'WARN' "$($p.Name) 不可达"
        }
    }
}

# ---- 8. 证书 --------------------------------------------------
$certChecked = $false
foreach ($probePort in @(443, 993)) {
    if ($certChecked -or -not (Test-Port -Target $MailHost -Port $probePort)) { continue }
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect($MailHost, $probePort)
        # 先拿到证书再判断，避免证书过期时脚本直接抛异常而看不到剩余天数
        $callback = [System.Net.Security.RemoteCertificateValidationCallback] { param($s, $c, $ch, $e) $true }
        $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, $callback)
        $ssl.AuthenticateAsClient($MailHost)
        $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
        $daysLeft = [int]($cert.NotAfter - (Get-Date)).TotalDays
        $cn = $cert.GetNameInfo([System.Security.Cryptography.X509Certificates.X509NameType]::DnsName, $false)
        if ($cn -ne $MailHost) {
            Add-Result '证书' 'FAIL' "证书主题为 $cn，与 $MailHost 不匹配"
        } elseif ($daysLeft -lt 0) {
            Add-Result '证书' 'FAIL' "证书已过期 $([Math]::Abs($daysLeft)) 天"
        } elseif ($daysLeft -lt 15) {
            Add-Result '证书' 'WARN' "证书仅剩 $daysLeft 天，ACME 续期可能已失效"
        } else {
            Add-Result '证书' 'PASS' "$cn，剩余 $daysLeft 天"
        }
        $ssl.Dispose()
        $tcp.Close()
        $certChecked = $true
    } catch {
        Add-Result '证书' 'WARN' "无法在 $probePort 端口读取证书：$($_.Exception.Message)"
    }
}
if (-not $certChecked) { Add-Result '证书' 'SKIP' '443/993 均不可达，跳过' }

# ---- 输出 -----------------------------------------------------
# 不用 Format-Table：CJK 字符按 1 字符计算宽度、终端按 2 列显示，表格列会错位，
# 长行还会被截断成难以阅读的碎片。逐行着色输出更稳定。
$script:results | ForEach-Object {
    $color = switch ($_.Status) {
        'FAIL' { 'Red' }
        'WARN' { 'Yellow' }
        'PASS' { 'Green' }
        default { 'DarkGray' }
    }
    Write-Host ("  {0,-4}  {1}：{2}" -f $_.Status, $_.Item, $_.Detail) -ForegroundColor $color
}

$pass = @($script:results | Where-Object { $_.Status -eq 'PASS' }).Count
$fail = @($script:results | Where-Object { $_.Status -eq 'FAIL' })
$warn = @($script:results | Where-Object { $_.Status -eq 'WARN' })
Write-Host ''
Write-Host ("通过 {0} 项 / 告警 {1} 项 / 失败 {2} 项" -f $pass, $warn.Count, $fail.Count)

if ($fail.Count -gt 0) {
    Write-Host '存在阻断项，先修 FAIL 再谈发信量。' -ForegroundColor Red
    exit 1
}
if ($warn.Count -gt 0) {
    Write-Host '无阻断项，但告警项会影响长期投递率。' -ForegroundColor Yellow
}
exit 0
