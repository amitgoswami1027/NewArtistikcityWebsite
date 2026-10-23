<#
.SYNOPSIS
    One-step setup and start for ArtistikCity (Spring Boot + React + MySQL) on Windows.

.DESCRIPTION
    1. Uses Java 17+ if installed, otherwise downloads a portable JDK 17 into .tools\ (no admin rights needed).
    2. Uses Maven if installed, otherwise downloads a portable Maven into .tools\.
    3. Builds the application (target\artistikcity-1.0.0.jar). The first build downloads the libraries (~150 MB).
    4. Prepares the MySQL database:
         -Db portable  (default) MySQL 8.4 LTS in .tools\ - downloaded once (~270 MB), no installer, no admin rights.
                       Data is kept in .tools\mysql-data between runs.
         -Db mysql     your own MySQL 8 server (-DbHost / -DbPort / -DbUser / -DbPassword).
         -Db docker    MySQL 8.4 in Docker Desktop (container "artistikcity-mysql").
       The database and user are created if missing; tables + demo data are loaded on the first run.
    5. Starts the site on http://localhost:<Port> and opens your browser. Press Ctrl+C to stop.

.EXAMPLE
    .\setup-and-run.ps1                                   # portable MySQL, nothing to install
.EXAMPLE
    .\setup-and-run.ps1 -DatabaseOnly                     # only start MySQL (e.g. to run the app from VS Code)
.EXAMPLE
    .\setup-and-run.ps1 -ResetDatabase                    # drop all tables and reload the demo data
.EXAMPLE
    .\setup-and-run.ps1 -Db mysql -DbUser root -DbPassword "YourPassword"
.EXAMPLE
    .\setup-and-run.ps1 -Db docker
