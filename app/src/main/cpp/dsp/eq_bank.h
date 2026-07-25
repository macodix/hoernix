#pragma once

#include <array>

#include "biquad.h"
#include "parameter.h"

namespace hoernix {

// 11 Peaking-Filter auf den Audiometrie-Frequenzen; Verstärkungsänderungen
// werden je Block geglättet (Überblendung statt Pegelsprung, Plan Kap. 2.1).
class EqBank {
public:
    explicit EqBank(float abtastrateHz);

    // Zielverstärkung eines Bands in dB; wird auf −24…+24 dB geklemmt.
    void setzeVerstaerkung(int band, float verstaerkungDb);

    // Verarbeitet einen Block in-place; führt vorher die Glättung einen Schritt fort.
    void verarbeite(float* puffer, int anzahl);

    void ruecksetzen();

private:
    void aktualisiereKoeffizienten();

    float abtastrateHz_;
    std::array<Biquad, kAnzahlBaender> filter_;
    std::array<float, kAnzahlBaender> zielDb_{};
    std::array<float, kAnzahlBaender> aktuellDb_{};
};

}  // namespace hoernix
