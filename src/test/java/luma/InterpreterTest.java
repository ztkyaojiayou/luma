package luma;

/**
 * 求值器测试:这是最重要的一组。
 *
 * <p>每一条断言都对应 SPEC 里一条明确的语义规定——
 * 如果哪天有人改坏了其中一条,这里会立刻报出来。
 */
final class InterpreterTest {

    private InterpreterTest() {
    }

    /** 执行源码并断言输出完全等于 expected。 */
    private static void expect(String desc, String source, String expected) {
        Assert.eq(desc, expected, LumaHarness.run(source));
    }

    /** 执行源码并断言产生了运行时错误,且信息里包含 needle。 */
    private static void expectError(String desc, String source, String needle) {
        String error = LumaHarness.runtimeErrorOf(source);
        if (error == null) {
            Assert.that(desc + "(应报错但没有报)", false);
        } else {
            Assert.contains(desc, needle, error);
        }
    }

    static void run() {
        arithmetic();
        truthiness();
        equality();
        logic();
        strings();
        variables();
        controlFlow();
        functions();
        closures();
        arrays();
        errors();
    }

    private static void arithmetic() {
        Assert.suite("Interpreter · 算术");

        expect("加法", "print 1 + 2;", "3\n");
        expect("减法", "print 7 - 3;", "4\n");
        expect("乘法", "print 3 * 4;", "12\n");
        expect("除法得小数", "print 7 / 2;", "3.5\n");
        expect("整除也带小数", "print 6 / 2;", "3\n");
        expect("取余", "print 7 % 2;", "1\n");
        expect("一元负号", "print -5;", "-5\n");
        expect("优先级", "print 1 + 2 * 3;", "7\n");
        expect("括号优先", "print (1 + 2) * 3;", "9\n");
        expect("整数显示省略 .0", "print 3.0;", "3\n");
        expect("小数正常显示", "print 3.5;", "3.5\n");
    }

    private static void truthiness() {
        Assert.suite("Interpreter · 真值规则(只有 false 和 null 为假)");

        expect("0 是真", "if (0) { print \"真\"; }", "真\n");
        expect("空字符串是真", "if (\"\") { print \"真\"; }", "真\n");
        expect("空数组是真", "if ([]) { print \"真\"; }", "真\n");
        expect("false 是假", "if (false) { print \"真\"; }", "");
        expect("null 是假", "if (null) { print \"真\"; }", "");
        expect("取反", "print !0;", "false\n");
        expect("对 null 取反", "print !null;", "true\n");
    }

    private static void equality() {
        Assert.suite("Interpreter · 相等规则");

        expect("数字比内容", "print 1 == 1;", "true\n");
        expect("文本比内容", "print \"a\" == \"a\";", "true\n");
        expect("跨类型是 false 而不是报错", "print 1 == \"1\";", "false\n");
        expect("不等", "print 1 != 2;", "true\n");
        expect("数组比身份,不同对象", "print [1, 2] == [1, 2];", "false\n");
        expect("数组比身份,同一对象", "let a = [1]; print a == a;", "true\n");
        expect("null 等于 null", "print null == null;", "true\n");
    }

    private static void logic() {
        Assert.suite("Interpreter · 短路求值");

        // boom() 一旦被执行就会打印,所以"没打印"就是短路的证据
        String boom = "func boom() { print \"执行了\"; return true; } ";
        expect("false && 短路", boom + "print false && boom();", "false\n");
        expect("true || 短路", boom + "print true || boom();", "true\n");
        expect("true && 不短路", boom + "print true && boom();", "执行了\ntrue\n");
        expect("false || 不短路", boom + "print false || boom();", "执行了\ntrue\n");
        expect("返回原值而不是布尔", "print 5 && 3;", "3\n");
        expect("0 为真所以短路返回 0", "print 0 || 7;", "0\n");
    }

    private static void strings() {
        Assert.suite("Interpreter · 字符串");

        expect("拼接", "print \"你好\" + \"世界\";", "你好世界\n");
        expect("打印不带引号", "print \"hi\";", "hi\n");
        expect("中文", "print \"你好\";", "你好\n");
        expectError("文本和数字不能相加", "print \"a\" + 1;", "不能把");
        expectError("数字和文本不能相加", "print 1 + \"a\";", "不能把");
    }

    private static void variables() {
        Assert.suite("Interpreter · 变量与作用域");

        expect("声明与读取", "let x = 1; print x;", "1\n");
        expect("赋值", "let x = 1; x = 2; print x;", "2\n");
        expect("遮蔽不修改外层", "let x = 1; { let x = 2; print x; } print x;", "2\n1\n");
        expect("不带 let 的赋值改的是外层", "let x = 1; { x = 2; } print x;", "2\n");
        expect("块内新变量出了块就没了", "let x = 1; { let y = 2; } print x;", "1\n");
        expectError("读未定义变量", "print nope;", "未定义的变量");
        expectError("给未声明变量赋值", "nope = 1;", "不能给未声明的变量");
    }

