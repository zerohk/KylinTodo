#!/usr/bin/env bash
#
# 在银河麒麟 / Linux 上构建 .deb 安装包。
#
# 为什么需要这个脚本：Compose Desktop 的 packageDeb 任务依赖 dpkg-deb，
# 该工具只存在于 Linux，因此在 Windows 上运行会被插件自动跳过（SKIPPED），
# 无法交叉编译。本脚本在 Linux 侧完成构建并校验产物。
#
# 用法：
#   ./scripts/build-deb.sh
#
# 前提：JDK 17+（建议 21）、dpkg-deb、fakeroot、网络可访问 Maven Central。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

echo "==> 项目目录: ${PROJECT_DIR}"

# ---------- 环境检查 ----------
fail=0

check() {
    if command -v "$1" >/dev/null 2>&1; then
        echo "    [ok] $1"
    else
        echo "    [缺失] $1  $2"
        fail=1
    fi
}

echo "==> 检查构建依赖"
check java "（请安装 JDK 17 或 21）"
check dpkg-deb "（属于 dpkg 包）"
check fakeroot "（apt install fakeroot）"

if [ "${fail}" -ne 0 ]; then
    echo "!! 缺少必要依赖，终止构建。" >&2
    exit 1
fi

JAVA_VER="$(java -version 2>&1 | head -1)"
echo "==> Java: ${JAVA_VER}"

# jpackage 自 JDK 14 起提供，打包必需；低于该版本会失败
JAVA_MAJOR="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
if [ -n "${JAVA_MAJOR}" ] && [ "${JAVA_MAJOR}" -lt 14 ] 2>/dev/null; then
    echo "!! 当前 JDK 版本为 ${JAVA_MAJOR}，打包需要 JDK 14+（建议 21）。" >&2
    echo "   可在 ~/.gradle/gradle.properties 中设置 org.gradle.java.home 指向 JDK 21。" >&2
    exit 1
fi

# ---------- 构建 ----------
echo "==> 开始构建 .deb"
./gradlew --no-daemon clean packageDeb

# ---------- 校验产物 ----------
OUT_DIR="${PROJECT_DIR}/build/compose/binaries/main/deb"
echo "==> 查找产物于 ${OUT_DIR}"

DEB_FILE="$(find "${OUT_DIR}" -maxdepth 1 -name '*.deb' -print -quit 2>/dev/null || true)"

if [ -z "${DEB_FILE}" ]; then
    echo "!! 未找到 .deb 产物，构建可能未成功。" >&2
    exit 1
fi

if ! command -v dpkg-deb >/dev/null 2>&1; then
    echo "?? 无 dpkg-deb，跳过产物校验"
else
    echo "==> 校验 deb 包信息"
    dpkg-deb --info "${DEB_FILE}" | sed 's/^/    /'
fi

echo ""
echo "==> 构建成功"
echo "    产物: ${DEB_FILE}"
echo "    大小: $(du -h "${DEB_FILE}" | cut -f1)"
echo ""
echo "    安装: sudo dpkg -i '${DEB_FILE}'"
echo "    卸载: sudo dpkg -r kylintodo"
