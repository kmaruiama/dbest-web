plugins {
    kotlin("jvm")
    application
}

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

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
    }
}
