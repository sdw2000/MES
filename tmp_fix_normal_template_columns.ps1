$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

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
    return [regex]::Replace($rowXml,$p1,[System.Text.RegularExpressions.MatchEvaluator]{param($m)$newCell},1)
  }
  $p2='<c\s+r="'+[regex]::Escape($ref)+'"[^>]*/>'
  if([regex]::IsMatch($rowXml,$p2)){
    return [regex]::Replace($rowXml,$p2,[System.Text.RegularExpressions.MatchEvaluator]{param($m)$newCell},1)
  }
  return $rowXml -replace '</row>$',($newCell+'</row>')
}

function Patch-File([string]$file){
  Write-Host ('Patch: ' + $file)
  $fs=[IO.File]::Open($file,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::ReadWrite)
  $zip=$null
  try{
    $zip=New-Object IO.Compression.ZipArchive($fs,[IO.Compression.ZipArchiveMode]::Update,$false)
    $entry=$zip.Entries | ? FullName -eq 'xl/worksheets/sheet1.xml' | select -First 1
    if(-not $entry){ throw 'sheet1.xml missing' }
    $sr=New-Object IO.StreamReader($entry.Open())
    $xml=$sr.ReadToEnd(); $sr.Close()

    # 1) 取消J:K合并，避免判定结果覆盖检测标准显示
    $xml=[regex]::Replace($xml,'<mergeCell\s+ref="J(1[5-9]|2[0-4]):K\1"\s*/>','')
    $xml=[regex]::Replace($xml,'<mergeCells\s+count="(\d+)"\s*>([\s\S]*?)</mergeCells>',[System.Text.RegularExpressions.MatchEvaluator]{
      param($m)
      $inner=$m.Groups[2].Value
      $list=[regex]::Matches($inner,'<mergeCell\s+ref="[^"]+"\s*/>') | % { $_.Value }
      if($list.Count -eq 0){ return '' }
      return '<mergeCells count="'+$list.Count+'">'+$inner+'</mergeCells>'
    },1)

    # 2) 把原J列“检测标准”文本复制到K列，J留给判定结果动态写入
    foreach($r in 15..24){
      $rowPattern='<row[^>]*r="'+$r+'"[^>]*>[\s\S]*?</row>'
      if(-not [regex]::IsMatch($xml,$rowPattern)){ continue }
      $xml=[regex]::Replace($xml,$rowPattern,[System.Text.RegularExpressions.MatchEvaluator]{
        param($m)
        $row=$m.Value
        $jRef='J'+$r
        $kRef='K'+$r
        $jCell=Get-Cell $row $jRef
        $kCell=Get-Cell $row $kRef
        if(-not $jCell){ return $row }

        $newK=$jCell -replace 'r="'+$jRef+'"','r="'+$kRef+'"'
        if($kCell){
          $kStyle=[regex]::Match($kCell,'\bs="(\d+)"')
          if($kStyle.Success){
            if([regex]::IsMatch($newK,'\bs="\d+"')){
              $newK=[regex]::Replace($newK,'\bs="\d+"','s="'+$kStyle.Groups[1].Value+'"',1)
            } else {
              $newK=$newK -replace '^<c\s+','<c s="'+$kStyle.Groups[1].Value+'" '
            }
          }
        }
        $row=Set-Cell $row $kRef $newK
        return $row
      },1)
    }

    $entry.Delete()
    $ne=$zip.CreateEntry('xl/worksheets/sheet1.xml')
    $sw=New-Object IO.StreamWriter($ne.Open())
    $sw.Write($xml)
    $sw.Flush(); $sw.Close()
    Write-Host '  done'
  }
  finally{
    if($zip){$zip.Dispose()}
    $fs.Dispose()
  }
}

Patch-File 'E:\vue\ERP\public\downloads\normal_1.placeholders.xlsx'
Patch-File 'E:\vue\ERP\public\downloads\normal_1.xlsx'
