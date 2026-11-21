import java.util.ArrayList;
import java.util.List;

/**
 * 一个一百来行的计算器 —— 《Luma 学习手册》结语里「第一晚」那个练习的参考实现。
 *
 * 只支持 + - * / 和括号。但它和 Luma 走的是同一条路:
 *
 *   源码 "1 + 2 * 3"
 *        │  tokenize()  第一站:切碎
 *        ▼  [1] [+] [2] [*] [3]
 *        │  expr() → term() → factor()  第二站:按优先级递归
 *        ▼
 *        7
 *
 * 和 Luma 最大的区别:这里**不建语法树**,解析出一个结构就立刻算掉。
 * 这叫「边解析边求值」,是让代码压到一百行的关键取舍。
 * 代价是没法复用——想做类型检查、想做优化,就必须先有树。
 *
 * 编译运行:
 *   javac -encoding UTF-8 -d demo-out demo/Calc.java
 *   java -cp demo-out Calc "1 + 2 * (3 - 1)"
 */
public class Calc {

    /** 切成 token 之后的源码。数字保留原文,运算符和括号各占一个。 */
    private final List<String> tokens;

    /** 当前读到的位置。整个解析过程就是让这个游标一路向右。 */
    private int pos = 0;

    private Calc(String source) {
        this.tokens = tokenize(source);
    }

    // ================================================================
    // 第一站:词法 —— 一个循环,把字符串切成碎片
    // ================================================================

    private static List<String> tokenize(String source) {
        List<String> out = new ArrayList<String>();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);

            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            // 数字:连续的数字和小数点算一个整体
            if (Character.isDigit(c) || c == '.') {
                int start = i;
                while (i < source.length()
                        && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                    i++;
                }
                out.add(source.substring(start, i));
                continue;
            }

            // 运算符和括号
            if ("+-*/()".indexOf(c) >= 0) {
                out.add(String.valueOf(c));
                i++;
                continue;
            }

            throw new RuntimeException("看不懂的字符: " + c);
        }
        // 结尾哨兵:有了它,判断"读完了"就不用到处写边界检查
        out.add("EOF");
        return out;
    }

    // ================================================================
    // 第二站 + 第三站:语法与求值
    //
    // 三个方法,就是三个优先级。调用链从低到高:
    //   expr(加减) → term(乘除) → factor(数字和括号)
    // 越靠后调用的,优先级越高。
    // ================================================================

    private double parse() {
        double value = expr();
        expect("EOF");
        return value;
    }

    /** 加减。优先级最低,所以在最外层。 */
    private double expr() {
        double value = term();
        while (peek("+") || peek("-")) {
            String op = next();
            double right = term();
            value = op.equals("+") ? value + right : value - right;
        }
        return value;
    }

    /** 乘除。优先级比加减高,所以被 expr 调用。 */
    private double term() {
        double value = factor();
        while (peek("*") || peek("/")) {
            String op = next();
            double right = factor();
            if (op.equals("/") && right == 0) {
                throw new RuntimeException("除数不能是 0");
            }
            value = op.equals("*") ? value * right : value / right;
        }
        return value;
    }

    /** 优先级最高:一个数字,或者一对括号里的完整表达式。 */
    private double factor() {
        if (peek("(")) {
            next();
            double value = expr();     // 括号里又是一个完整的表达式 → 递归
            expect(")");
            return value;
        }
        String token = next();
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            throw new RuntimeException("这里应该是一个数字,但读到了 '" + token + "'");
        }
    }

    // ================================================================
    // 游标小工具 —— 就是这四个方法让上面的递归写起来很轻
    // ================================================================

    private String peek() {
        return tokens.get(pos);
    }

    private boolean peek(String token) {
        return peek().equals(token);
    }

    private String next() {
        return tokens.get(pos++);
    }

    private void expect(String token) {
        if (!peek(token)) {
            throw new RuntimeException("这里应该有 '" + token + "',但读到了 '" + peek() + "'");
        }
        pos++;
    }

    // ================================================================

    /**
     * 把结果转成好看的文字。
     *
     * <p>不加这一步,{@code 1 + 2} 会输出 "3.0" —— 技术上没错,但看着很怪。
     * Luma 里专门有个 {@code stringify} 方法干这件事,原因就在这里:
     * <b>「值」和「值怎么显示」是两件事</b>,后者要单独写。
     */
    private static String format(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    public static void main(String[] args) {
        String source = args.length > 0 ? String.join(" ", args) : "1 + 2 * (3 - 1)";
        try {
            System.out.println(format(new Calc(source).parse()));
        } catch (RuntimeException e) {
            System.err.println("错误: " + e.getMessage());
            System.exit(1);
        }
    }
}
