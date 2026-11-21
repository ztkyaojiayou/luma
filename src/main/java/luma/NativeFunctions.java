package luma;

import java.util.List;

/**
 * 内建函数:{@code clock()} 和 {@code push(a, x)}。
 *
 * <p>它们和用户函数走**完全相同**的调用路径——都实现 {@link LumaCallable},
 * 都被自己的名字绑定在全局环境里,求值器在 Call 节点上不区分它们。
 *
 * <p>这一点是刻意的(SPEC §6.7):如果把 {@code push} 做成求值器里的一个
 * "if (名字.equals("push"))"特判,用户就看不到"调用一个东西"这件事的统一性了。
 *
 * <p>{@code print} 不在这里,因为它是**语句**而不是函数(见 {@link Stmt.Print})。
 */
final class NativeFunctions {

    private NativeFunctions() {
        // 纯静态工具类,不允许实例化
    }

    /** 把内建函数装进全局环境。启动时调用一次。 */
    static void install(Environment globals) {
        globals.define("clock", new LumaCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                // 秒为单位的浮点数,和 Luma 只有 double 保持一致
                return System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<内建函数 clock>";
            }
        });

        globals.define("push", new LumaCallable() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object target = arguments.get(0);
                if (!(target instanceof LumaArray)) {
                    // 这里没有 token,Interpreter 会在调用点补上位置信息
                    throw new RuntimeError("push 的第一个参数必须是数组,实际是 " + Interpreter.typeName(target));
                }
                // 返回值是 null:push 的意义在于"修改了数组",不在于"返回了什么"。
                // 因为数组是引用,调用方本来就持有同一个对象,不需要再返回一次。
                ((LumaArray) target).push(arguments.get(1));
                return null;
            }

            @Override
            public String toString() {
                return "<内建函数 push>";
            }
        });
    }
}
