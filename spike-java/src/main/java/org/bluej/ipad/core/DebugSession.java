package org.bluej.ipad.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bluej.ipad.spike.InterpretedObject;
import org.bluej.ipad.spike.Interpreter;
import org.bluej.ipad.spike.Value;

/**
 * A BlueJ-style debugger for interpreted code.
 *
 * <p>The interpreter calls {@link Interpreter.DebugListener#onLine} before it executes a source
 * line. When a breakpoint is hit, or a step finishes, this class parks the interpreter's own
 * thread on a monitor, answers the waiting request with a snapshot of the call stack, and only
 * lets execution continue when the next command arrives. Pausing therefore needs no
 * continuation support: the Java stack simply stays where it is.
 *
 * <p>One session drives one top-level call ({@code main} by default). While it is active the
 * core refuses to compile or reset, because both would pull the classes out from under the
 * paused thread.
 */
public final class DebugSession {

    public record Variable(String name, String type, Value value) {}

    public record Frame(String className, String method, int line, List<Variable> locals,
            List<Variable> fields) {}

    /**
     * One observation of the running program.
     *
     * @param status {@code paused}, {@code finished}, {@code failed} or {@code terminated}
     */
    public record Snapshot(String status, List<Frame> frames, Value value, String errorClass,
            String errorMessage, String output, boolean active) {}

    private enum Mode { CONTINUE, INTO, OVER, OUT }

    /** Thrown on the interpreter's thread to unwind it when the student stops debugging. */
    private static final class Abort extends RuntimeException {
        Abort() { super("debug session stopped", null, false, false); }
    }

    private final Interpreter interpreter;
    private final Object lock = new Object();
    private final Set<String> breakpoints = new HashSet<>();

    private Mode mode = Mode.CONTINUE;
    private int stopDepth;
    private boolean paused;
    private boolean abort;
    private boolean finished;
    private boolean active;
    private List<Frame> pausedFrames = List.of();
    private Value result;
    private String errorClass;
    private String errorMessage;

    public DebugSession(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    // ---------------------------------------------------------------- breakpoints

    /** Replaces the breakpoint set; each entry is {@code binary.ClassName#line}. */
    public void setBreakpoints(List<String> points) {
        synchronized (lock) {
            breakpoints.clear();
            for (String point : points) {
                if (point != null && point.contains("#")) breakpoints.add(point);
            }
        }
    }

    public List<String> breakpoints() {
        synchronized (lock) {
            List<String> sorted = new ArrayList<>(breakpoints);
            sorted.sort(String::compareTo);
            return List.copyOf(sorted);
        }
    }

    public boolean active() {
        synchronized (lock) { return active; }
    }

    // ---------------------------------------------------------------- running

    /** Runs a static method under the debugger and returns when it pauses or finishes. */
    public Snapshot start(String className, String method, List<Value> arguments) {
        synchronized (lock) {
            if (active) throw new IllegalStateException("a debug session is already running");
            active = true;
            paused = false;
            abort = false;
            finished = false;
            result = null;
            errorClass = null;
            errorMessage = null;
            pausedFrames = List.of();
            // With no breakpoints, debugging means "stop where the program starts" so the
            // student can step from the first line; with breakpoints, run to the first hit.
            mode = breakpoints.isEmpty() ? Mode.INTO : Mode.CONTINUE;
        }
        interpreter.setDebugListener(this::onLine);
        Thread worker = new Thread(() -> {
            try {
                Value value = interpreter.invokeStatic(className, method,
                        arguments.toArray(Value[]::new));
                complete("finished", value, null, null);
            } catch (Abort stopped) {
                complete("terminated", null, null, null);
            } catch (org.bluej.ipad.spike.InterpretedThrowable thrown) {
                complete("failed", null, thrown.className(), thrown.getMessage());
            } catch (RuntimeException failure) {
                complete("failed", null, failure.getClass().getName(), failure.getMessage());
            }
        }, "bluej-debuggee");
        worker.setDaemon(true);
        worker.start();
        return await();
    }

    /** Applies a debugger command and returns when the program pauses again or finishes. */
    public Snapshot command(String action) {
        synchronized (lock) {
            if (!active) return snapshot();
            if (finished) return snapshot();
            if (!paused) throw new IllegalStateException("the program is running");
            switch (action) {
                case "continue" -> mode = Mode.CONTINUE;
                case "stepInto" -> mode = Mode.INTO;
                case "stepOver" -> mode = Mode.OVER;
                case "stepOut" -> mode = Mode.OUT;
                case "stop" -> abort = true;
                default -> throw new IllegalArgumentException("unknown debug command: " + action);
            }
            paused = false;
            lock.notifyAll();
            while (!paused && !finished) {
                try {
                    lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return snapshot();
        }
    }

    /** The last observation, without changing the state. */
    public Snapshot snapshot() {
        synchronized (lock) {
            String status = paused ? "paused" : (finished ? lastStatus : "idle");
            return new Snapshot(status, pausedFrames, result, errorClass, errorMessage,
                    interpreter.output(), active);
        }
    }

    private volatile String lastStatus = "idle";

    // ---------------------------------------------------------------- interpreter callbacks

    private void onLine(Interpreter.FrameView frame) {
        synchronized (lock) {
            if (finished) return;
            if (abort) throw new Abort();
            if (!shouldStop(frame)) return;
            paused = true;
            stopDepth = frame.depth();
            pausedFrames = convert(interpreter.stack());
            lock.notifyAll();
            while (paused && !finished) {
                try {
                    lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    abort = true;
                    break;
                }
            }
            if (abort) throw new Abort();
        }
    }

    private boolean shouldStop(Interpreter.FrameView frame) {
        // The interpreter reports each line once per visit, so a breakpoint inside a loop fires
        // on every iteration without any extra bookkeeping here.
        if (breakpoints.contains(frame.className() + "#" + frame.line())) return true;
        return switch (mode) {
            case CONTINUE -> false;
            case INTO -> true;
            case OVER -> frame.depth() <= stopDepth;
            case OUT -> frame.depth() < stopDepth;
        };
    }

    private static List<Frame> convert(List<Interpreter.FrameView> frames) {
        List<Frame> converted = new ArrayList<>();
        for (Interpreter.FrameView frame : frames) {
            List<Variable> locals = new ArrayList<>();
            for (Interpreter.DebugVariable local : frame.locals()) {
                locals.add(new Variable(local.name(), local.type(), local.value()));
            }
            List<Variable> fields = new ArrayList<>();
            InterpretedObject receiver = frame.receiver();
            if (receiver != null) {
                receiver.fields().forEach((name, value) -> fields.add(new Variable(name, "field", value)));
            }
            converted.add(new Frame(frame.className(), frame.methodName(), frame.line(),
                    List.copyOf(locals), List.copyOf(fields)));
        }
        return List.copyOf(converted);
    }

    private void complete(String status, Value value, String failedClass, String failedMessage) {
        synchronized (lock) {
            lastStatus = status;
            result = value;
            errorClass = failedClass;
            errorMessage = failedMessage;
            paused = false;
            finished = true;
            active = false;
            pausedFrames = List.of();
            interpreter.setDebugListener(null);
            lock.notifyAll();
        }
    }

    private Snapshot await() {
        synchronized (lock) {
            while (!paused && !finished) {
                try {
                    lock.wait();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            return snapshot();
        }
    }
}
