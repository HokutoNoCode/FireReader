<#
  Creates a small headless Ubuntu VM in VirtualBox that automatically grabs the
  Fire tablet over USB (MediaTek bootrom 0E8D and Lab126 1949), so the Linux-only
  amonet bootrom step can run from a Windows PC.
  Prereqs: VirtualBox 7.x, seed.iso (see make-seed.py), internet.
#>
$ErrorActionPreference = 'Continue'
$vb  = 'C:\Program Files\Oracle\VirtualBox\VBoxManage.exe'
$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ova = "$dir\ubuntu.ova"
if (-not (Test-Path $ova)) {
  curl.exe -L -o $ova https://cloud-images.ubuntu.com/noble/current/noble-server-cloudimg-amd64.ova
}
& $vb import $ova --vsys 0 --vmname firevm --memory 2048 --cpus 1
& $vb modifyvm firevm --nic1 nat --natpf1 "ssh,tcp,127.0.0.1,2222,,22"
& $vb modifyvm firevm --uart1 0x3F8 4 --uartmode1 file "$dir\serial.log"
# The OVA already has an IDE controller: put the cloud-init seed there.
& $vb storageattach firevm --storagectl IDE --port 0 --device 0 --type dvddrive --medium "$dir\seed.iso"
& $vb modifyvm firevm --usbxhci on
& $vb usbfilter add 0 --target firevm --name MediaTek --vendorid 0E8D
& $vb usbfilter add 1 --target firevm --name Lab126 --vendorid 1949
& $vb startvm firevm --type headless
Write-Host 'VM started. When ready:  ssh -p 2222 fire@127.0.0.1'
