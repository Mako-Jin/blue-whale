# ============================================================
# 蓝鲸系统 - 开发环境检查 (Windows PowerShell)
# 本地手动安装基础设施后, 启动后端服务前检查:
#   JDK 21（JAVA_HOME 优先，缺省回退 PATH） / MySQL 127.0.0.1:3306 / Redis 6379 / Nacos 8848
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

# JDK 解析: JAVA_HOME/bin/java.exe 优先，缺省回退 PATH 上的 java
function Get-JavaExe {
    param([string]$JavaHome)
    foreach ($candidate in @("$JavaHome\bin\java.exe", "$JavaHome\bin\java")) {
        if ($JavaHome -and (Test-Path $candidate)) { return $candidate }
    }
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

# 主版本号断言: java -version 输出形如 version "21.0.8"
function Get-JavaMajorVersion {
    param([string]$JavaExe)
    if (-not $JavaExe) { return $null }
    $output = & $JavaExe -version 2>&1 | Out-String
    if ($output -match 'version "(\d+)') { return [int]$Matches[1] }
    return $null
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Blue
Write-Host "  BlueWhale 开发环境检查" -ForegroundColor Blue
Write-Host "========================================" -ForegroundColor Blue
Write-Host ""

$failed = @()

# JDK 断言: 后端全量编译依赖 21，17 会直接构建失败
$javaExe = Get-JavaExe $env:JAVA_HOME
$javaMajor = Get-JavaMajorVersion $javaExe
if ($javaMajor -eq 21) {
    Write-Pass "JDK    21  $javaExe"
} else {
    $found = if ($javaMajor) { "$javaMajor" } else { '未找到 java' }
    Write-Fail "JDK    21  需要 JDK 21，当前为 $found"
    $failed += @{ Name = 'JDK 21'; Hint = '设置 JAVA_HOME 或 WHALE_JAVA_HOME 指向 JDK 21（后端全量编译依赖 21）' }
}

$checks = @(
    @{ Name = 'MySQL'; Port = 3306; Hint = '请启动 MySQL 服务 (services.msc), 或 docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8' },
    @{ Name = 'Redis'; Port = 6379; Hint = '请启动 Redis 服务, 或 docker run -d --name redis -p 6379:6379 redis:7' },
    @{ Name = 'Nacos'; Port = 8848; Hint = '请启动 Nacos (startup.cmd -m standalone), 或 docker run -d --name nacos -p 8848:8848 nacos/nacos-server:v3.2.4'; Http = $true }
)

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
    Write-Host "JDK 与中间件均已就绪 ✓" -ForegroundColor Green
    exit 0
}

Write-Host "----------------------------------------" -ForegroundColor Red
Write-Host "$($failed.Count) 项检查未通过, 请按以下方式处理:" -ForegroundColor Red
Write-Host "----------------------------------------" -ForegroundColor Red
foreach ($f in $failed) {
    Write-Hint "$($f.Name): $($f.Hint)"
}
Write-Host ""
exit 1
