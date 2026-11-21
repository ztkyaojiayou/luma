package luma;

/**
 * 一个词法单元。Scanner 的产物、Parser 的输入。
 *
 * <p>不可变:创建后不再修改。这保证了 Parser 可以自由地回看已消费的 token。
 */
public class Token {

    /** 语法种类,Parser 主要靠它做判断。 */
    final TokenType type;

    /** 源码中的原始文本。报错时直接展示给人看,例如 "foo"、">=" 这种。 */
    final String lexeme;

    /** 字面量的值:NUMBER → Double,STRING → String,其余为 null。 */
    final Object literal;

    /** 所在行号(从 1 开始),用于报错。 */
    final int line;

    /** 所在列号(从 1 开始,按字符计),用于在报错里画插入符 ^。 */
    final int column;

    Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
    }

    @Override
    public String toString() {
        return type + " '" + lexeme + "'";
    }
}
