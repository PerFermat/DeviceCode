# DeviceCode

Fernseher, Konsolen und Streaming-Sticks lassen sich meist nicht per Tastatur anmelden. Stattdessen
zeigen sie einen Code und eine Adresse an – „Gehe zu netflix.com/tv8 und gib 48271593 ein“ oder
„google.com/device, Code ABC-DEF-GHI“. So machen es etwa Netflix, YouTube, Disney+, Prime Video
oder Kodi-Add-ons.

DeviceCode erspart das Abtippen: Handy auf den Bildschirm richten, die App liest Code und Adresse,
kopiert den Code in die Zwischenablage und öffnet die Seite im Browser. Dort ist man meist schon
angemeldet und muss den Code nur noch einfügen und bestätigen.

- **Automatisch** erkennt die App Codes im Format `ABC-DEF-GHI` (Google, YouTube, viele Kodi-Add-ons).
  Bei `google.com/device` ist der Code sogar schon eingetragen.
- **Alle anderen Codes** (z. B. `48271593` oder `AB12CD`): einmal aufs Kamerabild tippen und den
  Code markieren – die App schlägt den wahrscheinlichsten schon vor.

## Ablauf

1. App starten: Die Kamera läuft sofort. Aus größerer Entfernung mit zwei Fingern oder dem
   Zoom-Regler heranzoomen; der Zoom bleibt erhalten, wenn man aus dem Browser zurückkommt.
2. Handy auf den Fernseher richten. Wird ein Code im Format `ABC-DEF-GHI` in 3 Frames hintereinander
   gleich erkannt, vibriert das Handy, der Code wird groß angezeigt und in die Zwischenablage kopiert.
   Andere Formate markiert man von Hand (siehe [Andere Code-Formate](#andere-code-formate)).
3. Welche Seite sich öffnet:
   - Steht im Bild eine Adresse (z. B. `google.com/device`), wird sie geöffnet. Liest die Kamera sie
     über mehrere Bilder unterschiedlich, gilt die häufigste Lesart; ist sie bis auf ein, zwei Zeichen
     gleich einer Adresse aus den Einstellungen, wird diese genommen (`google.com/devlce` →
     `google.com/device`).
   - Sonst die Adresse aus den **Einstellungen** (Zahnrad oben rechts). Ab Werk ist das
     `https://www.google.com/device`.
   - Sind dort mehrere Adressen eingetragen, zeigt die App eine Auswahl.
4. Mit Chrome öffnet sich die Seite als Halbfenster, oben bleibt die Kamera sichtbar. Code
   bestätigen, dann oben auf die Kamera tippen (oder das Fenster nach unten wischen). Die Kamera
   läuft auch hinter dem Fenster weiter: Erscheint schon der nächste Code, wird das Fenster durch ein
   neues ersetzt. Codes, die schon verarbeitet wurden (die letzten 10), lösen nicht noch einmal aus.
5. Bei `google.com/device` hängt die App den Code als `?user_code=…` an, sodass das Eingabefeld
   schon ausgefüllt ist. Das lässt sich in den Einstellungen abschalten („Code automatisch
   eintragen“); dann bleibt nur die Zwischenablage.
6. **Erneut öffnen** kopiert den letzten Code noch einmal und öffnet die Seite, falls der Tab
   versehentlich geschlossen wurde. Wurde noch keine Adresse gewählt, erscheint die Auswahl erneut.

## Andere Code-Formate

1. Aufs Kamerabild tippen: Das Bild bleibt stehen, alle erkannten Wörter sind eingerahmt.
2. Wörter, die wie ein Code aussehen und nicht wie ein normales Wort, sind farbig hervorgehoben;
   das wahrscheinlichste ist schon ausgewählt.
3. Auf den Code tippen. Besteht er aus mehreren Teilen (`4827 1593`), alle nacheinander antippen.
   Im Textfeld lässt sich der Code noch korrigieren.
4. **Übernehmen**: Der Code wird wie ein automatisch erkannter behandelt. Die Adresse kommt aus dem
   Bild, sonst aus den Einstellungen. **Abbrechen** oder die Zurück-Geste führt zur Live-Kamera.

Solange das Bild eingefroren ist, ruht die automatische Erkennung. Ein von Hand übernommener Code
wird auch dann verwendet, wenn er schon einmal verarbeitet wurde.

## Einstellungen

Erreichbar über das Zahnrad oben rechts:

- **Adressen**: Liste der Seiten, die geöffnet werden, wenn im Bild keine Adresse zu lesen ist.
  `https://` ergänzt die App selbst. Mindestens eine Adresse bleibt immer stehen.
- **Code automatisch eintragen**: Hängt bei `google.com/device` den Code an die Adresse an
  (ab Werk an).

## Datenschutz

Die Texterkennung (ML Kit, lateinisches Skript) läuft komplett auf dem Gerät; Bilder verlassen das
Handy nicht. Die App fordert nur die Kamera- und Vibrationsberechtigung an, aber keinen
Internetzugriff. Die Webseite lädt der Browser.

## Voraussetzungen

Zum Benutzen: Android 8.0 oder neuer. Chrome wird empfohlen – nur damit erscheint die Seite als
Halbfenster und die Google-Anmeldung aus Chrome steht bereit; andere Browser zeigen sie im Vollbild.

Zum Bauen:

- JDK 17 oder neuer
- Android SDK mit Plattform 37 (`sdk.dir` in `local.properties` oder `ANDROID_HOME` setzen)

## Varianten

| Variante | Läuft auf | Größe (Release) |
| --- | --- | --- |
| `arm64` | Handys mit 64-Bit-ARM-Prozessor – praktisch alle der letzten Jahre | ca. 15 MB |
| `full` | allen Geräten, auch alten 32-Bit-Handys und dem x86-Emulator | ca. 45 MB |

Der Unterschied liegt nur in den mitgelieferten nativen Bibliotheken des ML-Kit-Modells.

## Bauen

```bash
./gradlew assembleRelease
```

Die APKs liegen danach unter `app/build/outputs/apk/arm64/release/app-arm64-release.apk` und
`app/build/outputs/apk/full/release/app-full-release.apk`. Debug-Varianten entsprechend mit
`./gradlew assembleDebug`.

Ohne eigenen Schlüssel wird das Release mit dem Debug-Schlüssel signiert. Für einen eigenen Schlüssel
eine Datei `key.properties` im Projektordner anlegen (wird nicht eingecheckt):

```properties
storeFile=release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

Unit-Tests für die Code-Erkennung:

```bash
./gradlew testArm64DebugUnitTest
```

## Installieren

USB-Debugging am Handy aktivieren, Handy anschließen und dann:

```bash
adb install -r app/build/outputs/apk/arm64/release/app-arm64-release.apk
```

## Aufbau

| Datei | Inhalt |
| --- | --- |
| `app/src/main/java/.../DeviceCodeParser.kt` | Erkennung von Google-Code und Adresse samt OCR-Korrekturen (0→O, 1→I, 5→S …), Vorschlag „sieht aus wie ein Code“ und die Prüfung „3 Frames gleich“ |
| `app/src/main/java/.../MainActivity.kt` | Compose-UI, Berechtigung, CameraX + ML Kit, Einfrieren und Markieren, Zwischenablage, Vibration, Custom Tab |
| `app/src/main/java/.../AppSettings.kt` | Einstellungen (Adressliste, Code eintragen) und Einstellungsseite |
| `gradle/libs.versions.toml` | Alle Abhängigkeits-Versionen |

## Lizenz

MIT – siehe [LICENSE](LICENSE).
