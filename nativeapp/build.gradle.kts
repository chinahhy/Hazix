plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersionName = providers.gradleProperty("VERSION_NAME").orElse("3.3.9")
val releaseVersionCode = providers.gradleProperty("VERSION_CODE").map { it.toInt() }.orElse(17)
val releaseKeystorePath = providers.environmentVariable("HAZIX_RELEASE_KEYSTORE")
    .orElse("${System.getProperty("user.home")}/.android/debug.keystore")
// Optional LAN release mirror (the NAS). Empty by default, which keeps the
// updater on GitHub; pass -PhazixMirrorBase=http://192.168.1.10:8088 to bake a
// default address in. The updater still falls back to GitHub when it is down,
// and a value written into the update_mirror preferences overrides this.
val mirrorBaseUrl = providers.gradleProperty("hazixMirrorBase").orElse("")

android {
    namespace = "tv.hdao.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tv.hdao.app"
        minSdk = 24
        targetSdk = 35
        versionCode = releaseVersionCode.get()
        versionName = releaseVersionName.get()
        buildConfigField("String", "MIRROR_BASE_URL", "\"${mirrorBaseUrl.get()}\"")
    }

    signingConfigs {
        create("hazixRelease") {
            storeFile = file(releaseKeystorePath.get())
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
            // Keep the v1 (JAR) signature on top of v2. The in-app updater asks
            // PackageManager for the certificates of the downloaded APK, and the
            // oldest supported devices read those through the JAR signature; a
            // v2-only APK can leave that answer empty. Both schemes carry the same
            // key, so installing over an older build is unaffected.
            enableV1Signing = true
            enableV2Signing = true
        }
    }

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
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("hazixRelease")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }

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
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps")

    implementation("androidx.media3:media3-exoplayer:1.6.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.6.0")
    implementation("androidx.media3:media3-datasource-okhttp:1.6.0")
    implementation("androidx.media3:media3-ui:1.6.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
