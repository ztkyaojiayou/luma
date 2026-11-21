#!/usr/bin/env bash
#
# 端到端测试:用真实的 ./run.sh 跑每个 tests/cases/*.luma,
# 比对标准输出、标准错误、退出码。
#
# 为什么不和单元测试放一起?因为单元测试测的是"组件对不对",
# 这里测的是"整条命令行走下来对不对"——包括读文件、UTF-8、报错格式、退出码。
# 两者都会坏,而且坏的原因不同。
#
# 期望文件(都在 tests/expected/ 下):
#   <name>.out    标准输出(必需)
#   <name>.code   退出码(可选,默认 0)
#   <name>.err    标准错误(可选;没有这个文件就跳过 stderr 比对)

set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CASES="$ROOT/tests/cases"
EXPECTED="$ROOT/tests/expected"

pass=0
fail=0
failed_names=""

# 统一去掉行尾换行,避免"文件末尾有没有 \n"这种无关差异造成误报
normalize() {
    printf '%s' "$1"
}

for case_file in "$CASES"/*.luma; do
    name=$(basename "$case_file" .luma)
    expected_out_file="$EXPECTED/$name.out"
    expected_code_file="$EXPECTED/$name.code"
    expected_err_file="$EXPECTED/$name.err"

    if [ ! -f "$expected_out_file" ]; then
        echo "  ⚠ 缺少期望文件: tests/expected/$name.out"
        fail=$((fail + 1))
        failed_names="$failed_names $name"
        continue
    fi

    expected_code=0
    [ -f "$expected_code_file" ] && expected_code=$(cat "$expected_code_file")

    out_tmp=$(mktemp)
    err_tmp=$(mktemp)
    "$ROOT/run.sh" "$case_file" >"$out_tmp" 2>"$err_tmp"
    actual_code=$?
    actual_out=$(cat "$out_tmp")
    actual_err=$(cat "$err_tmp")
    rm -f "$out_tmp" "$err_tmp"

    problems=""

    if [ "$(normalize "$actual_out")" != "$(normalize "$(cat "$expected_out_file")")" ]; then
        problems="${problems}stdout "
    fi

    if [ "$actual_code" != "$expected_code" ]; then
        problems="${problems}退出码 "
    fi

    if [ -f "$expected_err_file" ]; then
        if [ "$(normalize "$actual_err")" != "$(normalize "$(cat "$expected_err_file")")" ]; then
            problems="${problems}stderr "
        fi
    fi

    if [ -z "$problems" ]; then
        pass=$((pass + 1))
        continue
    fi

    fail=$((fail + 1))
    failed_names="$failed_names $name"
    echo "  ✗ $name  (不符:$problems)"

    case "$problems" in
        *退出码*)
            echo "      退出码 期望 $expected_code 实际 $actual_code"
            ;;
    esac
    case "$problems" in
        *stdout*)
            echo "      stdout 差异(期望 - 实际):"
            diff <(printf '%s\n' "$(normalize "$(cat "$expected_out_file")")") \
                 <(printf '%s\n' "$(normalize "$actual_out")") | head -10 | sed 's/^/        /'
            ;;
    esac
    case "$problems" in
        *stderr*)
            echo "      stderr 差异(期望 - 实际):"
            diff <(printf '%s\n' "$(normalize "$(cat "$expected_err_file")")") \
                 <(printf '%s\n' "$(normalize "$actual_err")") | head -10 | sed 's/^/        /'
            ;;
    esac
done

echo ""
if [ $fail -eq 0 ]; then
    echo "端到端:$pass 个全部通过"
    exit 0
else
    echo "端到端:失败 $fail 个 / 共 $((pass + fail)) 个"
    echo "失败用例:$failed_names"
    exit 1
fi
