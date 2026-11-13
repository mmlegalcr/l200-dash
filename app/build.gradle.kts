plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma permanente: el workflow escribe el keystore desde los secretos de GitHub.
val ksFile = System.getenv("KEYSTORE_FILE")?.let { file(it) }
val mapsKey: String = System.getenv("MAPS_API_KEY") ?: ""

android {
    namespace = "cr.andres.l200dash"
    compileSdk = 34

    defaultConfig {
        // Mismo paquete que la app de la dashcam: Android solo deja usar la carpeta de
        // comandos de la cámara (Android/data/com.fvision.camera) a la app con ese nombre.
        applicationId = "com.fvision.camera"
        minSdk = 26
        targetSdk = 29
        versionCode = 300
        versionName = "0.3"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("boolean", "HAS_MAPS_KEY", if (mapsKey.isNotBlank()) "true" else "false")

        // La librería de la dashcam solo existe en 32 bits.
        ndk {
            abiFilters += "armeabi-v7a"
        }
    }

    signingConfigs {
        create("perm") {
            if (ksFile != null && ksFile.exists()) {
                storeFile = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksFile != null && ksFile.exists()) signingConfigs.getByName("perm") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
}
