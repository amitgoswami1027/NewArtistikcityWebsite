# ============================================================
#  ArtistikCity - trim the LIVE (AWS) database to a small starter set
#  Runs database\maintenance\trim-to-starter.sql on the AWS database:
#  keeps 2 students, 2 courses, 2 paintings, 2 blog posts, 3 staff logins
#  (edit the lists at the top of that file to keep others).
#
#  1. takes an RDS snapshot first (the undo point) and waits for it
#  2. opens a private tunnel to the database through the server (Session Manager)
#  3. runs the clean-up and shows what is left, then closes the tunnel
#
#  Needs: AWS CLI signed in (aws configure), the Session Manager plugin.
#  You must type TRIM to confirm.
# ============================================================
param(
    [string]$Region = 'ap-south-1',
    [string]$Stack = 'artistikcity',
    [int]$LocalPort = 13306,
    [string]$MysqlExe = '',
    [string]$SqlFile = 'database\maintenance\trim-to-starter.sql',
    [string]$SslMode = 'REQUIRED',
    [switch]$SkipSnapshot
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

function Info($m) { Write-Host "==> $m" -ForegroundColor Cyan }
function Fail($m) { Write-Host "ERROR: $m" -ForegroundColor Red; exit 1 }
# runs the AWS CLI and returns its output (the CLI prints its own error message on failure)
function Invoke-Aws { $out = & aws @args; if ($LASTEXITCODE -ne 0) { Fail ("aws " + ($args[0..1] -join ' ') + " failed (see the message above).") }; return ($out | Out-String).Trim() }

if (-not $MysqlExe) { $MysqlExe = Join-Path $PSScriptRoot '.tools\mysql-8.4.13-winx64\bin\mysql.exe' }
if (-not (Test-Path -LiteralPath $MysqlExe)) { Fail "MySQL client not found at $MysqlExe (run START-ArtistikCity.bat once to download it)." }
if (-not (Test-Path -LiteralPath $SqlFile)) { Fail "Clean-up script not found: $SqlFile" }
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { Fail 'The AWS CLI is not installed (https://aws.amazon.com/cli/).' }

Info 'Reading the AWS stack'
$outputs = Invoke-Aws cloudformation describe-stacks --region $Region --stack-name $Stack --query 'Stacks[0].Outputs' --output json | ConvertFrom-Json
function Out($k) { $v = ($outputs | Where-Object { $_.OutputKey -eq $k }).OutputValue; if (-not $v) { Fail "Stack output $k not found." }; return $v }
$instance = Out 'InstanceId'
$endpoint = Out 'DatabaseEndpoint'
$secretArn = Out 'DatabaseSecretArn'

Write-Host ''
Write-Host 'This deletes data on the LIVE website (www) and keeps only the records listed at the top of' -ForegroundColor Yellow
Write-Host "  $SqlFile" -ForegroundColor Yellow
Write-Host 'A database snapshot is taken first, so it can be undone from the RDS console.' -ForegroundColor Yellow
$answer = Read-Host 'Type TRIM to continue'
if ($answer -cne 'TRIM') { Write-Host 'Nothing was changed.'; exit 0 }

if (-not $SkipSnapshot) {
    $dbId = Invoke-Aws rds describe-db-instances --region $Region --query "DBInstances[?Endpoint.Address=='$endpoint'].DBInstanceIdentifier" --output text
    if (-not $dbId) { Fail "No RDS database with endpoint $endpoint." }
    $snap = 'before-trim-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
    Info "Taking snapshot $snap of $dbId (5-10 minutes)"
    Invoke-Aws rds create-db-snapshot --region $Region --db-instance-identifier $dbId --db-snapshot-identifier $snap --output text | Out-Null
    Invoke-Aws rds wait db-snapshot-available --region $Region --db-snapshot-identifier $snap | Out-Null
    Info "Snapshot $snap is ready"
}

Info 'Opening a private tunnel to the database'
$tunnelLog = Join-Path ([IO.Path]::GetTempPath()) ("artistik-tunnel-" + [guid]::NewGuid().ToString('N') + '.log')
$tunnelArgs = @('ssm', 'start-session', '--region', $Region, '--target', $instance, '--document-name', 'AWS-StartPortForwardingSessionToRemoteHost',
                '--parameters', "host=$endpoint,portNumber=3306,localPortNumber=$LocalPort")
$awsCmd = (Get-Command aws).Source
$onWindows = $env:OS -eq 'Windows_NT'
$sp = @{ FilePath = $awsCmd; ArgumentList = $tunnelArgs; PassThru = $true; RedirectStandardOutput = $tunnelLog; RedirectStandardError = "$tunnelLog.err" }
if ($onWindows) { $sp.WindowStyle = 'Hidden' }
$tunnel = Start-Process @sp
try {
    $open = $false
    for ($i = 0; $i -lt 60 -and -not $open; $i++) {
        Start-Sleep -Seconds 1
        if ($tunnel.HasExited) { Fail ("The tunnel stopped. Is the Session Manager plugin installed?`n" + (Get-Content "$tunnelLog.err" -Raw -ErrorAction SilentlyContinue)) }
        try { $c = New-Object System.Net.Sockets.TcpClient; $c.Connect('127.0.0.1', $LocalPort); $open = $c.Connected; $c.Close() } catch { }
    }
    if (-not $open) { Fail 'The tunnel did not open within 60 seconds.' }

    $secret = Invoke-Aws secretsmanager get-secret-value --region $Region --secret-id $secretArn --query SecretString --output text | ConvertFrom-Json
    Info 'Trimming the data'
    $env:MYSQL_PWD = $secret.password
    try {
        $src = (Resolve-Path -LiteralPath $SqlFile).Path -replace '\\', '/'
        & $MysqlExe -h 127.0.0.1 -P $LocalPort -u $secret.username "--ssl-mode=$SslMode" --default-character-set=utf8mb4 --table artistikcity "--execute=source $src"
        if ($LASTEXITCODE -ne 0) { Fail 'The clean-up stopped with the error above. Restore the snapshot if the site looks wrong.' }
    } finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
    Write-Host ''
    Info 'Done. Open the site and check /courses, /marketplace and /studio-stories.'
} finally {
    # the tunnel runs as aws.exe + session-manager-plugin.exe: stop both
    if ($tunnel -and -not $tunnel.HasExited) {
        if ($onWindows) { & taskkill.exe /T /F /PID $tunnel.Id 2>&1 | Out-Null } else { Stop-Process -Id $tunnel.Id -Force -ErrorAction SilentlyContinue }
    }
    Remove-Item $tunnelLog, "$tunnelLog.err" -ErrorAction SilentlyContinue
}
