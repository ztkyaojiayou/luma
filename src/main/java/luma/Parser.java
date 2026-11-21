package luma;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import luma.Expr.Assign;
import luma.Expr.Binary;
import luma.Expr.Call;
import luma.Expr.Get;
import luma.Expr.Grouping;
import luma.Expr.Index;
import luma.Expr.IndexSet;
import luma.Expr.Literal;
import luma.Expr.Logical;
import luma.Expr.Unary;
import luma.Expr.Variable;
import luma.Stmt.Block;
import luma.Stmt.Expression;
import luma.Stmt.Function;
import luma.Stmt.If;
import luma.Stmt.Print;
import luma.Stmt.Return;
import luma.Stmt.Var;
import luma.Stmt.While;

/**
 * 语法分析器:把 token 序列组成抽象语法树。
 *
 * <p>用的是最朴素的<b>递归下降</b>:每条语法规则写成一个方法,方法之间互相调用。
 * 运算符优先级不靠查表,而是**由方法的调用层级体现**——
 * {@link #expression} 调 {@link #assignment},{@link #assignment} 调 {@link #or},
 * 一路往下到 {@link #primary}。越往后调,优先级越高。
 *
 * <p>这样写的好处是语法规则和代码结构一一对应,读代码就是在读文法。
 */
public class Parser {

    /**
     * 解析错误的内部信号。
     *
     * <p>它不携带信息(信息已经通过 {@link Luma#syntaxError} 报出去了),
     * 作用只是**把控制流从深层递归里弹出来**,让 {@link #declaration} 层去做错误恢复。
     * 不用栈展开消息,是因为我们不打调用栈。
     */
    private static class ParseError extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private final List<Token> tokens;

    /** 下一个待消费的 token 下标。 */
    private int current = 0;

