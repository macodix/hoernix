#include <jni.h>

#include <memory>
#include <mutex>

#include "audio/mikrofon_schleife.h"
#include "dsp/parameter.h"
#include "dsp/stereo_motor.h"

namespace {
constexpr const char* kDspVersion = "0.4.0";

hoernix::StereoMotor* motor(jlong griff) {
    return reinterpret_cast<hoernix::StereoMotor*>(griff);
}

// Eine Mikrofon-Durchleitung je App; Zugriff nur über die JNI-Funktionen.
std::mutex gSchleifeSchutz;
std::unique_ptr<hoernix::MikrofonSchleife> gSchleife;
}  // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_version(JNIEnv* env, jobject) {
    return env->NewStringUTF(kDspVersion);
}

JNIEXPORT jlong JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_erzeuge(JNIEnv*, jobject, jfloat abtastrate) {
    return reinterpret_cast<jlong>(new hoernix::StereoMotor(abtastrate));
}

JNIEXPORT void JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_gibFrei(JNIEnv*, jobject, jlong griff) {
    delete motor(griff);
}

JNIEXPORT void JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_verarbeiteVerschraenkt(JNIEnv* env, jobject,
                                                                jlong griff,
                                                                jfloatArray daten,
                                                                jint rahmen) {
    jfloat* zeiger = env->GetFloatArrayElements(daten, nullptr);
    if (zeiger == nullptr) {
        return;
    }
    motor(griff)->verarbeiteVerschraenkt(zeiger, rahmen);
    env->ReleaseFloatArrayElements(daten, zeiger, 0);
}

JNIEXPORT void JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_setzeEinstellung(
        JNIEnv* env, jobject, jlong griff, jint kanal, jfloatArray baenderDb,
        jboolean kompressionAktiv, jfloat grenzFrequenzHz, jfloat verhaeltnis,
        jboolean transpositionAktiv, jfloat quelleVonHz, jfloat quelleBisHz,
        jfloat versatzHz, jfloat schwelleDb) {
    hoernix::KanalEinstellung e;
    if (env->GetArrayLength(baenderDb) == hoernix::kAnzahlBaender) {
        env->GetFloatArrayRegion(baenderDb, 0, hoernix::kAnzahlBaender,
                                 e.bandVerstaerkungDb.data());
    }
    e.verschiebung.kompressionAktiv = kompressionAktiv == JNI_TRUE;
    e.verschiebung.grenzFrequenzHz = grenzFrequenzHz;
    e.verschiebung.verhaeltnis = verhaeltnis;
    e.verschiebung.transpositionAktiv = transpositionAktiv == JNI_TRUE;
    e.verschiebung.quelleVonHz = quelleVonHz;
    e.verschiebung.quelleBisHz = quelleBisHz;
    e.verschiebung.versatzHz = versatzHz;
    e.begrenzerSchwelleDb = schwelleDb;
    motor(griff)->setzeEinstellung(kanal, e);
}

JNIEXPORT jboolean JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_fehler(JNIEnv*, jobject, jlong griff) {
    return motor(griff)->fehler() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_begrenzerEingriff(JNIEnv*, jobject,
                                                           jlong griff) {
    return motor(griff)->begrenzerEingriff() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_ruecksetzen(JNIEnv*, jobject, jlong griff) {
    motor(griff)->ruecksetzen();
}

JNIEXPORT jfloat JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_spitzenPegel(JNIEnv*, jobject,
                                                      jlong griff, jint kanal) {
    return motor(griff)->spitzenPegel(kanal);
}

// Mikrofon-Durchleitung: Rückgabe ist der Motor-Griff für die
// Einstellungs-Aufrufe (0 = Start fehlgeschlagen). Der Griff gehört der
// Schleife und darf nicht über gibFrei freigegeben werden.
JNIEXPORT jlong JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_mikStarte(JNIEnv*, jobject,
                                                   jint mikGeraetId) {
    std::lock_guard<std::mutex> sperre(gSchleifeSchutz);
    if (gSchleife) {
        return 0;
    }
    auto schleife = std::make_unique<hoernix::MikrofonSchleife>(mikGeraetId);
    if (!schleife->starte()) {
        return 0;
    }
    gSchleife = std::move(schleife);
    return reinterpret_cast<jlong>(gSchleife->motor());
}

JNIEXPORT void JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_mikStoppe(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> sperre(gSchleifeSchutz);
    gSchleife.reset();
}

JNIEXPORT jboolean JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_mikFehler(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> sperre(gSchleifeSchutz);
    return (gSchleife && gSchleife->fehler()) ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"
