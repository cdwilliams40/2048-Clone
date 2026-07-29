plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.barnyardblitz"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.barnyardblitz"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "3.0.0"
    }

    buildTypes {
        release {
            // The engine is reflection-free, but nothing here needs shrinking.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // The whole game is plain Kotlin plus android.graphics - no third-party
    // runtime dependencies at all.
    testImplementation("junit:junit:4.13.2")
}
