$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Set-CellInline([string]$xml,[string]$cellRef,[string]$text){
  $esc=[System.Security.SecurityElement]::Escape($text)
  $replacement='<c r="' + $cellRef + '" t="inlineStr"><is><t>' + $esc + '</t></is></c>'
  $cellPattern='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*>[\s\S]*?</c>'
  if([regex]::IsMatch($xml,$cellPattern)){
    return [regex]::Replace($xml,$cellPattern,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $replacement },1)
  }
    $selfClosingPattern='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*/>'
    if([regex]::IsMatch($xml,$selfClosingPattern)){
      return [regex]::Replace($xml,$selfClosingPattern,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $replacement },1)
    }

  if($cellRef -notmatch '^([A-Z]+)(\d+)$'){ return $xml }
  $rowNum=$Matches[2]
  $rowPattern='(<row\b[^>]*\br="' + $rowNum + '"[^>]*>)([\s\S]*?)(</row>)'
  if([regex]::IsMatch($xml,$rowPattern)){
    return [regex]::Replace($xml,$rowPattern,[System.Text.RegularExpressions.MatchEvaluator]{
      param($m)
      $rowBody=$m.Groups[2].Value
      $inCell='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*>[\s\S]*?</c>'
      $inSelf='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*/>'
      if([regex]::IsMatch($rowBody,$inCell)){
        $rowBody=[regex]::Replace($rowBody,$inCell,[System.Text.RegularExpressions.MatchEvaluator]{ param($x) $replacement },1)
      } elseif([regex]::IsMatch($rowBody,$inSelf)){
        $rowBody=[regex]::Replace($rowBody,$inSelf,[System.Text.RegularExpressions.MatchEvaluator]{ param($x) $replacement },1)
      } else {
        $rowBody=$rowBody + $replacement
      }
      return $m.Groups[1].Value + $rowBody + $m.Groups[3].Value
    },1)
  }
  return $xml
}

function Remove-Merges([string]$xml,[int]$startRow,[int]$endRow){
  $xml=[regex]::Replace($xml,'<mergeCell\s+ref="([^"]+)"\s*/>',[System.Text.RegularExpressions.MatchEvaluator]{
    param($m)
    $ref=$m.Groups[1].Value
    if($ref -notmatch '^([A-Z]+)(\d+):([A-Z]+)(\d+)$'){ return $m.Value }
    $c1=$Matches[1]; $r1=[int]$Matches[2]; $c2=$Matches[3]; $r2=[int]$Matches[4]
    if($r1 -ne $r2){ return $m.Value }
    if($r1 -lt $startRow -or $r1 -gt $endRow){ return $m.Value }
    if((($c1 -eq 'D') -and ($c2 -eq 'H')) -or (($c1 -eq 'E') -and ($c2 -eq 'I')) -or (($c1 -eq 'D') -and ($c2 -eq 'I'))){ return '' }
    return $m.Value
  })

  $xml=[regex]::Replace($xml,'<mergeCells\s+count="(\d+)"\s*>([\s\S]*?)</mergeCells>',[System.Text.RegularExpressions.MatchEvaluator]{
    param($m)
    $inner=$m.Groups[2].Value
    $list=[regex]::Matches($inner,'<mergeCell\s+ref="[^"]+"\s*/>')
    if($list.Count -eq 0){ return '' }
    return '<mergeCells count="' + $list.Count + '">' + $inner + '</mergeCells>'
  },1)

  return $xml
}

function Rewrite-Template($file,[int]$baseRow,[string[]]$cols){
  Write-Host ('Patching: ' + $file)
  $items=@('width','length','baseThickness','adhesiveType','totalThickness','peelStrength','unwindForce','extraQcItem1','initialTack','heatResistance')

  $fs=[System.IO.File]::Open($file,[System.IO.FileMode]::Open,[System.IO.FileAccess]::ReadWrite,[System.IO.FileShare]::ReadWrite)
  $zip=$null
  try{
    $zip=New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Update,$false)
    $entry=$zip.Entries | Where-Object FullName -eq 'xl/worksheets/sheet1.xml' | Select-Object -First 1
    if(-not $entry){ throw 'sheet1.xml not found' }
    $sr=New-Object IO.StreamReader($entry.Open())
    $xml=$sr.ReadToEnd(); $sr.Close()

    $xml=Remove-Merges $xml 15 25

    for($r=0;$r -lt $items.Count;$r++){
      $row=$baseRow + $r
      for($i=0;$i -lt 5;$i++){
        $col=$cols[$i]
        $ref=$col + $row
        $ph='${' + $items[$r] + '_actual' + ($i+1) + '}'
        $xml=Set-CellInline $xml $ref $ph
      }
    }

    $entry.Delete()
    $newEntry=$zip.CreateEntry('xl/worksheets/sheet1.xml')
    $sw=New-Object IO.StreamWriter($newEntry.Open())
    $sw.Write($xml)
    $sw.Flush(); $sw.Close()

    Write-Host '  done'
  }
  finally{
    if($zip){ $zip.Dispose() }
    $fs.Dispose()
  }
}

Rewrite-Template 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx' 16 @('E','F','G','H','I')
Rewrite-Template 'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx' 15 @('D','E','F','G','H')
