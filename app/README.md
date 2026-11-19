# FireReader (app)

Home screen and PDF reader for Android tablets used as e-readers. Plain Java, no AndroidX, no Gradle.
How every file works: [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

```bash
./build.sh                 # downloads build-tools/platform from dl.google.com and creates build/FireReader.apk
KEYSTORE=~/my.keystore KEYSTORE_PASS=... ./build.sh   # sign with your own key
```

The first build creates a local signing key (`firereader.keystore`, ignored by git):
keep it, you need it to update the app without uninstalling.

**Note for JDK 21+**: no anonymous classes that capture local variables. javac emits unnamed
`MethodParameters` attributes that crash `d8`. Use lambdas or `static` classes.

Permissions to grant via adb after installing: see [docs/GUIDE.md](../docs/GUIDE.md#6-firereader).
