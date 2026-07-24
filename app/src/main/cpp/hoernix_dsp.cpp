#include <jni.h>

namespace {
constexpr const char* kDspVersion = "0.1.0";
}

extern "C" JNIEXPORT jstring JNICALL
Java_net_martinhenkel_hoernix_DspBruecke_version(JNIEnv* env, jobject /*empfaenger*/) {
    return env->NewStringUTF(kDspVersion);
}
