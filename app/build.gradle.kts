import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val releaseSigningReady = listOf(
    "VOLLEY_KEYSTORE_PATH",
    "VOLLEY_KEYSTORE_PASSWORD",
    "VOLLEY_KEY_ALIAS",
    "VOLLEY_KEY_PASSWORD"
).all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.volley.manager"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    defaultConfig {
        applicationId = "com.volley.manager"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("versionCode").orElse("1").get().toInt()
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(System.getenv("VOLLEY_KEYSTORE_PATH")!!)
                storePassword = System.getenv("VOLLEY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VOLLEY_KEY_ALIAS")
                keyPassword = System.getenv("VOLLEY_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures { compose = true }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        managedDevices {
            devices {
                create<com.android.build.api.dsl.ManagedVirtualDevice>("pixel2Api35") {
                    device = "Pixel 2"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
