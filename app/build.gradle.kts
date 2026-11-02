plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.breakingfree.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.breakingfree.app"
        minSdk = 26
        targetSdk = 35
        // CI passes -PbuildNumber so every release can be installed over the last one.
        val buildNumber = (project.findProperty("buildNumber") as String?)?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.$buildNumber"
    }

    // A fixed, committed key: Android only lets an update install over the old app (keeping its data)
    // when both are signed with the same key. Fine for a personal app; don't publish to a store with it.
    signingConfigs {
        create("fixed") {
            storeFile = file("breakingfree.keystore")
            storePassword = "breakingfree"
            keyAlias = "breakingfree"
            keyPassword = "breakingfree"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        debug {
            signingConfig = signingConfigs.getByName("fixed")
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
    }
    lint {
        // Personal sideloaded app: Play-policy lint checks (exact alarms etc.) shouldn't block releases.
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
