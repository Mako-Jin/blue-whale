
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $cred.JsonBody
$token = $login.data.accessToken
if (-not $token) { throw "login failed: $($login | ConvertTo-Json -Compress)" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

$nodes = '{"nodes":[{"key":"n1","name":"一级审批","assignee":"admin"},{"key":"n2","name":"二级审批","assignee":"user"}]}'
$defBody = @{ code = 'wf-e2e-525v2'; name = 'E2E报销流程'; definitionJson = $nodes; version = 1; status = 1 } | ConvertTo-Json -Compress
$def = Invoke-RestMethod -Method Post -Uri "$base/workflow/definition" -Headers $h -ContentType 'application/json' -Body $defBody
Show '1.createDefinition' $def
$defId = $def.data.id

# ---- 场景 A：闭环同意 ----
$startBody = @{ definitionId = $defId; title = '闭环-A'; businessKey = 'BK-A' } | ConvertTo-Json -Compress
$startA = Invoke-RestMethod -Method Post -Uri "$base/workflow/instance" -Headers $h -ContentType 'application/json' -Body $startBody
Show '2.startA' $startA
$instA = $startA.data.id

$tpA = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instA" -Headers $h
Show '3.tasksA(after start)' $tpA
$taskA0 = $tpA.data.records[0].id

$apA0 = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskA0/approve" -Headers $h -ContentType 'application/json' -Body '{"comment":"一级同意"}'
Show '4.approveA0' $apA0

$tpA2 = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instA" -Headers $h
Show '5.tasksA(after approve0)' $tpA2
$taskA1 = ($tpA2.data.records | Where-Object { $_.status -eq 0 })[0].id

$apA1 = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskA1/approve" -Headers $h -ContentType 'application/json' -Body '{"comment":"二级同意"}'
Show '6.approveA1(last)' $apA1

$detA = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/$instA" -Headers $h
Show '7.instanceA detail' $detA

# ---- 场景 B：驳回 ----
$startBodyB = @{ definitionId = $defId; title = '驳回-B' } | ConvertTo-Json -Compress
$startB = Invoke-RestMethod -Method Post -Uri "$base/workflow/instance" -Headers $h -ContentType 'application/json' -Body $startBodyB
$instB = $startB.data.id
$tpB = Invoke-RestMethod -Method Get -Uri "$base/workflow/task/page?instanceId=$instB" -Headers $h
$taskB0 = $tpB.data.records[0].id

$rjB = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskB0/reject" -Headers $h -ContentType 'application/json' -Body '{"comment":"不同意"}'
Show '8.rejectB0' $rjB

$detB = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/$instB" -Headers $h
Show '9.instanceB detail' $detB

# ---- 场景 C：撤销 ----
$startBodyC = @{ definitionId = $defId; title = '撤销-C' } | ConvertTo-Json -Compress
$startC = Invoke-RestMethod -Method Post -Uri "$base/workflow/instance" -Headers $h -ContentType 'application/json' -Body $startBodyC
$instC = $startC.data.id
$canC = Invoke-RestMethod -Method Post -Uri "$base/workflow/instance/$instC/cancel" -Headers $h
Show '10.cancelC' $canC

$detC = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/$instC" -Headers $h
Show '11.instanceC detail' $detC

# ---- 场景 D：负向 ----
$negRepeat = Invoke-RestMethod -Method Post -Uri "$base/workflow/task/$taskA0/approve" -Headers $h -ContentType 'application/json' -Body '{}'
Show '12.negative.repeatApprove' $negRepeat

$negMissing = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/999999999" -Headers $h
Show '13.negative.instanceNotFound' $negMissing

# ---- 实例分页 ----
$ipage = Invoke-RestMethod -Method Get -Uri "$base/workflow/instance/page?definitionCode=wf-e2e-525v2" -Headers $h
Show '14.instancePage' $ipage
