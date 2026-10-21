$ErrorActionPreference = 'Stop'
$nacos = 'http://127.0.0.1:8848'
$gw = 'http://127.0.0.1:8080'
$nacosUser = $env:SPRING_CLOUD_NACOS_USERNAME
$nacosPassword = $env:SPRING_CLOUD_NACOS_PASSWORD
if (-not $nacosUser -or -not $nacosPassword) { throw '请先设置 SPRING_CLOUD_NACOS_USERNAME/PASSWORD（凭据不入仓）' }
$login = Invoke-RestMethod -Method Post -Uri "$nacos/nacos/v1/auth/login" -Body @{ username = $nacosUser; password = $nacosPassword }
$token = $login.accessToken
if (-not $token) { throw 'Nacos 登录失败' }
Write-Host 'OK   Nacos 登录'


function Publish([string]$content) {
    $null = Invoke-RestMethod -Method Post -Uri "$nacos/nacos/v1/cs/configs?accessToken=$token" -Body @{
        dataId  = 'whale-gateway-routes.yaml'
        group   = 'DEFAULT_GROUP'
        tenant  = 'public'
        content = $content
    }
}

function Governance() {
    return Invoke-RestMethod -Method Get -Uri "$gw/actuator/routeGovernance"
}

$valid1 = @'
whale:
  gateway:
    dynamic-routes:
      - id: whale-smoke-route
        uri: lb://whale-auth
        predicates:
          - name: Path
            args:
              patterns: /smoke/**
        filters: []
'@

$invalid = @'
whale:
  gateway:
    dynamic-routes:
      - id: whale-bad-route
        uri: lb://whale-auth
        predicates:
          - name: Path
            args:
              patterns: /**
        filters: []
'@

$valid2 = @'
whale:
  gateway:
    dynamic-routes:
      - id: whale-smoke-route2
        uri: lb://whale-auth
        predicates:
          - name: Path
            args:
              patterns: /smoke2/**
        filters: []
'@

Publish $valid1
Start-Sleep -Seconds 6
$g1 = Governance
Write-Host ("OK   发布合法路由: current={0} previous={1} rejected={2}" -f $g1.current.Count, $g1.previous.Count, $g1.rejectedCount)
if ($g1.current.Count -ne 1) { throw '动态路由未加载' }

Publish $invalid
Start-Sleep -Seconds 6
$g2 = Governance
Write-Host ("OK   发布非法路由: current={0} previous={1} rejected={2}" -f $g2.current.Count, $g2.previous.Count, $g2.rejectedCount)
if ($g2.current.Count -ne 1 -or $g2.current[0].id -ne 'whale-smoke-route') { throw '非法配置未被整体拒绝' }
if ($g2.rejectedCount -lt 1) { throw '拒绝计数未累加' }

Publish $valid2
Start-Sleep -Seconds 6
$g3 = Governance
Write-Host ("OK   发布第二版合法路由: current={0} previous={1}" -f $g3.current[0].id, $g3.previous[0].id)

$rb = Invoke-RestMethod -Method Post -Uri "$gw/actuator/routeGovernance" -ContentType 'application/json' -Body '{}'
Write-Host ("OK   回滚: rolledBack={0} routeCount={1}" -f $rb.rolledBack, $rb.routeCount)
Start-Sleep -Seconds 6
$g4 = Governance
Write-Host ("OK   回滚后（刷新后）当前路由={0}，上一版={1}" -f $g4.current[0].id, $g4.previous[0].id)
if ($g4.current[0].id -ne 'whale-smoke-route') { throw "回滚未恢复到 whale-smoke-route：$($g4.current[0].id)" }
Write-Host 'OK   回滚后已恢复 whale-smoke-route 且未被刷新覆盖'

# 清理：删除动态路由测试配置
$null = Invoke-RestMethod -Method Delete -Uri "$nacos/nacos/v1/cs/configs?accessToken=$token&dataId=whale-gateway-routes.yaml&group=DEFAULT_GROUP&tenant=public"
Write-Host 'GW-GOVERNANCE-PASS'

