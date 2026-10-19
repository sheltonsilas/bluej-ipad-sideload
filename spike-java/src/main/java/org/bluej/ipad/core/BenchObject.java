package org.bluej.ipad.core;

import java.util.Map;
import org.bluej.ipad.spike.Value;

/**
 * One object on the bench: a stable handle the UI keeps, its class name and a snapshot of
 * its visible fields. Handles are session-scoped and never reused.
 */
public record BenchObject(int handle, String className, Map<String, Value> fields) {}
