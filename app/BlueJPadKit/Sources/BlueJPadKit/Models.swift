import Foundation

/// One Java source file in the open project.
public struct SourceFile: Identifiable, Hashable, Sendable {
    public var path: String
    public var text: String
    public var declaredType: String?

    public var id: String { path }

    public var fileName: String {
        path.split(separator: "/").last.map(String.init) ?? path
    }

    public var packageName: String {
        let parts = path.split(separator: "/").dropLast()
        return parts.joined(separator: ".")
    }

    public init(path: String, text: String, declaredType: String? = nil) {
        self.path = path
        self.text = text
        self.declaredType = declaredType
    }

    public init(json: JSONValue) {
        self.path = json["path"]?.stringValue ?? ""
        self.text = json["text"]?.stringValue ?? ""
        self.declaredType = json["type"]?.stringValue
    }
}

public struct ClassField: Identifiable, Hashable, Sendable {
    public let name: String
    public let type: String
    public let isStatic: Bool

    public var id: String { name }

    public init(name: String, type: String, isStatic: Bool) {
        self.name = name
        self.type = type
        self.isStatic = isStatic
    }

    public var simpleType: String { CoreValue.simpleName(type) }
}

public struct ClassMethod: Identifiable, Hashable, Sendable {
    public let name: String
    public let parameters: [String]
    public let returns: String
    public let isStatic: Bool

    public var id: String { name + "(" + parameters.joined(separator: ",") + ")" }

    public init(name: String, parameters: [String], returns: String, isStatic: Bool) {
        self.name = name
        self.parameters = parameters
        self.returns = returns
        self.isStatic = isStatic
    }

    public var signature: String {
        name + "(" + parameters.map(CoreValue.simpleName).joined(separator: ", ") + ")"
    }
}

/// One box in the class diagram.
public struct ClassCard: Identifiable, Hashable, Sendable {
    public let name: String
    public let kind: String
    public let superclass: String?
    public let path: String
    public let compiled: Bool
    public let error: Bool
    public let fields: [ClassField]
    public let methods: [ClassMethod]

    public var id: String { name }

    public var simpleName: String { CoreValue.simpleName(name) }

    public var isAbstract: Bool {
        kind == "interface" || kind == "annotation"
    }

    /// The superclass worth drawing an arrow to: Java's Object is not part of the project.
    public var projectSuperclass: String? {
        guard let superclass, superclass != "java.lang.Object" else { return nil }
        return superclass
    }

    public var constructorCount: Int {
        methods.filter { $0.name == simpleName }.count
    }

    public init(
        name: String,
        kind: String = "class",
        superclass: String? = nil,
        path: String = "",
        compiled: Bool = false,
        error: Bool = false,
        fields: [ClassField] = [],
        methods: [ClassMethod] = []
    ) {
        self.name = name
        self.kind = kind
        self.superclass = superclass
        self.path = path
        self.compiled = compiled
        self.error = error
        self.fields = fields
        self.methods = methods
    }

    public init(json: JSONValue) {
        self.name = json["name"]?.stringValue ?? ""
        self.kind = json["kind"]?.stringValue ?? "class"
        self.superclass = json["superclass"]?.stringValue
        self.path = json["path"]?.stringValue ?? ""
        self.compiled = json["compiled"]?.boolValue ?? false
        self.error = json["error"]?.boolValue ?? false
        self.fields = (json["fields"]?.arrayValue ?? []).map { field in
            ClassField(
                name: field["name"]?.stringValue ?? "",
                type: field["type"]?.stringValue ?? "int",
                isStatic: field["static"]?.boolValue ?? false
            )
        }
        self.methods = (json["methods"]?.arrayValue ?? []).map { method in
            ClassMethod(
                name: method["name"]?.stringValue ?? "",
                parameters: (method["parameters"]?.arrayValue ?? []).compactMap(\.stringValue),
                returns: method["returns"]?.stringValue ?? "void",
                isStatic: method["static"]?.boolValue ?? false
            )
        }
    }
}

/// One object sitting on the bench.
public struct BenchObject: Identifiable, Hashable, Sendable {
    public let handle: Int
    public let name: String
    public let className: String
    public let fields: [FieldValue]

    public var id: Int { handle }
    public var simpleClassName: String { CoreValue.simpleName(className) }

    public init(handle: Int, name: String, className: String, fields: [FieldValue]) {
        self.handle = handle
        self.name = name
        self.className = className
        self.fields = fields
    }

    public init(json: JSONValue) {
        self.handle = json["handle"]?.intValue ?? 0
        self.name = json["name"]?.stringValue ?? "object\(handle)"
        self.className = json["class"]?.stringValue ?? ""
        self.fields = (json["fields"]?.members ?? []).compactMap { member in
            guard let value = CoreValue(json: member.value) else { return nil }
            return FieldValue(name: member.key, value: value)
        }
    }
}

public struct Diagnostic: Identifiable, Hashable, Sendable {
    public let path: String
    public let line: Int
    public let column: Int
    public let message: String
    public let severity: String

    public var id: String { "\(path):\(line):\(column):\(message)" }
    public var isError: Bool { severity == "error" }
    public var fileName: String { path.split(separator: "/").last.map(String.init) ?? path }

    public init(path: String, line: Int, column: Int, message: String, severity: String) {
        self.path = path
        self.line = line
        self.column = column
        self.message = message
        self.severity = severity
    }

    public init(json: JSONValue) {
        self.path = json["path"]?.stringValue ?? ""
        self.line = json["line"]?.intValue ?? 1
        self.column = json["column"]?.intValue ?? 1
        self.message = json["message"]?.stringValue ?? ""
        self.severity = json["severity"]?.stringValue ?? "error"
    }
}

public struct CompileOutcome: Sendable {
    public let success: Bool
    public let revision: Int
    public let classes: [ClassCard]
    public let diagnostics: [Diagnostic]
    public let output: String

    public init(success: Bool, revision: Int, classes: [ClassCard], diagnostics: [Diagnostic], output: String) {
        self.success = success
        self.revision = revision
        self.classes = classes
        self.diagnostics = diagnostics
        self.output = output
    }

    public init(json: JSONValue) {
        self.success = json["success"]?.boolValue ?? false
        self.revision = json["revision"]?.intValue ?? 0
        self.classes = (json["classes"]?.arrayValue ?? []).map(ClassCard.init(json:))
        self.diagnostics = (json["diagnostics"]?.arrayValue ?? []).map(Diagnostic.init(json:))
        self.output = json["output"]?["text"]?.stringValue ?? ""
    }
}
