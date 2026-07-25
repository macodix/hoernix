#pragma once

#include <vector>

#include "begrenzer.h"
#include "eq_bank.h"
#include "frequenz_verschiebung.h"
#include "parameter.h"

namespace hoernix {

// Verarbeitungskette eines Ohrs (Plan Kap. 2.2):
// Frequenzverschiebung → EQ → Begrenzer. Beide Ohren erhalten unabhängige
// Instanzen; die Koppel-Funktion ist Sache der Profilschicht.
class KanalKette {
public:
    explicit KanalKette(float abtastrateHz);

    void setzeEinstellung(const KanalEinstellung& einstellung);

    void verarbeite(const float* ein, float* aus, int anzahl);

    bool fehler() const { return begrenzer_.fehler(); }
    bool begrenzerEingriff() const { return begrenzer_.eingriff(); }
    int latenz() const { return verschiebung_.latenz() + begrenzer_.latenz(); }

    void ruecksetzen();

private:
    FrequenzVerschiebung verschiebung_;
    EqBank eq_;
    Begrenzer begrenzer_;
    std::vector<float> zwischen_;
};

}  // namespace hoernix
