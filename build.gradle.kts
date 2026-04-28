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


val appName: String by extra {
    System.getenv("MULTIPAZ_WALLET_APP_NAME")
        ?: "Multipaz Wallet Dev"
}

val androidAppId: String by extra {
    System.getenv("MULTIPAZ_WALLET_ANDROID_APP_ID")
        ?: "org.multipaz.wallet.android.dev"
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

// Generate a project version as "<year>.W<weekNumber>.<numCommits>-git-<gitSha1Ish>[-dirty]"
val projectVersionName: String by extra {
    lazy {
        val now = java.time.ZonedDateTime.now()
        val year = now.year
        val week = "%02d".format(now.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR))
        val branchName = runCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD"))
        val subFieldPrefix = if (branchName == "main") "1-" else "0-$branchName-"
        val numCommits = runCommand(listOf("git", "rev-list", "HEAD", "--count"))
        val commitHash = runCommand(listOf("git", "rev-parse", "--short", "HEAD"))
        val isDirty = runCommand(listOf("git", "status", "--porcelain", "-uno")).isNotEmpty()
        val dirtySuffix = if (isDirty) "-dirty" else ""
        "$year.W$week.$subFieldPrefix$numCommits-git-$commitHash$dirtySuffix"
    }.value
}


