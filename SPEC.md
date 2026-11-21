# Luma 语言设计规范 v0.3

> 一门为「看懂语言怎么跑」而设计的小语言。
> 实现语言:**Java 8**(`/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk`)

---

## 一、定位

**Luma 不是给生产环境用的。** 它的每一条规则都优先回答一个问题:

> 「这条设计能不能让人看清语言在做什么?」

由此推出三条不可让渡的原则:

| 原则 | 含义 | 反例(我们不做) |
|---|---|---|
| **机制可见** | 不引入用户看不见的转换。语法糖必须有据可查 | 不做隐式类型转换、不做运算符重载 |
| **报错解释原因** | 错误信息回答「为什么错」,而不只是「错在哪」 | 不只报 `SyntaxError`,要报 `第 3 行: if 的条件后面缺少 ')'` |
| **错误早失败** | 宁可当场报错,不静默产生怪值 | 除零不返回 Infinity,直接报错 |

> **唯一的例外是数组的引用语义**(§5.6)。它无法"可见化"——任何语言都做不到。
> 所以 Luma 的做法是:**把它变成教材,而不是藏起来**。

---

## 二、为什么用 Java 8

这不是将就,是一个和「教学优先」一致的选择。

| Java 21 的写法 | Java 8 的写法 | 教学上的差别 |
|---|---|---|
| `sealed interface` + `record` | `abstract class` + 静态内部类 | 看得见「一个 AST 节点就是一个类」 |
| `switch` 模式匹配 | **Visitor 模式** | 看得见「动态分派」这个机制 |
| 编译器帮你检查穷尽性 | 自己维护 `Visitor<R>` 接口 | 加一个节点你就知道要改几处 |

**关键理由:** 用 Java 21,`switch (expr)` 会把「怎么根据节点类型跳到对应处理逻辑」这件事藏进语言特性里。
用 Java 8,你必须手写 `accept(Visitor)` —— **那一刻你才真正明白解释器是怎么按节点类型分派的**。

副产品:`Crafting Interpreters` 的 jlox 就是 Java 8 风格,可以逐行对照。

**代价(必须承认):** 代码更啰嗦。总计约 1300 行,其中真正有逻辑的约 650 行,其余是样板。
加一个新 AST 节点要改 3 个地方(子类、Visitor 接口、Interpreter 实现),而不是 1 个。

**Java 8 不可用的语法(实现时注意):**

```java
// 不能用
record Point(int x, int y) {}        // 需要 Java 16
sealed interface Expr {}              // 需要 Java 17
var x = 10;                           // 需要 Java 10
List.of(1, 2, 3)                      // 需要 Java 9 → 用 Arrays.asList
str.isBlank()                         // 需要 Java 11 → 用 str.trim().isEmpty()
switch 的模式匹配                       // 需要 Java 21

// 可以用
lambda、Stream、Optional、try-with-resources、接口默认方法
```

---

## 三、类型系统

**动态类型。** 运行期检查,不写类型注解,没有类型检查器。

运行时六种值(JVM 中的表示):

| Luma 类型 | 字面量 | Java 中的表示 |
|---|---|---|
| 数字 | `42`、`3.14` | `Double` |
| 字符串 | `"你好"` | `String` |
| 布尔 | `true`、`false` | `Boolean` |
| 空 | `null` | `null` |
| 数组 | `[1, 2, 3]` | `LumaArray`(包装 `List<Object>`) |
| 函数 | 由 `fn` 定义 | `LumaFunction` |

> **设计说明:为什么数字只有 double?**
> 分成 int/float 会引入隐式转换(`1/2 = 0` 还是 `0.5`?),而隐式转换违反「机制可见」原则。
> 代价是所有数字都不精确,这对一门教学语言可以接受。值为整数的 double 打印时省略小数部分。

---

## 四、词法

```
注释      // 到行尾
标识符    [A-Za-z_][A-Za-z0-9_]*
数字      123   3.14
字符串    "任意字符,支持转义"
```

**关键字(19 个,全小写英文)**

```
let  fn  if  else  while  for  return  print
true  false  null  and  or  not
```

