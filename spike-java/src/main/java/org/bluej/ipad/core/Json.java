package org.bluej.ipad.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for the core protocol.
 *
 * <p>The runtime must not pull a JSON library into the GraalVM iOS image for a handful of
 * small messages, so this covers exactly the RFC 8259 subset the protocol uses: objects,
 * arrays, strings with escapes, numbers, booleans and null. Integral numbers parse as
 * {@link Long}, fractional numbers as {@link Double}.
 */
final class Json {
    private Json() {}

    static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.done()) throw new IllegalArgumentException("trailing JSON at offset " + parser.offset);
        return value;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        switch (value) {
            case null -> out.append("null");
            case String s -> writeString(out, s);
            case Boolean b -> out.append(b);
            case Integer i -> out.append(i.intValue());
            case Long l -> out.append(l.longValue());
            case Double d -> out.append(Double.isFinite(d) ? d.toString() : "null");
            case Number n -> out.append(n);
            case Map<?, ?> map -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    writeString(out, String.valueOf(entry.getKey()));
                    out.append(':');
                    writeValue(out, entry.getValue());
                }
                out.append('}');
            }
            case Iterable<?> list -> {
                out.append('[');
                boolean first = true;
                for (Object element : list) {
                    if (!first) out.append(',');
                    first = false;
                    writeValue(out, element);
                }
                out.append(']');
            }
            default -> throw new IllegalArgumentException("cannot serialise " + value.getClass().getName());
        }
    }

    private static void writeString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int offset;

        Parser(String text) { this.text = text; }

        boolean done() { return offset >= text.length(); }

        void skipWhitespace() {
            while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) offset++;
        }

        Object readValue() {
            skipWhitespace();
            if (done()) throw new IllegalArgumentException("unexpected end of JSON");
            char c = text.charAt(offset);
            return switch (c) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't', 'f' -> readBoolean();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') { offset++; return map; }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                map.put(key, readValue());
                skipWhitespace();
                char c = next();
                if (c == '}') return map;
                if (c != ',') throw new IllegalArgumentException("expected ',' or '}' at offset " + (offset - 1));
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') { offset++; return list; }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') return list;
                if (c != ',') throw new IllegalArgumentException("expected ',' or ']' at offset " + (offset - 1));
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c != '\\') { out.append(c); continue; }
                char escape = next();
                switch (escape) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        String hex = text.substring(offset, offset + 4);
                        out.append((char) Integer.parseInt(hex, 16));
                        offset += 4;
                    }
                    default -> throw new IllegalArgumentException("invalid escape \\" + escape);
                }
            }
        }

        private Boolean readBoolean() {
            if (text.startsWith("true", offset)) { offset += 4; return Boolean.TRUE; }
            if (text.startsWith("false", offset)) { offset += 5; return Boolean.FALSE; }
            throw new IllegalArgumentException("invalid literal at offset " + offset);
        }

        private Object readNull() {
            if (!text.startsWith("null", offset)) throw new IllegalArgumentException("invalid literal at offset " + offset);
            offset += 4;
            return null;
        }

        private Number readNumber() {
            int start = offset;
            if (peek() == '-') offset++;
            while (!done() && "0123456789.eE+-".indexOf(text.charAt(offset)) >= 0) offset++;
            String token = text.substring(start, offset);
            if (token.isEmpty()) throw new IllegalArgumentException("invalid number at offset " + start);
            if (token.indexOf('.') < 0 && token.indexOf('e') < 0 && token.indexOf('E') < 0) {
                return Long.parseLong(token);
            }
            return Double.parseDouble(token);
        }

        private char peek() {
            if (done()) throw new IllegalArgumentException("unexpected end of JSON");
            return text.charAt(offset);
        }

        private char next() {
            char c = peek();
            offset++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) throw new IllegalArgumentException("expected '" + expected + "' at offset " + (offset - 1));
        }
    }
}
