# T4 / V-05：权限缓存双实例广播 + Redis 断连恢复实机联调
#
# 覆盖：
#   1) 双实例订阅同一 RTopic；实例 A 失效缓存后，实例 B 收到广播并删除 Key；
#   2) Redis 停机期间失效失败进入待重对齐集合，定时任务保留待处理；
#   3) Redis 恢复后 30s 内（本脚本缩短为 2s）重对齐成功、待处理清零；
#   4) Redis 重启后订阅自动恢复，反向（B → A）广播仍然可达。
#
# 前置：
#   - 本机 MySQL 已启动且 bluewhale_auth 已执行 init/迁移（admin/123456、平台租户 1、角色 1 可用）；
#   - 本机 Redis 已启动且已配置密码；
#   - 已构建 whale-service-auth.jar。
#
# 用法：
#   $env:MYSQL_PASSWORD='***'
#   $env:SPRING_DATA_REDIS_PASSWORD='***'
#   $env:REDIS_CLI='D:\tools\installer\redis\redis-cli.exe'
#   $env:REDIS_SERVER='D:\tools\installer\redis\redis-server.exe'
#   $env:REDIS_CONFIG='D:\tools\installer\redis\redis.windows.conf'
#   powershell -File scripts/smoke/bw-auth-permission-cache-broadcast-smoke.ps1
#
# 退出码：0 全部通过；1 存在失败；参数缺失返回 2。