> 注:`and` / `or` / `not` 是真关键字,不是库函数——因为它们要支持**短路求值**,这必须由求值器实现。

**符号**

```
( ) { } [ ] , ; .
+ - * / %     ==  !=  <  <=  >  >=     =     !  &&  ||
```

---

## 五、语法

### 5.1 语句

```luma
let x = 42;                     // 变量声明(必须初始化)
x = 43;                         // 赋值
a[0] = 99;                      // 下标赋值
print "你好";                    // 打印(不要求括号)
if (x > 10) { ... } else { ... }
while (x > 0) { ... }
for (let i = 0; i < 3; i = i + 1) { ... }
fn add(a, b) { return a + b; }  // 函数定义
return x;                       // 只能在函数内
{ let y = 1; }                  // 块,引入新作用域
```

> `for` 是纯粹的语法糖,会被改写为 `while`。保留它是因为**「语法糖如何展开」本身就是很好的教学素材**。

### 5.2 表达式优先级(从低到高)

| 级别 | 运算符 | 结合性 |
|---|---|---|
| 1 | `=`(赋值/下标赋值) | 右结合 |
| 2 | `or` | 左 |
| 3 | `and` | 左 |
| 4 | `==` `!=` | 左 |
| 5 | `<` `<=` `>` `>=` | 左 |
| 6 | `+` `-` | 左 |
| 7 | `*` `/` `%` | 左 |
| 8 | `!` `-`(一元) | 右 |
| 9 | `f(args)` `a[i]` `.name` | 左 |
| 10 | 字面量、变量、`(expr)` | — |

### 5.3 数组相关语法

```luma
[1, 2, 3]          // 数组字面量(可以任意嵌套)
a[0]               // 取下标(从 0 开始)
a[i + 1]           // 下标可以是任意表达式
a.length           // 长度,属性访问只支持 length 这一个名字
```

### 5.4 分号

**必须。** 表达式语句、`let`、`return`、赋值都要分号结尾;块语句 `{ }` 和 `fn` 定义不需要。

> 理由:强制分号让「语句边界」显式,解析器不必猜。教学语言不该在 ASI 这种坑上浪费注意力。

---

## 六、语义

### 6.1 真值规则

**只有 `false` 和 `null` 为假,其他一切为真。**

```luma
if (0)     { print "真"; }   // 0 是真
if ("")    { print "真"; }   // 空字符串是真
if ([])    { print "真"; }   // 空数组是真
if (null)  { print "假"; }   // 只有这个和 false 是假
```

> **设计说明:** JavaScript 把 `0`、`""`、`NaN` 都当假,导致 `if (x)` 的行为难以预测。
> Luma 采用 Lisp/Lua 式规则:真假只跟「有没有值」有关,跟「值是什么」无关。规则一句话能说完。

### 6.2 变量与作用域

- 词法作用域,块级
- `let` 声明当前块内的新变量;内层可遮蔽外层
- **读取未声明的变量 → 运行时错误**
- **赋值未声明的变量 → 运行时错误**(不隐式创建全局变量;这点和 JS 相反)

```luma
let x = 1;
{
    let x = 2;      // 遮蔽,不是修改
    print x;        // 2
}
print x;            // 1
```

### 6.3 函数与闭包

函数是一等公民,可以赋值、传参、返回。函数在**定义时**捕获所在环境——这就是闭包。

```luma
fn makeCounter() {
    let count = 0;
    fn next() {
        count = count + 1;
        return count;
    }
    return next;
}

let c = makeCounter();
print c();   // 1
print c();   // 2
```

### 6.4 运算符语义

| 表达式 | 规则 | 类型不符时 |
|---|---|---|
| `a + b` | 两数字相加;两字符串拼接 | 运行时错误 |
| `a - b` `a * b` | 仅数字 | 运行时错误 |
| `a / b` | 仅数字,**除零报错** | 运行时错误 |
| `a % b` | 仅数字,**除零报错** | 运行时错误 |
| `a == b` | 见下 | 不报错 |
| `a < b` | 仅数字 | 运行时错误 |
| `and` `or` | **短路求值**,返回原值 | 不报错 |
| `!a` | 按 §6.1 真值规则取反 | 不报错 |

