package bluej.example;

/**
 * Example "student program" interpreted by the BlueJ iPad runtime spike.
 *
 * This source is not compiled by Maven. It is compiled once by
 * {@code ci/ios-shell/tools/embed-counter.sh}, which embeds the resulting class file
 * as base64 in {@code BlueJShell} so that the interpreter can execute it on iOS,
 * where no JVM compiler is available.
 *
 * It deliberately exercises the bytecode subset the interpreter claims to support:
 * fields, static state, loops, branches, recursion, arrays, doubles, string
 * concatenation and a caught runtime exception.
 */
public class Counter {
    private static int created;
    private int count;

    public Counter(int start) {
        this.count = start;
        created++;
    }

    public int increment(int amount) {
        this.count = this.count + amount;
        return this.count;
    }

    public int doubled() {
        return this.count * 2;
    }

    public static int arithmetic(int x) {
        return (x + 3) * 2;
    }

    public static String greeting() {
        return "hello iPad";
    }

    public static int sumTo(int limit) {
        int total = 0;
        for (int i = 1; i <= limit; i++) {
            if (i % 2 == 0) {
                total += i;
            } else {
                total += i * 2;
            }
        }
        return total;
    }

    public static int fibonacci(int n) {
        int a = 0;
        int b = 1;
        while (n > 0) {
            int next = a + b;
            a = b;
            b = next;
            n--;
        }
        return a;
    }

    public static double average(int a, int b) {
        return (a + b) / 2.0;
    }

    public static String describe(String name, int age) {
        return name + " is " + age + " years old";
    }

    public static String initials(String first, String last) {
        return "" + first.charAt(0) + last.charAt(0);
    }

    public static int total(int[] values) {
        int total = 0;
        for (int value : values) {
            total += value;
        }
        return total;
    }

    public static int safeDivide(int a, int b) {
        try {
            return a / b;
        } catch (ArithmeticException e) {
            return -1;
        }
    }

    public static int crash(int[] values) {
        return values[7];
    }

    public static int created() {
        return created;
    }
}
