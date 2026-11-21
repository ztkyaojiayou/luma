package luma;

import java.util.List;

/**
 * "可以被调用"的东西。
 *
 * <p>这是 Luma 里唯一用到的多态。用户写的函数({@link LumaFunction})和
 * 内建函数({@link NativeFunctions} 里的 clock / push)都实现它,
 * 于是求值器碰到 Call 节点时只需要认这个接口,不必关心具体是谁。
 *
 * <p>这正对应 SPEC §6.7 的说法:内建函数不是特例,它和用户函数在调用那一刻没有区别。
 */
public interface LumaCallable {

    /** 参数个数。调用前先比对,数量不对就报错,而不是让函数体里莫名其妙地炸。 */
    int arity();

    /**
     * 执行调用。
     *
     * @param interpreter 求值器本身,用户函数需要它来执行函数体
     * @param arguments   实参列表,长度已由调用方保证等于 {@link #arity()}
     * @return 返回值;没有 return 语句的函数返回 null
     */
    Object call(Interpreter interpreter, List<Object> arguments);
}
