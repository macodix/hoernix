#include "kanal_kette.h"

namespace hoernix {

KanalKette::KanalKette(float abtastrateHz)
        : verschiebung_(abtastrateHz), eq_(abtastrateHz), begrenzer_(abtastrateHz) {}

void KanalKette::setzeEinstellung(const KanalEinstellung& einstellung) {
    verschiebung_.setzeEinstellung(einstellung.verschiebung);
    for (int b = 0; b < kAnzahlBaender; ++b) {
        eq_.setzeVerstaerkung(b, einstellung.bandVerstaerkungDb[b]);
    }
    begrenzer_.setzeSchwelle(einstellung.begrenzerSchwelleDb);
}

void KanalKette::verarbeite(const float* ein, float* aus, int anzahl) {
    zwischen_.resize(static_cast<size_t>(anzahl));
    verschiebung_.verarbeite(ein, zwischen_.data(), anzahl);
    eq_.verarbeite(zwischen_.data(), anzahl);
    begrenzer_.verarbeite(zwischen_.data(), aus, anzahl);
}

void KanalKette::ruecksetzen() {
    verschiebung_.ruecksetzen();
    eq_.ruecksetzen();
    begrenzer_.ruecksetzen();
}

}  // namespace hoernix
