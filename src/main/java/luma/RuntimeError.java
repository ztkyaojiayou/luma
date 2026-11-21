package luma;

/**
 * 运行时错误:代码语法没问题,但执行到某一步发现做不下去。
 *
 * <p>刻意继承 RuntimeException 而不是 Exception——解释器内部到处都要检查错误,
 * 用受检异常会让每个求值方法都挂着 throws,把真正的逻辑淹掉。
 *
 * <p>携带 token 是为了报错时能指出"哪一行、哪一列"。
 * token 允许为 null:内建函数(如 {@code push})内部出错时手上没有 token,
 * 这种情况由 {@link Interpreter} 在调用点补上,见 {@link #attachIfMissing}。
 */
public class RuntimeError extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 出错位置的 token。可能为 null,见类注释。 */
    Token token;

    /**
     * 出错那一刻的调用栈快照。
     *
     * <p>必须在这里存下来,不能等报告时再去问解释器——
     * {@link Interpreter} 的调用栈在异常向上传播的过程中会被 finally 逐层弹出,
     * 等 Luma 拿到这个异常时,栈已经空了。
     */
    private java.util.List<String> stackLines;

    RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }

    /** 给内建函数用:抛的时候还不知道调用发生在哪一行。 */
    RuntimeError(String message) {
        this(null, message);
    }

    /**
     * 如果还没有位置信息,就用调用点的 token 补上。
     *
     * <p>这样 {@code push(1, 2)} 报错时能指向用户写的那一行,
     * 而不是指向 JDK 内部某个不知道在哪的地方。
     */
    void attachIfMissing(Token fallback) {
        if (token == null) {
            token = fallback;
        }
    }

    /**
     * 在异常还没被外层接住之前,把当时的调用栈存成快照。
     *
     * <p>只记第一次——最内层(错误真正发生的地方)的栈才是完整的。
     */
    void captureStack(java.util.List<String> lines) {
        if (stackLines == null) {
            stackLines = lines;
        }
    }

    java.util.List<String> stackLines() {
        return stackLines;
    }
}
