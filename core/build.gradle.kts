plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// :core holds every decision Cues actually makes: what a routine means, whether
// a context matches, when a session may start, what a session still owes on the
// way out. None of it touches Android, so all of it is testable in a second on
// any machine — which is the whole point during Red Light.
dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Cues leans on exhaustive `when` over sealed hierarchies to make an
        // unhandled state a compile error rather than a silent fallthrough.
        allWarningsAsErrors.set(false)
    }
}

application {
    mainClass.set("com.cues.core.cli.MainKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        // Phone-readable: one line per failure, no stack-trace wall.
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.SHORT
        showStandardStreams = false
    }
}

// `./dev sim` routes here.
tasks.register<JavaExec>("sim") {
    group = "cues"
    description = "Runs a full cue lifecycle through CueService and prints each step's receipt."
    mainClass.set("com.cues.core.cli.SimMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// `./dev d "<sentence>"` routes here.
tasks.register<JavaExec>("demo") {
    group = "cues"
    description = "Compiles a spoken sentence and prints its review and rehearsal."
    mainClass.set("com.cues.core.cli.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // The review text contains en and em dashes. Without this the JVM follows
    // the ambient locale, which is ASCII in containers and in some terminals,
    // and the user sees "Monday?Friday" in the one place clarity matters most.
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    // Gradle's --args is awkward to type on a phone keyboard; -Pq="..." is not.
    if (project.hasProperty("q")) args(project.property("q").toString())
}

tasks.register<JavaExec>("bakeoff") {
    group = "cues"
    description = "Scores the local parser against the checked-in corpus; not a device measurement."
    mainClass.set("com.cues.core.cli.BakeOffMainKt")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("chat") {
    group = "cues"
    description = "Runs one Ask Cues conversation turn without a device."
    mainClass.set("com.cues.core.cli.ChatMain")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    if (project.hasProperty("q")) args(project.property("q").toString())
}

tasks.register<JavaExec>("coach") {
    group = "cues"
    description = "Replays the deterministic coach fixture."
    mainClass.set("com.cues.core.cli.CoachMain")
    classpath = sourceSets["main"].runtimeClasspath
}

tasks.register<JavaExec>("card") {
    group = "cues"
    description = "Exports, tampers with and reimports a sample Cue Card, phone-to-phone."
    mainClass.set("com.cues.core.cli.CardMain")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

tasks.register<JavaExec>("console") {
    group = "cues"
    description = "Builds a self-contained Cue Console HTML from a fixture dataset — no device, no server."
    mainClass.set("com.cues.core.cli.ConsoleMain")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
    if (project.hasProperty("q")) args(project.property("q").toString())
}

// `./dev insights` routes here.
tasks.register<JavaExec>("insights") {
    group = "cues"
    description = "Drives a synthetic week through CueService and prints its Insights report — no device."
    mainClass.set("com.cues.core.cli.InsightsMainKt")
    classpath = sourceSets["main"].runtimeClasspath
    jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
