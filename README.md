# DeviceCode

Liest den Code, den das Kodi-YouTube-Add-on auf dem Fernseher anzeigt (z. B. `ABC-DEF-GHI`),
mit der Handykamera ab, kopiert ihn in die Zwischenablage und öffnet
<https://www.google.com/device> in einem Chrome Custom Tab. Dort ist man schon mit dem Google-Konto
aus Chrome angemeldet und muss den Code nur noch einfügen.

## Ablauf

1. App starten: Die Kamera läuft sofort.
2. Handy auf den Fernseher richten. Wird derselbe Code in 3 Frames hintereinander erkannt,
   vibriert das Handy, der Code wird groß angezeigt und in die Zwischenablage kopiert,
   und die Google-Seite öffnet sich.
3. Mit Chrome öffnet sich die Seite als Halbfenster, oben bleibt die Kamera sichtbar. Code einfügen
   und bestätigen, dann oben auf die Kamera tippen (oder das Fenster nach unten wischen). Die Kamera
   läuft auch hinter dem Fenster weiter: Erscheint schon der nächste Code, wird das Fenster durch ein
   neues ersetzt. Codes, die schon verarbeitet wurden (die letzten 10), lösen nicht noch einmal aus.
4. **Erneut öffnen** kopiert den letzten Code noch einmal und öffnet die Seite, falls der Tab
   versehentlich geschlossen wurde.

Die Texterkennung (ML Kit, lateinisches Skript) läuft komplett auf dem Gerät. Die App fordert nur
die Kamera- und Vibrationsberechtigung an, aber keinen Internetzugriff. Die Webseite lädt der Browser.

## Voraussetzungen

- JDK 17 oder neuer
- Android SDK mit Plattform 37 (`sdk.dir` in `local.properties` oder `ANDROID_HOME` setzen)

## Bauen

```bash
./gradlew assembleDebug
```

Das APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Unit-Tests für die Code-Erkennung:

```bash
./gradlew testDebugUnitTest
```

## Installieren

USB-Debugging am Handy aktivieren, Handy anschließen und dann:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Aufbau

| Datei | Inhalt |
| --- | --- |
| `app/src/main/java/.../DeviceCodeParser.kt` | Regex samt OCR-Korrekturen (0→O, 1→I, 5→S …) und die Prüfung „3 Frames gleich“ |
| `app/src/main/java/.../MainActivity.kt` | Compose-UI, Berechtigung, CameraX + ML Kit, Zwischenablage, Vibration, Custom Tab |
| `gradle/libs.versions.toml` | Alle Abhängigkeits-Versionen |

Hinweis: Das Debug-APK ist rund 60 MB groß, weil das ML-Kit-Modell für alle CPU-Architekturen
mitgeliefert wird. Wer es kleiner möchte, kann in `app/build.gradle.kts` unter `defaultConfig`
`ndk { abiFilters += "arm64-v8a" }` eintragen. Das reicht für praktisch alle aktuellen Handys.

## Lizenz

MIT – siehe [LICENSE](LICENSE).
