package luma;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

/**
 * 跑一段 Luma 源码,把 print 的输出和运行时错误都抓回来。
 *
 * <p>单元测试需要"输入源码、拿到结果"。但 {@link Luma#main} 是给命令行用的
 * (会 System.exit、会读文件),不适合在测试里反复调用。
 *
 * <p>所以这里直接驱动三站:Scanner → Parser → Interpreter,
 * 顺便把 {@code System.out} 换成一个缓冲区。**这样测的是真正的执行路径**,
 * 不是某个被简化过的测试专用入口。
 */
final class LumaHarness {

    private LumaHarness() {
    }

    /** 一次执行的结果:输出 + 运行时错误(没有错误则为 null)。 */
    static final class Result {
        final String output;
        final String error;

        Result(String output, String error) {
            this.output = output;
            this.error = error;
        }
    }

    /**
     * 执行源码。
     *
     * <p>注意即使中途出错,<b>出错之前产生的输出也会保留</b>——
     * 这正是"运行时错误会中止后续语句"这类测试需要的信息。
     */
    static Result exec(String source) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        String error = null;

        try {
            System.setOut(new PrintStream(buffer, true, "UTF-8"));
            // 语法错误会往 stderr 写,单元测试不关心,丢掉
            System.setErr(new PrintStream(new ByteArrayOutputStream(), true, "UTF-8"));

            Interpreter interpreter = new Interpreter();
            List<Token> tokens = new Scanner(source).scanTokens();
            List<Stmt> statements = new Parser(tokens).parse();
            interpreter.interpret(statements);
        } catch (RuntimeError e) {
            error = e.getMessage();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        try {
            return new Result(buffer.toString("UTF-8"), error);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 只要输出。 */
    static String run(String source) {
        return exec(source).output;
    }

    /** 只要运行时错误文本;没有错误返回 null。 */
    static String runtimeErrorOf(String source) {
        return exec(source).error;
    }

    /** 扫描一段源码,返回 token 类型序列(不含 EOF),方便断言。 */
    static String tokenTypesOf(String source) {
        StringBuilder sb = new StringBuilder();
        for (Token token : new Scanner(source).scanTokens()) {
            if (token.type == TokenType.EOF) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token.type);
        }
        return sb.toString();
    }
}
