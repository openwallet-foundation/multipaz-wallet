import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.android.lint)
    alias(libs.plugins.ksp)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.skie)
}

val appName: String by rootProject.extra
val backendUrl: String by rootProject.extra
val backendClientId: String  by rootProject.extra
val backendSecret: String  by rootProject.extra
val developerModeAvailable: Boolean by rootProject.extra
val projectVersionCode: Int by rootProject.extra
val projectVersionName: String by rootProject.extra
val updateUrl: String by rootProject.extra
val updateWebsite: String by rootProject.extra

buildConfig {
    packageName("org.multipaz.wallet.shared")
    buildConfigField("APP_NAME", appName)
    buildConfigField("VERSION", projectVersionName)
    buildConfigField("BACKEND_URL", backendUrl)
    buildConfigField("BACKEND_CLIENT_ID", backendClientId)
    buildConfigField("BACKEND_SECRET", backendSecret)
    buildConfigField("DEVELOPER_MODE_AVAILABLE", developerModeAvailable)
    buildConfigField("UPDATE_URL", updateUrl)
    buildConfigField("UPDATE_WEBSITE", updateWebsite)
    useKotlinOutput { internalVisibility = false }
}

kotlin {
    jvm()

    // Target declarations - add or remove as needed below. These define
    // which platforms this KMP module supports.
    // See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
    androidLibrary {
        namespace = "org.multipaz.wallet.shared"
        compileSdk = 36
        minSdk = 29

        withHostTestBuilder {
        }

        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // For iOS targets, this is also where you should
    // configure native binary output. For more information, see:
    // https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

    // A step-by-step guide on how to include this library in an XCode
    // project can be found here:
    // https://developer.android.com/kotlin/multiplatform/migrate

    // Since we're bundling Multipaz and adding a tiny amount of code from this directory, call it
    // "Multipaz" it's a lot more intuitive to see "import Multipaz" than "import Shared"
    //
    val xcfName = "Multipaz"
    val xcf = XCFramework()
    val iosTargets = listOf(iosX64(), iosArm64(), iosSimulatorArm64())
    iosTargets.forEach {
        it.binaries.framework {
            export(libs.multipaz)
            export(libs.multipaz.dcapi)
            export(libs.multipaz.doctypes)
            export(libs.multipaz.longfellow)
            export(libs.multipaz.swiftui)
            export(libs.kotlinx.io.bytestring)
            export(libs.kotlinx.io.core)
            export(libs.kotlinx.datetime)
            export(libs.kotlinx.coroutines.core)
            export(libs.kotlinx.serialization.json)
            export(libs.ktor.client.core)
            export(libs.ktor.client.darwin)
            baseName = xcfName
            binaryOption("bundleId", "org.multipaz.wallet.shared")
            binaryOption("bundleVersion", projectVersionCode.toString())
            binaryOption("bundleShortVersionString", projectVersionName)
            freeCompilerArgs += listOf(
                // This is how we specify the minimum iOS version as 26.0
                "-Xoverride-konan-properties=" +
                        "osVersionMin.ios_arm64=26.0;" +
                        "osVersionMin.ios_simulator_arm64=26.0;" +
                        "osVersionMin.ios_x64=26.0",
                // Uncomment the following to get Garbage Collection logging when using the framework:
                //
                // "-Xruntime-logs=gc=info"
            )
            linkerOpts(
                "-lsqlite3",
                "-Wl,-rpath,/usr/lib/swift"
            )
            xcf.add(this)
        }
    }

    // Source set declarations.
    // Declaring a target automatically creates a source set with the same name. By default, the
    // Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
    // common to share sources between related targets.
    // See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(libs.kotlin.stdlib)
                // Add KMP dependencies here
                api(libs.multipaz)
            }
        }

        commonTest {
            kotlin.srcDir("build/generated/ksp/metadata/commonTest/kotlin")
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }

        androidMain {
            dependencies {
                // Add Android-specific dependencies here. Note that this source set depends on
                // commonMain by default and will correctly pull the Android artifacts of any KMP
                // dependencies declared in commonMain.
            }
        }

        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.runner)
                implementation(libs.androidx.core)
                implementation(libs.androidx.junit)
            }
        }

        iosMain {
            dependencies {
                // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
                // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
                // part of KMP’s default source set hierarchy. Note that this source set depends
                // on common by default and will correctly pull the iOS artifacts of any
                // KMP dependencies declared in commonMain.
                api(libs.multipaz.dcapi)
                api(libs.multipaz.doctypes)
                api(libs.multipaz.longfellow)
                api(libs.multipaz.swiftui)
                api(libs.kotlinx.io.bytestring)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.core)
                api(libs.ktor.client.darwin)
            }
        }
    }

}

dependencies {
    add("kspCommonMainMetadata", libs.multipaz.cbor.rpc)
    add("kspJvmTest", libs.multipaz.cbor.rpc)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

tasks["compileKotlinIosX64"].dependsOn("kspCommonMainKotlinMetadata")
tasks["compileKotlinIosArm64"].dependsOn("kspCommonMainKotlinMetadata")
tasks["compileKotlinIosSimulatorArm64"].dependsOn("kspCommonMainKotlinMetadata")
