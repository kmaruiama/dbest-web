plugins {
    application
    kotlin("jvm") version "2.0.21" apply false
}

allprojects {
    group = "io.github.dbest"
    version = "0.1.0-SNAPSHOT"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("dbest.kernel.http.ServerKt")
    applicationName = "dbest"
}

val frontendAssets = configurations.create("frontendAssets") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    runtimeOnly(project(":app:server"))
    frontendAssets(project(path = ":app:client", configuration = "frontendAssets"))
}

tasks.processResources {
    from(frontendAssets) {
        into("public")
    }
}

tasks.jar {
    archiveClassifier.set("web")
}

tasks.check {
    setDependsOn(listOf(":app:server:check", ":modules:engine:check"))
}

val runtimeClasspath = configurations.runtimeClasspath
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
    dependsOn(runtimeClasspath)
    from({ runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
            "org/http4k/security/HmacSha256*.class",
        )
    }
}

tasks.assemble {
    dependsOn(bundledJar)
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
