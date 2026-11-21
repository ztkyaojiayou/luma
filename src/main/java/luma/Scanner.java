package luma;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词法分析器:把源码字符流切成 {@link Token} 序列。
 *
 * <p>这是"源码 → 程序"这条流水线的第一站。它只做字符级的判断,
 * 不关心任何语义——比如它会把 {@code 1 + 2} 切成三个 token,
 * 但不会去算那个加法。
 *
 * <p>两个值得注意的实现点:
 * <ul>
 *   <li><b>双字符前瞻</b>:{@code !} 和 {@code !=} 都以前者开头,
 *       必须多看一眼下一个字符才能确定是哪一个。</li>
 *   <li><b>行列号</b>:每产出一个 token 都记下它的起点,
 *       这样后面报错才能画出精确的插入符 {@code ^}。</li>
 * </ul>
 */
public class Scanner {

    /** 关键字表。放在静态块里初始化一次,扫描时按字符串直接查。 */
    private static final Map<String, TokenType> KEYWORDS = new HashMap<String, TokenType>();

    static {
        KEYWORDS.put("let", TokenType.LET);
        KEYWORDS.put("func", TokenType.FUNC);
        KEYWORDS.put("if", TokenType.IF);
        KEYWORDS.put("else", TokenType.ELSE);
        KEYWORDS.put("while", TokenType.WHILE);
        KEYWORDS.put("for", TokenType.FOR);
        KEYWORDS.put("return", TokenType.RETURN);
        KEYWORDS.put("print", TokenType.PRINT);
        KEYWORDS.put("true", TokenType.TRUE);
        KEYWORDS.put("false", TokenType.FALSE);
        KEYWORDS.put("null", TokenType.NULL);
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<Token>();

    /** 当前 token 的起始下标。 */
    private int start = 0;

    /** 扫描游标,指向"下一个待处理"的字符。 */
    private int current = 0;

    /** 当前行号,从 1 开始。 */
    private int line = 1;

    /** 当前行第一个字符的下标,用来把绝对下标换算成列号。 */
    private int lineStart = 0;

    public Scanner(String source) {
        this.source = source;
    }

    /**
     * 扫完整个源码。
     *
     * <p>即使中途遇到词法错误也会继续扫下去——这样一次运行能报出所有问题,
     * 而不是改一个错跑一次。最后一定会追加 EOF,让 Parser 有明确的终止信号。
     */
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            // 每轮开始时,把 start 对齐到 current:上一个 token 已结束,新 token 从这里起算
            start = current;
            scanToken();
        }
        tokens.add(new Token(TokenType.EOF, "", null, line, columnAt(current)));
        return tokens;
    }

    /** 识别一个 token。这是整个词法分析的主循环体。 */
    private void scanToken() {
        char c = advance();
        switch (c) {
            // ---- 单字符符号 ----
            case '(': addToken(TokenType.LEFT_PAREN); break;
            case ')': addToken(TokenType.RIGHT_PAREN); break;
            case '{': addToken(TokenType.LEFT_BRACE); break;
            case '}': addToken(TokenType.RIGHT_BRACE); break;
            case '[': addToken(TokenType.LEFT_BRACKET); break;
            case ']': addToken(TokenType.RIGHT_BRACKET); break;
            case ',': addToken(TokenType.COMMA); break;
            case ';': addToken(TokenType.SEMICOLON); break;
            case '.': addToken(TokenType.DOT); break;
            case '+': addToken(TokenType.PLUS); break;
            case '-': addToken(TokenType.MINUS); break;
            case '*': addToken(TokenType.STAR); break;
            case '%': addToken(TokenType.PERCENT); break;

            // ---- 单字符或双字符运算符:多看一眼下一个字符 ----
            case '!': addToken(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG); break;
            case '=': addToken(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL); break;
            case '<': addToken(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS); break;
            case '>': addToken(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER); break;

            // && 和 || 没有单字符版本 —— 单独一个 & 或 | 是错误,而不是某种"位运算"
            case '&':
                if (match('&')) {
                    addToken(TokenType.AND_AND);
                } else {
                    Luma.syntaxError(line, columnAt(current - 1), "单个 '&' 不是合法运算符,你是不是想写 '&&'?");
                }
                break;
            case '|':
                if (match('|')) {
                    addToken(TokenType.OR_OR);
                } else {
                    Luma.syntaxError(line, columnAt(current - 1), "单个 '|' 不是合法运算符,你是不是想写 '||'?");
                }
                break;

            // ---- 空白:换行要单独处理,因为它决定行号 ----
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                lineStart = current;
                break;

            // ---- 注释:// 到行尾全部丢弃 ----
            case '/':
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
                } else {
                    addToken(TokenType.SLASH);
                }
                break;

            // ---- 字符串字面量 ----
            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    identifier();
                } else {
                    Luma.syntaxError(line, columnAt(current - 1), "无法识别的字符 '" + c + "'");
                }
                break;
        }
    }

    /** 扫描标识符或关键字。两者词法上一样,靠查表区分。 */
    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        String text = source.substring(start, current);
        TokenType type = KEYWORDS.get(text);
        // 不在关键字表里的,就是普通标识符
        addToken(type == null ? TokenType.IDENTIFIER : type);
    }

    /** 扫描数字。Luma 只有 double,所以小数字面量统一转成 Double。 */
    private void number() {
        while (isDigit(peek())) {
            advance();
        }
        // 只有小数点后面紧跟数字才算小数部分,这样可以支持 "1." 之外的写法
        if (peek() == '.' && isDigit(peekNext())) {
            advance(); // 吃掉 '.'
            while (isDigit(peek())) {
                advance();
            }
        }
        String text = source.substring(start, current);
        addToken(TokenType.NUMBER, Double.parseDouble(text));
    }

    /** 扫描字符串字面量,处理 \n \t \" \\ 四种转义。 */
    private void string() {
        StringBuilder value = new StringBuilder();
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                // 字符串可以跨行,但行号要跟着走,否则后面的报错会错位
                line++;
                lineStart = current + 1;
            }
            char c = advance();
            if (c == '\\' && !isAtEnd()) {
                char escaped = advance();
                switch (escaped) {
                    case 'n': value.append('\n'); break;
                    case 't': value.append('\t'); break;
                    case '"': value.append('"'); break;
                    case '\\': value.append('\\'); break;
                    default:
                        // 未知转义按原样保留,不静默吞掉——否则 "为什么要吃掉我的 \$" 会很难查
                        Luma.syntaxError(line, columnAt(current - 1), "不支持的转义符 '\\" + escaped + "'");
                        value.append(escaped);
                        break;
                }
            } else {
                value.append(c);
            }
        }

        if (isAtEnd()) {
            Luma.syntaxError(line, columnAt(start), "字符串没有闭合的 '\"'");
            return;
        }

        advance(); // 吃掉结尾的引号
        addToken(TokenType.STRING, value.toString());
    }

    // ---------- 字符扫描的基础设施 ----------

    private boolean isAtEnd() {
        return current >= source.length();
    }

    /** 消费并返回下一个字符。注意:索引越界时返回 '\0',让调用方不必到处判空。 */
    private char advance() {
        return source.charAt(current++);
    }

    /** 前瞻一个字符,不消费。到头了返回 '\0'。 */
    private char peek() {
        return isAtEnd() ? '\0' : source.charAt(current);
    }

    /** 前瞻两个字符,用于判断 "1.5" 里的小数点是不是真的属于数字。 */
    private char peekNext() {
        return current + 1 >= source.length() ? '\0' : source.charAt(current + 1);
    }

    /** 只有当前字符等于 expected 才消费它,并返回是否消费。双字符运算符靠这个收敛。 */
    private boolean match(char expected) {
        if (isAtEnd() || source.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private static boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    /** 把绝对下标换算成"列",从 1 开始。 */
    private int columnAt(int index) {
        return index - lineStart + 1;
    }

    private void addToken(TokenType type) {
        addToken(type, null);
    }

    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, columnAt(start)));
    }
}
