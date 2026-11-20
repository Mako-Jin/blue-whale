param(
    # E2E 前强制重建本脚本启动的模块及其依赖，避免旧 jar 造成路由/接口漂移
    [switch]$Rebuild
)

$ErrorActionPreference = 'Stop'  # 审计修复：与 ops 脚本对齐，命令失败立即中止

# JDK 21：优先 WHALE_JAVA_HOME，缺省回退本机安装路径
$javaHome = if ($env:WHALE_JAVA_HOME) { $env:WHALE_JAVA_HOME } else { 'D:\tools\installer\jdk\java21' }
if (-not (Test-Path "$javaHome\bin\java.exe")) { throw "未找到 JDK：$javaHome（请设置 WHALE_JAVA_HOME）" }
$java = "$javaHome\bin\java.exe"
$base = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'whale-backend'
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

$services = @(
    @{ Name = 'gateway'; Jar = "$base\whale-services\whale-service-gateway\target\whale-service-gateway.jar"; Module = 'whale-services/whale-service-gateway'; Port = 8080; Extra = @{ NACOS_SERVER = '127.0.0.1:8848' } }
    @{ Name = 'auth';    Jar = "$base\whale-services\whale-service-auth\whale-service-auth-starter\target\whale-service-auth.jar"; Module = 'whale-services/whale-service-auth/whale-service-auth-starter'; Port = 8081; Extra = $null }
    @{ Name = 'alert';   Jar = "$base\whale-ops\whale-ops-alert\target\whale-alert.jar"; Module = 'whale-ops/whale-ops-alert'; Port = 9103; Extra = $null }
    @{ Name = 'audit';   Jar = "$base\whale-ops\whale-ops-audit\target\whale-audit.jar"; Module = 'whale-ops/whale-ops-audit'; Port = 9101; Extra = $null }
    @{ Name = 'monitor'; Jar = "$base\whale-ops\whale-monitor\target\whale-monitor.jar"; Module = 'whale-ops/whale-monitor'; Port = 9102; Extra = $null }
    @{ Name = 'pay';     Jar = "$base\whale-services\whale-pay\target\whale-pay.jar"; Module = 'whale-services/whale-pay'; Port = 8086; Extra = $null }
)

if ($Rebuild) {
    Write-Host 'Rebuilding E2E target modules and dependencies...'
    $moduleList = ($services | ForEach-Object { $_.Module }) -join ','
    & mvn.cmd -f "$base\pom.xml" -pl $moduleList -am -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Maven rebuild failed (exit=$LASTEXITCODE)" }
}

foreach ($service in $services) {
    if (-not (Test-Path -LiteralPath $service.Jar)) {
        throw "Missing jar: $($service.Jar). Run with -Rebuild or build the module first."
    }
    Start-Svc $service.Name $service.Jar $service.Port $service.Extra
}
Write-Host 'all launched'
