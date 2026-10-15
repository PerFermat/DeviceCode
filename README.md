# DeviceCode

Viele Geräte ohne richtige Tastatur melden sich über Google an, indem sie einen Code anzeigen
(z. B. `ABC-DEF-GHI`), den man unter <https://www.google.com/device> eingibt. Das machen etwa
YouTube auf Smart-TVs und Konsolen, Kodi-Add-ons wie das YouTube-Add-on und andere Apps mit
Google-Anmeldung.

DeviceCode liest diesen Code mit der Handykamera ab, kopiert ihn in die Zwischenablage und öffnet
die passende Seite in einem Chrome Custom Tab. Dort ist man schon mit dem Google-Konto aus Chrome
angemeldet und muss den Code nur noch bestätigen. Steht auf dem Bildschirm eine andere Adresse,
öffnet die App diese. Automatisch erkannt werden nur Codes im Format von Google (drei Gruppen à 3–4
Zeichen). Codes in anderen Formaten (z. B. `48271593` oder `AB12CD`) markiert man von Hand, siehe
unten.

## Ablauf

1. App starten: Die Kamera läuft sofort.
2. Handy auf den Fernseher richten. Wird derselbe Code in 3 Frames hintereinander erkannt,
   vibriert das Handy, der Code wird groß angezeigt und in die Zwischenablage kopiert.
3. Welche Seite sich öffnet:
   - Steht im Bild eine Adresse (z. B. `google.com/device`), wird genau diese geöffnet.
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
| `app/src/main/java/.../DeviceCodeParser.kt` | Erkennung von Google-Code und Adresse samt OCR-Korrekturen (0→O, 1→I, 5→S …), Vorschlag „sieht aus wie ein Code“ und die Prüfung „3 Frames gleich“ |
| `app/src/main/java/.../MainActivity.kt` | Compose-UI, Berechtigung, CameraX + ML Kit, Einfrieren und Markieren, Zwischenablage, Vibration, Custom Tab |
| `app/src/main/java/.../AppSettings.kt` | Einstellungen (Adressliste, Code eintragen) und Einstellungsseite |
| `gradle/libs.versions.toml` | Alle Abhängigkeits-Versionen |

Hinweis: Das Debug-APK ist rund 60 MB groß, weil das ML-Kit-Modell für alle CPU-Architekturen
mitgeliefert wird. Wer es kleiner möchte, kann in `app/build.gradle.kts` unter `defaultConfig`
`ndk { abiFilters += "arm64-v8a" }` eintragen. Das reicht für praktisch alle aktuellen Handys.

## Lizenz

MIT – siehe [LICENSE](LICENSE).
