import org.gradle.internal.os.OperatingSystem

plugins {
    application
    java
    jacoco
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "8.10.3"
}

group = "io.github.marodriguezd.taskflow"
version = "1.2.3"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.12"
    modules("javafx.controls", "javafx.fxml", "javafx.media", "javafx.graphics")
}

dependencies {
    // SQLite local persistence
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")

    // Logging: SLF4J + Logback
    implementation("org.slf4j:slf4j-api:2.0.20")
    implementation("ch.qos.logback:logback-classic:1.6.5")

    // Jackson for legacy JSON migration & config
    implementation("com.fasterxml.jackson.core:jackson-databind:2.22.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.22.3")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:6.1.3")
    testImplementation("org.assertj:assertj-core:3.27.7")
}

application {
    // Non-modular project (no module-info.java): intentionally no mainModule — setting an
    // empty/invalid module name makes the :run task fail with "Module  not found".
    // TaskFlowLauncher (a plain class, not an Application subclass) starts JavaFX reliably
    // from the classpath; jpackage passes --main-class explicitly.
    mainClass.set("io.github.marodriguezd.taskflow.TaskFlowLauncher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-this-escape"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    testLogging {
        events("passed", "skipped", "failed")
    }
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.45".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.named("build") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotless {
    java {
        googleJavaFormat("1.36.1").aosp()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "io.github.marodriguezd.taskflow.TaskFlowLauncher",
            "Implementation-Title" to "TaskFlow",
            "Implementation-Version" to project.version,
            "Implementation-License" to "GPL-3.0-only"
        )
    }
}

// ─────────────────────────────────────────────
// Native Packaging via jpackage
// ─────────────────────────────────────────────

val currentOs = OperatingSystem.current()

tasks.register<Exec>("jpackageImage") {
    group = "distribution"
    description = "Builds a standalone application image using jpackage with bundled Java runtime."
    dependsOn(tasks.installDist)

    val inputDir = layout.buildDirectory.dir("install/${project.name}/lib")
    val outputDir = layout.buildDirectory.dir("dist")
    val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
    val jpackageBin = if (currentOs.isWindows) "$javaHome/bin/jpackage.exe" else "$javaHome/bin/jpackage"

    doFirst {
        outputDir.get().asFile.mkdirs()
        delete(outputDir.get().dir("TaskFlow"))
    }

    val iconPath = when {
        currentOs.isWindows -> file("assets/taskflow.ico").absolutePath
        currentOs.isMacOsX -> file("assets/taskflow.icns").absolutePath
        else -> file("assets/TaskFlow.png").absolutePath
    }

    commandLine(
        jpackageBin,
        "--type", "app-image",
        "--name", "TaskFlow",
        "--app-version", project.version.toString(),
        "--vendor", "TaskFlow",
        "--input", inputDir.get().asFile.absolutePath,
        "--main-jar", tasks.jar.get().archiveFileName.get(),
        "--main-class", "io.github.marodriguezd.taskflow.TaskFlowLauncher",
        "--dest", outputDir.get().asFile.absolutePath,
        "--icon", iconPath,
        "--java-options", "-Dfile.encoding=UTF-8"
    )
}

tasks.register("releaseStamp") {
    group = "distribution"
    description =
            "Writes a RELEASE_VERSION stamp into build/dist so packaged artifacts carry verifiable provenance."
    // Capture at configuration time: resolving Task.project at execution time is deprecated
    // in Gradle 9 and will fail in Gradle 10.
    val stampedVersion = project.version.toString()
    doLast {
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        distDir.mkdirs()
        distDir.resolve("RELEASE_VERSION").writeText(stampedVersion + "\n")
        println("RELEASE_VERSION stamp written: $stampedVersion")
    }
}

// Linux: jpackage builds a plain app image, then tools/build-appimage.sh turns
// it into an x86_64 AppImage. The bundled runtime still relies on compatible Linux userspace
// and desktop/runtime libraries (no dpkg package integration is required).
if (currentOs.isLinux) {
    val appImageStagingDir = layout.buildDirectory.dir("dist/appimage-staging")
    val linuxJavaHome =
            javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath

    tasks.register<Exec>("jpackageLinuxAppImage") {
        group = "distribution"
        description = "Builds the jpackage app image used as the source for the Linux AppImage package."
        dependsOn(tasks.installDist)

        doFirst {
            appImageStagingDir.get().asFile.deleteRecursively()
            appImageStagingDir.get().asFile.mkdirs()
        }

        commandLine(
            "$linuxJavaHome/bin/jpackage",
            "--type", "app-image",
            "--name", "TaskFlow",
            "--app-version", project.version.toString(),
            "--vendor", "TaskFlow",
            "--input", layout.buildDirectory.dir("install/${project.name}/lib").get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "io.github.marodriguezd.taskflow.TaskFlowLauncher",
            "--dest", appImageStagingDir.get().asFile.absolutePath,
            "--icon", file("assets/TaskFlow.png").absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8"
        )
    }

    tasks.register<Exec>("jpackagePackage") {
        group = "distribution"
        description = "Builds the native Linux package: a portable .AppImage with a bundled Java runtime."
        dependsOn(tasks.installDist)
        dependsOn("jpackageLinuxAppImage")
        finalizedBy("releaseStamp")

        doFirst {
            layout.buildDirectory.dir("dist").get().asFile.mkdirs()
        }

        commandLine(
            "bash",
            file("tools/build-appimage.sh").absolutePath,
            appImageStagingDir.get().dir("TaskFlow").asFile.absolutePath,
            layout.buildDirectory
                .dir("dist")
                .get()
                .asFile
                .resolve("TaskFlow-${project.version}-x86_64.AppImage")
                .absolutePath
        )
    }
} else {
    tasks.register<Exec>("jpackagePackage") {
        group = "distribution"
        description = "Builds native package (exe/msi on Windows, dmg on macOS)."
        dependsOn(tasks.installDist)
        finalizedBy("releaseStamp")

        val inputDir = layout.buildDirectory.dir("install/${project.name}/lib")
        val outputDir = layout.buildDirectory.dir("dist")
        val javaHome = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile.absolutePath
        val jpackageBin = if (currentOs.isWindows) "$javaHome/bin/jpackage.exe" else "$javaHome/bin/jpackage"

        doFirst {
            outputDir.get().asFile.mkdirs()
        }

        val iconPath = when {
            currentOs.isWindows -> file("assets/taskflow.ico").absolutePath
            else -> file("assets/taskflow.icns").absolutePath
        }

        val argsList = mutableListOf(
            jpackageBin,
            "--type", if (currentOs.isWindows) "msi" else "dmg",
            "--name", "TaskFlow",
            "--app-version", project.version.toString(),
            "--vendor", "TaskFlow",
            "--input", inputDir.get().asFile.absolutePath,
            "--main-jar", tasks.jar.get().archiveFileName.get(),
            "--main-class", "io.github.marodriguezd.taskflow.TaskFlowLauncher",
            "--dest", outputDir.get().asFile.absolutePath,
            "--icon", iconPath,
            "--license-file", file("LICENSE").absolutePath,
            "--java-options", "-Dfile.encoding=UTF-8"
        )

        if (currentOs.isWindows) {
            argsList.addAll(listOf(
                "--win-shortcut",
                "--win-menu"
            ))
        }

        commandLine(argsList)
    }
}
