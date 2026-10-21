$ErrorActionPreference = 'Stop'
$script:token = $null
$gateway = 'http://127.0.0.1:8080'

function Api([string]$method, [string]$path, $body) {
    $headers = @{ Authorization = "Bearer $script:token" }
    if ($null -ne $body) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 20 -Compress))
        return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers `
            -ContentType 'application/json;charset=utf-8' -Body $bytes
    }
    return Invoke-RestMethod -Method $method -Uri ($gateway + $path) -Headers $headers
}

function Check([string]$name, $result, [int]$expectCode = 0) {
    if ($result.code -ne $expectCode) {
        throw "$name failed: code=$($result.code) message=$($result.message)"
    }
    Write-Host "OK   $name"
}

function Assert([string]$name, [bool]$condition) {
    if (-not $condition) {
        throw "$name assertion failed"
    }
    Write-Host "OK   $name"
}

function Get-Bpmn([string]$id) {
    $headers = @{ Authorization = "Bearer $script:token" }
    $response = Invoke-WebRequest -Method Get -Uri "$gateway/workflow/definition/$id/bpmn" -Headers $headers -UseBasicParsing
    return $response.Content
}

# ---------- 1. login ----------
$login = Invoke-RestMethod -Method Post -Uri "$gateway/auth/login" -ContentType 'application/json;charset=utf-8' `
    -Body ([System.Text.Encoding]::UTF8.GetBytes('{"username":"admin","password":"123456"}'))
$script:token = $login.data.accessToken
$stamp = Get-Date -Format 'HHmmss'
Write-Host "OK   login"

# ---------- 2. definition with canvas coordinates ----------
$model = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT'; x = 100; y = 80 },
        @{ key = 't1'; type = 'USER_TASK'; name = 'Approve'; assignee = 'admin'; x = 240; y = 160 },
        @{ key = 'end'; type = 'END_EVENT'; x = 420; y = 170 }
    )
    flows = @(
        @{ key = 'f1'; from = 'start'; to = 't1' },
        @{ key = 'f2'; from = 't1'; to = 'end' }
    )
}
$definitionJson = ($model | ConvertTo-Json -Depth 20 -Compress)
$created = Api 'Post' '/workflow/definition' @{
    code           = ('smoke_bpmndi_' + $stamp)
    name           = 'bpmndi-smoke'
    definitionJson = $definitionJson
}
Check 'create definition with x/y' $created
$definitionId = $created.data.id

# ---------- 3. export writes bpmndi ----------
$xml = Get-Bpmn $definitionId
Assert 'export contains BPMNDiagram' ($xml.Contains('<bpmndi:BPMNDiagram'))
Assert 'export contains BPMNPlane bpmnElement' ($xml.Contains('bpmnElement="smoke_bpmndi_' + $stamp + '"'))
Assert 'export contains shape for t1' ($xml.Contains('bpmnElement="t1"'))
Assert 'export contains bounds x=240 y=160' ($xml.Contains('<dc:Bounds x="240" y="160" width="120" height="60"/>'))
Assert 'export contains event bounds' ($xml.Contains('<dc:Bounds x="100" y="80" width="36" height="36"/>'))

# ---------- 4. re-import keeps coordinates ----------
$imported = Api 'Post' '/workflow/definition/bpmn' @{
    code     = ('smoke_bpmndi_rt_' + $stamp)
    name     = 'bpmndi-roundtrip'
    bpmnXml  = $xml
}
Check 'import exported bpmn' $imported
$importedId = $imported.data.id
$importedModel = $imported.data.definitionJson | ConvertFrom-Json
$importedT1 = @($importedModel.nodes | Where-Object { $_.key -eq 't1' })[0]
$importedStart = @($importedModel.nodes | Where-Object { $_.key -eq 'start' })[0]
Assert 'imported t1 x/y preserved' ($importedT1.x -eq 240 -and $importedT1.y -eq 160)
Assert 'imported start x/y preserved' ($importedStart.x -eq 100 -and $importedStart.y -eq 80)

# ---------- 5. export without coordinates omits bpmndi ----------
$plainModel = @{
    type  = 'stateMachine'
    nodes = @(
        @{ key = 'start'; type = 'START_EVENT' },
        @{ key = 't1'; type = 'USER_TASK'; name = 'Approve'; assignee = 'admin' },
        @{ key = 'end'; type = 'END_EVENT' }
    )
    flows = @(
        @{ key = 'f1'; from = 'start'; to = 't1' },
        @{ key = 'f2'; from = 't1'; to = 'end' }
    )
}
$plain = Api 'Post' '/workflow/definition' @{
    code           = ('smoke_bpmndi_plain_' + $stamp)
    name           = 'bpmndi-plain'
    definitionJson = ($plainModel | ConvertTo-Json -Depth 20 -Compress)
}
Check 'create definition without x/y' $plain
$plainId = $plain.data.id
$plainXml = Get-Bpmn $plainId
Assert 'export without coordinates omits BPMNDiagram' (-not $plainXml.Contains('<bpmndi:BPMNDiagram'))
Assert 'export without coordinates omits BPMNShape' (-not $plainXml.Contains('<bpmndi:BPMNShape'))

# ---------- 6. cleanup ----------
Api 'Delete' "/workflow/definition/$definitionId" $null | Out-Null
Api 'Delete' "/workflow/definition/$importedId" $null | Out-Null
Api 'Delete' "/workflow/definition/$plainId" $null | Out-Null
Write-Host 'OK   cleanup'

Write-Host 'SMOKE-ALL-PASS'
