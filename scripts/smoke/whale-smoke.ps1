
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Continue'
$gw = 'http://127.0.0.1:8080'
function Call($label, $method, $uri, $headers, $body) {
  try {
    if ($body) {
      $r = Invoke-RestMethod -Method $method -Uri $uri -Headers $headers -ContentType 'application/json' -Body $body
    } else {
      $r = Invoke-RestMethod -Method $method -Uri $uri -Headers $headers
    }
    Show $label $r
    return $r
  } catch {
    $resp = $_.Exception.Response
    if ($resp) {
      $code = [int]$resp.StatusCode
      $sr = New-Object System.IO.StreamReader($resp.GetResponseStream())
      $txt = $sr.ReadToEnd()
      Write-Host "== $label == HTTP $code body=$txt"
    } else {
      Write-Host "== $label == ERROR $($_.Exception.Message)"
    }
    return $null
  }
}

# P0 登录
$cred = Get-SmokeAdminCredential
$login = Call 'P0 LOGIN' 'POST' "$gw/auth/login" $null $cred.JsonBody
$token = $login.data.accessToken
Write-Host "P0 hasToken=$([bool]$token)"
$h = @{ Authorization = "Bearer $token" }

# monitor
Call 'P1 monitor/service/list' 'GET' "$gw/monitor/service/list" $h $null | Out-Null
Call 'P2 monitor/instances(gateway)' 'GET' "$gw/monitor/service/whale-service-gateway/instances" $h $null | Out-Null
Call 'P2b monitor/instances(no-such-svc)' 'GET' "$gw/monitor/service/NO_SUCH_SVC/instances" $h $null | Out-Null

# alert
Call 'P3 alert/rule/page' 'GET' "$gw/alert/rule/page?current=1&size=10" $h $null | Out-Null
$created = Call 'P4 alert/rule/create' 'POST' "$gw/alert/rule" $h '{"ruleName":"smoke-rule","metricName":"cpu","thresholdValue":90,"operator":"GT","level":"WARN","status":1,"notifyTargets":"admin"}'
Call 'P5 alert/rule/create-dup' 'POST' "$gw/alert/rule" $h '{"ruleName":"smoke-rule","metricName":"cpu","thresholdValue":90,"operator":"GT","level":"WARN","status":1,"notifyTargets":"admin"}' | Out-Null
Call 'P6 alert/rule/404' 'GET' "$gw/alert/rule/999999999" $h $null | Out-Null
Call 'P7 alert/record/page' 'GET' "$gw/alert/record/page?current=1&size=10" $h $null | Out-Null

# pay
Call 'P8 pay/order/page' 'GET' "$gw/pay/order/page?current=1&size=10" $h $null | Out-Null
$order = Call 'P9 pay/order/create' 'POST' "$gw/pay/order" $h '{"bizOrderNo":"SMOKE-1","amount":100.00,"payChannel":"ALIPAY","subject":"smoke"}'
if ($order -and $order.data) {
  $oid = $order.data.id
  Call 'P10 pay/order/pay' 'POST' "$gw/pay/order/$oid/pay" $h '{}' | Out-Null
  Call 'P11 pay/order/pay-again' 'POST' "$gw/pay/order/$oid/pay" $h '{}' | Out-Null
}

# audit
Call 'P12 audit/log/page' 'GET' "$gw/audit/log/page?current=1&size=10" $h $null | Out-Null

# no-token -> real 401
Call 'P13 no-token monitor/list' 'GET' "$gw/monitor/service/list" $null $null | Out-Null

Write-Host 'DONE'
