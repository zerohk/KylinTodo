#!/usr/bin/env bash
# 把 WSL 内构建好的 .deb 复制回 Windows 工作区，并输出校验信息。
#
# 通过 stdin 管道执行（tr -d '\r' | bash），避免 Windows 侧引号与变量展开干扰。
# 与 wsl-build-deb.sh 分开的原因：构建脚本在收尾阶段会因 WSL 内残留的
# Gradle 守护进程而挂住，而产物本身是好的；本脚本只做校验与回传，
# 可重复执行且不会挂住。

DEB=/root/kylintodo-deb/build/compose/binaries/main/deb/dazhi-calendar_1.1.2_amd64.deb
DEST=/mnt/e/Kotlin/DSH-Kylin/build/deb-output

if [ ! -f "$DEB" ]; then
    echo "!! 未找到 .deb: $DEB"
    exit 1
fi

mkdir -p "$DEST"
cp -f "$DEB" "$DEST/"

echo "=== 产物 ==="
ls -l "$DEST/dazhi-calendar_1.1.2_amd64.deb"

echo ""
echo "=== 包信息 ==="
dpkg-deb --info "$DEB" 2>/dev/null | grep -E 'Package|Version|Architecture|Installed-Size|Maintainer'

TMP=$(mktemp -d)
dpkg-deb --extract "$DEB" "$TMP" 2>/dev/null

echo ""
echo "=== desktop 项（应用显示名与图标）==="
find "$TMP" -name '*.desktop' -exec cat {} \; 2>/dev/null | head -20

echo ""
echo "=== 图标文件 ==="
find "$TMP" \( -name '*.png' -o -name '*.ico' \) 2>/dev/null | head -8

echo ""
echo "=== 运行时 Java 与模块（必须含 java.sql，否则一构造仓库就崩）==="
REL=$(find "$TMP" -name release -path '*runtime*' -print -quit)
if [ -n "$REL" ]; then
    grep -E '^(JAVA_VERSION|MODULES)' "$REL"
else
    echo "!! 未找到 runtime/release"
fi

rm -rf "$TMP"

echo ""
echo "=== SHA256 ==="
sha256sum "$DEST/dazhi-calendar_1.1.2_amd64.deb" | tee "$DEST/dazhi-calendar_1.1.2_amd64.deb.sha256"

echo ""
echo "=== 完成 ==="
echo "Windows 路径: E:\\Kotlin\\DSH-Kylin\\build\\deb-output\\dazhi-calendar_1.1.2_amd64.deb"
