# ============================================================
# 蓝鲸系统 - 开发环境中间件可达性检查 (Windows PowerShell)
# 本地手动安装基础设施后, 启动后端服务前检查:
#   MySQL 127.0.0.1:3306 / Redis 127.0.0.1:6379 / Nacos 127.0.0.1:8848
# 用法: .\check-env.ps1 [-Host 127.0.0.1]
# ============================================================

param(
    [Alias('Host')]
    [string]$TargetHost = '127.0.0.1'
)

$ErrorActionPreference = 'Continue'

function Write-Pass ($msg) { Write-Host "[PASS] " -ForegroundColor Green -NoNewline; Write-Host $msg }
function Write-Fail ($msg) { Write-Host "[FAIL] " -ForegroundColor Red -NoNewline; Write-Host $msg }
function Write-Hint ($msg) { Write-Host "[HINT] " -ForegroundColor Cyan -NoNewline; Write-Host $msg }

# TCP 探测 (System.Net.Sockets.TcpClient, 2 秒超时, 兼容 PowerShell 5.1)
function Test-Port {
    param([string]$TargetHost, [int]$Port, [int]$TimeoutMs = 2000)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $async = $client.BeginConnect($TargetHost, $Port, $null, $null)
        if (-not $async.AsyncWaitHandle.WaitOne($TimeoutMs, $false)) { return $false }
        $client.EndConnect($async)
        return $true
    } catch {
        return $false
    } finally {
        try { $client.Close() } catch {}
    }
}

# Nacos HTTP 探测: /nacos 返回 200/302 均视为可达
function Test-Nacos {
    param([string]$TargetHost)
    $code = 0
    try {
        $resp = Invoke-WebRequest -Uri "http://${TargetHost}:8848/nacos" -TimeoutSec 2 -UseBasicParsing -ErrorAction Stop
        $code = [int]$resp.StatusCode
    } catch {
        if ($_.Exception.Response) {
            try { $code = [int]$_.Exception.Response.StatusCode } catch {}
        }
    }
    return ($code -eq 200 -or $code -eq 302)
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Blue
Write-Host "  BlueWhale 中间件可达性检查" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

$checks = @(
    @{ Name = 'MySQL'; Port = 3306; Hint = '请启动 MySQL 服务 (services.msc), 或 docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8' },
    @{ Name = 'Redis'; Port = 6379; Hint = '请启动 Redis 服务, 或 docker run -d --name redis -p 6379:6379 redis:7' },
    @{ Name = 'Nacos'; Port = 8848; Hint = '请启动 Nacos (startup.cmd -m standalone), 或 docker run -d --name nacos -p 8848:8848 nacos/nacos-server:v2.3.2'; Http = $true }
)

$failed = @()

foreach ($c in $checks) {
    $target = "${TargetHost}:$($c.Port)"
    $ok = Test-Port -TargetHost $TargetHost -Port $c.Port

    # Nacos 额外要求 HTTP /nacos 返回 200/302
    if ($ok -and $c.Http) {
        if (-not (Test-Nacos -TargetHost $TargetHost)) { $ok = $false }
    }

    if ($ok) {
        Write-Pass "$($c.Name)  $target 可达"
    } else {
        Write-Fail "$($c.Name)  $target 不可达"
        $failed += $c
    }
}

Write-Host ""
if ($failed.Count -eq 0) {
    Write-Host "所有中间件已就绪 ✓" -ForegroundColor Green
    exit 0
}

Write-Host "----------------------------------------" -ForegroundColor Red
Write-Host "$($failed.Count) 项中间件检查未通过, 请按以下方式处理:" -ForegroundColor Red
Write-Host "----------------------------------------" -ForegroundColor Red
foreach ($f in $failed) {
    Write-Hint "$($f.Name): $($f.Hint)"
}
Write-Host ""
exit 1