param(
    [string]$JarPath = '',
    [string]$MysqlPassword = $env:MYSQL_PASSWORD,
    [string]$RedisPassword = $env:SPRING_DATA_REDIS_PASSWORD,
    [string]$RedisCli = $env:REDIS_CLI,
    [string]$RedisServer = $env:REDIS_SERVER,
    [string]$RedisConfig = $env:REDIS_CONFIG,
    [int]$PortA = 18081,
    [int]$PortB = 18082,
    [string]$TenantId = '1',
    [long]$UserId = 1,
    [long]$RoleId = 1,
    [int]$TimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

if (-not $JarPath) {
    $JarPath = Join-Path $PSScriptRoot '..\..\backend\whale-services\whale-service-auth\whale-service-auth-starter\target\whale-service-auth.jar'
}
foreach ($required in @(
        @{ Name = 'MysqlPassword'; Value = $MysqlPassword },
        @{ Name = 'RedisPassword'; Value = $RedisPassword },
        @{ Name = 'RedisCli'; Value = $RedisCli },
        @{ Name = 'RedisServer'; Value = $RedisServer },
        @{ Name = 'RedisConfig'; Value = $RedisConfig }
    )) {
    if (-not $required.Value) {
        Write-Host "MISSING: 未提供 $($required.Name)，请设置对应环境变量或参数"
        exit 2
    }
}
if (-not (Test-Path $JarPath)) {
    Write-Host "MISSING: 未找到 auth jar：$JarPath（请先执行 mvn package）"
    exit 2
}

foreach ($port in @($PortA, $PortB)) {
    if (netstat -ano | Select-String ":$port\s" | Where-Object { $_ -match 'LISTENING' }) {
        Write-Host "FAIL: 端口 $port 已被占用，请先释放或改用 -PortA/-PortB"
        exit 2
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$logDir = Join-Path $env:TEMP "bw-auth-cache-broadcast-$stamp"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logA = Join-Path $logDir 'a.out.log'
$logB = Join-Path $logDir 'b.out.log'
$errA = Join-Path $logDir 'a.err.log'
$errB = Join-Path $logDir 'b.err.log'

$script:failed = $false
$processes = @()
$redisRestarted = $false

function Assert($label, $cond, $detail = '') {
    if (-not $cond) {
        Write-Host "FAIL $label $detail"
        $script:failed = $true
        return
    }
    Write-Host "OK   $label $detail"
}

function Invoke-Redis([string[]]$arguments) {
    $output = & $RedisCli -a $RedisPassword @arguments 2>$null
    if ($LASTEXITCODE -ne 0) {
        throw "Redis 命令失败: $($arguments -join ' ') -> $output"
    }
    return $output
}

function Login([int]$port) {
    $uri = "http://127.0.0.1:$port/auth/login"
    $body = '{"username":"admin","password":"123456"}'
    $response = Invoke-RestMethod -Method Post -Uri $uri -ContentType 'application/json; charset=utf-8' -Body $body
    if ($response.code -ne 0 -or -not $response.data.accessToken) {
        throw "登录失败: port=$port code=$($response.code) message=$($response.message)"
    }
    return $response.data.accessToken
}

function AssignAdminRoles([int]$port, [string]$token) {
    $uri = "http://127.0.0.1:$port/user/$UserId/roles"
    $headers = @{ Authorization = "Bearer $token"; 'X-Tenant-Id' = $TenantId }
    $body = @{ roleIds = @($RoleId) } | ConvertTo-Json -Compress
    return Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $body
}

function Wait-ForNewLogLine([string]$path, [string]$pattern, [int]$baseline, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $path) {
            $matches = @(Select-String -Path $path -Pattern $pattern -SimpleMatch -ErrorAction SilentlyContinue)
            if ($matches.Count -gt $baseline) {
                return $matches[-1]
            }
        }
        Start-Sleep -Milliseconds 500
    }
    return $null
}

function Count-LogLine([string]$path, [string]$pattern) {
    if (-not (Test-Path $path)) {
        return 0
    }
    return @(Select-String -Path $path -Pattern $pattern -SimpleMatch -ErrorAction SilentlyContinue).Count
}

function Wait-ForPort([int]$port, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        $listening = netstat -ano | Select-String ":$port\s" | Where-Object { $_ -match 'LISTENING' }
        if ($listening) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

function Wait-ForRedis([int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if ((Invoke-Redis @('PING')) -eq 'PONG') {
                return $true
            }
        } catch {
            # 重启窗口内忽略连接错误
        }
        Start-Sleep -Milliseconds 500
    }
    return $false
}

$instanceIdA = $null
$instanceIdB = $null

try {
    Write-Host "== 启动双实例：A=$PortA, B=$PortB, logs=$logDir =="
    $env:MYSQL_PASSWORD = $MysqlPassword
    $env:SPRING_DATA_REDIS_PASSWORD = $RedisPassword
    $env:WHALE_AUDIT_FORWARD_ENABLED = 'false'
    $env:WHALE_AUDIT_MQ_PUBLISH_ENABLED = 'false'
    $env:PERMISSION_CACHE_RECONCILE_INTERVAL_MS = '2000'
    $env:PERMISSION_CACHE_RECONCILE_INITIAL_DELAY_MS = '1000'
    $env:SPRING_CLOUD_NACOS_DISCOVERY_ENABLED = 'false'
    $env:JWT_SECRET = 'bluewhale-jwt-secret-2026-please-change-in-production'

    $env:SERVER_PORT = "$PortA"
    $processes += Start-Process -FilePath 'java' -PassThru -WindowStyle Hidden `
        -ArgumentList '-Xms256m', '-Xmx512m', '-jar', $JarPath `
        -RedirectStandardOutput $logA -RedirectStandardError $errA
    $env:SERVER_PORT = "$PortB"
    $processes += Start-Process -FilePath 'java' -PassThru -WindowStyle Hidden `
        -ArgumentList '-Xms256m', '-Xmx512m', '-jar', $JarPath `
        -RedirectStandardOutput $logB -RedirectStandardError $errB

    $readyA = Wait-ForPort $PortA $TimeoutSeconds
    $readyB = Wait-ForPort $PortB $TimeoutSeconds
    Assert '1.instancesListening' ($readyA -and $readyB) "(A=$readyA, B=$readyB)"
    if (-not ($readyA -and $readyB)) {
        throw '实例未在超时时间内启动'
    }

    Start-Sleep -Seconds 3
    $subscribeA = Wait-ForNewLogLine $logA '权限缓存跨实例失效广播已订阅' 0 30
    $subscribeB = Wait-ForNewLogLine $logB '权限缓存跨实例失效广播已订阅' 0 30
    Assert '2.broadcastSubscribed' ($null -ne $subscribeA -and $null -ne $subscribeB)
    if ($subscribeA) {
        $instanceIdA = [regex]::Match($subscribeA.Line, 'instance=([0-9a-f]+)').Groups[1].Value
    }
    if ($subscribeB) {
        $instanceIdB = [regex]::Match($subscribeB.Line, 'instance=([0-9a-f]+)').Groups[1].Value
    }
    Assert '2.instanceIdsParsed' ([bool]$instanceIdA -and [bool]$instanceIdB) "(A=$instanceIdA, B=$instanceIdB)"

    $cacheKey = "whale:${TenantId}:cache:auth:user:perm:$UserId"
    $broadcastPatternB = '收到跨实例权限缓存失效广播'

    Write-Host '== 场景 1：A 变更 → 共享 Redis 删除 + B 收到广播 =='
    Invoke-Redis @('SET', $cacheKey, 'stale-before-broadcast', 'EX', '300') | Out-Null
    Assert '3.cachePrimed' ((Invoke-Redis @('EXISTS', $cacheKey)) -eq '1')
    $tokenA = Login $PortA
    $baselineB = Count-LogLine $logB $broadcastPatternB
    $response = AssignAdminRoles $PortA $tokenA
    Assert '3.assignRolesOnA' ($response.code -eq 0) "(code=$($response.code), message=$($response.message))"
    Start-Sleep -Seconds 2
    Assert '3.sharedRedisEvicted' ((Invoke-Redis @('EXISTS', $cacheKey)) -eq '0')
    $receivedB = Wait-ForNewLogLine $logB $broadcastPatternB $baselineB 15
    Assert '3.broadcastReachedB' ($null -ne $receivedB -and $receivedB.Line -match "source=$instanceIdA") `
        "(line=$($receivedB.Line))"

    Write-Host '== 场景 2：Redis 停机 → 失效失败登记待处理 =='
    Invoke-Redis @('SET', $cacheKey, 'stale-during-outage', 'EX', '300') | Out-Null
    $tokenA = Login $PortA
    Stop-Process -Name redis-server -Force
    Start-Sleep -Seconds 3
    Assert '4.redisStopped' (-not (netstat -ano | Select-String ':6379\s' | Where-Object { $_ -match 'LISTENING' }))
    $baselinePending = Count-LogLine $logA '权限缓存删除失败，登记待重对齐'
    $baselineReconcileFail = Count-LogLine $logA '权限缓存重对齐失败，保留待处理'
    $response = AssignAdminRoles $PortA $tokenA
    Assert '4.assignRolesWhileRedisDown' ($response.code -eq 0) "(code=$($response.code), message=$($response.message))"
    $pending = Wait-ForNewLogLine $logA '权限缓存删除失败，登记待重对齐' $baselinePending 20
    Assert '4.pendingRegistered' ($null -ne $pending) "(line=$($pending.Line))"
    $reconcileFail = Wait-ForNewLogLine $logA '权限缓存重对齐失败，保留待处理' $baselineReconcileFail 20
    Assert '4.pendingKeptWhileRedisDown' ($null -ne $reconcileFail) "(line=$($reconcileFail.Line))"

    Write-Host '== 场景 3：Redis 恢复 → 定时任务重对齐 =='
    $baselineReconcileOk = Count-LogLine $logA '权限缓存断连重对齐完成'
    Start-Process -FilePath $RedisServer -WorkingDirectory (Split-Path $RedisServer) `
        -WindowStyle Hidden -ArgumentList $RedisConfig | Out-Null
    $redisRestarted = $true
    Assert '5.redisRestarted' (Wait-ForRedis 30)
    $reconcileOk = Wait-ForNewLogLine $logA '权限缓存断连重对齐完成' $baselineReconcileOk 30
    Assert '5.reconcileCompleted' ($null -ne $reconcileOk -and $reconcileOk.Line -match '剩余=0') `
        "(line=$($reconcileOk.Line))"
    Start-Sleep -Seconds 2
    Assert '5.cacheKeyAbsentAfterReconcile' ((Invoke-Redis @('EXISTS', $cacheKey)) -eq '0')

    Write-Host '== 场景 4：Redis 重启后订阅恢复 → B 变更可广播到 A =='
    Start-Sleep -Seconds 10
    $broadcastPatternA = '收到跨实例权限缓存失效广播'
    Invoke-Redis @('SET', $cacheKey, 'stale-after-restart', 'EX', '300') | Out-Null
    $tokenB = Login $PortB
    $baselineA = Count-LogLine $logA $broadcastPatternA
    $response = AssignAdminRoles $PortB $tokenB
    Assert '6.assignRolesOnB' ($response.code -eq 0) "(code=$($response.code), message=$($response.message))"
    Start-Sleep -Seconds 2
    Assert '6.sharedRedisEvicted' ((Invoke-Redis @('EXISTS', $cacheKey)) -eq '0')
    $receivedA = Wait-ForNewLogLine $logA $broadcastPatternA $baselineA 15
    Assert '6.broadcastReachedA' ($null -ne $receivedA -and $receivedA.Line -match "source=$instanceIdB") `
        "(line=$($receivedA.Line))"
} catch {
    Write-Host "ERROR $($_.Exception.Message)"
    $script:failed = $true
} finally {
    foreach ($process in $processes) {
        if ($process -and -not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    Write-Host "日志目录: $logDir"
}

if ($script:failed) {
    Write-Host 'RESULT: FAILED'
    exit 1
}
if (-not $redisRestarted) {
    Write-Host 'WARN: 本次未执行 Redis 重启场景'
}
Write-Host 'RESULT: PASSED'
exit 0
