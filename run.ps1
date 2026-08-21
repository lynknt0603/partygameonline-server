$env:JAVA_HOME = "C:\Program Files\Java\graalvm-jdk-21.0.5+9.1"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

function Stop-BackendOn8080 {
    Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        Where-Object { $_ -gt 0 } |
        ForEach-Object {
            & taskkill.exe /PID $_ /T /F 2>$null | Out-Null
        }
}

function Stop-ProcessTree([int] $ProcessId) {
    if ($ProcessId -gt 0) {
        & taskkill.exe /PID $ProcessId /T /F 2>$null | Out-Null
    }
}

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
    Stop-BackendOn8080
}
