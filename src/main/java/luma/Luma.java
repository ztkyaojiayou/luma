package luma;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 程序入口:负责命令行、REPL,以及所有错误信息的最终展示格式。
 *
 * <p>错误报告集中放在这里,而不是散落在 Scanner/Parser/Interpreter 里,
 * 是为了保证三种错误(语法、运行时、用法)长得一致——用户只需要认一种格式。
 *
 * <p>退出码沿用 UNIX 惯例(见 SPEC §7.1):
 * 64 用法错误 / 65 语法错误 / 70 运行时错误。脚本调用者可以靠它判断失败原因。
 */
public class Luma {

    /** 求值器全局唯一。REPL 里它会被反复复用,所以变量能跨行保留。 */
    private static final Interpreter interpreter = new Interpreter();

    /** 本次运行是否出现过语法错误。 */
    private static boolean hadError = false;

    /** 本次运行是否出现过运行时错误。 */
    private static boolean hadRuntimeError = false;

    /** 当前处理的源码按行拆开,报错时用来打印出错的整行和插入符。 */
    private static List<String> sourceLines = new ArrayList<String>();

    public static void main(String[] args) throws IOException {
        // Java 8 的默认编码取自 LANG,换个环境就可能不是 UTF-8。
        // 这里强制用 UTF-8 输出,免得中文报错在别的终端里变乱码。
        System.setOut(new PrintStream(System.out, true, "UTF-8"));

        if (args.length > 1) {
            System.err.println("用法: luma [脚本.luma]");
            System.err.println("  不带参数进入交互模式(REPL)");
            System.exit(64);
        }
        if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    // ==================================================================
    // 两种运行方式
    // ==================================================================

    private static void runFile(String path) throws IOException {
        String source;
        try {
            // 显式指定 UTF-8,不依赖 file.encoding
            source = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            System.err.println("找不到文件: " + path);
            System.exit(64);
            return;
        }

        run(source);

        // 语法错误优先于运行时错误:语法都没通过,根本没执行过
        if (hadError) {
            System.exit(65);
        }
        if (hadRuntimeError) {
            System.exit(70);
        }
    }

    /**
     * 交互模式。每行单独扫描和解析,但**共用同一个 Interpreter**——
     * 所以上一行定义的变量和函数,下一行还能用。
     */
    private static void runPrompt() throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.println("Luma 0.4  —— 输入语句回车执行,print 输出结果,exit 退出");
        System.out.println();

        while (true) {
            System.out.print("> ");
            System.out.flush();

            String line = reader.readLine();
            if (line == null || line.trim().equals("exit")) {
                break;
            }
            if (line.trim().isEmpty()) {
                continue;
            }

            run(line);

            // REPL 里每行都是独立的一次尝试,错误不该累积到下一行
            hadError = false;
            hadRuntimeError = false;
        }
    }

    // ==================================================================
    // 流水线:扫描 → 解析 → 求值
    // ==================================================================

    private static void run(String source) {
        sourceLines = splitLines(source);

        luma.Scanner scanner = new luma.Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // 有语法错误就不执行 —— 语法树是残缺的,执行它只会产生莫名其妙的运行时错误
        if (hadError) {
            return;
        }

        try {
            interpreter.interpret(statements);
        } catch (RuntimeError error) {
            reportRuntimeError(error);
            hadRuntimeError = true;
        } catch (StackOverflowError overflow) {
            // 兜底:如果用户没用 -Xss8m 运行(默认栈只够 700 层左右),
            // 会先撞上 JVM 栈溢出,而 Spec 规定的 1000 层检查还没来得及生效。
            // 这时栈已经展开,把它转成和普通运行时错误一致的格式。
            System.err.println("运行时错误: 调用层数过深(JVM 栈空间耗尽),可能是无限递归");
            System.err.println("    提示: 用 -Xss8m 运行可以让上限达到 1000 层,见 run.sh");
            hadRuntimeError = true;
        }
    }

    private static List<String> splitLines(String source) {
        List<String> lines = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lines.add(source.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(source.substring(start));
        return lines;
    }

    // ==================================================================
    // 错误报告 —— 三种错误共用一套格式
    // ==================================================================

    /**
     * 语法/词法错误。由 Scanner 和 Parser 调用。
     *
     * <p>输出形如:
     * <pre>
     *   [第 3 行] 语法错误: if 的条件后面缺少 ')'
     *       if (x &gt; 1 {
     *                 ^
     * </pre>
     */
    static void syntaxError(int line, int column, String message) {
        hadError = true;
        System.err.println("[第 " + line + " 行] 语法错误: " + message);

        if (line >= 1 && line <= sourceLines.size()) {
            String text = sourceLines.get(line - 1);
            System.err.println("    " + text);
            System.err.println("    " + caretFor(text, column));
        }
    }

    /**
     * 运行时错误。带上调用栈,让人看出错误是在哪条调用链上发生的。
     *
     * <p>输出形如:
     * <pre>
     *   [第 7 行] 运行时错误: 未定义的变量 'foo'
     *       在 函数 'bar' 中调用 (第 12 行)
     *       在 顶层 中调用 (第 20 行)
     * </pre>
     */
    private static void reportRuntimeError(RuntimeError error) {
        // 理论上不该发生(内建函数抛错会在调用点补 token),兜底防 NPE
        int line = error.token == null ? 0 : error.token.line;
        System.err.println("[第 " + line + " 行] 运行时错误: " + error.getMessage());

        // 用异常自带快照,而不是现场问解释器 —— 那时调用栈已经被弹空了
        if (error.stackLines() != null) {
            for (String frame : error.stackLines()) {
                System.err.println(frame);
            }
        }
    }

    /** 生成插入符那一行。中日韩字符占两列,要按显示宽度补空格才能对齐。 */
    private static String caretFor(String text, int column) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(column - 1, text.length());
        for (int i = 0; i < limit; i++) {
            char c = text.charAt(i);
            if (c == '\t') {
                sb.append('\t');
            } else if (isWide(c)) {
                sb.append("  "); // 一个全角字符占两列
            } else {
                sb.append(' ');
            }
        }
        return sb.append('^').toString();
    }

    /** 判断是不是"占两列"的东亚宽字符,用于对齐插入符。 */
    private static boolean isWide(char c) {
        return (c >= 0x1100 && c <= 0x115F)
                || (c >= 0x2E80 && c <= 0xA4CF)
                || (c >= 0xAC00 && c <= 0xD7A3)
                || (c >= 0xF900 && c <= 0xFAFF)
                || (c >= 0xFE30 && c <= 0xFE6F)
                || (c >= 0xFF00 && c <= 0xFF60)
                || (c >= 0xFFE0 && c <= 0xFFE6);
    }
}
