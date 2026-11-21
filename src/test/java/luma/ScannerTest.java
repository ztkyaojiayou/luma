package luma;

import java.util.List;

/** 词法分析器测试:只关心"字符 → token"这一步对不对。 */
final class ScannerTest {

    private ScannerTest() {
    }

    private static List<Token> scan(String source) {
        return new Scanner(source).scanTokens();
    }

    private static String types(String source) {
        return LumaHarness.tokenTypesOf(source);
    }

    private static Token first(String source) {
        return scan(source).get(0);
    }

    static void run() {
        keywords();
        identifiers();
        numbers();
        strings();
        operators();
        positions();
        comments();
    }

    private static void keywords() {
        Assert.suite("Scanner · 16 个关键字");

        String[][] expected = {
                {"let", "LET"}, {"func", "FUNC"}, {"if", "IF"}, {"else", "ELSE"},
                {"while", "WHILE"}, {"for", "FOR"}, {"return", "RETURN"},
                {"print", "PRINT"}, {"true", "TRUE"}, {"false", "FALSE"}, {"null", "NULL"},
        };
        for (String[] pair : expected) {
            Assert.eq("'" + pair[0] + "' 是关键字", pair[1], first(pair[0]).type.toString());
        }

        // 关键字数量是文档里承诺过的数字,这里用反射锁死,防止以后加减关键字忘了改文档
        Assert.eq("关键字数量(应与 SPEC/README 一致)", 11, keywordCount());
    }

    /** 读 Scanner 里的关键字表大小。 */
    private static int keywordCount() {
        try {
            java.lang.reflect.Field field = Scanner.class.getDeclaredField("KEYWORDS");
            field.setAccessible(true);
            java.util.Map<?, ?> keywords = (java.util.Map<?, ?>) field.get(null);
            return keywords.size();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读不到关键字表", e);
        }
    }

    private static void identifiers() {
        Assert.suite("Scanner · 标识符");

        Assert.eq("普通标识符", "IDENTIFIER", first("foo").type.toString());
        Assert.eq("下划线开头", "IDENTIFIER", first("_x").type.toString());
        Assert.eq("含数字", "IDENTIFIER", first("a1b2").type.toString());
        // 关键字只是"表里有的标识符",加个后缀就变普通名字
        Assert.eq("letx 不是关键字", "IDENTIFIER", first("letx").type.toString());
        Assert.eq("xlet 不是关键字", "IDENTIFIER", first("xlet").type.toString());
        Assert.eq("大小写敏感", "IDENTIFIER", first("Let").type.toString());
    }

    private static void numbers() {
        Assert.suite("Scanner · 数字");

        Assert.eq("整数转成 Double", 42.0, first("42").literal);
        Assert.eq("小数", 3.14, first("3.14").literal);
        // "1." 后面的点不是小数点,因为点后面没有数字 —— 应该切成 NUMBER + DOT
        Assert.eq("'1.' 切成两个 token", "NUMBER DOT", types("1."));
        Assert.eq("'1.5' 是一个 token", "NUMBER", types("1.5"));
        // "1..2" 同理:第一个点后面不是数字
        Assert.eq("'1..2' 的切分", "NUMBER DOT DOT NUMBER", types("1..2"));
    }

    private static void strings() {
        Assert.suite("Scanner · 字符串");

        Assert.eq("基本字符串", "hi", first("\"hi\"").literal);
        Assert.eq("转义换行", "a\nb", first("\"a\\nb\"").literal);
        Assert.eq("转义制表", "a\tb", first("\"a\\tb\"").literal);
        Assert.eq("转义引号", "a\"b", first("\"a\\\"b\"").literal);
        Assert.eq("转义反斜杠", "a\\b", first("\"a\\\\b\"").literal);
        Assert.eq("空字符串", "", first("\"\"").literal);
        Assert.eq("中文内容", "你好", first("\"你好\"").literal);
    }

    private static void operators() {
        Assert.suite("Scanner · 运算符");

        Assert.eq("双字符 != ", "BANG_EQUAL", first("!=").type.toString());
        Assert.eq("单字符 !", "BANG", first("!").type.toString());
        Assert.eq("== 与 = 的区别", "EQUAL_EQUAL EQUAL", types("== ="));
        Assert.eq("<= 与 <", "LESS_EQUAL LESS", types("<= <"));
        Assert.eq(">= 与 >", "GREATER_EQUAL GREATER", types(">= >"));
        Assert.eq("&&", "AND_AND", first("&&").type.toString());
        Assert.eq("||", "OR_OR", first("||").type.toString());
        Assert.eq("全部符号", "LEFT_PAREN RIGHT_PAREN LEFT_BRACE RIGHT_BRACE "
                        + "LEFT_BRACKET RIGHT_BRACKET COMMA SEMICOLON DOT",
                types("(){}[],;."));
        Assert.eq("算术符号", "PLUS MINUS STAR SLASH PERCENT", types("+-*/%"));
    }

    private static void positions() {
        Assert.suite("Scanner · 行列号");

        Token t = first("print");
        Assert.eq("第一行的行号", 1, t.line);
        Assert.eq("第一个 token 的列号", 1, t.column);

        // 换行之后行号要加,列号要重新从 1 开始
        List<Token> tokens = scan("let x = 1;\nlet y = 2;");
        Token secondLet = null;
        int seen = 0;
        for (Token token : tokens) {
            if (token.type == TokenType.LET) {
                seen++;
                if (seen == 2) {
                    secondLet = token;
                    break;
                }
            }
        }
        Assert.that("第二行有 let", secondLet != null);
        if (secondLet != null) {
            Assert.eq("第二行的行号", 2, secondLet.line);
            Assert.eq("第二行的列号", 1, secondLet.column);
        }

        // 同一行里靠后的 token,列号应该变大
        List<Token> line1 = scan("let abc = 1;");
        Assert.eq("abc 的列号", 5, line1.get(1).column);
    }

    private static void comments() {
        Assert.suite("Scanner · 注释");

        Assert.eq("行尾注释被跳过", "LET SEMICOLON", types("let // 这是注释\n;"));
        Assert.eq("注释里的符号不产出 token", "LET", types("let // + - * / ( )"));
        Assert.eq("斜杠仍然是除号", "SLASH", types("/"));
    }
}
