package org.bluej.ipad.core;

import java.util.Map;

/**
 * One object on the bench: a stable handle the UI keeps, the BlueJ-style instance name
 * ({@code counter1}), its class name and a snapshot of its visible fields. Handles are
 * session-scoped and never reused.
 */
public record BenchObject(int handle, String name, String className, Map<String, org.bluej.ipad.spike.Value> fields) {}
