#include "frequenz_verschiebung.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace hoernix {
namespace {
constexpr int kBins = kFftLaenge / 2 + 1;
// Hann² bei 75 % Überlappung summiert konstant auf 3/2 → Normierung 2/3,
// zusätzlich 1/kFftLaenge für die unnormierte kissfft-Rücktransformation.
constexpr float kOlaNorm = (2.0f / 3.0f) / static_cast<float>(kFftLaenge);
}  // namespace

FrequenzVerschiebung::FrequenzVerschiebung(float abtastrateHz)
        : abtastrateHz_(abtastrateHz),
          vor_(kiss_fftr_alloc(kFftLaenge, 0, nullptr, nullptr)),
          zurueck_(kiss_fftr_alloc(kFftLaenge, 1, nullptr, nullptr)),
          fenster_(kFftLaenge),
          eingang_(kFftLaenge, 0.0f),
          ueberlappung_(kFftLaenge, 0.0f),
          zeit_(kFftLaenge),
          spektrum_(kBins),
          zielSpektrum_(kBins) {
    for (int i = 0; i < kFftLaenge; ++i) {
        fenster_[i] = 0.5f * (1.0f - std::cos(2.0f * static_cast<float>(M_PI) * i /
                                              kFftLaenge));
    }
}

FrequenzVerschiebung::~FrequenzVerschiebung() {
    kiss_fftr_free(vor_);
    kiss_fftr_free(zurueck_);
}

void FrequenzVerschiebung::setzeEinstellung(const VerschiebungsEinstellung& einstellung) {
    const bool vorherAktiv = aktiv();
    einstellung_ = einstellung;
    einstellung_.verhaeltnis = std::max(1.0f, einstellung_.verhaeltnis);
    einstellung_.versatzHz = std::max(0.0f, einstellung_.versatzHz);
    if (einstellung_.quelleBisHz < einstellung_.quelleVonHz) {
        std::swap(einstellung_.quelleVonHz, einstellung_.quelleBisHz);
    }
    if (aktiv() && !vorherAktiv) {
        ruecksetzen();
    }
}

void FrequenzVerschiebung::verarbeite(const float* ein, float* aus, int anzahl) {
    if (!aktiv()) {
        std::memmove(aus, ein, sizeof(float) * static_cast<size_t>(anzahl));
        return;
    }

    for (int i = 0; i < anzahl; ++i) {
        // Analysepuffer füllen; bei vollem Puffer einen Rahmen verarbeiten
        // und um einen Sprung weiterschieben.
        eingang_[static_cast<size_t>(kFftLaenge - kSprung + eingangGefuellt_)] = ein[i];
        ++eingangGefuellt_;
        if (eingangGefuellt_ == kSprung) {
            verarbeiteRahmen();
            std::memmove(eingang_.data(), eingang_.data() + kSprung,
                         sizeof(float) * static_cast<size_t>(kFftLaenge - kSprung));
            eingangGefuellt_ = 0;
        }

        // Ausgabe: erst Anlauf-Nullen (Systemlatenz), dann fertige Werte.
        if (anlauf_ < kFftLaenge) {
            aus[i] = 0.0f;
            ++anlauf_;
        } else if (bereitLesePos_ < bereit_.size()) {
            aus[i] = bereit_[bereitLesePos_++];
        } else {
            aus[i] = 0.0f;  // sollte nach dem Anlauf nicht eintreten
        }
    }

    // Gelesenes aus dem FIFO entfernen.
    if (bereitLesePos_ > 0) {
        bereit_.erase(bereit_.begin(),
                      bereit_.begin() + static_cast<long>(bereitLesePos_));
        bereitLesePos_ = 0;
    }
}

void FrequenzVerschiebung::verarbeiteRahmen() {
    for (int i = 0; i < kFftLaenge; ++i) {
        zeit_[i] = eingang_[i] * fenster_[i];
    }
    kiss_fftr(vor_, zeit_.data(), spektrum_.data());

    const float binHz = abtastrateHz_ / kFftLaenge;
    std::memset(zielSpektrum_.data(), 0, sizeof(kiss_fft_cpx) * kBins);

    if (einstellung_.kompressionAktiv) {
        const int k0 = std::clamp(
                static_cast<int>(std::lround(einstellung_.grenzFrequenzHz / binHz)), 0,
                kBins - 1);
        for (int k = 0; k <= k0; ++k) {
            zielSpektrum_[k] = spektrum_[k];
        }
        for (int k = k0 + 1; k < kBins; ++k) {
            const int t = k0 + static_cast<int>(std::lround((k - k0) /
                                                            einstellung_.verhaeltnis));
            if (t < kBins) {
                zielSpektrum_[t].r += spektrum_[k].r;
                zielSpektrum_[t].i += spektrum_[k].i;
            }
        }
    } else {
        std::memcpy(zielSpektrum_.data(), spektrum_.data(),
                    sizeof(kiss_fft_cpx) * kBins);
    }

    if (einstellung_.transpositionAktiv) {
        const int k1 = std::clamp(
                static_cast<int>(std::lround(einstellung_.quelleVonHz / binHz)), 0,
                kBins - 1);
        const int k2 = std::clamp(
                static_cast<int>(std::lround(einstellung_.quelleBisHz / binHz)), 0,
                kBins - 1);
        const int dk = static_cast<int>(std::lround(einstellung_.versatzHz / binHz));
        for (int k = k1; k <= k2; ++k) {
            const int t = k - dk;
            if (t >= 0 && t < kBins) {
                zielSpektrum_[t].r += spektrum_[k].r;
                zielSpektrum_[t].i += spektrum_[k].i;
            }
        }
    }

    kiss_fftri(zurueck_, zielSpektrum_.data(), zeit_.data());

    // Synthese-Fenster, Normierung, Überlappung-Addition.
    for (int i = 0; i < kFftLaenge; ++i) {
        ueberlappung_[i] += zeit_[i] * fenster_[i] * kOlaNorm;
    }
    bereit_.insert(bereit_.end(), ueberlappung_.begin(),
                   ueberlappung_.begin() + kSprung);
    std::memmove(ueberlappung_.data(), ueberlappung_.data() + kSprung,
                 sizeof(float) * static_cast<size_t>(kFftLaenge - kSprung));
    std::fill(ueberlappung_.end() - kSprung, ueberlappung_.end(), 0.0f);
}

void FrequenzVerschiebung::ruecksetzen() {
    std::fill(eingang_.begin(), eingang_.end(), 0.0f);
    std::fill(ueberlappung_.begin(), ueberlappung_.end(), 0.0f);
    bereit_.clear();
    bereitLesePos_ = 0;
    eingangGefuellt_ = 0;
    anlauf_ = 0;
}

}  // namespace hoernix
