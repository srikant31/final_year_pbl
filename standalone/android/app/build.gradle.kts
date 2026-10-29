plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.concretevision.offline"
    compileSdk = 35
    buildToolsVersion = "35.0.0"
    defaultConfig {
        applicationId = "com.concretevision.offline"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.1-camera"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources { noCompress += "onnx" }
    buildTypes { release { isMinifyEnabled = false } }
    lint { abortOnError = true }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.24.3")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("junit:junit:4.13.2")
}

// Fail clearly when cloning without the separately distributed model asset.
val verifyModelAsset by tasks.registering {
    doLast {
        check(file("src/main/assets/flowx.onnx").isFile) {
            "Download the model first: python standalone/tools/download_assets.py --only model (from repository root)"
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyModelAsset) }
