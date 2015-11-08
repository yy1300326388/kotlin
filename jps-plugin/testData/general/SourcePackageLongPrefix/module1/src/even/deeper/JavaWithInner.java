package good.prefix.even.deeper;

public class JavaWithInner {
    public interface A {}

    public static class B implements A {
        public interface D {
        }

        public static class C implements D {
        }
    }
}
