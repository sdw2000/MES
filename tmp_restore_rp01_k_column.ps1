$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-SheetXml([System.IO.Compression.ZipArchive]$zip){
  $entry=$zip.Entries | Where-Object FullName -eq 'xl/worksheets/sheet1.xml' | Select-Object -First 1
  if(-not $entry){ throw 'sheet1.xml not found' }
  $sr=New-Object IO.StreamReader($entry.Open())
  $xml=$sr.ReadToEnd(); $sr.Close()
  return @{ Entry=$entry; Xml=$xml }
}

function Get-CellXml([string]$rowXml,[string]$cellRef){
  $m=[regex]::Match($rowXml,'<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*>[\s\S]*?</c>')
  if($m.Success){ return $m.Value }
  $m2=[regex]::Match($rowXml,'<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*/>')
  if($m2.Success){ return $m2.Value }
  return ''
}

function Replace-CellInRow([string]$rowXml,[string]$cellRef,[string]$newCellXml){
  $pattern1='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*>[\s\S]*?</c>'
  if([regex]::IsMatch($rowXml,$pattern1)){
    return [regex]::Replace($rowXml,$pattern1,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $newCellXml },1)
  }
  $pattern2='<c\s+r="' + [regex]::Escape($cellRef) + '"[^>]*/>'
  if([regex]::IsMatch($rowXml,$pattern2)){
    return [regex]::Replace($rowXml,$pattern2,[System.Text.RegularExpressions.MatchEvaluator]{ param($m) $newCellXml },1)
  }
  # row里没有K单元格就追加
  return $rowXml -replace '</row>$', ($newCellXml + '</row>')
}

$backup='E:\vue\ERP\public\downloads\quality-report-template-rp01.backup2.xlsx'
$targets=@(
  'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.xlsx',
  'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx'
)

$fsB=[IO.File]::Open($backup,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::ReadWrite)
$zipB=New-Object IO.Compression.ZipArchive($fsB,[IO.Compression.ZipArchiveMode]::Read,$false)
try{
  $b=Get-SheetXml $zipB
  $backupXml=[string]$b.Xml

  foreach($t in $targets){
    Write-Host ('Restore K column from backup -> ' + $t)
    $fs=[IO.File]::Open($t,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
    $zip=$null
    try{
      $zip=New-Object IO.Compression.ZipArchive($fs,[IO.Compression.ZipArchiveMode]::Update,$false)
      $s=Get-SheetXml $zip
      $xml=[string]$s.Xml

      foreach($r in 16..25){
        $rowPattern='<row[^>]*r="' + $r + '"[^>]*>[\s\S]*?</row>'
        $rowB=[regex]::Match($backupXml,$rowPattern).Value
        if(-not $rowB){ continue }
        $kRef='K' + $r
        $kCell=Get-CellXml $rowB $kRef
        if(-not $kCell){ continue }

        $xml=[regex]::Replace($xml,$rowPattern,[System.Text.RegularExpressions.MatchEvaluator]{
          param($m)
          $rowNow=$m.Value
          return Replace-CellInRow $rowNow $kRef $kCell
        },1)
      }

      $s.Entry.Delete()
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
}
finally{
  $zipB.Dispose()
  $fsB.Dispose()
}
