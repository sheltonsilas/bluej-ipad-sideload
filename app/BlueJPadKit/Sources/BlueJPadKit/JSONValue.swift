import Foundation

/// One member of a JSON object. Objects keep insertion order because the class diagram and
/// the inspector should show fields in declaration order, not alphabetically.
public struct JSONMember: Equatable, Sendable {
    public let key: String
    public let value: JSONValue

    public init(_ key: String, _ value: JSONValue) {
        self.key = key
        self.value = value
    }
}

public enum JSONError: Error, Equatable {
    case unexpectedEnd
    case unexpectedCharacter(Character, offset: Int)
    case invalidNumber(String)
    case invalidEscape(Character)
    case trailingData(offset: Int)
}

public enum JSONValue: Equatable, Sendable {
    case null
    case bool(Bool)
    case int(Int)
    case number(Double)
    case string(String)
    case array([JSONValue])
    case object([JSONMember])

    // MARK: - Construction

    public static func obj(_ members: JSONMember...) -> JSONValue {
        .object(members)
    }

    public static func obj(_ members: [JSONMember]) -> JSONValue {
        .object(members)
    }

    public static func obj(_ members: [String: JSONValue]) -> JSONValue {
        .object(members.map { JSONMember($0.key, $0.value) })
    }

    public static func arr(_ values: JSONValue...) -> JSONValue {
        .array(values)
    }

    // MARK: - Access

    public subscript(key: String) -> JSONValue? {
        guard case .object(let members) = self else { return nil }
        return members.first { $0.key == key }?.value
    }

    public var members: [JSONMember]? {
        guard case .object(let members) = self else { return nil }
        return members
    }

    public var arrayValue: [JSONValue]? {
        guard case .array(let values) = self else { return nil }
        return values
    }

    public var stringValue: String? {
        guard case .string(let value) = self else { return nil }
        return value
    }

    public var boolValue: Bool? {
        if case .bool(let value) = self { return value }
        return nil
    }

    public var intValue: Int? {
        switch self {
        case .int(let value): return value
        case .number(let value) where value.rounded() == value: return Int(value)
        default: return nil
        }
    }

    public var doubleValue: Double? {
        switch self {
        case .int(let value): return Double(value)
        case .number(let value): return value
        default: return nil
        }
    }

    public var isNull: Bool {
        if case .null = self { return true }
        return false
    }

    // MARK: - Serialisation

    public func encoded() -> String {
        var out = ""
        encode(into: &out)
        return out
    }

    public func data() throws -> Data {
        Data(encoded().utf8)
    }

    private func encode(into out: inout String) {
        switch self {
        case .null:
            out += "null"
        case .bool(let value):
            out += value ? "true" : "false"
        case .int(let value):
            out += String(value)
        case .number(let value):
            out += value.isFinite ? String(value) : "null"
        case .string(let value):
            JSONValue.encode(string: value, into: &out)
        case .array(let values):
            out += "["
            for (index, value) in values.enumerated() {
                if index > 0 { out += "," }
                value.encode(into: &out)
            }
            out += "]"
        case .object(let members):
            out += "{"
            for (index, member) in members.enumerated() {
                if index > 0 { out += "," }
                JSONValue.encode(string: member.key, into: &out)
                out += ":"
                member.value.encode(into: &out)
            }
            out += "}"
        }
    }

