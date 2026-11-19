# FireReader (app)

Home screen per tablet Android usati come e-reader. Java puro, senza AndroidX né Gradle.

```bash
./build.sh                 # scarica build-tools/platform da dl.google.com e crea build/FireReader.apk
KEYSTORE=~/mia.keystore KEYSTORE_PASS=... ./build.sh   # per firmare con la tua chiave
```

Al primo build viene creata una chiave di firma locale (`firereader.keystore`, ignorata da git):
tienila, ti serve per aggiornare l'app senza disinstallarla.

**Nota per JDK 21+**: niente classi anonime che catturano variabili locali. javac genera attributi
`MethodParameters` senza nome che mandano in crash `d8`. Usa lambda o classi `static`.

Permessi da concedere via adb dopo l'installazione: vedi [docs/GUIDA.md](../docs/GUIDA.md#6-firereader).
