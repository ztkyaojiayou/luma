# Luma

一门为「看懂语言怎么跑」而设计的小语言。Java 8 实现的树遍历解释器,约 2400 行。

不是给生产环境用的。每一条规则都在回答同一个问题:**这条设计能不能让人看清语言在做什么?**

## 快速开始

```bash
./run.sh                        # 进入 REPL
./run.sh examples/fib.luma      # 执行脚本
```

`run.sh` 会自动用 JDK 8 编译,并带上 `-Xss8m`(原因见 [SPEC §10.5](SPEC.md))。

## 一段程序

```luma
func makeCounter() {
    let n = 0;
    func next() {
        n = n + 1;
        return n;
    }
    return next;
}

let a = makeCounter();
let b = makeCounter();

print a();   // 1
print a();   // 2
print b();   // 1  ← 两个独立的闭包
```

## 语言一览

| | |
|---|---|
| 类型 | 动态类型,运行期检查 |
| 值 | 数字(double)、文本、布尔、null、数组、函数 |
| 关键字 | 11 个:`let func if else while for return print true false null` |
| 控制流 | `if` / `while` / `for`(语法糖) |
| 函数 | 一等公民,闭包,递归 |
| 数组 | `[1, 2, 3]`、`a[i]`、`a.length`、`push(a, x)`,**引用语义** |
| 内建 | `print`(语句)、`clock()`、`push(a, x)` |

图灵完备(有 `while` + 可变状态 + 分支)。

## 文档

| 文档 | 是什么 | 给谁 |
|---|---|---|
| **[MANUAL.md](MANUAL.md)** | 学习手册,18 章 + 8 附录 | **从这里开始**。从零教你写,再带你读懂源码 |
| **[SPEC.md](SPEC.md)** | 语言设计规范 | 查条款、查精确规则、做设计决策 |

学习手册有三条读法:只想会用(第 1–10 章)、想懂原理(第 1–15 章)、想动手改(全部)。

如果只读两节:**§零 三十秒读懂** 和 **第 11 章 跟着一行代码走完全程**。

## 结构

```
src/main/java/luma/
  Scanner.java       字符流 → Token 流
  Token.java         词法单元
  Parser.java        递归下降 → AST
  Expr.java          12 个表达式节点 + Visitor
  Stmt.java          8 个语句节点 + Visitor
  Interpreter.java   树遍历求值
  Environment.java   作用域链(闭包就藏在这)
  LumaFunction.java  函数对象 + 捕获的环境
  LumaArray.java     数组
  Luma.java          main + REPL + 错误报告
examples/            五段验收程序
```

## 想自己造一门语言?从这里开始

**[`demo/Calc.java`](demo/Calc.java)** —— 用 **Java** 写的 107 行计算器,支持 `+ - * /` 和括号。

> **注意:它是 Java,不是 Luma。**
> 这是在**造**一门语言,不是在**用**一门语言——造语言必须用一个已经存在的语言当宿主。
> Luma 自己也做不到:它没有字符串下标,连"看第一个字符"都不可能,
> 所以没法扫描 `"1 + 2 * 3"` 这样的输入。

```bash
javac -encoding UTF-8 -d demo-out demo/Calc.java
java -cp demo-out Calc "1 + 2 * (3 - 1)"      # 5
```

它是[学习手册](MANUAL.md)结语里那个「第一晚」的练习,也是**最小可行的起点**。

关键差别:它**不建语法树**,边解析边求值——这是能压到一百行的原因。
Luma 之所以要建树,是因为类型检查、优化、错误恢复都需要先有个完整结构。

**从这个计算器往上加变量、加 `if`、加函数,就长成 Luma 了。**

## 测试

```bash
./test.sh          # 全部:单元测试 + 端到端
./test.sh --unit   # 只跑单元测试(约 50ms)
```

**两层测试:**

| 层 | 测什么 | 位置 |
|---|---|---|
| **单元测试** | Scanner / Parser / Environment / Interpreter 各自的行为,191 项断言 | `src/test/java/luma/` |
| **端到端测试** | 用真正的 `./run.sh` 跑 `.luma` 文件,比对输出和退出码 | `tests/cases/` + `tests/expected/` |

零第三方依赖——测试框架本身只有 60 行(`Assert.java`),因为项目不想为了跑测试拉一个 JUnit jar 进来。

> 单元测试里有一条特别值得看:`ScannerTest` 用反射读关键字表的大小,断言它等于 11。
> 这个数字是文档里承诺过的,加一个测试就再也不会出现"文档说 16、代码里 11"这种事。

## 验收

```bash
./run.sh examples/factorial.luma   # 3628800        递归
./run.sh examples/counter.luma     # 1 2 1          闭包独立性
./run.sh examples/fib.luma         # 6765           循环
./run.sh examples/sort.luma        # [1, 2, 5, 8]   数组 + 引用语义
./run.sh examples/primes.luma      # 15 个质数       数组增长
```
