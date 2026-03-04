plugins {
    alias(libs.plugins.android.application)
}

// Read API keys from .env file in project root
val envFile = rootProject.file(".env")
val geminiApiKey: String = if (envFile.exists()) {
    envFile.readLines()
        .firstOrNull { it.startsWith("GEMINI_API_KEY=") }
        ?.substringAfter("=")
        ?.trim()
        ?: ""
} else {
    ""
}
val hfApiKey: String = if (envFile.exists()) {
    envFile.readLines()
        .firstOrNull { it.startsWith("HF_API_KEY=") }
        ?.substringAfter("=")
        ?.trim()
        ?: ""
} else {
    ""
}

android {
    namespace = "com.example.boardgames"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.boardgames"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"")
        buildConfigField("String", "HF_API_KEY", "\"${hfApiKey}\"")
    }

    buildFeatures {
        buildConfig = true
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

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
            )
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.google.genai)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
