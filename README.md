# 大智桌面日历 · Dazhi Desktop Calendar

面向**银河麒麟桌面操作系统 V10**的日历 + 待办桌面应用，基于
**Kotlin + Compose Multiplatform (Desktop)**，同时在 Windows 上开发调试。

> 早期版本名为「麒麟日历 · KylinTodo」。显示名称已改为「大智桌面日历」，
> 但**代码包名（`space.buercheng.kylintodo`）与 Linux 包名保持 `dazhi-calendar`
> 不变** —— 改包名会被系统视为另一个应用，已安装用户将无法平滑升级。

> 本仓库每次更改都会同步到 Git，方便随时回退。

## 应用图标

图标由 `scripts/generate_icon.py` **程序化生成**（无外部素材、无版权风险），
输出 512/256/128/64/48/32/16 七种尺寸的 PNG 与多尺寸 ICO 到
`src/main/resources/icon/`。

设计要素：麒麟蓝渐变圆角底、白色日期卡、顶部装订孔、右下角绿色对勾
（表达待办完成）。刻意不用中文字符做主体 —— 小尺寸下汉字会糊成一团。

重新生成（需要 Pillow）：

```bash
python scripts/generate_icon.py
```

## 功能

| 编号 | 功能 | 状态 |
| --- | --- | --- |
| F-01 | 日历视图：主界面显示日历，含农历、二十四节气、中国大陆法定节假日（含调休） | ✅ 已实现 |
| F-02 | 添加待办：点击格子右上角「+」弹出输入弹窗；空或纯空格内容禁止保存 | ✅ 已实现 |
| F-03 | 查看列表：月视图下每个日节点显示当天待办数量角标，右侧面板显示当日列表 | ✅ 已实现 |
| F-04 | 标记完成：点击复选框切换状态，完成后文字显示删除线 | ✅ 已实现 |
| F-05 | 删除待办：列表项右侧删除按钮 | ✅ 已实现 |
| F-06 | 数据持久化：SQLite 本地存储，关闭后自动保存、再次打开自动加载 | ✅ 已实现 |

视图支持**日 / 周 / 月**三种模式切换：

- **月视图**：固定 6 行 × 7 列共 42 天，从周一起始对齐
- **周视图**：显示所在自然周 7 天（周一至周日）
- **日视图**：单日详情，显示完整农历与节日信息

每个日期格子的布局：**左上角**阿拉伯数字日期（附「休」/「班」角标）、
**右上角**「+」号按钮、**右下角**农历或节气。

## 项目概况

| 项目 | 值 |
| --- | --- |
| 远程仓库 | `git@github.com:zerohk/KylinTodo.git` |
| 默认分支 | `main` |
| 包名 | `space.buercheng.kylintodo` |
| 版本 | `1.0-SNAPSHOT` |
| 主类 | `space.buercheng.kylintodo.MainKt` |
| 打包目标 | Msi（Windows）/ Deb（银河麒麟） |
| 目标环境 | 银河麒麟 V10，x86_64 与 ARM64 |

## 技术栈

- **Kotlin JVM** — `kotlin.version`（见 `gradle.properties`）
- **Compose Multiplatform** — `compose.version`（见 `gradle.properties`）
- **Material 3** — 主色适配「麒麟蓝」
- **Gradle Kotlin DSL** — `build.gradle.kts` / `settings.gradle.kts`
- **SQLite** — `org.xerial:sqlite-jdbc`，自带 Windows/Linux 的 x86_64 与 ARM64 原生库
- **农历与节假日** — `cn.6tail:lunar`（MIT，零传递依赖），
  选型依据与 API 验证见 `docs/技术选型-农历日期库.md`
- 依赖仓库：`mavenCentral()`、JetBrains Compose dev、`google()`

### 依赖库说明

| 依赖 | 用途 | 许可 |
| --- | --- | --- |
| `cn.6tail:lunar` | 农历、二十四节气、法定节假日与调休 | MIT |
| `org.xerial:sqlite-jdbc` | SQLite 持久化 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 日期时间处理 | Apache-2.0 |

节假日数据覆盖 **2001-12-29 ~ 2026-10-10**。超出范围时库返回 `null`（语义为
普通日）而不会抛异常，因此未来年份会自动降级为正常显示，不会崩溃。

## 开发

```bash
# 运行桌面应用
./gradlew run

# 运行全部测试
./gradlew test

# 构建
./gradlew build

# 打包本机安装包
./gradlew packageDistributionForCurrentOS

# 查看可用任务
./gradlew tasks
```

Windows 下把 `./gradlew` 换成 `.\gradlew.bat`。

在 IntelliJ IDEA 中也可以直接用仓库自带的运行配置 **desktop**（`.run/desktop.run.xml`）。

### 开发期启动参数

应用支持几个便于调试的命令行参数（不影响正常使用）：

