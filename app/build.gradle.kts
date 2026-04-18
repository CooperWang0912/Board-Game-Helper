plugins {
    alias(libs.plugins.android.application)
    checkstyle
    id("com.google.gms.google-services")
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

    lint {
        xmlReport = true
        htmlReport = true
        warningsAsErrors = false
        abortOnError = false
        lintConfig = rootProject.file("lint.xml")
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

checkstyle {
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    isIgnoreFailures = true
    isShowViolations = true
}

tasks.register<Checkstyle>("checkstyle") {
    description = "Run Checkstyle on Java source files"
    group = "verification"
    source("src/main/java")
    classpath = files()
    include("**/*.java")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.register("codeQuality") {
    description = "Run all code quality checks (Checkstyle + Android Lint)"
    group = "verification"
    dependsOn("checkstyle", "lint")
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.google.genai)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation(platform("com.google.firebase:firebase-bom:34.11.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-database")
}
