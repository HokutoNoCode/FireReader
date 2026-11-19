# Contributing to FireReader

Thanks for wanting to help! Every contribution counts: code, testing on other devices, guide fixes,
translations, well-described bug reports. Issues and pull requests are welcome in **English or Italian**.

- [Reporting a bug or suggesting an idea](#reporting-a-bug-or-suggesting-an-idea)
- [Setting up](#setting-up)
- [Building and testing on a tablet](#building-and-testing-on-a-tablet)
- [Code rules](#code-rules)
- [Submitting a change (pull request)](#submitting-a-change-pull-request)
- [Where to start](#where-to-start)

## Reporting a bug or suggesting an idea

Open an [issue](https://github.com/HokutoNoCode/FireReader/issues/new/choose) and pick the matching form.
For bugs, always include: tablet model, ROM and version, FireReader version
(`adb shell dumpsys package it.firereader.home | grep versionName`), steps to reproduce and, if you can,
a screenshot (`adb exec-out screencap -p > screen.png`) and the log (`adb logcat -d | grep -i firereader`).

> **Privacy:** don't attach books, covers or screenshots showing copyrighted or personal content.
> The tablet's serial number and the "unlock code" from `fastboot getvar all` are personal data: strip them from logs.

## Setting up

You'll need:

- **JDK 11 or later** (`javac`, `keytool`)
- **bash, curl, unzip, zip** (on Windows: Git Bash or WSL)
- **adb** from the [platform-tools](https://developer.android.com/tools/releases/platform-tools)
- an Android 5.0+ device (it doesn't have to be a Fire: the app runs on any tablet)

No Android Studio, no Gradle.

```bash
git clone https://github.com/HokutoNoCode/FireReader.git
cd FireReader/app
./build.sh            # first run downloads the build-tools into app/.sdk/
```

The output is `app/build/FireReader.apk`.

## Building and testing on a tablet

```bash
cd app && ./build.sh
adb install -r build/FireReader.apk

# permissions (once per device)
adb shell pm grant it.firereader.home android.permission.READ_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_EXTERNAL_STORAGE
adb shell pm grant it.firereader.home android.permission.WRITE_SECURE_SETTINGS
adb shell appops set it.firereader.home WRITE_SETTINGS allow
adb shell appops set it.firereader.home SYSTEM_ALERT_WINDOW allow
adb shell cmd package set-home-activity it.firereader.home/.Home

# restart the app after a change
adb shell am force-stop it.firereader.home && adb shell input keyevent KEYCODE_HOME
```

Test books: use **public-domain** texts only (for example [Project Gutenberg](https://www.gutenberg.org),
[Standard Ebooks](https://standardebooks.org)). To test margin cropping you need a PDF laid out for print, with
wide margins.

> If you update the app with a build signed by a different key than the installed one, Android rejects the
> update: run `adb uninstall it.firereader.home` first.

## Code rules

- **No external libraries.** The app must stay small and buildable with `build.sh` alone.
- **UI built in code**, using the colors and fonts already defined in `Home.java`
  (`INK`, `GREY`, `PAPER`, `CHIP`, serif for headings and books, sans-serif for controls).
- **No anonymous classes that capture local variables.** With JDK 21, `javac` emits a `MethodParameters`
  attribute that crashes `d8`. Use **lambdas** or a `static` nested class with a field pointing to the Activity
  (see `Home.TW`, `Home.SB`, `Reader.Tap`, `Reader.Seek`).
- **Never block the UI thread:** page rendering, covers and scans go on a `Thread`, then `ui.post(...)` to update views.
- **UI text** is short and clear, written for readers, not programmers. It's currently Italian; new strings
  should come in both English and Italian once the translation lands.
- **Never commit** signing keys (`*.keystore`, `*.jks`), books or third-party files: `.gitignore` already
  excludes them. Release APKs go in `release/` only when a version is published.

To find your way around the code: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Submitting a change (pull request)

1. Fork the repo and create a branch with a clear name: `git checkout -b epub-reader`.
2. Keep changes small and focused. One pull request = one topic.
3. Make sure `./build.sh` ends with `OK` and test the app on a real device.
4. Update the docs if you change visible behavior (README, GUIDE, ARCHITECTURE) and add a line to
   [CHANGELOG.md](CHANGELOG.md) under *Unreleased*.
5. Write descriptive commit messages in the imperative, for example
   `Reader: skip blank pages when computing margins`.
6. Open the pull request and fill in the form: what changes, why, how you tested it, screenshots if it touches the UI.

## Where to start

Good first contributions (also look for issues labeled **good first issue**):

- **English interface**: move the strings in `Home.java` and `Reader.java` into a small table with English and
  Italian, chosen from the system language
- A dedicated app icon (it currently uses a system icon)
- Show the app version at the bottom of the Comfort tab
- Skip fully blank pages when computing margins
- A progress bar under each cover in the library

More ambitious:

- Built-in EPUB reader (pagination, font choice, line spacing)
- Calibre sync (*wireless device* protocol)
- Collections and shelves

Thank you! 📚
