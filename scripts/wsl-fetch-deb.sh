#!/usr/bin/env bash
# 把 WSL 内构建好的 .deb 复制回 Windows 工作区并输出校验信息。
# 通过 stdin 管道执行（tr -d '\r' | bash），避免 Windows 侧引号/变量展开干扰。

DEB=/root/kylintodo-deb/build/compose/binaries/main/deb/kylintodo_1.0.0_amd64.deb
DEST=/mnt/e/Kotlin/DSH-Kylin/build/deb-output

if [ ! -f "$DEB" ]; then
    echo "!! 未找到 .deb: $DEB"
    exit 1
fi

mkdir -p "$DEST"
cp -f "$DEB" "$DEST/"

echo "=== 产物 ==="
ls -l "$DEST/"

echo ""
echo "=== SHA256 ==="
sha256sum "$DEST/kylintodo_1.0.0_amd64.deb" | tee "$DEST/kylintodo_1.0.0_amd64.deb.sha256"

echo ""
echo "=== 包信息 ==="
dpkg-deb --info "$DEB" 2>/dev/null | grep -E 'Package|Version|Architecture|Installed-Size'

echo ""
echo "=== 运行时 Java 与模块（应含 java.sql）==="
TMP=$(mktemp -d)
dpkg-deb --extract "$DEB" "$TMP" 2>/dev/null
REL=$(find "$TMP" -name release -path '*runtime*' -print -quit)
[ -n "$REL" ] && grep -E '^(JAVA_VERSION|MODULES)' "$REL"
rm -rf "$TMP"

echo ""
echo "=== 完成 ==="
echo "Windows 路径: E:\\Kotlin\\DSH-Kylin\\build\\deb-output\\kylintodo_1.0.0_amd64.deb"
