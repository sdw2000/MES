$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Set-CellInline([string]$xml,[string]$cellRef,[string]$text){
  $esc=[System.Security.SecurityElement]::Escape($text)
  $replacement='<c r="' + $cellRef + '" t="inlineStr"><is><t>' + $esc + '</t></is></c>'
  $p1='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*>[\s\S]*?</c>'
  if([regex]::IsMatch($xml,$p1)){
    return [regex]::Replace($xml,$p1,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $replacement },1)
  }
  $p2='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*/>'
  if([regex]::IsMatch($xml,$p2)){
    return [regex]::Replace($xml,$p2,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $replacement },1)
  }
  if($cellRef -notmatch '^([A-Z]+)(\d+)$'){ return $xml }
  $row=$Matches[2]
  $rowP='(<row\b[^>]*\br="' + $row + '"[^>]*>)([\s\S]*?)(</row>)'
  if([regex]::IsMatch($xml,$rowP)){
    return [regex]::Replace($xml,$rowP,[System.Text.RegularExpressions.MatchEvaluator]{
      param($m)
      return $m.Groups[1].Value + $m.Groups[2].Value + $replacement + $m.Groups[3].Value
    },1)
  }
  return $xml
}

function Patch-File([string]$file,[int]$glueRow,[string[]]$cols){
  Write-Host ('Patching: ' + $file)
  $fs=[IO.File]::Open($file,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
  $zip=$null
  try{
    $zip=New-Object IO.Compression.ZipArchive($fs,[IO.Compression.ZipArchiveMode]::Update,$false)
    $entry=$zip.Entries | Where-Object FullName -eq 'xl/worksheets/sheet1.xml' | Select-Object -First 1
    if(-not $entry){ throw 'sheet1.xml not found' }
    $sr=New-Object IO.StreamReader($entry.Open())
    $xml=$sr.ReadToEnd(); $sr.Close()

    foreach($c in $cols){
      $xml=Set-CellInline $xml ($c + $glueRow) '${glueMaterial}'
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

Patch-File 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.xlsx' 19 @('E','F','G','H','I')
Patch-File 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx' 19 @('E','F','G','H','I')
Patch-File 'E:\vue\ERP\public\downloads\normal_1.xlsx' 18 @('D','E','F','G','H')
Patch-File 'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx' 18 @('D','E','F','G','H')
