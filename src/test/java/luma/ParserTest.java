package luma;

import java.util.ArrayList;
import java.util.List;

/**
 * 语法分析器测试。
 *
 * <p>核心手法:把 AST 渲染成 S 表达式(如 {@code (+ 1 (* 2 3))}),
 * 这样"树的形状对不对"就变成一行字符串比较——
 * 比 instanceof 嵌套断言好读得多,失败时也一眼能看出哪里不一样。
 */
final class ParserTest {

    private ParserTest() {
    }

    /** 解析源码,过滤掉因语法错误产生的 null。 */
    private static List<Stmt> parse(String source) {
        List<Stmt> all = new Parser(new Scanner(source).scanTokens()).parse();
        List<Stmt> result = new ArrayList<Stmt>();
        for (Stmt stmt : all) {
            if (stmt != null) {
                result.add(stmt);
            }
        }
        return result;
    }

    /** 把 {@code print <expr>;} 里的表达式渲染成 S 表达式。 */
    private static String expr(String exprSource) {
        List<Stmt> statements = parse("print " + exprSource + ";");
        if (statements.isEmpty() || !(statements.get(0) instanceof Stmt.Print)) {
            return "<<解析失败>>";
        }
        return render(((Stmt.Print) statements.get(0)).expression);
    }

    /** AST → S 表达式。 */
    private static String render(Expr e) {
        if (e instanceof Expr.Literal) {
            // 复用解释器的显示规则,这样整数值显示成 1 而不是 1.0,断言读起来才清楚
            return Interpreter.stringify(((Expr.Literal) e).value);
        }
        if (e instanceof Expr.Grouping) {
            return "(group " + render(((Expr.Grouping) e).expression) + ")";
        }
        if (e instanceof Expr.Unary) {
            Expr.Unary u = (Expr.Unary) e;
            return "(" + u.operator.lexeme + " " + render(u.right) + ")";
        }
        if (e instanceof Expr.Binary) {
            Expr.Binary b = (Expr.Binary) e;
            return "(" + b.operator.lexeme + " " + render(b.left) + " " + render(b.right) + ")";
        }
        if (e instanceof Expr.Logical) {
            Expr.Logical l = (Expr.Logical) e;
            return "(" + l.operator.lexeme + " " + render(l.left) + " " + render(l.right) + ")";
        }
        if (e instanceof Expr.Variable) {
            return ((Expr.Variable) e).name.lexeme;
        }
        if (e instanceof Expr.Assign) {
            Expr.Assign a = (Expr.Assign) e;
            return "(= " + a.name.lexeme + " " + render(a.value) + ")";
        }
        if (e instanceof Expr.Call) {
            Expr.Call c = (Expr.Call) e;
            StringBuilder sb = new StringBuilder("(call ").append(render(c.callee));
            for (Expr arg : c.arguments) {
                sb.append(' ').append(render(arg));
            }
            return sb.append(')').toString();
        }
        if (e instanceof Expr.ArrayLiteral) {
            StringBuilder sb = new StringBuilder("(array");
            for (Expr item : ((Expr.ArrayLiteral) e).elements) {
                sb.append(' ').append(render(item));
            }
            return sb.append(')').toString();
        }
        if (e instanceof Expr.Index) {
            Expr.Index i = (Expr.Index) e;
            return "(index " + render(i.object) + " " + render(i.index) + ")";
        }
        if (e instanceof Expr.IndexSet) {
            Expr.IndexSet s = (Expr.IndexSet) e;
            return "(index-set " + render(s.object) + " " + render(s.index) + " " + render(s.value) + ")";
        }
        if (e instanceof Expr.Get) {
            Expr.Get g = (Expr.Get) e;
            return "(get " + render(g.object) + " " + g.name.lexeme + ")";
        }
        return "<<未知节点 " + e.getClass().getSimpleName() + ">>";
    }

    static void run() {
        precedence();
        associativity();
        postfix();
        statements();
        sugar();
        errors();
    }

    private static void precedence() {
        Assert.suite("Parser · 优先级");

        Assert.eq("* 比 + 高", "(+ 1 (* 2 3))", expr("1 + 2 * 3"));
        Assert.eq("括号改变结构", "(* (group (+ 1 2)) 3)", expr("(1 + 2) * 3"));
        Assert.eq("比较低于算术", "(< (+ 1 2) 5)", expr("1 + 2 < 5"));
        Assert.eq("== 低于比较", "(== (< 1 2) true)", expr("1 < 2 == true"));
        Assert.eq("&& 低于 ==", "(&& (== 1 1) (== 2 2))", expr("1 == 1 && 2 == 2"));
        Assert.eq("|| 最低", "(|| (&& a b) c)", expr("a && b || c"));
        Assert.eq("一元高于乘除", "(* (! a) 2)", expr("!a * 2"));
    }

