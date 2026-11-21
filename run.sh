#!/usr/bin/env bash
#
# Luma 编译 + 运行脚本。
#
# 两件事必须在这里做,不能让用户自己记:
#   1. 用 JDK 8 —— 本机默认的 java 是 21,直接跑会用到 Java 8 没有的特性
#   2. -Xss8m  —— 每层 Luma 调用要消耗约 6 个 Java 栈帧,默认栈只够 700 层左右。
#                  加到 8m,SPEC 规定的 1000 层递归上限才真正生效,
#                  无限递归才会得到「调用层数过深」的中文报错,而不是 JVM 的 StackOverflowError
#
# 用法:
#   ./run.sh                      进入 REPL
#   ./run.sh examples/fib.luma    执行脚本

set -e

JAVA8=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home
ROOT="$(cd "$(dirname "$0")" && pwd)"

if [ ! -x "$JAVA8/bin/javac" ]; then
    echo "找不到 JDK 8: $JAVA8" >&2
    echo "请修改本脚本顶部的 JAVA8 路径" >&2
    exit 64
fi

# 源码比 out/ 新(或 out/ 不存在)时才重新编译
if [ ! -d "$ROOT/out" ] || [ -n "$(find "$ROOT/src/main/java" -name '*.java' -newer "$ROOT/out" -print -quit)" ]; then
    echo "编译中..." >&2
    rm -rf "$ROOT/out"
    mkdir -p "$ROOT/out"
    "$JAVA8/bin/javac" -encoding UTF-8 -d "$ROOT/out" \
        $(find "$ROOT/src/main/java" -name '*.java')
fi

exec "$JAVA8/bin/java" -Xss8m -cp "$ROOT/out" luma.Luma "$@"
