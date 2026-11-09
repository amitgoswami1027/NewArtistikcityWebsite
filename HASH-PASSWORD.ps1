# ============================================================
#  ArtistikCity - make the bcrypt hash of a password (for the go-live reset through the pipeline)
#
#  The password is typed here (twice, hidden) and checked against the site's rules; only the hash is shown and copied
#  to the clipboard. Put it in GitHub > repository > Settings > Secrets and variables > Actions > New repository secret:
#      GO_LIVE_ADMIN_HASH    (super admin, 12+ characters)       HASH-PASSWORD.bat -Staff -Email admin@artistikcity.com
#      GO_LIVE_STUDENT_HASH  (student@artistikcity.com, 8+)      HASH-PASSWORD.bat -Email student@artistikcity.com
#  The password itself never leaves this PC. Needs START-ArtistikCity.bat run once (Java and the built jar).
# ============================================================
param([string]$Email = '', [switch]$Staff, [string]$JavaExe = '', [string]$Jar = '')
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $Email) { $Email = Read-Host 'E-mail address of the account' }
if (-not $JavaExe) {
    $jdk = Get-ChildItem -Path (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $JavaExe = Join-Path $jdk.FullName 'bin\java.exe' } elseif (Get-Command java -ErrorAction SilentlyContinue) { $JavaExe = (Get-Command java).Source }
}
if (-not $JavaExe -or -not (Test-Path -LiteralPath $JavaExe)) { Write-Host 'Java not found: run START-ArtistikCity.bat once.' -ForegroundColor Red; exit 1 }
if (-not $Jar) { $Jar = Join-Path $PSScriptRoot 'target\artistikcity-1.0.0.jar' }

# The jar must contain the hashing tool (added in Nov 2026): rebuild it with the project's Maven when it is missing or older.
function Test-JarHasTool([string]$path) {
    if (-not (Test-Path -LiteralPath $path)) { return $false }
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $z = [IO.Compression.ZipFile]::OpenRead($path)
        try { return [bool]($z.Entries | Where-Object { $_.FullName -eq 'BOOT-INF/classes/com/artistikcity/tools/HashPassword.class' }) } finally { $z.Dispose() }
    } catch { return $false }
}
if (-not (Test-JarHasTool $Jar)) {
    Write-Host 'The built site is older than the hashing tool: rebuilding it now (a few minutes)...' -ForegroundColor Yellow
    $mvn = Get-ChildItem -Path (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'apache-maven-*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($mvn) { $mvnCmd = Join-Path $mvn.FullName 'bin\mvn.cmd' } elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { $mvnCmd = (Get-Command mvn.cmd).Source } else { $mvnCmd = $null }
    if (-not $mvnCmd) { Write-Host 'Maven not found: run START-ArtistikCity.bat once (it builds the site), then run this again.' -ForegroundColor Red; exit 1 }
    $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $JavaExe)
    cmd /c "call `"$mvnCmd`" -B -q --no-transfer-progress -DskipTests package 2>&1" | Out-Host
    if (-not (Test-JarHasTool $Jar)) {
        Write-Host 'The build did not produce the tool. If the site is running (START-ArtistikCity window), close it and try again.' -ForegroundColor Red
        exit 1
    }
}
if ($Staff) { Write-Host "Password for $Email (super admin: at least 12 characters)" } else { Write-Host "Password for $Email (at least 8 characters)" }
for ($try = 0; $try -lt 5; $try++) {
    $a = Read-Host 'Password' -AsSecureString
    $b = Read-Host 'Password (again)' -AsSecureString
    $pa = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($a))
    $pb = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($b))
    if ($pa -cne $pb -or -not $pa) { Write-Host 'The two entries differ, try again.' -ForegroundColor Yellow; continue }
    $javaArgs = @('-cp', $Jar, '-Dloader.main=com.artistikcity.tools.HashPassword', 'org.springframework.boot.loader.launch.PropertiesLauncher')
    if ($Staff) { $javaArgs += '--staff' }
    $javaArgs += $Email
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $JavaExe
    $psi.Arguments = ($javaArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '
    $psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true; $psi.UseShellExecute = $false
    $p = [Diagnostics.Process]::Start($psi)
    $p.StandardInput.WriteLine($pa); $p.StandardInput.Close()
    $out = $p.StandardOutput.ReadToEnd(); $err = $p.StandardError.ReadToEnd(); $p.WaitForExit()
    $hash = ($out -split "`r?`n" | Where-Object { $_ -match '^\$2[aby]\$\d\d\$.{53}$' } | Select-Object -First 1)
    if ($p.ExitCode -eq 0 -and $hash) {
        Write-Host ''
        Write-Host $hash -ForegroundColor Green
        try { Set-Clipboard -Value $hash; Write-Host '(copied to the clipboard)' } catch { }
        Write-Host ("Save it as the GitHub secret " + $(if ($Staff) { 'GO_LIVE_ADMIN_HASH' } else { 'GO_LIVE_STUDENT_HASH' }) + '.')
        exit 0
    }
    # the tool prints the reason on one line (exit 2); anything else: show the exception line, not the stack frames
    $lines = @($err -split "`r?`n" | Where-Object { $_ -and $_ -notmatch 'JAVA_TOOL_OPTIONS|Commons Logging|^\s+at |^\s+\.\.\. ' })
    if ($lines.Count -eq 0) { $lines = @("Java stopped with exit code $($p.ExitCode).") }
    Write-Host ($lines -join [Environment]::NewLine) -ForegroundColor Yellow
    if ($p.ExitCode -ne 2) { exit 1 }
}
exit 1
