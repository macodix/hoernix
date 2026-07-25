#pragma once

#include <atomic>
#include <memory>
#include <vector>

#include <oboe/Oboe.h>

#include "../dsp/stereo_motor.h"

namespace hoernix {

// Mikrofon-Durchleitung (Plan Kap. 2.3): Gerätemikrofon (Mono) → StereoMotor
// → Kopfhörer (Stereo). Oboe/AAudio im Modus niedriger Latenz; die Ausgabe
// treibt den Takt (Callback), die Eingabe wird nicht-blockierend gelesen.
class MikrofonSchleife : public oboe::AudioStreamDataCallback,
                         public oboe::AudioStreamErrorCallback {
public:
    // mikGeraetId: AudioDeviceInfo-Id des Gerätemikrofons (nie Headset-Mikrofon —
    // Android würde sonst automatisch umrouten).
    explicit MikrofonSchleife(int32_t mikGeraetId);
    ~MikrofonSchleife() override;

    bool starte();
    void stoppe();

    StereoMotor* motor() { return motor_.get(); }
    bool fehler() const { return fehler_.load(); }

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* strom, void* daten,
                                          int32_t rahmen) override;
    void onErrorAfterClose(oboe::AudioStream* strom, oboe::Result ergebnis) override;

private:
    int32_t mikGeraetId_;
    std::shared_ptr<oboe::AudioStream> eingang_;
    std::shared_ptr<oboe::AudioStream> ausgang_;
    std::unique_ptr<StereoMotor> motor_;
    std::vector<float> mono_;
    std::atomic<bool> fehler_{false};
};

}  // namespace hoernix
