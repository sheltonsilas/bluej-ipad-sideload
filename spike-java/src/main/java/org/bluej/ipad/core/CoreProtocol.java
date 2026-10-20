package org.bluej.ipad.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bluej.ipad.spike.InterpretedArray;
import org.bluej.ipad.spike.InterpretedObject;
import org.bluej.ipad.spike.InterpretedThrowable;
import org.bluej.ipad.spike.Value;

/**
 * JSON protocol between the native iPad UI and {@link CoreSession}.
 *
 * <p>The shell sends one JSON object per call and receives one JSON object. The bridge on the
 * iOS side only has to pass two strings in each direction; the protocol is deliberately
 * synchronous so the UI can treat every call as a request/response pair.
 *
 * <pre>
 * request                                                    response
 * {"op":"sources"}                                           {"sources":[{path,text,type}]}
 * {"op":"setSource","path":"Counter.java","text":"..."}      {"saved":true,"revision":3}
 * {"op":"newClass","name":"Dog","kind":"class"}              {"path":"Dog.java","text":"...","revision":4}
 * {"op":"compile"}                                           {success,revision,classes,diagnostics}
 * {"op":"classes"}                                           {"classes":[...]}
 * {"op":"new","class":"Counter","args":[{"int":4}]}          {"handle":1,"name":"counter1",...}
 * {"op":"invoke","handle":1,"method":"increment","args":[…]} {"value":{"int":9}}
 * {"op":"invokeStatic","class":"Counter","method":"sumTo"}   {"value":{"int":30}}
 * {"op":"inspect","handle":1}                                {"handle":1,"class":"Counter","fields":{…}}
 * {"op":"bench"}                                             {"bench":[...]}
 * {"op":"removeObject","handle":1}                           {"removed":true}
 * {"op":"clearBench"}                                        {"cleared":true}
 * {"op":"output"}                                            {"output":"hello\n"}
 * {"op":"clearOutput"}                                       {"cleared":true}
 * {"op":"reset"}                                             {"reset":true}
 * </pre>
 *
 * <p>Values encode as {@code {"int":n}}, {@code {"double":d}}, {@code {"string":s}},
 * {@code {"bool":b}}, {@code {"null":true}}, {@code {"ref":handle}} or
 * {@code {"array":{"component":"int","items":[...]}}}. Failures come back as
 * {@code {"error":{"class":...,"message":...,"unsupported":..}}} and never as an exception
 * crossing the bridge.
 */
public final class CoreProtocol {
    private final CoreSession session;

    public CoreProtocol(CoreSession session) { this.session = session; }

    public String handle(String requestJson) {
        try {
            Object parsed = Json.parse(requestJson);
            if (!(parsed instanceof Map<?, ?> request)) {
                throw new IllegalArgumentException("request must be a JSON object");
            }
            return Json.write(dispatch(request));
        } catch (InterpretedThrowable thrown) {
            return Json.write(error(thrown.className(), thrown.getMessage(), false));
        } catch (UnsupportedOperationException thrown) {
            return Json.write(error("java.lang.UnsupportedOperationException", thrown.getMessage(), true));
        } catch (RuntimeException thrown) {
            return Json.write(error(thrown.getClass().getName(), thrown.getMessage(), false));
        }
    }

