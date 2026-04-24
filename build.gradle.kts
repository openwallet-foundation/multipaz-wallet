// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.skie) apply false
}


// The version number of the project.
//
// For a tagged release, projectVersionNext should be blank and the next commit
// following the release should bump it to the next version number.
//
val projectVersionLast: String? = null
val projectVersionNext: String = "0.1.0"

val appName: String by extra {
    System.getenv("MULTIPAZ_WALLET_APP_NAME")
        ?: "Multipaz Wallet Dev"
}

val updateUrl: String by extra {
    System.getenv("MULTIPAZ_WALLET_UPDATE_URL") ?: ""
}

val updateWebsite: String by extra {
    System.getenv("MULTIPAZ_WALLET_UPDATE_WEBSITE") ?: ""
}

// This must be of the form https://hostname.example.com, no trailing path allowed.
val backendUrl: String by extra {
    System.getenv("MULTIPAZ_WALLET_BACKEND_URL")
        ?: "https://dev.wallet.multipaz.org"
}

val backendClientId: String by extra {
    System.getenv("MULTIPAZ_WALLET_BACKEND_CLIENT_ID")
        ?: "1016113070986-54k257ap3h8hc40qm9kbl5lmhppjfel1.apps.googleusercontent.com"
}

val backendClientSecret: String by extra {
    System.getenv("MULTIPAZ_WALLET_BACKEND_CLIENT_SECRET")
        ?: "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"
}

val backendSecret: String by extra {
    System.getenv("MULTIPAZ_WALLET_BACKEND_SECRET")
        ?: "1234567890"
}

val developerModeAvailable: Boolean by extra {
    val str = System.getenv("MULTIPAZ_WALLET_DEVELOPER_MODE_AVAILABLE")
    str?.let {
        if (it.lowercase() == "false" || it == "0") {
            return@extra false
        }
    }
    true
}


// For `versionCode` we just use the number of commits.
val projectVersionCode: Int by extra {
    lazy {
        runCommand(listOf("git", "rev-list", "HEAD", "--count")).toInt()
    }.value
}

tasks.register("printVersionName") {
    doLast {
        println(projectVersionName)
    }
}

private fun runCommand(args: List<String>): String {
    return providers.exec {
        commandLine(args)
    }.standardOutput.asText.get().trim()
}

// Generate a project version meeting the requirements of Semantic Versioning 2.0.0
// according to https://semver.org/
//
// Essentially, for tagged releases use the version number e.g. "0.91.0". Otherwise use
// the next version number with a pre-release string set to "pre.N.H" where N is the
// number of commits since the last version and H is the short commit hash of the
// where we cut the pre-release from. Example: 0.91.0-pre.48.574b479c
//
val projectVersionName: String by extra {
    lazy {
        if (projectVersionNext.isEmpty()) {
            projectVersionLast!!
        } else {
            val numCommitsSinceTag =
            if (projectVersionLast != null) {
                runCommand(listOf("git", "rev-list", "${projectVersionLast}..", "--count"))
            } else {
                runCommand(listOf("git", "rev-list", "HEAD", "--count"))
            }
            val commitHash = runCommand(listOf("git", "rev-parse", "--short", "HEAD"))
            projectVersionNext + "-pre.${numCommitsSinceTag}.${commitHash}"
        }
    }.value
}


