param(
    [switch]$FailIfNonMes
)

function Test-IsMesProcess {
    param(
        [Parameter(Mandatory = $true)]
        [int]$ProcessIdToCheck
    )

    try {
        $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessIdToCheck"
        if (-not $processInfo) {
            return $false
        }
        $commandLine = [string]$processInfo.CommandLine
        return $commandLine -like '*com.fine.MesApplication*' -or $commandLine -like '*MES-0.0.1-SNAPSHOT.jar*'
    } catch {
        return $false
    }
}

function Wait-PortReleased {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port,
        [int]$TimeoutSeconds = 15
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        $conn = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
        if (-not $conn) {
            return $true
        }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $deadline)

    return $false
}

$conn = Get-NetTCPConnection -LocalPort 8090 -ErrorAction SilentlyContinue
if (-not $conn) {
    Write-Host '8090 idle'
    exit 0
}

$processIdToCheck = [int]($conn | Select-Object -First 1 -ExpandProperty OwningProcess)
if (-not (Test-IsMesProcess -ProcessIdToCheck $processIdToCheck)) {
    $message = "Port 8090 is occupied by non-MES process $processIdToCheck"
    if ($FailIfNonMes) {
        Write-Error $message
        exit 1
    }
    Write-Host $message
    exit 0
}

Stop-Process -Id $processIdToCheck -Force
if (-not (Wait-PortReleased -Port 8090)) {
    Write-Error 'Port 8090 release timeout'
    exit 1
}

Write-Host "Stopped MES process $processIdToCheck on 8090"
exit 0
