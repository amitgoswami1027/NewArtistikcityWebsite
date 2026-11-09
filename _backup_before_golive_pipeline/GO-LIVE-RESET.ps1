# ============================================================
#  ArtistikCity - GO-LIVE RESET of the LIVE (AWS) website
#
#  Turns the live site into a clean starting point for general availability:
#    * the database is emptied and rebuilt: ONE super admin (every right), ONE student
#      (student@artistikcity.com, marked test buyer) and six fully filled sample courses
#      (database\golive\golive-seed.sql + database\golive\files). Configuration Panel values (payment / SMS / Google
#      keys) and site settings (texts, SEO, contact, social, menu) are KEPT; testing switches are removed.
#    * uploaded files in S3 (storage/uploads, private/) are MOVED to archive/pre-go-live-<time>/ (not deleted)
#      and the sample course files are uploaded.
#    * RDS: a final snapshot "pre-go-live-<time>" is taken FIRST (the only way back), then every other manual
#      snapshot and every retained automated backup in the region is deleted, and the database's automated
#      backups are cleared (backup retention 0 and back: two short database restarts).
#    * the app is restarted and /healthz checked.
#
#  You type the two passwords here; only their bcrypt hashes are sent (never shown, never on a command line).
#  Run -DryRun first: it shows everything that would happen and changes nothing.
#
#    GO-LIVE-RESET.bat -DryRun
#    GO-LIVE-RESET.bat                                 (asks you to type GO LIVE, then DELETE for the snapshots)
#    GO-LIVE-RESET.bat -AdminEmail you@example.com -AdminName "ArtistikCity"
#    GO-LIVE-RESET.bat -Local                          (rehearsal on this PC's database in .tools; no AWS at all)
#
#  Options: -KeepSiteLock (keep the "Site under construction" password), -KeepOldSnapshots,
#           -KeepAutomatedBackups, -SkipS3. Needs: AWS CLI signed in (aws configure), the Session Manager plugin,
#           and START-ArtistikCity.bat run once (MySQL client, Java and the built jar in this folder).
# ============================================================
param(
    [string]$Region = 'ap-south-1',
    [string]$Stack = 'artistikcity',
    [string]$AdminEmail = 'admin@artistikcity.com',
    [string]$AdminName = 'ArtistikCity',
    [string]$StudentEmail = 'student@artistikcity.com',
    [int]$LocalPort = 13306,
    [string]$MysqlExe = '',
    [string]$JavaExe = '',
    [string]$Jar = '',
    [string]$SslMode = 'REQUIRED',
    [switch]$DryRun,
    [switch]$Local,
    [switch]$KeepSiteLock,
    [switch]$KeepOldSnapshots,
    [switch]$KeepAutomatedBackups,
    [switch]$SkipS3,
    [string]$SiteUrl = '',                 # health check address (default https://<the stack's domain>)
    # for automated runs/tests only: passwords from these environment variables instead of the prompt
    [string]$AdminPasswordEnv = '',
    [string]$StudentPasswordEnv = '',
    [switch]$Yes
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$Stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Warn($m) { Write-Host $m -ForegroundColor Yellow }
function Fail($m) { Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }
function Invoke-Aws { $out = & aws @args; if ($LASTEXITCODE -ne 0) { Fail ("aws " + ($args[0..1] -join ' ') + " failed (see the message above).") }; return ($out | Out-String).Trim() }
function Confirm-Word($word, $prompt) {
    if ($Yes) { return }
    $a = Read-Host $prompt
    if ($a -cne $word) { Write-Host 'Nothing was changed.'; exit 0 }
}

# ---------------------------------------------------------------- tools
$onWindows = $env:OS -eq 'Windows_NT'
if (-not $MysqlExe) {
    $found = Get-ChildItem -Path (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'mysql-8.4*' -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($found) { $MysqlExe = Join-Path $found.FullName 'bin\mysql.exe' } elseif (Get-Command mysql -ErrorAction SilentlyContinue) { $MysqlExe = (Get-Command mysql).Source }
}
if (-not $MysqlExe -or -not (Test-Path -LiteralPath $MysqlExe)) { Fail 'MySQL client not found (run START-ArtistikCity.bat once to download it).' }
if (-not $JavaExe) {
    $jdk = Get-ChildItem -Path (Join-Path $PSScriptRoot '.tools') -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($jdk) { $JavaExe = Join-Path $jdk.FullName 'bin\java.exe' } elseif (Get-Command java -ErrorAction SilentlyContinue) { $JavaExe = (Get-Command java).Source }
}
if (-not $JavaExe -or -not (Test-Path -LiteralPath $JavaExe)) { Fail 'Java not found (run START-ArtistikCity.bat once).' }
if (-not $Jar) { $Jar = Join-Path $PSScriptRoot 'target\artistikcity-1.0.0.jar' }
if (-not (Test-Path -LiteralPath $Jar)) { Fail "The built site ($Jar) is missing: run START-ArtistikCity.bat once (it builds it)." }
foreach ($f in 'database\schema.sql', 'database\golive\golive-seed.sql', 'database\golive\go-live-before.sql', 'database\golive\go-live-after.sql') {
    if (-not (Test-Path -LiteralPath $f)) { Fail "$f is missing." }
}
if (-not $Local -and -not (Get-Command aws -ErrorAction SilentlyContinue)) { Fail 'The AWS CLI is not installed (https://aws.amazon.com/cli/).' }
if ($AdminEmail -notmatch '^[^@\s''"\\]+@[^@\s''"\\]+\.[^@\s''"\\]+$') { Fail "Not an e-mail address: $AdminEmail" }
if ($StudentEmail -notmatch '^[^@\s''"\\]+@[^@\s''"\\]+\.[^@\s''"\\]+$') { Fail "Not an e-mail address: $StudentEmail" }
if ($AdminName -match '[''"\\<>]' -or $AdminName.Length -lt 2 -or $AdminName.Length -gt 100) { Fail 'Choose a plain name of 2-100 characters for -AdminName.' }

# ---------------------------------------------------------------- what is where
if ($Local) {
    # MySQL's root (as START-ArtistikCity.bat): schema.sql drops a view root created, which the app user may not drop
    $dbHost = '127.0.0.1'; $dbPort = 3306; $dbUser = 'root'; $dbPass = 'ArtistikRoot@2026'; $dbName = 'artistikcity'
    if ($env:ARTISTIK_DB_PORT) { $dbPort = [int]$env:ARTISTIK_DB_PORT }
    if ($null -ne $env:ARTISTIK_DB_ROOT_PASSWORD) { $dbPass = $env:ARTISTIK_DB_ROOT_PASSWORD }
    $localSsl = 'PREFERRED'
    Info "Rehearsal on this PC: database $dbName at ${dbHost}:$dbPort, files into storage\ (no AWS)"
} else {
    Info 'Reading the AWS stack'
    $stackJson = Invoke-Aws cloudformation describe-stacks --region $Region --stack-name $Stack --output json | ConvertFrom-Json
    $outputs = $stackJson.Stacks[0].Outputs
    function Out($k) { $v = ($outputs | Where-Object { $_.OutputKey -eq $k }).OutputValue; if (-not $v) { Fail "Stack output $k not found." }; return $v }
    $instance = Out 'InstanceId'; $endpoint = Out 'DatabaseEndpoint'; $secretArn = Out 'DatabaseSecretArn'; $bucket = Out 'UploadsBucketName'
    $distribution = ($outputs | Where-Object { $_.OutputKey -eq 'DistributionId' }).OutputValue
    $domain = ($stackJson.Stacks[0].Parameters | Where-Object { $_.ParameterKey -eq 'DomainName' }).ParameterValue
    $site = if ($SiteUrl) { $SiteUrl.TrimEnd('/') } else { "https://$domain" }
    $db = Invoke-Aws rds describe-db-instances --region $Region --output json | ConvertFrom-Json
    $dbi = $db.DBInstances | Where-Object { $_.Endpoint.Address -eq $endpoint } | Select-Object -First 1
    if (-not $dbi) { Fail "No RDS database with endpoint $endpoint." }
    $dbId = $dbi.DBInstanceIdentifier; $retention = [int]$dbi.BackupRetentionPeriod
    $snaps = (Invoke-Aws rds describe-db-snapshots --region $Region --snapshot-type manual --output json | ConvertFrom-Json).DBSnapshots
    $retained = (Invoke-Aws rds describe-db-instance-automated-backups --region $Region --output json | ConvertFrom-Json).DBInstanceAutomatedBackups |
        Where-Object { $_.Status -eq 'retained' }
    $auto = (Invoke-Aws rds describe-db-snapshots --region $Region --db-instance-identifier $dbId --snapshot-type automated --output json | ConvertFrom-Json).DBSnapshots
    $objects = 0
    if (-not $SkipS3) {
        foreach ($p in 'storage/uploads/', 'private/') {
            $n = Invoke-Aws s3api list-objects-v2 --region $Region --bucket $bucket --prefix $p --query 'KeyCount' --output text
            if ($n -match '^\d+$') { $objects += [int]$n }
        }
    }
    Write-Host ''
    Write-Host "  Website           $site" -ForegroundColor White
    Write-Host "  Database          $dbId ($endpoint), automated backups kept $retention days" -ForegroundColor White
    Write-Host "  Uploads bucket    $bucket (about $objects files under storage/uploads and private/)" -ForegroundColor White
    Write-Host "  Manual snapshots  $(@($snaps).Count) in $Region" -ForegroundColor White
    foreach ($s in $snaps) { Write-Host ("      {0,-48} {1,-28} {2}" -f $s.DBSnapshotIdentifier, $s.DBInstanceIdentifier, $s.SnapshotCreateTime) }
    Write-Host "  Automated backups $(@($auto).Count) of $dbId; retained backups of deleted databases: $(@($retained).Count)" -ForegroundColor White

    # go-live readiness of the server settings (Parameter Store under SettingsPath): names only, no secret values
    $ssmPath = ($outputs | Where-Object { $_.OutputKey -eq 'SettingsPath' }).OutputValue
    if ($ssmPath) {
        $names = @((Invoke-Aws ssm get-parameters-by-path --region $Region --path $ssmPath --recursive --query 'Parameters[].Name' --output json | ConvertFrom-Json) |
            ForEach-Object { ($_ -split '/')[-1] })
        Write-Host "  Server settings   $($names.Count) in $ssmPath : $($names -join ', ')" -ForegroundColor White
        foreach ($flag in 'PAYMENT_TEST_MODE', 'AUTH_EXPOSE_DEV_OTP') {
            if ($names -contains $flag) {
                $v = Invoke-Aws ssm get-parameter --region $Region --name "$ssmPath/$flag" --query 'Parameter.Value' --output text
                if ($v -match '^(?i)true|1|yes$') { Warn "  ! $flag is on in $ssmPath : switch it off (delete the parameter) before going live, then redeploy" }
            }
        }
        if ($names -contains 'SITE_PREVIEW_PASSWORD') { Warn "  ! SITE_PREVIEW_PASSWORD is set in ${ssmPath}: the site stays behind the password until you delete it and redeploy" }
        if (-not ($names -contains 'MAIL_HOST')) { Warn '  ! MAIL_HOST is not set: receipts, sign-in codes and password resets cannot be e-mailed' }
        if (-not ($names -contains 'OTP_SECRET')) { Warn '  ! OTP_SECRET is not set: sign-in codes and two-step verification need it' }
    }
}

Write-Host ''
Warn 'This will:'
if (-not $Local) { Warn "  1. take a final snapshot pre-go-live-$Stamp (kept: the only way back)" }
Warn '  2. EMPTY the database and rebuild it: 1 super admin, 1 student, 6 sample courses (configuration and site settings are kept)'
if (-not $Local -and -not $SkipS3) { Warn "  3. move every uploaded file to s3://$bucket/archive/pre-go-live-$Stamp/ and upload the sample course files" }
Warn '  4. restart the website (right after the database and files)'
if (-not $Local -and -not $KeepOldSnapshots) { Warn "  5. DELETE all $(@($snaps).Count) older manual snapshots and $(@($retained).Count) retained backups in $Region" }
if (-not $Local -and -not $KeepAutomatedBackups -and $retention -gt 0) { Warn "  6. clear the automated backups of $dbId (retention 0, then back to $retention days: two short restarts)" }
if (-not $KeepSiteLock) { Warn '  The "Site under construction" password set in the admin is removed (a SITE_PREVIEW_PASSWORD on the server still applies).' }
Write-Host "  Super admin: $AdminName <$AdminEmail>    Student: $StudentEmail" -ForegroundColor White
if ($DryRun) { Write-Host ''; Info 'Dry run: nothing was changed.'; exit 0 }

# ---------------------------------------------------------------- passwords (hashed locally)
function Read-Secret($label, $envName) {
    if ($envName) { $v = [Environment]::GetEnvironmentVariable($envName); if (-not $v) { Fail "$envName is empty." }; return $v }
    for ($i = 0; $i -lt 3; $i++) {
        $a = Read-Host "$label" -AsSecureString
        $b = Read-Host "$label (again)" -AsSecureString
        $pa = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($a))
        $pb = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($b))
        if ($pa -ceq $pb -and $pa) { return $pa }
        Warn 'The two entries differ, try again.'
    }
    Fail 'Passwords did not match.'
}
function Get-Hash($password, $email, [switch]$Staff) {
    $javaArgs = @('-cp', $Jar, '-Dloader.main=com.artistikcity.tools.HashPassword', 'org.springframework.boot.loader.launch.PropertiesLauncher')
    if ($Staff) { $javaArgs += '--staff' }
    $javaArgs += $email
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $JavaExe
    $psi.Arguments = ($javaArgs | ForEach-Object { '"' + ($_ -replace '"', '\"') + '"' }) -join ' '   # Windows PowerShell 5.1 has no ArgumentList
    $psi.RedirectStandardInput = $true; $psi.RedirectStandardOutput = $true; $psi.RedirectStandardError = $true; $psi.UseShellExecute = $false
    $p = [Diagnostics.Process]::Start($psi)
    $p.StandardInput.WriteLine($password); $p.StandardInput.Close()
    $out = $p.StandardOutput.ReadToEnd(); $err = $p.StandardError.ReadToEnd(); $p.WaitForExit()
    $hash = ($out -split "`r?`n" | Where-Object { $_ -match '^\$2[aby]\$\d\d\$.{53}$' } | Select-Object -First 1)
    if ($p.ExitCode -ne 0 -or -not $hash) {
        $reason = ($err -split "`r?`n" | Where-Object { $_ -and $_ -notmatch 'JAVA_TOOL_OPTIONS|Commons Logging' } | Select-Object -Last 1)
        return @{ ok = $false; reason = $reason }
    }
    return @{ ok = $true; hash = $hash }
}
Write-Host ''
Info "Password for the super admin ($AdminEmail): at least 12 characters"
while ($true) { $h = Get-Hash (Read-Secret 'Super admin password' $AdminPasswordEnv) $AdminEmail -Staff; if ($h.ok) { $adminHash = $h.hash; break }; Warn $h.reason; if ($AdminPasswordEnv) { Fail $h.reason } }
Info "Password for the student ($StudentEmail): at least 8 characters"
while ($true) { $h = Get-Hash (Read-Secret 'Student password' $StudentPasswordEnv) $StudentEmail; if ($h.ok) { $studentHash = $h.hash; break }; Warn $h.reason; if ($StudentPasswordEnv) { Fail $h.reason } }

