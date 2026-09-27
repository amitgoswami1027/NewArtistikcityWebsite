<#
.SYNOPSIS
    One-step setup and start for ArtistikCity (Spring Boot + React + SQL Server) on Windows.

.DESCRIPTION
    1. Uses Java 17+ if installed, otherwise downloads a portable JDK 17 into .tools\ (no admin rights needed).
    2. Uses Maven if installed, otherwise downloads a portable Maven into .tools\.
    3. Builds the application (target\artistikcity-1.0.0.jar). The first build downloads the libraries (~150 MB).
    4. Prepares the database:
         -Db dev        in-memory database in SQL Server mode, created fresh on every start (nothing to install)
         -Db docker     starts Microsoft SQL Server 2022 in Docker Desktop (container "artistikcity-sql")
         -Db sqlserver  uses your own SQL Server / SQL Server Express
       For docker/sqlserver the database is created if missing and the tables + demo data are loaded on first run.
    5. Starts the site on http://localhost:<Port> and opens your browser. Press Ctrl+C to stop.

.EXAMPLE
    .\setup-and-run.ps1                                   # demo mode, no database install needed
.EXAMPLE
    .\setup-and-run.ps1 -Db docker                        # SQL Server 2022 in Docker
.EXAMPLE
    .\setup-and-run.ps1 -Db sqlserver -SqlUser sa -SqlPassword "YourPassword"
.EXAMPLE
    .\setup-and-run.ps1 -Db sqlserver -SqlInstance SQLEXPRESS -Trusted     # Windows authentication
