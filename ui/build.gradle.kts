import org.teavm.gradle.api.WasmDebugInfoLocation
import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.bundling.War

plugins {
    id("java")
    id("war")
    alias(libs.plugins.teavm)
    alias(libs.plugins.nodejs)
    alias(libs.plugins.gretty)
}

configurations {
    create("webapp") {
        isTransitive = true
    }
}

dependencies {
    add("teavmImplementation", teavm.libs.jsoApis)
    add("teavmImplementation", project(":protocol"))
    add("webapp", project(mapOf("path" to ":compiler", "configuration" to "dist")))
}

teavm.wasmGC {
    mainClass = "org.teavm.javac.ui.Client"
    addedToWebApp = true
    debugInformation = true
    debugInfoLocation = WasmDebugInfoLocation.EXTERNAL
    modularRuntime = true
}

node {
    download = providers.gradleProperty("teavm.localNodeJS")
            .map { it == "true" }
            .map { !it }
            .orElse(true)
    distBaseUrl = null
}

val generateStaticTask = tasks.register("generateStatic", NpmTask::class.java) {
    dependsOn(tasks.npmInstall)
    inputs.files(layout.projectDirectory.file("package.json"))
    inputs.files(layout.projectDirectory.file("build.js"))
    outputs.dir(layout.projectDirectory.dir("build/static"))
    npmCommand.addAll(listOf("run", "build-all"))
}

tasks.named<War>("war") {
    extensions.extraProperties.set("archivePath", archiveFile.get().asFile)
    dependsOn(generateStaticTask, configurations.named("webapp"))
    from(layout.projectDirectory.dir("build/static"))
    from(provider {
        configurations.named("webapp").get().map { zipTree(it) }
    })
    exclude("**/*.jar")
}

gretty {
    contextPath = ""
}
