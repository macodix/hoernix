#pragma once

#include <vector>

#include "parameter.h"

namespace hoernix {

// Look-ahead-Limiter am Kettenende (Plan Kap. 2.2): feste, nicht
// überschreitbare Deckelung bei −1 dBFS; einstellbare Ansprechschwelle
// −24…−6 dBFS; Look-ahead 5 ms, Rückstellzeit 100 ms. Fail-closed: bei
// nicht-endlichen Werten wird die Ausgabe dauerhaft stummgeschaltet, bis
// ruecksetzen() gerufen wird.
class Begrenzer {
public:
    explicit Begrenzer(float abtastrateHz);

    // Schwelle in dBFS; wird auf −24…−6 geklemmt.
    void setzeSchwelle(float schwelleDb);

    void verarbeite(const float* ein, float* aus, int anzahl);

    // Fail-closed-Zustand (Stummschaltung aktiv).
    bool fehler() const { return fehler_; }

    // Greift der Begrenzer aktuell ein? (für die Pegelanzeige)
    bool eingriff() const { return verstaerkung_ < 0.999f; }

    int latenz() const { return lookAhead_; }

    void ruecksetzen();

private:
    float abtastrateHz_;
    int lookAhead_;
    float schwelleLinear_;
    float deckelungLinear_;
    float rueckstellKoeffizient_;

    std::vector<float> verzoegerung_;  // Ringpuffer, Länge lookAhead_
    int schreibPos_ = 0;
    float verstaerkung_ = 1.0f;
    bool fehler_ = false;
};

}  // namespace hoernix
