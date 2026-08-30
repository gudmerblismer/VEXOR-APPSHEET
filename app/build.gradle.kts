plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.appsheetvexor"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.appsheetvexor"
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Permite generar un applicationId distinto por cliente (ej: com.example.appsheetvexor.cliente_nexo)
        // sin tocar el código base. Si no se define, se usa el applicationId normal.
        System.getenv("VEXOR_APP_ID_SUFFIX")?.let { suffix ->
            if (suffix.isNotBlank()) applicationIdSuffix = ".$suffix"
        }
    }

    signingConfigs {
        create("release") {
            val ksPath = System.getenv("VEXOR_KEYSTORE_PATH")
            if (ksPath != null) {
                storeFile = file(ksPath)
                storePassword = System.getenv("VEXOR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VEXOR_KEY_ALIAS")
                keyPassword = System.getenv("VEXOR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Si no hay variables de entorno de firma (build local sin CI), usa la firma debug
            // para que ./gradlew assembleRelease siga funcionando sin romperse.
            signingConfig = if (System.getenv("VEXOR_KEYSTORE_PATH") != null)
                signingConfigs.getByName("release")
            else
                signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("androidx.browser:browser:1.8.0")

    // LECTOR QR - ESTAS SON LAS 2 LINEAS QUE IMPORTAN
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}