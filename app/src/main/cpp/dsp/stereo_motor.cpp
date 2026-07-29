#include "stereo_motor.h"

#include <cmath>

namespace hoernix {

StereoMotor::StereoMotor(float abtastrateHz)
        : links_(abtastrateHz), rechts_(abtastrateHz) {}

void StereoMotor::setzeEinstellung(int kanal, const KanalEinstellung& einstellung) {
    if (kanal != kLinks && kanal != kRechts) {
        return;
    }
    std::lock_guard<std::mutex> sperre(ausstehendSchutz_);
    ausstehend_[static_cast<size_t>(kanal)] = einstellung;
}

void StereoMotor::uebernehmeAusstehende() {
    std::unique_lock<std::mutex> sperre(ausstehendSchutz_, std::try_to_lock);
    if (!sperre.owns_lock()) {
        return;  // nächster Block übernimmt
    }
    if (ausstehend_[kLinks]) {
        links_.setzeEinstellung(*ausstehend_[kLinks]);
        ausstehend_[kLinks].reset();
    }
    if (ausstehend_[kRechts]) {
        rechts_.setzeEinstellung(*ausstehend_[kRechts]);
        ausstehend_[kRechts].reset();
    }
}

void StereoMotor::verarbeiteVerschraenkt(float* daten, int rahmen) {
    uebernehmeAusstehende();

    pufferLinks_.resize(static_cast<size_t>(rahmen));
    pufferRechts_.resize(static_cast<size_t>(rahmen));
    for (int i = 0; i < rahmen; ++i) {
        pufferLinks_[static_cast<size_t>(i)] = daten[2 * i];
        pufferRechts_[static_cast<size_t>(i)] = daten[2 * i + 1];
    }
    links_.verarbeite(pufferLinks_.data(), pufferLinks_.data(), rahmen);
    rechts_.verarbeite(pufferRechts_.data(), pufferRechts_.data(), rahmen);
    merkeSpitze(kLinks, pufferLinks_.data(), rahmen);
    merkeSpitze(kRechts, pufferRechts_.data(), rahmen);
    for (int i = 0; i < rahmen; ++i) {
        daten[2 * i] = pufferLinks_[static_cast<size_t>(i)];
        daten[2 * i + 1] = pufferRechts_[static_cast<size_t>(i)];
    }
}

bool StereoMotor::fehler() const {
    return links_.fehler() || rechts_.fehler();
}

bool StereoMotor::begrenzerEingriff() const {
    return links_.begrenzerEingriff() || rechts_.begrenzerEingriff();
}

int StereoMotor::latenz() const {
    return std::max(links_.latenz(), rechts_.latenz());
}

void StereoMotor::ruecksetzen() {
    links_.ruecksetzen();
    rechts_.ruecksetzen();
    spitze_[kLinks].store(0.0f, std::memory_order_relaxed);
    spitze_[kRechts].store(0.0f, std::memory_order_relaxed);
}

float StereoMotor::spitzenPegel(int kanal) {
    if (kanal != kLinks && kanal != kRechts) {
        return 0.0f;
    }
    return spitze_[static_cast<size_t>(kanal)].exchange(0.0f,
                                                        std::memory_order_relaxed);
}

void StereoMotor::merkeSpitze(int kanal, const float* daten, int rahmen) {
    float maximum = 0.0f;
    for (int i = 0; i < rahmen; ++i) {
        const float betrag = std::fabs(daten[i]);
        if (betrag > maximum) {
            maximum = betrag;
        }
    }
    auto& spitze = spitze_[static_cast<size_t>(kanal)];
    float bisher = spitze.load(std::memory_order_relaxed);
    while (maximum > bisher &&
           !spitze.compare_exchange_weak(bisher, maximum,
                                         std::memory_order_relaxed)) {
    }
}

}  // namespace hoernix
