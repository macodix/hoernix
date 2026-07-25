#include "begrenzer.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace hoernix {

Begrenzer::Begrenzer(float abtastrateHz)
        : abtastrateHz_(abtastrateHz),
          lookAhead_(std::max(1, static_cast<int>(abtastrateHz *
                                                  kBegrenzerLookAheadSekunden))),
          schwelleLinear_(std::pow(10.0f, kSchwelleVoreinstellungDb / 20.0f)),
          deckelungLinear_(std::pow(10.0f, kDeckelungDb / 20.0f)),
          rueckstellKoeffizient_(
                  std::exp(-1.0f / (abtastrateHz * kBegrenzerRueckstellSekunden))),
          verzoegerung_(static_cast<size_t>(lookAhead_), 0.0f) {}

void Begrenzer::setzeSchwelle(float schwelleDb) {
    const float geklemmt = std::clamp(schwelleDb, kSchwelleMinDb, kSchwelleMaxDb);
    schwelleLinear_ = std::pow(10.0f, geklemmt / 20.0f);
}

void Begrenzer::verarbeite(const float* ein, float* aus, int anzahl) {
    if (fehler_) {
        std::memset(aus, 0, sizeof(float) * static_cast<size_t>(anzahl));
        return;
    }

    for (int i = 0; i < anzahl; ++i) {
        const float x = ein[i];
        if (!std::isfinite(x)) {
            fehler_ = true;
            std::memset(aus, 0, sizeof(float) * static_cast<size_t>(anzahl));
            return;
        }

        // Ältesten Wert entnehmen, neuen einreihen.
        const float verzoegert = verzoegerung_[static_cast<size_t>(schreibPos_)];
        verzoegerung_[static_cast<size_t>(schreibPos_)] = x;
        schreibPos_ = (schreibPos_ + 1) % lookAhead_;

        // Spitze im Look-ahead-Fenster (einschließlich des neuen Werts).
        float spitze = 0.0f;
        for (const float v : verzoegerung_) {
            spitze = std::max(spitze, std::fabs(v));
        }

        // Sofortiges Absenken auf die nötige Verstärkung, langsames Erholen.
        const float noetig =
                spitze > schwelleLinear_ ? schwelleLinear_ / spitze : 1.0f;
        if (noetig < verstaerkung_) {
            verstaerkung_ = noetig;
        } else {
            verstaerkung_ = noetig + (verstaerkung_ - noetig) * rueckstellKoeffizient_;
        }

        float y = verzoegert * verstaerkung_;
        // Harte Deckelung — unabhängig von Schwelle und Verstärkung.
        y = std::clamp(y, -deckelungLinear_, deckelungLinear_);
        if (!std::isfinite(y)) {
            fehler_ = true;
            std::memset(aus, 0, sizeof(float) * static_cast<size_t>(anzahl));
            return;
        }
        aus[i] = y;
    }
}

void Begrenzer::ruecksetzen() {
    std::fill(verzoegerung_.begin(), verzoegerung_.end(), 0.0f);
    schreibPos_ = 0;
    verstaerkung_ = 1.0f;
    fehler_ = false;
}

}  // namespace hoernix
