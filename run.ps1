$env:JAVA_HOME = "C:\Users\Admin\.jdks\jdk-21.0.12.1+1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$pgCtl = "C:\Users\Admin\tools\pgsql\bin\pg_ctl.exe"
$pgData = "C:\Users\Admin\tools\pgsql\data"
if (Test-Path $pgCtl) {
    & $pgCtl -D $pgData status 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[run.ps1] Starting PostgreSQL service..." -ForegroundColor Cyan
        & $pgCtl -D $pgData -l "C:\Users\Admin\tools\pgsql\postgres.log" start
    }
}

function Stop-BackendOn8080 {
    $pids = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -gt 0 }

    if ($pids) {
        foreach ($pidToKill in $pids) {
            Write-Host "[run.ps1] Port 8080 is in use by PID $pidToKill. Stopping..." -ForegroundColor Yellow
            & taskkill.exe /PID $pidToKill /T /F 2>$null | Out-Null
        }
        Start-Sleep -Milliseconds 800
    }
}

function Stop-ExistingSpringBoot {
    Stop-BackendOn8080

    # Also terminate any orphan partygameonline java process
    Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like "*partygameonline*" -and $_.Name -like "*java*" } |
        ForEach-Object {
            Write-Host "[run.ps1] Stopping existing partygameonline Java process (PID $($_.ProcessId))..." -ForegroundColor Yellow
            & taskkill.exe /PID $_.ProcessId /T /F 2>$null | Out-Null
        }
}

function Stop-ProcessTree([int] $ProcessId) {
    if ($ProcessId -gt 0) {
        & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
    }
}

# Check and kill existing Spring Boot / port 8080 process BEFORE starting
Stop-ExistingSpringBoot

Write-Host "[run.ps1] Starting Spring Boot (dev profile)..." -ForegroundColor Green

# PowerShell strips unquoted -Dspring-boot.run.profiles=dev
$mvnw = Join-Path $PSScriptRoot "mvnw.cmd"
$proc = Start-Process -FilePath $mvnw -ArgumentList @(
    "spring-boot:run",
    "-Dspring-boot.run.profiles=dev"
) -WorkingDirectory $PSScriptRoot -NoNewWindow -PassThru

try {
    Wait-Process -Id $proc.Id
} finally {
    if ($null -ne $proc -and -not $proc.HasExited) {
        Stop-ProcessTree -ProcessId $proc.Id
    }
    Stop-ExistingSpringBoot
}
