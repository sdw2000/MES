$ErrorActionPreference='Stop'
$base='http://localhost:8090'
$loginBody='{"username":"liuizhiming","password":"123456"}'
$login=Invoke-RestMethod -Method Post -Uri "$base/user/login" -ContentType 'application/json' -Body $loginBody
$tk=if($login.token){$login.token}else{$login.data.token}
if(-not $tk){ throw 'login token empty' }
$h=@{token=$tk; 'X-Token'=$tk}
$batch='20260420'
$inDate='2026-04-20'

$filmRes=Invoke-RestMethod -Method Get -Uri "$base/api/stock/film/list/page?current=1&size=2000" -Headers $h
if($filmRes.code -ne 20000){ throw ('film list fail: ' + $filmRes.msg) }
$filmList=@($filmRes.data.records)
$filmOk=0; $filmSkip=0
foreach($s in $filmList){
  if(-not $s.id){ continue }
  $area=1000
  if($s.availableArea -and [decimal]$s.availableArea -gt 0){ $area=[decimal]$s.availableArea }
  elseif($s.totalArea -and [decimal]$s.totalArea -gt 0){ $area=[decimal]$s.totalArea }
  $detail=@{
    batchNo=$batch
    rollNo=("INIT-"+$batch+"-"+[string]$s.id)
    width=if($s.width){[int]$s.width}else{1000}
    length=if($s.length){[int]$s.length}else{1000}
    area=$area
    qcStatus='qualified'
    location=if($s.location){[string]$s.location}else{'F-INIT'}
    supplier=if($s.supplier){[string]$s.supplier}else{'INIT'}
    inboundDate=$inDate
    status='available'
    remark='init available batch 20260420'
  } | ConvertTo-Json -Depth 5
  try {
    $r=Invoke-RestMethod -Method Post -Uri ("$base/api/stock/film/"+[string]$s.id+"/details") -Headers $h -ContentType 'application/json' -Body $detail
    if($r.code -eq 20000){ $filmOk++ } else { $filmSkip++ }
  } catch { $filmSkip++ }
}

$chemRes=Invoke-RestMethod -Method Get -Uri "$base/api/stock/chemical/list/page?current=1&size=2000" -Headers $h
if($chemRes.code -ne 20000){ throw ('chemical list fail: ' + $chemRes.msg) }
$chemList=@($chemRes.data.records)
$chemOk=0; $chemSkip=0
foreach($s in $chemList){
  if(-not $s.id){ continue }
  $weight=1
  if($s.unitWeight -and [decimal]$s.unitWeight -gt 0){ $weight=[decimal]$s.unitWeight }
  $detail=@{
    batchNo=$batch
    containerNo=("INIT-"+$batch+"-"+[string]$s.id)
    unit=if($s.unit){[string]$s.unit}else{'bucket'}
    weight=$weight
    location=if($s.location){[string]$s.location}else{'C-INIT'}
    supplier=if($s.supplier){[string]$s.supplier}else{'INIT'}
    inboundDate=$inDate
    expiryDate='2027-04-20'
    isOpened=$false
    dangerLevel=1
    status='available'
    remark='init available batch 20260420'
  } | ConvertTo-Json -Depth 5
  try {
    $r=Invoke-RestMethod -Method Post -Uri ("$base/api/stock/chemical/"+[string]$s.id+"/details") -Headers $h -ContentType 'application/json' -Body $detail
    if($r.code -eq 20000){ $chemOk++ } else { $chemSkip++ }
  } catch { $chemSkip++ }
}

Write-Output ("FILM_TOTAL="+$filmList.Count+";FILM_INIT_OK="+$filmOk+";FILM_SKIP="+$filmSkip)
Write-Output ("CHEM_TOTAL="+$chemList.Count+";CHEM_INIT_OK="+$chemOk+";CHEM_SKIP="+$chemSkip)
