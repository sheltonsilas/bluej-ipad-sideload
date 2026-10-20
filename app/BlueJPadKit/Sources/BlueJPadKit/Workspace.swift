import Combine
import Foundation

/// Everything the SwiftUI layer renders and every action it can take. One instance per app.
@MainActor
public final class Workspace: ObservableObject {

    public enum Connection: Equatable {
        case offline(String)
        case ready

        public var isReady: Bool {
            if case .ready = self { return true }
            return false
        }

        public var label: String {
            switch self {
            case .ready: return "Core connected"
            case .offline(let message): return message
            }
        }
    }

    public struct ConsoleEntry: Identifiable, Equatable {
        public let id: UUID
        public let text: String
        public let isError: Bool

        public init(id: UUID = UUID(), text: String, isError: Bool) {
            self.id = id
            self.text = text
            self.isError = isError
        }
    }

    @Published public private(set) var projects: [ProjectSummary] = []
    @Published public private(set) var project: String?
    @Published public private(set) var files: [SourceFile] = []
    @Published public private(set) var classes: [ClassCard] = []
    @Published public private(set) var bench: [BenchObject] = []
    @Published public private(set) var diagnostics: [Diagnostic] = []
    @Published public private(set) var console: [ConsoleEntry] = []
    @Published public private(set) var connection: Connection = .offline("Not connected")
    @Published public private(set) var isBusy = false
    @Published public private(set) var needsCompile = false
    @Published public private(set) var lastCompileSucceeded = true
    @Published public private(set) var status: String = "Choose a project"
    @Published public var selectedClass: String?
    @Published public var editingFile: SourceFile?

    public let store: ProjectStore
    public let api: CoreAPI
    private var capturedOutput = ""

    public init(api: CoreAPI, store: ProjectStore) {
        self.api = api
        self.store = store
    }

    public var fileCount: Int { files.count }

    public var compileErrors: [Diagnostic] { diagnostics.filter(\.isError) }

    // MARK: - Lifecycle

    public func start() async {
        do {
            _ = try store.seedWelcomeProjectIfNeeded()
            projects = try store.projects()
        } catch {
            present(error)
        }
        do {
            _ = try await api.classes()
            connection = .ready
        } catch {
            connection = .offline((error as? CoreError)?.errorDescription ?? error.localizedDescription)
        }
    }

    public func closeProject() {
        project = nil
        files = []
        classes = []
        bench = []
        diagnostics = []
        console = []
        selectedClass = nil
        status = "Choose a project"
        refreshProjects()
    }

    public func refreshProjects() {
        do {
            projects = try store.projects()
        } catch {
            present(error)
        }
    }

    public func createProject(named name: String) async {
        do {
            let summary = try store.createProject(named: name)
            projects = try store.projects()
            await openProject(summary.name)
        } catch {
            present(error)
        }
    }

    public func deleteProject(named name: String) {
        do {
            try store.deleteProject(named: name)
            if project == name {
                project = nil
                files = []
                classes = []
                bench = []
                diagnostics = []
                console = []
            }
            projects = try store.projects()
        } catch {
            present(error)
        }
    }

