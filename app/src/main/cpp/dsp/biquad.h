#pragma once

namespace hoernix {

// Peaking-Filter zweiter Ordnung (RBJ-Audio-EQ-Kochbuch), Direktform II transponiert.
class Biquad {
public:
    void setzePeaking(float abtastrateHz, float mittenfrequenzHz, float guete,
                      float verstaerkungDb);
    void ruecksetzen();

    float verarbeite(float x) {
        const float y = b0_ * x + z1_;
        z1_ = b1_ * x - a1_ * y + z2_;
        z2_ = b2_ * x - a2_ * y;
        return y;
    }

private:
    float b0_ = 1.0f, b1_ = 0.0f, b2_ = 0.0f;
    float a1_ = 0.0f, a2_ = 0.0f;
    float z1_ = 0.0f, z2_ = 0.0f;
};

}  // namespace hoernix