```bash
./gradlew run --args="--view=week"                        # 指定启动视图
./gradlew run --args="--view=day --date=2026-10-01"       # 指定视图与锚定日期
./gradlew run --args="--seed=买牛奶@2026-10-01"            # 插入一条待办后启动
./gradlew run --args="--widget"                           # 启动即打开桌面小窗
./gradlew run -Pprobe                                     # 输出容器实测尺寸，排查布局问题
```

> 注意：`--args` 的值含空格时需整体加引号，否则 Gradle 会把后半段当成自己的选项。
> PowerShell 示例：`gradlew.bat run "--args=--widget --seed=测试"`

## 桌面小窗（小组件）

主界面工具栏的「**桌面小窗**」按钮可打开一个常驻小窗，显示当前选中日期的待办：

- 无系统边框，顶部标题条即拖动把手（可拖到任意位置）
- 置顶显示，不会被其他窗口完全遮住
- 可直接勾选完成、删除待办，也可新增
- 与主窗口共用同一份状态与数据库，两处数据始终一致
- 右上角按钮：打开主窗口 / 关闭小窗

### 为什么不做成 UKUI 面板插件

银河麒麟 V10 使用 **UKUI** 桌面，它**没有**类似 Android AppWidget 或 Windows
桌面小组件的通用第三方接口。要真正嵌入面板，需单独编写依赖麒麟专有 API 的
panel applet（通常为 C/Python + GTK），那是一个独立项目，且无法在 Windows 上
开发验证。

因此这里采用「无边框 + 置顶 + 可拖动」的独立小窗方案，跨桌面环境（UKUI /
GNOME / KDE / Windows）通用，行为可预期。

## 构建环境要求

**JDK 17 或 21 均可编译，但打包必须用 JDK 21。** 原因：

- `packageMsi` / `packageDeb` 依赖 `jpackage`，而该工具自 **JDK 14** 才引入，JDK 11 没有
- 项目通过 `build.gradle.kts` 的 `jvmToolchain(21)` 统一编译与运行的 Java 版本，
  Gradle 会自动探测本机 JDK，无需写死路径

若 `JAVA_HOME` 指向低版本 JDK，可在**用户级** `~/.gradle/gradle.properties` 中指定：

```properties
org.gradle.java.home=/absolute/path/to/jdk-21
```

注意不要写进项目仓库的 `gradle.properties`，那会把机器相关路径提交出去。

## 打包

### Windows

```bash
gradlew.bat packageMsi
```

产出 `build/compose/binaries/main/msi/KylinTodo-1.0.0.msi`。

### 银河麒麟 / Linux（生成 .deb）

`packageDeb` 在非 Linux 系统上会被 Compose 插件**自动跳过**（显示 `SKIPPED`），
因此 `.deb` **必须在 Linux 环境构建**，Windows 上无法交叉编译：

```bash
./gradlew packageDeb
```

产出 `build/compose/binaries/main/deb/kylintodo_1.0.0_amd64.deb`。

前提条件：`dpkg-deb` 与 `fakeroot`（Kylin/Ubuntu 通常自带），以及 JDK 21。

也可以直接用仓库脚本，它带依赖自检与产物校验：

```bash
./scripts/build-deb.sh
```

#### 已验证的构建结果

在 Ubuntu 22.04 + OpenJDK 21 下实测构建通过（WSL 亦可）：

| 项目 | 值 |
| --- | --- |
| 包名 | `kylintodo_1.0.0_amd64.deb` |
| 架构 | `amd64` |
| 体积 | 98.7 MB |
| SHA256 | 见 `build/deb-output/*.sha256` |
| 安装路径 | `/opt/kylintodo/`（含 `bin/KylinTodo` 与 `.desktop` 启动项） |
| 运行时 | JBR 21，模块含 `java.sql` |

包依赖由 jpackage 自动声明（`libgl1`、`libx11-6`、`libfreetype6`、`libasound2`、
`xdg-utils` 等），在麒麟 V10 上 `sudo dpkg -i` 即可安装。

#### 在 Windows 上借助 WSL 构建

`packageDeb` 无法在 Windows 上交叉编译，但可以借助 WSL 完成真实 Linux 构建：

```bash
wsl -d Ubuntu-22.04 -u root -- apt-get install -y openjdk-21-jdk-headless
wsl -d Ubuntu-22.04 -u root -- bash -c "tr -d '\r' < /mnt/e/Kotlin/DSH-Kylin/scripts/wsl-build-deb.sh | bash"
```

产物会被复制到 `build/deb-output/`，可直接拷到麒麟机器安装。

> **行尾陷阱**：从 NTFS 工作区直接 `cp` 出来的 `gradlew` 是 CRLF 行尾，
> Linux 下会报 `/bin/sh^M: bad interpreter` 而无法执行。`.gitattributes` 里的
> `eol=lf` 只在 `git checkout` 时生效，直接复制会绕过它。
> `scripts/wsl-build-deb.sh` 已在内部用 `tr -d '\r'` 处理。

