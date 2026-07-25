#pragma once

#include <vector>

#include "kiss_fftr.h"
#include "parameter.h"

namespace hoernix {

// FFT-basierte Frequenzverschiebung (Plan Kap. 2.2): Kompression staucht das
// Spektrum oberhalb der Grenzfrequenz, Transposition mischt einen Quellbereich
// nach unten verschoben bei. Überlappung-Addition mit Hann-Fenster,
// Blocklänge 512, Sprung 128 (75 % Überlappung). Sind beide Verfahren aus,
// wird der FFT-Block vollständig umgangen (keine Zusatzlatenz).
class FrequenzVerschiebung {
public:
    explicit FrequenzVerschiebung(float abtastrateHz);
    ~FrequenzVerschiebung();

    FrequenzVerschiebung(const FrequenzVerschiebung&) = delete;
    FrequenzVerschiebung& operator=(const FrequenzVerschiebung&) = delete;

    void setzeEinstellung(const VerschiebungsEinstellung& einstellung);

    void verarbeite(const float* ein, float* aus, int anzahl);

    void ruecksetzen();

    // Zusatzlatenz in Abtastwerten (0 bei Umgehung).
    int latenz() const { return aktiv() ? kFftLaenge : 0; }

private:
    bool aktiv() const {
        return einstellung_.kompressionAktiv || einstellung_.transpositionAktiv;
    }
    void verarbeiteRahmen();

    float abtastrateHz_;
    VerschiebungsEinstellung einstellung_;

    kiss_fftr_cfg vor_;
    kiss_fftr_cfg zurueck_;

    std::vector<float> fenster_;         // Hann, Länge 512
    std::vector<float> eingang_;         // Analysepuffer, Länge 512
    int eingangGefuellt_ = 0;            // gültige Werte am Pufferende
    std::vector<float> ueberlappung_;    // OLA-Akkumulator, Länge 512
    std::vector<float> bereit_;          // fertige Ausgabewerte (FIFO)
    size_t bereitLesePos_ = 0;
    int anlauf_ = 0;                     // bereits ausgegebene Anlauf-Nullen

    std::vector<kiss_fft_scalar> zeit_;
    std::vector<kiss_fft_cpx> spektrum_;
    std::vector<kiss_fft_cpx> zielSpektrum_;
};

}  // namespace hoernix