**`==` 的规则(分三种)**

- 两个数字 / 两个字符串 → 比内容
- 类型不同 → 直接 `false`,**不报错**
- **两个数组 → 比身份**(是不是同一个数组),不是比内容

> **设计说明:`==` 为什么不报错?**
> `1 == "1"` 做类型转换是 JS 的历史包袱。Luma 选择「不同类型就是不相等,且不报错」——
> 因为 `==` 的用途常常正是判断类型。
>
> **`==` 为什么对数组比身份?**
> 因为数组是引用。`[1,2] == [1,2]` 得到 `false` 是引用语义的直接推论,
> 而不是一条额外规则。要比较内容必须显式写循环——这正是「引用 vs 值」这一课的核心。

### 6.5 数字显示

```luma
print 3;        // 3      ← 整数值省略 .0
print 3.5;      // 3.5
print 1 / 2;    // 0.5
print true;     // true
print null;     // null
print "hi";     // hi     ← 字符串不加引号
print [1, "a", true];   // [1, a, true]
```

### 6.6 数组:引用语义(本语言唯一的"隐藏机制")

**数组是引用类型。赋值和传参传递的是「指向同一个数组」,不是副本。**

```luma
let a = [1, 2, 3];
let b = a;          // b 和 a 是同一个数组,不是两个
b[0] = 99;
print a[0];         // 99  ← a 也变了

fn modify(x) { x[0] = 0; }
modify(a);
print a[0];         // 0
```

**下标规则**

| 情况 | 行为 |
|---|---|
| `a[i]`,0 ≤ i < length 且 i 是整数值 | 返回元素 |
| i 不是整数值(如 `a[1.5]`) | 运行时错误 |
| i 越界(负数或 ≥ length) | 运行时错误,报出实际下标和长度 |
| 对非数组取下标 | 运行时错误 |

**`a.length`**

只有 `length` 这一个属性名,只对数组有效。对非数组访问 `.length` 报错。
对数组访问其他属性名报错——**不做通用的属性访问**(那是「类」的语言才需要的东西)。

> **设计说明:为什么必须这么设计?**
> 引用语义无法"可见化"——你没法让 `let b = a` 在语法上就长得像共享。
> 所有主流语言都选择把它写进规范,让学习者记住。
> Luma 的选择是:**在规范里用整整一节讲它,并且让 `==` 的行为替它做提示**。
> 这样「数组赋值会不会复制」就不再是一个可以忽略的细节,而是一堂必须通过的课。

### 6.7 运行时行为

- 函数递归深度上限:1000 层(超出报「递归过深」,而不是让 JVM 栈溢出崩溃)
- 表达式求值顺序:严格从左到右
- 数组字面量求值:按书写顺序求值每个元素

---

## 七、错误处理

### 7.1 三类错误

| 类型 | 时机 | 举例 | 退出码 |
|---|---|---|---|
| 词法/语法错误 | 解析期 | 少了 `;`、括号不匹配 | 65 |
| 运行时错误 | 求值期 | 未定义变量、类型不符、除零、越界 | 70 |
| 用法错误 | 启动时 | 文件不存在 | 64 |

### 7.2 报错格式(死规定)

**解析错误** —— 带源码行和插入符定位:

```
[第 3 行] 语法错误: if 的条件后面缺少 ')'
    if (x > 1 {
              ^
```

**运行时错误** —— 带调用栈:

```
[第 7 行] 运行时错误: 未定义的变量 'foo'
    在 函数 'bar' 中调用 (第 12 行)
    在 顶层 (第 20 行)
```

**越界错误** —— 必须报出具体数字:

```
[第 9 行] 运行时错误: 下标 5 超出范围(数组长度 3)
```

### 7.3 错误恢复

解析器遇到错误时**不立即退出**,而是同步到下一个语句边界继续解析。
这样一次运行能报出所有语法错误,而不是改一个跑一次。

---

## 八、明确不做的事

设计的一半是划边界。v0.3 **不做**:

