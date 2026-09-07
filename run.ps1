$requiredJavaVersion = 21

function Get-JavaMajorVersion([string] $JavaHome) {
    $javaExe = Join-Path $JavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $javaExe -PathType Leaf)) {
        return $null
    }

    $versionOutput = (& $javaExe -version 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -and $versionOutput -match 'version "(?:1\.)?(\d+)') {
        return [int] $Matches[1]
    }

    return $null
}

function Find-JavaHome([int] $RequiredVersion) {
    $candidateHomes = [System.Collections.Generic.List[string]]::new()

    if ($env:JAVA_HOME) {
        $candidateHomes.Add($env:JAVA_HOME)
    }

    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($null -ne $javaCommand) {
        $candidateHomes.Add((Split-Path (Split-Path $javaCommand.Source -Parent) -Parent))
    }

    $searchRoots = @(
        (Join-Path $env:USERPROFILE ".jdks"),
        (Join-Path $env:ProgramFiles "Java"),
        (Join-Path $env:ProgramFiles "Eclipse Adoptium"),
        (Join-Path $env:ProgramFiles "Microsoft"),
        (Join-Path $env:ProgramFiles "Amazon Corretto")
    )

    foreach ($searchRoot in $searchRoots) {
        if (Test-Path -LiteralPath $searchRoot -PathType Container) {
            Get-ChildItem -LiteralPath $searchRoot -Directory -ErrorAction SilentlyContinue |
                ForEach-Object { $candidateHomes.Add($_.FullName) }
        }
    }

    $compatibleHomes = foreach ($candidateHome in ($candidateHomes | Select-Object -Unique)) {
        $majorVersion = Get-JavaMajorVersion -JavaHome $candidateHome
        if ($null -ne $majorVersion -and $majorVersion -ge $RequiredVersion) {
            [PSCustomObject]@{
                Home = $candidateHome
                MajorVersion = $majorVersion
            }
        }
    }

    return $compatibleHomes |
        Sort-Object @{ Expression = { if ($_.MajorVersion -eq $RequiredVersion) { 0 } else { 1 } } }, MajorVersion |
        Select-Object -First 1
}

$java = Find-JavaHome -RequiredVersion $requiredJavaVersion
if ($null -eq $java) {
    throw "JDK $requiredJavaVersion or higher was not found. Install it or set JAVA_HOME to its installation directory."
}

$env:JAVA_HOME = $java.Home
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"
Write-Host "[run.ps1] Using Java $($java.MajorVersion) from $env:JAVA_HOME." -ForegroundColor DarkGray

$pgRoot = Join-Path $env:USERPROFILE "tools\pgsql"
$pgCtl = Join-Path $pgRoot "bin\pg_ctl.exe"
$pgData = Join-Path $pgRoot "data"
$postgresPortOpen = $null -ne (Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue |
    Select-Object -First 1)

if ($postgresPortOpen) {
    Write-Host "[run.ps1] PostgreSQL is already listening on port 5432." -ForegroundColor DarkGray
} elseif (Test-Path $pgCtl) {
    & $pgCtl -D $pgData status 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[run.ps1] Starting PostgreSQL service..." -ForegroundColor Cyan
        & $pgCtl -D $pgData -l (Join-Path $pgRoot "postgres.log") start
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
