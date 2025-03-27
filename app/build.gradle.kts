plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.carbofiber"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.carbofiber"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //CAMERAX
    // Core library for CameraX
    implementation (libs.androidx.camera.core)

    // CameraX Camera2 interop library
    implementation (libs.androidx.camera.camera2)

    // CameraX Lifecycle library
    implementation (libs.androidx.camera.lifecycle)

    // CameraX View library for PreviewView
    implementation (libs.androidx.camera.view)

    // CameraX Extensions (Optional: for HDR, Night Mode, etc.)
    implementation (libs.camera.extensions)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}