import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "sk.kajka.liekovka"
    compileSdk = 36

    defaultConfig {
        applicationId = "sk.kajka.liekovka"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Prevádzkovateľ a kontakt: zobrazujú sa v aplikácii, v zásadách ochrany súkromia a v podmienkach používania.
        buildConfigField("String", "PREVADZKOVATEL", "\"Lukáš Babinec\"")
        buildConfigField("String", "KONTAKT_EMAIL", "\"kajkaforkids@gmail.com\"")
        // Adresa, kde GitHub Pages zverejňuje dáta (lieky.json.gz a meta.json).
        buildConfigField("String", "DATA_URL", "\"https://dextroza22.github.io/liekovka/\"")
        // Liekovka Plus (jednorazový nákup cez Google Play). false = všetko zadarmo, nákup sa vôbec neponúka.
        buildConfigField("boolean", "PLUS_AKTIVNE", "true")
    }

    // Podpisový kľúč na nahratie do Google Play: súbor keystore.properties (nie je v GitHube, pozri README).
    val podpis = rootProject.file("keystore.properties")
    signingConfigs {
        if (podpis.exists()) create("release") {
            val p = Properties().apply { podpis.inputStream().use { load(it) } }
            storeFile = rootProject.file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            if (podpis.exists()) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    // exportované schémy databázy pre test migrácií
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
    androidResources {
        noCompress += listOf("gz", "dat")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("com.android.billingclient:billing:8.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")   // skutočné org.json pre unit testy (v android.jar sú len prázdne stuby)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
