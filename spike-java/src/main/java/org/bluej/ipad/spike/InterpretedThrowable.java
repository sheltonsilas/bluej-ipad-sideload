package org.bluej.ipad.spike;

import java.util.Objects;

/**
 * A Java exception raised while interpreting bytecode.
 *
 * <p>It carries the interpreted class name so {@code catch} blocks and the UI can present
 * the failure the way the student's program would see it. It is a host
 * {@link RuntimeException} only so it can cross the interpreter API boundary; student code
 * catches it through the interpreter's own exception table, not through host try/catch.
 */
public final class InterpretedThrowable extends RuntimeException {
    private final String className;
    private final InterpretedObject object;

    public InterpretedThrowable(String className, String message) {
        this(className, message, null);
    }

    InterpretedThrowable(String className, String message, InterpretedObject object) {
        super(message == null ? className : message);
        this.className = Objects.requireNonNull(className);
        this.object = object;
    }

    public String className() { return className; }

    /** The interpreted exception instance when one was constructed, otherwise {@code null}. */
    public InterpretedObject object() { return object; }

    @Override public String toString() { return className + (getMessage() == null ? "" : ": " + getMessage()); }
}
