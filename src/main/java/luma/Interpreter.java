package luma;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 求值器:按语法树直接执行。这叫"树遍历解释器"(tree-walking interpreter)。
 *
 * <p>它同时实现了两个访问者:表达式访客返回 {@code Object}(求出的值),
 * 语句访客返回 {@code Void}(只为副作用)。所有语义规则都集中在这一个类里,
 * 想确认"某个运算到底怎么算",只需要看这里。
 *
 * <p>设计上刻意不用 Visitor 以外的技巧:没有任何字节码、没有栈机、没有 JIT。
 * 结构就是"遇到什么节点,做对应的事"——慢,但是一眼能看懂。
 */
public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    /** 全局作用域。内建函数和顶层变量都在这。 */
    final Environment globals = new Environment();

    /** 当前作用域。随块进出而移动。 */
    private Environment environment = globals;

    /** 调用栈,只用于报错时展示"错误发生在哪条调用链上"。栈顶是最近的一次调用。 */
    private final Deque<CallFrame> frames = new ArrayDeque<CallFrame>();

    /** 当前正在执行的函数名。"顶层"表示不在任何函数里。 */
    private String currentFunction = "顶层";

    /** 调用栈的一帧:在哪个函数里、第几行发起的调用。 */
    private static class CallFrame {
        final String callerName;
        final int callLine;

        CallFrame(String callerName, int callLine) {
            this.callerName = callerName;
            this.callLine = callLine;
        }
    }

    public Interpreter() {
        NativeFunctions.install(globals);
    }

    // ==================================================================
    // 入口
    // ==================================================================

    /** 执行一段程序。运行时错误由调用方(Luma)统一捕获和展示。 */
    void interpret(List<Stmt> statements) {
        for (Stmt statement : statements) {
            execute(statement);
        }
    }

    /**
     * 在一个指定作用域里执行一串语句,执行完恢复原来的作用域。
     *
     * <p>函数体和块都走这里。用 try/finally 保证即使中途抛异常(比如 return 信号),
     * 环境引用也会被还原——否则解释器会残留在错误的作用域里,后面全乱。
     */
    void executeBlock(List<Stmt> statements, Environment blockEnvironment) {
        Environment previous = this.environment;
        try {
            this.environment = blockEnvironment;
            for (Stmt statement : statements) {
                execute(statement);
            }
        } finally {
            this.environment = previous;
        }
    }

    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // ==================================================================
    // 语句
    // ==================================================================

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression); // 只为副作用,结果丢弃
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        // 先求值再 define。这样 `let x = x;` 里的 x 指向外层作用域,而不是自己
        Object value = evaluate(stmt.initializer);
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        if (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            execute(stmt.elseBranch);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        // 每次都重新求值条件——这是 while 和 if 的唯一区别
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // 创建函数对象时把"当前环境"塞进去 —— 这一步就是闭包。
        // 注意是先创建后 define:这样函数体内可以递归调用自己。
        LumaFunction function = new LumaFunction(stmt, environment);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = stmt.value == null ? null : evaluate(stmt.value);
        // 用异常一路弹回 LumaFunction.call(),中间的所有块、循环都会被跳过
        throw new LumaFunction.ReturnSignal(value);
    }

    // ==================================================================
    // 表达式
    // ==================================================================

    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression); // 括号只影响"什么时候算",不改变结果
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(Double) right;
            default:
                // Scanner 只会产出这两种一元运算符,走到这里说明解释器自身有 bug
                throw new IllegalStateException("未知的一元运算符: " + expr.operator.type);
        }
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        // 严格从左到右求值,顺序在 SPEC §6.8 里是硬规定
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type) {
            case PLUS:
                // '+' 是唯一有重载含义的运算符:两个数字相加,或两个字符串拼接。
                // 混合类型直接报错,绝不做隐式转换。
                if (left instanceof Double && right instanceof Double) {
                    return (Double) left + (Double) right;
                }
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(expr.operator,
                        "不能把 " + typeName(left) + " 和 " + typeName(right) + " 相加");

            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left - (Double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left * (Double) right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                checkNotZero(expr.operator, right, "除数");
                return (Double) left / (Double) right;
            case PERCENT:
                checkNumberOperands(expr.operator, left, right);
                checkNotZero(expr.operator, right, "取模的除数");
                return (Double) left % (Double) right;

            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left > (Double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left >= (Double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left < (Double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (Double) left <= (Double) right;

            case EQUAL_EQUAL:
                return isEqual(left, right);
            case BANG_EQUAL:
                return !isEqual(left, right);

            default:
                throw new IllegalStateException("未知的二元运算符: " + expr.operator.type);
        }
    }

    /**
     * {@code &&} / {@code ||}。
     *
     * <p>这里是全语言唯一"可能不把两遍都算完"的地方。
     * {@code false && 爆炸()} 必须先返回 false,不能去执行右边。
     * 也正因为如此,它不能做成库函数——库函数的参数在调用前就已经全部求值了。
     */
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        Object left = evaluate(expr.left);

        if (expr.operator.type == TokenType.OR_OR && isTruthy(left)) {
            return left; // 左边为真,短路,直接返回左边的值
        }
        if (expr.operator.type == TokenType.AND_AND && !isTruthy(left)) {
            return left; // 左边为假,短路
        }
        return evaluate(expr.right);
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        return environment.get(expr.name);
    }

    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        environment.assign(expr.name, value);
        return value; // 赋值本身是个表达式,值是刚赋的那个值,所以 a = b = 1 成立
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        Object callee = evaluate(expr.callee);

        if (!(callee instanceof LumaCallable)) {
            throw new RuntimeError(expr.paren, "只能调用函数," + typeName(callee) + " 不是函数");
        }

        List<Object> arguments = new ArrayList<Object>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }

        LumaCallable function = (LumaCallable) callee;
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren,
                    "实参个数不对:期望 " + function.arity() + " 个,实际传了 " + arguments.size() + " 个");
        }

        if (frames.size() >= 1000) {
            // 在这里拦住,否则会先撞上 JVM 的栈溢出,错误信息对用户毫无意义
            throw new RuntimeError(expr.paren, "调用层数过深(超过 1000 层),可能是无限递归");
        }

        String caller = currentFunction;
        if (callee instanceof LumaFunction) {
            currentFunction = ((LumaFunction) callee).functionName();
        }
        frames.push(new CallFrame(caller, expr.paren.line));
        try {
            return function.call(this, arguments);
        } catch (RuntimeError error) {
            // 内建函数抛错时没有位置信息,用调用点的 token 补上,
            // 这样报错指向用户写的那一行,而不是 JDK 内部
            error.attachIfMissing(expr.paren);
            // 必须在 finally 弹栈之前抓快照,否则等 Luma 报告时栈已经空了
            error.captureStack(callStackLines());
            throw error;
        } finally {
            frames.pop();
            currentFunction = caller;
        }
    }

    @Override
    public Object visitArrayLiteralExpr(Expr.ArrayLiteral expr) {
        LumaArray array = new LumaArray();
        // 按书写顺序求值并追加 —— 元素之间没有相互依赖,顺序只影响副作用的先后
        for (Expr element : expr.elements) {
            array.push(evaluate(element));
        }
        return array;
    }

    @Override
    public Object visitIndexExpr(Expr.Index expr) {
        Object target = evaluate(expr.object);
        Object indexValue = evaluate(expr.index);
        int index = resolveIndex(expr.bracket, target, indexValue);
        return ((LumaArray) target).get(index);
    }

    @Override
    public Object visitIndexSetExpr(Expr.IndexSet expr) {
        // 顺序很重要:先确定数组和下标,再求右边。
        // 这样 a[i] = 会修改 a 的表达式 时,右边看到的是修改前的 a —— 和直觉一致。
        Object target = evaluate(expr.object);
        Object indexValue = evaluate(expr.index);
        int index = resolveIndex(expr.bracket, target, indexValue);
        Object value = evaluate(expr.value);
        ((LumaArray) target).set(index, value);
        return value; // 和 Assign 一致:赋值表达式的值是被赋的值
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        Object target = evaluate(expr.object);
        if (!"length".equals(expr.name.lexeme)) {
            // 只支持 length。不做通用属性访问,因为没有"对象"这种东西
            throw new RuntimeError(expr.name, "数组只支持 .length 属性,没有 '" + expr.name.lexeme + "'");
        }
        if (!(target instanceof LumaArray)) {
            throw new RuntimeError(expr.name, typeName(target) + " 没有 .length 属性");
        }
        return (double) ((LumaArray) target).size();
    }

    // ==================================================================
    // 语义辅助:真值、相等、类型名、显示格式
    // ==================================================================

    /**
     * 真值规则:只有 {@code false} 和 {@code null} 为假,其余一切为真。
     *
     * <p>0 和空字符串都算真 —— 这是 Lua/Lisp 的规则,一句话能说完,
     * 不像 JavaScript 需要背一张表。
     */
    static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return true;
    }

    /**
     * 相等判断。
     *
     * <ul>
     *   <li>类型不同 → 直接 false,<b>不报错</b>({@code ==} 的用途常常正是判断类型)</li>
     *   <li>数组 → 比<b>身份</b>(是不是同一个对象),不是比内容。这是引用语义的直接推论</li>
     *   <li>其余 → 比值</li>
     * </ul>
     */
    private static boolean isEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a instanceof LumaArray || b instanceof LumaArray) {
            // 显式用 == 表达"同一个数组才算相等",避免读者误以为这里在做内容比较
            return a == b;
        }
        return a.equals(b);
    }

    /** 把一个值转成显示用的字符串。print 和数组内元素都用它,保证格式一致。 */
    static String stringify(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double) {
            double d = (Double) value;
            // 值为整数的 double 省略 ".0" —— 否则 print 1; 会输出 1.0,很奇怪
            if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        if (value instanceof LumaArray) {
            return ((LumaArray) value).display();
        }
        return value.toString();
    }

    /** 类型的用户可读名字,用于报错信息。 */
    static String typeName(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Double) {
            return "数字";
        }
        if (value instanceof String) {
            return "文本";
        }
        if (value instanceof Boolean) {
            return "布尔值";
        }
        if (value instanceof LumaArray) {
            return "数组";
        }
        if (value instanceof LumaCallable) {
            return "函数";
        }
        return "未知类型";
    }

    // ==================================================================
    // 检查
    // ==================================================================

    private void checkNumberOperand(Token operator, Object operand) {
        if (!(operand instanceof Double)) {
            throw new RuntimeError(operator, "一元 '" + operator.lexeme + "' 只能用于数字,实际是 " + typeName(operand));
        }
    }

    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }
        throw new RuntimeError(operator,
                "运算符 '" + operator.lexeme + "' 需要两个数字,实际是 " + typeName(left) + " 和 " + typeName(right));
    }

    /**
     * 除零检查。
     *
     * <p>这是和 Java 有意不同的一处:Java 里 {@code 1.0 / 0} 得到 Infinity,
     * 那个怪值会一路传播,等你发现结果不对时已经离现场很远了。
     * Luma 选择当场报错。
     */
    private void checkNotZero(Token operator, Object divisor, String what) {
        if ((Double) divisor == 0.0) {
            throw new RuntimeError(operator, what + "不能是 0");
        }
    }

    /**
     * 校验下标并转成 int。越界、非整数、对非数组取下标,都在这里一次性拦掉。
     *
     * @return 合法的 int 下标
     */
    private int resolveIndex(Token bracket, Object target, Object indexValue) {
        if (!(target instanceof LumaArray)) {
            throw new RuntimeError(bracket, typeName(target) + " 不能用下标访问");
        }
        if (!(indexValue instanceof Double)) {
            throw new RuntimeError(bracket, "下标必须是数字,实际是 " + typeName(indexValue));
        }
        double d = (Double) indexValue;
        // NaN 也会在这里被拦下:任何和 Math.floor(d) 的比较对 NaN 都是 false
        if (d != Math.floor(d) || Double.isInfinite(d)) {
            throw new RuntimeError(bracket, "下标必须是整数,实际是 " + stringify(indexValue));
        }
        LumaArray array = (LumaArray) target;
        if (d < 0 || d >= array.size()) {
            // 报错里必须带上具体数字,否则"到底哪里越界了"要靠猜
            throw new RuntimeError(bracket,
                    "下标 " + stringify(indexValue) + " 超出范围(数组长度 " + array.size() + ")");
        }
        return (int) d;
    }

    /** 供 Luma 打印调用栈用。栈顶(最近一次调用)排在最前。 */
    List<String> callStackLines() {
        List<String> lines = new ArrayList<String>();
        for (CallFrame frame : frames) {
            String who = "顶层".equals(frame.callerName) ? "顶层" : "函数 '" + frame.callerName + "'";
            lines.add("    在 " + who + " 中调用 (第 " + frame.callLine + " 行)");
        }
        return lines;
    }
}
