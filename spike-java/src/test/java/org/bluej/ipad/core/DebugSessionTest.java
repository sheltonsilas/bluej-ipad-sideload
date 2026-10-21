package org.bluej.ipad.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.bluej.ipad.spike.Value;
import org.junit.jupiter.api.Test;

class DebugSessionTest {

    private static final String LOOPER = """
            public class Looper {
                public static void main(String[] args) {
                    int total = 0;
                    for (int i = 1; i <= 3; i++) {
                        total = total + i;
                    }
                    System.out.println("total " + total);
                }
            }
            """;

    private static final int LOOP_BODY = 5;
    private static final int PRINT_LINE = 7;

    private static CoreSession loopingSession() {
        CoreSession session = new CoreSession();
        session.setSource("Looper.java", LOOPER);
        CoreSession.CompileReport report = session.compile();
        assertTrue(report.success(), () -> "diagnostics: " + report.diagnostics());
        return session;
    }

    @Test
    void stopsAtABreakpointAndExposesTheFrame() {
        CoreSession session = loopingSession();
        session.setBreakpoints(List.of("Looper#" + LOOP_BODY));

        DebugSession.Snapshot paused = session.debugStart("Looper", "main",
                List.of(Value.NullValue.INSTANCE));

        assertEquals("paused", paused.status());
        assertTrue(paused.active());
        assertEquals(1, paused.frames().size(), "the stack shows the paused method");
        DebugSession.Frame frame = paused.frames().get(0);
        assertEquals("Looper", frame.className());
        assertEquals("main", frame.method());
        assertEquals(LOOP_BODY, frame.line());
        assertEquals(new Value.IntValue(0), local(frame, "total"));
        assertEquals(new Value.IntValue(1), local(frame, "i"));

        // Changing the program under a paused thread is refused rather than corrupting it.
        assertThrows(IllegalStateException.class, session::compile);
        assertThrows(IllegalStateException.class, () -> session.invokeStatic("Looper", "main",
                List.of(Value.NullValue.INSTANCE)));
    }

    @Test
    void stepsLineByLineAndRunsToCompletion() {
        CoreSession session = loopingSession();
        session.setBreakpoints(List.of("Looper#" + LOOP_BODY));
        session.debugStart("Looper", "main", List.of(Value.NullValue.INSTANCE));

        DebugSession.Snapshot afterBody = session.debugCommand("stepOver");
        assertEquals("paused", afterBody.status());
        DebugSession.Frame frame = afterBody.frames().get(0);
        assertEquals(4, frame.line(), "the loop header is the next source line");
        assertEquals(new Value.IntValue(1), local(frame, "total"));
        // javac maps the loop increment to the `for` line, so the step stops before i++.
        assertEquals(new Value.IntValue(1), local(frame, "i"));

        DebugSession.Snapshot again = session.debugCommand("stepOver");
        assertEquals(LOOP_BODY, again.frames().get(0).line());

        // A breakpoint inside a loop has to fire on every pass, not just the first one.
        DebugSession.Snapshot third = session.debugCommand("continue");
        assertEquals("paused", third.status());
        assertEquals(LOOP_BODY, third.frames().get(0).line());
        assertEquals(new Value.IntValue(3), local(third.frames().get(0), "i"));

        DebugSession.Snapshot finished = session.debugCommand("continue");
        assertEquals("finished", finished.status());
        assertFalse(finished.active());
        assertTrue(finished.frames().isEmpty());
        assertEquals("total 6\n", finished.output());
    }

    @Test
    void aBreakpointAfterTheLoopSeesTheFinalValues() {
        CoreSession session = loopingSession();
        session.setBreakpoints(List.of("Looper#" + PRINT_LINE));

        DebugSession.Snapshot paused = session.debugStart("Looper", "main",
                List.of(Value.NullValue.INSTANCE));

        assertEquals("paused", paused.status());
        DebugSession.Frame frame = paused.frames().get(0);
        assertEquals(PRINT_LINE, frame.line());
        assertEquals(new Value.IntValue(6), local(frame, "total"));
        // The loop variable is out of scope after the loop, so it is not listed here.
        assertTrue(frame.locals().stream().noneMatch(variable -> variable.name().equals("i")));

        DebugSession.Snapshot finished = session.debugCommand("continue");
        assertEquals("finished", finished.status());
        assertEquals("total 6\n", finished.output());
    }

