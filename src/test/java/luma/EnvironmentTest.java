package luma;

/** 作用域链测试:只管"名字从哪层找到"这一件事,不涉及求值。 */
final class EnvironmentTest {

    private EnvironmentTest() {
    }

    private static Token ident(String name) {
        return new Token(TokenType.IDENTIFIER, name, null, 1, 1);
    }

    static void run() {
        basic();
        nesting();
        assignment();
        nullValue();
        missing();
    }

    private static void basic() {
        Assert.suite("Environment · 定义与读取");

        Environment env = new Environment();
        env.define("x", 1.0);
        Assert.eq("定义后能读到", 1.0, env.get(ident("x")));

        env.define("x", 2.0);
        Assert.eq("重复定义会覆盖本层", 2.0, env.get(ident("x")));
    }

    private static void nesting() {
        Assert.suite("Environment · 链式查找");

        Environment outer = new Environment();
        outer.define("shared", "外层");
        Environment inner = new Environment(outer);

        Assert.eq("内层找不到会去外层", "外层", inner.get(ident("shared")));

        inner.define("shared", "内层");
        Assert.eq("内层遮蔽外层", "内层", inner.get(ident("shared")));
        Assert.eq("外层不受内层影响", "外层", outer.get(ident("shared")));

        // 三层
        Environment deepest = new Environment(inner);
        Assert.eq("三层也能一路找到", "内层", deepest.get(ident("shared")));
    }

    private static void assignment() {
        Assert.suite("Environment · 赋值");

        Environment outer = new Environment();
        outer.define("x", 1.0);
        Environment inner = new Environment(outer);

        // 内层没有 x,赋值应该穿透到外层
        inner.assign(ident("x"), 9.0);
        Assert.eq("赋值穿透到外层", 9.0, outer.get(ident("x")));

        // 内层一旦自己定义了 x,赋值就停在内层
        inner.define("x", 100.0);
        inner.assign(ident("x"), 200.0);
        Assert.eq("内层有同名变量时赋值停在本地", 200.0, inner.get(ident("x")));
        Assert.eq("外层仍是旧值", 9.0, outer.get(ident("x")));
    }

    private static void nullValue() {
        Assert.suite("Environment · null 是一个合法的值");

        Environment env = new Environment();
        env.define("n", null);
        // 关键:用判空判断"变量存在与否"会在这里出错
        Assert.eq("值为 null 时仍应能读到 null", null, env.get(ident("n")));
    }

    private static void missing() {
        Assert.suite("Environment · 未定义");

        Environment env = new Environment();
        try {
            env.get(ident("nope"));
            Assert.that("读取未定义变量应该抛错", false);
        } catch (RuntimeError e) {
            Assert.contains("错误信息说明未定义", "未定义的变量", e.getMessage());
        }

        try {
            env.assign(ident("nope"), 1.0);
            Assert.that("给未定义变量赋值应该抛错", false);
        } catch (RuntimeError e) {
            Assert.contains("错误信息说明不能赋值", "不能给未声明的变量", e.getMessage());
        }

        // 子环境里读不到也不该"穿透到底"变成未定义以外的东西
        Environment child = new Environment(env);
        try {
            child.get(ident("nope"));
            Assert.that("子环境读未定义变量也要抛错", false);
        } catch (RuntimeError e) {
            Assert.contains("子环境的错误信息", "未定义的变量", e.getMessage());
        }
    }
}
