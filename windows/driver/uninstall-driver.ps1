<#  Removes the FireReader fastboot driver and its self-signed certificate. Run elevated. #>
$pub = pnputil /enum-drivers | Out-String
$blocks = $pub -split "(\r?\n){2,}"
foreach ($b in $blocks) { if ($b -match 'fire_fastboot\.inf' -and $b -match '(oem\d+\.inf)') { pnputil /delete-driver $Matches[1] /uninstall /force } }
foreach ($store in 'Root','TrustedPublisher') {
  Get-ChildItem "Cert:\LocalMachine\$store" | Where-Object Subject -eq 'CN=FireReader Driver' | Remove-Item
}
Get-ChildItem Cert:\CurrentUser\My | Where-Object Subject -eq 'CN=FireReader Driver' | Remove-Item
Write-Host 'Driver and certificate removed.'
