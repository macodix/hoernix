#include "eq_bank.h"

#include <algorithm>
#include <cmath>

namespace hoernix {
namespace {
// Feste Güte; bei halboktavigem Bandabstand ein Kompromiss aus Trennschärfe
// und Welligkeit des Summenfrequenzgangs.
constexpr float kGuete = 1.4f;
}  // namespace

EqBank::EqBank(float abtastrateHz) : abtastrateHz_(abtastrateHz) {
    aktualisiereKoeffizienten();
}

void EqBank::setzeVerstaerkung(int band, float verstaerkungDb) {
    if (band < 0 || band >= kAnzahlBaender) {
        return;
    }
    zielDb_[band] = std::clamp(verstaerkungDb, kBandVerstaerkungMinDb,
                               kBandVerstaerkungMaxDb);
}

void EqBank::verarbeite(float* puffer, int anzahl) {
    // Glättung: ein Schritt je Block Richtung Zielwert.
    const float alpha =
            1.0f - std::exp(-static_cast<float>(anzahl) /
                            (abtastrateHz_ * kGlaettungSekunden));
    bool geaendert = false;
    for (int b = 0; b < kAnzahlBaender; ++b) {
        const float delta = zielDb_[b] - aktuellDb_[b];
        if (std::fabs(delta) > 0.01f) {
            aktuellDb_[b] += alpha * delta;
            geaendert = true;
        } else if (aktuellDb_[b] != zielDb_[b]) {
            aktuellDb_[b] = zielDb_[b];
            geaendert = true;
        }
    }
    if (geaendert) {
        aktualisiereKoeffizienten();
    }

    for (int i = 0; i < anzahl; ++i) {
        float x = puffer[i];
        for (auto& f : filter_) {
            x = f.verarbeite(x);
        }
        puffer[i] = x;
    }
}

void EqBank::ruecksetzen() {
    for (auto& f : filter_) {
        f.ruecksetzen();
    }
    aktuellDb_ = zielDb_;
    aktualisiereKoeffizienten();
}

void EqBank::aktualisiereKoeffizienten() {
    for (int b = 0; b < kAnzahlBaender; ++b) {
        filter_[b].setzePeaking(abtastrateHz_, kBandFrequenzenHz[b], kGuete,
                                aktuellDb_[b]);
    }
}

}  // namespace hoernix
