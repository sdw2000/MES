Get-CimInstance Win32_Process |
  Where-Object { $_.CommandLine -match 'start-gateway\.ps1' } |
  Select-Object ProcessId,CommandLine |
  Format-List