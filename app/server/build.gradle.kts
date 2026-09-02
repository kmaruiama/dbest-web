plugins {
    kotlin("jvm") version "2.0.21"
    application
}

val frontendDir = file("../client")

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("dbest.kernel.http.ServerKt")
    applicationName = "dbest"
}

sourceSets {
    main { kotlin.setSrcDirs(listOf("src/features", "src/kernel", "src/util")) }
    test { kotlin.setSrcDirs(listOf("test")) }
}

dependencies {
    implementation(project(":modules:engine"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.http4k:http4k-core:5.47.0.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

val npmExecutable =
    if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

val buildFrontend = tasks.register<Exec>("buildFrontend") {
    inputs.dir(frontendDir.resolve("src"))
    inputs.files(
        frontendDir.resolve("package.json"),
        frontendDir.resolve("package-lock.json"),
        frontendDir.resolve("index.html"),
        frontendDir.resolve("vite.config.ts"),
        frontendDir.resolve("tsconfig.json"),
    )
    outputs.dir(frontendDir.resolve("dist"))
    workingDir = frontendDir
    commandLine(npmExecutable, "run", "build")
}

tasks.processResources {
    dependsOn(buildFrontend)
    from(frontendDir.resolve("dist")) {
        into("public")
    }
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}

val bundledJar = tasks.register<Jar>("bundledJar") {
    group = "build"
    description = "Builds a self-contained JAR containing the API server and frontend."
    archiveBaseName.set("dbest")
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Title"] = "DBest"
        attributes["Implementation-Version"] = project.version.toString()
    }
    from(sourceSets.main.get().output)
    dependsOn(tasks.classes)
    from({
        configurations.runtimeClasspath.get()
            .map { if (it.isDirectory) it else zipTree(it) }
    }) {
        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "org/http4k/security/HmacSha256*.class",
        )
    }
}

tasks.register<Exec>("signBundledJar") {
    group = "distribution"
    description = "Signs dbest-${project.version}.jar with the configured code-signing certificate."
    dependsOn(bundledJar)
    doFirst {
        val keystore = System.getenv("DBEST_SIGNING_KEYSTORE") ?: error("DBEST_SIGNING_KEYSTORE is required")
        val alias = System.getenv("DBEST_SIGNING_ALIAS") ?: error("DBEST_SIGNING_ALIAS is required")
        check(System.getenv("DBEST_SIGNING_STOREPASS") != null) { "DBEST_SIGNING_STOREPASS is required" }
        check(System.getenv("DBEST_SIGNING_KEYPASS") != null) { "DBEST_SIGNING_KEYPASS is required" }
        val executableName = if (System.getProperty("os.name").startsWith("Windows")) "jarsigner.exe" else "jarsigner"
        commandLine(
            file(System.getProperty("java.home")).resolve("bin").resolve(executableName).absolutePath,
            "-keystore", keystore,
            "-storepass:env", "DBEST_SIGNING_STOREPASS",
            "-keypass:env", "DBEST_SIGNING_KEYPASS",
            bundledJar.get().archiveFile.get().asFile.absolutePath,
            alias,
        )
    }
}
