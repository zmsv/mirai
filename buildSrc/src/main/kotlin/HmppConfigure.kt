/*
 * Copyright 2019-2022 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/dev/LICENSE
 */

import com.google.gradle.osdetector.OsDetector
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.MAIN_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation.Companion.TEST_COMPILATION_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinTargetPreset
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.io.File

private val miraiPlatform = Attribute.of(
    "net.mamoe.mirai.platform", String::class.java
)

val IDEA_ACTIVE = System.getProperty("idea.active") == "true" && System.getProperty("publication.test") != "true"

val NATIVE_ENABLED = System.getProperty("mirai.enable.native", "true").toBoolean()
val ANDROID_ENABLED = System.getProperty("mirai.enable.android", "true").toBoolean()

val OS_NAME = System.getProperty("os.name").toLowerCase()

enum class HostKind {
    LINUX, WINDOWS, MACOS,
}

val HOST_KIND = when {
    OS_NAME.contains("windows", true) -> HostKind.WINDOWS
    OS_NAME.contains("mac", true) -> HostKind.MACOS
    else -> HostKind.LINUX
}

enum class HostArch {
    X86, X64, ARM32, ARM64
}

lateinit var osDetector: OsDetector

val MAC_TARGETS: Set<String> by lazy {
    if (!IDEA_ACTIVE) setOf(
//        "watchosX86",
        "macosX64",
        "macosArm64",

        // Failed to generate cinterop for :mirai-core:cinteropOpenSSLIosX64: Process 'command '/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java'' finished with non-zero exit value 1
        // Exception in thread "main" java.lang.Error: /var/folders/kw/ndw272ns06s7cys2mcwwlb5c0000gn/T/1181140105365175708.c:1:10: fatal error: 'openssl/ec.h' file not found
        //
        // Note: the openssl/ec.h is actually there, seems Kotlin doesn't support that

//        "iosX64",
//        "iosArm64",
//        "iosArm32",
//        "iosSimulatorArm64",
//        "watchosX64",
//        "watchosArm32",
//        "watchosArm64",
//        "watchosSimulatorArm64",
//        "tvosX64",
//        "tvosArm64",
//        "tvosSimulatorArm64",
    ) else setOf(
        // IDEA active, reduce load
        if (osDetector.arch.contains("aarch")) "macosArm64" else "macosX64"
    )
}

val WIN_TARGETS = setOf("mingwX64")

val LINUX_TARGETS = setOf("linuxX64")

val UNIX_LIKE_TARGETS by lazy { LINUX_TARGETS + MAC_TARGETS }

val NATIVE_TARGETS by lazy { UNIX_LIKE_TARGETS + WIN_TARGETS }

val HOST_NATIVE_TARGET by lazy {
    when (HOST_KIND) {
        HostKind.LINUX -> "linuxX64"
        HostKind.MACOS -> if (osDetector.arch.contains("aarch")) "macosArm64" else "macosX64"
        HostKind.WINDOWS -> "mingwX64"
    }
}


fun Project.configureHMPPJvm() {
    extensions.getByType(KotlinMultiplatformExtension::class.java).apply {
        jvm("jvmBase") {
            compilations.all {
                this.compileKotlinTask.enabled = false // IDE complain
            }
            attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.common) // avoid resolving by others
//            attributes.attribute(miraiPlatform, "jvmBase")
        }

        if (isAndroidSDKAvailable && ANDROID_ENABLED) {
            jvm("android") {
                attributes.attribute(KotlinPlatformType.attribute, KotlinPlatformType.androidJvm)
                //   publishAllLibraryVariants()
            }
        } else {
            printAndroidNotInstalled()
        }

        jvm("jvm") {

        }
    }
}

