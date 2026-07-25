plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "net.martinhenkel.hoernix"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.martinhenkel.hoernix"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        externalNativeBuild {
            cmake {
                // Oboe-Prefab ist gegen die geteilte C++-Standardbibliothek gebaut.
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildFeatures {
        compose = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.oboe)
}