    private static void controlFlow() {
        Assert.suite("Interpreter · 控制流");

        expect("if 走 then", "if (true) { print 1; } else { print 2; }", "1\n");
        expect("if 走 else", "if (false) { print 1; } else { print 2; }", "2\n");
        expect("else if 链", "let x = 2; if (x == 1) { print 1; } else if (x == 2) { print 2; } else { print 3; }", "2\n");
        expect("while", "let i = 0; while (i < 3) { print i; i = i + 1; }", "0\n1\n2\n");
        expect("while 条件一开始就假", "let i = 9; while (i < 3) { print i; }", "");
        expect("for", "for (let i = 0; i < 3; i = i + 1) { print i; }", "0\n1\n2\n");
        expect("for 递减", "for (let i = 3; i > 0; i = i - 1) { print i; }", "3\n2\n1\n");
    }

    private static void functions() {
        Assert.suite("Interpreter · 函数");

        expect("定义与调用", "func add(a, b) { return a + b; } print add(1, 2);", "3\n");
        expect("无 return 返回 null", "func f() { } print f();", "null\n");
        expect("return 无值", "func f() { return; } print f();", "null\n");
        expect("递归", "func fact(n) { if (n <= 1) { return 1; } return n * fact(n - 1); } print fact(5);", "120\n");
        expect("return 从嵌套块里跳出",
                "func f() { if (true) { return 1; } return 2; } print f();", "1\n");
        expect("函数是值", "func d(n) { return n * 2; } let f = d; print f(5);", "10\n");
        expect("函数当参数", "func d(n) { return n * 2; } func ap(f, x) { return f(x); } print ap(d, 7);", "14\n");
        expectError("实参个数不对", "func f(a) { } f();", "实参个数不对");
        expectError("调用非函数", "let x = 1; x();", "不是函数");
        // 参数也是普通变量,所以可以在函数体里被 let 遮蔽 —— 这是合法行为,不是错误
        expect("参数可被 let 遮蔽", "func f(a) { let a = 2; return a; } print f(1);", "2\n");
    }

    private static void closures() {
        Assert.suite("Interpreter · 闭包");

        expect("计数器能记住状态",
                "func make() { let n = 0; func next() { n = n + 1; return n; } return next; } "
                        + "let c = make(); print c(); print c();",
                "1\n2\n");

        // 这条是最关键的:两个闭包必须各自持有独立的环境
        expect("两个闭包互不干扰",
                "func make() { let n = 0; func next() { n = n + 1; return n; } return next; } "
                        + "let a = make(); let b = make(); print a(); print a(); print b();",
                "1\n2\n1\n");

        expect("闭包能让函数记住配置",
                "func adder(n) { func add(x) { return x + n; } return add; } "
                        + "let add5 = adder(5); print add5(3);",
                "8\n");
    }

    private static void arrays() {
        Assert.suite("Interpreter · 数组");

        expect("字面量与下标", "let a = [10, 20, 30]; print a[1];", "20\n");
        expect("下标赋值", "let a = [1, 2]; a[0] = 9; print a;", "[9, 2]\n");
        expect("长度", "print [1, 2, 3].length;", "3\n");
        expect("空数组长度", "print [].length;", "0\n");
        expect("push", "let a = []; push(a, 1); push(a, 2); print a;", "[1, 2]\n");
        expect("push 返回 null", "print push([1], 2);", "null\n");
        expect("打印格式", "print [1, \"a\", true, null];", "[1, a, true, null]\n");
        expect("嵌套数组打印", "print [[1, 2], [3]];", "[[1, 2], [3]]\n");

        // 引用语义:两个名字一个对象
        expect("赋值共享同一个数组", "let a = [1, 2]; let b = a; b[0] = 99; print a[0];", "99\n");
        expect("传参也是共享", "func mod(x) { x[0] = 0; } let a = [1]; mod(a); print a;", "[0]\n");
        // 重新赋值不是修改:这题最能区分"引用"和"重新绑定"
        expect("重新赋值不影响原数组", "let a = [1]; let b = a; b = [9]; print a;", "[1]\n");

        expectError("越界读", "print [1, 2][5];", "超出范围");
        expectError("负下标", "print [1][-1];", "超出范围");
        expectError("非整数下标", "print [1][0.5];", "下标必须是整数");
        expectError("对非数组取下标", "print 1[0];", "不能用下标访问");
        expectError("访问其他属性", "print [].foo;", "只支持 .length");
        expectError("push 非数组", "push(1, 2);", "必须是数组");

        // 这两条是「已知限制」,不是想要的行为。
        // 如果哪天有人实现了字符串下标,这两条会失败 —— 那时候记得同步改 SPEC §八 和手册。
        expectError("字符串不能取下标", "print \"abc\"[0];", "不能用下标访问");
        expectError("字符串没有 length", "print \"abc\".length;", "没有 .length");
    }

    private static void errors() {
        Assert.suite("Interpreter · 运行时错误");

        expectError("除零", "print 1 / 0;", "除数不能是 0");
        expectError("取模除零", "print 1 % 0;", "不能是 0");
        expectError("文本不能减", "print \"a\" - 1;", "需要两个数字");
        expectError("文本不能比较大小", "print \"a\" < 1;", "需要两个数字");
        expectError("一元负号只能用于数字", "print -\"a\";", "只能用于数字");

        // 错误发生后,后面的语句不该继续执行 —— 要同时检查"没有任何输出"和"确实报了错"
        LumaHarness.Result aborted = LumaHarness.exec(
                "func boom() { return 1 / 0; } boom(); print \"不该出现\";");
        Assert.eq("出错前的输出为空", "", aborted.output);
        Assert.that("确实产生了运行时错误", aborted.error != null);
    }
}
