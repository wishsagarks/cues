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

// `./dev d "<sentence>"` routes here.
tasks.register<JavaExec>("demo") {
    group = "cues"
    description = "Compiles a spoken sentence and prints its review and rehearsal."
    mainClass.set("com.cues.core.cli.MainKt")
    classpath = sourceSets["main"].runtimeClasspath
    // Gradle's --args is awkward to type on a phone keyboard; -Pq="..." is not.
    if (project.hasProperty("q")) args(project.property("q").toString())
}
