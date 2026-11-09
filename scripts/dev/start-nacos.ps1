# 启动工作区内的 Nacos 3.x（宿主机 standalone），并等待 8848 就绪。
#
# 为什么需要单独一个脚本：Nacos 的 startup.cmd 依赖 JAVA_HOME，而本机 JAVA_HOME 为空
# （java 只在 PATH 里）。此脚本自动从 java.exe 反推 java.home 再拉起，避免每次手敲。
#
# 用法（用户自己终端执行，或在本机计划任务里挂开机自启）：
#   powershell -ExecutionPolicy Bypass -File scripts\dev\start-nacos.ps1
#   powershell -File scripts\dev\start-nacos.ps1 -Stop      # 停
#
# 注意：Nacos 3.x 需 JDK 17+；本机 D:\java 为 21。

param(
  [switch]$Stop,
  [int]$TimeoutSec = 120
)

$ErrorActionPreference = 'Stop'

$workspaceRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$nacosHome = if ($env:WHALE_NACOS_HOME) { $env:WHALE_NACOS_HOME } else { Join-Path $workspaceRoot 'tools\nacos' }
$startup = Join-Path $nacosHome 'bin\startup.cmd'
$shutdown = Join-Path $nacosHome 'bin\shutdown.cmd'

if (-not (Test-Path $startup)) { throw "未找到 Nacos：$startup（请解压 tools/nacos-server-*.zip）" }

if ($Stop) {
  & $shutdown
  Write-Host 'Nacos 停止指令已发出'
  exit 0
}

# 已在运行则直接返回（8848 已监听）
$c = New-Object System.Net.Sockets.TcpClient
try { $c.Connect('127.0.0.1', 8848); Write-Host 'Nacos 已在运行（8848 已监听）'; exit 0 }
catch { } finally { $c.Dispose() }

# JAVA_HOME 为空时从 PATH 上的 java 反推（Nacos 3.x 需 17+）
if (-not $env:JAVA_HOME -or -not (Test-Path "$($env:JAVA_HOME)\bin\java.exe")) {
  $line = & java.exe -XshowSettings:properties -version 2>&1 |
          Select-String -Pattern '^\s*java\.home\s*=' | Select-Object -First 1
  if (-not $line) { throw '未找到 java：请设置 JAVA_HOME 或 WHALE_NACOS_JAVA_HOME' }
  $env:JAVA_HOME = ($line.ToString() -split '=', 2)[1].Trim()
}
Write-Host "JAVA_HOME=$($env:JAVA_HOME)"

# startup.cmd 内部已带 -Dnacos.home 与 -Dloader.path（plugins 是 MySQL 驱动所在），
# 因此不要手工拼 java 命令行，直接调脚本。
Start-Process -FilePath $startup -ArgumentList '-m', 'standalone' `
  -WorkingDirectory (Join-Path $nacosHome 'bin') -WindowStyle Hidden

for ($i = 0; $i -lt $TimeoutSec; $i++) {
  Start-Sleep -Seconds 1
  $t = New-Object System.Net.Sockets.TcpClient
  try { $t.Connect('127.0.0.1', 8848); Write-Host "Nacos 8848 UP after ${i}s"; exit 0 }
  catch { } finally { $t.Dispose() }
}

Write-Host "Nacos 未在 ${TimeoutSec}s 内就绪，查 $nacosHome\logs\start.out"
exit 1
