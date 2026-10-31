# Wave 5.61 M2 印章与本地盖章冒烟：
#   印章列表 → 合同审批通过 → 发起签署 → 未授权用章 403 + 审计 →
#   骑缝章签署（2 页占位 PDF）→ 文件哈希一致 → 证据链 → 验签 →
#   重复签署幂等 → 篡改验签失败 → 拒签（合同停留 SIGNING）→ 作废。
# 前置：gateway(8080)/auth(8081)/workflow(8083)/file(8085)/whale-contract(9006) 就绪；
#       whale-contract 已执行 migration-5.62-contract-esign.sql（含印章种子 1 + admin 授权）。
# 用法：powershell -File scripts/smoke/bw-contract-m2-smoke.ps1


# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $true
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$fileBase = 'http://127.0.0.1:8085'
$mysqlBin = if ($env:MYSQL_BIN) { $env:MYSQL_BIN } else { 'D:\tools\installer\mysql\bin\mysql.exe' }
if (-not $env:MYSQL_PASSWORD) { throw '未设置 MYSQL_PASSWORD：本脚本的审计/篡改数据库断言必需该口令（不再内置默认值）' }
$mysqlPassword = $env:MYSQL_PASSWORD

function Invoke-Sql($sql) {
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
        -ContentType 'application/json' -Body '{"comment":"M2 冒烟同意"}'
    Assert "approve[$title]" ($approve.code -eq 0)
    $after = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid" -Headers $h
    Assert "approved[$title]" ($after.data.status -eq 'APPROVED')
    return $cid
}

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' `
    -Body $cred.JsonBody
Assert '0.login' ($login.code -eq 0 -and $null -ne $login.data.accessToken)
$h = @{ Authorization = "Bearer $($login.data.accessToken)" }

# 1) 印章列表与授权（种子数据）
$sealPage = Invoke-RestMethod -Method Get -Uri "$base/contract/seal/page?current=1&size=20" -Headers $h
Assert '1.sealPage' ($sealPage.code -eq 0 -and $sealPage.data.total -ge 1)
$grantedSealId = ($sealPage.data.records | Where-Object { $_.name -eq '公司合同专用章' } | Select-Object -First 1).id
Assert '2.seedSeal' ($null -ne $grantedSealId)
$grants = Invoke-RestMethod -Method Get -Uri "$base/contract/seal/$grantedSealId/grants" -Headers $h
Assert '3.seedGrant' ($grants.code -eq 0 -and $grants.data.Count -ge 1 -and $grants.data[0].granteeUserId -eq '1')

# 2) 新建一枚未授权印章（用于负向用例）
$noGrantSeal = Post-Json "$base/contract/seal" $h @{
    name         = 'M2 未授权测试章'
    sealType     = 'CONTRACT'
    keeperUserId = 1
    validFrom    = '2026-01-01'
    validTo      = '2030-12-31'
}
Assert '4.createSeal' ($noGrantSeal.code -eq 0 -and $noGrantSeal.data.status -eq 'ENABLED')
$noGrantSealId = $noGrantSeal.data.id

# 3) 合同一：单签署人（admin），用于骑缝章 / 哈希 / 幂等 / 篡改验签
$cid1 = New-ApprovedContract 'M2 冒烟合同（单签）' @(
    @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; contact = '张三'; phone = '13800000001'; signerUserId = 1; signerName = '张三' },
    @{ partyType = 'B'; name = '冒烟供应商有限公司'; creditCode = '91310000MA1K000099'; contact = '李四'; phone = '13800000002'; signerName = '李四' }
)
$start1 = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/sign/start" -Headers $h -ContentType 'application/json' -Body '{}'
Assert '5.startSign' ($start1.code -eq 0 -and $start1.data.Count -eq 1 -and $start1.data[0].status -eq 'PENDING')
$task1 = $start1.data[0].id
$contract1 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1" -Headers $h
Assert '6.contractSigning' ($contract1.data.status -eq 'SIGNING')

# 4) 未授权用章：403 + 审计
$denied = Post-Json "$base/contract/sign-task/$task1/sign" $h @{
    sealId = $noGrantSealId; page = 1; x = 300; y = 200; width = 120; height = 120
}
Assert '7.unauthorized403' ($denied.code -eq 403)
Start-Sleep -Milliseconds 500
$auditHit = Invoke-Sql "SELECT COUNT(*) FROM bluewhale_contract.audit_log WHERE request_uri LIKE '%/sign-task/%' AND biz_status = 0;"
Assert '8.auditDenied' ([int]$auditHit -ge 1)

# 5) 合法签署（骑缝章，占位 PDF 2 页）
$signed = Post-Json "$base/contract/sign-task/$task1/sign" $h @{
    sealId = $grantedSealId; crossPage = $true; width = 240; height = 120; y = 72
}
Assert '9.signed' ($signed.code -eq 0 -and $signed.data.taskStatus -eq 'SIGNED' -and $signed.data.algorithm -eq 'NONE')
Assert '10.stampedPages' ($signed.data.stampedPages -eq 2)
Assert '11.sha256Hex' ($signed.data.signedSha256 -match '^[0-9a-f]{64}$')
Assert '12.contractSigned' ($signed.data.contractStatus -eq 'SIGNED')

