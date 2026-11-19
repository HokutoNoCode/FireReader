# Step-by-step guide: from Fire HD 8 (2017) to FireReader, starting on Windows

<sub>🇮🇹 [Versione italiana](GUIDA.md)</sub>

**Contents:** [0. What you need](#0-what-you-need) · [1. Fastboot driver](#1-fastboot-driver-for-windows) ·
[2. Intentional brick](#2-intentional-preloader-brick-from-windows) · [3. Bootrom in the VM](#3-bootrom-step-in-the-linux-vm) ·
[4. TWRP](#4-twrp) · [5. LineageOS](#5-lineageos-151-android-81) · [6. FireReader](#6-firereader) ·
[Troubleshooting](#if-something-goes-wrong)

Expected time: **1–2 hours** the first time, much of it spent downloading.

> ⚠️ **Read everything before you start.** Unlocking the bootloader wipes all data on the tablet and,
> if something goes wrong, can leave it unbootable (usually recoverable, but painfully).
> You do this at your own risk. This guide is **only** for the **7th-generation Fire HD 8 (2017)**,
> codename **douglas**, model **KFDOWI**.

## 0. What you need

- A Fire HD 8 2017 (`adb shell getprop ro.product.device` must return `douglas`)
- A Windows 10/11 PC and a reliable micro-USB cable
- [platform-tools](https://developer.android.com/tools/releases/platform-tools) (adb and fastboot)
- The Windows SDK (for `makecat.exe` and `signtool.exe`, needed by the fastboot driver)
- [VirtualBox 7](https://www.virtualbox.org/) (only for the one step that doesn't work on Windows)
- Python 3 on Windows with `pip install pycdlib` (to build the VM configuration)
- The **amonet-douglas v1.2** and **brick-douglas** files from
  [k4y0z's XDA thread](https://xdaforums.com/t/unlock-root-twrp-unbrick-fire-hd-8-2017-douglas.3962846/)
  (also summarized in [ryanfortner/FireHD8-2017](https://github.com/ryanfortner/FireHD8-2017)).
  They are **not** included in this repository.
- LineageOS 15.1 for douglas, from
  [Rortiz2's XDA thread](https://xdaforums.com/t/rom-unlocked-douglas-giza-otas-lineage-15-1-02-jan-2022.4328503/)

On the tablet: *Settings → Device Options → About → tap the serial number 7 times*,
then *Developer Options → ADB debugging*. Check with `adb devices`.

> 🐧 **On Linux?** Skip the driver (step 1) and the VM: run every command natively, with `sudo` for
> fastboot and amonet. Keep `-S 512M` in step 2, it does no harm.

## 1. Fastboot driver for Windows

In fastboot mode the Fire shows up as `USB\VID_1949&PID_0260` and Windows has no driver for it:
`fastboot` hangs on `< waiting for any device >`.

1. `adb reboot bootloader`
2. In PowerShell **as Administrator**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File windows\driver\install-driver.ps1
   ```
   The script signs a WinUSB driver package with a local certificate and installs it.
3. `fastboot devices` should now list the serial, and `fastboot getvar all` should report `product: DOUGLAS`.

> The certificate is only used to sign the driver. **Remove it when you're done** with
> `windows\driver\uninstall-driver.ps1`.

## 2. Intentional preloader brick (from Windows)

Recent firmware is no longer vulnerable to mtk-su, so we use the *brick* method: the preloader header is
damaged on purpose so the chip falls back to its emergency mode (bootrom).

```bat
fastboot -S 512M flash brick amonet\bin\brick-9820.img
```

`-S 512M` is **essential** on Windows: without it, modern fastboot splits the image into "sparse" chunks and
the exploit never fires. The tablet screen must show:

```
PWNED by k4y0z!!!  Bricking Preloader...
All good, now unplug the device and power off
```

Then **unplug the cable** and hold the power button for ~30 seconds.
From here on the screen stays black: that's expected.

## 3. Bootrom step (in the Linux VM)

On Windows, amonet's `main.py` reaches "all good" but then the eMMC reads fail
(`RuntimeError: read fail`): the Windows serial port can't talk to the payload.
The fix is a small Ubuntu VM that takes over the tablet over USB.

1. Create an SSH key: `ssh-keygen -t ed25519 -f %USERPROFILE%\.ssh\firevm -N ""`
2. `python vm\make-seed.py %USERPROFILE%\.ssh\firevm.pub`
3. `powershell -ExecutionPolicy Bypass -File vm\create-vm.ps1`
   (with Hyper-V enabled the first boot is slow; with more than 1 CPU the kernel may hang)
4. Once `ssh -i %USERPROFILE%\.ssh\firevm -p 2222 fire@127.0.0.1` answers:
   ```bat
   scp -i %USERPROFILE%\.ssh\firevm -P 2222 -r amonet fire@127.0.0.1:
   ```
5. Inside the VM:
   ```bash
   cd ~/amonet && patch -p1 < amonet-noninteractive.patch   # optional
   cd modules && sudo python3 -u main.py
   ```
6. **Plug the cable back** into the powered-off tablet. The VM's USB filters catch the bootrom (0E8D:0003)
   automatically. The script runs: GPT → rpmb → preloader → tz → lk → payload → `Reboot`.
   It takes a few minutes; don't touch anything.

## 4. TWRP

The tablet reboots into the unlocked fastboot (also captured by the VM). Inside the VM:

```bash
cd ~/amonet
sudo fastboot flash recovery_x bin/twrp.img
sudo fastboot erase userdata
sudo fastboot flash MISC bin/boot-recovery.bin
sudo fastboot reboot
```

The tablet boots into TWRP (`adb devices -l` → `product:omni_douglas`, recovery).
You can now shut down the VM: Windows sees the tablet over adb again.

## 5. LineageOS 15.1 (Android 8.1)

After `erase userdata` the data partition has no file system. Create one, leaving 16 KB free at the end
(room for the encryption metadata):

```bash
adb shell cat /proc/partitions                # find mmcblk0p16 (userdata) and its size in KB
# 4 KB blocks = (size in KB / 4) - 4
adb shell mke2fs -F -t ext4 -b 4096 /dev/block/mmcblk0p16 <BLOCKS>
adb shell twrp mount data
adb shell mkdir -p /data/media/0
adb push lineage-15.1-...-douglas.zip /data/media/0/lineage.zip
adb shell twrp install /data/media/0/lineage.zip
adb shell twrp wipe cache
adb shell twrp wipe dalvik
adb reboot
```

The first boot takes a few minutes. Finish the setup wizard and re-enable *USB debugging*
(About tablet → tap Build number 7 times → Developer options).

> Note: there are **no** Android 9+ ROMs for douglas (the 16/17 builds are for the 2018 "karnak" model).
> The OTA update offered by the *Updater* app hasn't been tested with amonet: better ignore it.

## 6. FireReader

```bash
cd app && ./build.sh            # or download the APK from release/
adb install build/FireReader.apk
adb shell pm grant it.firereader.home android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_SECURE_SETTINGS
adb shell appops set it.firereader.home WRITE_SETTINGS allow
adb shell appops set it.firereader.home SYSTEM_ALERT_WINDOW allow
adb shell cmd package set-home-activity it.firereader.home/.Home
```

PDFs open in FireReader's built-in reader. For other formats, install a reader app: FireReader uses
**ReadEra** if present, otherwise any compatible app.

Recommended tweaks:

```bash
adb shell settings put global policy_control immersive.full=*    # hide system bars
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

Recommended on an e-reader: turn off the lock screen (on this ROM it's black and makes the tablet look
powered off) and ambient display:

```bash
adb shell locksettings set-disabled true
adb shell settings put secure doze_enabled 0
```

Books go in the `Books` folder of internal storage (or use *Comfort → Receive books over Wi-Fi*).

## If something goes wrong

- **Black screen, no response** after the brick: that's the expected state. The bootrom is always reachable:
  power off (hold the button 30 s), start the step 3 script, plug the cable back in.
- **`read fail` on Windows**: use the VM (step 3).
- **The VM doesn't see the tablet**: in VirtualBox, check the USB filters for 0E8D and 1949 and that the
  USB controller is xHCI.
- **Going back to Fire OS**: k4y0z's thread has `amonet-douglas-return-to-stock.zip`.
