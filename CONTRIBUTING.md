# Contribuire a FireReader

Grazie per voler dare una mano! Ogni contributo conta: codice, test su altri dispositivi, correzioni alla guida,
traduzioni, segnalazioni di bug ben descritte.

- [Segnalare un bug o proporre un'idea](#segnalare-un-bug-o-proporre-unidea)
- [Preparare l'ambiente](#preparare-lambiente)
- [Compilare e provare sul tablet](#compilare-e-provare-sul-tablet)
- [Regole del codice](#regole-del-codice)
- [Inviare una modifica (pull request)](#inviare-una-modifica-pull-request)
- [Da dove iniziare](#da-dove-iniziare)

## Segnalare un bug o proporre un'idea

Apri una [issue](https://github.com/HokutoNoCode/FireReader/issues/new/choose) e scegli il modulo adatto.
Per i bug indica sempre: modello del tablet, ROM e versione, versione di FireReader
(`adb shell dumpsys package it.firereader.home | grep versionName`), passi per riprodurre e, se puoi,
uno screenshot (`adb exec-out screencap -p > schermata.png`) e il log (`adb logcat -d | grep -i firereader`).

> **Privacy:** non allegare libri, copertine o screenshot con contenuti protetti o personali.
> Il seriale del tablet e il "codice di sblocco" di `fastboot getvar all` sono dati personali: cancellali dai log.

## Preparare l'ambiente

Ti servono:

- **JDK 11 o superiore** (`javac`, `keytool`)
- **bash, curl, unzip, zip** (su Windows: Git Bash o WSL)
- **adb** dai [platform-tools](https://developer.android.com/tools/releases/platform-tools)
- un dispositivo Android 5.0+ (non per forza un Fire: l'app gira su qualsiasi tablet)

Non servono Android Studio né Gradle.

```bash
git clone https://github.com/HokutoNoCode/FireReader.git
cd FireReader/app
./build.sh            # la prima volta scarica i build-tools in app/.sdk/
```

Il risultato è `app/build/FireReader.apk`.

## Compilare e provare sul tablet

```bash
cd app && ./build.sh
adb install -r build/FireReader.apk

# permessi (una volta sola per dispositivo)
adb shell pm grant it.firereader.home android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_SECURE_SETTINGS
adb shell appops set it.firereader.home WRITE_SETTINGS allow
adb shell appops set it.firereader.home SYSTEM_ALERT_WINDOW allow
adb shell cmd package set-home-activity it.firereader.home/.Home

# riavvia l'app dopo una modifica
adb shell am force-stop it.firereader.home && adb shell input keyevent KEYCODE_HOME
```

Libri di prova: usa solo testi di **pubblico dominio** (ad esempio [Liber Liber](https://www.liberliber.it),
[Project Gutenberg](https://www.gutenberg.org)). Per provare il ritaglio dei margini serve un PDF impaginato per la
stampa, con margini larghi.

> Se aggiorni l'app firmata con una chiave diversa da quella installata, Android rifiuta l'aggiornamento:
> prima `adb uninstall it.firereader.home`.

## Regole del codice

- **Niente librerie esterne.** L'app deve restare piccola e compilabile con il solo `build.sh`.
- **Interfaccia costruita in codice**, con i colori e i caratteri già definiti in `Home.java`
  (`INK`, `GREY`, `PAPER`, `CHIP`, serif per titoli e libri, sans-serif per i comandi).
- **Niente classi anonime che catturano variabili locali.** Con JDK 21 `javac` genera un attributo
  `MethodParameters` che manda in crash `d8`. Usa le **lambda** oppure una classe `static` annidata con un campo
  che punta all'Activity (vedi `Home.TW`, `Home.SB`, `Reader.Tap`, `Reader.Seek`).
- **Mai bloccare il thread dell'interfaccia:** rendering delle pagine, copertine e scansioni vanno in un `Thread`,
  poi `ui.post(...)` per aggiornare le viste.
- **Testi in italiano**, brevi e chiari, rivolti a chi legge, non a chi programma.
- **Non includere** chiavi di firma (`*.keystore`, `*.jks`), APK, libri o file di terze parti: `.gitignore` li esclude già.

Per capire dove mettere le mani: [docs/ARCHITETTURA.md](docs/ARCHITETTURA.md).

## Inviare una modifica (pull request)

1. Fai un fork e crea un branch con un nome chiaro: `git checkout -b lettore-epub`.
2. Fai modifiche piccole e mirate. Una pull request = un argomento.
3. Verifica che `./build.sh` termini con `OK` e prova l'app su un dispositivo vero.
4. Aggiorna la documentazione se cambi un comportamento visibile (README, GUIDA, ARCHITETTURA) e aggiungi
   una riga in [CHANGELOG.md](CHANGELOG.md) sotto *Non rilasciato*.
5. Scrivi messaggi di commit al presente e descrittivi, ad esempio
   `Lettore: salta le pagine bianche nel calcolo dei margini`.
6. Apri la pull request compilando il modulo: cosa cambia, perché, come l'hai provato, screenshot se tocca l'interfaccia.

## Da dove iniziare

Idee adatte a chi inizia (cerca anche le issue con etichetta **good first issue**):

- Icona dedicata per l'app (oggi usa un'icona di sistema)
- Traduzione dell'interfaccia in inglese (stringhe in `Home.java` e `Reader.java`)
- Mostrare la versione dell'app in fondo alla scheda Comfort
- Saltare le pagine completamente bianche nel calcolo dei margini
- Nella libreria, una barra di avanzamento sotto ogni copertina

Più impegnativi:

- Lettore EPUB integrato (impaginazione, scelta del carattere, interlinea)
- Sincronizzazione con Calibre (protocollo *wireless device*)
- Collezioni e scaffali

Grazie! 📚