    private Map<String, Object> dispatch(Map<?, ?> request) {
        String op = string(request.get("op"));
        return switch (op) {
            case "classes" -> Map.of("classes", classes());
            case "sources" -> Map.of("sources", sources());
            case "source" -> source(request);
            case "setSource" -> Map.of("saved", true,
                    "revision", session.setSource(string(request.get("path")), string(request.get("text"))));
            case "deleteSource" -> Map.of("deleted", true,
                    "revision", session.deleteSource(string(request.get("path"))));
            case "newClass" -> newClass(request);
            case "compile" -> compile();
            case "new" -> newObject(request);
            case "invoke" -> Map.of("value", encode(session.invoke(integer(request.get("handle")),
                    string(request.get("method")), arguments(request))));
            case "invokeStatic" -> Map.of("value", encode(session.invokeStatic(string(request.get("class")),
                    string(request.get("method")), arguments(request))));
            case "inspect" -> encodeBench(session.inspect(integer(request.get("handle"))));
            case "bench" -> Map.of("bench", session.bench().stream().map(CoreProtocol::encodeBench).toList());
            case "removeObject" -> { session.removeObject(integer(request.get("handle"))); yield Map.of("removed", true); }
            case "clearBench" -> { session.clearBench(); yield Map.of("cleared", true); }
            case "output" -> Map.of("output", session.output());
            case "clearOutput" -> { session.clearOutput(); yield Map.of("cleared", true); }
            case "reset" -> { session.reset(); yield Map.of("reset", true); }
            default -> throw new IllegalArgumentException("unknown op: " + op);
        };
    }

    private Map<String, Object> compile() {
        CoreSession.CompileReport report = session.compile();
        List<Object> diagnostics = new ArrayList<>();
        for (ProjectCompiler.Diagnostic diagnostic : report.diagnostics()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", diagnostic.path());
            entry.put("line", diagnostic.line());
            entry.put("column", diagnostic.column());
            entry.put("message", diagnostic.message());
            entry.put("severity", diagnostic.severity().name().toLowerCase(java.util.Locale.ROOT));
            diagnostics.add(entry);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", report.success());
        response.put("revision", report.revision());
        response.put("classes", classes());
        response.put("diagnostics", diagnostics);
        response.put("output", Map.of("text", session.output()));
        return response;
    }

    private Map<String, Object> source(Map<?, ?> request) {
        String path = string(request.get("path"));
        String text = session.source(path);
        if (text == null) return Map.of("error", Map.of("class", "java.lang.IllegalArgumentException",
                "message", "no such source: " + path, "unsupported", false));
        return Map.of("path", path, "text", text);
    }

    private Map<String, Object> newClass(Map<?, ?> request) {
        String simpleName = string(request.get("name")).trim();
        if (!simpleName.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
            throw new IllegalArgumentException("invalid class name: " + simpleName);
        }
        String kind = request.get("kind") == null ? "class" : string(request.get("kind"));
        String packageName = request.get("package") == null ? "" : string(request.get("package"));
        java.util.Set<String> existing = new java.util.HashSet<>();
        session.classes().forEach(info -> existing.add(info.name()));
        String binaryName = packageName.isBlank() ? simpleName : packageName + "." + simpleName;
        if (existing.contains(binaryName)) {
            throw new IllegalArgumentException("a class named " + simpleName + " already exists");
        }
        String path = (packageName.isBlank() ? "" : packageName.replace('.', '/') + "/") + simpleName + ".java";
        long revision = session.setSource(path, CoreSession.newClassSource(simpleName, kind, packageName));
        return Map.of("path", path, "text", session.source(path), "revision", revision);
    }

    private Map<String, Object> newObject(Map<?, ?> request) {
        BenchObject object = session.create(string(request.get("class")), arguments(request));
        return encodeBench(object);
    }

    private List<Object> sources() {
        List<Object> result = new ArrayList<>();
        session.sources().forEach((path, text) -> {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("path", path);
            entry.put("text", text);
            entry.put("type", ProjectCompiler.declaredTypeName(text));
            result.add(entry);
        });
        return result;
    }

    private List<Object> classes() {
        List<Object> result = new ArrayList<>();
        for (ClassInfo info : session.classes()) {
            List<Object> fields = new ArrayList<>();
            for (ClassInfo.Field field : info.fields()) {
                fields.add(Map.of("name", field.name(), "type", field.type(), "static", field.isStatic()));
            }
            List<Object> methods = new ArrayList<>();
            for (ClassInfo.Method method : info.methods()) {
                methods.add(Map.of("name", method.name(), "parameters", method.parameterTypes(),
                        "returns", method.returnType(), "static", method.isStatic()));
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", info.name());
            entry.put("kind", info.kind());
            entry.put("superclass", info.superclass());
            entry.put("path", info.path());
            entry.put("compiled", info.compiled());
            entry.put("error", info.error());
            entry.put("fields", fields);
            entry.put("methods", methods);
            result.add(entry);
        }
        return result;
    }

    private static Map<String, Object> encodeBench(BenchObject object) {
        Map<String, Object> fields = new LinkedHashMap<>();
        object.fields().forEach((name, value) -> fields.put(name, encode(value)));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("handle", object.handle());
        entry.put("name", object.name());
        entry.put("class", object.className());
        entry.put("fields", fields);
        return entry;
    }

    // ---------------------------------------------------------------- values

    static Object encode(Value value) {
        return switch (value) {
            case Value.IntValue i -> Map.of("int", i.value());
            case Value.DoubleValue d -> Map.of("double", d.value());
            case Value.StringValue s -> Map.of("string", s.value());
            case Value.NullValue n -> Map.of("null", true);
            case Value.ObjectValue o -> Map.of("object", o.value().className(), "fields", encodeFields(o.value()));
            case Value.ArrayValue a -> Map.of("array", Map.of(
                    "component", a.value().componentType(),
                    "items", encodeItems(a.value())));
        };
    }

    private static Map<String, Object> encodeFields(InterpretedObject object) {
        Map<String, Object> fields = new LinkedHashMap<>();
        object.fields().forEach((name, value) -> fields.put(name, encode(value)));
        return fields;
    }

    private static List<Object> encodeItems(InterpretedArray array) {
        List<Object> items = new ArrayList<>();
        for (Value value : array.snapshot()) items.add(encode(value));
        return items;
    }

    private List<Value> arguments(Map<?, ?> request) {
        Object raw = request.get("args");
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> list)) throw new IllegalArgumentException("args must be an array");
        List<Value> values = new ArrayList<>();
        for (Object element : list) values.add(decode(element));
        return values;
    }