    public func openProject(_ name: String) async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            let loaded = try store.sources(in: name)
            try await api.reset()
            for file in loaded {
                try await api.setSource(path: file.path, text: file.text)
            }
            project = name
            files = loaded
            console = []
            capturedOutput = ""
            bench = []
            diagnostics = []
            needsCompile = false
            connection = .ready
            append("Opened project \(name).", isError: false)
            await compileNow(pushingSources: false)
        } catch {
            present(error)
        }
    }

    // MARK: - Compilation

    public func compile() async {
        guard project != nil, !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        await compileNow(pushingSources: true)
    }

    private func compileNow(pushingSources: Bool) async {
        do {
            if pushingSources {
                for file in files {
                    try await api.setSource(path: file.path, text: file.text)
                }
            }
            let outcome = try await api.compile()
            classes = outcome.classes
            diagnostics = outcome.diagnostics
            lastCompileSucceeded = outcome.success
            bench = []
            capturedOutput = outcome.output
            needsCompile = false
            connection = .ready
            if outcome.success {
                status = classes.isEmpty
                    ? "Nothing to compile"
                    : "Compiled \(classes.count) class\(classes.count == 1 ? "" : "es")"
                append(status, isError: false)
            } else {
                let errors = outcome.diagnostics.filter(\.isError)
                status = errors.count == 1 ? "1 compile error" : "\(errors.count) compile errors"
                append("Compilation failed.", isError: true)
                for diagnostic in outcome.diagnostics {
                    append("\(diagnostic.fileName):\(diagnostic.line): \(diagnostic.message)",
                           isError: diagnostic.isError)
                }
            }
        } catch {
            present(error)
        }
    }

    // MARK: - Sources

    @discardableResult
    public func newClass(named name: String, kind: String = "class") async -> SourceFile? {
        guard project != nil, !isBusy else { return nil }
        isBusy = true
        defer { isBusy = false }
        do {
            let file = try await api.newClass(name: name, kind: kind)
            try store.writeSource(file.text, path: file.path, in: project ?? "")
            files = try store.sources(in: project ?? "")
            connection = .ready
            append("Created \(file.fileName).", isError: false)
            await compileNow(pushingSources: false)
            selectedClass = file.declaredType
            return file
        } catch {
            present(error)
            return nil
        }
    }

    public func saveSource(path: String, text: String) async {
        guard let project, !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            try store.writeSource(text, path: path, in: project)
            try await api.setSource(path: path, text: text)
            files = try store.sources(in: project)
            needsCompile = true
            connection = .ready
            append("Saved \(path).", isError: false)
        } catch {
            present(error)
        }
    }

    public func deleteSource(path: String) async {
        guard let project, !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        do {
            try await api.deleteSource(path: path)
            try store.deleteSource(path: path, in: project)
            files = try store.sources(in: project)
            needsCompile = true
            await compileNow(pushingSources: false)
        } catch {
            present(error)
        }
    }

    public func source(forClassName className: String) -> SourceFile? {
        files.first { file in
            file.declaredType == CoreValue.simpleName(className)
        }
    }

    // MARK: - Bench

    @discardableResult
    public func instantiate(_ className: String, arguments: [CoreValue] = []) async -> BenchObject? {
        guard !isBusy else { return nil }
        isBusy = true
        defer { isBusy = false }
        do {
            let object = try await api.instantiate(className: className, arguments: arguments)
            bench = try await api.bench()
            connection = .ready
            await captureOutput()
            append("Created \(object.name) (\(object.simpleClassName)).", isError: false)
            return object
        } catch {
            present(error)
            return nil
        }
    }

    @discardableResult
    public func call(handle: Int, method: String, arguments: [CoreValue]) async -> CoreValue? {
        guard !isBusy else { return nil }
        isBusy = true
        defer { isBusy = false }
        let objectName = bench.first { $0.handle == handle }?.name ?? "object"
        do {
            let value = try await api.invoke(handle: handle, method: method, arguments: arguments)
            bench = try await api.bench()
            connection = .ready
            await captureOutput()
            append("\(objectName).\(method)() returned \(value.resultText).", isError: false)
            return value
        } catch {
            present(error, whileRunning: "\(objectName).\(method)()")
            return nil
        }
    }

    @discardableResult
    public func callStatic(className: String, method: String, arguments: [CoreValue]) async -> CoreValue? {
        guard !isBusy else { return nil }
        isBusy = true
        defer { isBusy = false }
        do {
            let value = try await api.invokeStatic(className: className, method: method, arguments: arguments)
            connection = .ready
            await captureOutput()
            append("\(CoreValue.simpleName(className)).\(method)() returned \(value.resultText).", isError: false)
            return value
        } catch {
            present(error, whileRunning: "\(CoreValue.simpleName(className)).\(method)()")
            return nil
        }
    }

    @discardableResult
    public func inspect(handle: Int) async -> BenchObject? {
        do {
            let refreshed = try await api.inspect(handle: handle)
            if let index = bench.firstIndex(where: { $0.handle == handle }) {
                bench[index] = refreshed
            }
            return refreshed
        } catch {
            present(error)
            return nil
        }
    }

    public func removeObject(handle: Int) async {
        do {
            try await api.removeObject(handle: handle)
            bench = try await api.bench()
            append("Removed an object from the bench.", isError: false)
        } catch {
            present(error)
        }
    }

    public func resetBench() async {
        do {
            try await api.clearBench()
            bench = []
        } catch {
            present(error)
        }
    }

    // MARK: - Console

    public func clearConsole() async {
        do {
            try await api.clearOutput()
            console = []
            capturedOutput = ""
        } catch {
            present(error)
        }
    }

    private func captureOutput() async {
        guard let text = try? await api.output() else { return }
        if text.hasPrefix(capturedOutput) {
            let delta = String(text.dropFirst(capturedOutput.count))
            if !delta.isEmpty {
                for line in delta.split(separator: "\n", omittingEmptySubsequences: false) where !line.isEmpty {
                    append(String(line), isError: false)
                }
            }
        }
        capturedOutput = text
    }

    private func append(_ text: String, isError: Bool) {
        console.append(ConsoleEntry(text: text, isError: isError))
        if console.count > 400 {
            console.removeFirst(console.count - 400)
        }
    }

    private func present(_ error: Error, whileRunning context: String? = nil) {
        if let core = error as? CoreError {
            switch core {
            case .core(let failure):
                let prefix = context.map { "Exception while running \($0): " } ?? ""
                append(prefix + failure.className + (failure.message.isEmpty ? "" : ": " + failure.message),
                       isError: true)
                if failure.unsupported {
                    append("This Java feature is not supported on iPad yet.", isError: false)
                }
                connection = .ready
            case .transport(let message):
                connection = .offline(message)
                append("Cannot reach the Java core: \(message)", isError: true)
            case .protocolViolation(let message):
                append("Unexpected core response: \(message)", isError: true)
            }
        } else {
            append(error.localizedDescription, isError: true)
        }
        status = "Something went wrong"
    }
}
