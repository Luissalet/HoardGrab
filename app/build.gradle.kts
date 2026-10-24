import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.luissalet.hoardgrab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.luissalet.hoardgrab"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    // One APK per ABI (each carries its own Python + ffmpeg): arm64 for phones, x86_64 for the emulator.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the local debug key so it installs over USB with no keystore setup.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The embedded Python and ffmpeg run from the extracted native library folder.
    packaging { jniLibs { useLegacyPackaging = true } }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