    private static void associativity() {
        Assert.suite("Parser · 结合性");

        Assert.eq("减法左结合", "(- (- 1 2) 3)", expr("1 - 2 - 3"));
        Assert.eq("除法左结合", "(/ (/ 8 4) 2)", expr("8 / 4 / 2"));
        Assert.eq("赋值右结合", "(= a (= b 1))", expr("a = b = 1"));
        Assert.eq("&& 左结合", "(&& (&& a b) c)", expr("a && b && c"));
    }

    private static void postfix() {
        Assert.suite("Parser · 调用 / 下标 / 属性");

        Assert.eq("函数调用", "(call f 1 2)", expr("f(1, 2)"));
        Assert.eq("无参调用", "(call f)", expr("f()"));
        Assert.eq("链式调用", "(call (call f 1) 2)", expr("f(1)(2)"));
        Assert.eq("下标", "(index a 0)", expr("a[0]"));
        Assert.eq("连续下标", "(index (index a 0) 1)", expr("a[0][1]"));
        Assert.eq("属性", "(get a length)", expr("a.length"));
        Assert.eq("下标后取属性", "(get (index a 0) length)", expr("a[0].length"));
        Assert.eq("下标里可以是表达式", "(index a (+ i 1))", expr("a[i + 1]"));
        Assert.eq("数组字面量", "(array 1 2 3)", expr("[1, 2, 3]"));
        Assert.eq("空数组", "(array)", expr("[]"));
        Assert.eq("嵌套数组", "(array (array 1) (array 2))", expr("[[1], [2]]"));
        Assert.eq("尾随逗号", "(array 1 2)", expr("[1, 2,]"));
        Assert.eq("下标赋值是独立节点", "(index-set a 0 99)", expr("a[0] = 99"));
    }

    private static void statements() {
        Assert.suite("Parser · 语句");

        List<Stmt> var = parse("let x = 1;");
        Assert.eq("变量声明", 1, var.size());
        Assert.that("是 Var 节点", var.get(0) instanceof Stmt.Var);

        List<Stmt> block = parse("{ let x = 1; }");
        Assert.that("块", block.get(0) instanceof Stmt.Block);

        List<Stmt> ifStmt = parse("if (a) { } else { }");
        Assert.that("if 带 else", ((Stmt.If) ifStmt.get(0)).elseBranch != null);

        List<Stmt> ifNoElse = parse("if (a) { }");
        Assert.that("if 无 else", ((Stmt.If) ifNoElse.get(0)).elseBranch == null);

        List<Stmt> fn = parse("func f(a, b) { return a; }");
        Assert.that("函数声明", fn.get(0) instanceof Stmt.Function);
        Assert.eq("参数个数", 2, ((Stmt.Function) fn.get(0)).params.size());

        // else if 应该解析成 else 里套一个 if
        List<Stmt> elseIf = parse("if (a) { } else if (b) { }");
        Stmt.If outer = (Stmt.If) elseIf.get(0);
        Assert.that("else if 是嵌套的 If", outer.elseBranch instanceof Stmt.If);
    }

    private static void sugar() {
        Assert.suite("Parser · 语法糖展开");

        // for 必须被展开成 { let i = 0; while (...) { body; i = i + 1; } }
        List<Stmt> forStmt = parse("for (let i = 0; i < 3; i = i + 1) { print i; }");
        Assert.eq("for 展开成一个块", 1, forStmt.size());
        Assert.that("外层是 Block", forStmt.get(0) instanceof Stmt.Block);

        Stmt.Block outerBlock = (Stmt.Block) forStmt.get(0);
        Assert.eq("块里有两样东西:初始化 + while", 2, outerBlock.statements.size());
        Assert.that("第一样是变量声明", outerBlock.statements.get(0) instanceof Stmt.Var);
        Assert.that("第二样是 while", outerBlock.statements.get(1) instanceof Stmt.While);

        Stmt.While loop = (Stmt.While) outerBlock.statements.get(1);
        Assert.that("循环体是 Block", loop.body instanceof Stmt.Block);
        Stmt.Block loopBody = (Stmt.Block) loop.body;
        Assert.eq("循环体里是 body + 递增两句", 2, loopBody.statements.size());

        // AST 里不该出现一个叫 For 的节点类型
        boolean hasForNode = false;
        for (Stmt s : forStmt) {
            hasForNode |= s.getClass().getSimpleName().equals("For");
        }
        Assert.that("AST 中没有 For 节点", !hasForNode);
    }

    private static void errors() {
        Assert.suite("Parser · 错误处理");

        // 缺分号:错误恢复后不该把后面的语句也吃掉
        Assert.eq("缺分号后能继续解析\nlet y = 2;", 1, parse("let x = 1\nlet y = 2;").size());

        // 非法赋值目标应该被拒绝(不产生语句)
        Assert.eq("1 = 2 被拒绝", 0, parse("1 = 2;").size());

        // 缺右括号
        Assert.eq("缺 ')' 被拒绝", 0, parse("let x = (1 + 2;").size());
    }
}
