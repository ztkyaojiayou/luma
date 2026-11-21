package luma;

import java.util.ArrayList;
import java.util.List;

/**
 * Luma 的数组,底层就是一个 {@code List<Object>}。
 *
 * <p><b>它是引用类型。</b>这是整个语言里唯一"看不见"的机制(见 SPEC §6.6):
 * {@code let b = a;} 之后 b 和 a 指向同一个 LumaArray 对象,不是复制。
 * 所以这里刻意**不提供 copy 方法**——想复制就得自己写循环,
 * 这样"复制是要付出代价的"这件事不会被藏起来。
 *
 * <p>本类只负责存取,不负责报错。越界、下标类型不对这些判断放在
 * {@link Interpreter} 里,因为那里才有 token,能报出准确的行号和列号。
 */
public class LumaArray {

    private final List<Object> elements = new ArrayList<Object>();

    int size() {
        return elements.size();
    }

    Object get(int index) {
        return elements.get(index);
    }

    void set(int index, Object value) {
        elements.set(index, value);
    }

    /** 追加到末尾。Luma 里只能通过内建函数 {@code push(a, x)} 调到。 */
    void push(Object value) {
        elements.add(value);
    }

    /**
     * 打印时的样子,例如 {@code [1, a, true]}。
     *
     * <p>元素交给 {@link Interpreter#stringify} 逐个转换,保证和直接 print 单个值的格式一致。
     * 注意这里**不处理自引用**(比如把数组塞进它自己)——那会无限递归。
     * 教学语言不做环检测,但要知道这是个边界。
     */
    String display() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(Interpreter.stringify(elements.get(i)));
        }
        return sb.append("]").toString();
    }
}
