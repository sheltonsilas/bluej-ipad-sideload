import Foundation

/// One field of an interpreted object, in declaration order.
public struct FieldValue: Equatable, Hashable, Sendable, Identifiable {
    public let name: String
    public let value: CoreValue

    public var id: String { name }

    public init(name: String, value: CoreValue) {
        self.name = name
        self.value = value
    }
}

/// A Java value crossing the bridge. Primitives are widened the way BlueJ's inspector shows
/// them; booleans are carried as integers because that is what the protocol does.
public enum CoreValue: Equatable, Hashable, Sendable {
    case int(Int)
    case double(Double)
    case string(String)
    case boolean(Bool)
    case null
    case reference(Int)
    case object(className: String, fields: [FieldValue])
    case array(component: String, items: [CoreValue])

    public init?(json: JSONValue) {
        if json.isNull {
            self = .null
            return
        }
        if let value = json["int"]?.intValue {
            self = .int(value)
            return
        }
        if let value = json["double"]?.doubleValue {
            self = .double(value)
            return
        }
        if let value = json["string"]?.stringValue {
            self = .string(value)
            return
        }
        if let value = json["bool"]?.boolValue {
            self = .boolean(value)
            return
        }
        if let encoded = json["array"]?.members {
            let component = encoded.first { $0.key == "component" }?.value.stringValue ?? "int"
            let items = (encoded.first { $0.key == "items" }?.value.arrayValue ?? [])
                .compactMap(CoreValue.init(json:))
            self = .array(component: component, items: items)
            return
        }
        if let className = json["object"]?.stringValue {
            let fields = (json["fields"]?.members ?? []).compactMap { member -> FieldValue? in
                guard let value = CoreValue(json: member.value) else { return nil }
                return FieldValue(name: member.key, value: value)
            }
            self = .object(className: className, fields: fields)
            return
        }
        return nil
    }

    public var jsonArgument: JSONValue {
        switch self {
        case .int(let value):
            return .obj(JSONMember("int", .int(value)))
        case .double(let value):
            return .obj(JSONMember("double", .number(value)))
        case .string(let value):
            return .obj(JSONMember("string", .string(value)))
        case .boolean(let value):
            return .obj(JSONMember("bool", .bool(value)))
        case .null:
            return .obj(JSONMember("null", .bool(true)))
        case .reference(let handle):
            return .obj(JSONMember("ref", .int(handle)))
        case .object(_, let fields):
            return .obj(JSONMember("object", .obj([
                JSONMember("fields", .object(fields.map { JSONMember($0.name, $0.value.jsonArgument) }))
            ])))
        case .array(let component, let items):
            return .obj(JSONMember("array", .obj([
                JSONMember("component", .string(component)),
                JSONMember("items", .array(items.map(\.jsonArgument)))
            ])))
        }
    }

    /// How the inspector shows the value.
    public var displayText: String {
        switch self {
        case .int(let value): return String(value)
        case .double(let value): return CoreValue.format(double: value)
        case .string(let value): return "\"" + value + "\""
        case .boolean(let value): return value ? "true" : "false"
        case .null: return "null"
        case .reference(let handle): return "bench #\(handle)"
        case .object(let className, let fields):
            return "\(simpleName(className)) (\(fields.count) field\(fields.count == 1 ? "" : "s"))"
        case .array(let component, let items):
            return "\(component)[\(items.count)]"
        }
    }

    /// How the terminal shows a returned value.
    public var resultText: String {
        switch self {
        case .string(let value): return value
        case .null: return "null"
        case .object(let className, _): return "an instance of \(simpleName(className))"
        case .array(let component, let items):
            return "[" + items.map(\.displayText).joined(separator: ", ") + "] (\(component) array)"
        default: return displayText
        }
    }

    /// Text to put back into a parameter field.
    public var literalText: String {
        switch self {
        case .int(let value): return String(value)
        case .double(let value): return CoreValue.format(double: value)
        case .string(let value): return value
        case .boolean(let value): return value ? "true" : "false"
        case .null: return "null"
        default: return displayText
        }
    }

    public var isNumeric: Bool {
        switch self {
        case .int, .double: return true
        default: return false
        }
    }

    public static func format(double value: Double) -> String {
        if value.isNaN { return "NaN" }
        if value.isInfinite { return value > 0 ? "Infinity" : "-Infinity" }
        if value.rounded() == value && abs(value) < 1e15 {
            return String(format: "%.1f", value)
        }
        return String(value)
    }

    public static func simpleName(_ javaType: String) -> String {
        guard let last = javaType.split(separator: ".").last else { return javaType }
        return String(last)
    }
}

public enum ValueParseError: Error, Equatable, LocalizedError {
    case invalidInt(String)
    case invalidDouble(String)
    case invalidBoolean(String)
    case emptyCharacter
    case unsupportedType(String)

    public var errorDescription: String? {
        switch self {
        case .invalidInt(let text): return "\"\(text)\" is not a whole number."
        case .invalidDouble(let text): return "\"\(text)\" is not a number."
        case .invalidBoolean(let text): return "\"\(text)\" is not true or false."
        case .emptyCharacter: return "A char needs exactly one character."
        case .unsupportedType(let type): return "Parameters of type \(type) are not supported on iPad yet."
        }
    }
}

extension CoreValue {
    /// Turns what the student typed into a parameter of the given declared Java type.
    public static func argument(text: String, javaType: String) throws -> CoreValue {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        switch javaType {
        case "int", "short", "byte", "long":
            guard let value = Int(trimmed) else { throw ValueParseError.invalidInt(trimmed) }
            return .int(value)
        case "double", "float":
            guard let value = Double(trimmed) else { throw ValueParseError.invalidDouble(trimmed) }
            return .double(value)
        case "boolean":
            if trimmed == "true" { return .boolean(true) }
            if trimmed == "false" { return .boolean(false) }
            throw ValueParseError.invalidBoolean(trimmed)
        case "char":
            guard let character = trimmed.first else { throw ValueParseError.emptyCharacter }
            return .int(Int(character.unicodeScalars.first?.value ?? 0))
        case "java.lang.String":
            if trimmed == "null" && text == "null" { return .null }
            return .string(text)
        default:
            if trimmed == "null" { return .null }
            throw ValueParseError.unsupportedType(CoreValue.simpleName(javaType))
        }
    }
}
