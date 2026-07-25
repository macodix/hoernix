#include "biquad.h"

#include <cmath>

namespace hoernix {

void Biquad::setzePeaking(float abtastrateHz, float mittenfrequenzHz, float guete,
                          float verstaerkungDb) {
    const float a = std::pow(10.0f, verstaerkungDb / 40.0f);
    const float w0 = 2.0f * static_cast<float>(M_PI) * mittenfrequenzHz / abtastrateHz;
    const float alpha = std::sin(w0) / (2.0f * guete);
    const float cosW0 = std::cos(w0);

    const float a0 = 1.0f + alpha / a;
    b0_ = (1.0f + alpha * a) / a0;
    b1_ = (-2.0f * cosW0) / a0;
    b2_ = (1.0f - alpha * a) / a0;
    a1_ = (-2.0f * cosW0) / a0;
    a2_ = (1.0f - alpha / a) / a0;
}

void Biquad::ruecksetzen() {
    z1_ = 0.0f;
    z2_ = 0.0f;
}

}  // namespace hoernix
