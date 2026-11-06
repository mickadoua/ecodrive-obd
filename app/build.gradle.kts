import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.github.triplet.gradle.androidpublisher.ResolutionStrategy

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.github.triplet.play")
}

// Version numbers. versionCode = number of commits reachable from HEAD (it grows with every
// merge into the published branch and is stable for a given commit); VERSION_CODE overrides it,
// for example to upload the same commit again. In a shallow clone rev-list counts too few
// commits: pass VERSION_CODE or clone in full. versionName = latest vX.Y.Z tag without the "v";
// VERSION_NAME overrides it.
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { null }
}.getOrNull()

val appVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.ifEmpty { null }
    ?.let { it.toIntOrNull()?.takeIf { code -> code > 0 } ?: throw GradleException("VERSION_CODE must be a positive integer") }
    ?: git("rev-list", "--count", "HEAD")?.toIntOrNull()
    ?: 1
val appVersionName = providers.environmentVariable("VERSION_NAME").orNull
    ?: git("describe", "--tags", "--abbrev=0", "--match", "v[0-9]*.[0-9]*.[0-9]*")?.removePrefix("v")
    ?: "0.1.0"

// Play upload key, read from the environment only. None of the variables set: the release
// build stays unsigned (developer machines). Only some of them set: a build that runs a release
// task fails, so that a half-configured release never goes out unsigned by mistake; debug
// builds, tests and lintDebug are not affected by a stray UPLOAD_* variable in the shell.
val uploadKeyEnv = listOf(
    "UPLOAD_KEYSTORE_PATH",
    "UPLOAD_KEYSTORE_PASSWORD",
    "UPLOAD_KEY_ALIAS",
    "UPLOAD_KEY_PASSWORD",
).associateWith { providers.environmentVariable(it).orNull?.ifEmpty { null } }
val uploadKeyMissing = uploadKeyEnv.filterValues { it == null }.keys
if (uploadKeyMissing.isNotEmpty() && uploadKeyMissing.size < uploadKeyEnv.size) {
    // The task graph, not the command line: "assemble", "build" or "publishBundle" also run
    // release tasks without naming them.
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.project == project && it.name.contains("Release") }) {
            throw GradleException("Upload key: missing $uploadKeyMissing (set all four UPLOAD_* or none)")
        }
    }
}

// Play service account key, as a file path (scripts/play-publish.sh writes it to a temporary
// file). Not set: Gradle Play Publisher stays disabled and a regular build never calls Play.
val playCredentials = providers.environmentVariable("PLAY_SERVICE_ACCOUNT_FILE").orNull?.ifEmpty { null }

android {
    namespace = "tech.dm.conso"
    compileSdk = 36

    defaultConfig {
        applicationId = "tech.dm.conso"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    if (uploadKeyMissing.isEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(uploadKeyEnv.getValue("UPLOAD_KEYSTORE_PATH")!!)
            storePassword = uploadKeyEnv.getValue("UPLOAD_KEYSTORE_PASSWORD")
            keyAlias = uploadKeyEnv.getValue("UPLOAD_KEY_ALIAS")
            keyPassword = uploadKeyEnv.getValue("UPLOAD_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Only the release variant goes to Play.
    playConfigs {
        register("release") {
            enabled.set(playCredentials != null)
        }
    }
}

// Defaults for scripts/play-publish.sh, which passes --track and --release-status.
// AUTO: if the versionCode taken from git is already used on Play, the next free one is used.
play {
    enabled.set(false)
    playCredentials?.let { serviceAccountCredentials.set(file(it)) }
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(ReleaseStatus.DRAFT)
    resolutionStrategy.set(ResolutionStrategy.AUTO)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