| 不做 | 理由 |
|---|---|
| 类 / 对象 / 继承 | 属于第二门语言的范畴,会把篇幅从「求值机制」拉走 |
| 通用属性访问(`a.foo`) | 只支持数组的 `.length`,不做对象 |
| 字符串下标(`"abc"[0]`) | 会引出「字符串什么时候可变」的麻烦问题 |
| 模块 / import | 单文件足够教学 |
| 标准库 | 只有内建 `print` 和 `clock()` |
| 宏 / 元编程 | 复杂度爆炸 |
| 垃圾回收 | 由 JVM 代劳,但这意味着「内存管理」这一课讲不了 |
| 静态类型 | 已明确选动态 |

---

## 九、验收程序

规范是否成立,用这四段程序检验。全跑通即达标。四个文件放在 `examples/`。

**① 递归 —— 阶乘** `examples/factorial.luma`
```luma
fn fact(n) {
    if (n <= 1) { return 1; }
    return n * fact(n - 1);
}
print fact(10);   // 3628800
```

**② 闭包 —— 独立计数器** `examples/counter.luma`
```luma
fn makeCounter() {
    let n = 0;
    fn next() { n = n + 1; return n; }
    return next;
}
let a = makeCounter();
let b = makeCounter();     // 独立的闭包
print a();   // 1
print a();   // 2
print b();   // 1  ← 证明环境是各自捕获的
```

**③ 循环 —— 斐波那契** `examples/fib.luma`
```luma
fn fib(n) {
    let a = 0;
    let b = 1;
    for (let i = 0; i < n; i = i + 1) {
        let t = a + b;
        a = b;
        b = t;
    }
    return a;
}
print fib(20);   // 6765
```

**④ 数组 —— 冒泡排序(原地修改)** `examples/sort.luma`
```luma
fn sort(a) {
    let n = a.length;
    for (let i = 0; i < n; i = i + 1) {
        for (let j = 0; j < n - i - 1; j = j + 1) {
            if (a[j] > a[j + 1]) {
                let t = a[j];
                a[j] = a[j + 1];
                a[j + 1] = t;
            }
        }
    }
}

let data = [5, 2, 8, 1];
sort(data);             // 原地排序,不返回新数组
print data;             // [1, 2, 5, 8]
```

> 注意 ④ 同时验证了两件事:下标赋值能写、**引用语义让函数能原地修改调用者的数组**。

---

## 十、实现架构(Java 8)

```
luma/
  SPEC.md                      本文件
  pom.xml                      Maven 配置,source/target = 1.8
  src/main/java/luma/
    TokenType.java             枚举:关键字、符号、字面量类型     ~40 行
    Token.java                 final 字段 + toString            ~30 行
    Scanner.java               字符流 → Token 流                ~180 行
    Expr.java                  abstract + 12 个静态内部类 + Visitor  ~150 行
    Stmt.java                  abstract + 8 个静态内部类 + Visitor   ~110 行
    Parser.java                递归下降 → AST                   ~280 行
    Environment.java           作用域链                          ~50 行
    Interpreter.java           实现两个 Visitor,树遍历求值         ~320 行
    LumaFunction.java          闭包:参数 + 函数体 + 捕获的环境      ~40 行
    LumaArray.java             数组:包装 List<Object>            ~35 行
    RuntimeError.java          带行号 + 调用栈的运行时错误          ~25 行
    Luma.java                  main + REPL + 错误报告             ~120 行
  examples/                    四段验收程序
```

合计约 **1300 行**,其中用户逻辑约 650 行,其余是 Java 8 的样板。

### 10.1 AST 的写法(Visitor 模式)

```java
public abstract class Expr {
    public interface Visitor<R> {
        R visitBinaryExpr(Binary expr);
        R visitLiteralExpr(Literal expr);
        // ... 每个节点一个方法
    }
    public abstract <R> R accept(Visitor<R> visitor);

    public static class Binary extends Expr {
        public final Expr left;
        public final Token operator;
        public final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left = left; this.operator = operator; this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }
    }
}
```

**加了节点要改 3 处**:`Expr` 里加内部类、`Visitor` 接口加方法、`Interpreter` 里实现方法。
Java 21 的 `sealed` 能让编译器提醒你漏了哪处,Java 8 不会——这是要付的代价。

