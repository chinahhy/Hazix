plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The TV app and the phone app ship as one release. They share a version number
// and a signing key so that both can be installed over an older build, and so
// the TV in-app updater keeps a working signature chain. The release workflow
// passes -PVERSION_NAME/-VERSION_CODE derived from the release tag.
val releaseVersionName = providers.gradleProperty("VERSION_NAME").orElse("3.3.9")
val releaseVersionCode = providers.gradleProperty("VERSION_CODE").map { it.toInt() }.orElse(17)
val releaseKeystorePath = providers.environmentVariable("HAZIX_RELEASE_KEYSTORE")
    .orElse("${System.getProperty("user.home")}/.android/debug.keystore")

android {
    namespace = "tv.hdao.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.hdao.mobile"
        minSdk = 24
        targetSdk = 35
        versionCode = releaseVersionCode.get()
        versionName = releaseVersionName.get()
    }

    signingConfigs {
        // Deliberately the same key as :nativeapp. The previous configuration
        // reused AGP's debug signing config, whose keystore silently moves with
        // ANDROID_USER_HOME, so a cloud build could produce an APK that cannot
        // be installed over an already released one.
        create("hazixRelease") {
            storeFile = file(releaseKeystorePath.get())
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    sourceSets["main"].java.srcDir("../nativeapp/src/main/java/tv/hdao/app/data")

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("hazixRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps")

    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
