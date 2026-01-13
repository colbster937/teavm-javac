plugins {
    alias(libs.plugins.download)
    id("java")
}

val revision = providers.gradleProperty("jdk.revision").get()

val downloadJDK = tasks.register("downloadJDK", de.undercouch.gradle.tasks.download.Download::class.java) {
    src("https://github.com/openjdk/jdk25u/archive/${revision}.zip")
    dest(layout.buildDirectory.file("jdk-${revision}.zip"))
    overwrite(false)
}

val unpackJDK = tasks.register("unpackJDK", Sync::class.java) {
    dependsOn(downloadJDK)
    from(downloadJDK.map { it.outputs.files.map { zipTree(it) } })
    into(layout.buildDirectory.dir("jdk"))
    exclude("**/module-info.java")
}

val baseJDKDir = layout.buildDirectory.dir("jdk/jdk25u-${revision}").get()

val compileTools = tasks.register("compileTools", JavaCompile::class.java) {
    dependsOn(unpackJDK)
    source(fileTree(baseJDKDir.dir("make/langtools/tools")) {
        exclude("anttasks")
        exclude("genstubs")
        exclude("crules")
    })
    destinationDirectory.set(layout.buildDirectory.dir("buildTools"))
    classpath = files()
}

val baseSrcDir = baseJDKDir.dir("src")
val generatedSrcDir = layout.buildDirectory.dir("generated/java").get()

val generateCompilerProperties = tasks.register("generateCompilerProperties", JavaExec::class.java) {
    classpath(compileTools)
    classpath(baseJDKDir.dir("make/langtools/tools"))
    dependsOn(unpackJDK)
    val files = listOf(
            "jdk.compiler/share/classes/com/sun/tools/javac/resources/compiler.properties",
            "jdk.compiler/share/classes/com/sun/tools/javac/resources/launcher.properties"
    )
    for (file in files) {
        val fileNameIndex = file.lastIndexOf('/')
        val dirName = file.substring(0, fileNameIndex)
        val fileName = file.substring(fileNameIndex + 1)
        val fileNameWithoutExtension = fileName.substring(0, fileName.length - 11)
        val capitalizedName = fileNameWithoutExtension.substring(0, 1).uppercase() +
                fileNameWithoutExtension.substring(1)
        val outputPath = dirName + "/" + capitalizedName + "Properties.java"

        val inputFile = baseSrcDir.file(file)
        val outputFile = generatedSrcDir.file(outputPath)
        args("-compile")
        args(inputFile.asFile.absolutePath)
        args(outputFile.asFile.parentFile.absolutePath)
        inputs.file(inputFile)
        outputs.file(outputFile)
        doFirst {
            outputFile.asFile.parentFile.mkdirs()
        }
    }

    mainClass.set("propertiesparser.PropertiesParser")
}

val compileCompilerProperties = tasks.register("compileCompilerProperties", JavaExec::class.java) {
    classpath(compileTools)
    classpath(baseJDKDir.dir("make/langtools/tools"))
    dependsOn(unpackJDK)
    val files = listOf(
            "com/sun/tools/javac/resources/compiler.properties",
            "com/sun/tools/javac/resources/launcher.properties",
            "com/sun/tools/javac/resources/javac.properties"
    )
    for (file in files) {
        val outputPath = file.substring(0, file.length - ".properties".length) + ".java"

        val outputFile = generatedSrcDir.file("jdk.compiler/share/classes/" + outputPath)
        args("-compile")
        args("./" + file)
        args(outputFile.asFile.absolutePath)
        args("java.util.ListResourceBundle")
        inputs.file(baseSrcDir.file("jdk.compiler/share/classes/" + file))
        outputs.file(outputFile)
        workingDir = baseSrcDir.dir("jdk.compiler/share/classes").asFile
        doFirst {
            outputFile.asFile.parentFile.mkdirs()
        }
    }

    mainClass.set("compileproperties.CompileProperties")
}

tasks.named("compileJava") {
    dependsOn(unpackJDK, compileTools, generateCompilerProperties, compileCompilerProperties)
}

sourceSets.named("main") {
    java {
        srcDir(baseSrcDir.dir("jdk.compiler/share/classes"))
        srcDir(generatedSrcDir.dir("jdk.compiler/share/classes"))
        srcDir(baseSrcDir.dir("java.compiler/share/classes"))
        srcDir(baseSrcDir.dir("jdk.internal.opt/share/classes"))
        exclude("module-info.java")
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(
            listOf(
                    "--limit-modules", "java.base",
                    "--add-exports", "java.base/jdk.internal.javac=ALL-UNNAMED",
                    "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                    "--add-exports", "java.base/jdk.internal.module=ALL-UNNAMED",
                    "--add-exports", "java.base/sun.reflect.annotation=ALL-UNNAMED"
            )
    )
    source(fileTree(baseSrcDir.dir("java.base/share/classes/")) {
        include("jdk/internal/jmod/**")
    })
}
