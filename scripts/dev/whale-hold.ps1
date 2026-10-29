# JDK 21：优先 WHALE_JAVA_HOME，缺省回退本机安装路径
$ErrorActionPreference = 'Stop'  # 审计修复：与 ops 脚本对齐，命令失败立即中止
$javaHome = if ($env:WHALE_JAVA_HOME) { $env:WHALE_JAVA_HOME } else { 'D:\tools\installer\jdk\java21' }
if (-not (Test-Path "$javaHome\bin\java.exe")) { throw "未找到 JDK：$javaHome（请设置 WHALE_JAVA_HOME）" }
$env:JAVA_HOME = $javaHome
$env:NACOS_SERVER = '127.0.0.1:8848'
$java = "$($env:JAVA_HOME)\bin\java.exe"
$base = 'd:\BlueWhale\BlueWhale\backend'
$logDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

foreach ($name in @('MYSQL_PASSWORD','SPRING_DATA_REDIS_PASSWORD','SPRING_CLOUD_NACOS_USERNAME','SPRING_CLOUD_NACOS_PASSWORD')) {
  if (-not [Environment]::GetEnvironmentVariable($name)) { throw "缺少环境变量 $name（凭据不入仓，请先在当前终端设置）" }
}

function Start-Svc($name, $jar, $port) {
  $env:SERVER_PORT = "$port"
  Start-Process -FilePath $java -ArgumentList @('-jar', $jar) -WindowStyle Hidden `
    -RedirectStandardOutput "$logDir\$name.log" -RedirectStandardError "$logDir\$name.err"
  Write-Host "started $name port=$port"
}

$env:NACOS_ADDR = '127.0.0.1:8848'
Start-Svc 'gateway' "$base\whale-services\whale-gateway\target\whale-gateway.jar" 8080
Remove-Item Env:\NACOS_ADDR
Start-Svc 'auth'    "$base\whale-services\whale-service-auth\whale-service-auth-starter\target\whale-service-auth.jar"       8081
Start-Svc 'alert'   "$base\whale-ops\whale-alert\target\whale-alert.jar"          9103
Start-Svc 'audit'   "$base\whale-ops\whale-audit\target\whale-audit.jar"          9101
Start-Svc 'monitor' "$base\whale-ops\whale-monitor\target\whale-monitor.jar"      9102
Start-Svc 'pay'     "$base\whale-services\whale-pay\target\whale-pay.jar"         8086
Write-Host 'all launched; holding terminal open'
while ($true) { Start-Sleep -Seconds 60 }
