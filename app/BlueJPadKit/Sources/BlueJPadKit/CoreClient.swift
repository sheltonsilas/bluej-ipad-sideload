import Foundation

/// A failure reported by the core itself: a compile-time problem, a student exception or a
/// rejected request. These are in-band and shown to the student, never fatal to the app.
public struct CoreFailure: Error, Equatable, LocalizedError, Sendable {
    public let className: String
    public let message: String
    public let unsupported: Bool

    public var errorDescription: String? {
        if message.isEmpty { return className }
        return "\(className): \(message)"
    }

    public init(className: String, message: String, unsupported: Bool) {
        self.className = className
        self.message = message
        self.unsupported = unsupported
    }

    public init(json: JSONValue) {
        self.className = json["class"]?.stringValue ?? "java.lang.RuntimeException"
        self.message = json["message"]?.stringValue ?? ""
        self.unsupported = json["unsupported"]?.boolValue ?? false
    }
}

public enum CoreError: Error, Equatable, LocalizedError {
    case transport(String)
    case protocolViolation(String)
    case core(CoreFailure)

    public var errorDescription: String? {
        switch self {
        case .transport(let message): return "Cannot reach the Java core: \(message)"
        case .protocolViolation(let message): return "Unexpected core response: \(message)"
        case .core(let failure): return failure.errorDescription
        }
    }

    public var isUnsupported: Bool {
        if case .core(let failure) = self { return failure.unsupported }
        return false
    }
}

/// The one thing the UI needs from the runtime: send one protocol object, get one back.
public protocol CoreClient: Sendable {
    func send(_ request: JSONValue) async throws -> JSONValue
}

/// Loopback HTTP transport. Used by the simulator, the desktop harness and UI tests; the
/// device build swaps in the in-process bridge without touching any other layer.
public struct HTTPCoreClient: CoreClient {
    public let baseURL: URL
    private let session: URLSession

    public init(baseURL: URL, session: URLSession = HTTPCoreClient.defaultSession()) {
        self.baseURL = baseURL
        self.session = session
    }

    public static func defaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 60
        configuration.timeoutIntervalForResource = 120
        configuration.waitsForConnectivity = false
        return URLSession(configuration: configuration)
    }

    public func send(_ request: JSONValue) async throws -> JSONValue {
        var urlRequest = URLRequest(url: baseURL.appendingPathComponent("rpc"))
        urlRequest.httpMethod = "POST"
        urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        urlRequest.httpBody = try request.data()
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch {
            throw CoreError.transport(error.localizedDescription)
        }
        guard let http = response as? HTTPURLResponse else {
            throw CoreError.transport("no HTTP response")
        }
        guard http.statusCode == 200 else {
            throw CoreError.transport("HTTP \(http.statusCode)")
        }
        let decoded: JSONValue
        do {
            decoded = try JSONValue.decode(data)
        } catch {
            throw CoreError.protocolViolation("response was not JSON")
        }
        if let failure = decoded["error"] {
            throw CoreError.core(CoreFailure(json: failure))
        }
        return decoded
    }

    public func health() async throws -> Bool {
        let url = baseURL.appendingPathComponent("health")
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { return false }
        let json = try JSONValue.decode(data)
        return json["status"]?.stringValue == "ok"
    }
}

/// In-memory transport for previews and for unit tests of the app model.
public final class ScriptedCoreClient: CoreClient, @unchecked Sendable {
    public typealias Handler = @Sendable (JSONValue) throws -> JSONValue

    private let lock = NSLock()
    private var handler: Handler
    public private(set) var requests: [JSONValue] = []

    public init(handler: @escaping Handler) {
        self.handler = handler
    }

    public func setHandler(_ handler: @escaping Handler) {
        lock.lock()
        defer { lock.unlock() }
        self.handler = handler
    }

    public var recordedRequests: [JSONValue] {
        lock.lock()
        defer { lock.unlock() }
        return requests
    }

    public func send(_ request: JSONValue) async throws -> JSONValue {
        lock.lock()
        requests.append(request)
        let current = handler
        lock.unlock()
        return try current(request)
    }
}
