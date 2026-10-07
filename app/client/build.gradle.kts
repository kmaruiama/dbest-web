plugins {
    base
}

val npmExecutable =
    if (System.getProperty("os.name").startsWith("Windows")) "npm.cmd" else "npm"

val npmInstall = tasks.register<Exec>("npmInstall") {
    description = "Installs the locked client dependencies."
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
    workingDir = projectDir
    commandLine(npmExecutable, "ci")
}

val frontendDir = layout.projectDirectory.dir("dist")
val buildFrontend = tasks.register<Exec>("buildFrontend") {
    group = "build"
    description = "Type-checks and builds the browser client."
    dependsOn(npmInstall)
    inputs.files(fileTree(projectDir) {
        include("src/**", "public/**", "tests/**", "index.html", "package*.json", "tsconfig*.json", "*.config.*", ".env*")
    })
    outputs.dir(frontendDir)
    workingDir = projectDir
    commandLine(npmExecutable, "run", "build")
}

configurations.create("frontendAssets") {
    isCanBeConsumed = true
    isCanBeResolved = false
    outgoing.artifact(frontendDir) {
        builtBy(buildFrontend)
    }
}

tasks.assemble {
    dependsOn(buildFrontend)
}

tasks.clean {
    delete(frontendDir)
}
