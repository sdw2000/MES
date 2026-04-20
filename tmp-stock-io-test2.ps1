$ErrorActionPreference='Stop'
$base='http://localhost:8090'
$login=Invoke-RestMethod -Method Post -Uri "$base/user/login" -ContentType 'application/json' -Body '{"username":"liuizhiming","password":"123456"}'
$tk=if($login.token){$login.token}else{$login.data.token}
if(-not $tk){ throw 'login token empty' }
$h=@{token=$tk; 'X-Token'=$tk}
$results = New-Object System.Collections.ArrayList
function Add-Res($name,$ok,$detail){ [void]$results.Add([pscustomobject]@{name=$name; ok=$ok; detail=$detail}) }

try {
  $b='UTSL'+(Get-Date -Format 'MMddHHmmss')
  $payload=@{materialCode='1011-R02-2307-G03-0350';productName='AUTO-TEST';batchNo=$b;thickness=30;width=500;length=1000;rolls=1;applicant='liuizhiming';applyDept='warehouse';remark='process=SLITTING;autotest'}|ConvertTo-Json
  $r=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound" -Headers $h -ContentType 'application/json' -Body $payload
  $ok=($r.code -eq 20000 -and $r.data -and $r.data.status -eq 1)
  Add-Res 'tape_inbound_slitting' $ok ("code="+$r.code+";status="+$r.data.status+";id="+$r.data.id)
} catch { Add-Res 'tape_inbound_slitting' $false $_.Exception.Message }

try {
  $b='UTCO'+(Get-Date -Format 'MMddHHmmss')
  $payload=@{materialCode='1011-R02-2307-G03-0350';productName='AUTO-TEST';batchNo=$b;thickness=30;width=500;length=1000;rolls=1;applicant='liuizhiming';applyDept='warehouse';remark='process=COATING;autotest'}|ConvertTo-Json
  $c=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound" -Headers $h -ContentType 'application/json' -Body $payload
  $id=$c.data.id
  $fail=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound/$id/approve?approved=true&auditor=qa" -Headers $h
  $pass=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound/$id/approve?approved=true&auditor=qa&scannedLocation=A-01" -Headers $h
  $ok=($c.code -eq 20000 -and $fail.code -ne 20000 -and $pass.code -eq 20000)
  Add-Res 'tape_inbound_coating_location_rule' $ok ("create="+$c.code+";noLoc="+$fail.code+";withLoc="+$pass.code+";id="+$id)
} catch { Add-Res 'tape_inbound_coating_location_rule' $false $_.Exception.Message }

try {
  $b='UTRW'+(Get-Date -Format 'MMddHHmmss')
  $payload=@{materialCode='1011-R02-2307-G03-0350';productName='AUTO-TEST';batchNo=$b;thickness=30;width=500;length=1000;rolls=1;applicant='liuizhiming';applyDept='warehouse';remark='process=REWINDING;autotest'}|ConvertTo-Json
  $c=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound" -Headers $h -ContentType 'application/json' -Body $payload
  $id=$c.data.id
  $fail=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound/$id/approve?approved=true&auditor=qa" -Headers $h
  $pass=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/inbound/$id/approve?approved=true&auditor=qa&scannedLocation=R-01" -Headers $h
  $ok=($c.code -eq 20000 -and $fail.code -ne 20000 -and $pass.code -eq 20000)
  Add-Res 'tape_inbound_rewinding_location_rule' $ok ("create="+$c.code+";noLoc="+$fail.code+";withLoc="+$pass.code+";id="+$id)
} catch { Add-Res 'tape_inbound_rewinding_location_rule' $false $_.Exception.Message }

try {
  $list=Invoke-RestMethod -Method Get -Uri "$base/api/tape-stock/list?page=1&size=20" -Headers $h
  $rec=$list.data.records | Where-Object { $_.totalRolls -ge 1 } | Select-Object -First 1
  if(-not $rec){ throw 'no tape stock' }
  $outReq=@{stockId=[long]$rec.id;materialCode=$rec.materialCode;productName=$rec.productName;batchNo=$rec.batchNo;rolls=1;applicant='liuizhiming';applyDept='warehouse';remark='autotest-out'}|ConvertTo-Json
  $create=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/outbound" -Headers $h -ContentType 'application/json' -Body $outReq
  $oid=$create.data.id
  $approve=Invoke-RestMethod -Method Post -Uri "$base/api/tape-stock/outbound/$oid/approve?approved=true&auditor=qa" -Headers $h
  $ok=($create.code -eq 20000 -and $approve.code -eq 20000)
  Add-Res 'tape_outbound_apply_approve' $ok ("create="+$create.code+";approve="+$approve.code+";oid="+$oid+";stockId="+$rec.id)
} catch { Add-Res 'tape_outbound_apply_approve' $false $_.Exception.Message }

