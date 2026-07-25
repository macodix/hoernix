#pragma once

#include <array>
#include <mutex>
#include <optional>
#include <vector>

#include "kanal_kette.h"
#include "parameter.h"

namespace hoernix {

// Beide Ohren: unabhängige Kanalketten hinter einer Schnittstelle für
// verschränktes (interleaved) Stereo-Audio. Einstellungsänderungen werden
// zwischengelagert und vom Audio-Thread an der Blockgrenze übernommen —
// der Audio-Thread blockiert nie (try_lock; bei Kollision nächster Block).
class StereoMotor {
public:
    static constexpr int kLinks = 0;
    static constexpr int kRechts = 1;

    explicit StereoMotor(float abtastrateHz);

    void setzeEinstellung(int kanal, const KanalEinstellung& einstellung);

    // daten: verschränkt L R L R …, rahmen Paare.
    void verarbeiteVerschraenkt(float* daten, int rahmen);

    bool fehler() const;
    bool begrenzerEingriff() const;
    int latenz() const;
    void ruecksetzen();

private:
    void uebernehmeAusstehende();

    KanalKette links_;
    KanalKette rechts_;
    std::mutex ausstehendSchutz_;
    std::array<std::optional<KanalEinstellung>, 2> ausstehend_;
    std::vector<float> pufferLinks_;
    std::vector<float> pufferRechts_;
};

}  // namespace hoernix
