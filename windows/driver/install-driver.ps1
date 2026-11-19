<#
  Installs the fastboot WinUSB driver for Fire tablets.
  Run from an elevated PowerShell:  powershell -ExecutionPolicy Bypass -File install-driver.ps1
  Needs the Windows SDK (makecat.exe, signtool.exe).
  A local self-signed certificate is created and trusted ONLY to sign this driver.
  Remove it afterwards with uninstall-driver.ps1.
#>
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk = Get-ChildItem 'C:\Program Files (x86)\Windows Kits\10\bin\*\x64\signtool.exe' | Sort-Object FullName | Select-Object -Last 1
if (-not $sdk) { throw 'Windows SDK not found (signtool.exe). Install the Windows SDK first.' }
$bin = $sdk.DirectoryName
Set-Location $here

@"
[CatalogHeader]
Name=fire_fastboot.cat
ResultDir=$here
PublicVersion=0x0000001
EncodingType=0x00010001
CATATTR1=0x10010001:OSAttr:2:10.0

[CatalogFiles]
<hash>fire_fastboot.inf=$here\fire_fastboot.inf
"@ | Set-Content -Encoding ASCII fire_fastboot.cdf
& "$bin\makecat.exe" -v fire_fastboot.cdf

$cert = Get-ChildItem Cert:\CurrentUser\My | Where-Object Subject -eq 'CN=FireReader Driver' | Select-Object -First 1
if (-not $cert) { $cert = New-SelfSignedCertificate -Type CodeSigningCert -Subject 'CN=FireReader Driver' -CertStoreLocation Cert:\CurrentUser\My -NotAfter (Get-Date).AddYears(1) }
Export-Certificate -Cert $cert -FilePath "$here\firereader-driver.cer" | Out-Null
certutil -addstore -f Root "$here\firereader-driver.cer"
certutil -addstore -f TrustedPublisher "$here\firereader-driver.cer"
& "$bin\signtool.exe" sign /sha1 $cert.Thumbprint /fd sha256 /s My fire_fastboot.cat
pnputil /add-driver "$here\fire_fastboot.inf" /install
pnputil /scan-devices
Write-Host 'Done. Check with: fastboot devices'
