plugins {
    // Versions come from the root buildscript classpath (no Plugin Portal needed)
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
}

android {
    namespace = "com.jiko.moneytracker"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jiko.moneytracker"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }

    // Signing credentials are intentionally NOT committed to this repo.
    // Supply your own keystore via gradle properties (e.g. in your local
    // ~/.gradle/gradle.properties or with -P flags at build time):
    //   signing.storeFile, signing.storePassword,
    //   signing.keyAlias, signing.keyPassword
    // See "Build & signing" in README.md.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file(
                findProperty("signing.storeFile") as String? ?: "release.keystore"
            )
            storePassword = findProperty("signing.storePassword") as String?
            keyAlias = findProperty("signing.keyAlias") as String? ?: "saldo"
            keyPassword = findProperty("signing.keyPassword") as String?
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            // Sandbox-only: lifecycle-common / annotation resolve here via their
            // POMs (see the maven-local note in settings.gradle.kts), so both arrive
            // as raw KMP jars whose .knm linkdata collides at merge time. The JVM
            // build doesn't need that metadata — drop it from the APK.
            excludes += "nonJvmMain/**"
            excludes += "commonMain/**"
            excludes += "META-INF/kotlin-project-structure-metadata.json"
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // ML Kit text recognition (bundled Latin model, fully on-device) — receipt scan OCR
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // WorkManager — automatic Drive backup scheduling (runs with the app closed).
    // Artifacts were fetched via curl into ~/maven-local: the sandbox egress proxy
    // blocks Gradle's own TLS to dl.google.com, same as ML Kit above.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
