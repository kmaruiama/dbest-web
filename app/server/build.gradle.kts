import com.ncorti.ktfmt.gradle.TrailingCommaManagementStrategy

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
    id("com.ncorti.ktfmt.gradle") version "0.24.0"
}

ktfmt {
    kotlinLangStyle()
    removeUnusedImports.set(false)
    trailingCommaManagementStrategy.set(TrailingCommaManagementStrategy.NONE)
}

kotlin { jvmToolchain(17) }

application {
    mainClass.set("dbest.ServerKt")
    applicationName = "dbest"
}

sourceSets {
    main { kotlin.setSrcDirs(listOf("src")) }
    test { kotlin.setSrcDirs(listOf("test")) }
}

dependencies {
    implementation(project(":modules:engine"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.http4k:http4k-core:5.47.0.0")

    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }

tasks.jar { manifest { attributes["Main-Class"] = application.mainClass.get() } }
