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
| 关键字 | 16 个:`let func if else while for return print true false null` |
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

## 验收

```bash
./run.sh examples/factorial.luma   # 3628800        递归
./run.sh examples/counter.luma     # 1 2 1          闭包独立性
./run.sh examples/fib.luma         # 6765           循环
./run.sh examples/sort.luma        # [1, 2, 5, 8]   数组 + 引用语义
./run.sh examples/primes.luma      # 15 个质数       数组增长
```
