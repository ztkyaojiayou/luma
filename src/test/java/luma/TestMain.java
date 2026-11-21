package luma;

/**
 * 测试入口。
 *
 * <p>运行: {@code ./test.sh}
 *
 * <p>为什么用 main 而不是 JUnit?因为项目坚持零第三方依赖(见 README),
 * 拉一个 JUnit jar 进来就破坏了"一个 jar 就能跑"。
 */
public class TestMain {

    public static void main(String[] args) {
        long started = System.currentTimeMillis();

        ScannerTest.run();
        ParserTest.run();
        EnvironmentTest.run();
        InterpreterTest.run();

        int failures = Assert.summary();
        long elapsed = System.currentTimeMillis() - started;
        System.out.println("耗时 " + elapsed + " ms");
        System.exit(failures == 0 ? 0 : 1);
    }
}
