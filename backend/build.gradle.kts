plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.ktor)
}

application {
    mainClass.set("org.multipaz.wallet.backend.Main")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":shared"))
    ksp(libs.multipaz.cbor.rpc)
    implementation(libs.multipaz)
    implementation(libs.multipaz.server)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.bytestring)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.java)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.logging)
    implementation(libs.logback.classic)
    implementation(libs.identity.google.api.client)
}

tasks.named<ProcessResources>("processResources") {
    val jsDist = project(":webApp").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")
    val resourcesDist = project(":webApp").layout.buildDirectory.dir("processedResources/js/main")
    
    from(jsDist) {
        into("static/web")
    }
    from(resourcesDist) {
        into("static/web")
    }
    dependsOn(":webApp:jsBrowserProductionWebpack")
}

ktor {
}