///**
// * [IDEA_ACTIVE] 时配置单一 'native' target, 基于 host 平台; 否则配置所有 native targets 依赖 'native' 作为中间平台.
// */
//@Deprecated("")
//fun KotlinMultiplatformExtension.configureNativeTargets(
//    project: Project
//) {
//    val nativeMainSets = mutableListOf<KotlinSourceSet>()
//    val nativeTestSets = mutableListOf<KotlinSourceSet>()
//    val nativeTargets = mutableListOf<KotlinNativeTarget>()
//
//    if (IDEA_ACTIVE) {
//        val target = when {
//            Os.isFamily(Os.FAMILY_MAC) -> if (Os.isArch("aarch64")) macosArm64("native") else macosX64(
//                "native"
//            )
//            Os.isFamily(Os.FAMILY_WINDOWS) -> mingwX64("native")
//            else -> linuxX64("native")
//        }
//        nativeTargets.add(target)
//    } else {
//        // 1.6.0
//        val nativeTargetNames: List<String> = arrayOf(
//            // serialization doesn't support those commented targets
////                "androidNativeArm32, androidNativeArm64, androidNativeX86, androidNativeX64",
//            "iosArm32, iosArm64, iosX64, iosSimulatorArm64",
//            "watchosArm32, watchosArm64, watchosX86, watchosX64, watchosSimulatorArm64",
//            "tvosArm64, tvosX64, tvosSimulatorArm64",
//            "macosX64, macosArm64",
////            "linuxMips32, linuxMipsel32, linuxX64",
//            "linuxX64",
//            "mingwX64",
////                "wasm32" // linuxArm32Hfp, mingwX86
//        ).flatMap { it.split(",") }.map { it.trim() }
//        presets.filter { it.name in nativeTargetNames }.forEach { preset ->
//            val target = targetFromPreset(preset, preset.name) as KotlinNativeTarget
//            nativeMainSets.add(target.compilations[MAIN_COMPILATION_NAME].kotlinSourceSets.first())
//            nativeTestSets.add(target.compilations[TEST_COMPILATION_NAME].kotlinSourceSets.first())
//            nativeTargets.add(target)
//        }
//
//        if (!IDEA_ACTIVE) {
//            project.configure(nativeMainSets) {
//                dependsOn(sourceSets.maybeCreate("nativeMain"))
//            }
//
//            project.configure(nativeTestSets) {
//                dependsOn(sourceSets.maybeCreate("nativeTest"))
//            }
//        }
//    }
//
//    project.configureNativeInterop("main", project.projectDir.resolve("src/nativeMainInterop"), nativeTargets)
//    project.configureNativeInterop("test", project.projectDir.resolve("src/nativeTestInterop"), nativeTargets)
//    project.configureNativeLinkOptions(nativeTargets)
//
//    val sourceSets = project.kotlinSourceSets.orEmpty()
//    val commonMain = sourceSets.single { it.name == "commonMain" }
//    val commonTest = sourceSets.single { it.name == "commonTest" }
//    val jvmBaseMain = this.sourceSets.maybeCreate("jvmBaseMain")
//    val jvmBaseTest = this.sourceSets.maybeCreate("jvmBaseTest")
//    val jvmMain = sourceSets.single { it.name == "jvmMain" }
//    val jvmTest = sourceSets.single { it.name == "jvmTest" }
//    val androidMain = sourceSets.single { it.name == "androidMain" }
//    val androidTest = sourceSets.single { it.name == "androidTest" }
//
//    val nativeMain = sourceSets.single { it.name == "nativeMain" }
//    val nativeTest = sourceSets.single { it.name == "nativeTest" }
//
//
//    jvmBaseMain.dependsOn(commonMain)
//    jvmBaseTest.dependsOn(commonTest)
//
//    jvmMain.dependsOn(jvmBaseMain)
//    androidMain.dependsOn(jvmBaseMain)
//
//    jvmTest.dependsOn(jvmBaseTest)
//    androidTest.dependsOn(jvmBaseTest)
//
//    nativeMain.dependsOn(commonMain)
//    nativeTest.dependsOn(commonTest)
//}

/**
 * ```
 *                      common
 *                        |
 *        /---------------+---------------\
 *     jvmBase                          native
 *     /    \                          /     \
 *   jvm   android                  unix      \
 *                                 /    \     mingwX64
 *                                /      \
 *                            darwin     linuxX64
 *                               |
 *                               *
 *                        <darwin targets>
 * ```
 *
 * `<darwin targets>`: macosX64, macosArm64, tvosX64, iosArm64, iosArm32...
 */
