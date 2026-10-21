import Foundation

/// Typed wrapper around the JSON protocol. Every UI action goes through one of these calls,
/// which keeps the wire format in exactly one place.
public struct CoreAPI: Sendable {
    public let client: any CoreClient

    public init(client: any CoreClient) {
        self.client = client
    }

    private func call(_ request: JSONValue) async throws -> JSONValue {
        let response = try await client.send(request)
        // Transports may hand back the error payload untouched; turn it into a typed failure
        // here so every caller sees the same thing.
        if let failure = response["error"] {
            throw CoreError.core(CoreFailure(json: failure))
        }
        return response
    }

    // MARK: - Project

    public func sources() async throws -> [SourceFile] {
        let response = try await call(.obj(JSONMember("op", .string("sources"))))
        return (response["sources"]?.arrayValue ?? []).map(SourceFile.init(json:))
    }

    public func setSource(path: String, text: String) async throws {
        _ = try await call(.obj(
            JSONMember("op", .string("setSource")),
            JSONMember("path", .string(path)),
            JSONMember("text", .string(text))
        ))
    }

    public func deleteSource(path: String) async throws {
        _ = try await call(.obj(
            JSONMember("op", .string("deleteSource")),
            JSONMember("path", .string(path))
        ))
    }

    public func newClass(name: String, kind: String = "class", package: String = "") async throws -> SourceFile {
        let response = try await call(.obj(
            JSONMember("op", .string("newClass")),
            JSONMember("name", .string(name)),
            JSONMember("kind", .string(kind)),
            JSONMember("package", .string(package))
        ))
        return SourceFile(
            path: response["path"]?.stringValue ?? "\(name).java",
            text: response["text"]?.stringValue ?? "",
            declaredType: name
        )
    }

    public func compile() async throws -> CompileOutcome {
        CompileOutcome(json: try await call(.obj(JSONMember("op", .string("compile")))))
    }

    public func classes() async throws -> [ClassCard] {
        let response = try await call(.obj(JSONMember("op", .string("classes"))))
        return (response["classes"]?.arrayValue ?? []).map(ClassCard.init(json:))
    }

    // MARK: - Bench

    public func instantiate(className: String, arguments: [CoreValue] = []) async throws -> BenchObject {
        let response = try await call(.obj(
            JSONMember("op", .string("new")),
            JSONMember("class", .string(className)),
            JSONMember("args", .array(arguments.map(\.jsonArgument)))
        ))
        return BenchObject(json: response)
    }

    public func invoke(handle: Int, method: String, arguments: [CoreValue] = []) async throws -> CoreValue {
        let response = try await call(.obj(
            JSONMember("op", .string("invoke")),
            JSONMember("handle", .int(handle)),
            JSONMember("method", .string(method)),
            JSONMember("args", .array(arguments.map(\.jsonArgument)))
        ))
        return try Self.value(from: response)
    }

    public func invokeStatic(className: String, method: String, arguments: [CoreValue] = []) async throws -> CoreValue {
        let response = try await call(.obj(
            JSONMember("op", .string("invokeStatic")),
            JSONMember("class", .string(className)),
            JSONMember("method", .string(method)),
            JSONMember("args", .array(arguments.map(\.jsonArgument)))
        ))
        return try Self.value(from: response)
    }

    public func bench() async throws -> [BenchObject] {
        let response = try await call(.obj(JSONMember("op", .string("bench"))))
        return (response["bench"]?.arrayValue ?? []).map(BenchObject.init(json:))
    }

    public func inspect(handle: Int) async throws -> BenchObject {
        let response = try await call(.obj(
            JSONMember("op", .string("inspect")),
            JSONMember("handle", .int(handle))
        ))
        return BenchObject(json: response)
    }

    public func removeObject(handle: Int) async throws {
        _ = try await call(.obj(
            JSONMember("op", .string("removeObject")),
            JSONMember("handle", .int(handle))
        ))
    }

    public func clearBench() async throws {
        _ = try await call(.obj(JSONMember("op", .string("clearBench"))))
    }

    // MARK: - Debugger

    /// Enables the given {@code binary.Class#line} breakpoints and returns the active set.
    @discardableResult
    public func setBreakpoints(_ points: [String]) async throws -> [String] {
        let response = try await call(.obj(
            JSONMember("op", .string("debugBreakpoints")),
            JSONMember("points", .array(points.map(JSONValue.string)))
        ))
        return (response["points"]?.arrayValue ?? []).compactMap(\.stringValue)
    }

    public func debugStart(className: String, method: String = "main",
                           arguments: [CoreValue] = []) async throws -> DebugSnapshot {
        let response = try await call(.obj(
            JSONMember("op", .string("debugStart")),
            JSONMember("class", .string(className)),
            JSONMember("method", .string(method)),
            JSONMember("args", .array(arguments.map(\.jsonArgument)))
        ))
        return DebugSnapshot(json: response)
    }

    public func debugCommand(_ action: DebugAction) async throws -> DebugSnapshot {
        let response = try await call(.obj(
            JSONMember("op", .string("debugCommand")),
            JSONMember("action", .string(action.rawValue))
        ))
        return DebugSnapshot(json: response)
    }

    public func debugState() async throws -> DebugSnapshot {
        DebugSnapshot(json: try await call(.obj(JSONMember("op", .string("debugState")))))
    }

    // MARK: - Console and lifecycle

    public func output() async throws -> String {
        let response = try await call(.obj(JSONMember("op", .string("output"))))
        return response["output"]?.stringValue ?? ""
    }

    public func clearOutput() async throws {
        _ = try await call(.obj(JSONMember("op", .string("clearOutput"))))
    }

    public func reset() async throws {
        _ = try await call(.obj(JSONMember("op", .string("reset"))))
    }

    private static func value(from response: JSONValue) throws -> CoreValue {
        guard let encoded = response["value"], let value = CoreValue(json: encoded) else {
            throw CoreError.protocolViolation("missing value")
        }
        return value
    }
}