    @Test
    void stepIntoCallsAndStepsBackOut() {
        CoreSession session = new CoreSession();
        session.setSource("Nested.java", """
                public class Nested {
                    public static int doubleIt(int value) {
                        int doubled = value * 2;
                        return doubled;
                    }
                    public static void main(String[] args) {
                        int result = doubleIt(21);
                        System.out.println(result);
                    }
                }
                """);
        assertTrue(session.compile().success());
        session.setBreakpoints(List.of("Nested#7"));

        DebugSession.Snapshot paused = session.debugStart("Nested", "main",
                List.of(Value.NullValue.INSTANCE));
        assertEquals(7, paused.frames().get(0).line());

        DebugSession.Snapshot inside = session.debugCommand("stepInto");
        assertEquals("paused", inside.status());
        DebugSession.Frame callee = inside.frames().get(0);
        assertEquals("doubleIt", callee.method());
        assertEquals(3, callee.line());
        assertEquals(new Value.IntValue(21), local(callee, "value"));
        assertEquals(2, inside.frames().size(), "the caller stays on the stack");

        DebugSession.Snapshot back = session.debugCommand("stepOut");
        assertEquals("paused", back.status());
        assertEquals(1, back.frames().size());
        assertEquals("main", back.frames().get(0).method());
        assertEquals(8, back.frames().get(0).line());

        assertEquals("finished", session.debugCommand("continue").status());
    }

    @Test
    void stoppingEndsTheSessionAndLetsTheProjectCompileAgain() {
        CoreSession session = loopingSession();
        session.setBreakpoints(List.of("Looper#" + LOOP_BODY));
        session.debugStart("Looper", "main", List.of(Value.NullValue.INSTANCE));

        DebugSession.Snapshot stopped = session.debugCommand("stop");
        assertEquals("terminated", stopped.status());
        assertFalse(stopped.active());
        assertDoesNotThrow(session::compile);
    }

    @Test
    void withoutBreakpointsDebuggingStopsAtTheFirstLine() {
        CoreSession session = loopingSession();
        session.setBreakpoints(List.of());

        DebugSession.Snapshot started = session.debugStart("Looper", "main",
                List.of(Value.NullValue.INSTANCE));

        assertEquals("paused", started.status());
        assertEquals(3, started.frames().get(0).line(), "stop on the first statement of main");
        assertEquals("main", started.frames().get(0).method());

        DebugSession.Snapshot next = session.debugCommand("stepOver");
        assertEquals(new Value.IntValue(0), local(next.frames().get(0), "total"));
        assertEquals("finished", session.debugCommand("continue").status());
    }

    @Test
    void anUncaughtExceptionFinishesTheSessionAsFailed() {
        CoreSession session = new CoreSession();
        session.setSource("Boom.java", """
                public class Boom {
                    public static void main(String[] args) {
                        int zero = 0;
                        System.out.println(1 / zero);
                    }
                }
                """);
        assertTrue(session.compile().success());

        DebugSession.Snapshot paused = session.debugStart("Boom", "main",
                List.of(Value.NullValue.INSTANCE));
        assertEquals("paused", paused.status(), "a run without breakpoints stops at the first line");

        DebugSession.Snapshot failed = session.debugCommand("continue");
        assertEquals("failed", failed.status());
        assertEquals("java.lang.ArithmeticException", failed.errorClass());
        assertFalse(failed.active());
    }

    private static Value local(DebugSession.Frame frame, String name) {
        return frame.locals().stream()
                .filter(variable -> variable.name().equals(name))
                .map(DebugSession.Variable::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no local named " + name
                        + " in " + frame.locals()));
    }
}