### 运行时镜像的模块依赖（重要）

Compose 插件默认只把 `java.base` / `java.desktop` / `java.logging` / `jdk.crypto.ec`
交给 `jlink` 生成运行时镜像。本项目使用 SQLite，**必须额外包含 `java.sql`**，
否则打包产物能启动 JVM，但一构造数据库仓库就抛
`NoClassDefFoundError: java/sql/DriverManager`，应用闪退。

这一点已在 `build.gradle.kts` 的 `nativeDistributions { modules(...) }` 中显式声明。

> 补充：`--strip-native-commands` 是 Compose 插件固定传给 jlink 的参数，
> 它会剥掉运行时镜像里的 `java.exe`/`javaw.exe`。这**不是**故障 ——
> jpackage 生成的启动器直接用 `runtime/lib/server/jvm.dll` 起 JVM。
> 排查此类问题时不要被“镜像里没有 java.exe”误导，应直接捕获启动器的
> stdout/stderr 看真实异常。

## 目录结构

```
DSH-Kylin/
├── .gitattributes                        # 换行规范（仓库内统一 LF）
├── .gitignore
├── .idea/                                # IDE 配置（已跟踪，换机可续用）
├── .run/desktop.run.xml                  # IDEA 运行配置
├── build.gradle.kts                      # 依赖、toolchain、打包模块声明
├── gradle.properties                     # kotlin.version / compose.version
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── scripts/
│   ├── build-deb.sh                      # 麒麟/Linux 上构建 .deb（含依赖自检）
│   └── wsl-build-deb.sh                  # 借助 WSL 在 Windows 上构建 .deb
├── docs/
│   ├── 需求分析报告.md                    # 需求基准
│   └── 技术选型-农历日期库.md             # 农历库选型依据与 API 验证
└── src/
    ├── main/kotlin/space/buercheng/kylintodo/
    │   ├── Main.kt                       # 应用入口（含启动参数解析）
    │   ├── domain/                       # 领域模型（不依赖 UI 与具体库）
    │   │   ├── CalendarModels.kt         # 日/周/月视图模型与 42 天网格计算
    │   │   ├── LunarService.kt           # 农历 / 节气 / 节假日（含调休）
    │   │   └── TodoModels.kt             # TodoItem 与仓库接口
    │   ├── data/                         # 持久化
    │   │   ├── AppPaths.kt               # 跨平台数据目录（Linux 遵循 XDG）
    │   │   └── SqliteTodoRepository.kt   # SQLite 实现
    │   └── ui/                           # Compose 界面
    │       ├── KylinTheme.kt             # Material 3 主题（麒麟蓝）
    │       ├── CalendarScreen.kt         # 主界面骨架
    │       ├── CalendarGrid.kt           # 日/周/月网格与日详情头
    │       ├── CalendarCell.kt           # 单个格子
    │       ├── ClickGestures.kt          # 单击/双击组合手势
    │       ├── TodoList.kt               # 待办列表
    │       ├── AddTodoDialog.kt          # 添加待办弹窗
    │       ├── DayInfoDialog.kt          # 日期详情弹窗（双击日期触发）
    │       ├── DesktopWidgetScreen.kt    # 桌面小窗界面
    │       ├── WindowDrag.kt             # 无边框窗口拖动
    │       └── AppViewModel.kt           # 状态与业务编排
    └── test/kotlin/space/buercheng/kylintodo/
        ├── domain/                       # 网格、农历、模型测试
        ├── data/                         # 仓库与路径测试
        ├── ui/                           # ViewModel 测试（内存仓库）
        └── integration/                  # 持久化集成测试（真实 SQLite）
```

## 数据存储位置

| 平台 | 路径 |
| --- | --- |
| 银河麒麟 / Linux | `$XDG_DATA_HOME/KylinTodo/kylintodo.db`，未设置时回落 `~/.local/share/KylinTodo/` |
| Windows | `%LOCALAPPDATA%\KylinTodo\kylintodo.db` |

## 版本记录与回退

```bash
# 查看历史（含图形化分支）
git log --oneline --graph --decorate

# 提交本次更改
git add -A
git commit -m "feat: 描述本次更改"
git push

# 撤销未提交的更改
git restore .

# 回退到某个提交（保留更改在工作区，可继续修改）
git reset --soft <commit-hash>

# 回退到某个提交（丢弃其后的所有更改）
git reset --hard <commit-hash>

# 安全回滚：生成一个反向提交，不改写已有历史
git revert <commit-hash>

# 误删文件恢复
git restore <file>
```

### 提交信息约定

| 前缀 | 用途 |
| --- | --- |
| `feat:` | 新功能 |
| `fix:` | 修复缺陷 |
| `refactor:` | 重构，行为不变 |
| `style:` | 格式、空白调整 |
| `docs:` | 文档 |
| `chore:` | 构建、依赖、配置 |
| `test:` | 测试 |
