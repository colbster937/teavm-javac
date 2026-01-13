plugins {
    id("java")
    alias(libs.plugins.teavm)
}

configurations {
    create("classlibInput") {
        isTransitive = false
    }
    create("classlibAuxInput") {
        isTransitive = false
    }
    create("dist") {
        isTransitive = false
    }
}

dependencies {
    implementation(project(":javac"))
    implementation(project(":protocol"))
    compileOnly(libs.teavm.core)
    implementation(libs.teavm.tooling)
    implementation(libs.asm.core)
    add("classlibInput", libs.teavm.classlib)
    add("classlibInput", libs.teavm.jso)
    add("classlibInput", libs.teavm.jso.apis)
    add("classlibAuxInput", libs.teavm.core)
    add("classlibAuxInput", libs.teavm.jso.impl)
    add("classlibAuxInput", libs.teavm.interop)
    add("classlibAuxInput", libs.teavm.platform)
    add("classlibAuxInput", libs.jzlib)
}

sourceSets {
    create("classlibEmu") {
    }
}

tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("--limit-modules", "java.base"))
}

teavm {
    all {
        mainClass = "org.teavm.javac.CompilerLib"
    }
    wasmGC {
        outOfProcess = true
        processMemory = 4096
        modularRuntime = true
    }
}

val classlibOutDir = project.layout.buildDirectory.dir("classlib")

tasks.register("generateClassLib", JavaExec::class.java) {
    val output = classlibOutDir.map { it.file("compile-classlib-teavm.bin") }
    dependsOn(configurations.named("classlibInput"))
    dependsOn(sourceSets.named("classlibEmu").get().output)
    inputs.files(configurations.named("classlibInput"))
    inputs.files(sourceSets.named("classlibEmu").get().output.classesDirs)
    outputs.file(output)

    classpath(sourceSets.named("main").get().output, sourceSets.named("main").get().runtimeClasspath)
    mainClass.set("org.teavm.javac.StdlibConverter")

    doFirst {
        args = listOf(
            output.get().asFile.absolutePath,
            *configurations.named("classlibInput").get().files.map { it.absolutePath }.toTypedArray(),
            *sourceSets.named("classlibEmu").get().output.classesDirs.files.map { it.absolutePath }.toTypedArray()
        )
    }
}

val classLibTmpDir = project.layout.buildDirectory.dir("tmp/teavm-classlib")

val unpackTeaVMClasslib = tasks.register("unpackTeaVMClassLib", Copy::class.java) {
    from(provider {
        configurations.named("classlibInput").get().map { zipTree(it) }
    })
    exclude("META-INF/MANIFEST.MF")
    into(classLibTmpDir)
}

val unpackCldr = tasks.register("unpackCldr", Copy::class.java) {
    dependsOn(unpackTeaVMClasslib)
    from(classLibTmpDir.map { it.file("org/teavm/classlib/impl/unicode/cldr-json.zip") }.map { zipTree(it) })
    into(project.layout.buildDirectory.dir("tmp/cldr"))
}

val repackCldr = tasks.register("repackCldr", Zip::class.java) {
    from(unpackCldr)
    destinationDirectory.set(project.layout.buildDirectory.dir("tmp"))
    archiveFileName.set("cldr-json.zip")
    include("en/**")
    include("supplemental/**")
}

val prepareTeaVMClassLib = tasks.register("prepareTeaVMClassLib", Copy::class.java) {
    dependsOn(unpackTeaVMClasslib, repackCldr)
    from(unpackTeaVMClasslib) {
        exclude("org/teavm/classlib/impl/unicode/cldr-json.zip")
    }
    from(provider {
        configurations.named("classlibAuxInput").get().map {
            zipTree(it)
        }
    }) {
        include("org/teavm/classlib/**")
        include("org/teavm/platform/**")
        include("org/teavm/jso/**")
        include("org/teavm/runtime/**")
        include("org/teavm/interop/**")
        include("org/teavm/backend/wasm/runtime/**")
        include("org/teavm/backend/wasm/WasmRuntime*.class")
        include("org/teavm/backend/wasm/WasmHeap*.class")
        include("com/jcraft/jzlib/**")
    }
    from(repackCldr) {
        eachFile {
            path = "org/teavm/classlib/impl/unicode/" + path
            if (path == "META-INF/MANIFEST.MF") {
                exclude()
            }
        }
    }
    into(project.layout.buildDirectory.dir("tmp/classlib-runtime"))
}

val buildTeaVMClassLib = tasks.register("buildTeaVMClassLib", JavaExec::class.java) {
    dependsOn(prepareTeaVMClassLib, tasks.named("compileJava"))
    classpath(sourceSets.named("main").get().runtimeClasspath)
    val inputDir = project.layout.buildDirectory.dir("tmp/classlib-runtime")
    val outputFile = project.layout.buildDirectory.file("classlib/runtime-classlib-teavm.bin")
    inputs.dir(inputDir)
    outputs.file(outputFile)

    mainClass.set("org.teavm.javac.ArchiveBuilder")

    doFirst {
        args = listOf(
            inputDir.get().asFile.absolutePath,
            outputFile.get().asFile.absolutePath
        )
    }
}

tasks.named("build") {
    dependsOn(tasks.named("buildWasmGC"), buildTeaVMClassLib, tasks.named("generateClassLib"))
}

val createDist = tasks.register("createDist", Zip::class.java) {
    group = "build"
    dependsOn(tasks.named("buildWasmGC"))
    from(buildTeaVMClassLib)
    from(tasks.named("generateClassLib"))
    from(layout.buildDirectory.dir("generated/teavm/wasm-gc"))
    exclude("src/**")
    archiveBaseName.set("dist")
}

artifacts.add("dist", createDist)