### 10.2 节点清单

**Expr(12 个)**

| 节点 | 对应语法 |
|---|---|
| `Literal` | `42` `"hi"` `true` `null` |
| `Grouping` | `( expr )` |
| `Unary` | `!a` `-a` |
| `Binary` | `a + b` 及所有二元运算 |
| `Logical` | `a and b` `a or b`(短路,和 Binary 分开) |
| `Variable` | `x` |
| `Assign` | `x = v` |
| `Call` | `f(a, b)` |
| `ArrayLiteral` | `[1, 2, 3]` |
| `Index` | `a[i]` |
| `IndexSet` | `a[i] = v` |
| `Get` | `a.length` |

**Stmt(8 个)**

| 节点 | 对应语法 |
|---|---|
| `Expression` | `expr;` |
| `Print` | `print expr;` |
| `Var` | `let x = expr;` |
| `Block` | `{ ... }` |
| `If` | `if (c) s else s` |
| `While` | `while (c) s` |
| `Function` | `fn name(a, b) { ... }` |
| `Return` | `return expr;` |

> `for` 不进 AST —— 它在解析时直接展开成 `Block { Var; While { ...; Sequence } }`。
> 「语法糖在解析期消失」这件事,就是语法糖的定义。

### 10.3 数据流

```
源码 ──Scanner──> Token 流 ──Parser──> AST ──Interpreter──> 结果
                                            │
                                      Environment(作用域链)
```

### 10.4 关键技术点(按实现顺序)

1. **Scanner**:字符游标 + 双字符前瞻(`==` / `!=` / `<=` / `>=`)
2. **Parser**:每个语法规则一个方法,优先级由调用层级体现
3. **AST**:`abstract class` + 静态内部类 + `Visitor<R>`
4. **Interpreter**:同时实现 `Expr.Visitor<Object>` 和 `Stmt.Visitor<Void>`
5. **Environment**:父指针链,`get` 递归向上找;`HashMap<String, Object>`
6. **闭包**:`LumaFunction` 持有 `closure` 字段,指向定义时的 `Environment`——这一行就是闭包的全部秘密
7. **错误恢复**:`synchronize()` 跳到下一个 `;` 或语句开头
8. **下标赋值**:赋值解析时看左值是什么——`Variable` 生成 `Assign`,`Index` 生成 `IndexSet`

### 10.5 构建与运行(用 JDK 8)

```bash
JAVA8=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home

# 编译
$JAVA8/bin/javac -encoding UTF-8 -d out \
    $(find src/main/java -name '*.java')

# 运行脚本
$JAVA8/bin/java -cp out luma.Luma examples/factorial.luma

# REPL
$JAVA8/bin/java -cp out luma.Luma
```

`pom.xml` 里的 `source`/`target` 固定为 `1.8`,IDEA 打开时会按 Java 8 编译。

### 10.6 编码约定(已实测)

实测环境:`JDK 1.8.0_181` + `LANG=zh_CN.UTF-8` → `file.encoding = UTF-8`,中文读写正常。

**但不能依赖默认值。** Java 8 的 `file.encoding` 取自 `LANG`,一旦换了运行环境(IDE 内置终端、
launchd、cron、别的机器)就可能回退到非 UTF-8,中文全部变乱码。所以实现时必须显式指定:

```java
// 读源文件:永远显式指定 UTF-8
new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)

// 编译
// javac -encoding UTF-8 ...
```

**IDEA 里的设置**:Settings → Editor → File Encodings,把 Global / Project / Properties 三项都设为 UTF-8。

> 这条约定同样服务于「机制可见」原则——**编码不能是个看不见的隐式依赖**。

---

## 十一、版本计划

| 版本 | 内容 | 状态 |
|---|---|---|
| v0.3 | 当前规范:六种值、函数闭包、数组、完整错误报告、Java 8 约束 | ✅ 设计完成 |
| v1.0 | Java 8 实现 + 四段验收程序跑通 | ⬜ 待实现 |
| 之后 | 类与对象 / 字符串下标 / 静态类型检查 | 视兴趣 |

**语言名: Luma**(文件后缀 `.luma`)
