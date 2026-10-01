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