    private static func encode(string: String, into out: inout String) {
        out += "\""
        for scalar in string.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\t": out += "\\t"
            default:
                if scalar.value < 0x20 {
                    out += String(format: "\\u%04x", scalar.value)
                } else {
                    out.unicodeScalars.append(scalar)
                }
            }
        }
        out += "\""
    }

    // MARK: - Parsing

    public static func parse(_ text: String) throws -> JSONValue {
        var parser = Parser(bytes: Array(text.utf8))
        let value = try parser.value()
        parser.skipWhitespace()
        if !parser.done {
            throw JSONError.trailingData(offset: parser.offset)
        }
        return value
    }

    public static func decode(_ data: Data) throws -> JSONValue {
        try parse(String(decoding: data, as: UTF8.self))
    }

    private struct Parser {
        let bytes: [UInt8]
        var offset = 0

        var done: Bool { offset >= bytes.count }

        mutating func skipWhitespace() {
            while offset < bytes.count {
                switch bytes[offset] {
                case 0x20, 0x09, 0x0A, 0x0D: offset += 1
                default: return
                }
            }
        }

        mutating func value() throws -> JSONValue {
            skipWhitespace()
            guard offset < bytes.count else { throw JSONError.unexpectedEnd }
            switch bytes[offset] {
            case UInt8(ascii: "{"): return try object()
            case UInt8(ascii: "["): return try array()
            case UInt8(ascii: "\""): return .string(try string())
            case UInt8(ascii: "t"):
                try literal("true")
                return .bool(true)
            case UInt8(ascii: "f"):
                try literal("false")
                return .bool(false)
            case UInt8(ascii: "n"):
                try literal("null")
                return .null
            default: return try number()
            }
        }

        mutating func literal(_ text: String) throws {
            let expected = Array(text.utf8)
            guard offset + expected.count <= bytes.count,
                  Array(bytes[offset..<(offset + expected.count)]) == expected else {
                throw JSONError.invalidNumber(String(decoding: bytes[min(offset, bytes.count)...], as: UTF8.self))
            }
            offset += expected.count
        }

        mutating func object() throws -> JSONValue {
            offset += 1
            var members: [JSONMember] = []
            skipWhitespace()
            if offset < bytes.count, bytes[offset] == UInt8(ascii: "}") {
                offset += 1
                return .object(members)
            }
            while true {
                skipWhitespace()
                let key = try string()
                skipWhitespace()
                guard offset < bytes.count, bytes[offset] == UInt8(ascii: ":") else {
                    throw JSONError.unexpectedCharacter(":", offset: offset)
                }
                offset += 1
                members.append(JSONMember(key, try value()))
                skipWhitespace()
                guard offset < bytes.count else { throw JSONError.unexpectedEnd }
                if bytes[offset] == UInt8(ascii: "}") {
                    offset += 1
                    return .object(members)
                }
                guard bytes[offset] == UInt8(ascii: ",") else {
                    throw JSONError.unexpectedCharacter(",", offset: offset)
                }
                offset += 1
            }
        }

        mutating func array() throws -> JSONValue {
            offset += 1
            var values: [JSONValue] = []
            skipWhitespace()
            if offset < bytes.count, bytes[offset] == UInt8(ascii: "]") {
                offset += 1
                return .array(values)
            }
            while true {
                values.append(try value())
                skipWhitespace()
                guard offset < bytes.count else { throw JSONError.unexpectedEnd }
                if bytes[offset] == UInt8(ascii: "]") {
                    offset += 1
                    return .array(values)
                }
                guard bytes[offset] == UInt8(ascii: ",") else {
                    throw JSONError.unexpectedCharacter(",", offset: offset)
                }
                offset += 1
            }
        }

        mutating func string() throws -> String {
            guard offset < bytes.count, bytes[offset] == UInt8(ascii: "\"") else {
                throw JSONError.unexpectedEnd
            }
            offset += 1
            var buffer: [UInt8] = []
            while offset < bytes.count {
                let byte = bytes[offset]
                offset += 1
                if byte == UInt8(ascii: "\"") {
                    return String(decoding: buffer, as: UTF8.self)
                }
                if byte != UInt8(ascii: "\\") {
                    buffer.append(byte)
                    continue
                }
                guard offset < bytes.count else { throw JSONError.unexpectedEnd }
                let escape = bytes[offset]
                offset += 1
                switch escape {
                case UInt8(ascii: "\""): buffer.append(UInt8(ascii: "\""))
                case UInt8(ascii: "\\"): buffer.append(UInt8(ascii: "\\"))
                case UInt8(ascii: "/"): buffer.append(UInt8(ascii: "/"))
                case UInt8(ascii: "b"): buffer.append(0x08)
                case UInt8(ascii: "f"): buffer.append(0x0C)
                case UInt8(ascii: "n"): buffer.append(0x0A)
                case UInt8(ascii: "r"): buffer.append(0x0D)
                case UInt8(ascii: "t"): buffer.append(0x09)
                case UInt8(ascii: "u"):
                    guard offset + 4 <= bytes.count,
                          let hex = String(bytes: bytes[offset..<(offset + 4)], encoding: .utf8),
                          let code = UInt32(hex, radix: 16) else {
                        throw JSONError.invalidEscape("u")
                    }
                    offset += 4
                    if let scalar = Unicode.Scalar(code) {
                        buffer.append(contentsOf: Array(String(Character(scalar)).utf8))
                    }
                default:
                    throw JSONError.invalidEscape(Character(String(Unicode.Scalar(escape))))
                }
            }
            throw JSONError.unexpectedEnd
        }

        mutating func number() throws -> JSONValue {
            let start = offset
            var isFractional = false
            while offset < bytes.count {
                let byte = bytes[offset]
                if byte == UInt8(ascii: "-") || byte == UInt8(ascii: "+")
                    || (byte >= UInt8(ascii: "0") && byte <= UInt8(ascii: "9")) {
                    offset += 1
                } else if byte == UInt8(ascii: ".") || byte == UInt8(ascii: "e") || byte == UInt8(ascii: "E") {
                    isFractional = true
                    offset += 1
                } else {
                    break
                }
            }
            let token = String(decoding: bytes[start..<offset], as: UTF8.self)
            guard !token.isEmpty else {
                let character = offset < bytes.count
                    ? Character(String(Unicode.Scalar(bytes[offset])))
                    : " "
                throw JSONError.unexpectedCharacter(character, offset: offset)
            }
            if !isFractional, let value = Int(token) {
                return .int(value)
            }
            guard let value = Double(token) else { throw JSONError.invalidNumber(token) }
            return .number(value)
        }
    }
}