#>
param(
    [ValidateSet('dev', 'docker', 'sqlserver')]
    [string]$Db = 'dev',
    [string]$SqlHost = 'localhost',
    [int]$SqlPort = 1433,
    [string]$SqlInstance = '',
    [string]$SqlUser = 'sa',
    [string]$SqlPassword = '',
    [switch]$Trusted,
    [string]$Database = 'artistikcity',
    [switch]$ResetDatabase,
    [int]$Port = 8080,
    [switch]$SkipBuild,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # makes Invoke-WebRequest much faster on Windows PowerShell 5
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root
$Tools = Join-Path $Root '.tools'
New-Item -ItemType Directory -Force -Path $Tools | Out-Null
$LogDir = Join-Path $Root 'logs'
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
try { Start-Transcript -Path (Join-Path $LogDir 'setup.log') -Force | Out-Null } catch { }
Write-Host "ArtistikCity setup - PowerShell $($PSVersionTable.PSVersion) - folder $Root"

# any unexpected error: show it clearly (it is also written to logs\setup.log)
trap {
    Write-Host ''
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host $_.InvocationInfo.PositionMessage
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}

$JdkUrl        = 'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.12%2B7/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip'
$MavenVersion  = '3.9.9'
$MavenUrls     = @("https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MavenVersion/apache-maven-$MavenVersion-bin.zip",
                   "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip")
$MssqlVersion  = '12.8.1'
$Jar           = Join-Path $Root 'target\artistikcity-1.0.0.jar'
$DockerName    = 'artistikcity-sql'
$DockerDefaultPassword = 'Artistik#2026Pass'

function Step($msg) { Write-Host ''; Write-Host "==> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    $msg" -ForegroundColor Green }
function Info($msg) { Write-Host "    $msg" }
function Fail($msg) {
    Write-Host ''; Write-Host "ERROR: $msg" -ForegroundColor Red
    try { Stop-Transcript | Out-Null } catch { }
    exit 1
}

# unzip with the built-in tar.exe (fast) and fall back to Expand-Archive
function Unzip($zip, $dest) {
    $tar = if ($env:SystemRoot) { Join-Path $env:SystemRoot 'System32\tar.exe' } else { '' }
    if ($tar -and (Test-Path $tar)) {
        & $tar -xf $zip -C $dest
        if ($LASTEXITCODE -eq 0) { return }
    }
    Expand-Archive -Path $zip -DestinationPath $dest -Force
}

function Download($urls, $target) {
    foreach ($u in @($urls)) {
        try {
            Info "Downloading $u"
            Invoke-WebRequest -Uri $u -OutFile $target -UseBasicParsing
            return
        } catch {
            Info "  failed: $($_.Exception.Message)"
        }
    }
    Fail "Could not download $target. Check your internet connection / proxy."
}

function Get-JavaMajor($javaExe) {
    try {
        $out = cmd /c "call `"$javaExe`" -version 2>&1" | Out-String
        if ($out -match 'version "(\d+)(\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1) { $major = [int]$Matches[3] }
            return $major
        }
    } catch { }
    return 0
}

# real installation folder of a java.exe (resolves Oracle's "javapath" shortcuts)
function Get-JavaHome($javaExe) {
    try {
        $out = cmd /c "call `"$javaExe`" -XshowSettings:properties -version 2>&1" | Out-String
        if ($out -match 'java\.home = (.+)') { return $Matches[1].Trim() }
    } catch { }
    return $null
}

# ----------------------------------------------------------------------------------------------- Java
Step 'Checking Java (JDK 17 or newer)'
$JdkHome = $null
$candidates = @()
$portableJdk = Get-ChildItem -Path $Tools -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($portableJdk) { $candidates += (Join-Path $portableJdk.FullName 'bin\java.exe') }
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$cmd = Get-Command java.exe -ErrorAction SilentlyContinue
if ($cmd) { $candidates += $cmd.Source }
foreach ($base in @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
    if (Test-Path $base) {
        Get-ChildItem -Path $base -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
            ForEach-Object { $candidates += (Join-Path $_.FullName 'bin\java.exe') }
    }
}
foreach ($c in ($candidates | Select-Object -Unique)) {
    if (-not (Test-Path $c)) { continue }
    $major = Get-JavaMajor $c
    if ($major -lt 17) { continue }
    $jh = Get-JavaHome $c
    if ($jh -and (Test-Path (Join-Path $jh 'bin\javac.exe'))) { $JdkHome = $jh; break }
    Info "Skipping Java $major at $c - it is a runtime only (no javac), a JDK is needed to build"
}
if (-not $JdkHome) {
    Info 'JDK 17+ not found - downloading a portable JDK 17 (about 190 MB, one time only)'
    $zip = Join-Path $Tools 'jdk17.zip'
    Download $JdkUrl $zip
    Info 'Unpacking the JDK'
    Unzip $zip $Tools
    Remove-Item $zip
    $portableJdk = Get-ChildItem -Path $Tools -Directory -Filter 'jdk-17*' | Select-Object -First 1
    $JdkHome = $portableJdk.FullName
}
$JavaExe = Join-Path $JdkHome 'bin\java.exe'
$env:JAVA_HOME = $JdkHome
$env:Path = (Join-Path $JdkHome 'bin') + ';' + $env:Path
Ok "Using Java $(Get-JavaMajor $JavaExe) at $env:JAVA_HOME"

# ---------------------------------------------------------------------------------------------- Maven
Step 'Checking Maven'
$Mvn = $null
$mvnCmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
$portableMvn = Join-Path $Tools "apache-maven-$MavenVersion\bin\mvn.cmd"
if (Test-Path $portableMvn) { $Mvn = $portableMvn }
elseif ($mvnCmd) { $Mvn = $mvnCmd.Source }
else {
    Info 'Maven not found - downloading a portable Maven (about 10 MB, one time only)'
    $zip = Join-Path $Tools 'maven.zip'
    Download $MavenUrls $zip
    Unzip $zip $Tools
    Remove-Item $zip
    $Mvn = $portableMvn
}
Ok "Using Maven at $Mvn"

# ------------------------------------------------------------------- stop a previous run of the app
# a still-running ArtistikCity keeps its jar locked (the rebuild then fails) and keeps the port busy
$old = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
         Where-Object { $_.CommandLine -and $_.CommandLine -match 'artistikcity(-1\.0\.0|-run)\.jar' })
