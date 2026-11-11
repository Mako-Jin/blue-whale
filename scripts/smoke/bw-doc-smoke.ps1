# Wave 5.74 M1 文档服务冒烟（只读）
# 前置：gateway(8080)/auth(8081)/whale-doc(8095) 就绪，bluewhale_doc 与 migration-5.74-doc.sql 已执行。
# 用法：powershell -File scripts/smoke/bw-doc-smoke.ps1
# 退出码：0 全部通过；1 断言失败。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $cred.JsonBody
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }
Assert '0.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken)

# 1) /doc/meta：服务连通性、租户上下文与功能开关
$meta = Invoke-RestMethod -Method Get -Uri "$base/doc/meta" -Headers $h
Assert '1.meta' ($meta.code -eq 0 -and $meta.data.service -eq 'whale-doc') "(code=$($meta.code))"
Assert '2.tenant' ($null -ne $meta.data.tenantId) "(tenantId=$($meta.data.tenantId))"
Assert '3.switches' ($meta.data.collabEnabled -eq $false -and $meta.data.approvalEnabled -eq $false) `
    "(collab=$($meta.data.collabEnabled), approval=$($meta.data.approvalEnabled))"
Assert '4.limits' ($meta.data.contentMaxBytes -eq 2097152 -and $meta.data.attachmentMaxBytes -eq 52428800 -and $meta.data.maxDepth -eq 10) `
    "(contentMaxBytes=$($meta.data.contentMaxBytes), attachmentMaxBytes=$($meta.data.attachmentMaxBytes), maxDepth=$($meta.data.maxDepth))"

# 2) 内网通道：经网关统一 403（普通调用方不可达）
try {
    Invoke-RestMethod -Method Get -Uri "$base/doc/internal/page/changes" -Headers $h | Out-Null
    Assert '5.internalDeny' $false '(expected 403)'
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Assert '5.internalDeny' ($status -eq 403) "(http=$status)"
}

# 3) 未认证访问被网关拒绝（401/403）
try {
    Invoke-RestMethod -Method Get -Uri "$base/doc/meta" | Out-Null
    Assert '6.unauthenticatedDenied' $false '(expected 401/403)'
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    Assert '6.unauthenticatedDenied' ($status -eq 401 -or $status -eq 403) "(http=$status)"
}

Write-Host 'RESULT: PASSED'