fun KotlinMultiplatformExtension.configureNativeTargetsHierarchical(
    project: Project
) {
    val nativeMainSets = mutableListOf<KotlinSourceSet>()
    val nativeTestSets = mutableListOf<KotlinSourceSet>()
    val nativeTargets = mutableListOf<KotlinNativeTarget>()


    fun KotlinMultiplatformExtension.addNativeTarget(
        preset: KotlinTargetPreset<*>,
    ): KotlinNativeTarget {
        val target = targetFromPreset(preset, preset.name) as KotlinNativeTarget
        nativeMainSets.add(target.compilations[MAIN_COMPILATION_NAME].kotlinSourceSets.first())
        nativeTestSets.add(target.compilations[TEST_COMPILATION_NAME].kotlinSourceSets.first())
        nativeTargets.add(target)
        return target
    }


    // project.configureNativeInterop("main", project.projectDir.resolve("src/nativeMainInterop"), nativeTargets)
    // project.configureNativeInterop("test", project.projectDir.resolve("src/nativeTestInterop"), nativeTargets)


    val sourceSets = project.objects.domainObjectContainer(KotlinSourceSet::class.java)
        .apply { addAll(project.kotlinSourceSets.orEmpty()) }

    val commonMain by sourceSets.getting
    val commonTest by sourceSets.getting
    val jvmBaseMain = this.sourceSets.maybeCreate("jvmBaseMain")
    val jvmBaseTest = this.sourceSets.maybeCreate("jvmBaseTest")
    val jvmMain by sourceSets.getting
    val jvmTest by sourceSets.getting
    val androidMain by sourceSets.getting
    val androidTest by sourceSets.getting

    val nativeMain = this.sourceSets.maybeCreate("nativeMain")
    val nativeTest = this.sourceSets.maybeCreate("nativeTest")

    val unixMain = this.sourceSets.maybeCreate("unixMain")
    val unixTest = this.sourceSets.maybeCreate("unixTest")

    val darwinMain = this.sourceSets.maybeCreate("darwinMain")
    val darwinTest = this.sourceSets.maybeCreate("darwinTest")

    presets.filter { it.name in MAC_TARGETS }.forEach { preset ->
        addNativeTarget(preset).run {
            compilations[MAIN_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(darwinMain) }
            compilations[TEST_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(darwinTest) }
        }
    }

    presets.filter { it.name in LINUX_TARGETS }.forEach { preset ->
        addNativeTarget(preset).run {
            compilations[MAIN_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(unixMain) }
            compilations[TEST_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(unixTest) }
        }
    }

    presets.filter { it.name in WIN_TARGETS }.forEach { preset ->
        addNativeTarget(preset).run {
            compilations[MAIN_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(nativeMain) }
            compilations[TEST_COMPILATION_NAME].kotlinSourceSets.forEach { it.dependsOn(nativeTest) }
        }
    }

    // Workaround from https://youtrack.jetbrains.com/issue/KT-52433/KotlinNative-Unable-to-generate-framework-with-Kotlin-1621-and-Xcode-134#focus=Comments-27-6140143.0-0
    project.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
        val properties = listOf(
            "ios_arm32", "watchos_arm32", "watchos_x86"
        ).joinToString(separator = ";") { "clangDebugFlags.$it=-Os" }
        kotlinOptions.freeCompilerArgs += listOf(
            "-Xoverride-konan-properties=$properties"
        )
    }

    jvmBaseMain.dependsOn(commonMain)
    jvmBaseTest.dependsOn(commonTest)

    nativeMain.dependsOn(commonMain)
    nativeTest.dependsOn(commonTest)

    unixMain.dependsOn(nativeMain)
    unixTest.dependsOn(nativeTest)

    darwinMain.dependsOn(unixMain)
    darwinTest.dependsOn(unixTest)

    jvmMain.dependsOn(jvmBaseMain)
    jvmTest.dependsOn(jvmBaseTest)

    androidMain.dependsOn(jvmBaseMain)
    androidTest.dependsOn(jvmBaseTest)
}


// e.g. Linker will try to link curl for mingwX64 but this can't be done on macOS.
fun Project.disableCrossCompile() {
    project.afterEvaluate {
        if (HOST_KIND != HostKind.MACOS) {
            MAC_TARGETS.forEach { target -> disableTargetLink(this, target) }
        }
        if (HOST_KIND != HostKind.WINDOWS) {
            WIN_TARGETS.forEach { target -> disableTargetLink(this, target) }
        }
        if (HOST_KIND != HostKind.LINUX) {
            LINUX_TARGETS.forEach { target -> disableTargetLink(this, target) }
        }
    }
}

private fun disableTargetLink(project: Project, target: String) {
    project.tasks.getByName("linkDebugTest${target.titlecase()}").enabled = false
    project.tasks.getByName("${target}Test").enabled = false
}

private fun Project.linkerDirs(): List<String> {
    return listOf(
        ":mirai-core",
        ":mirai-core-api",
        ":mirai-core-utils",
    ).map {
        rootProject.project(it).projectDir.resolve("src/nativeMainInterop/target/debug/").absolutePath
    }
}

private fun Project.includeDirs(): List<String> {
    return listOf(
        ":mirai-core",
        ":mirai-core-api",
        ":mirai-core-utils",
    ).map {
        rootProject.project(it).projectDir.resolve("src/nativeMainInterop/").absolutePath
    }
}