if ($old.Count -gt 0) {
    Step 'Stopping the ArtistikCity that is still running from an earlier start'
    foreach ($o in $old) {
        Info "Stopping process $($o.ProcessId)"
        Stop-Process -Id $o.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 2
}

# ---------------------------------------------------------------------------------------------- Build
if ($SkipBuild -and (Test-Path $Jar)) {
    Step 'Skipping build (-SkipBuild)'
} else {
    Step 'Building the application (first time downloads libraries, this can take a few minutes)'
    # run through cmd so Maven's output is shown and recorded in logs\setup.log
    cmd /c "call `"$Mvn`" -B --no-transfer-progress -DskipTests package 2>&1" | Out-Host
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $Jar)) { Fail 'The Maven build failed - see the messages above.' }
    Ok "Built $Jar"
}

# ------------------------------------------------------------------------------------------- Database
$JavaArgs = @()
$env:PAYMENT_TEST_MODE = 'true'      # local setup: allow simulated payments when no Razorpay keys are configured
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue

if ($Db -eq 'dev') {
    Step 'Database: in-memory (SQL Server compatibility mode) - demo data is loaded on every start'
    $env:SPRING_PROFILES_ACTIVE = 'dev'
} else {
    if ($Db -eq 'docker') {
        Step 'Database: SQL Server 2022 in Docker'
        if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
            Fail 'Docker is not installed. Install Docker Desktop (https://www.docker.com/products/docker-desktop/) or use -Db dev / -Db sqlserver.'
        }
        if (-not $SqlPassword) { $SqlPassword = $DockerDefaultPassword }
        $SqlUser = 'sa'; $SqlHost = 'localhost'; $SqlInstance = ''; $Trusted = $false
        $ErrorActionPreference = 'Continue'   # docker writes progress to stderr
        $existing = cmd /c "docker ps -a --filter name=^$DockerName$ --format {{.Names}} 2>nul" | Out-String
        $existing = $existing.Trim()
        if ($existing -eq $DockerName) {
            Info "Starting existing container $DockerName"
            docker start $DockerName | Out-Null
        } else {
            Info "Creating container $DockerName (downloads the SQL Server image the first time, ~1.5 GB)"
            docker run -d --name $DockerName -e 'ACCEPT_EULA=Y' -e "MSSQL_SA_PASSWORD=$SqlPassword" -p "${SqlPort}:1433" mcr.microsoft.com/mssql/server:2022-latest | Out-Null
            if ($LASTEXITCODE -ne 0) { Fail 'docker run failed. Is Docker Desktop running?' }
        }
        $ErrorActionPreference = 'Stop'
    } else {
        Step 'Database: your SQL Server'
    }

    # JDBC URLs
    $server = if ($SqlInstance) { "$SqlHost\$SqlInstance" } else { "${SqlHost}:$SqlPort" }
    $common = 'encrypt=true;trustServerCertificate=true;loginTimeout=15'
    if ($Trusted) {
        $common += ';integratedSecurity=true'
        $authDir = Join-Path $Tools 'sqlauth'
        $dll = Join-Path $authDir "mssql-jdbc_auth-$MssqlVersion.x64.dll"
        if (-not (Test-Path $dll)) {
            Info 'Downloading the SQL Server Windows-authentication library (mssql-jdbc_auth)'
            New-Item -ItemType Directory -Force -Path $authDir | Out-Null
            & $Mvn -B -q dependency:copy "-Dartifact=com.microsoft.sqlserver:mssql-jdbc_auth:$MssqlVersion.x64:dll" "-DoutputDirectory=$authDir"
            if ($LASTEXITCODE -ne 0 -or -not (Test-Path $dll)) { Fail 'Could not download mssql-jdbc_auth. Use SQL authentication (-SqlUser/-SqlPassword) instead.' }
        }
        $JavaArgs += "-Djava.library.path=$authDir"
    } elseif (-not $SqlPassword) {
        $sec = Read-Host "Password for SQL Server login '$SqlUser'" -AsSecureString
        $SqlPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    }
    $masterUrl = "jdbc:sqlserver://$server;databaseName=master;$common"
    $appUrl    = "jdbc:sqlserver://$server;databaseName=$Database;$common"

    # create the database if needed (uses the SQL Server JDBC driver downloaded by the build)
    $driverJar = Join-Path $env:USERPROFILE ".m2\repository\com\microsoft\sqlserver\mssql-jdbc\$MssqlVersion.jre11\mssql-jdbc-$MssqlVersion.jre11.jar"
    if (-not (Test-Path $driverJar)) { Fail "SQL Server JDBC driver not found at $driverJar (run without -SkipBuild once)." }
    $u = if ($Trusted) { '-' } else { $SqlUser }
    $p = if ($Trusted) { '-' } else { $SqlPassword }
    $state = $null
    $attempts = if ($Db -eq 'docker') { 40 } else { 1 }
    for ($i = 1; $i -le $attempts; $i++) {
        $out = cmd /c "call `"$JavaExe`" $($JavaArgs -join ' ') -cp `"$driverJar`" tools\CreateDatabase.java `"$masterUrl`" `"$u`" `"$p`" $Database 2>&1" | Out-String
        if ($out -match '(CREATED|EMPTY|READY)') { $state = $Matches[1]; break }
        if ($i -lt $attempts) { Info "Waiting for SQL Server to accept connections ($i/$attempts)..."; Start-Sleep -Seconds 3 }
    }
    if (-not $state) {
        Write-Host $out
        Fail ("Could not connect to SQL Server at $server. Check that the service is running, TCP/IP is enabled " +
              "(SQL Server Configuration Manager > Protocols > TCP/IP) and the login/password are correct.")
    }
    Ok "Database '$Database' state: $state"

    $env:DB_URL = $appUrl
    $env:DB_USERNAME = if ($Trusted) { '' } else { $SqlUser }
    $env:DB_PASSWORD = if ($Trusted) { '' } else { $SqlPassword }
    if ($state -ne 'READY' -or $ResetDatabase) {
        Info 'Creating tables and loading demo data (database\schema.sql + database\seed.sql)'
        $env:DB_INIT = 'always'
    } else {
        Info 'Tables already exist - keeping your data (use -ResetDatabase to recreate them)'
        $env:DB_INIT = 'never'
    }
}

