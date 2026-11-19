# Guida completa: da Fire HD 8 (2017) a FireReader, partendo da Windows

<sub>🇬🇧 [English version](GUIDE.md)</sub>

**Indice:** [0. Cosa serve](#0-cosa-serve) · [1. Driver fastboot](#1-driver-fastboot-per-windows) ·
[2. Brick intenzionale](#2-brick-intenzionale-del-preloader-da-windows) · [3. Bootrom nella VM](#3-passaggio-bootrom-nella-vm-linux) ·
[4. TWRP](#4-twrp) · [5. LineageOS](#5-lineageos-151-android-81) · [6. FireReader](#6-firereader) ·
[Problemi](#se-qualcosa-va-storto)

Tempo indicativo: **1–2 ore** la prima volta, di cui buona parte in download.

> ⚠️ **Leggi tutto prima di iniziare.** Sbloccare il bootloader cancella tutti i dati del tablet
> e, se qualcosa va storto, può lasciarlo non avviabile (di solito recuperabile, ma con fatica).
> Lo fai a tuo rischio. Questa guida vale **solo** per il **Fire HD 8 di 7ª generazione (2017)**,
> nome in codice **douglas**, modello **KFDOWI**.

## 0. Cosa serve

- Fire HD 8 2017 (`adb shell getprop ro.product.device` deve rispondere `douglas`)
- PC Windows 10/11 con un cavo micro-USB affidabile
- [platform-tools](https://developer.android.com/tools/releases/platform-tools) (adb e fastboot)
- Windows SDK (per `makecat.exe` e `signtool.exe`, servono al driver fastboot)
- [VirtualBox 7](https://www.virtualbox.org/) (per il solo passaggio che su Windows non funziona)
- Python 3 su Windows con `pip install pycdlib` (per creare la configurazione della VM)
- I file di **amonet-douglas v1.2** e **brick-douglas** dal
  [thread di k4y0z su XDA](https://xdaforums.com/t/unlock-root-twrp-unbrick-fire-hd-8-2017-douglas.3962846/)
  (un riepilogo è anche in [ryanfortner/FireHD8-2017](https://github.com/ryanfortner/FireHD8-2017)).
  **Non** sono inclusi in questo repository.
- LineageOS 15.1 per douglas, dal
  [thread di Rortiz2 su XDA](https://xdaforums.com/t/rom-unlocked-douglas-giza-otas-lineage-15-1-02-jan-2022.4328503/)

Attiva sul tablet: *Impostazioni → Opzioni dispositivo → Info → tocca 7 volte il numero di serie*,
poi *Opzioni sviluppatore → Debug ADB*. Verifica con `adb devices`.

## 1. Driver fastboot per Windows

In modalità fastboot il Fire si presenta come `USB\VID_1949&PID_0260` e Windows non ha un driver:
`fastboot` resta su `< waiting for any device >`.

1. `adb reboot bootloader`
2. PowerShell **da amministratore**:
   ```powershell
   powershell -ExecutionPolicy Bypass -File windows\driver\install-driver.ps1
   ```
   Lo script firma un pacchetto driver WinUSB con un certificato locale e lo installa.
3. `fastboot devices` ora deve mostrare il seriale, `fastboot getvar all` deve dire `product: DOUGLAS`.

> Il certificato serve solo a firmare il driver. **Rimuovilo a lavoro finito** con
> `windows\driver\uninstall-driver.ps1`.

## 2. Brick intenzionale del preloader (da Windows)

Il firmware recente non è vulnerabile a mtk-su, quindi si usa il metodo *brick*: si danneggia
apposta l'header del preloader così il chip entra nella modalità di emergenza (bootrom).

```bat
fastboot -S 512M flash brick amonet\bin\brick-9820.img
```

`-S 512M` è **essenziale** su Windows: senza, il fastboot moderno spezza l'immagine in blocchi
"sparse" e l'exploit non parte. Sullo schermo del tablet deve comparire:

```
PWNED by k4y0z!!!  Bricking Preloader...
All good, now unplug the device and power off
```

Poi **stacca il cavo** e tieni premuto il tasto di accensione per ~30 secondi.
Da qui in poi lo schermo resta nero: è normale.

## 3. Passaggio bootrom (nella VM Linux)

Su Windows lo script `main.py` di amonet arriva a "all good" ma poi le letture eMMC falliscono
(`RuntimeError: read fail`): la porta seriale di Windows non dialoga col payload.
La soluzione è una piccola VM Ubuntu che si prende il tablet via USB.

1. Crea una chiave SSH: `ssh-keygen -t ed25519 -f %USERPROFILE%\.ssh\firevm -N ""`
2. `python vm\make-seed.py %USERPROFILE%\.ssh\firevm.pub`
3. `powershell -ExecutionPolicy Bypass -File vm\create-vm.ps1`
   (con Hyper-V attivo il primo avvio è lento; con più di 1 CPU il kernel può bloccarsi)
4. Quando `ssh -i %USERPROFILE%\.ssh\firevm -p 2222 fire@127.0.0.1` risponde:
   ```bat
   scp -i %USERPROFILE%\.ssh\firevm -P 2222 -r amonet fire@127.0.0.1:
   ```
5. Nella VM:
   ```bash
   cd ~/amonet && patch -p1 < amonet-noninteractive.patch   # opzionale
   cd modules && sudo python3 -u main.py
   ```
6. **Ricollega il cavo** al tablet spento. I filtri USB della VM agganciano il bootrom (0E8D:0003)
   automaticamente. Lo script fa: GPT → rpmb → preloader → tz → lk → payload → `Reboot`.
   Ci vogliono alcuni minuti; non toccare nulla.

## 4. TWRP

Il tablet riparte nel fastboot sbloccato (agganciato anche lui dalla VM). Nella VM:

```bash
cd ~/amonet
sudo fastboot flash recovery_x bin/twrp.img
sudo fastboot erase userdata
sudo fastboot flash MISC bin/boot-recovery.bin
sudo fastboot reboot
```

Il tablet si avvia in TWRP (`adb devices -l` → `product:omni_douglas`, recovery).
Da qui puoi spegnere la VM: Windows vede di nuovo il tablet in adb.

## 5. LineageOS 15.1 (Android 8.1)

Dopo `erase userdata` la partizione dati non ha un file system. Creane uno lasciando 16 KB
liberi in fondo (spazio per i metadati di cifratura):

```bash
adb shell cat /proc/partitions                # trova mmcblk0p16 (userdata) e la dimensione in KB
# blocchi da 4 KB = (dimensione in KB / 4) - 4
adb shell mke2fs -F -t ext4 -b 4096 /dev/block/mmcblk0p16 <BLOCCHI>
adb shell twrp mount data
adb shell mkdir -p /data/media/0
adb push lineage-15.1-...-douglas.zip /data/media/0/lineage.zip
adb shell twrp install /data/media/0/lineage.zip
adb shell twrp wipe cache
adb shell twrp wipe dalvik
adb reboot
```

Il primo avvio richiede qualche minuto. Completa la configurazione iniziale e riattiva il
*Debug USB* (Info sul tablet → 7 tocchi su Numero build → Opzioni sviluppatore).

> Nota: per douglas **non esistono** ROM con Android 9 o successivi (le build 16/17 sono per il
> modello 2018 "karnak"). L'aggiornamento OTA proposto dall'app *Aggiornamenti* non è stato
> testato con amonet: meglio ignorarlo.

## 6. FireReader

```bash
cd app && ./build.sh            # oppure scarica l'APK da release/
adb install build/FireReader.apk
adb shell pm grant it.firereader.home android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_SECURE_SETTINGS
adb shell appops set it.firereader.home WRITE_SETTINGS allow
adb shell appops set it.firereader.home SYSTEM_ALERT_WINDOW allow
adb shell cmd package set-home-activity it.firereader.home/.Home
```

Installa anche un lettore per aprire i file: FireReader usa **ReadEra** se presente,
altrimenti qualsiasi app compatibile.

Ritocchi consigliati:

```bash
adb shell settings put global policy_control immersive.full=*    # niente barre di sistema
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
adb shell settings put global animator_duration_scale 0.5
```

Consigliato su un e-reader: togliere la schermata di blocco (su questa ROM resta nera e sembra
che il tablet sia spento) e l'ambient display:

```bash
adb shell locksettings set-disabled true
adb shell settings put secure doze_enabled 0
```

I libri vanno nella cartella `Books` della memoria interna (oppure via *Comfort → Ricevi libri via
Wi-Fi*).

## Se qualcosa va storto

- **Schermo nero e nessuna reazione** dopo il brick: è lo stato previsto. Il bootrom è sempre
  raggiungibile: spegni (30 s sul tasto), avvia lo script del passo 3, ricollega il cavo.
- **`read fail` su Windows**: usa la VM (passo 3).
- **La VM non vede il tablet**: controlla in VirtualBox i filtri USB 0E8D e 1949 e che il
  controller USB sia xHCI.
- **Tornare a Fire OS**: nel thread di k4y0z c'è `amonet-douglas-return-to-stock.zip`.
