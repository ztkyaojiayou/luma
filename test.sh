#!/usr/bin/env bash
#
# 运行全部测试。
#
# 两层:
#   1. 单元测试 —— 直接测 Scanner / Parser / Environment / Interpreter
#   2. 端到端测试 —— 真的用 ./run.sh 跑 .luma 文件,比对输出和退出码
#
# 用法:
#   ./test.sh          跑全部
#   ./test.sh --unit   只跑单元测试(快)

set -e

JAVA8=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ ! -x "$JAVA8/bin/javac" ]; then
    echo "找不到 JDK 8: $JAVA8" >&2
    exit 64
fi

echo "编译主程序..."
rm -rf "$ROOT/out" && mkdir -p "$ROOT/out"
"$JAVA8/bin/javac" -encoding UTF-8 -d "$ROOT/out" \
    $(find "$ROOT/src/main/java" -name '*.java')

echo "编译测试..."
rm -rf "$ROOT/out-test" && mkdir -p "$ROOT/out-test"
"$JAVA8/bin/javac" -encoding UTF-8 -cp "$ROOT/out" -d "$ROOT/out-test" \
    $(find "$ROOT/src/test/java" -name '*.java')

echo "运行单元测试..."
"$JAVA8/bin/java" -Xss8m -cp "$ROOT/out:$ROOT/out-test" luma.TestMain
UNIT=$?

if [ "$1" = "--unit" ]; then
    exit $UNIT
fi

echo ""
echo "运行端到端测试..."
"$ROOT/tests/run-golden.sh"
GOLDEN=$?

# ------------------------------------------------------------------
# demo/Calc.java 是手册结语里「第一晚」的参考实现。
# 手册里既然说它"能跑",就得有人看着它一直能跑。
# ------------------------------------------------------------------
echo ""
echo "检查 demo/Calc.java..."
DEMO=0
rm -rf "$ROOT/demo-out" && mkdir -p "$ROOT/demo-out"
"$JAVA8/bin/javac" -encoding UTF-8 -d "$ROOT/demo-out" "$ROOT/demo/Calc.java"

check_calc() {
    expected="$1"
    expression="$2"
    actual=$("$JAVA8/bin/java" -cp "$ROOT/demo-out" Calc "$expression" 2>&1)
    if [ "$actual" != "$expected" ]; then
        echo "  ✗ Calc \"$expression\"  期望 [$expected] 实际 [$actual]"
        DEMO=1
    fi
}

check_calc "7"   "1 + 2 * 3"
check_calc "9"   "(1 + 2) * 3"
check_calc "5"   "1 + 2 * (3 - 1)"
check_calc "2.5" "10 / 4"
check_calc "26"  "2 * 3 + 4 * 5"
check_calc "21"  "((1 + 2) * (3 + 4))"
check_calc "50"  "100 - 20 - 30"

if [ $DEMO -ne 0 ]; then
    echo "  demo 有 $DEMO 项不符"
else
    echo "  demo 7 个表达式全部正确"
fi

if [ $UNIT -ne 0 ] || [ $GOLDEN -ne 0 ] || [ $DEMO -ne 0 ]; then
    echo ""
    echo "✗ 有测试未通过"
    exit 1
fi

echo ""
echo "✓ 全部通过"
