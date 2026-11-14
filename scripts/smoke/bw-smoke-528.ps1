
# 公共工具（审计 P2：Assert/Check/Show 已收口到 lib/common.ps1）
$script:SmokeAssertThrow = $false
. "$PSScriptRoot/lib/common.ps1"
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'
$script:token = $null
$script:out = @()

function Call($method, $path, $obj, $tnt = '1') {
    $h = @{ Authorization = "Bearer $script:token"; 'X-Tenant-Id' = "$tnt" }
    try {
        if ($null -ne $obj) {
            $json = $obj | ConvertTo-Json -Depth 12 -Compress
            $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
            $r = Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $h -ContentType 'application/json; charset=utf-8' -Body $bytes -TimeoutSec 20
        } else {
            $r = Invoke-RestMethod -Method $method -Uri "$base$path" -Headers $h -TimeoutSec 20
        }
        return @{ status = 200; body = $r }
    } catch {
        $code = 0
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        $msg = $_.ErrorDetails.Message
        if (-not $msg) { $msg = $_.Exception.Message }
        return @{ status = $code; body = $msg }
    }
}

# ---------- 0. login ----------
$cred = Get-SmokeAdminCredential
$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body $cred.JsonBody -TimeoutSec 20
$script:token = $login.data.accessToken
$script:out += "== login => HTTP 200 tokenLen=$($script:token.Length)"

# ---------- 1. table definition: 3 columns ----------
$tdReq = @{
    tableCode = 'bw_demo_t'; tableName = '演示表'; remark = 'Wave5.28 冒烟'
    columns   = @(
        @{ columnCode = 'name'; columnName = '姓名'; dataType = 'text'; length = 50; nullable = 0; required = 1; sortNo = 0; remark = '姓名列' }
        @{ columnCode = 'grade'; columnName = '等级'; dataType = 'select'; length = 255; sortNo = 1 }
        @{ columnCode = 'score'; columnName = '分数'; dataType = 'number'; precision = 10; scale = 2; sortNo = 2 }
    )
}
$td = Call 'POST' '/form/v1/table-definition' $tdReq
Show '1. create table-definition' $td
$tableId = $td.body.data.id
$colId = @{}
foreach ($c in $td.body.data.columns) { $colId[$c.columnCode] = $c.id }
$script:out += "tableDefinitionId=$tableId columnIds=$($colId | ConvertTo-Json -Compress)"

# ---------- 2. publish table definition ----------
$pub = Call 'POST' "/form/v1/table-definition/$tableId/publish" $null
Show '2. publish table-definition' $pub
$physical = $pub.body.data.physicalTable
$script:out += "physicalTable=$physical"

# ---------- 3. view A ----------
$faReq = @{
    code = 'bw_demo_a'; name = '申请表'; tableDefinitionId = $tableId; remark = '视图A'
    fields = @(
        @{ columnMetaId = $colId['name']; fieldLabel = '姓名'; widgetType = 'text'; showInForm = 1; showInList = 1; editable = 1; sortNo = 0 }
        @{ columnMetaId = $colId['grade']; fieldLabel = '等级'; widgetType = 'select'; showInForm = 1; showInList = 1; editable = 1; sortNo = 1;
            options = @(@{ optionLabel = '优'; optionValue = 'A'; sortNo = 0 }, @{ optionLabel = '良'; optionValue = 'B'; sortNo = 1 }) }
        @{ fieldLabel = '分组标题'; showInForm = 1; sortNo = 2 }
    )
}
$fa = Call 'POST' '/form/v1/definition' $faReq
Show '3. create view A' $fa
$faId = $fa.body.data.id

# ---------- 4. view B (name readonly; grade hidden; score shown) ----------
$fbReq = @{
    code = 'bw_demo_b'; name = '审批表'; tableDefinitionId = $tableId; remark = '视图B'
    fields = @(
        @{ columnMetaId = $colId['name']; fieldLabel = '姓名(只读)'; widgetType = 'text'; showInForm = 1; showInList = 1; editable = 0; sortNo = 0 }
        @{ columnMetaId = $colId['grade']; fieldLabel = '等级(不显示)'; widgetType = 'select'; showInForm = 0; showInList = 0; editable = 1; sortNo = 1 }
        @{ columnMetaId = $colId['score']; fieldLabel = '分数'; widgetType = 'number'; showInForm = 1; showInList = 1; editable = 1; sortNo = 2 }
    )
}
$fb = Call 'POST' '/form/v1/definition' $fbReq
Show '4. create view B' $fb
$fbId = $fb.body.data.id

# ---------- 5. publish views ----------
Show '5a. publish view A' (Call 'POST' "/form/v1/definition/$faId/publish" $null)
Show '5b. publish view B' (Call 'POST' "/form/v1/definition/$fbId/publish" $null)

# ---------- 6. details ----------
Show '6a. detail view A (fields[])' (Call 'GET' "/form/v1/definition/$faId" $null)
Show '6b. detail view B (fields[])' (Call 'GET' "/form/v1/definition/$fbId" $null)

# ---------- 7. submit via view A ----------
$sub = Call 'POST' '/form/v1/data' @{ definitionId = $faId; dataJson = '{"name":"张三","grade":"A"}'; bizKey = 'BK-528-1' }
Show '7. submit via view A' $sub
$dataId = $sub.body.data.id

# ---------- 8. data detail ----------
Show '8. data detail' (Call 'GET' "/form/v1/data/$dataId" $null)

# ---------- 9. negatives ----------
Show '9a. unknown field (expect 400)' (Call 'POST' '/form/v1/data' @{ definitionId = $faId; dataJson = '{"name":"x","hack":1}' })
Show '9b. required missing (expect 400)' (Call 'POST' '/form/v1/data' @{ definitionId = $faId; dataJson = '{"name":""}' })
Show '9c. cross tenant (expect 404)' (Call 'GET' "/form/v1/data/$dataId" $null '2')

$td2 = Call 'POST' '/form/v1/table-definition' @{ tableCode = 'bw_demo_t2'; tableName = '未发布表'; columns = @(@{ columnCode = 'title'; columnName = '标题'; dataType = 'text'; length = 30; sortNo = 0 }) }
$td2Id = $td2.body.data.id
$col2 = @{}
foreach ($c in $td2.body.data.columns) { $col2[$c.columnCode] = $c.id }
$fc = Call 'POST' '/form/v1/definition' @{ code = 'bw_demo_c'; name = '未发布视图'; tableDefinitionId = $td2Id; fields = @(@{ columnMetaId = $col2['title']; fieldLabel = '标题'; widgetType = 'text'; showInForm = 1; sortNo = 0 }) }
$fcId = $fc.body.data.id
Show '9d. submit on unpublished table (expect 400)' (Call 'POST' '/form/v1/data' @{ definitionId = $fcId; dataJson = '{"title":"T"}' })

$script:out += "SMOKE_DONE tableId=$tableId viewA=$faId viewB=$fbId dataId=$dataId physical=$physical t2=$td2Id viewC=$fcId"
$script:out | Set-Content -Path "$env:TEMP\bw-smoke-528.txt" -Encoding utf8
Write-Host "written to $env:TEMP\bw-smoke-528.txt"
