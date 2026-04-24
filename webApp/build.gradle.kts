import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    js(IR) {
        browser {
            commonWebpackConfig {
                cssSupport { enabled.set(true) }
            }
            binaries.executable()
        }
    }

    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(libs.multipaz)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.datetime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.ktor.client.js)
                
                implementation(libs.kotlin.wrappers.web)
                implementation(libs.kotlin.wrappers.react)
                implementation(libs.kotlin.wrappers.react.dom)
                implementation(libs.kotlin.wrappers.emotion.react.js)
            }
        }
    }
}
