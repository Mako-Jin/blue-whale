# JDK 21：优先 WHALE_JAVA_HOME，缺省回退本机安装路径
$javaHome = if ($env:WHALE_JAVA_HOME) { $env:WHALE_JAVA_HOME } else { 'D:\tools\installer\jdk\java21' }
if (-not (Test-Path "$javaHome\bin\java.exe")) { throw "未找到 JDK：$javaHome（请设置 WHALE_JAVA_HOME）" }
$java = "$javaHome\bin\java.exe"
$base = 'd:\BlueWhale\BlueWhale\backend'
$logDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

foreach ($name in @('MYSQL_PASSWORD','SPRING_DATA_REDIS_PASSWORD','SPRING_CLOUD_NACOS_USERNAME','SPRING_CLOUD_NACOS_PASSWORD')) {
  if (-not [Environment]::GetEnvironmentVariable($name)) { throw "缺少环境变量 $name（凭据不入仓，请先在当前终端设置）" }
}

function Start-Svc($name, $jar, $port, $extra) {
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = $java
  $psi.Arguments = "-jar `"$jar`""
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $psi.EnvironmentVariables['JAVA_HOME'] = $javaHome
  $psi.EnvironmentVariables['SERVER_PORT'] = "$port"
  if ($extra) { $extra.GetEnumerator() | ForEach-Object { $psi.EnvironmentVariables[$_.Key] = $_.Value } }
  $p = [System.Diagnostics.Process]::Start($psi)
  Write-Host "$name pid=$($p.Id) port=$port"
}

Start-Svc 'gateway' "$base\whale-services\whale-gateway\target\whale-gateway.jar" 8080 @{ NACOS_ADDR = '127.0.0.1:8848' }
Start-Svc 'auth'    "$base\whale-services\whale-auth\target\whale-auth.jar"       8081 $null
Start-Svc 'alert'   "$base\whale-ops\whale-alert\target\whale-alert.jar"          9103 $null
Start-Svc 'audit'   "$base\whale-ops\whale-audit\target\whale-audit.jar"          9101 $null
Start-Svc 'monitor' "$base\whale-ops\whale-monitor\target\whale-monitor.jar"      9102 $null
Start-Svc 'pay'     "$base\whale-services\whale-pay\target\whale-pay.jar"         8086 $null
Write-Host 'all launched'
