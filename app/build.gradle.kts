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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

    lint {
        // Errors fail the build; warnings are reported but do not. Turning
        // warnings into errors is worth doing once the first run has shown
        // what it actually finds.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
        // Unused resource ids in a game that draws everything itself are noise.
        disable += setOf("UnusedResources")
    }
}

dependencies {
    // The whole game is plain Kotlin plus android.graphics - no third-party
    // runtime dependencies at all. These are test-only and never ship.
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
