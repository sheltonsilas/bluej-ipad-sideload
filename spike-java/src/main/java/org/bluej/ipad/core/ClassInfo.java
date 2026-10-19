package org.bluej.ipad.core;

import java.util.List;

/**
 * Class metadata handed to the UI. It is deliberately free of ASM and interpreter types so
 * the Swift shell can consume it without knowing how the runtime is implemented.
 */
public record ClassInfo(String name, List<Field> fields, List<Method> methods) {

    public record Field(String name, String type, boolean isStatic) {}

    public record Method(String name, List<String> parameterTypes, String returnType, boolean isStatic) {}
}
