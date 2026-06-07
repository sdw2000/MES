$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Get-CellTextMap($xml,[hashtable]$sst) {
  $map=@{}
  $rowRe=[regex]'<row\b[^>]*r="(?<r>\d+)"[^>]*>(?<body>[\s\S]*?)</row>'
  $cellRe=[regex]'<c\b[^>]*r="(?<ref>[A-Z]+\d+)"(?<attrs>[^>]*)>(?<inner>[\s\S]*?)</c>'
  foreach($rm in $rowRe.Matches($xml)) {
    $r=[int]$rm.Groups['r'].Value
    if($r -lt 15 -or $r -gt 24){ continue }
    $body=$rm.Groups['body'].Value
    foreach($cm in $cellRe.Matches($body)) {
      $ref=$cm.Groups['ref'].Value
      if($ref -notmatch '^[D-I](1[5-9]|2[0-4])$'){ continue }
      $attrs=$cm.Groups['attrs'].Value
      $inner=$cm.Groups['inner'].Value
      $txt=''
      if($attrs -match 't="inlineStr"') {
        $tm=[regex]::Match($inner,'<t[^>]*>([\s\S]*?)</t>')
        if($tm.Success){ $txt=$tm.Groups[1].Value }
      } elseif($attrs -match 't="s"') {
        $vm=[regex]::Match($inner,'<v>(\d+)</v>')
        if($vm.Success) {
          $idx=[int]$vm.Groups[1].Value
          if($sst.ContainsKey($idx)){ $txt=$sst[$idx] }
        }
      } else {
        $vm=[regex]::Match($inner,'<v>([\s\S]*?)</v>')
        if($vm.Success){ $txt=$vm.Groups[1].Value }
      }
      $map[$ref]=$txt
    }
  }
  return $map
}

function Inspect-Xlsx($file) {
  Write-Host "=== $file ==="
  $fs=[System.IO.File]::Open($file,[System.IO.FileMode]::Open,[System.IO.FileAccess]::Read,[System.IO.FileShare]::ReadWrite)
  $zip=$null
  try {
    $zip=New-Object System.IO.Compression.ZipArchive($fs,[System.IO.Compression.ZipArchiveMode]::Read,$false)
    $sst=@{}
    $sstEntry=$zip.Entries | Where-Object FullName -eq 'xl/sharedStrings.xml' | Select-Object -First 1
    if($sstEntry) {
      $sr=New-Object IO.StreamReader($sstEntry.Open())
      $ssx=$sr.ReadToEnd()
      $sr.Close()
      $si=[regex]::Matches($ssx,'<si>([\s\S]*?)</si>')
      for($i=0;$i -lt $si.Count;$i++) {
        $m=[regex]::Match($si[$i].Groups[1].Value,'<t[^>]*>([\s\S]*?)</t>')
        if($m.Success){ $sst[$i]=$m.Groups[1].Value } else { $sst[$i]='' }
      }
    }

    $sheet=$zip.Entries | Where-Object FullName -eq 'xl/worksheets/sheet1.xml' | Select-Object -First 1
    if(-not $sheet){ Write-Host 'No sheet1'; return }

    $sr2=New-Object IO.StreamReader($sheet.Open())
    $sx=$sr2.ReadToEnd()
    $sr2.Close()

    $cell=Get-CellTextMap $sx $sst
    $rows=15..24
    $cols='D','E','F','G','H','I'
    foreach($r in $rows) {
      $line=@()
      foreach($c in $cols) {
        $ref="$c$r"
        $v=$cell[$ref]
        if($null -eq $v){ $v='(empty)' }
        $kind = if($v -match '\$\{[^}]+\}' -or $v -match '\{\{[^}]+\}\}' -or $v -match '%%[^%]+%%'){ 'PH' } else { 'TXT' }
        $short=$v
        if($short.Length -gt 36){ $short=$short.Substring(0,36)+'...' }
        $line += "${ref}=${kind}:${short}"
      }
      Write-Host ($line -join ' | ')
    }

    $allPh=[regex]::Matches(($sx + "`n" + ($sst.Values -join "`n")),'\$\{[^}]+\}|\{\{[^}]+\}\}|%%[^%]+%%') | ForEach-Object Value
    Write-Host ('PlaceholdersCount=' + $allPh.Count)
    ($allPh | Group-Object | Sort-Object Count -Descending | Select-Object -First 30) | ForEach-Object {
      Write-Host ("  " + $_.Count + " x " + $_.Name)
    }
  }
  finally {
    if($zip){ $zip.Dispose() }
    $fs.Dispose()
  }
}

Inspect-Xlsx 'E:\vue\ERP\public\downloads\quality-report-template-rp01.full.placeholders.xlsx'
Inspect-Xlsx 'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx'
