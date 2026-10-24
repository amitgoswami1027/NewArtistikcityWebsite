# Deploying ArtistikCity on AWS

This guide takes the site from your PC to AWS in the Mumbai region (`ap-south-1`), with automatic deploys from GitHub.
Commands are for **Windows PowerShell** with the [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
installed and signed in (`aws configure`, region `ap-south-1`). Everything is created by two CloudFormation templates in `infra/`.

## How it fits together

```
 visitor ──HTTPS──► CloudFront (your domain, free AWS certificate)
                      ├── /storage/*          ──► S3 bucket (private; uploads: course photos, student work, artworks, videos)
                      ├── /js /css /assets /fonts ─► EC2, cached at the edge
                      └── everything else      ──► EC2 :80  ──► Docker container (Spring Boot + React, port 8080)
                                                              │  only CloudFront can reach it (security group + secret header)
                                                              └──► RDS MySQL 8.4 (private, TLS, daily backups)
 GitHub push to main ──► GitHub Actions: test ► build image ► ECR ► SSM Run Command on EC2 ► migrate, swap, health check, auto-rollback
```

| Piece | What it is | Why |
|---|---|---|
| CloudFront | CDN in front of everything | HTTPS on your domain, fast images/videos across India, hides the server |
| S3 bucket | Uploaded files under `storage/...` | Survives server rebuilds; no disk to fill up |
| EC2 (t3.small, Amazon Linux 2023) | Runs the app image with Docker | Simple, one server; resize any time |
| RDS MySQL 8.4 | Managed database | Backups, patches, encryption handled by AWS |
| ECR | Private Docker image registry | Every deploy is an image tagged with its git commit → easy rollback |
| Secrets Manager / Parameter Store | Passwords and keys | Nothing secret lives in the code, GitHub or the server's disk image |
| IAM roles | Permissions | The server and GitHub get temporary credentials; no access keys anywhere |

### What changed in the code

| Change | Where | Setting |
|---|---|---|
| Uploads can live in S3 (same `/storage/...` URLs, nothing in the database changes) | `support/Storage`, `support/s3/*`, `web/StorageController` | `STORAGE_DRIVER=s3`, `S3_BUCKET`, `AWS_REGION` (+ `S3_PREFIX`, default `storage/`) |
| Safe behind CloudFront: real visitor IP for rate limits, HTTPS detection, direct access blocked | `http/CloudFrontFilter` | `TRUST_CLOUDFRONT=true`, `ORIGIN_VERIFY_SECRET` |
| Health check for deploys and monitoring | `GET /healthz` → `{"status":"UP","db":"UP"}` | – |
| Gzip for pages and the 4.5 MB script bundle (→ ~0.9 MB) | `application.properties` | `SERVER_COMPRESSION` (on) |
| Secure cookies on HTTPS | `SessionInterceptor`, deploy settings | `SESSION_COOKIE_SECURE=true` |
| Image runs as a non-root user and carries its own migrations and deploy script | `Dockerfile`, `deploy/` | – |

Locally nothing changes: without these settings the app still uses the `storage/` folder and `START-ArtistikCity.bat` works as before.

The S3 client talks to the S3 REST API directly (AWS Signature Version 4, `support/s3/SigV4.java`) instead of the AWS SDK, so
no new libraries are needed. It is checked against AWS's published signature examples (`SigV4Test`) and was tested end to end
against an S3 emulator that verifies every signature. On EC2 it gets credentials from the server's IAM role (IMDSv2).
If you prefer the official SDK later, only `support/s3/S3Client.java` would change.

---

## Step 0 - Before you start

- An AWS account with billing set up, and the AWS CLI v2 on your PC (`aws --version`).
- Your domain and access to its DNS settings. This guide uses **`www.yourdomain.com`** as the site address - replace it everywhere.
- Your GitHub repository: `amitgoswami1027/NewArtistikcityWebsite`, branch `main`.
- Optional, for copying your existing database (step 7): the
  [Session Manager plugin](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) for the AWS CLI.
- Use the stack name **`artistikcity`** (lower case: it is used for the bucket and image repository names).

Rough monthly cost at low traffic: EC2 t3.small ≈ $16, RDS db.t4g.micro ≈ $15 + storage ≈ $3, public IPv4 address ≈ $4,
three secrets ≈ $1.2, plus CloudFront/S3 traffic (pennies per GB). About **$40-45 a month** before data transfer. Check with the
[AWS Pricing Calculator](https://calculator.aws/) for your expected traffic.

## Step 1 - HTTPS certificate (us-east-1)

CloudFront only uses certificates from `us-east-1`, so this small stack goes there:

```powershell
aws cloudformation deploy --region us-east-1 --stack-name artistikcity-cert `
  --template-file infra/certificate.yml `
  --parameter-overrides DomainName=www.yourdomain.com AlternateDomainName=yourdomain.com
```

While it runs, open **ACM in us-east-1** (console → Certificate Manager → region N. Virginia), open the pending certificate and
add the **CNAME record(s)** it shows at your DNS provider. The stack finishes a few minutes after DNS has them
(if your DNS is in Route 53 in this account, pass `HostedZoneId=Z...` and it is done for you). Then:

```powershell
aws cloudformation describe-stacks --region us-east-1 --stack-name artistikcity-cert `
  --query "Stacks[0].Outputs[?OutputKey=='CertificateArn'].OutputValue" --output text
```

Leave out `AlternateDomainName` if you only want `www`.

## Step 2 - Look up the network values

The default VPC is fine. Find its id, its subnets (they are public and in different zones) and CloudFront's IP list:

```powershell
$vpc = aws ec2 describe-vpcs --region ap-south-1 --filters Name=isDefault,Values=true --query "Vpcs[0].VpcId" --output text
aws ec2 describe-subnets --region ap-south-1 --filters Name=vpc-id,Values=$vpc `
  --query "Subnets[].[SubnetId,AvailabilityZone,MapPublicIpOnLaunch]" --output table
$pl = aws ec2 describe-managed-prefix-lists --region ap-south-1 `
  --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing --query "PrefixLists[0].PrefixListId" --output text
$vpc; $pl
```

Pick one subnet for the server (`MapPublicIpOnLaunch` must be `True`) and two or three subnets in different zones for the database.

## Step 3 - Create the infrastructure

```powershell
aws cloudformation deploy --region ap-south-1 --stack-name artistikcity `
  --template-file infra/artistikcity.yml --capabilities CAPABILITY_IAM `
  --parameter-overrides `
    DomainName=www.yourdomain.com AlternateDomainName=yourdomain.com `
    CertificateArn=arn:aws:acm:us-east-1:123456789012:certificate/xxxx `
    VpcId=$vpc AppSubnetId=subnet-aaaa DbSubnetIds=subnet-aaaa,subnet-bbbb,subnet-cccc `
    CloudFrontPrefixListId=$pl `
    GitHubOwner=amitgoswami1027 GitHubRepo=NewArtistikcityWebsite
```

This takes 20-30 minutes (the database and CloudFront are the slow parts). It creates:

| Resource | Notes |
|---|---|
| S3 bucket `artistikcity-uploads-<account>` | Private, encrypted, versioned (old versions kept 30 days), TLS only. Only CloudFront can read, only the server can write. |
| CloudFront distribution | HTTP→HTTPS redirect, TLS 1.2+, HTTP/2+3. `/storage/*` from S3 with Origin Access Control; static files cached; pages never cached. |
| EC2 server + Elastic IP | Amazon Linux 2023, Docker, 2 GB swap, encrypted 30 GB disk, IMDSv2 only. Security group: **port 80 from CloudFront only**. No SSH unless you pass `SshCidr` (use Session Manager instead). |
| RDS MySQL 8.4 | Private (no public IP), reachable only from the server's security group, TLS required, utf8mb4, IST time zone, 7 days of backups, deletion protection, password in Secrets Manager. |
| ECR repository `artistikcity` | Tags can't be overwritten, scanned on push, keeps the last 30 images. |
| Secrets | DB password (managed by RDS), the CloudFront→server secret, the OTP key. All generated by AWS. |
| IAM | Server role: its bucket folder, pull images, read its secrets/settings, write logs, Session Manager. GitHub role: push images, run the deploy on this one server, refresh CloudFront. |
| CloudWatch log group `/artistikcity/app` | App logs, kept 30 days. |

About the ports you might expect: **443** is handled by CloudFront (the certificate lives there), the **app port 8080** is
only inside the server (Docker maps it to 80), and **SSH 22** is closed by default because Session Manager gives you a shell
without an open port or a key. If you really want SSH, add `SshCidr=<your-ip>/32 KeyName=<key-pair>`.

Show the outputs (you need them in the next steps):

```powershell
aws cloudformation describe-stacks --region ap-south-1 --stack-name artistikcity --query "Stacks[0].Outputs" --output table
```

## Step 4 - App settings (payments, e-mail, sign-in)

The database, S3, CloudFront secret and OTP key are wired up automatically. Everything else is optional and goes into
**Parameter Store** under `/artistikcity/prod/` as encrypted `SecureString` values. The name of each parameter is the
environment variable from README §4:

```powershell
function Set-AppSetting($name, $value) {
  aws ssm put-parameter --region ap-south-1 --name "/artistikcity/prod/$name" --type SecureString --value $value --overwrite | Out-Null
}
Set-AppSetting RAZORPAY_KEY        "rzp_live_..."
Set-AppSetting RAZORPAY_SECRET     "..."
Set-AppSetting RAZORPAY_WEBHOOK_SECRET "..."
Set-AppSetting MAIL_HOST           "email-smtp.ap-south-1.amazonaws.com"
Set-AppSetting MAIL_PORT           "587"
Set-AppSetting MAIL_USERNAME       "..."
Set-AppSetting MAIL_PASSWORD       "..."
Set-AppSetting MAIL_FROM_ADDRESS   "no-reply@yourdomain.com"
Set-AppSetting NEWSLETTER_SECRET   "<any long random text>"
# optional: GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET, FACEBOOK_CLIENT_ID / FACEBOOK_CLIENT_SECRET,
#           PAYPAL_CLIENT_ID / PAYPAL_SECRET / PAYPAL_MODE, SMS_PROVIDER + TWILIO_*, WEBAUTHN_RP_ID / WEBAUTHN_ORIGINS
```

Settings are read on every deploy. After changing one, re-run the last deploy (Actions → Deploy to AWS → Run workflow) or
roll back and forward. Don't set `FORWARD_HEADERS` (the CloudFront filter does that job) or `PAYMENT_TEST_MODE=true` in production.

Also update the addresses registered with your providers to the real domain:
Razorpay webhook `https://www.yourdomain.com/api/v1/courses/razorpay-webhook`,
Google `https://www.yourdomain.com/social-login/google/callback`, Facebook `https://www.yourdomain.com/social-login/facebook/callback`.
For e-mail, Amazon SES in ap-south-1 works well (verify your domain in SES first).

## Step 5 - Point your domain at CloudFront

At your DNS provider add **`www` → CNAME → the `CloudFrontDomain` output** (`dxxxxxxxx.cloudfront.net`).
A bare domain (`yourdomain.com`) can't be a CNAME at most providers: either use your provider's "ALIAS/ANAME/flattening"
record pointing to the same CloudFront name, or redirect `yourdomain.com` to `https://www.yourdomain.com` in the provider's settings.
With Route 53, create an **A (alias)** record to the distribution.

## Step 6 - Connect GitHub and deploy

1. In GitHub → repository **Settings → Secrets and variables → Actions → Variables**, add (values from the stack outputs):

   | Variable | Value |
   |---|---|
   | `AWS_DEPLOY_ROLE_ARN` | output `GitHubDeployRoleArn` |
   | `ECR_REPOSITORY` | output `EcrRepository` (`artistikcity`) |
   | `EC2_INSTANCE_ID` | output `InstanceId` |
   | `CLOUDFRONT_DISTRIBUTION_ID` | output `DistributionId` |
   | `SITE_DOMAIN` | `www.yourdomain.com` |

   No AWS keys go into GitHub: the workflow signs in with OpenID Connect and gets a one-hour role session.
2. Optional: **Settings → Environments → production → Required reviewers** to approve each production deploy by hand.
3. Push to `main` (e.g. with `PUSH-TO-GITHUB.bat`). The workflow `.github/workflows/deploy.yml` then:
   - **Test**: unit tests; installs the database from scratch on MySQL 8.4 and runs the migrations a second time (must change
     nothing); starts the app and checks `/healthz`, `/`, `/courses`, `/login`, `/admin/login`, `/faq`.
   - **Build**: builds the Docker image and pushes it to ECR as `artistikcity:<commit sha>`.
   - **Deploy**: through SSM, the server runs `deploy-image.sh`, which takes `deploy/ec2-deploy.sh` out of the new image and runs it:
     1. writes the settings file from Secrets Manager and Parameter Store (only readable by root);
     2. pulls the image;
     3. runs the database migrations (`deploy/db-migrate.sh`, in a `mysql:8.4` container). **If a migration fails the deploy
        stops here and the running site is not touched;**
     4. replaces the container (about 30-40 seconds of downtime while Spring Boot starts);
     5. waits for `/healthz`; **if the new version is not healthy within 3 minutes it starts the previous image again** and
        the workflow fails with the app's last log lines;
     6. refreshes CloudFront's cache for `/assets/*` and `/fonts/*` (`app.js` and the theme CSS carry a content hash);
     7. checks `https://www.yourdomain.com/healthz` from the internet.

The **first** deploy finds an empty database and creates all tables, so the site starts empty. Step 7 copies your real
courses, users, orders and uploads from your PC.

### Rolling back

- **Automatic**: a deploy that isn't healthy rolls itself back.
- **One click**: Actions → *Deploy to AWS* → **Run workflow** → tick **rollback** (goes to the version before the current one;
  run it again to undo the rollback).
- **A specific version**: Run workflow → `image_tag` = the commit SHA of a good build.
- **On the server**: Session Manager → `sudo /opt/artistikcity/ec2-deploy.sh --rollback` (or `--status`).

Rollback changes the app only. Database migrations are not undone, so write migrations that the previous version can live
with (add columns/tables first, remove old ones in a later release).

## Step 7 - Move your existing data (database + uploads)

Do this before you announce the site. It replaces the tables on AWS with the ones from your PC.

**1. Export the database on your PC** (MySQL running via `START-MySQL.bat`; root password `ArtistikRoot@2026` unless you changed it):

```powershell
.\.tools\mysql-8.4.13-winx64\bin\mysqldump.exe -h 127.0.0.1 -u root -p --single-transaction --routines --triggers `
  --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 artistikcity --result-file=artistikcity.sql
# RDS doesn't allow objects owned by 'root'@'localhost' (the view in the schema): drop the DEFINER clauses.
# --hex-blob writes binary columns (passkeys) as text; ReadAllText/WriteAllText keep the file UTF-8 without a BOM,
# so ₹, é etc. survive.
$sql = [IO.File]::ReadAllText("$PWD\artistikcity.sql") -replace 'DEFINER=`[^`]+`@`[^`]+`', ''
[IO.File]::WriteAllText("$PWD\artistikcity-rds.sql", $sql)
```

**2. Open a private tunnel to RDS through the server** (Session Manager plugin needed; leave this window open):

```powershell
$out = aws cloudformation describe-stacks --region ap-south-1 --stack-name artistikcity --query "Stacks[0].Outputs" | ConvertFrom-Json
$id = ($out | ? OutputKey -eq InstanceId).OutputValue; $db = ($out | ? OutputKey -eq DatabaseEndpoint).OutputValue
aws ssm start-session --region ap-south-1 --target $id --document-name AWS-StartPortForwardingSessionToRemoteHost `
  --parameters "host=$db,portNumber=3306,localPortNumber=13306"
```

**3. Import it** (in a second PowerShell window, in the project folder):

```powershell
$out = aws cloudformation describe-stacks --region ap-south-1 --stack-name artistikcity --query "Stacks[0].Outputs" | ConvertFrom-Json
$sec = aws secretsmanager get-secret-value --region ap-south-1 --secret-id ($out | ? OutputKey -eq DatabaseSecretArn).OutputValue `
  --query SecretString --output text | ConvertFrom-Json
$env:MYSQL_PWD = $sec.password          # read by mysql.exe; avoids quoting problems with special characters
.\.tools\mysql-8.4.13-winx64\bin\mysql.exe -h 127.0.0.1 -P 13306 -u $sec.username --ssl-mode=REQUIRED `
  --default-character-set=utf8mb4 artistikcity --execute="source artistikcity-rds.sql"
Remove-Item Env:\MYSQL_PWD
```

`source` lets mysql read the file itself (piping a file into a program in Windows PowerShell can change its encoding).
The dump includes `schema_migrations`, so the next deploy only runs migrations your PC didn't have yet.

**4. Upload the files** to the bucket (same paths as on your PC):

```powershell
$bucket = ($out | ? OutputKey -eq UploadsBucketName).OutputValue
aws s3 sync storage\uploads "s3://$bucket/storage/uploads" --region ap-south-1
aws s3 sync storage\certificates "s3://$bucket/storage/certificates" --region ap-south-1
```

Then open `https://www.yourdomain.com` - courses, photos, the studio and the admin should look exactly like on your PC.
**Change or remove the demo accounts** (`password` is their password) before going live.

## Day-to-day

| Task | How |
|---|---|
| See the app log | `aws logs tail /artistikcity/app --follow --region ap-south-1` or CloudWatch → Log groups |
| Shell on the server | EC2 console → instance → **Connect → Session Manager** (or `aws ssm start-session --target <InstanceId>`) |
| What is running | on the server: `sudo /opt/artistikcity/ec2-deploy.sh --status` |
| Database backups | automatic daily (7 days); manual snapshot in the RDS console before big changes |
| Bigger server / database | change `InstanceType` / `DbInstanceClass` and re-run step 3 (the server is replaced; the next deploy brings the app back - re-run the last workflow) |
| New secret value | `Set-AppSetting NAME value`, then re-run the last deploy |
| Rotate the CloudFront secret | Secrets Manager → `artistikcity/origin-verify` → new value, re-run step 3 (updates CloudFront), then re-run the last deploy |

## Troubleshooting

| Symptom | Likely cause / fix |
|---|---|
| Every page is **403** | The server's `ORIGIN_VERIFY_SECRET` differs from CloudFront's header (secret changed without redeploying). Re-run the last deploy. |
| **502 / 504** from CloudFront | The app isn't running or is still starting. Check the log; `--status` on the server. |
| Images **403/404** | File not in the bucket under `storage/...` (step 7.4), or the bucket policy was changed. |
| Deploy fails at "database migration" | The SQL error is in the workflow log; nothing was changed on the live site. Fix the migration and push again. |
| Deploy fails at the health check and rolls back | The app's last log lines are in the workflow log (often a missing setting). |
| Stack stuck on `AppInstance` | The server's set-up script failed: EC2 → instance → Actions → Monitor → *Get system log*, or `/var/log/cloud-init-output.log` via Session Manager. |
| `aws cloudformation delete-stack` fails | The database has deletion protection on purpose; turn it off in RDS first. The bucket and image repository are kept when the stack is deleted. |

## Files

| File | Purpose |
|---|---|
| `infra/certificate.yml` | ACM certificate (us-east-1) |
| `infra/artistikcity.yml` | Everything else (ap-south-1) |
| `.github/workflows/deploy.yml` | CI/CD |
| `deploy/ec2-deploy.sh` | Deploy / rollback / status on the server (runs from inside the image) |
| `deploy/db-migrate.sh` | Creates or upgrades the database (same rules as the Windows launcher) |
| `Dockerfile`, `.dockerignore` | The production image |
