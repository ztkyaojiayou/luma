package luma;

import java.util.ArrayList;
import java.util.List;

/**
 * 零依赖的最小断言工具。
 *
 * <p>为什么不用 JUnit?因为整个项目刻意不引入任何第三方依赖(见 README),
 * 装 JUnit 就要拉 jar 包,和"一个 jar 就能跑"的目标冲突。
 *
 * <p>这里的实现只有几十行,但它提供的三件事已经够用了:
 * 分组、断言、汇总。
 */
final class Assert {

    private static int passed = 0;
    private static int failed = 0;
    private static final List<String> failures = new ArrayList<String>();
    private static String suite = "(未分组)";

    private Assert() {
    }

    /** 开一个新的测试分组。 */
    static void suite(String name) {
        suite = name;
        System.out.println();
        System.out.println("── " + name + " ──");
    }

    /** 断言条件为真。 */
    static void that(String desc, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        fail(desc, "条件为假", "条件为真");
    }

    /** 断言两个值相等。null 安全。 */
    static void eq(String desc, Object expected, Object actual) {
        boolean ok = (expected == null) ? (actual == null) : expected.equals(actual);
        if (ok) {
            passed++;
            return;
        }
        fail(desc, String.valueOf(expected), String.valueOf(actual));
    }

    /** 断言 actual 里包含 needle。 */
    static void contains(String desc, String needle, String actual) {
        if (actual != null && actual.contains(needle)) {
            passed++;
            return;
        }
        fail(desc, "包含 [" + needle + "]", "实际 [" + actual + "]");
    }

    private static void fail(String desc, String expected, String actual) {
        failed++;
        String line = suite + " :: " + desc;
        failures.add(line + "   期望 [" + expected + "]  实际 [" + actual + "]");
        System.out.println("  ✗ " + desc);
        System.out.println("      期望 [" + expected + "]");
        System.out.println("      实际 [" + actual + "]");
    }

    /** 打印汇总,返回失败数(0 表示全绿)。 */
    static int summary() {
        System.out.println();
        System.out.println("════════════════════════════════════");
        if (failed == 0) {
            System.out.println("全部通过:" + passed + " 项断言");
        } else {
            System.out.println("失败 " + failed + " 项 / 共 " + (passed + failed) + " 项");
            System.out.println();
            for (String f : failures) {
                System.out.println("  • " + f);
            }
        }
        System.out.println("════════════════════════════════════");
        return failed;
    }
}
