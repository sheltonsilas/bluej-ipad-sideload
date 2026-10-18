package bluej.example;

/**
 * Example "student program" interpreted by the BlueJ iPad runtime spike.
 *
 * This source is not compiled by Maven. It is compiled once by
 * {@code ci/ios-shell/tools/embed-counter.sh}, which embeds the resulting class file
 * as base64 in {@code BlueJShell} so that the interpreter can execute it on iOS,
 * where no JVM compiler is available.
 */
public class Counter {
    private int count;

    public Counter(int start) {
        this.count = start;
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
}
