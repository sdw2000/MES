$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Ensure-Style([string]$cellXml,[string]$styleId){
  if(-not $cellXml){ return $cellXml }
  if([regex]::IsMatch($cellXml,'\bs="\d+"')){
    return [regex]::Replace($cellXml,'\bs="\d+"','s="'+$styleId+'"')
  }
  return [regex]::Replace($cellXml,'^<c\s+','<c s="'+$styleId+'" ')
}

function Get-Cell([string]$rowXml,[string]$ref){
  $m=[regex]::Match($rowXml,'<c\s+r="'+[regex]::Escape($ref)+'"[^>]*>[\s\S]*?</c>')
  if($m.Success){ return $m.Value }
  $m2=[regex]::Match($rowXml,'<c\s+r="'+[regex]::Escape($ref)+'"[^>]*/>')
  if($m2.Success){ return $m2.Value }
  return ''
}

function Set-Cell([string]$rowXml,[string]$ref,[string]$newCell){
  $p1='<c\s+r="'+[regex]::Escape($ref)+'"[^>]*>[\s\S]*?</c>'
  if([regex]::IsMatch($rowXml,$p1)){
    return [regex]::Replace($rowXml,$p1,[System.Text.RegularExpressions.MatchEvaluator]{param($m)$newCell})
  }
  $p2='<c\s+r="'+[regex]::Escape($ref)+'"[^>]*/>'
  if([regex]::IsMatch($rowXml,$p2)){
    return [regex]::Replace($rowXml,$p2,[System.Text.RegularExpressions.MatchEvaluator]{param($m)$newCell})
  }
  return [regex]::Replace($rowXml,'</row>$',($newCell+'</row>'))
}

function Patch-File([string]$file,[int]$startRow,[int]$endRow,[string[]]$resultCols,[string]$judgeCol,[string]$stdCol){
  Write-Host ('Centering: ' + $file)
  $fs=[IO.File]::Open($file,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
  $zip=$null
  try{
    $zip=New-Object IO.Compression.ZipArchive($fs,[IO.Compression.ZipArchiveMode]::Update,$false)
    $entry=$zip.Entries | Where-Object FullName -eq 'xl/worksheets/sheet1.xml' | Select-Object -First 1
    if(-not $entry){ throw 'sheet1.xml not found' }
    $sr=New-Object IO.StreamReader($entry.Open())
    $xml=$sr.ReadToEnd(); $sr.Close()

    for($r=$startRow; $r -le $endRow; $r++){
      $rowPattern='<row[^>]*r="'+$r+'"[^>]*>[\s\S]*?</row>'
      if(-not [regex]::IsMatch($xml,$rowPattern)){ continue }
      $xml=[regex]::Replace($xml,$rowPattern,[System.Text.RegularExpressions.MatchEvaluator]{
        param($m)
        $rowXml=$m.Value
        $stdRef=$stdCol + $r
        $stdCell=Get-Cell $rowXml $stdRef
        $styleId=''
        if($stdCell){
          $sm=[regex]::Match($stdCell,'\bs="(\d+)"')
          if($sm.Success){ $styleId=$sm.Groups[1].Value }
        }
        if(-not $styleId){
          $probe=Get-Cell $rowXml ('C'+$r)
          $sm=[regex]::Match($probe,'\bs="(\d+)"')
          if($sm.Success){ $styleId=$sm.Groups[1].Value }
        }
        if(-not $styleId){ return $rowXml }

        foreach($col in $resultCols){
          $ref=$col + $r
          $cell=Get-Cell $rowXml $ref
          if($cell){
            $rowXml=Set-Cell $rowXml $ref (Ensure-Style $cell $styleId)
          }
        }
        $jRef=$judgeCol + $r
        $jCell=Get-Cell $rowXml $jRef
        if($jCell){
          $rowXml=Set-Cell $rowXml $jRef (Ensure-Style $jCell $styleId)
        }
        return $rowXml
      })
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

Patch-File 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.xlsx' 16 25 @('E','F','G','H','I') 'J' 'K'
Patch-File 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx' 16 25 @('E','F','G','H','I') 'J' 'K'
Patch-File 'E:\vue\ERP\public\downloads\normal_1.xlsx' 15 24 @('D','E','F','G','H') 'J' 'K'
Patch-File 'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx' 15 24 @('D','E','F','G','H') 'J' 'K'
