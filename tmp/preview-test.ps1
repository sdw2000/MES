$body = @{ template = 'SLITTING_CORE_LABEL'; data = @{ materialCode = 'TEST-001'; materialName = '测试标签'; spec = '100*500*1000'; rollNo = 'ROLL-TEST-001' } } | ConvertTo-Json -Depth 6
try {
  $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:9123/preview' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 60
  $resp.Content
} catch {
  if ($_.Exception.Response) {
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $reader.ReadToEnd()
  } else {
    $_.Exception.Message
  }
}