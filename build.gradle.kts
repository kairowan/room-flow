// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.ksp) apply false
}

// Reuse the pinned Kotlin compiler for syntax-aware repository rules, never ship it in the AAR.
val typeRulesCompiler by configurations.creating
dependencies { typeRulesCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.21") }
val typeRulesOutput = layout.buildDirectory.dir("type-rules")
val compileTypeRules by tasks.registering(JavaExec::class) {
    classpath = typeRulesCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.file("scripts/CheckKotlinTypes.kt")
    outputs.dir(typeRulesOutput)
    doFirst {
        args = listOf("-no-stdlib", "-no-reflect", "-classpath", typeRulesCompiler.asPath,
            "-d", typeRulesOutput.get().asFile.absolutePath, file("scripts/CheckKotlinTypes.kt").absolutePath)
    }
}
tasks.register<JavaExec>("checkKotlinTypes") {
    dependsOn(compileTypeRules)
    classpath = typeRulesCompiler + files(typeRulesOutput)
    mainClass.set("CheckKotlinTypesKt")
    args(rootDir.absolutePath)
}

tasks.register("checkModuleBoundaries") {
    doLast {
        val core = project(":room-flow").configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
        check(core.none { it.moduleVersion?.group in setOf("androidx.appcompat", "androidx.recyclerview", "com.google.android.material") }) {
            "核心模块不能传递引入调试 UI 依赖"
        }
        check(core.none { it.moduleVersion?.let { module ->
            module.group == "com.google.devtools.ksp" || module.name.contains("compiler") || module.name == "kotlin-reflect"
        } == true }) { "编译器/KSP/反射依赖不能进入核心运行时" }
        val release = project(":app").configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
        check(release.none { it.id.displayName == "project :room-flow-debug" }) { "示例 Release 不能包含调试模块" }
        check(release.none { it.moduleVersion?.group?.let { group ->
            group.startsWith("androidx.test") || group == "junit" || group == "org.hamcrest"
        } == true }) { "正常 Release 不能包含验证 Runner/JUnit 依赖" }
        println("Module boundaries: core UI-free, app Release excludes debug module")
    }
}

tasks.register("checkJvmApi") {
    dependsOn(":room-flow:assembleRelease", ":room-flow-debug:assembleRelease")
    doLast {
        for (module in listOf("room-flow", "room-flow-debug")) {
            exec {
                commandLine("bash", rootProject.file("scripts/check-jvm-api.sh"),
                    project(":$module").layout.buildDirectory.file("outputs/aar/$module-release.aar").get().asFile,
                    rootProject.file("verification/api/$module.txt"))
            }
        }
    }
}
