$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Set-D10([string]$xml){
  $rowP='<row[^>]*r="10"[^>]*>[\s\S]*?</row>'
  if(-not [regex]::IsMatch($xml,$rowP)){ return $xml }
  return [regex]::Replace($xml,$rowP,[System.Text.RegularExpressions.MatchEvaluator]{
    param($m)
    $row=$m.Value
    $cellP='<c\s+r="D10"([^>]*)>([\s\S]*?)</c>|<c\s+r="D10"([^>]*)/>'
    $sm=[regex]::Match($row,$cellP)
    $style=''
    if($sm.Success){
      $attrs=($sm.Groups[1].Value + ' ' + $sm.Groups[3].Value)
      $sid=[regex]::Match($attrs,'\bs="(\d+)"')
      if($sid.Success){ $style=$sid.Groups[1].Value }
    }
    $newCell= if($style){ '<c r="D10" s="'+$style+'" t="inlineStr"><is><t>${materialCode}</t></is></c>' } else { '<c r="D10" t="inlineStr"><is><t>${materialCode}</t></is></c>' }

    if([regex]::IsMatch($row,'<c\s+r="D10"[^>]*>[\s\S]*?</c>')){
      $row=[regex]::Replace($row,'<c\s+r="D10"[^>]*>[\s\S]*?</c>',$newCell)
    } elseif([regex]::IsMatch($row,'<c\s+r="D10"[^>]*/>')){
      $row=[regex]::Replace($row,'<c\s+r="D10"[^>]*/>',$newCell)
    } else {
      $row=$row -replace '</row>$',($newCell+'</row>')
    }
    return $row
  })
}

function Patch([string]$file){
  if(-not (Test-Path $file)){ return }
  Write-Host ('Patch D10 in ' + $file)
  $fs=[IO.File]::Open($file,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
  $zip=$null
  try{
    $zip=New-Object IO.Compression.ZipArchive($fs,[IO.Compression.ZipArchiveMode]::Update,$false)
    $entry=$zip.Entries | ? FullName -eq 'xl/worksheets/sheet1.xml' | select -First 1
    if(-not $entry){ throw 'sheet1.xml missing' }
    $sr=New-Object IO.StreamReader($entry.Open())
    $x=$sr.ReadToEnd(); $sr.Close()
    $x2=Set-D10 $x
    $entry.Delete()
    $ne=$zip.CreateEntry('xl/worksheets/sheet1.xml')
    $sw=New-Object IO.StreamWriter($ne.Open())
    $sw.Write($x2); $sw.Flush(); $sw.Close()
    ' done'
  } finally { if($zip){$zip.Dispose()}; $fs.Dispose() }
}

$files=@(
  'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx',
  'E:\vue\ERP\public\downloads\normal_1.xlsx',
  'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx',
  'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.xlsx'
)
$files | % { Patch $_ }
