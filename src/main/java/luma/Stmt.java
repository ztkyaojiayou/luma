package luma;

import java.util.List;

/**
 * 语句节点(执行后产生效果、但不产生值的东西)。
 *
 * <p>和 {@link Expr} 用同一套 Visitor 模式,区别只在返回值:
 * 表达式的访问者返回 {@code Object}(要拿结果),语句的返回 {@code Void}(只为副作用)。
 *
 * <p>注意:没有 {@code For} 节点。「for 循环」在解析阶段就被改写成
 * "块 + 变量声明 + while",所以到了这一层已经消失——这正是语法糖的定义。
 */
public abstract class Stmt {

    public interface Visitor<R> {
        R visitExpressionStmt(Expression stmt);
        R visitPrintStmt(Print stmt);
        R visitVarStmt(Var stmt);
        R visitBlockStmt(Block stmt);
        R visitIfStmt(If stmt);
        R visitWhileStmt(While stmt);
        R visitFunctionStmt(Function stmt);
        R visitReturnStmt(Return stmt);
    }

    public abstract <R> R accept(Visitor<R> visitor);

    /** {@code expr;} —— 求值后丢弃结果,纯粹为了副作用(比如调用一个函数)。 */
    public static class Expression extends Stmt {
        final Expr expression;

        public Expression(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitExpressionStmt(this);
        }
    }

    /** {@code print expr;} —— 求值并输出一行。是语句而非函数,所以不带括号。 */
    public static class Print extends Stmt {
        final Expr expression;

        public Print(Expr expression) {
            this.expression = expression;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitPrintStmt(this);
        }
    }

    /** {@code let x = expr;} —— 在当前作用域里新建变量。必须带初始值。 */
    public static class Var extends Stmt {
        final Token name;
        final Expr initializer;

        public Var(Token name, Expr initializer) {
            this.name = name;
            this.initializer = initializer;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitVarStmt(this);
        }
    }

    /** {@code { ... }} —— 引入一个新的作用域。变量遮蔽就发生在这里。 */
    public static class Block extends Stmt {
        final List<Stmt> statements;

        public Block(List<Stmt> statements) {
            this.statements = statements;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitBlockStmt(this);
        }
    }

    /** {@code if (cond) ... else ...} —— else 分支可以为 null(即没有 else)。 */
    public static class If extends Stmt {
        final Expr condition;
        final Stmt thenBranch;
        final Stmt elseBranch;

        public If(Expr condition, Stmt thenBranch, Stmt elseBranch) {
            this.condition = condition;
            this.thenBranch = thenBranch;
            this.elseBranch = elseBranch;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitIfStmt(this);
        }
    }

    /** {@code while (cond) body} —— 唯一的真循环结构,for 最终也会变成它。 */
    public static class While extends Stmt {
        final Expr condition;
        final Stmt body;

        public While(Expr condition, Stmt body) {
            this.condition = condition;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitWhileStmt(this);
        }
    }

    /**
     * {@code func name(a, b) { ... }} —— 函数声明。
     *
     * <p>执行这条语句的效果是"把一个函数对象绑定到名字上",
     * 绑定的同时把**当前环境**记进函数对象里——那一记就是闭包。
     */
    public static class Function extends Stmt {
        final Token name;
        final List<Token> params;
        final List<Stmt> body;

        public Function(Token name, List<Token> params, List<Stmt> body) {
            this.name = name;
            this.params = params;
            this.body = body;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitFunctionStmt(this);
        }
    }

    /**
     * {@code return expr;} —— 用异常来跳出函数体(见 {@link LumaFunction})。
     *
     * <p>为什么用异常而不是在每个执行方法里返回状态?
     * 因为 return 可能藏在任意深度的 if / while / block 里,
     * 逐层判断返回值会污染所有签名。异常是这里最干净的"非局部跳转"手段。
     */
    public static class Return extends Stmt {
        final Token keyword;
        final Expr value;

        public Return(Token keyword, Expr value) {
            this.keyword = keyword;
            this.value = value;
        }

        @Override
        public <R> R accept(Visitor<R> visitor) {
            return visitor.visitReturnStmt(this);
        }
    }
}
