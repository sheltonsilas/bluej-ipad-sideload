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
 * request                                  response
 * {"op":"classes"}                         {"classes":[...]}
 * {"op":"new","class":"Counter","args":[{"int":4}]}
 *                                          {"handle":1,"class":"Counter","fields":{...}}
 * {"op":"invoke","handle":1,"method":"increment","args":[{"int":5}]}
 *                                          {"value":{"int":9}}
 * {"op":"invokeStatic","class":"Counter","method":"sumTo","args":[{"int":6}]}
 *                                          {"value":{"int":30}}
 * {"op":"inspect","handle":1}              {"handle":1,"class":"Counter","fields":{...}}
 * {"op":"bench"}                           {"bench":[...]}
 * {"op":"output"}                          {"output":"hello\n"}
 * {"op":"clearOutput"}                     {"cleared":true}
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
            case "new" -> newObject(request);
            case "invoke" -> Map.of("value", encode(session.invoke(integer(request.get("handle")),
                    string(request.get("method")), arguments(request))));
            case "invokeStatic" -> Map.of("value", encode(session.invokeStatic(string(request.get("class")),
                    string(request.get("method")), arguments(request))));
            case "inspect" -> encodeBench(session.inspect(integer(request.get("handle"))));
            case "bench" -> Map.of("bench", session.bench().stream().map(CoreProtocol::encodeBench).toList());
            case "output" -> Map.of("output", session.output());
            case "clearOutput" -> { session.clearOutput(); yield Map.of("cleared", true); }
            default -> throw new IllegalArgumentException("unknown op: " + op);
        };
    }

    private Map<String, Object> newObject(Map<?, ?> request) {
        BenchObject object = session.create(string(request.get("class")), arguments(request));
        return encodeBench(object);
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
            result.add(Map.of("name", info.name(), "fields", fields, "methods", methods));
        }
        return result;
    }

    private static Map<String, Object> encodeBench(BenchObject object) {
        Map<String, Object> fields = new LinkedHashMap<>();
        object.fields().forEach((name, value) -> fields.put(name, encode(value)));
        return Map.of("handle", object.handle(), "class", object.className(), "fields", fields);
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
            InterpretedArray created = new InterpretedArray(primitive, primitive == 0 ? component : component, items.size());
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
