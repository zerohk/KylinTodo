import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "space.buercheng.kylintodo"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KylinTodo"
            packageVersion = "1.0.0"
        }
    }
}

kotlin {
    // 与需求分析报告约定的开发环境一致（JDK 17/21）。
    // 使用 toolchain 而非绝对路径，Gradle 会自动探测本机 JDK，
    // 使 IDE 与命令行构建使用同一 Java 版本，且换机器无需改动。
    jvmToolchain(21)
}

// jvmToolchain 只约束编译与构建期间的 Java 版本；JavaExec 类任务（如 :run）
// 默认仍用启动 Gradle 守护进程的那个 JVM。若 JAVA_HOME 指向低版本 JDK（如 11），
// 运行时会因字节码版本不兼容抛 UnsupportedClassVersionError。
//
// 注意两点：
//   1. Gradle 会校验 executable 与 javaLauncher 必须来自同一 toolchain，
//      只设置其中一个会报 "Toolchain from `executable` property does not match"，
//      因此两者都从同一个 toolchain 启动器推导，且不写死任何绝对路径。
//   2. Compose 插件在 afterEvaluate 中会把 executable 重置为 Gradle 自身的 JVM。
//      afterEvaluate 按注册顺序执行，本脚本的 afterEvaluate 晚于插件注册，
//      因此在这里赋值才能最终生效。
afterEvaluate {
    tasks.withType<JavaExec>().configureEach {
        val launcher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        javaLauncher.set(launcher)
        executable = launcher.get().metadata.installationPath
            .file("bin/java${if (System.getProperty("os.name").lowercase().contains("win")) ".exe" else ""}")
            .asFile.absolutePath
    }
}
