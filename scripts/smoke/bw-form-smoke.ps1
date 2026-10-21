$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8080'

function Show($label, $obj) {
    Write-Host "== $label =="
    Write-Host ($obj | ConvertTo-Json -Depth 8 -Compress)
    Write-Host ""
}

$login = Invoke-RestMethod -Method Post -Uri "$base/auth/login" -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}'
$token = $login.data.accessToken
if (-not $token) { throw "login failed" }
Write-Host "login ok, token length=$($token.Length)"
$h = @{ Authorization = "Bearer $token" }

# 1) 建表单定义
$schema = '{"fields":[{"name":"title","type":"text"},{"name":"amount","type":"number"}]}'
$defBody = @{ code = 'fm-e2e-526v2'; name = 'E2E报销单'; schemaJson = $schema; version = 1; status = 1 } | ConvertTo-Json -Compress
$def = Invoke-RestMethod -Method Post -Uri "$base/form/definition" -Headers $h -ContentType 'application/json' -Body $defBody
Show '1.createDefinition' $def
$defId = $def.data.id

# 2) 提交数据
$dataBody = @{ definitionId = $defId; dataJson = '{"title":"差旅","amount":100}'; bizKey = 'BK-F-1' } | ConvertTo-Json -Compress
$sub = Invoke-RestMethod -Method Post -Uri "$base/form/data" -Headers $h -ContentType 'application/json' -Body $dataBody
Show '2.submitData' $sub
$dataId = $sub.data.id

# 3) 分页
$pg = Invoke-RestMethod -Method Get -Uri "$base/form/data/page?definitionCode=fm-e2e-526v2" -Headers $h
Show '3.dataPage' $pg

# 4) 详情
$det = Invoke-RestMethod -Method Get -Uri "$base/form/data/$dataId" -Headers $h
Show '4.dataDetail' $det

# 5) 删除
$del = Invoke-RestMethod -Method Delete -Uri "$base/form/data/$dataId" -Headers $h
Show '5.deleteData' $del

# 6) 删除后详情 -> 404
$after = Invoke-RestMethod -Method Get -Uri "$base/form/data/$dataId" -Headers $h
Show '6.detailAfterDelete' $after

# 7) 分页（删除后）
$pg2 = Invoke-RestMethod -Method Get -Uri "$base/form/data/page?definitionCode=fm-e2e-526v2" -Headers $h
Show '7.dataPageAfterDelete' $pg2

# 8) 负向：定义不存在
$negDef = Invoke-RestMethod -Method Post -Uri "$base/form/data" -Headers $h -ContentType 'application/json' -Body (@{ definitionId = 999999999; dataJson = '{}' } | ConvertTo-Json -Compress)
Show '8.negative.definitionNotFound' $negDef

# 9) 负向：dataJson 为空
$negEmpty = Invoke-RestMethod -Method Post -Uri "$base/form/data" -Headers $h -ContentType 'application/json' -Body (@{ definitionId = $defId; dataJson = '' } | ConvertTo-Json -Compress)
Show '9.negative.dataEmpty' $negEmpty
