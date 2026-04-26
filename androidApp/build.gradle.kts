plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

val androidAppId: String by rootProject.extra
val appName: String by rootProject.extra
val backendUrl: String by rootProject.extra
val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra

android {
    namespace = "org.multipaz.wallet.android"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = androidAppId
        resValue("string", "app_name", appName)
        manifestPlaceholders["appName"] = appName
        manifestPlaceholders["applinkHost"] = backendUrl.removePrefix("https://").removePrefix("http://")
        minSdk = 29
        targetSdk = 36
        versionCode = projectVersionCode
        versionName = projectVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("devkey") {
            storeFile = file("devkey.keystore")
            storePassword = "devkey"
            keyAlias = "devkey-alias"
            keyPassword = "devkey"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("devkey")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("devkey")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    ksp(libs.multipaz.cbor.rpc)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.multipaz)
    implementation(libs.multipaz.doctypes)
    implementation(libs.multipaz.utopia)
    implementation(libs.multipaz.dcapi)
    implementation(libs.multipaz.compose)
    implementation(libs.multipaz.longfellow)
    implementation(libs.ktor.client.android)
    implementation(libs.androidx.credentials)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)
    implementation(libs.identity.googleid)
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor3)
    implementation(libs.lottie)
    implementation(libs.play.services.coroutines)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.gson)
    implementation(libs.accompanist.drawablepainter)
    implementation(libs.accompanist.permissions)
    implementation(libs.semver)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}