    private Value decode(Object element) {
        if (element == null) return Value.NullValue.INSTANCE;
        if (!(element instanceof Map<?, ?> map)) throw new IllegalArgumentException("argument must be an object");
        if (map.containsKey("null")) return Value.NullValue.INSTANCE;
        if (map.containsKey("int")) return new Value.IntValue(integer(map.get("int")));
        if (map.containsKey("double")) return new Value.DoubleValue(((Number) map.get("double")).doubleValue());
        if (map.containsKey("string")) return new Value.StringValue(string(map.get("string")));
        if (map.containsKey("bool")) return new Value.IntValue(Boolean.TRUE.equals(map.get("bool")) ? 1 : 0);
        if (map.containsKey("ref")) return session.reference(integer(map.get("ref")));
        if (map.get("array") instanceof Map<?, ?> array) {
            String component = string(array.get("component"));
            List<?> items = array.get("items") instanceof List<?> list ? list : List.of();
            int primitive = switch (component) {
                case "int", "boolean", "byte" -> 11;
                case "double" -> 5;
                case "char" -> 9;
                case "short" -> 10;
                default -> 0;
            };
            InterpretedArray created = new InterpretedArray(primitive, component, items.size());
            for (int i = 0; i < items.size(); i++) created.set(i, decode(items.get(i)));
            return new Value.ArrayValue(created);
        }
        throw new IllegalArgumentException("unsupported argument encoding: " + map.keySet());
    }

    // ---------------------------------------------------------------- scalar helpers

    private static Map<String, Object> error(String className, String message, boolean unsupported) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("class", className);
        error.put("message", message == null ? "" : message);
        error.put("unsupported", unsupported);
        return Map.of("error", error);
    }

    private static String string(Object value) {
        if (value instanceof String s) return s;
        throw new IllegalArgumentException("expected a string but got " + value);
    }

    private static int integer(Object value) {
        if (value instanceof Number n) return n.intValue();
        throw new IllegalArgumentException("expected a number but got " + value);
    }
}
