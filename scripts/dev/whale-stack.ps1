# JDK 21：优先 WHALE_JAVA_HOME，缺省回退本机安装路径；Nacos 2.4 需 JDK 17，单独用 WHALE_NACOS_JAVA_HOME
$javaHome = if ($env:WHALE_JAVA_HOME) { $env:WHALE_JAVA_HOME } else { 'D:\tools\installer\jdk\java21' }
$nacosJavaHome = if ($env:WHALE_NACOS_JAVA_HOME) { $env:WHALE_NACOS_JAVA_HOME } else { 'D:\tools\installer\jdk\java17' }
foreach ($home in @($javaHome, $nacosJavaHome)) {
  if (-not (Test-Path "$home\bin\java.exe")) { throw "未找到 JDK：$home（请设置 WHALE_JAVA_HOME / WHALE_NACOS_JAVA_HOME）" }
}

$logDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'logs'
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

# 1) 中间件
Start-Process -FilePath 'D:\tools\installer\mysql\bin\mysqld.exe' -ArgumentList '--console' `
  -WorkingDirectory 'D:\tools\installer\mysql\bin' -WindowStyle Hidden `
  -RedirectStandardOutput "$logDir\mysql.log" -RedirectStandardError "$logDir\mysql.err"
Start-Process -FilePath 'D:\tools\installer\redis\redis-server.exe' -ArgumentList 'redis.windows.conf' `
  -WorkingDirectory 'D:\tools\installer\redis' -WindowStyle Hidden `
  -RedirectStandardOutput "$logDir\redis.log" -RedirectStandardError "$logDir\redis.err"
$env:JAVA_HOME = $nacosJavaHome
Start-Process -FilePath 'D:\tools\installer\nacos\bin\startup.cmd' -ArgumentList '-m', 'standalone' `
  -WorkingDirectory 'D:\tools\installer\nacos\bin' -WindowStyle Hidden

Wait-Port 3306 'mysql' 60 | Out-Null
Wait-Port 6379 'redis' 30 | Out-Null
Wait-Port 8848 'nacos' 90 | Out-Null

# 2) 业务服务
$env:JAVA_HOME = $javaHome
$env:NACOS_SERVER = '127.0.0.1:8848'
$java = "$($env:JAVA_HOME)\bin\java.exe"
$base = 'd:\BlueWhale\BlueWhale\backend'

function Start-Svc($name, $jar, $port) {
  $env:SERVER_PORT = "$port"
  Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WindowStyle Hidden `
    -RedirectStandardOutput "$logDir\$name.log" -RedirectStandardError "$logDir\$name.err"
  Write-Host "started $name port=$port"
}

$env:NACOS_ADDR = '127.0.0.1:8848'
Start-Svc 'gateway' "$base\whale-services\whale-gateway\target\whale-gateway.jar" 8080
Remove-Item Env:\NACOS_ADDR
Start-Svc 'auth'    "$base\whale-services\whale-service-auth\whale-service-auth-starter\target\whale-service-auth.jar"  8081
Start-Svc 'alert'   "$base\whale-ops\whale-alert\target\whale-alert.jar"     9103
Start-Svc 'audit'   "$base\whale-ops\whale-audit\target\whale-audit.jar"     9101
Start-Svc 'monitor' "$base\whale-ops\whale-monitor\target\whale-monitor.jar" 9102
Start-Svc 'pay'     "$base\whale-services\whale-pay\target\whale-pay.jar"    8086

Wait-Port 8080 'gateway' 90 | Out-Null
Write-Host 'stack ready; holding terminal open'
while ($true) { Start-Sleep -Seconds 60 }
