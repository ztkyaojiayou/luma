package luma;

/**
 * 词法单元的类型。
 *
 * <p>Scanner 把源码字符流切成这组类型,Parsed 再按它们判断语法结构。
 * 枚举值是"语法层面的种类",不等于运算优先级——优先级由 Parser 的方法调用层级体现。
 */
public enum TokenType {

    // ---- 单字符符号 ----
    LEFT_PAREN, RIGHT_PAREN,       // ( )
    LEFT_BRACE, RIGHT_BRACE,       // { }
    LEFT_BRACKET, RIGHT_BRACKET,   // [ ]
    COMMA, SEMICOLON, DOT,         // , ; .
    PLUS, MINUS, STAR, SLASH, PERCENT,  // + - * / %

    // ---- 单/双字符运算符 ----
    BANG, BANG_EQUAL,              // !  !=
    EQUAL, EQUAL_EQUAL,            // =  ==
    LESS, LESS_EQUAL,              // <  <=
    GREATER, GREATER_EQUAL,        // >  >=
    AND_AND, OR_OR,                // && ||

    // ---- 字面量与标识符 ----
    IDENTIFIER,                    // 变量名 / 函数名
    STRING,                        // "文本"
    NUMBER,                        // 123 / 3.14

    // ---- 关键字(共 16 个)----
    LET, FUNC, IF, ELSE, WHILE, FOR, RETURN, PRINT,
    TRUE, FALSE, NULL,

    EOF                            // 文件结束,Parser 的终止哨兵
}