$filmId=$null
try {
  $fl=Invoke-RestMethod -Method Get -Uri "$base/api/stock/film/list/page?current=1&size=20" -Headers $h
  $f=($fl.data.records | Select-Object -First 1)
  if(-not $f){ throw 'no film stock' }
  $filmId=[long]$f.id
  $inBody=@{batchNo=('FMIN'+(Get-Date -Format 'MMddHHmmss'));rollNo=('R'+(Get-Date -Format 'HHmmss'));area=5;location='F-01';supplier='autotest';status='available'}|ConvertTo-Json
  $cin=Invoke-RestMethod -Method Post -Uri "$base/api/stock/film/$filmId/details" -Headers $h -ContentType 'application/json' -Body $inBody
  $ok=($cin.code -eq 20000 -and $cin.data.id)
  Add-Res 'film_inbound_create_detail' $ok ("code="+$cin.code+";filmId="+$filmId+";detailId="+$cin.data.id)
} catch { Add-Res 'film_inbound_create_detail' $false $_.Exception.Message }

try {
  if(-not $filmId){ throw 'film id missing' }
  $av=Invoke-RestMethod -Method Get -Uri "$base/api/stock/film/$filmId/available" -Headers $h
  $d=$av.data | Select-Object -First 1
  if(-not $d){ throw 'no film detail' }
  $did=[long]$d.id
  $lock=Invoke-RestMethod -Method Post -Uri "$base/api/stock/film/lock" -Headers $h -ContentType 'application/json' -Body (@{filmStockId=$filmId;lockArea=1;lockRolls=1;detailIds=@($did)}|ConvertTo-Json)
  $out=Invoke-RestMethod -Method Post -Uri "$base/api/stock/film/outbound" -Headers $h -ContentType 'application/json' -Body (@{filmStockId=$filmId;outArea=1;outRolls=1;detailIds=@($did);purpose='autotest';outboundBy='liuizhiming'}|ConvertTo-Json)
  $ok=($lock.code -eq 20000 -and $out.code -eq 20000)
  Add-Res 'film_outbound_lock_outbound' $ok ("lock="+$lock.code+";out="+$out.code+";filmId="+$filmId+";detailId="+$did)
} catch { Add-Res 'film_outbound_lock_outbound' $false $_.Exception.Message }

$chemId=$null
try {
  $cl=Invoke-RestMethod -Method Get -Uri "$base/api/stock/chemical/list/page?current=1&size=20" -Headers $h
  $c=($cl.data.records | Select-Object -First 1)
  if(-not $c){ throw 'no chemical stock' }
  $chemId=[long]$c.id
  $inBody=@{batchNo=('CHIN'+(Get-Date -Format 'MMddHHmmss'));containerNo=('B'+(Get-Date -Format 'HHmmss'));unit='TONG';weight=1;location='C-01';supplier='autotest';status='available'}|ConvertTo-Json
  $cin=Invoke-RestMethod -Method Post -Uri "$base/api/stock/chemical/$chemId/details" -Headers $h -ContentType 'application/json' -Body $inBody
  $ok=($cin.code -eq 20000 -and $cin.data.id)
  Add-Res 'chemical_inbound_create_detail' $ok ("code="+$cin.code+";chemId="+$chemId+";detailId="+$cin.data.id)
} catch { Add-Res 'chemical_inbound_create_detail' $false $_.Exception.Message }

try {
  if(-not $chemId){ throw 'chem id missing' }
  $av=Invoke-RestMethod -Method Get -Uri "$base/api/stock/chemical/$chemId/available" -Headers $h
  $d=$av.data | Select-Object -First 1
  if(-not $d){ throw 'no chem detail' }
  $did=[long]$d.id
  $lock=Invoke-RestMethod -Method Post -Uri "$base/api/stock/chemical/lock" -Headers $h -ContentType 'application/json' -Body (@{chemicalStockId=$chemId;lockQuantity=1;detailIds=@($did)}|ConvertTo-Json)
  $out=Invoke-RestMethod -Method Post -Uri "$base/api/stock/chemical/outbound" -Headers $h -ContentType 'application/json' -Body (@{chemicalStockId=$chemId;outQuantity=1;outWeight=0.1;detailIds=@($did);purpose='autotest';outboundBy='liuizhiming'}|ConvertTo-Json)
  $ok=($lock.code -eq 20000 -and $out.code -eq 20000)
  Add-Res 'chemical_outbound_lock_outbound' $ok ("lock="+$lock.code+";out="+$out.code+";chemId="+$chemId+";detailId="+$did)
} catch { Add-Res 'chemical_outbound_lock_outbound' $false $_.Exception.Message }

$fail = ($results | Where-Object { -not $_.ok }).Count
$summary=[pscustomobject]@{total=$results.Count;pass=($results.Count-$fail);fail=$fail;items=$results}
$summary | ConvertTo-Json -Depth 6