# Luma 语言设计规范 v0.4

> 一门为「看懂语言怎么跑」而设计的小语言。
> 实现语言:**Java 8**(`/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk`)

---

## 零、三十秒读懂

如果你只想先知道「这东西到底是个啥」，看这张表就够了。细节在后面。

| 问题 | 答案 |
|---|---|
| **这是什么** | 一门小语言 + 一个解释器,约 1400 行 Java 8 |
| **能干什么** | 排序、质数筛选、递归、闭包、动态数组 —— **图灵完备** |
| **不能干什么** | 读写文件、处理字符串、类与对象 |
| **在 Java 技术栈里是什么角色** | **≈ JVM**(负责执行),**不是 JDK**(没有编译器、没有标准库) |
| **别人怎么用** | `java -jar luma.jar 脚本.luma` |
| **要读多少** | 写程序只需读 §5 语法 + §6 语义;§10 是实现者视角,可以先跳过 |

> 四个最容易卡住的疑问(图灵完备吗 / 相当于 JDK 吗 / 标准库要不要补 / **Java 自己是用什么写的**),
> 在 **[§十二 常见疑问](#十二常见疑问)** 里集中回答,读代码前可以先看那节。

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

**代价(必须承认):** 代码更啰嗦。总计约 1380 行,其中真正有逻辑的约 700 行,其余是样板。
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
| 函数 | 由 `func` 定义 | `LumaFunction`;内建函数同为值 |

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

**关键字(16 个,全小写英文)**

```
let  func  if  else  while  for  return  print
true  false  null
```

> 注:`&&` / `||` / `!` 都是符号,风格统一,不再设 `and` / `or` / `not` 关键字。
> 但 `&&` / `||` **不是普通二元运算**——它们必须**短路求值**:
> `a && b` 在 `a` 为假时根本不会去算 `b`。这一点只能由求值器实现,库函数做不到。

**符号**

```
( ) { } [ ] , ; .
+ - * / %     ==  !=  <  <=  >  >=     =     !  &&  ||
```

> **只有一种写法。** 逻辑运算统一用符号 `&&` / `||` / `!`,不设 `and` / `or` / `not` 关键字。
> 同一个概念给两套语法,读代码的人就得同时认得两套,这违反「机制可见」。
> 也**不混搭**(关键字和符号各用一半)——那比两套并存更容易让人踩坑。

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
func add(a, b) { return a + b; }  // 函数定义
return x;                       // 只能在函数内
{ let y = 1; }                  // 块,引入新作用域
```

> `for` 是纯粹的语法糖,会被改写为 `while`。保留它是因为**「语法糖如何展开」本身就是很好的教学素材**。

### 5.2 表达式优先级(从低到高)

| 级别 | 运算符 | 结合性 |
|---|---|---|
| 1 | `=`(赋值/下标赋值) | 右结合 |
| 2 | `\|\|` | 左 |
| 3 | `&&` | 左 |
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

**必须。** 表达式语句、`let`、`return`、赋值都要分号结尾;块语句 `{ }` 和 `func` 定义不需要。

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
func makeCounter() {
    let count = 0;
    func next() {
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
| `&&` `\|\|` | **短路求值**,返回原值 | 不报错 |
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

func modify(x) { x[0] = 0; }
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

### 6.7 内建函数

只有三个。它们**和用户定义的函数是同一种东西**——都是值,都能被赋值、传递、调用。

| 内建 | 行为 | 返回 | 错误 |
|---|---|---|---|
| `print x` | 打印并换行(是语句形式,见 §5.1) | — | — |
| `clock()` | 当前时间戳(秒,带小数) | 数字 | — |
| `push(a, x)` | 向数组 `a` 末尾追加 `x` | `null` | `a` 不是数组 → 运行时错误 |

```luma
let 结果 = [];
let i = 2;
while (i < 20) {
    push(结果, i * i);
    i = i + 1;
}
print 结果;      // [4, 9, 16, 25, 36, 49, ...]
```

**为什么内建函数必须是值?**

如果 `push` 只能在 `Call` 节点里按名字特判,那它就是求值器里的一个硬编码分支——
用户看不到「调用一个函数」和「调用 `push`」走的是同一条路。这违反「机制可见」。

所以实现上有一个 `LumaCallable` 接口:

```java
public interface LumaCallable {
    int arity();                       // 参数个数,用于调用前的检查
    Object call(Interpreter interpreter, List<Object> arguments);
}
```

`LumaFunction`(用户函数)和三个内建函数都实现它。
**求值器只认这个接口,不认具体是谁**——用户函数和内建函数在调用那一刻没有区别。

> 这是本语言里「多态」唯一的用武之地,而且只用了一次。但它足以说明:
> 语言里看起来最特殊的东西(内建的 `push`),在实现里可以和用户写的函数走完全相同的代码路径。

### 6.8 运行时行为

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
| 标准库 | 只有内建 `print` / `clock()` / `push()` 三个 |
| 宏 / 元编程 | 复杂度爆炸 |
| 垃圾回收 | 由 JVM 代劳,但这意味着「内存管理」这一课讲不了 |
| 静态类型 | 已明确选动态 |

---

## 九、验收程序

规范是否成立,用这四段程序检验。全跑通即达标。四个文件放在 `examples/`。

**① 递归 —— 阶乘** `examples/factorial.luma`
```luma
func fact(n) {
    if (n <= 1) { return 1; }
    return n * fact(n - 1);
}
print fact(10);   // 3628800
```

**② 闭包 —— 独立计数器** `examples/counter.luma`
```luma
func makeCounter() {
    let n = 0;
    func next() { n = n + 1; return n; }
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
func fib(n) {
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
func sort(a) {
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

**⑤ 数组增长 —— 收集质数** `examples/primes.luma`
```luma
func isPrime(n) {
    if (n < 2) { return false; }
    let d = 2;
    while (d * d <= n) {
        if (n % d == 0) { return false; }
        d = d + 1;
    }
    return true;
}

let primes = [];
let i = 2;
while (i <= 50) {
    if (isPrime(i)) { push(primes, i); }
    i = i + 1;
}
print primes;
// [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47]
print primes.length;   // 15
```

> ⑤ 是「生成数据」类程序的样板:数组从空开始,长度在运行时决定。
> 没有 `push` 这一段写不出来——这正是它被加进来的原因。

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
    LumaCallable.java          调用接口:用户函数与内建函数共用      ~10 行
    LumaFunction.java          闭包:参数 + 函数体 + 捕获的环境      ~45 行
    LumaArray.java             数组:包装 List<Object>            ~35 行
    NativeFunctions.java       内建 clock / push                 ~40 行
    RuntimeError.java          带行号 + 调用栈的运行时错误          ~25 行
    Luma.java                  main + REPL + 错误报告             ~120 行
  examples/                    五段验收程序
```

合计约 **1380 行**,其中用户逻辑约 700 行,其余是 Java 8 的样板。

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
| `Logical` | `a && b` `a \|\| b`(短路,和 Binary 分开) |
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
| `Function` | `func name(a, b) { ... }` |
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
9. **内建函数**:`NativeFunctions` 里三个都实现 `LumaCallable`,求值器的 `Call` 分支只认接口,不认具体是谁

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
| v0.4 | 当前规范:六种值、函数闭包、数组 + `push`、完整错误报告、Java 8 约束 | ✅ 设计完成 |
| v1.0 | Java 8 实现 + 四段验收程序跑通 | ⬜ 待实现 |
| 之后 | 类与对象 / 字符串下标 / 静态类型检查 | 视兴趣 |

**语言名: Luma**(文件后缀 `.luma`)

---

## 十二、常见疑问

这一节是为了**降低理解成本**而写的:它集中回答那些「读规范之前会先冒出来的问题」。
如果你只想知道这门语言大不大、能干什么,读这一节就够了。

### 12.1 图灵完备吗?

**是。** 判定标准是四条,我们全都有:

| 条件 | Luma 里的对应物 |
|---|---|
| 可变状态 | `let` / 赋值 / `a[i] =` |
| 顺序执行 | 语句序列 |
| 条件分支 | `if / else` |
| 无界循环或递归 | `while` / `for` / 递归函数 |

理论上只要 **`while` + 变量 + 算术** 三条就够(形式语义学里叫 *while-language*,是证明图灵完备的标准最小语言)。函数、闭包、数组是「让程序好写」的附加品,不是完备性的必要条件。

**两句必须说的实话:**

1. **严格意义上的图灵完备不存在。** 任何真实语言(Java、C、Python)都是有限内存 + 有限精度
   (我们的数字是 53 位 double),实际状态有限,等同于一个超大有限状态机。
   图灵完备是理论概念。**实践含义是:有 `while`/递归 + 可变状态,就足以表达任意算法。**

2. **图灵完备 ≠ 能写好程序。** 完备只保证「理论上写得出来」,
   不保证「写得出来得不痛苦」。Luma 没有文件读写、没有字符串操作,
   所以它能写算法题,写不了实用工具。这是**刻意的边界**(见 §八)。

### 12.2 它相当于 Java 的 JDK 吗?

**不是。** 更准确的说法:Luma 解释器 ≈ **JVM 的角色**,而不是 JDK。

| Java 世界 | Luma 对应物 | 有没有 |
|---|---|---|
| Java 语言规范(JLS) | `SPEC.md`(本文件) | ✅ 有 |
| `javac` 编译器(源码 → 字节码) | — | ❌ **不需要**,直接解释语法树 |
| JVM(负责执行) | Luma 解释器 | ✅ 有 |
| 标准库(`java.util` 等) | — | ❌ **故意留空**(见 12.3) |
| **JDK**(以上全部打包 + 工具链) | — | ❌ 没有 |

**最贴切的类比其实是 Python:**

- `python3` 这个命令 ≈ 我们的 `luma` 解释器
- Python 标准库 ≈ 我们完全没有
- 所以 Luma ≈ **一个裸的 CPython 解释器,没有标准库,没有 pip**

> 真正的语言通常追求**自举**——用自己写自己(Go、Rust、Swift 都做到了)。
> Luma 做不到,因为它的语言能力不足以写一个解释器。
> 这是教学语言的常态,`Crafting Interpreters` 的 Lox 也是用 Java/C 写的。

### 12.3 标准库到底重要吗?(答案:不重要,但要分清三层)

**你的直觉是对的:标准库就是一堆常用函数的集合,有没有、有多少确实不太重要。**
但要说清为什么,得把「语言能力」和「标准库」分开看:

| 层 | 是什么 | Luma 有吗 | 能自己写出来吗 |
|---|---|---|---|
| ① **语言能力** | 语法 + 语义(能表达什么) | ✅ 有 | — |
| ② **内建原语** | 必须由宿主(Java)提供的 | ✅ `print` / `clock` / `push` | ❌ **写不出来** |
| ③ **标准库** | 用这门语言**自己写出来**的常用函数 | ❌ 故意留空 | ✅ **完全能** |

**判断标准只有一条:这东西能不能用语言自身写出来?**

- 排序、质数筛选、字符串拼接、集合操作 → **都是第 ③ 层**,
  只要语言有循环、数组、函数,就能自己造。所以标准库小一些无所谓。
- 读文件、网络、读标准输入 → **是第 ② 层**,
  必须靠宿主 Java 的 API,语言自己无论多强都造不出来。

**所以 Luma 的取舍是:**

它有函数、数组、`push`、闭包 —— 足以让你自己造出排序和集合;
但它故意不给文件 IO,因为「和操作系统打交道」跟「看懂语言怎么跑」这个目标无关。

> **一个反例帮你记住这个区分:**
> 如果一门语言连 `sort` 都写不出来(比如没有循环、没有数组),
> 那它缺的**不是标准库,是语言能力**。
> 分清这两者,是判断一门语言强弱的基本功。

### 12.4 写完之后别人怎么用?

```bash
# 前提:对方机器装了 JRE 8 或以上(这是用 Java 实现语言的代价)

mvn package                                  # 打出 target/luma-1.0.jar

java -jar luma-1.0.jar 脚本.luma              # 执行脚本
java -jar luma-1.0.jar                       # 进入 REPL
```

分发就是一个 jar 文件,几十 KB,不依赖任何第三方库。

### 12.5 Java 本身是用什么写的?(以及为什么 Luma 不能这么做)

这是个经典困惑,混淆点在于**把两样东西当成了一样**。要分开看:

| 部件 | 是什么语言 | 本机怎么验证 |
|---|---|---|
| **`javac`**(编译器) | **Java** | `java -cp $JAVA_HOME/lib/tools.jar com.sun.tools.javac.Main Hello.java` 直接能跑 |
| **JVM / HotSpot**(运行时) | **C++** | `libjvm.dylib` 链接了 `/usr/lib/libstdc++.6.dylib`(C 程序不链接它) |

**javac 本身就是一袋 Java 字节码。**
JDK 8 的 `tools.jar` 里有 **1052 个** `com/sun/tools/javac/*.class`。
你根本不需要 `javac` 这个命令——直接让 JVM 去跑那个类就行。

> **用 Java 编译 Java**,这个动作叫 **自举(bootstrapping)**。

**JVM 是原生程序。** 它是 Mach-O 可执行文件 + 动态库,链接 C++ 标准库。
它负责执行字节码,自己不可能"用 Java 写"——否则就需要一个更底层的引擎来跑它,无限递归。

**那鸡生蛋的问题怎么破的?**

第一版编译器不可能用 Java 写(那时还没有 Java),必然存在一个**起点语言**。
一旦能做到"用 Java 编译 Java",那个起点编译器就可以退休了。

这个机制今天还在起作用:编译 OpenJDK 源码**必须先准备一个旧版 JDK**,术语叫
*bootstrap JDK*。你机器上装的每个 JDK,都是这条自举链的末端。

**其他语言对照**

| 语言 | 编译器用什么写 | 运行时用什么写 |
|---|---|---|
| C | C(自举) | 无,直接生成机器码 |
| **Java** | **Java**(自举) | **C++**(JVM) |
| Go | Go(最初是 C) | Go 运行时 |
| Rust | Rust(最初是 OCaml) | 无,生成机器码 |
| Python (CPython) | **C**(选择不自举) | C |

CPython 是重要反例:它**有能力**自举,但**选择**用 C——因为 C 版本快得多。
**「能不能自举」和「该不该自举」是两个问题。**

**为什么 Luma 不能这么做?**

Luma 用 Java 8 写解释器,这相当于 Java 历史上"用 C 写第一个编译器"那个阶段。
Luma **永远无法自举**,因为它的语言能力不足以写一个解释器:
没有文件读写、没有字符串处理、没有类。

> 这不是缺陷,是边界。
> **一门语言能不能自举,本身就是衡量它能力强度的指标之一**——
> 而 Luma 的目标从来不是变强,是让你看清机制。

**这条对理解 Luma 有什么用?**

它解释了一件容易被忽略的事:**每一门语言背后都站着一门宿主语言**。
你写的 Luma 程序跑在 Luma 解释器上,解释器跑在 JVM 上,JVM 跑在 C++ 上,
C++ 编译成机器码跑在 CPU 上。**这条链的每一层都是一次"翻译"**——
理解这条链,比记住任何一门语言的语法都更接近「看懂语言怎么跑」这个目标。
