package luma;

import java.util.HashMap;
import java.util.Map;

/**
 * 一个作用域:从"名字"到"值"的映射,外加一条指向外层的链。
 *
 * <p>整条链就是"作用域链"。查变量时从当前环境往上找,找到最外层还找不到就是未定义。
 * 闭包的实现几乎全部落在这里——函数对象记住自己定义时的那个 Environment,
 * 以后再调用时用那个环境当父节点建新环境,于是"离开作用域之后变量还在"就自然发生了。
 */
public class Environment {

    /** 外层环境。全局环境为 null。 */
    final Environment enclosing;

    /** 本层的变量表。用 Object 存值,因为 Luma 是动态类型的。 */
    private final Map<String, Object> values = new HashMap<String, Object>();

    /** 创建全局环境。 */
    public Environment() {
        this(null);
    }

    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    /** 在本层新建一个变量。已存在则覆盖——{@code let} 允许遮蔽。 */
    void define(String name, Object value) {
        values.put(name, value);
    }

    /**
     * 读取变量。从本层开始逐层向外找。
     *
     * <p>注意这里用 {@code containsKey} 而不是判断 {@code get() != null}:
     * Luma 里 null 是一个合法的值,不能把它和"不存在"混为一谈。
     */
    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }
        if (enclosing != null) {
            return enclosing.get(name);
        }
        throw new RuntimeError(name, "未定义的变量 '" + name.lexeme + "'");
    }

    /**
     * 给**已存在**的变量赋值。
     *
     * <p>和 define 的区别很关键:找不到就报错,绝不隐式创建。
     * 这点和 JavaScript 相反——JS 里 {@code x = 1} 会悄悄建一个全局变量,
     * 是大量诡异 bug 的来源。
     */
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeError(name, "不能给未声明的变量 '" + name.lexeme + "' 赋值");
    }
}
