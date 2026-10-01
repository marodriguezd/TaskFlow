import org.gradle.internal.os.OperatingSystem

plugins {
    application
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
    id("com.diffplug.spotless") version "7.0.2"
}

group = "io.github.marodriguezd.taskflow"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

javafx {
    version = "21.0.4"
    modules("javafx.controls", "javafx.fxml", "javafx.media", "javafx.graphics")
}

dependencies {
    // SQLite local persistence
    implementation("org.xerial:sqlite-jdbc:3.47.2.0")

    // Logging: SLF4J + Logback
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.16")

    // Jackson for legacy JSON migration & config
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.0")
}

application {
    mainModule.set("")
    mainClass.set("io.github.marodriguezd.taskflow.TaskFlowLauncher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-this-escape"))
}

tasks.withType<Test> {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

spotless {
    java {
        googleJavaFormat("1.24.0").aosp()
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
            "Implementation-License" to "CC-BY-NC-SA-4.0"
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
    doLast {
        val distDir = layout.buildDirectory.dir("dist").get().asFile
        distDir.mkdirs()
        distDir.resolve("RELEASE_VERSION").writeText(project.version.toString() + "\n")
        println("RELEASE_VERSION stamp written: ${project.version}")
    }
}

tasks.register<Exec>("jpackagePackage") {
    group = "distribution"
    description = "Builds native package (deb/rpm on Linux, exe/msi on Windows, dmg on macOS)."
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
        currentOs.isMacOsX -> file("assets/taskflow.icns").absolutePath
        else -> file("assets/TaskFlow.png").absolutePath
    }

    val pkgType = when {
        currentOs.isWindows -> "msi"
        currentOs.isMacOsX -> "dmg"
        else -> "deb"
    }

    val argsList = mutableListOf(
        jpackageBin,
        "--type", pkgType,
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

    if (currentOs.isLinux) {
        argsList.addAll(listOf(
            "--linux-shortcut",
            "--linux-menu-group", "Utility"
        ))
    } else if (currentOs.isWindows) {
        argsList.addAll(listOf(
            "--win-shortcut",
            "--win-menu"
        ))
    }

    commandLine(argsList)
}
