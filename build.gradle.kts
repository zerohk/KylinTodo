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

    // Material 3：需求 4.3 要求采用 Material 3 设计规范
    implementation(compose.material3)
    // Material 3 桌面端需要显式补充 icons，Compose 1.7 起不再随 material3 传递
    implementation(compose.materialIconsExtended)

    // 日期时间处理（避免直接依赖易错的 java.util.Calendar 做农历换算）
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

    // SQLite 持久化：需求 5.2 指定 SQLite。
    // xerial sqlite-jdbc 自带多平台原生库，覆盖 Windows/Linux 的 x86_64 与 ARM64，
    // 满足需求 2.2 中麒麟系统双架构的目标运行环境。
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")

    // 农历 / 二十四节气 / 中国大陆法定节假日（含调休）。
    // 注意坐标是 cn.6tail:lunar（Maven Central），不是 JitPack 的
    // com.github.6tail:lunar-java。MIT 许可，零传递依赖。
    // 选型依据见 docs/技术选型-农历日期库.md
    // 节假日数据覆盖 2001-12-29 ~ 2026-10-10，越界返回 null 而不抛异常。
    implementation("cn.6tail:lunar:1.7.7")

    testImplementation(kotlin("test"))

    // Compose 桌面端 UI 测试支持（无头运行，不需要真实鼠标输入）。
    // 用途：验证弹窗、输入校验等交互逻辑 —— 这些路径无法用合成鼠标事件
    // 可靠驱动，但可以通过 Compose 的测试框架直接操作语义树。
    // 该库被标注为实验性，需要显式 opt-in。
    //
    // createComposeRule() 返回的是 JUnit4 的 TestRule，因此必须同时提供
    // JUnit Vintage 引擎，否则 useJUnitPlatform() 只会发现 Jupiter 测试，
    // 表现为 "No tests found for given includes"。
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.desktop.uiTestJUnit4)
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.10.1")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "space.buercheng.kylintodo.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "KylinTodo"
            packageVersion = "1.0.0"

            // 显式声明运行时镜像需要的 JDK 模块。
            //
            // 这是必需的，不是可选优化：Compose 插件默认只把
            // java.base / java.desktop / java.logging / jdk.crypto.ec 交给 jlink，
            // 而 org.xerial:sqlite-jdbc 依赖 java.sql.DriverManager。
            // 缺少 java.sql 时打包产物能启动 JVM，但一构造仓库就抛
            // NoClassDefFoundError: java/sql/DriverManager，应用直接崩溃。
            //
            // 实测：未声明时运行时镜像为
            //   java.base java.desktop java.logging jdk.crypto.ec
            // 声明后为
            //   java.base java.datatransfer java.xml java.prefs java.desktop
            //   java.logging java.management java.security.sasl java.naming
            //   java.transaction.xa java.sql jdk.crypto.ec
            //
            // modules() 会替换默认列表，但插件仍会先注入它自己的默认模块，
            // 因此参数里基础模块会出现两次 —— 重复无害，jlink 会去重。
            modules(
                "java.sql",            // sqlite-jdbc 需要 DriverManager
                "java.transaction.xa", // java.sql 的 XA 事务依赖
                "java.naming",
                "java.management",
                "java.xml",
            )
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

    // 布局探针：gradlew run -Pprobe 时让应用输出容器实测尺寸，便于排查 UI 布局问题。
    // 必须在 afterEvaluate 中查找 —— Compose 插件此时才注册 :run 任务。
    if (project.hasProperty("probe")) {
        tasks.named<JavaExec>("run") {
            jvmArgs("-Dkylintodo.probe=true")
        }
    }
}
