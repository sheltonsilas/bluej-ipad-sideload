package org.bluej.ipad.core;

import java.util.List;

/**
 * Class metadata handed to the UI. It is deliberately free of ASM and interpreter types so
 * the Swift shell can consume it without knowing how the runtime is implemented.
 *
 * <p>{@code name} is the binary name, {@code path} the project-relative source file that
 * declares it (empty when the class came from precompiled bytecode), and the two flags let
 * the diagram stripe uncompiled and erroneous classes the way BlueJ does.
 */
public record ClassInfo(
        String name,
        String kind,
        String superclass,
        String path,
        boolean compiled,
        boolean error,
        List<Field> fields,
        List<Method> methods) {

    public record Field(String name, String type, boolean isStatic) {}

    public record Method(String name, List<String> parameterTypes, String returnType, boolean isStatic) {}
}
