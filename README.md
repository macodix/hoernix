# HörNix

Android-App, die Ohr-/Kopfhörer bei Schwerhörigkeit nutzbar macht: je Ohr
getrennte Verstärkung/Absenkung einzelner Frequenzbereiche sowie
Frequenzverschiebung (Kompression und Transposition) für nicht mehr hörbare
Bereiche. Zwei Betriebsarten: Mikrofon-Durchleitung (Umgebung hören) und
eigener Player.

**Status:** Projektgerüst (Umsetzungsschritt 1) — noch keine Signalverarbeitung.

## Hinweis

HörNix ist eine Einstellhilfe, kein Medizinprodukt und kein Hörtest; die App
ersetzt keine HNO-Diagnostik. Das digitale Ausgangssignal ist fest auf
−1 dBFS gedeckelt; die tatsächliche Lautheit am Ohr hängt zusätzlich von
Systemlautstärke und Kopfhörer-Empfindlichkeit ab.

## Bauen

Voraussetzungen: JDK ≥ 17, Android-SDK (compileSdk 36, NDK, CMake).
Der SDK-Pfad wird über `local.properties` (`sdk.dir=…`, nicht versioniert)
bekannt gemacht.

```
./gradlew assembleDebug
```

Ergebnis: `app/build/outputs/apk/debug/app-debug.apk`, installierbar per
Sideload (`adb install`). Mindestversion Android 10 (API 29).

## Aufbau

- `app/src/main/kotlin/` — App-Code (Kotlin, Jetpack Compose)
- `app/src/main/cpp/` — DSP-Kern (C++, über CMake/NDK gebaut)
- `app/src/main/cpp/third_party/` — eingebettete Fremdquellen (KissFFT),
  Herkunft und Prüfsummen in `THIRD_PARTY.md`
