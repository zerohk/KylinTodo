#!/usr/bin/env bash
#
# 在 WSL（Linux）内构建并校验 .deb 安装包。
#
# 用途：Compose Desktop 的 packageDeb 任务在非 Linux 系统上会被插件自动
# 跳过（SKIPPED），无法在 Windows 上交叉编译。本脚本借助 WSL 完成真实
# 的 Linux 构建，并把产物取回 Windows 工作区，便于在麒麟物理机/虚拟机上安装。
#
# Windows 侧调用方式（注意用 tr 去掉 CRLF，否则 bash 无法解析）：
#   wsl -d Ubuntu-22.04 -u root -- bash -c "tr -d '\r' < /mnt/e/Kotlin/DSH-Kylin/scripts/wsl-build-deb.sh | bash"
#
# 依赖：WSL 内已安装 JDK 17+（建议 21）与 dpkg-deb/fakeroot。
#   wsl -d Ubuntu-22.04 -u root -- apt-get install -y openjdk-21-jdk-headless

set -u

SRC=/mnt/e/Kotlin/DSH-Kylin
B=/root/kylintodo-deb
DEST="$SRC/build/deb-output"

echo "=== 1. 准备构建目录 ==="
rm -rf "$B"
mkdir -p "$B"
cp "$SRC/build.gradle.kts" "$SRC/settings.gradle.kts" "$SRC/gradle.properties" "$SRC/gradlew" "$B/"
cp -r "$SRC/gradle" "$SRC/src" "$B/"
[ -d "$SRC/scripts" ] && cp -r "$SRC/scripts" "$B/"

# 关键坑：NTFS 上的 gradlew 是 CRLF 行尾，Linux 下会报
# "/bin/sh^M: bad interpreter: No such file or directory" 而无法执行。
# 必须在 WSL 内转成 LF —— .gitattributes 的 eol=lf 只作用于 git checkout，
# 直接 cp 会绕过它。
tr -d '\r' < "$B/gradlew" > "$B/gradlew.lf" && mv "$B/gradlew.lf" "$B/gradlew"
chmod +x "$B/gradlew"
for f in "$B"/scripts/*.sh; do
    [ -f "$f" ] || continue
    tr -d '\r' < "$f" > "$f.lf" && mv "$f.lf" "$f"
    chmod +x "$f"
done
echo "    gradlew: $(file -b "$B/gradlew")"
echo "    java   : $(java -version 2>&1 | head -1)"

echo ""
echo "=== 2. 构建 .deb ==="
cd "$B" || exit 1
# 注意：这里不用管道接 tail —— 实测管道会让 WSL 进程在构建完成后挂住不退出。
# 输出直接打印，由调用方（PowerShell）自行截取尾部。
./gradlew --no-daemon clean packageDeb --console=plain

echo ""
echo "=== 3. 校验产物 ==="
DEB=$(find "$B/build/compose/binaries/main/deb" -name '*.deb' -print -quit 2>/dev/null)
if [ -z "$DEB" ]; then
    echo "!! 未找到 .deb，构建失败"
    exit 1
fi
ls -lh "$DEB"
echo ""
echo "--- 包元信息 ---"
dpkg-deb --info "$DEB" 2>/dev/null | grep -E "Package|Version|Architecture|Installed-Size"
echo ""
echo "--- 运行时 Java 版本与模块（应包含 java.sql）---"
TMP=$(mktemp -d)
dpkg-deb --extract "$DEB" "$TMP" 2>/dev/null
REL=$(find "$TMP" -name release -path '*runtime*' -print -quit)
[ -n "$REL" ] && grep -E "^(JAVA_VERSION|MODULES)" "$REL"
rm -rf "$TMP"

echo ""
echo "=== 4. 取回 Windows 工作区 ==="
mkdir -p "$DEST"
cp -f "$DEB" "$DEST/"
echo "    已复制到: E:\\Kotlin\\DSH-Kylin\\build\\deb-output\\$(basename "$DEB")"
sha256sum "$DEST/$(basename "$DEB")" | tee "$DEST/$(basename "$DEB").sha256"
echo ""
echo "=== 完成 ==="
echo "在麒麟系统上安装:"
echo "  sudo dpkg -r kylintodo        # 若装过 1.0.0（旧包名），先卸掉"
echo "  sudo dpkg -i $(basename "$DEB")"
echo "  sudo apt-get install -f       # 补齐依赖"
echo ""
echo "注意：1.0.0 的包名是 kylintodo，1.1.0 起改为 dazhi-calendar。"
echo "dpkg 不视为升级，需先卸载旧包；用户数据在 ~/.local/share/KylinTodo，"
echo "卸载不会删除，重装后仍可读回。"