#>
param(
    [ValidateSet('portable', 'mysql', 'docker')]
    [string]$Db = 'portable',
    [string]$DbHost = '127.0.0.1',
    [int]$DbPort = 3306,
    [string]$DbUser = 'root',
    [string]$DbPassword = '',
    [string]$Database = 'artistikcity',
    [string]$AppUser = 'artistik',
    [string]$AppPassword = 'Artistik@2026',
    [string]$RootPassword = 'ArtistikRoot@2026',
    [switch]$ResetDatabase,
    [switch]$DatabaseOnly,
    [switch]$StopDatabase,
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
# MySQL 8.4 LTS for Windows (ZIP archive). The pinned release is checked against its published MD5. Only the newest
# 8.4 release stays on cdn.mysql.com; older ones move to the archive, and newer 8.4 releases are tried as a fallback.
$MysqlVersion  = '8.4.13'
$MysqlMd5      = '7b94489d5524dea9b27097068bea25a8'
$MysqlHome     = Join-Path $Tools "mysql-$MysqlVersion-winx64"
$MysqlData     = Join-Path $Tools 'mysql-data'
$MysqlIni      = Join-Path $Tools 'mysql.ini'
$Jar           = Join-Path $Root 'target\artistikcity-1.0.0.jar'
$DockerName    = 'artistikcity-mysql'

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

$BrowserAgent = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36'
function TryDownload($urls, $target) {
    foreach ($u in @($urls)) {
        try {
            Info "Downloading $u"
            Invoke-WebRequest -Uri $u -OutFile $target -UseBasicParsing -UserAgent $BrowserAgent
            return $true
        } catch {
            Info "  failed: $($_.Exception.Message)"
            Remove-Item $target -ErrorAction SilentlyContinue
        }
    }
    return $false
}
function Download($urls, $target) {
    if (-not (TryDownload $urls $target)) { Fail "Could not download $target. Check your internet connection / proxy." }
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

$DbOnlyMode = $DatabaseOnly -or $StopDatabase
if (-not $DbOnlyMode) {
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

}   # end of: if (-not $DbOnlyMode)

# ------------------------------------------------------------------------------------------- Database
# MySQL helper: runs mysql.exe / mysqladmin.exe with a private option file, so passwords never appear
# on the command line (and MySQL does not print "using a password on the command line" warnings).
function Write-ClientCnf($user, $password, $hostName, $port) {
    $cnf = Join-Path $Tools "mysql-client-$user.cnf"
    $lines = @('[client]', "user=$user", "host=$hostName", "port=$port", 'protocol=TCP', 'default-character-set=utf8mb4')
    if ($password) { $lines += "password=`"$password`"" }
    [IO.File]::WriteAllLines($cnf, $lines)
    return $cnf
}
function Invoke-MysqlClient($exe, $cnf, $arguments, $inputFile) {
    if ($cnf -eq 'docker') {
        $cmdLine = "docker exec -i $DockerName $exe --protocol=TCP -h127.0.0.1 -uroot `"-p$RootPassword`" --default-character-set=utf8mb4 $arguments"
    } else {
        $cmdLine = "`"$exe`" --defaults-extra-file=`"$cnf`" $arguments"
    }
    if ($inputFile) { $cmdLine += " < `"$inputFile`"" }
    $out = cmd /c "$cmdLine 2>&1" | Out-String
    return @{ Code = $LASTEXITCODE; Output = $out.Trim() }
}
function Test-Ping($mysqladmin, $cnf) {
    $r = Invoke-MysqlClient $mysqladmin $cnf 'ping --connect-timeout=3' $null
    return ($r.Code -eq 0 -and $r.Output -match 'alive')
}
function Get-PortableMysqld {
    @(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" -ErrorAction SilentlyContinue |
      Where-Object { $_.ExecutablePath -and $_.ExecutablePath.StartsWith($MysqlHome, [StringComparison]::OrdinalIgnoreCase) })
}

$MysqlExe = $null; $MysqladminExe = $null; $StartedMysqld = $null
$env:PAYMENT_TEST_MODE = 'true'      # local setup: allow simulated payments when no Razorpay keys are configured
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:DB_INIT -ErrorAction SilentlyContinue

if ($Db -eq 'portable') {
    # reuse an 8.4 already unpacked in .tools (any release), newest first
    $have = Get-ChildItem -Path $Tools -Directory -Filter 'mysql-8.4.*-winx64' -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin\mysqld.exe') } |
            Sort-Object { [version]($_.Name -replace '^mysql-([0-9.]+)-winx64$', '$1') } -Descending | Select-Object -First 1
    if ($have) { $MysqlHome = $have.FullName; $MysqlVersion = $have.Name -replace '^mysql-([0-9.]+)-winx64$', '$1' }
    $MysqlExe = Join-Path $MysqlHome 'bin\mysql.exe'
    $MysqladminExe = Join-Path $MysqlHome 'bin\mysqladmin.exe'
    $Mysqld = Join-Path $MysqlHome 'bin\mysqld.exe'
    $DbHost = '127.0.0.1'

    if ($StopDatabase) {
        Step 'Stopping the portable MySQL'
        $running = Get-PortableMysqld
        if ($running.Count -eq 0) { Ok 'MySQL is not running.'; exit 0 }
        $rootCnf = Write-ClientCnf 'root' $RootPassword $DbHost $DbPort
        $r = Invoke-MysqlClient $MysqladminExe $rootCnf 'shutdown' $null
        Start-Sleep -Seconds 3
        if ((Get-PortableMysqld).Count -gt 0) { $running | ForEach-Object { Stop-Process -Id $_.ProcessId -Force } }
        Ok 'MySQL stopped.'
        exit 0
    }

    Step "Database: portable MySQL 8.4 LTS (.tools\)"
    if (-not (Test-Path $Mysqld)) {
        # mysqld.exe needs the Microsoft Visual C++ 2015-2022 runtime, which most PCs already have
        $vc = Join-Path $env:SystemRoot 'System32\vcruntime140_1.dll'
        if (-not (Test-Path $vc)) {
            Fail ('MySQL needs the Microsoft Visual C++ Redistributable (x64). Install it from ' +
                  'https://aka.ms/vs/17/release/vc_redist.x64.exe and run this again.')
        }
        Info "MySQL not found in .tools - downloading MySQL $MysqlVersion (about 270 MB, one time only)"
        $zip = Join-Path $Tools 'mysql.zip'
        $got = TryDownload @("https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$MysqlVersion-winx64.zip",
                             "https://downloads.mysql.com/archives/get/p/23/file/mysql-$MysqlVersion-winx64.zip") $zip
        if ($got) {
            $md5 = (Get-FileHash -Path $zip -Algorithm MD5).Hash.ToLower()
            if ($md5 -ne $MysqlMd5) { Remove-Item $zip; Fail "The MySQL download is damaged (MD5 $md5, expected $MysqlMd5). Run again." }
            Ok 'Download verified (MD5)'
        } else {
            # the pinned release was retired - take the current 8.4 release from the CDN
            foreach ($n in 14..30) {
                $v = "8.4.$n"
                if (TryDownload @("https://cdn.mysql.com/Downloads/MySQL-8.4/mysql-$v-winx64.zip") $zip) { $got = $true; $MysqlVersion = $v; break }
            }
            if (-not $got) { Fail 'Could not download MySQL 8.4. Check your internet connection / proxy, or install MySQL yourself and use -Db mysql.' }
        }
        $MysqlHome = Join-Path $Tools "mysql-$MysqlVersion-winx64"
        $MysqlExe = Join-Path $MysqlHome 'bin\mysql.exe'
        $MysqladminExe = Join-Path $MysqlHome 'bin\mysqladmin.exe'
        $Mysqld = Join-Path $MysqlHome 'bin\mysqld.exe'
        Info 'Unpacking MySQL (this takes a minute)'
        Unzip $zip $Tools
        Remove-Item $zip
        if (-not (Test-Path $Mysqld)) { Fail "Unpacking MySQL failed: $Mysqld not found." }
        Ok "MySQL $MysqlVersion unpacked to $MysqlHome"
    }

    # configuration: local connections only, UTF-8, data kept in .tools\mysql-data
    $fwd = { param($x) $x.Replace('\', '/') }
    $ini = @('[mysqld]',
             "basedir=`"$(& $fwd $MysqlHome)`"",
             "datadir=`"$(& $fwd $MysqlData)`"",
             "port=$DbPort",
             'bind-address=127.0.0.1',
             'mysqlx=OFF',
             'character-set-server=utf8mb4',
             'collation-server=utf8mb4_0900_ai_ci',
             'max_allowed_packet=64M',
             'innodb_buffer_pool_size=256M',
             "log-error=`"$(& $fwd (Join-Path $LogDir 'mysql-error.log'))`"",
             '', '[client]', "port=$DbPort", 'default-character-set=utf8mb4')
    [IO.File]::WriteAllLines($MysqlIni, $ini)

    $freshData = $false
    if (-not (Test-Path (Join-Path $MysqlData 'mysql'))) {
        Info 'Initialising a new MySQL data folder (.tools\mysql-data)'
        if (Test-Path $MysqlData) { Remove-Item -Recurse -Force $MysqlData }
        $out = cmd /c "`"$Mysqld`" --defaults-file=`"$MysqlIni`" --initialize-insecure --console 2>&1" | Out-String
        if ($LASTEXITCODE -ne 0) { Write-Host $out; Fail 'MySQL could not initialise its data folder - see the messages above.' }
        $freshData = $true
    }

    $rootCnf = Write-ClientCnf 'root' $RootPassword $DbHost $DbPort
    $noPwCnf = Write-ClientCnf 'root-initial' '' $DbHost $DbPort
    (Get-Content $noPwCnf) -replace '^user=root-initial$', 'user=root' | Set-Content $noPwCnf

    if ((Get-PortableMysqld).Count -gt 0) {
        Ok 'MySQL is already running'
    } else {
        $busy = Get-NetTCPConnection -LocalPort $DbPort -State Listen -ErrorAction SilentlyContinue
        if ($busy) {
            $owner = Get-Process -Id $busy[0].OwningProcess -ErrorAction SilentlyContinue
            Fail ("Port $DbPort is already used by '$($owner.ProcessName)' (another MySQL?). Stop it, or run with " +
                  "-DbPort 3307, or use that server with -Db mysql -DbUser root -DbPassword ...")
        }
        Info "Starting MySQL on 127.0.0.1:$DbPort"
        # own hidden window: MySQL keeps running cleanly even if this console is closed
        $StartedMysqld = Start-Process -FilePath $Mysqld -ArgumentList "--defaults-file=`"$MysqlIni`"" -WindowStyle Hidden -PassThru
    }
    $ready = $false
    for ($i = 1; $i -le 60; $i++) {
        if ((Test-Ping $MysqladminExe $rootCnf) -or (Test-Ping $MysqladminExe $noPwCnf)) { $ready = $true; break }
        if ($StartedMysqld -and $StartedMysqld.HasExited) { break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { Fail "MySQL did not start - see $LogDir\mysql-error.log" }
    Ok 'MySQL is running'

    # first start: give root a password; every start: make sure the database + app user exist
    $probe = Invoke-MysqlClient $MysqlExe $rootCnf '-N -e "SELECT 1"' $null
    if ($probe.Code -ne 0) {
        $set = Invoke-MysqlClient $MysqlExe $noPwCnf ("-e `"ALTER USER 'root'@'localhost' IDENTIFIED BY '$RootPassword'`"") $null
        if ($set.Code -ne 0) { Write-Host $set.Output; Fail 'Could not log in to MySQL as root (wrong -RootPassword?).' }
        Ok 'Set the MySQL root password'
    }
    Remove-Item $noPwCnf -ErrorAction SilentlyContinue
    $AdminCnf = $rootCnf
} elseif ($Db -eq 'docker') {
    Step 'Database: MySQL 8.4 in Docker'
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        Fail 'Docker is not installed. Install Docker Desktop (https://www.docker.com/products/docker-desktop/) or use the portable MySQL (default).'
    }
    $DbHost = '127.0.0.1'
    $ErrorActionPreference = 'Continue'   # docker writes progress to stderr
    $existing = (cmd /c "docker ps -a --filter name=^$DockerName$ --format {{.Names}} 2>nul" | Out-String).Trim()
    if ($existing -eq $DockerName) {
        Info "Starting existing container $DockerName"
        docker start $DockerName | Out-Null
    } else {
        Info "Creating container $DockerName (downloads the MySQL image the first time, ~600 MB)"
        docker run -d --name $DockerName -e "MYSQL_ROOT_PASSWORD=$RootPassword" -p "${DbPort}:3306" mysql:8.4 `
            --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --mysqlx=OFF | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail 'docker run failed. Is Docker Desktop running?' }
    }
    if ($StopDatabase) { docker stop $DockerName | Out-Null; Ok 'Container stopped.'; exit 0 }
    # the first start initialises MySQL inside the container (about 30 s) before it accepts TCP connections
    $MysqlExe = 'mysql'
    $ready = $false
    for ($i = 1; $i -le 60; $i++) {
        if ((Invoke-MysqlClient $MysqlExe 'docker' '-N -e "SELECT 1"' $null).Code -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    $ErrorActionPreference = 'Stop'
    if (-not $ready) { Fail "MySQL in container $DockerName did not become ready - check: docker logs $DockerName" }
    Ok 'MySQL is running in Docker'
    $AdminCnf = 'docker'
} else {
    Step "Database: your MySQL server at ${DbHost}:$DbPort"
    if ($StopDatabase) { Fail '-StopDatabase only applies to the portable MySQL or Docker.' }
    # use a mysql.exe from a local MySQL installation when there is one
    $found = @(Get-Command mysql.exe -ErrorAction SilentlyContinue | ForEach-Object { $_.Source })
    $found += @(Get-ChildItem "$env:ProgramFiles\MySQL\MySQL Server *\bin\mysql.exe" -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
    if ($found.Count -gt 0) {
        $MysqlExe = $found[0]
        if (-not $DbPassword) {
            $sec = Read-Host "Password for MySQL user '$DbUser'" -AsSecureString
            $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
        }
        $AdminCnf = Write-ClientCnf 'admin' $DbPassword $DbHost $DbPort
        (Get-Content $AdminCnf) -replace '^user=admin$', "user=$DbUser" | Set-Content $AdminCnf
        $ok = $false
        for ($i = 1; $i -le 3 -and -not $ok; $i++) {
            $ok = (Invoke-MysqlClient $MysqlExe $AdminCnf '-N -e "SELECT 1"' $null).Code -eq 0
            if (-not $ok) { Start-Sleep -Seconds 2 }
        }
        if (-not $ok) { Fail "Could not log in to MySQL at ${DbHost}:$DbPort as '$DbUser'. Check that the server runs and the password is right." }
    } else {
        Info 'mysql.exe not found - the application will connect with your login and create the tables itself.'
        $AdminCnf = $null
    }
}

$AppUrl = "jdbc:mysql://${DbHost}:$DbPort/$Database" + '?useUnicode=true&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&useSSL=false'
if ($AdminCnf) {
    # database + application user (idempotent)
    $setup = "CREATE DATABASE IF NOT EXISTS ``$Database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; " +
             "CREATE USER IF NOT EXISTS '$AppUser'@'%' IDENTIFIED BY '$AppPassword'; " +
             "ALTER USER '$AppUser'@'%' IDENTIFIED BY '$AppPassword'; " +
             "GRANT ALL PRIVILEGES ON ``$Database``.* TO '$AppUser'@'%';"
    $r = Invoke-MysqlClient $MysqlExe $AdminCnf ("-e `"$setup`"") $null
    if ($r.Code -ne 0) { Write-Host $r.Output; Fail 'Could not create the database / application user.' }
    $t = Invoke-MysqlClient $MysqlExe $AdminCnf ("-N -e `"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$Database' AND table_name='users'`"") $null
    $hasTables = ($t.Output -match '(^|\s)1(\s|$)')
    if (-not $hasTables -or $ResetDatabase) {
        Info 'Creating tables and loading demo data (database\schema.sql + database\seed.sql)'
        foreach ($f in @('database\schema.sql', 'database\seed.sql')) {
            $r = Invoke-MysqlClient $MysqlExe $AdminCnf $Database (Join-Path $Root $f)
            if ($r.Code -eq 0 -and $r.Output -match 'ERROR \d+') { $r.Code = 1 }
            if ($r.Code -ne 0) { Write-Host $r.Output; Fail "Loading $f failed - see the message above." }
        }
        Ok 'Database ready with demo data'
    } else {
        Ok "Database '$Database' already has tables - keeping your data (use -ResetDatabase to reload the demo data)"
    }

    # database\migrations\*.sql: schema changes for databases that already hold data. A fresh schema.sql
    # already contains all of them, so they are only recorded; existing databases run the pending ones in order.
    $mk = 'CREATE TABLE IF NOT EXISTS schema_migrations (filename VARCHAR(191) NOT NULL PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)'
    $r = Invoke-MysqlClient $MysqlExe $AdminCnf ("$Database -e `"$mk`"") $null
    if ($r.Code -ne 0) { Write-Host $r.Output; Fail 'Could not create the schema_migrations table.' }
    $done = (Invoke-MysqlClient $MysqlExe $AdminCnf ("$Database -N -e `"SELECT filename FROM schema_migrations`"") $null).Output -split "`r?`n" |
            ForEach-Object { $_.Trim() } | Where-Object { $_ -and $_ -notmatch '^mysql: \[Warning\]' }
    $files = Get-ChildItem -Path (Join-Path $Root 'database\migrations') -Filter '*.sql' -ErrorAction SilentlyContinue | Sort-Object Name
    $fresh = (-not $hasTables -or $ResetDatabase)
    foreach ($m in $files) {
        if ($done -contains $m.Name) { continue }
        if (-not $fresh) {
            Info "Applying database migration $($m.Name)"
            $r = Invoke-MysqlClient $MysqlExe $AdminCnf $Database $m.FullName
            if ($r.Code -eq 0 -and $r.Output -match 'ERROR \d+') { $r.Code = 1 }
            if ($r.Code -ne 0) { Write-Host $r.Output; Fail "Migration $($m.Name) failed - see the message above. Your data was not deleted." }
        }
        $r = Invoke-MysqlClient $MysqlExe $AdminCnf ("$Database -e `"INSERT IGNORE INTO schema_migrations (filename) VALUES ('$($m.Name)')`"") $null
    }
    $env:DB_USERNAME = $AppUser; $env:DB_PASSWORD = $AppPassword; $env:DB_INIT = 'never'
} else {
    # no mysql.exe on this PC (remote server): the application connects with your login and,
    # on an empty database, creates the tables itself
    $env:DB_USERNAME = $DbUser; $env:DB_PASSWORD = $DbPassword
    $AppUrl += '&createDatabaseIfNotExist=true'
    $env:DB_INIT = if ($ResetDatabase) { 'always' } else { 'never' }
    if (-not $ResetDatabase) { Info 'First run on an empty database? Run once with -ResetDatabase to create the tables and demo data.' }
}
$env:DB_URL = $AppUrl

if ($DatabaseOnly) {
    Write-Host ''
    Write-Host '  MySQL is ready' -ForegroundColor Green
    Write-Host "    Server   : ${DbHost}:$DbPort"
    Write-Host "    Database : $Database"
    if ($AdminCnf) { Write-Host "    App user : $AppUser / $AppPassword" }
    if ($Db -eq 'portable') {
        Write-Host "    Root     : root / $RootPassword"
        Write-Host '    It keeps running in the background. Stop it with STOP-MySQL.bat.'
    }
    try { Stop-Transcript | Out-Null } catch { }
    exit 0
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
$JavaArgs = @()
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
    Write-Host "    MySQL        : ${DbHost}:$DbPort  database $Database  user $($env:DB_USERNAME)"
    Write-Host ''
    if (-not $NoBrowser) { Start-Process $url }
} elseif ($proc.HasExited) {
    Fail 'The application stopped during start-up - see the log above (also logs\app.log).'
} else {
    Write-Host 'The site did not answer within 3 minutes - still waiting; see logs\app.log.' -ForegroundColor Yellow
}
try {
    Wait-Process -Id $proc.Id
} finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
    # the portable MySQL is stopped again when this script started it
    if ($StartedMysqld -and -not $StartedMysqld.HasExited) {
        Write-Host 'Stopping MySQL...'
        Invoke-MysqlClient $MysqladminExe $rootCnf 'shutdown' $null | Out-Null
    }
}
