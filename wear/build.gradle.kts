plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.workersafety.watch"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.safetymonitor"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    // Optional shared debug key for both independently built projects; keep it outside Git.
    providers.gradleProperty("pairDebugKeystore").orNull?.let { keyPath ->
        signingConfigs.getByName("debug") {
            storeFile = file(keyPath)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(files("libs/samsung-health-sensor-api-1.4.1.aar")) // Copy official Samsung SDK into this folder
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    testImplementation("junit:junit:4.13.2")
}
