# Wave 5.61 M3 第三方电子签章回调冒烟：
#   provider=esignbao 发起签署（带 workflowTaskId）→ HMAC 回调（经网关匿名）→
#   签署任务/合同/工作流推进 → 证据链与验签 → 环形重放 10 次仅一条证据 →
#   错误签名/过期时间戳 403 + 审计 → 证据链篡改检出 → provider 切换 fadada →
#   拒签回调 → 合同生效 → 归档 → 未知渠道拒绝。
# 前置：gateway(8080)/auth(8081)/workflow(8083)/file(8085)/whale-contract(9006) 就绪；
#       已执行 migration-5.63-contract-esign-callback.sql、migration-5.63-workflow-task-operator.sql、
#       migration-5.63-contract-esign-m3.sql。
# 用法（PowerShell 7）：pwsh -NoProfile -File scripts/smoke/bw-contract-m3-smoke.ps1
# 说明：数据库断言依赖用户级环境变量 MYSQL_PASSWORD；缺失时跳过并提示，不影响 HTTP 断言。


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$fileBase = 'http://127.0.0.1:8085'
$mysqlBin = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'D:\tools\installer\mysql\bin\mysql.exe' }
$mysqlPassword = $env:MYSQL_PASSWORD
$dbAvailable = [bool]$mysqlPassword
$callbackSecret = if ($env:CONTRACT_ESIGN_CALLBACK_SECRET) { $env:CONTRACT_ESIGN_CALLBACK_SECRET } else { 'bluewhale-esign-dev-callback-secret' }
$definitionId = if ($env:CONTRACT_APPROVAL_DEFINITION_ID) { [long]$env:CONTRACT_APPROVAL_DEFINITION_ID } else { 2114579272542597122 }

function Invoke-Sql($sql) {
    if (-not $dbAvailable) {
        Write-Warning "跳过数据库断言（未设置 MYSQL_PASSWORD）：$sql"
        return $null
    }
    $env:MYSQL_PWD = $mysqlPassword
    return (& $mysqlBin --user=root --host=127.0.0.1 --port=3306 -N -B -e $sql) -join "`n"
}

function Post-Json($uri, $headers, $body) {
    $json = $body | ConvertTo-Json -Depth 6 -Compress
    return Invoke-RestMethod -Method Post -Uri $uri -Headers $headers -ContentType 'application/json; charset=utf-8' `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function New-ApprovedContract($title, $parties) {
    $body = @{
        title         = $title
        category      = 'PURCHASE'
        amount        = 99000.00
        currency      = 'CNY'
        ownerId       = 1
        deptId        = 1
        effectiveDate = '2026-11-01'
        expireDate    = '2027-10-31'
        parties       = $parties
    }
    $created = Post-Json "$base/contract" $h $body
    Assert "create[$title]" ($created.code -eq 0 -and $created.data.status -eq 'DRAFT')
    $cid = $created.data.id
    $submit = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid/submit" -Headers $h
    Assert "submit[$title]" ($submit.code -eq 0 -and $submit.data.status -eq 'APPROVING')
    $detail = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid" -Headers $h
    $instId = [long]$detail.data.flows[0].processInstanceId
    $tasks = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instId" -Headers $h
    $taskId = $tasks.data.records[0].id
    $approve = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskId/approve" -Headers $h `
        -ContentType 'application/json' -Body '{"comment":"M3 冒烟审批同意"}'
    Assert "approve[$title]" ($approve.code -eq 0)
    $after = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid" -Headers $h
    Assert "approved[$title]" ($after.data.status -eq 'APPROVED')
    return $cid
}

function New-SignHeaders($payloadJson, $timestampSec) {
    $nonce = [guid]::NewGuid().ToString('N')
    $content = "$timestampSec`n$nonce`n$payloadJson"
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($callbackSecret))
    try {
        $signature = [Convert]::ToBase64String($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($content)))
    } finally {
        $hmac.Dispose()
    }
    return @{
        'X-Esign-Timestamp' = "$timestampSec"
        'X-Esign-Nonce'     = $nonce
        'X-Esign-Signature' = $signature
        'Content-Type'      = 'application/json; charset=utf-8'
    }
}