private fun Project.configureNativeInterop(
    compilationName: String, nativeInteropDir: File, nativeTargets: MutableList<KotlinNativeTarget>
) {
    val crateName = project.name.replace("-", "_") + "_i"

    if (nativeInteropDir.exists() && nativeInteropDir.isDirectory && nativeInteropDir.resolve("build.rs").exists()) {
        val kotlinDylibName = project.name.replace("-", "_") + "_i"
        val kotlinDylibName = project.name.replace("-", "_")

        val headerName = "$crateName.h"
        val rustLibDir = nativeInteropDir.resolve("target/debug/")

        var interopTaskName = ""

        configure(nativeTargets) {
            interopTaskName = compilations.getByName(compilationName).cinterops.create(compilationName) {
                defFile(nativeInteropDir.resolve("interop.def"))
                val headerFile = nativeInteropDir.resolve(headerName)
                if (headerFile.exists()) headers(headerFile)
            }.interopProcessingTaskName

            binaries {
                sharedLib {
                    linkerOpts("-v")
                    linkerOpts("-L${rustLibDir.absolutePath.replace("\\", "/")}")
//                    linkerOpts("-lmirai_core_utils_i")
                    linkerOpts("-undefined", "dynamic_lookup")
                    baseName = project.name
                }
                getTest(NativeBuildType.DEBUG).apply {
                    linkerOpts("-v")
                    linkerOpts("-L${rustLibDir.absolutePath.replace("\\", "/")}")
                    linkerOpts("-lmirai_core_utils_i")
//                    linkerOpts("-undefined", "dynamic_lookup")
                }
            }
        }

        val cbindgen = tasks.register("cbindgen${compilationName.titlecase()}") {
            group = "mirai"
            description = "Generate C Headers from Rust"
            inputs.files(project.objects.fileTree().from(nativeInteropDir.resolve("src"))
                .filterNot { it.name == "bindings.rs" })
            outputs.file(nativeInteropDir.resolve(headerName))
            doLast {
                exec {
                    workingDir(nativeInteropDir)
                    commandLine(
                        "cbindgen", "--config", "cbindgen.toml", "--crate", crateName, "--output", headerName
                    )
                }
            }
        }

        val generateRustBindings = tasks.register("generateRustBindings${compilationName.titlecase()}") {
            group = "mirai"
            description = "Generates Rust bindings for Kotlin"
            dependsOn(cbindgen)
        }

        afterEvaluate {
            val cinteropTask = tasks.getByName(interopTaskName)
            cinteropTask.mustRunAfter(cbindgen)
            generateRustBindings.get().dependsOn(cinteropTask)
        }

        val bindgen = tasks.register("bindgen${compilationName.titlecase()}") {
            group = "mirai"
            val bindingsPath = nativeInteropDir.resolve("src/bindings.rs")
            val headerFile = buildDir.resolve("bin/native/debugShared/lib${kotlinDylibName}_api.h")
            inputs.files(headerFile)
            outputs.file(bindingsPath)
            mustRunAfter(tasks.findByName("linkDebugSharedNative"))
            doLast {
                exec {
                    workingDir(nativeInteropDir)
                    // bindgen input.h -o bindings.rs
                    commandLine(
                        "bindgen",
                        headerFile,
                        "-o", bindingsPath,
                    )
                }
            }
        }

        val generateKotlinBindings = tasks.register("generateKotlinBindings${compilationName.titlecase()}") {
            group = "mirai"
            description = "Generates Kotlin bindings for Rust"
            dependsOn(bindgen)
            dependsOn(tasks.findByName("linkDebugSharedNative"))
        }

        var targetCompilation: KotlinNativeCompilation? = null
        configure(nativeTargets) {
            val compilations = compilations.filter { nativeInteropDir.name.contains(it.name, ignoreCase = true) }
            check(compilations.isNotEmpty()) { "Should be at lease one corresponding native compilation, but found 0" }
            targetCompilation = compilations.single()
//            targetCompilation!!.compileKotlinTask.dependsOn(cbindgen)
//            tasks.getByName("cinteropNative$name").dependsOn(cbindgen)
        }

        if (targetCompilation != null) {
            val compileRust = tasks.register("compileRust${compilationName.titlecase()}") {
                group = "mirai"
                inputs.files(nativeInteropDir.resolve("src"))
                outputs.file(rustLibDir.resolve("lib$crateName.dylib"))
//            dependsOn(targetCompilation!!.compileKotlinTask)
                dependsOn(bindgen)
                dependsOn(tasks.findByName("linkDebugSharedNative")) // dylib to link
                doLast {
                    exec {
                        workingDir(nativeInteropDir)
                        commandLine(
                            "cargo",
                            "build",
                            "--color", "always",
                            "--all",
//                        "--", "--color", "always", "2>&1"
                        )
                    }
                }
            }

            tasks.getByName("assemble").dependsOn(compileRust)
        }
    }
}

private fun Project.configureNativeLinkOptions(nativeTargets: MutableList<KotlinNativeTarget>) {
    configure(nativeTargets) {
        binaries {
            for (buildType in NativeBuildType.values()) {
                findTest(buildType)?.apply {
                    linkerOpts("-v")
                    linkerOpts(*linkerDirs().map { "-L$it" }.toTypedArray())
                    linkerOpts("-undefined", "dynamic_lookup") // resolve symbol in runtime
                }
            }
        }
    }
}

fun String.titlecase(): String {
    if (this.isEmpty()) return this
    val c = get(0)
    return replaceFirst(c, Character.toTitleCase(c))
}