# JDK：优先 WHALE_JAVA_HOME / WHALE_NACOS_JAVA_HOME，缺省取 PATH 中 java 的 java.home；Nacos 3.x 需 JDK 17+
$ErrorActionPreference = 'Stop'  # 审计修复：与 ops 脚本对齐，命令失败立即中止
$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Resolve-JavaHome([string]$override) {
  if ($override) { return $override }
  $line = & java.exe -XshowSettings:properties -version 2>&1 | Select-String -Pattern '^\s*java\.home\s*=' | Select-Object -First 1
  if (-not $line) { throw '未找到 java：请设置 WHALE_JAVA_HOME / WHALE_NACOS_JAVA_HOME' }
  return ($line.ToString() -split '=', 2)[1].Trim()
}

$javaHome = Resolve-JavaHome $env:WHALE_JAVA_HOME
$nacosJavaHome = Resolve-JavaHome $env:WHALE_NACOS_JAVA_HOME
foreach ($home in @($javaHome, $nacosJavaHome)) {
  if (-not (Test-Path "$home\bin\java.exe")) { throw "未找到 JDK：$home（请设置 WHALE_JAVA_HOME / WHALE_NACOS_JAVA_HOME）" }
}

$logDir = Join-Path $workspaceRoot 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

foreach ($name in @('MYSQL_PASSWORD','SPRING_DATA_REDIS_PASSWORD','SPRING_CLOUD_NACOS_USERNAME','SPRING_CLOUD_NACOS_PASSWORD')) {
  if (-not [Environment]::GetEnvironmentVariable($name)) { throw "缺少环境变量 $name（凭据不入仓，请先在当前终端设置）" }
}

function Wait-Port($port, $label, $timeoutSec) {
  for ($i = 0; $i -lt $timeoutSec; $i++) {
    $c = New-Object System.Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $port); $c.Close(); Write-Host "${label}:${port} UP after ${i}s"; return $true }
    catch { Start-Sleep -Seconds 1 }
    finally { $c.Dispose() }
  }
  Write-Host "${label}:${port} TIMEOUT after ${timeoutSec}s"; return $false
}

function Test-Port($port) {
  $c = New-Object System.Net.Sockets.TcpClient
  try { $c.Connect('127.0.0.1', $port); return $true } catch { return $false } finally { $c.Dispose() }
}

# 1) 中间件：Nacos 统一用工作区 tools/nacos（3.2.4 解压体），已运行则跳过启动
$nacosHome = if ($env:WHALE_NACOS_HOME) { $env:WHALE_NACOS_HOME } else { Join-Path $workspaceRoot 'tools\nacos' }
if (-not (Test-Path "$nacosHome\bin\startup.cmd")) { throw "未找到 Nacos：$nacosHome（请先解压 tools/nacos-server-3.2.4.zip）" }

if (-not (Test-Port 3306)) {
  if (-not $env:WHALE_MYSQL_HOME) { throw 'MySQL 3306 未启动：请启动 MySQL 服务，或设置 WHALE_MYSQL_HOME 指向 MySQL 安装目录' }
  Start-Process -FilePath "$($env:WHALE_MYSQL_HOME)\bin\mysqld.exe" -ArgumentList '--console' `
    -WorkingDirectory "$($env:WHALE_MYSQL_HOME)\bin" -WindowStyle Hidden `
    -RedirectStandardOutput "$logDir\mysql.log" -RedirectStandardError "$logDir\mysql.err"
}
$redisHome = if ($env:WHALE_REDIS_HOME) { $env:WHALE_REDIS_HOME } else { Join-Path $workspaceRoot 'tools\Redis-x64-5.0.14.1' }
if (-not (Test-Port 6379)) {
  Start-Process -FilePath (Join-Path $redisHome 'redis-server.exe') -ArgumentList 'redis.windows.conf' `
    -WorkingDirectory $redisHome -WindowStyle Hidden `
    -RedirectStandardOutput "$logDir\redis.log" -RedirectStandardError "$logDir\redis.err"
}
$env:JAVA_HOME = $nacosJavaHome
Start-Process -FilePath (Join-Path $nacosHome 'bin\startup.cmd') -ArgumentList '-m', 'standalone' `
  -WorkingDirectory (Join-Path $nacosHome 'bin') -WindowStyle Hidden

Wait-Port 3306 'mysql' 60 | Out-Null
Wait-Port 6379 'redis' 30 | Out-Null
Wait-Port 8848 'nacos' 90 | Out-Null
Wait-Port 8849 'nacos-console' 60 | Out-Null

# 2) 业务服务
$env:JAVA_HOME = $javaHome
$env:NACOS_SERVER = '127.0.0.1:8848'
$java = "$($env:JAVA_HOME)\bin\java.exe"
$base = Join-Path $workspaceRoot 'whale-backend'

function Start-Svc($name, $jar, $port) {
  $env:SERVER_PORT = "$port"
  Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WindowStyle Hidden `
    -RedirectStandardOutput "$logDir\$name.log" -RedirectStandardError "$logDir\$name.err"
  Write-Host "started $name port=$port"
}

$env:NACOS_ADDR = '127.0.0.1:8848'
Start-Svc 'gateway' "$base\whale-services\whale-service-gateway\target\whale-service-gateway.jar" 8080
Remove-Item Env:\NACOS_ADDR
Start-Svc 'auth'    "$base\whale-services\whale-service-auth\whale-service-auth-starter\target\whale-service-auth.jar"  8081
Start-Svc 'alert'   "$base\whale-ops\whale-alert\target\whale-alert.jar"     9103
Start-Svc 'audit'   "$base\whale-ops\whale-audit\target\whale-audit.jar"     9101
Start-Svc 'monitor' "$base\whale-ops\whale-monitor\target\whale-monitor.jar" 9102
Start-Svc 'pay'     "$base\whale-services\whale-pay\target\whale-pay.jar"    8086

Wait-Port 8080 'gateway' 90 | Out-Null
Write-Host 'stack ready; holding terminal open'
while ($true) { Start-Sleep -Seconds 60 }
