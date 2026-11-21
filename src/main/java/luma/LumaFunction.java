package luma;

import java.util.List;

/**
 * 用户用 {@code func} 定义的函数,外加它捕获的环境。
 *
 * <p><b>闭包的全部秘密就是 {@link #closure} 这一个字段。</b>
 * 函数被创建时,把"当时所在的环境"整个记住;之后无论在哪里被调用,
 * 都拿这个环境当父作用域。于是函数体里能访问到"定义处的局部变量",
 * 哪怕那些变量所在的作用域早就执行完了。
 *
 * <p>递归深度上限也在这里检查——否则 {@code func f() { return f(); }} 会直接把 JVM 栈打爆,
 * 用户看到的将是一个看不懂的 StackOverflowError 而不是清楚的中文报错。
 */
public class LumaFunction implements LumaCallable {

    /** 语法树上的函数声明,提供名字、参数和函数体。 */
    private final Stmt.Function declaration;

    /** 定义这个函数时所在的环境。闭包就靠它。 */
    private final Environment closure;

    LumaFunction(Stmt.Function declaration, Environment closure) {
        this.declaration = declaration;
        this.closure = closure;
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    /** 函数名。报错时展示调用栈需要它。 */
    String functionName() {
        return declaration.name.lexeme;
    }

    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // 每次调用都新建一层作用域,父节点是"定义处"的环境而不是"调用处"的环境。
        // 这一行是词法作用域的核心:变量去哪找,由定义位置决定,不由调用位置决定。
        Environment environment = new Environment(closure);

        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (ReturnSignal signal) {
            // return 是靠异常跳出来的,在这里接住并当成函数调用的返回值
            return signal.value;
        }
        // 函数体跑完却没有遇到 return —— 返回 null
        return null;
    }

    @Override
    public String toString() {
        return "<函数 " + declaration.name.lexeme + ">";
    }

    /**
     * {@code return} 语句的非局部跳转信号。
     *
     * <p>关闭了栈追踪({@code super(null, null, false, false)}),原因有二:
     * 一是 return 是正常控制流、不是异常,每次调用都抓栈太浪费;
     * 二是这个信号对外不可见,栈信息没有任何用处。
     */
    static class ReturnSignal extends RuntimeException {
        private static final long serialVersionUID = 1L;

        final Object value;

        ReturnSignal(Object value) {
            super(null, null, false, false);
            this.value = value;
        }
    }
}
