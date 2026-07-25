#pragma once

#include <array>

namespace hoernix {

// Audiometrie-Frequenzen der 11 EQ-Bänder (Plan Kap. 2.2).
inline constexpr int kAnzahlBaender = 11;
inline constexpr std::array<float, kAnzahlBaender> kBandFrequenzenHz = {
        125.0f, 250.0f, 500.0f, 750.0f, 1000.0f, 1500.0f,
        2000.0f, 3000.0f, 4000.0f, 6000.0f, 8000.0f};

inline constexpr float kBandVerstaerkungMinDb = -24.0f;
inline constexpr float kBandVerstaerkungMaxDb = 24.0f;

// Feste, nicht überschreitbare Deckelung des digitalen Ausgangs.
inline constexpr float kDeckelungDb = -1.0f;
// Einstellbare Ansprechschwelle des Begrenzers, immer unterhalb der Deckelung.
inline constexpr float kSchwelleMinDb = -24.0f;
inline constexpr float kSchwelleMaxDb = -6.0f;
inline constexpr float kSchwelleVoreinstellungDb = -12.0f;

inline constexpr float kBegrenzerLookAheadSekunden = 0.005f;
inline constexpr float kBegrenzerRueckstellSekunden = 0.1f;

// Überblendungsdauer bei sprunghaften Einstellungsänderungen (Plan Kap. 2.1).
inline constexpr float kGlaettungSekunden = 0.2f;

// FFT-Raster der Frequenzverschiebung (Plan Kap. 2.2).
inline constexpr int kFftLaenge = 512;
inline constexpr int kSprung = kFftLaenge / 4;  // 75 % Überlappung (COLA für Hann²)

struct VerschiebungsEinstellung {
    bool kompressionAktiv = false;
    float grenzFrequenzHz = 3000.0f;   // Kompression wirkt oberhalb
    float verhaeltnis = 2.0f;          // Kompressionsverhältnis >= 1
    bool transpositionAktiv = false;
    float quelleVonHz = 6000.0f;       // Quellbereich der Transposition
    float quelleBisHz = 10000.0f;
    float versatzHz = 3000.0f;         // Verschiebung nach unten
};

struct KanalEinstellung {
    std::array<float, kAnzahlBaender> bandVerstaerkungDb{};  // 0 = neutral
    VerschiebungsEinstellung verschiebung;
    float begrenzerSchwelleDb = kSchwelleVoreinstellungDb;
};

}  // namespace hoernix