    /** 当前所在的函数嵌套深度。return 只在深度 > 0 时合法。 */
    private int functionDepth = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * 解析整个程序。
     *
     * <p>遇到错误不会立刻放弃:报错 → 同步到下一个语句边界 → 继续解析。
     * 所以一次运行可以把所有语法错误都报出来。
     */
    public List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<Stmt>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }

    // ==================================================================
    // 声明(变量、函数)
    // ==================================================================

    /**
     * 一个"声明"。声明是比语句更高一层的东西——只有它允许出现 {@code let} 和 {@code func}。
     * 这样写是为了让 {@code if (x) let y = 1;} 这种语法自然地被拒绝。
     */
    private Stmt declaration() {
        try {
            if (match(TokenType.FUNC)) {
                return function("函数");
            }
            if (match(TokenType.LET)) {
                return varDeclaration();
            }
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    /** {@code func name(参数...) { 函数体 }} */
    private Stmt.Function function(String kind) {
        Token name = consume(TokenType.IDENTIFIER, kind + "名不能为空");

        consume(TokenType.LEFT_PAREN, kind + "名后面缺少 '('");
        List<Token> params = new ArrayList<Token>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (params.size() >= 255) {
                    // 不是技术限制,是不想让一次调用写得太长——报错比默默接受好
                    error(peek(), "参数个数不能超过 255 个");
                }
                params.add(consume(TokenType.IDENTIFIER, "参数名不能为空"));
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_PAREN, "参数列表后面缺少 ')'");

        consume(TokenType.LEFT_BRACE, kind + "体缺少 '{'");

        // 进入函数体:从这一刻起 return 才合法
        functionDepth++;
        List<Stmt> body;
        try {
            body = block();
        } finally {
            functionDepth--;
        }

        return new Stmt.Function(name, params, body);
    }

    /** {@code let name = expr;} —— 必须有初始值,不允许"声明了但没值"。 */
    private Stmt varDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, "let 后面需要变量名");
        consume(TokenType.EQUAL, "变量声明必须给初始值,例如 let x = 0;");
        Expr initializer = expression();
        consume(TokenType.SEMICOLON, "变量声明结尾缺少 ';'");
        return new Stmt.Var(name, initializer);
    }

    // ==================================================================
    // 语句
    // ==================================================================

    private Stmt statement() {
        if (match(TokenType.FOR)) {
            return forStatement();
        }
        if (match(TokenType.IF)) {
            return ifStatement();
        }
        if (match(TokenType.WHILE)) {
            return whileStatement();
        }
        if (match(TokenType.RETURN)) {
            return returnStatement();
        }
        if (match(TokenType.PRINT)) {
            return printStatement();
        }
        if (match(TokenType.LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        return expressionStatement();
    }

    /**
     * for 循环 —— 这里是**语法糖在解析期消失**的地方。
     *
     * <pre>
     *   for (init; cond; incr) body
     * ==&gt; { init; while (cond) { body; incr; } }
     * </pre>
     *
     * 注意 increment 被放进和 body 同一个块里,这样它在循环体里可以访问到 body 声明的变量
     * (虽然 Luma 里没什么用,但保持了和直觉一致的作用域)。
     */
    private Stmt forStatement() {
        consume(TokenType.LEFT_PAREN, "for 后面缺少 '('");

        Stmt initializer;
        if (match(TokenType.SEMICOLON)) {
            initializer = null;
        } else if (match(TokenType.LET)) {
            initializer = varDeclaration();
        } else {
            initializer = expressionStatement();
        }

        Expr condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = expression();
        }
        consume(TokenType.SEMICOLON, "for 的条件后面缺少 ';'");

        Expr increment = null;
        if (!check(TokenType.RIGHT_PAREN)) {
            increment = expression();
        }
        consume(TokenType.RIGHT_PAREN, "for 的递增部分后面缺少 ')'");

        Stmt body = statement();

        // 从里往外包:先包出 { body; incr; },再包出 while,最后包出带初始化语句的块
        if (increment != null) {
            body = new Stmt.Block(Arrays.<Stmt>asList(body, new Stmt.Expression(increment)));
        }
        if (condition == null) {
            condition = new Expr.Literal(Boolean.TRUE); // for (;;) 视为恒真
        }
        body = new Stmt.While(condition, body);
        if (initializer != null) {
            body = new Stmt.Block(Arrays.<Stmt>asList(initializer, body));
        }
        return body;
    }

    private Stmt ifStatement() {
        consume(TokenType.LEFT_PAREN, "if 的条件缺少 '('");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "if 的条件后面缺少 ')'");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    private Stmt whileStatement() {
        consume(TokenType.LEFT_PAREN, "while 的条件缺少 '('");
        Expr condition = expression();
        consume(TokenType.RIGHT_PAREN, "while 的条件后面缺少 ')'");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    private Stmt returnStatement() {
        Token keyword = previous();
        if (functionDepth == 0) {
            // 顶层 return 没有可以退回的地方,必须在解析期就拦住
            throw error(keyword, "return 只能出现在函数内部");
        }
        Expr value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = expression();
        }
        consume(TokenType.SEMICOLON, "return 语句结尾缺少 ';'");
        return new Stmt.Return(keyword, value);
    }

    private Stmt printStatement() {
        Expr value = expression();
        consume(TokenType.SEMICOLON, "print 语句结尾缺少 ';'");
        return new Stmt.Print(value);
    }

    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(TokenType.SEMICOLON, "语句结尾缺少 ';'");
        return new Stmt.Expression(expr);
    }

    /** 解析一串语句直到 '}'。左花括号已由调用方吃掉,这里只负责吃右花括号。 */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<Stmt>();
        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(TokenType.RIGHT_BRACE, "代码块缺少 '}'");
        return statements;
    }

    // ==================================================================
    // 表达式:下面是严格按优先级从低到高排列的一串方法
    // ==================================================================

    private Expr expression() {
        return assignment();
    }

    /**
     * 赋值,优先级最低且**右结合**({@code a = b = c} 等价于 {@code a = (b = c)})。
     *
     * <p>难点在于:赋值和普通表达式的开头长得一样——都得先解析出左边。
     * 所以这里的做法是"先按普通表达式解析,再看后面是不是 '='",
     * 是的话就把左边当成**赋值目标**重新解释。
     */
    private Expr assignment() {
        Expr expr = or();

        if (match(TokenType.EQUAL)) {
            Token equals = previous();
            Expr value = assignment(); // 右结合:右边又是一次赋值解析

            if (expr instanceof Variable) {
                Token name = ((Variable) expr).name;
                return new Assign(name, value);
            }
            if (expr instanceof Index) {
                // a[i] = v:下标也能当赋值目标,但它不是"变量",所以单独一个节点
                Index index = (Index) expr;
                return new IndexSet(index.object, index.index, value, index.bracket);
            }
            // 到这儿说明左边不是"可写"的东西,比如 1 = 2 或 f() = 3
            throw error(equals, "赋值目标必须是变量或数组下标");
        }
        return expr;
    }

    /** {@code ||} —— 短路求值,所以生成 Logical 而不是 Binary。 */
    private Expr or() {
        Expr expr = and();
        while (match(TokenType.OR_OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Logical(expr, operator, right);
        }
        return expr;
    }

    /** {@code &&} —— 同样短路。 */
    private Expr and() {
        Expr expr = equality();
        while (match(TokenType.AND_AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr equality() {
        Expr expr = comparison();
        while (match(TokenType.EQUAL_EQUAL, TokenType.BANG_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr comparison() {
        Expr expr = term();
        while (match(TokenType.LESS, TokenType.LESS_EQUAL, TokenType.GREATER, TokenType.GREATER_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr term() {
        Expr expr = factor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr factor() {
        Expr expr = unary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Binary(expr, operator, right);
        }
        return expr;
    }

    /** 一元运算,右结合:{@code !!a} 和 {@code --a}(后者在 Luma 里没有意义,但语法上允许)。 */
    private Expr unary() {
        if (match(TokenType.BANG, TokenType.MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Unary(operator, right);
        }
        return call();
    }

    /**
     * 调用、下标、属性访问 —— 三者优先级相同,可以链式叠加。
     *
     * <p>{@code f(1).length}、{@code a[0][1]} 这类写法都从这里长出来:
     * 先把主体解析出来,然后不停地往后看有没有 {@code ( } / {@code [ } / {@code . }。
     */
    private Expr call() {
        Expr expr = primary();

        while (true) {
            if (match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else if (match(TokenType.LEFT_BRACKET)) {
                Token bracket = previous();
                Expr index = expression();
                consume(TokenType.RIGHT_BRACKET, "下标后面缺少 ']'");
                expr = new Index(expr, index, bracket);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "'.' 后面需要属性名");
                expr = new Get(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }

    /** 调用括号已经吃掉,这里解析实参列表并收尾。 */
    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<Expr>();
        if (!check(TokenType.RIGHT_PAREN)) {
            do {
                if (arguments.size() >= 255) {
                    error(peek(), "参数个数不能超过 255 个");
                }
                arguments.add(expression());
            } while (match(TokenType.COMMA));
        }
        Token paren = consume(TokenType.RIGHT_PAREN, "实参列表后面缺少 ')'");
        return new Call(callee, paren, arguments);
    }

    /** 优先级最高:字面量、变量、括号、数组字面量。 */
    private Expr primary() {
        if (match(TokenType.FALSE)) {
            return new Literal(Boolean.FALSE);
        }
        if (match(TokenType.TRUE)) {
            return new Literal(Boolean.TRUE);
        }
        if (match(TokenType.NULL)) {
            return new Literal(null);
        }
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Literal(previous().literal);
        }
        if (match(TokenType.IDENTIFIER)) {
            return new Variable(previous());
        }
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();
            consume(TokenType.RIGHT_PAREN, "表达式后面缺少 ')'");
            return new Grouping(expr);
        }
        if (match(TokenType.LEFT_BRACKET)) {
            return arrayLiteral(previous());
        }

        throw error(peek(), "这里需要一个表达式,但读到了 '" + peek().lexeme + "'");
    }

    /** 数组字面量。左方括号已吃掉。允许尾随逗号,方便多行书写。 */
    private Expr arrayLiteral(Token bracket) {
        List<Expr> elements = new ArrayList<Expr>();
        if (!check(TokenType.RIGHT_BRACKET)) {
            do {
                if (check(TokenType.RIGHT_BRACKET)) {
                    break; // 尾随逗号:[1, 2, 3,]
                }
                elements.add(expression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RIGHT_BRACKET, "数组字面量缺少 ']'");
        return new Expr.ArrayLiteral(elements);
    }

    // ==================================================================
    // token 游标与错误处理
    // ==================================================================

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    /** 要求下一个 token 就是指定类型,否则报错。语法分析里用得最多的一招。 */
    private Token consume(TokenType type, String message) {
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    /** 把错误报给用户,同时返回一个信号让控制流弹回 {@link #declaration}。 */
    private ParseError error(Token token, String message) {
        Luma.syntaxError(token.line, token.column, message);
        return new ParseError();
    }

    /**
     * 错误恢复:跳过 token 直到看起来像"下一条语句的开头"。
     *
     * <p>判据很土但有效:遇到分号(上一条语句结束了),或者遇到 let/func/if/while
     * 这类只会出现在语句开头关键字。这样一处语法错误只毁掉一条语句,
     * 不会把后面的代码也带崩。
     *
     * <p><b>注意开头的那个提前返回。</b>如果报错时游标正好停在一条语句的开头
     * (典型场景:上一行忘了分号),这里绝不能先 advance 把它吃掉——
     * 否则"下一条完好的语句"会被连带跳过,和恢复的目的正好相反。
     */
    private void synchronize() {
        if (!isAtEnd() && isStatementStart(peek().type)) {
            return;
        }

        advance();
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) {
                return;
            }
            if (isStatementStart(peek().type)) {
                return;
            }
            advance();
        }
    }

    /** 这个 token 类型是否只可能出现在一条新语句的开头。 */
    private static boolean isStatementStart(TokenType type) {
        switch (type) {
            case FUNC:
            case LET:
            case FOR:
            case IF:
            case WHILE:
            case PRINT:
            case RETURN:
                return true;
            default:
                return false;
        }
    }
}
