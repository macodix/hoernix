// Modultests des DSP-Kerns (Host-Build, ohne Fremd-Testrahmen).
// Nachweise laut Plan Kap. 1.3: Deckelung nie überschritten, fail-closed
// bei DSP-Fehler, EQ-/Verschiebungs-Wirkung.

#include <cmath>
#include <cstdio>
#include <vector>

#include "../dsp/begrenzer.h"
#include "../dsp/eq_bank.h"
#include "../dsp/frequenz_verschiebung.h"
#include "../dsp/kanal_kette.h"
#include "../dsp/stereo_motor.h"
#include "kiss_fftr.h"

namespace {

int fehlgeschlagen = 0;

#define PRUEFE(bedingung, meldung)                                     \
    do {                                                               \
        if (!(bedingung)) {                                            \
            std::printf("FEHLSCHLAG: %s (Zeile %d)\n", meldung, __LINE__); \
            ++fehlgeschlagen;                                          \
        } else {                                                       \
            std::printf("ok: %s\n", meldung);                          \
        }                                                              \
    } while (0)

constexpr float kFs = 48000.0f;

std::vector<float> sinus(float frequenzHz, float amplitude, int anzahl) {
    std::vector<float> v(static_cast<size_t>(anzahl));
    for (int i = 0; i < anzahl; ++i) {
        v[static_cast<size_t>(i)] = amplitude *
                std::sin(2.0f * static_cast<float>(M_PI) * frequenzHz * i / kFs);
    }
    return v;
}

float effektivwert(const std::vector<float>& v, size_t von) {
    double summe = 0.0;
    for (size_t i = von; i < v.size(); ++i) {
        summe += static_cast<double>(v[i]) * v[i];
    }
    return static_cast<float>(std::sqrt(summe / static_cast<double>(v.size() - von)));
}

float spitzenwert(const std::vector<float>& v) {
    float s = 0.0f;
    for (const float x : v) {
        s = std::max(s, std::fabs(x));
    }
    return s;
}

// Betragsspektrum der letzten 512 Werte (Hann-gefenstert).
std::vector<float> spektrum(const std::vector<float>& v) {
    const int n = 512;
    std::vector<kiss_fft_scalar> zeit(n);
    for (int i = 0; i < n; ++i) {
        const float fenster =
                0.5f * (1.0f - std::cos(2.0f * static_cast<float>(M_PI) * i / n));
        zeit[i] = v[v.size() - n + static_cast<size_t>(i)] * fenster;
    }
    kiss_fftr_cfg cfg = kiss_fftr_alloc(n, 0, nullptr, nullptr);
    std::vector<kiss_fft_cpx> bins(n / 2 + 1);
    kiss_fftr(cfg, zeit.data(), bins.data());
    kiss_fftr_free(cfg);
    std::vector<float> betrag(bins.size());
    for (size_t k = 0; k < bins.size(); ++k) {
        betrag[k] = std::sqrt(bins[k].r * bins[k].r + bins[k].i * bins[k].i);
    }
    return betrag;
}

float bandEnergie(const std::vector<float>& betrag, float vonHz, float bisHz) {
    const float binHz = kFs / 512.0f;
    float summe = 0.0f;
    for (size_t k = 0; k < betrag.size(); ++k) {
        const float f = static_cast<float>(k) * binHz;
        if (f >= vonHz && f <= bisHz) {
            summe += betrag[k];
        }
    }
    return summe;
}

float spitzenFrequenz(const std::vector<float>& betrag) {
    size_t bester = 0;
    for (size_t k = 1; k < betrag.size(); ++k) {
        if (betrag[k] > betrag[bester]) {
            bester = k;
        }
    }
    return static_cast<float>(bester) * kFs / 512.0f;
}

void testEqNeutral() {
    hoernix::EqBank eq(kFs);
    auto signal = sinus(1000.0f, 0.5f, 4800);
    auto kopie = signal;
    eq.verarbeite(signal.data(), static_cast<int>(signal.size()));
    float maxDelta = 0.0f;
    for (size_t i = 0; i < signal.size(); ++i) {
        maxDelta = std::max(maxDelta, std::fabs(signal[i] - kopie[i]));
    }
    PRUEFE(maxDelta < 1e-5f, "EQ neutral (0 dB) verändert das Signal nicht");
}

void testEqVerstaerkung() {
    hoernix::EqBank eq(kFs);
    eq.setzeVerstaerkung(4, 12.0f);  // 1000 Hz
    eq.ruecksetzen();                // Glättung überspringen: sofort Zielwert
    auto signal = sinus(1000.0f, 0.05f, 48000);
    const float rmsVorher = effektivwert(signal, 24000);
    eq.verarbeite(signal.data(), static_cast<int>(signal.size()));
    const float rmsNachher = effektivwert(signal, 24000);
    const float faktor = rmsNachher / rmsVorher;
    PRUEFE(faktor > 3.5f && faktor < 4.5f,
           "EQ-Band 1 kHz mit +12 dB verstärkt um etwa Faktor 4");
}

void testVerschiebungUmgehung() {
    hoernix::FrequenzVerschiebung fv(kFs);
    auto signal = sinus(440.0f, 0.3f, 1000);
    std::vector<float> aus(signal.size());
    fv.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    float maxDelta = 0.0f;
    for (size_t i = 0; i < signal.size(); ++i) {
        maxDelta = std::max(maxDelta, std::fabs(aus[i] - signal[i]));
    }
    PRUEFE(maxDelta == 0.0f, "Verschiebung aus: exakte Durchleitung ohne Latenz");
}

void testKompression() {
    hoernix::FrequenzVerschiebung fv(kFs);
    hoernix::VerschiebungsEinstellung e;
    e.kompressionAktiv = true;
    e.grenzFrequenzHz = 2000.0f;
    e.verhaeltnis = 2.0f;
    fv.setzeEinstellung(e);
    auto signal = sinus(6000.0f, 0.4f, 48000);
    std::vector<float> aus(signal.size());
    fv.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    // Erwartung: 2000 + (6000-2000)/2 = 4000 Hz
    const float f = spitzenFrequenz(spektrum(aus));
    PRUEFE(std::fabs(f - 4000.0f) < 200.0f,
           "Kompression: 6 kHz landet bei ca. 4 kHz (Grenze 2 kHz, Verhältnis 2)");
}

void testTransposition() {
    hoernix::FrequenzVerschiebung fv(kFs);
    hoernix::VerschiebungsEinstellung e;
    e.transpositionAktiv = true;
    e.quelleVonHz = 5000.0f;
    e.quelleBisHz = 7000.0f;
    e.versatzHz = 3000.0f;
    fv.setzeEinstellung(e);
    auto signal = sinus(6000.0f, 0.4f, 48000);
    std::vector<float> aus(signal.size());
    fv.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    const auto betrag = spektrum(aus);
    const float beigemischt = bandEnergie(betrag, 2800.0f, 3200.0f);
    const float original = bandEnergie(betrag, 5800.0f, 6200.0f);
    PRUEFE(beigemischt > 0.3f * original && original > 0.0f,
           "Transposition: 6 kHz wird bei 3 kHz beigemischt, Original bleibt");
}

void testBegrenzerSchwelle() {
    hoernix::Begrenzer b(kFs);
    b.setzeSchwelle(-12.0f);
    auto signal = sinus(1000.0f, 0.8f, 24000);
    std::vector<float> aus(signal.size());
    b.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    const float schwelle = std::pow(10.0f, -12.0f / 20.0f);
    PRUEFE(spitzenwert(aus) <= schwelle * 1.05f,
           "Begrenzer hält die Schwelle −12 dBFS ein");
    PRUEFE(b.eingriff(), "Begrenzer meldet Eingreifen (Pegelanzeige)");
}

void testDeckelung() {
    hoernix::Begrenzer b(kFs);
    b.setzeSchwelle(0.0f);  // wird auf −6 dBFS geklemmt
    auto signal = sinus(1000.0f, 2.0f, 24000);
    std::vector<float> aus(signal.size());
    b.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    const float deckelung = std::pow(10.0f, hoernix::kDeckelungDb / 20.0f);
    PRUEFE(spitzenwert(aus) <= deckelung + 1e-6f,
           "Feste Deckelung −1 dBFS wird nie überschritten");
}

void testFailClosed() {
    hoernix::KanalKette kette(kFs);
    hoernix::KanalEinstellung e;
    e.bandVerstaerkungDb.fill(6.0f);
    kette.setzeEinstellung(e);
    auto signal = sinus(1000.0f, 0.2f, 4800);
    signal[2400] = std::nanf("");
    std::vector<float> aus(signal.size());
    kette.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    PRUEFE(kette.fehler(), "Fail-closed: NaN im Signal setzt den Fehlerzustand");
    std::vector<float> weiter = sinus(1000.0f, 0.2f, 480);
    std::vector<float> aus2(weiter.size());
    kette.verarbeite(weiter.data(), aus2.data(), static_cast<int>(weiter.size()));
    PRUEFE(spitzenwert(aus2) == 0.0f,
           "Fail-closed: Ausgabe bleibt stumm bis zum Rücksetzen");
    kette.ruecksetzen();
    kette.verarbeite(weiter.data(), aus2.data(), static_cast<int>(weiter.size()));
    PRUEFE(!kette.fehler(), "Rücksetzen hebt den Fehlerzustand auf");
}

void testKetteGesamt() {
    hoernix::KanalKette kette(kFs);
    hoernix::KanalEinstellung e;
    e.bandVerstaerkungDb.fill(24.0f);
    e.verschiebung.kompressionAktiv = true;
    e.verschiebung.grenzFrequenzHz = 3000.0f;
    e.verschiebung.verhaeltnis = 2.0f;
    e.verschiebung.transpositionAktiv = true;
    kette.setzeEinstellung(e);
    // Deterministisches Rauschen (LCG), Vollaussteuerung.
    std::vector<float> signal(48000);
    unsigned int zustand = 12345;
    for (auto& x : signal) {
        zustand = zustand * 1664525u + 1013904223u;
        x = (static_cast<float>(zustand >> 8) / 8388608.0f) - 1.0f;
    }
    std::vector<float> aus(signal.size());
    kette.verarbeite(signal.data(), aus.data(), static_cast<int>(signal.size()));
    const float deckelung = std::pow(10.0f, hoernix::kDeckelungDb / 20.0f);
    bool endlich = true;
    for (const float x : aus) {
        if (!std::isfinite(x)) {
            endlich = false;
        }
    }
    PRUEFE(endlich, "Gesamtkette: Ausgabe bleibt endlich (Volllast, +24 dB überall)");
    PRUEFE(spitzenwert(aus) <= deckelung + 1e-6f,
           "Gesamtkette: Deckelung hält auch unter Volllast");
    PRUEFE(!kette.fehler(), "Gesamtkette: kein Fehlerzustand unter Volllast");
}

void testStereoMotor() {
    hoernix::StereoMotor motor(kFs);
    hoernix::KanalEinstellung rechts;
    rechts.bandVerstaerkungDb[4] = 12.0f;  // 1000 Hz nur rechts
    motor.setzeEinstellung(hoernix::StereoMotor::kRechts, rechts);

    const int rahmen = 48000;
    std::vector<float> verschraenkt(static_cast<size_t>(rahmen) * 2);
    for (int i = 0; i < rahmen; ++i) {
        const float wert = 0.04f *
                std::sin(2.0f * static_cast<float>(M_PI) * 1000.0f * i / kFs);
        verschraenkt[static_cast<size_t>(2 * i)] = wert;
        verschraenkt[static_cast<size_t>(2 * i) + 1] = wert;
    }
    motor.verarbeiteVerschraenkt(verschraenkt.data(), rahmen);

    std::vector<float> links(static_cast<size_t>(rahmen));
    std::vector<float> rechtsAus(static_cast<size_t>(rahmen));
    for (int i = 0; i < rahmen; ++i) {
        links[static_cast<size_t>(i)] = verschraenkt[static_cast<size_t>(2 * i)];
        rechtsAus[static_cast<size_t>(i)] =
                verschraenkt[static_cast<size_t>(2 * i) + 1];
    }
    const float rmsLinks = effektivwert(links, 24000);
    const float rmsRechts = effektivwert(rechtsAus, 24000);
    PRUEFE(rmsLinks > 0.026f && rmsLinks < 0.031f,
           "Stereo-Motor: linker Kanal bleibt neutral");
    PRUEFE(rmsRechts / rmsLinks > 3.0f,
           "Stereo-Motor: rechter Kanal getrennt verstärkt (Glättung wirksam)");
    PRUEFE(!motor.fehler(), "Stereo-Motor: kein Fehlerzustand");
}

void testSpitzenPegel() {
    hoernix::StereoMotor motor(kFs);
    const int rahmen = 4800;
    std::vector<float> verschraenkt(static_cast<size_t>(rahmen) * 2, 0.0f);
    for (int i = 0; i < rahmen; ++i) {
        // 0,1 ≙ −20 dBFS, deutlich unter der Begrenzer-Schwelle (−12 dBFS).
        verschraenkt[static_cast<size_t>(2 * i)] = 0.1f *
                std::sin(2.0f * static_cast<float>(M_PI) * 1000.0f * i / kFs);
    }
    motor.verarbeiteVerschraenkt(verschraenkt.data(), rahmen);

    const float links = motor.spitzenPegel(hoernix::StereoMotor::kLinks);
    const float rechts = motor.spitzenPegel(hoernix::StereoMotor::kRechts);
    PRUEFE(links > 0.09f && links < 0.11f,
           "Spitzenpegel: linker Kanal meldet die Signalspitze");
    PRUEFE(rechts < 1e-6f, "Spitzenpegel: stiller Kanal meldet 0");
    PRUEFE(motor.spitzenPegel(hoernix::StereoMotor::kLinks) == 0.0f,
           "Spitzenpegel: Abfrage setzt den Merker zurück");
}

}  // namespace

int main() {
    testEqNeutral();
    testEqVerstaerkung();
    testVerschiebungUmgehung();
    testKompression();
    testTransposition();
    testBegrenzerSchwelle();
    testDeckelung();
    testFailClosed();
    testKetteGesamt();
    testStereoMotor();
    testSpitzenPegel();
    if (fehlgeschlagen == 0) {
        std::printf("Alle Tests bestanden.\n");
    } else {
        std::printf("%d Test(s) fehlgeschlagen.\n", fehlgeschlagen);
    }
    return fehlgeschlagen == 0 ? 0 : 1;
}