function Send-Callback($provider, $payload, $timestampOverride, $badSignature) {
    $json = $payload | ConvertTo-Json -Depth 5 -Compress
    $ts = if ($timestampOverride) { [long]$timestampOverride } else { [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() }
    $headers = New-SignHeaders $json $ts
    if ($badSignature) { $headers['X-Esign-Signature'] = 'AAAAinvalid' }
    return Invoke-RestMethod -Method Post -Uri "$base/contract/sign/callback/$provider" -Headers $headers `
        -Body ([System.Text.Encoding]::UTF8.GetBytes($json))
}

function New-SignedFile($name) {
    $path = Join-Path $env:TEMP "bw-contract-m3-$name-$([guid]::NewGuid().ToString('N')).pdf"
    $content = "%PDF-1.4`n1 0 obj<</Type/Catalog>>endobj`ntrailer<</Root 1 0 R>>`n%%EOF`n$name"
    [System.IO.File]::WriteAllBytes($path, [Text.Encoding]::UTF8.GetBytes($content))
    $sha = (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLower()
    $uploaded = Invoke-RestMethod -Method Post -Uri "$fileBase/file/internal/upload" -Form @{
        file   = Get-Item $path
        bucket = 'contract'
    }
    Remove-Item $path -Force
    if ($uploaded.code -ne 0) { throw "签章件上传失败: $($uploaded.message)" }
    return @{ fileId = "$($uploaded.data.id)"; sha256 = $sha }
}

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '00.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken)
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }

# 1) 合同一：esignbao + 工作流任务关联
$cid1 = New-ApprovedContract 'M3 冒烟合同（e签宝回调）' @(
    @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; contact = '张三'; phone = '13800000001'; signerUserId = 1; signerName = '张三' }
)
$wf1 = Post-Json "$base/workflow/instance" $h @{
    definitionId = $definitionId
    title        = 'M3 冒烟签署流程'
    businessKey  = "contract:$cid1"
    variables    = @{ amount = 99000; category = 'PURCHASE' }
}
Assert '01.workflowStart' ($wf1.code -eq 0 -and $null -ne $wf1.data.id)
$wfTask1 = (Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$($wf1.data.id)" -Headers $h).data.records[0]
Assert '02.workflowTask' ($null -ne $wfTask1.id)
$signed1 = New-SignedFile 'esignbao'
$start1 = Post-Json "$base/contract/$cid1/sign/start" $h @{
    provider = 'esignbao'
    tasks    = @(@{
        signerParty   = 'A'
        signerUserId  = 1
        signerName    = '张三'
        workflowTaskId = [long]$wfTask1.id
    })
}
Assert '03.startEsignbao' ($start1.code -eq 0 -and $start1.data.Count -eq 1 -and $start1.data[0].status -eq 'SIGNING')
Assert '04.providerTaskId' ($start1.data[0].providerTaskId -match '^ESIGNBAO-')
$task1 = $start1.data[0].id
$event1 = "evt-m3-$([guid]::NewGuid().ToString('N'))"
$payload1 = @{
    providerEventId = $event1
    action          = 'SIGN'
    signTaskId      = "$task1"
    providerTaskId  = $start1.data[0].providerTaskId
    evidenceNo      = "EV-M3-ESIGNBAO-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
    signedAt        = '2026-10-26T10:00:00'
    signedFileId    = $signed1.fileId
    signedSha256    = $signed1.sha256
}

# 2) 合法回调（经网关、不带 Authorization）
$cb1 = Send-Callback 'esignbao' $payload1 $null $false
Assert '05.callbackOk' ($cb1.code -eq 0)
$contract1 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1" -Headers $h
Assert '06.contractSigned' ($contract1.data.status -eq 'SIGNED')
$wfTaskAfter1 = (Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$($wf1.data.id)" -Headers $h).data.records[0]
Assert '07.workflowAdvanced' ($wfTaskAfter1.status -eq 1)
if ($dbAvailable) {
    $operatorFlag = Invoke-Sql "SELECT CONCAT(operator_type, ':', operator_source) FROM bluewhale_workflow.process_task WHERE id=$($wfTask1.id);"
    Assert '07b.operatorSource' ($operatorFlag -eq 'SYSTEM:ESIGN_CALLBACK')
}

# 3) 证据链与验签
$evidence1 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1/evidence" -Headers $h
Assert '08.evidenceChain' ($evidence1.code -eq 0 -and $evidence1.data.records.Count -eq 1 -and $evidence1.data.chainValid -eq $true)
Assert '09.evidenceProvider' ($evidence1.data.records[0].algorithm -eq 'ESIGNBAO-REMOTE')
$verify1 = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/verify" -Headers $h
Assert '10.verifyOk' ($verify1.code -eq 0 -and $verify1.data.hashMatch -eq $true -and $verify1.data.chainValid -eq $true -and $verify1.data.signatureValid -eq $true)

# 4) 环形重放 10 次：任务与证据不重复推进
for ($i = 1; $i -le 10; $i++) {
    $replay = Send-Callback 'esignbao' $payload1 $null $false
    Assert "11.replay$i" ($replay.code -eq 0)
}
$evidence2 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1/evidence" -Headers $h
Assert '12.replayNoNewRecord' ($evidence2.data.records.Count -eq 1)
if ($dbAvailable) {
    $cbCount = Invoke-Sql "SELECT COUNT(*) FROM bluewhale_contract.ct_esign_callback WHERE provider='ESIGNBAO' AND provider_event_id='$event1';"
    Assert '13.callbackUnique' ([int]$cbCount -eq 1)
}

# 5) 非法签名 / 过期时间戳一律 403 并落审计
$badPayload = @{
    providerEventId = "evt-m3-bad-$([guid]::NewGuid().ToString('N'))"
    action          = 'SIGN'
    signTaskId      = "$task1"
}
$badSig = Send-Callback 'esignbao' $badPayload $null $true
Assert '14.badSignature403' ($badSig.code -eq 403)
$staleTs = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() - 600
$stalePayload = @{
    providerEventId = "evt-m3-stale-$([guid]::NewGuid().ToString('N'))"
    action          = 'SIGN'
    signTaskId      = "$task1"
}
$stale = Send-Callback 'esignbao' $stalePayload $staleTs $false
Assert '15.staleTimestamp403' ($stale.code -eq 403)
if ($dbAvailable) {
    Start-Sleep -Milliseconds 900
    $auditCount = Invoke-Sql "SELECT COUNT(*) FROM bluewhale_contract.audit_log WHERE request_uri LIKE '%/contract/sign/callback/%' AND biz_status = 0;"
    Assert '16.auditRejected' ([int]$auditCount -ge 2)
}

# 6) 证据链篡改可检出（临时改动后恢复）
if ($dbAvailable) {
    $originalChain = Invoke-Sql "SELECT chain_hash FROM bluewhale_contract.ct_sign_record WHERE contract_id=$cid1 ORDER BY signed_at ASC LIMIT 1;"
    Invoke-Sql "UPDATE bluewhale_contract.ct_sign_record SET chain_hash='$('f' * 64)' WHERE contract_id=$cid1;" | Out-Null
    $tampered = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/verify" -Headers $h
    Assert '17.chainTamperDetected' ($tampered.code -eq 0 -and $tampered.data.chainValid -eq $false)
    Invoke-Sql "UPDATE bluewhale_contract.ct_sign_record SET chain_hash='$originalChain' WHERE contract_id=$cid1;" | Out-Null
    $restored = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/verify" -Headers $h
    Assert '18.chainRestored' ($restored.data.chainValid -eq $true)
}

# 7) provider 切换：fadada 走同一回调链路
$cid2 = New-ApprovedContract 'M3 冒烟合同（法大大回调）' @(
    @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; contact = '张三'; phone = '13800000001'; signerUserId = 1; signerName = '张三' }
)
$signed2 = New-SignedFile 'fadada'
$start2 = Post-Json "$base/contract/$cid2/sign/start" $h @{ provider = 'fadada' }
Assert '19.startFadada' ($start2.code -eq 0 -and $start2.data[0].providerTaskId -match '^FADADA-')
$payload2 = @{
    providerEventId = "evt-m3-$([guid]::NewGuid().ToString('N'))"
    action          = 'SIGN'
    signTaskId      = "$($start2.data[0].id)"
    providerTaskId  = $start2.data[0].providerTaskId
    evidenceNo      = "EV-M3-FADADA-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
    signedAt        = '2026-10-26T11:00:00'
    signedFileId    = $signed2.fileId
    signedSha256    = $signed2.sha256
}
$cb2 = Send-Callback 'fadada' $payload2 $null $false
Assert '20.fadadaCallback' ($cb2.code -eq 0)
$contract2 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2" -Headers $h
Assert '21.fadadaSigned' ($contract2.data.status -eq 'SIGNED')

# 8) 拒签回调：任务 REFUSED，合同停留 SIGNING
$cid3 = New-ApprovedContract 'M3 冒烟合同（三方拒签）' @(
    @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; signerUserId = 1; signerName = '张三' }
)
$start3 = Post-Json "$base/contract/$cid3/sign/start" $h @{ provider = 'esignbao' }
$refusePayload = @{
    providerEventId = "evt-m3-$([guid]::NewGuid().ToString('N'))"
    action          = 'REFUSE'
    signTaskId      = "$($start3.data[0].id)"
    providerTaskId  = $start3.data[0].providerTaskId
    refuseReason    = '三方签署方拒签：金额需复核'
}
$cb3 = Send-Callback 'esignbao' $refusePayload $null $false
Assert '22.refuseCallback' ($cb3.code -eq 0)
$contract3 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid3" -Headers $h
Assert '23.staySigning' ($contract3.data.status -eq 'SIGNING')
$evidence3 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid3/evidence" -Headers $h
Assert '24.refusedTask' ($evidence3.data.tasks[0].status -eq 'REFUSED' -and $evidence3.data.tasks[0].refuseReason -eq '三方签署方拒签：金额需复核')

# 9) 生效 → 归档
$effect = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/effective" -Headers $h
Assert '25.effective' ($effect.code -eq 0 -and $effect.data.status -eq 'EFFECTIVE')
$archive = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/archive" -Headers $h
Assert '26.archive' ($archive.code -eq 0 -and $archive.data.status -eq 'ARCHIVED')

# 10) 未知渠道拒绝
$unknown = Send-Callback 'unknown' $payload1 $null $false
Assert '27.unknownProvider' ($unknown.code -eq 400)

Write-Host '合同 M3 冒烟完成：三方签署 → HMAC 回调 → 工作流推进 → 证据/验签 → 重放幂等 → 验签拒绝+审计 → 链篡改检出 → provider 切换 → 拒签 → 生效归档 全通过。'
