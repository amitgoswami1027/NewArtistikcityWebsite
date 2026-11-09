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
if (-not (Test-Path -LiteralPath $Jar)) { Write-Host "The built site ($Jar) is missing: run START-ArtistikCity.bat once." -ForegroundColor Red; exit 1 }
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
    Write-Host (($err -split "`r?`n" | Where-Object { $_ -and $_ -notmatch 'JAVA_TOOL_OPTIONS|Commons Logging' } | Select-Object -Last 1)) -ForegroundColor Yellow
}
exit 1