# 6) 签章件实际哈希与记录一致（直连文件服务内网接口）
$signedFileId = $signed.data.signedFileId
$tmp = Join-Path $env:TEMP "bw-contract-m2-$([guid]::NewGuid().ToString('N')).pdf"
Invoke-WebRequest -Method Get -Uri "$fileBase/file/internal/$signedFileId/content" -OutFile $tmp | Out-Null
$actualHash = (Get-FileHash -Algorithm SHA256 -Path $tmp).Hash.ToLower()
Assert '13.fileHashMatched' ($actualHash -eq $signed.data.signedSha256)
Remove-Item $tmp -Force

# 7) 证据链与验签
$evidence = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1/evidence" -Headers $h
Assert '14.evidence' ($evidence.code -eq 0 -and $evidence.data.records.Count -eq 1 -and $evidence.data.chainValid -eq $true)
$verify = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/verify" -Headers $h
Assert '15.verifyOk' ($verify.code -eq 0 -and $verify.data.hashMatch -eq $true -and $verify.data.chainValid -eq $true -and $verify.data.signatureValid -eq $true)

# 8) 重复签署幂等（记录数仍为 1）
$repeat = Post-Json "$base/contract/sign-task/$task1/sign" $h @{
    sealId = $grantedSealId; page = 1; x = 100; y = 100; width = 80; height = 80
}
Assert '16.idempotent' ($repeat.code -eq 0 -and $repeat.data.evidenceNo -eq $signed.data.evidenceNo)
$evidence2 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid1/evidence" -Headers $h
Assert '17.recordCountStillOne' ($evidence2.data.records.Count -eq 1)

# 9) 篡改验签失败（临时改动 signed_sha256 后恢复）
$originalHash = $signed.data.signedSha256
$fakeHash = ('f' * 64)
Invoke-Sql "UPDATE bluewhale_contract.ct_sign_record SET signed_sha256 = '$fakeHash' WHERE contract_id = $cid1;" | Out-Null
$tampered = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid1/verify" -Headers $h
Assert '18.tamperDetected' ($tampered.code -eq 0 -and $tampered.data.hashMatch -eq $false -and $tampered.data.signatureValid -eq $false)
Invoke-Sql "UPDATE bluewhale_contract.ct_sign_record SET signed_sha256 = '$originalHash' WHERE contract_id = $cid1;" | Out-Null

# 10) 合同二：双任务（同人 A/B），签署一个 + 拒签一个 → 合同停留 SIGNING
$cid2 = New-ApprovedContract 'M2 冒烟合同（双任务）' @(
    @{ partyType = 'A'; name = '蓝鲸科技有限公司'; creditCode = '91310000MA1K000001'; signerUserId = 1; signerName = '张三' },
    @{ partyType = 'B'; name = '冒烟供应商有限公司'; creditCode = '91310000MA1K000099'; signerUserId = 1; signerName = '李四' }
)
$start2 = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/sign/start" -Headers $h -ContentType 'application/json' -Body '{}'
Assert '19.twoTasks' ($start2.code -eq 0 -and $start2.data.Count -eq 2)
$taskA = ($start2.data | Where-Object { $_.signerParty -eq 'A' }).id
$taskB = ($start2.data | Where-Object { $_.signerParty -eq 'B' }).id
$signA = Post-Json "$base/contract/sign-task/$taskA/sign" $h @{
    sealId = $grantedSealId; page = 1; x = 300; y = 200; width = 120; height = 120
}
Assert '20.signFirst' ($signA.code -eq 0 -and $signA.data.contractStatus -eq 'SIGNING')
$refuse = Post-Json "$base/contract/sign-task/$taskB/refuse" $h @{ reason = '条款金额需复核' }
Assert '21.refuse' ($refuse.code -eq 0)
$afterRefuse = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2" -Headers $h
Assert '22.staySigning' ($afterRefuse.data.status -eq 'SIGNING')
$evidence3 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2/evidence" -Headers $h
$refusedTask = $evidence3.data.tasks | Where-Object { $_.status -eq 'REFUSED' }
Assert '23.refuseReason' ($null -ne $refusedTask -and $refusedTask.refuseReason -eq '条款金额需复核')

# 11) 作废合同二：未完成任务失效，证据保留
$void = Invoke-RestMethod -Method Post -Uri "$base/contract/$cid2/void" -Headers $h
Assert '24.void' ($void.code -eq 0)
$afterVoid = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2" -Headers $h
Assert '25.voided' ($afterVoid.data.status -eq 'VOID')
$evidence4 = Invoke-RestMethod -Method Get -Uri "$base/contract/$cid2/evidence" -Headers $h
Assert '26.evidenceKept' ($evidence4.data.records.Count -eq 1)

Write-Host '合同 M2 冒烟完成：印章/授权 → 403 审计 → 骑缝章签署 → 哈希一致 → 验签 → 幂等 → 篡改检测 → 拒签 → 作废 全通过。'
