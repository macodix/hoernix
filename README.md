# HörNix

Android-App, die Ohr-/Kopfhörer bei Schwerhörigkeit nutzbar macht: je Ohr
getrennte Verstärkung/Absenkung einzelner Frequenzbereiche sowie
Frequenzverschiebung (Kompression und Transposition) für nicht mehr hörbare
Bereiche. Zwei Betriebsarten: Mikrofon-Durchleitung (Umgebung hören) und
eigener Player.

**Status:** funktionsfähig (Umsetzungsschritte 1–7): DSP-Kern, Player- und
Mikrofonpfad, Profilverwaltung, Oberfläche, Doku.

## Hinweis

HörNix ist eine Einstellhilfe, kein Medizinprodukt und kein Hörtest; die App
ersetzt keine HNO-Diagnostik. Das digitale Ausgangssignal ist fest auf
−1 dBFS gedeckelt; die tatsächliche Lautheit am Ohr hängt zusätzlich von
Systemlautstärke und Kopfhörer-Empfindlichkeit ab.

## Funktionen

- **Equalizer:** 11 Bänder auf den Audiometrie-Frequenzen (125–8000 Hz),
  −24 bis +24 dB, je Ohr getrennt oder gekoppelt (mit Richtungswahl).
- **Frequenzkompression:** staucht das Spektrum oberhalb einer einstellbaren
  Grenzfrequenz in den hörbaren Bereich (Verhältnis einstellbar).
- **Frequenztransposition:** mischt einen einstellbaren Quellbereich um einen
  festen Versatz nach unten verschoben bei; das Original bleibt hörbar.
- **Begrenzer:** nicht abschaltbar, Deckelung −1 dBFS, Ansprechschwelle
  −24 bis −6 dBFS einstellbar; bei ungültigen Werten wird die Ausgabe sofort
  stummgeschaltet (fail-closed) und der Fehler angezeigt.
- **Audiogramm-Maske:** Hörverlust je Frequenz eintragen; Vorbelegung der
  Bandverstärkungen nach der Halbverstärkungsregel (gedeckelt +24 dB).
- **Profile:** benennbar, je Ohr vollständig, Export/Import als JSON über den
  Systemdateidialog; Importe werden geprüft und geklemmt, nie teilweise
  übernommen.
- **Umgebung hören:** Gerätemikrofon → Verarbeitung → Kopfhörer als
  Vordergrund-Dienst. Start nur bei angeschlossenen Kopfhörern; der eingebaute
  Lautsprecher ist ausgeschlossen (Rückkopplung). Bei klassischem Bluetooth
  (A2DP) zeigt die App einen Latenzhinweis (0,1–0,3 s).
- **Player:** lokale Audiodateien, Sitzungs-Wiedergabeliste, gleiche
  Verarbeitungskette.

Die App arbeitet vollständig offline (keine Netzwerkberechtigung);
Mikrofondaten werden nur durchgeleitet, nie gespeichert oder übertragen.
Profile verlassen das Gerät nur über den Export.

## Bedienung (Kurzanleitung)

1. **Kopfhörer anschließen.** Für Gespräche über „Umgebung hören" möglichst
   USB-C- oder Kabel-Kopfhörer (Bluetooth verzögert hörbar).
2. **Reiter „Audiogramm":** Hörverlust je Frequenz und Ohr eintragen (Werte
   aus dem Audiogramm), „In Bandverstärkungen übernehmen". Ohne Audiogramm:
   direkt im Reiter „Equalizer" nach Gehör einstellen.
3. **Reiter „Equalizer":** Feinabstimmung je Ohr; „Gekoppelt" überträgt eine
   Seite auf beide Ohren.
4. **Reiter „Verschiebung":** nur nötig, wenn hohe Frequenzen trotz
   Verstärkung unhörbar bleiben. Erst Kompression probieren, Transposition
   als Alternative; Erklärtexte stehen in der Ansicht.
5. **Reiter „Start":** Betriebsart wählen — Player (Audiodateien öffnen)
   oder „Umgebung hören starten" (fragt die Mikrofon-Berechtigung ab).
   Hier sitzen auch Profil-Schnellwahl, Verarbeitung Ein/Aus, Pegelanzeige
   mit Begrenzer-Indikator, Ansprechschwelle und Gesamtlautstärke.
6. **Reiter „Profile":** Einstellungen als Profil sichern, wechseln,
   exportieren/importieren.

Leuchtet „Begrenzer greift ein" dauerhaft, Bandverstärkungen oder
Gesamtlautstärke verringern.

## Bauen

Voraussetzungen: JDK ≥ 17, Android-SDK (compileSdk 36, NDK, CMake).
Der SDK-Pfad wird über `local.properties` (`sdk.dir=…`, nicht versioniert)
bekannt gemacht.

```
./gradlew assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`, installierbar per
Sideload (`adb install`). Mindestversion Android 10 (API 29).

Tests: `./gradlew testDebugUnitTest` (Profilschicht, JVM) sowie
Host-Modultests des DSP-Kerns:

```
cmake -S app/src/main/cpp -B <bauverzeichnis>
cmake --build <bauverzeichnis>
<bauverzeichnis>/dsp_tests
```

## Aufbau

- `app/src/main/kotlin/` — App-Code (Kotlin, Jetpack Compose):
  `profil/` Profilverwaltung, `audio/` Player-/Mikrofonanbindung,
  `ui/` Ansichten
- `app/src/main/cpp/` — DSP-Kern (C++, über CMake/NDK gebaut):
  Frequenzverschiebung → EQ → Begrenzer, je Ohr eine Kette
- `app/src/main/cpp/third_party/` — eingebettete Fremdquellen (KissFFT),
  Herkunft und Prüfsummen in `THIRD_PARTY.md`
