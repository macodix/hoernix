#include "mikrofon_schleife.h"

namespace hoernix {

MikrofonSchleife::MikrofonSchleife(int32_t mikGeraetId) : mikGeraetId_(mikGeraetId) {}

MikrofonSchleife::~MikrofonSchleife() {
    stoppe();
}

bool MikrofonSchleife::starte() {
    oboe::AudioStreamBuilder ausgabe;
    ausgabe.setDirection(oboe::Direction::Output)
            ->setChannelCount(2)
            ->setFormat(oboe::AudioFormat::Float)
            ->setSampleRate(48000)
            ->setSampleRateConversionQuality(
                    oboe::SampleRateConversionQuality::Medium)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setUsage(oboe::Usage::Media)
            ->setDataCallback(this)
            ->setErrorCallback(this);
    if (ausgabe.openStream(ausgang_) != oboe::Result::OK) {
        return false;
    }

    oboe::AudioStreamBuilder eingabe;
    eingabe.setDirection(oboe::Direction::Input)
            ->setChannelCount(1)
            ->setFormat(oboe::AudioFormat::Float)
            ->setSampleRate(ausgang_->getSampleRate())
            ->setSampleRateConversionQuality(
                    oboe::SampleRateConversionQuality::Medium)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            // Roh-Signal ohne Sprachverarbeitung des Systems; das feste
            // Gerätemikrofon verhindert das automatische Umrouten aufs
            // Headset-Mikrofon.
            ->setInputPreset(oboe::InputPreset::VoiceRecognition)
            ->setDeviceId(mikGeraetId_)
            ->setErrorCallback(this);
    if (eingabe.openStream(eingang_) != oboe::Result::OK) {
        ausgang_->close();
        ausgang_.reset();
        return false;
    }

    motor_ = std::make_unique<StereoMotor>(
            static_cast<float>(ausgang_->getSampleRate()));

    if (eingang_->requestStart() != oboe::Result::OK ||
        ausgang_->requestStart() != oboe::Result::OK) {
        stoppe();
        return false;
    }
    return true;
}

void MikrofonSchleife::stoppe() {
    if (ausgang_) {
        ausgang_->stop();
        ausgang_->close();
        ausgang_.reset();
    }
    if (eingang_) {
        eingang_->stop();
        eingang_->close();
        eingang_.reset();
    }
}

oboe::DataCallbackResult MikrofonSchleife::onAudioReady(oboe::AudioStream* /*strom*/,
                                                        void* daten,
                                                        int32_t rahmen) {
    auto* aus = static_cast<float*>(daten);
    mono_.resize(static_cast<size_t>(rahmen));

    int32_t gelesen = 0;
    if (eingang_) {
        auto ergebnis = eingang_->read(mono_.data(), rahmen, 0);
        if (ergebnis) {
            gelesen = ergebnis.value();
        }
    }
    for (int32_t i = 0; i < rahmen; ++i) {
        const float wert = i < gelesen ? mono_[static_cast<size_t>(i)] : 0.0f;
        aus[2 * i] = wert;
        aus[2 * i + 1] = wert;
    }
    if (motor_) {
        motor_->verarbeiteVerschraenkt(aus, rahmen);
    }
    return oboe::DataCallbackResult::Continue;
}

void MikrofonSchleife::onErrorAfterClose(oboe::AudioStream* /*strom*/,
                                         oboe::Result /*ergebnis*/) {
    // Stromabriss (z. B. Gerät getrennt): fail-closed, der Dienst fragt den
    // Zustand ab und beendet die Durchleitung.
    fehler_.store(true);
}

}  // namespace hoernix
