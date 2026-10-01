# KylinTodo

基于 **Kotlin + Compose Multiplatform (Desktop)** 的待办事项应用。

> 本仓库每次更改都会同步到 Git，方便随时回退。

## 项目概况

| 项目 | 值 |
| --- | --- |
| 本地路径 | `E:\Kotlin\DSH-Kylin` |
| 远程仓库 | `git@github.com:zerohk/KylinTodo.git` |
| 默认分支 | `main` |
| 包名 | `space.buercheng.kylintodo` |
| 版本 | `1.0-SNAPSHOT` |
| 主类 | `MainKt` |
| 打包目标 | Dmg / Msi / Deb |

## 技术栈

- **Kotlin JVM** — `kotlin.version`（见 `gradle.properties`）
- **Compose Multiplatform** — `compose.version`（见 `gradle.properties`）
- **Gradle Kotlin DSL** — `build.gradle.kts` / `settings.gradle.kts`
- 依赖仓库：`mavenCentral()`、JetBrains Compose dev、`google()`

## 开发

```bash
# 运行桌面应用
./gradlew run

# 构建
./gradlew build

# 打包本机安装包
./gradlew packageDistributionForCurrentOS

# 查看可用任务
./gradlew tasks
```

Windows 下把 `./gradlew` 换成 `.\gradlew.bat`。

在 IntelliJ IDEA 中也可以直接用仓库自带的运行配置 **desktop**（`.run/desktop.run.xml`）。

## 目录结构

```
DSH-Kylin/
├── .gitattributes            # 换行规范（仓库内统一 LF）
├── .gitignore
├── .idea/                    # IDE 配置（已跟踪，换机可续用）
├── .run/desktop.run.xml      # IDEA 运行配置
├── build.gradle.kts
├── gradle.properties         # kotlin.version / compose.version
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── src/main/kotlin/Main.kt   # 应用入口
```

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
