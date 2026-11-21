package luma;

import java.util.List;

/**
 * 表达式节点(会产生一个值的东西)。
 *
 * <p>Java 8 没有 {@code sealed} 和 {@code record},所以用"抽象类 + 静态内部类 + Visitor 接口"
 * 来表达一个封闭的语法树。代价是啰嗦,好处是**分派机制是可见的**:
 * {@link #accept} 那一行就是解释器"按节点类型跳到对应处理逻辑"的全部秘密。
 *
 * <p>每个子类只做三件事:存字段、构造、在 accept 里回调 Visitor 的对应方法。
 * 不加任何业务逻辑——AST 是数据,求值是 Interpreter 的事。
 */
public abstract class Expr {

    /**
     * 表达式访问者。返回值类型由调用方决定:
     * 求值时 {@code R = Object},做检查时可以是别的。
     */
    public interface Visitor<R> {
        R visitLiteralExpr(Literal expr);
        R visitGroupingExpr(Grouping expr);
        R visitUnaryExpr(Unary expr);
        R visitBinaryExpr(Binary expr);
        R visitLogicalExpr(Logical expr);
        R visitVariableExpr(Variable expr);
        R visitAssignExpr(Assign expr);
        R visitCallExpr(Call expr);
        R visitArrayLiteralExpr(ArrayLiteral expr);
        R visitIndexExpr(Index expr);
        R visitIndexSetExpr(IndexSet expr);
        R visitGetExpr(Get expr);
    }

    /** 双重分派:先按节点类型进到子类的 accept,再由子类回调 Visitor 的具体方法。 */
    public abstract <R> R accept(Visitor<R> visitor);

    // ------------------------------------------------------------------
    // 字面量与分组
    // ------------------------------------------------------------------

    /** {@code 42} / {@code "你好"} / {@code true} / {@code null} —— 运行时值已经在此定好。 */
    public static class Literal extends Expr {
        final Object value;

        public Literal(Object value) {
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLiteralExpr(this);
        }
    }

    /** {@code ( expr )} —— 只为了改变求值顺序,本身不产生任何运算。 */
    public static class Grouping extends Expr {
        final Expr expression;

        public Grouping(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGroupingExpr(this);
        }
    }

    // ------------------------------------------------------------------
    // 运算
    // ------------------------------------------------------------------

    /** {@code !a} / {@code -a} —— 只有一个操作数。 */
    public static class Unary extends Expr {
        final Token operator;
        final Expr right;

        public Unary(Token operator, Expr right) {
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitUnaryExpr(this);
        }
    }

    /** {@code a + b} 这类普通二元运算:两边都求值,再做运算。 */
    public static class Binary extends Expr {
        final Expr left;
        final Token operator;
        final Expr right;

        public Binary(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBinaryExpr(this);
        }
    }

    /**
     * {@code a && b} / {@code a || b}。
     *
     * <p>为什么单独建一个节点、不并进 {@link Binary}?
     * 因为它是**唯一一个可能不把两边都求值**的运算:
     * {@code false && 报错()} 不该执行右边。求值器必须在这里提前返回。
     */
    public static class Logical extends Expr {
        final Expr left;
        final Token operator;
        final Expr right;

        public Logical(Expr left, Token operator, Expr right) {
            this.left = left;
            this.operator = operator;
            this.right = right;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitLogicalExpr(this);
        }
    }

    // ------------------------------------------------------------------
    // 变量与赋值
    // ------------------------------------------------------------------

    /** {@code x} —— 从当前环境链里取值。 */
    public static class Variable extends Expr {
        final Token name;

        public Variable(Token name) {
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVariableExpr(this);
        }
    }

    /** {@code x = v} —— 往**已存在**的变量里写。变量不存在是运行时错误,不会隐式创建。 */
    public static class Assign extends Expr {
        final Token name;
        final Expr value;

        public Assign(Token name, Expr value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitAssignExpr(this);
        }
    }

    // ------------------------------------------------------------------
    // 函数调用
    // ------------------------------------------------------------------

    /** {@code f(a, b)} —— 被调用者本身也是表达式,所以函数可以存在变量里、作为参数传。 */
    public static class Call extends Expr {
        final Expr callee;
        /** 右括号 token,只用于报错时定位。 */
        final Token paren;
        final List<Expr> arguments;

        public Call(Expr callee, Token paren, List<Expr> arguments) {
            this.callee = callee;
            this.paren = paren;
            this.arguments = arguments;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitCallExpr(this);
        }
    }

    // ------------------------------------------------------------------
    // 数组
    // ------------------------------------------------------------------

    /** {@code [1, 2, 3]} —— 元素按书写顺序求值,运行时创建一个新的数组对象。 */
    public static class ArrayLiteral extends Expr {
        final List<Expr> elements;

        public ArrayLiteral(List<Expr> elements) {
            this.elements = elements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitArrayLiteralExpr(this);
        }
    }

    /** {@code a[i]} —— 读取下标。 */
    public static class Index extends Expr {
        final Expr object;
        final Expr index;
        /** 左方括号 token,越界等错误需要用它定位。 */
        final Token bracket;

        public Index(Expr object, Expr index, Token bracket) {
            this.object = object;
            this.index = index;
            this.bracket = bracket;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIndexExpr(this);
        }
    }

    /**
     * {@code a[i] = v} —— 写入下标。
     *
     * <p>它和 {@link Assign} 分开,是因为"能被赋值的东西"不止变量一种。
     * Parser 在解析赋值时会看左边是什么:变量生成 Assign,下标生成 IndexSet。
     */
    public static class IndexSet extends Expr {
        final Expr object;
        final Expr index;
        final Expr value;
        final Token bracket;

        public IndexSet(Expr object, Expr index, Expr value, Token bracket) {
            this.object = object;
            this.index = index;
            this.value = value;
            this.bracket = bracket;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIndexSetExpr(this);
        }
    }

    /**
     * {@code a.length} —— 属性访问。
     *
     * <p>只支持 {@code length} 一个名字、只对数组有效。不做通用属性访问,
     * 因为那是"类"才需要的东西,而 Luma 没有类。
     */
    public static class Get extends Expr {
        final Expr object;
        final Token name;

        public Get(Expr object, Token name) {
            this.object = object;
            this.name = name;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitGetExpr(this);
        }
    }
}
