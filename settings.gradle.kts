// Cues — build settings.
//
// :core is pure Kotlin/JVM and builds anywhere a JDK exists.
// :app is Android and is included ONLY when an SDK is actually present.
//
// This is deliberate. A large share of the event's build time is Red Light
// (phone only), and some work happens in cloud containers where dl.google.com
// is unreachable. `./dev t` has to stay green in all of them.
//
// Repository order is load-bearing: Maven Central is declared first and holds
// every :core dependency, so in an Android-free build Gradle finds everything
// before it ever looks at google(). Declaring a repository costs nothing; only
// querying one does.

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google() // reached only when :app is in the build and needs AGP
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google() // reached only for AndroidX/MediaPipe, i.e. only from :app
    }
}

rootProject.name = "cues"

include(":core")

if (androidSdkPath() != null) {
    include(":app")
} else {
    logger.lifecycle(
        "Cues: no Android SDK found — configuring :core only. " +
            "Set ANDROID_HOME or put sdk.dir in local.properties to include :app."
    )
}

/**
 * Locates an Android SDK, or returns null.
 *
 * Checked in the order a developer would expect to win: an explicit
 * local.properties entry, then the environment. A path that does not exist on
 * disk counts as absent — a stale local.properties carried between machines is
 * a common way to lose an afternoon.
 */
fun androidSdkPath(): String? {
    val fromProperties = file("local.properties")
        .takeIf { it.isFile }
        ?.let { propsFile ->
            java.util.Properties()
                .apply { propsFile.inputStream().use { load(it) } }
                .getProperty("sdk.dir")
        }

    return listOfNotNull(
        fromProperties,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    ).firstOrNull { it.isNotBlank() && File(it).isDirectory }
}