# -------------------------------------------------------------------------------------------- Run it
$busy = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($busy) { Fail "Port $Port is already in use. Stop the other program or run with -Port 8081." }

Step "Starting ArtistikCity on http://localhost:$Port  (press Ctrl+C to stop)"
# run a copy of the jar, so a running app never blocks the next rebuild
$RunDir = Join-Path $Tools 'run'
New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
$RunJar = Join-Path $RunDir 'artistikcity-run.jar'
Copy-Item -Path $Jar -Destination $RunJar -Force
$argList = @($JavaArgs + @('-jar', "`"$RunJar`"", "--server.port=$Port", '--logging.file.name=logs/app.log'))
Info "Application log: $LogDir\app.log"
$proc = Start-Process -FilePath $JavaExe -ArgumentList $argList -WorkingDirectory $Root -NoNewWindow -PassThru

$url = "http://localhost:$Port/"
$up = $false
for ($i = 0; $i -lt 90 -and -not $proc.HasExited; $i++) {
    Start-Sleep -Seconds 2
    try { Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 | Out-Null; $up = $true; break } catch { }
}
if ($up) {
    Write-Host ''
    Write-Host '  ArtistikCity is running' -ForegroundColor Green
    Write-Host "    Website      : $url"
    Write-Host "    Student login: ${url}login        student@artistikcity.com / password"
    Write-Host "    Admin panel  : ${url}admin/login  admin@artistikcity.com / password"
    Write-Host ''
    if (-not $NoBrowser) { Start-Process $url }
} elseif ($proc.HasExited) {
    Fail 'The application stopped during start-up - see the log above (also logs\app.log).'
} else {
    Write-Host 'The site did not answer within 3 minutes - still waiting; see logs\app.log.' -ForegroundColor Yellow
}
try { Wait-Process -Id $proc.Id } finally { if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force } }
