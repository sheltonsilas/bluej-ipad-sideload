import Foundation

/// One variable in a paused frame: a local, a parameter or an instance field.
public struct DebugVariable: Identifiable, Hashable, Sendable {
    public let name: String
    public let type: String
    public let value: CoreValue

    public var id: String { name }
    public var displayType: String { CoreValue.simpleName(type) }

    public init(name: String, type: String, value: CoreValue) {
        self.name = name
        self.type = type
        self.value = value
    }

    public init(json: JSONValue) {
        self.name = json["name"]?.stringValue ?? ""
        self.type = json["type"]?.stringValue ?? ""
        self.value = json["value"].flatMap(CoreValue.init(json:)) ?? .null
    }
}

/// One frame of the paused call stack.
public struct DebugFrame: Identifiable, Hashable, Sendable {
    public let className: String
    public let method: String
    public let line: Int
    public let locals: [DebugVariable]
    public let fields: [DebugVariable]

    public var id: String { "\(className).\(method):\(line)" }
    public var simpleClassName: String { CoreValue.simpleName(className) }

    /// How BlueJ labels a frame: {@code Counter.increment():12}.
    public var title: String { "\(simpleClassName).\(method)()" }
    public var location: String { "\(simpleClassName).\(method):\(line)" }

    public init(className: String, method: String, line: Int,
                locals: [DebugVariable] = [], fields: [DebugVariable] = []) {
        self.className = className
        self.method = method
        self.line = line
        self.locals = locals
        self.fields = fields
    }

    public init(json: JSONValue) {
        self.className = json["class"]?.stringValue ?? ""
        self.method = json["method"]?.stringValue ?? ""
        self.line = json["line"]?.intValue ?? 0
        self.locals = (json["locals"]?.arrayValue ?? []).map(DebugVariable.init(json:))
        self.fields = (json["fields"]?.arrayValue ?? []).map(DebugVariable.init(json:))
    }
}

public enum DebugStatus: String, Sendable {
    case idle
    case paused
    case finished
    case failed
    case terminated

    public var isRunning: Bool { self == .paused }
}

/// One observation of a debugged program, as returned by every debug call.
public struct DebugSnapshot: Sendable {
    public let status: DebugStatus
    public let frames: [DebugFrame]
    public let value: CoreValue?
    public let errorClass: String?
    public let errorMessage: String?
    public let output: String
    public let active: Bool

    public var isPaused: Bool { status == .paused }
    public var topFrame: DebugFrame? { frames.first }

    /// A one-line summary for the terminal.
    public var summary: String {
        switch status {
        case .paused:
            guard let frame = topFrame else { return "Paused." }
            return "Paused at \(frame.location)."
        case .finished:
            if let value, value != .null { return "Debug run finished: \(value.resultText)." }
            return "Debug run finished."
        case .terminated:
            return "Debug run stopped."
        case .failed:
            let name = errorClass.map(CoreValue.simpleName) ?? "Exception"
            if let errorMessage, !errorMessage.isEmpty {
                return "Exception while debugging: \(name): \(errorMessage)"
            }
            return "Exception while debugging: \(name)."
        case .idle:
            return "Not debugging."
        }
    }

    public init(status: DebugStatus, frames: [DebugFrame] = [], value: CoreValue? = nil,
                errorClass: String? = nil, errorMessage: String? = nil,
                output: String = "", active: Bool = false) {
        self.status = status
        self.frames = frames
        self.value = value
        self.errorClass = errorClass
        self.errorMessage = errorMessage
        self.output = output
        self.active = active
    }

    public init(json: JSONValue) {
        self.status = DebugStatus(rawValue: json["status"]?.stringValue ?? "idle") ?? .idle
        self.frames = (json["frames"]?.arrayValue ?? []).map(DebugFrame.init(json:))
        self.value = json["value"].flatMap(CoreValue.init(json:))
        self.errorClass = json["error"]?["class"]?.stringValue
        self.errorMessage = json["error"]?["message"]?.stringValue
        self.output = json["output"]?.stringValue ?? ""
        self.active = json["active"]?.boolValue ?? false
    }
}

/// The four step commands, spelled the way the protocol spells them.
public enum DebugAction: String, Sendable, CaseIterable, Identifiable {
    public var id: String { rawValue }

    case resume = "continue"
    case stepInto
    case stepOver
    case stepOut
    case stop

    public var title: String {
        switch self {
        case .resume: return "Continue"
        case .stepInto: return "Step Into"
        case .stepOver: return "Step Over"
        case .stepOut: return "Step Out"
        case .stop: return "Stop"
        }
    }

    public var symbol: String {
        switch self {
        case .resume: return "play.fill"
        case .stepInto: return "arrow.down.to.line"
        case .stepOver: return "arrow.turn.down.right"
        case .stepOut: return "arrow.up.to.line"
        case .stop: return "stop.fill"
        }
    }
}