Write-Host ''
Confirm-Word 'GO LIVE' 'Type GO LIVE to reset the website'

# ---------------------------------------------------------------- 1. final snapshot
if (-not $Local) {
    $final = "pre-go-live-$Stamp"
    Info "1/6 Taking the final snapshot $final of $dbId (5-15 minutes)"
    Invoke-Aws rds create-db-snapshot --region $Region --db-instance-identifier $dbId --db-snapshot-identifier $final --tags 'Key=purpose,Value=pre-go-live' --output text | Out-Null
    Invoke-Aws rds wait db-snapshot-available --region $Region --db-snapshot-identifier $final | Out-Null
    Info "Snapshot $final is ready"
}

# ---------------------------------------------------------------- 2. database
$work = Join-Path ([IO.Path]::GetTempPath()) ("artistik-golive-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $work | Out-Null
$tunnel = $null
try {
    if (-not $Local) {
        Info '2/6 Opening a private tunnel to the database'
        $tunnelLog = Join-Path $work 'tunnel.log'
        $tunnelArgs = @('ssm', 'start-session', '--region', $Region, '--target', $instance, '--document-name', 'AWS-StartPortForwardingSessionToRemoteHost',
                        '--parameters', "host=$endpoint,portNumber=3306,localPortNumber=$LocalPort")
        $sp = @{ FilePath = (Get-Command aws).Source; ArgumentList = $tunnelArgs; PassThru = $true; RedirectStandardOutput = $tunnelLog; RedirectStandardError = "$tunnelLog.err" }
        if ($onWindows) { $sp.WindowStyle = 'Hidden' }
        $tunnel = Start-Process @sp
        $open = $false
        for ($i = 0; $i -lt 60 -and -not $open; $i++) {
            Start-Sleep -Seconds 1
            if ($tunnel.HasExited) { Fail ("The tunnel stopped. Is the Session Manager plugin installed?`n" + (Get-Content "$tunnelLog.err" -Raw -ErrorAction SilentlyContinue)) }
            try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1', $LocalPort); $open = $c.Connected; $c.Close() } catch { }
        }
        if (-not $open) { Fail 'The tunnel did not open within 60 seconds.' }
        $secret = Invoke-Aws secretsmanager get-secret-value --region $Region --secret-id $secretArn --query SecretString --output text | ConvertFrom-Json
        $dbHost = '127.0.0.1'; $dbPort = $LocalPort; $dbUser = $secret.username; $dbPass = $secret.password; $dbName = 'artistikcity'
        $ssl = $SslMode
    } else {
        Info '2/6 Database'
        $ssl = $localSsl
    }
    $baseArgs = @('-h', $dbHost, '-P', "$dbPort", '-u', $dbUser, "--ssl-mode=$ssl", '--default-character-set=utf8mb4')
    $mysqlArgs = $baseArgs + @('--table', $dbName)
    if ($dbPass) { $env:MYSQL_PWD = $dbPass } else { Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue }
    # tables on the server that schema.sql doesn't know (left untouched, listed for information)
    $known = [regex]::Matches((Get-Content -Raw 'database\schema.sql'), '(?m)^CREATE TABLE\s+`?(\w+)') | ForEach-Object { $_.Groups[1].Value.ToLower() }
    $have = & $MysqlExe @($baseArgs + @('--batch', '--skip-column-names', $dbName, "--execute=SELECT LOWER(table_name) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'"))
    if ($LASTEXITCODE -ne 0) { Fail 'Could not read the database (see the message above).' }
    $extra = @($have | Where-Object { $_ -and $known -notcontains $_ -and $_ -ne 'schema_migrations' -and $_ -notlike 'ac_keep_*' })
    if ($extra.Count) { Warn ("Tables not in schema.sql (left as they are): " + ($extra -join ', ')) }

    $vars = @"
SET @ga_admin_email = '$AdminEmail';
SET @ga_admin_name = '$AdminName';
SET @ga_admin_hash = '$adminHash';
SET @ga_student_email = '$StudentEmail';
SET @ga_student_hash = '$studentHash';
SET @ga_keep_site_lock = $([int][bool]$KeepSiteLock);
"@
    $migrations = (Get-ChildItem 'database\migrations\*.sql' | Sort-Object Name | ForEach-Object { "INSERT IGNORE INTO schema_migrations (filename) VALUES ('$($_.Name)');" }) -join "`n"
    $all = @($vars, (Get-Content -Raw 'database\golive\go-live-before.sql'), (Get-Content -Raw 'database\schema.sql'),
             "CREATE TABLE IF NOT EXISTS schema_migrations (filename VARCHAR(191) NOT NULL PRIMARY KEY, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);`n$migrations",
             $vars, (Get-Content -Raw 'database\golive\golive-seed.sql'), (Get-Content -Raw 'database\golive\go-live-after.sql')) -join "`n`n"
    $sqlFile = Join-Path $work 'go-live.sql'
    [IO.File]::WriteAllText($sqlFile, $all, (New-Object Text.UTF8Encoding $false))
    Info 'Rebuilding the database'
    & $MysqlExe @mysqlArgs ("--execute=source " + ($sqlFile -replace '\\', '/'))
    if ($LASTEXITCODE -ne 0) { Fail 'The reset stopped with the error above. Restore the pre-go-live snapshot from the RDS console if the site looks wrong.' }
} finally {
    Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    if ($tunnel -and -not $tunnel.HasExited) {
        if ($onWindows) { & taskkill.exe /T /F /PID $tunnel.Id 2>&1 | Out-Null } else { Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue }
    }
    Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- 3. files
$filesDir = Join-Path $PSScriptRoot 'database\golive\files\uploads'
if ($Local) {
    Info '3/6 Copying the sample course files into storage\uploads'
    $dest = Join-Path $PSScriptRoot 'storage\uploads'
    Get-ChildItem -Path $filesDir -Recurse -File | ForEach-Object {
        $rel = $_.FullName.Substring($filesDir.Length).TrimStart('\', '/')
        $to = Join-Path $dest $rel
        New-Item -ItemType Directory -Force -Path (Split-Path $to) | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $to -Force
    }
} elseif (-not $SkipS3) {
    Info "3/6 Archiving the old uploads to s3://$bucket/archive/pre-go-live-$Stamp/"
    foreach ($p in 'storage/uploads/', 'private/') {
        & aws s3 mv "s3://$bucket/$p" "s3://$bucket/archive/pre-go-live-$Stamp/$p" --recursive --region $Region --only-show-errors
        if ($LASTEXITCODE -ne 0) { Fail "Moving s3://$bucket/$p failed (the database is already reset; run with -SkipS3 to skip this step next time)." }
    }
    Info 'Uploading the sample course files'
    & aws s3 cp $filesDir "s3://$bucket/storage/uploads/" --recursive --region $Region --cache-control 'public, max-age=86400' --only-show-errors
    if ($LASTEXITCODE -ne 0) { Fail 'Uploading the course files failed.' }
    if ($distribution) { Invoke-Aws cloudfront create-invalidation --distribution-id $distribution --paths '/storage/uploads/*' --output text | Out-Null }
}

# ---------------------------------------------------------------- 4. restart (new data, empty caches) and check
function Test-Site {
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Seconds 5
        try { $r = Invoke-WebRequest -Uri "$site/healthz" -UseBasicParsing -TimeoutSec 10; if ($r.StatusCode -eq 200) { return $true } } catch { }
    }
    return $false
}
if (-not $Local) {
    Info '4/6 Restarting the website'
    $cmdFile = Join-Path ([IO.Path]::GetTempPath()) ("artistik-restart-" + [guid]::NewGuid().ToString('N') + '.json')
    [IO.File]::WriteAllText($cmdFile, '{"commands":["docker restart artistikcity"]}', (New-Object Text.UTF8Encoding $false))
    try {
        $cmdId = Invoke-Aws ssm send-command --region $Region --instance-ids $instance --document-name AWS-RunShellScript --parameters "file://$cmdFile" --query 'Command.CommandId' --output text
        & aws ssm wait command-executed --region $Region --command-id $cmdId --instance-id $instance 2>$null
    } finally { Remove-Item $cmdFile -ErrorAction SilentlyContinue }
    if (Test-Site) { Info "$site is up with the new data" } else { Warn "$site/healthz did not answer yet: check the server with ec2-deploy.sh --status" }
} else {
    Info '4/6 Restart the site on this PC (close it and run START-ArtistikCity.bat) so it picks up the new data'
}

# ---------------------------------------------------------------- 5. old snapshots and backups
if (-not $Local -and -not $KeepOldSnapshots) {
    $old = @($snaps | Where-Object { $_.DBSnapshotIdentifier -ne $final })
    if ($old.Count -or @($retained).Count) {
        Write-Host ''
        Warn "5/6 These $($old.Count) snapshots and $(@($retained).Count) retained backups will be DELETED (cannot be undone). $final is kept."
        foreach ($s in $old) { Write-Host "      $($s.DBSnapshotIdentifier)" }
        foreach ($r in $retained) { Write-Host "      retained backup of $($r.DBInstanceIdentifier) ($($r.DbiResourceId))" }
        Confirm-Word 'DELETE' 'Type DELETE to delete them'
        foreach ($s in $old) { Info "Deleting $($s.DBSnapshotIdentifier)"; Invoke-Aws rds delete-db-snapshot --region $Region --db-snapshot-identifier $s.DBSnapshotIdentifier --output text | Out-Null }
        foreach ($r in $retained) { Info "Deleting retained backup $($r.DbiResourceId)"; Invoke-Aws rds delete-db-instance-automated-backup --region $Region --dbi-resource-id $r.DbiResourceId --output text | Out-Null }
    } else { Info '5/6 No older snapshots to delete' }
}
if (-not $Local -and -not $KeepAutomatedBackups -and $retention -gt 0) {
    Info "6/6 Clearing the automated backups of $dbId (retention 0, then $retention days; the site pauses twice for a minute or two)"
    Invoke-Aws rds modify-db-instance --region $Region --db-instance-identifier $dbId --backup-retention-period 0 --apply-immediately --output text | Out-Null
    Start-Sleep -Seconds 30
    Invoke-Aws rds wait db-instance-available --region $Region --db-instance-identifier $dbId | Out-Null
    Invoke-Aws rds modify-db-instance --region $Region --db-instance-identifier $dbId --backup-retention-period $retention --apply-immediately --output text | Out-Null
    Start-Sleep -Seconds 30
    Invoke-Aws rds wait db-instance-available --region $Region --db-instance-identifier $dbId | Out-Null
    Info "Automated backups are on again ($retention days); the first new one is taken in the next backup window"
    if (Test-Site) { Info "$site is up" } else { Warn "$site/healthz did not answer after the database restart: check ec2-deploy.sh --status" }
}

Write-Host ''
Info 'Done. Sign in:'
Write-Host "    admin    $AdminEmail  at /admin/login (set up two-step verification first: account menu > Two-step verification)"
Write-Host "    student  $StudentEmail  at /login (a test buyer: its checkouts can simulate payments)"
Write-Host '  The six courses are samples: check prices and dates in Admin > Courses before announcing anything.'
if (-not $Local) { Write-Host "  Undo point: snapshot $(if ($final) { $final }) (RDS console > Snapshots > Restore), old files under s3://$bucket/archive/pre-go-live-$Stamp/" }
