import XCTest
@testable import BlueJPadKit

private final class FakeCore: @unchecked Sendable {
    private let lock = NSLock()
    private var log: [String] = []

    var classNames: [String] = ["Counter"]
    var compileSucceeds = true
    var diagnostics: [Diagnostic] = []
    var invokeResult: CoreValue = .int(1)
    var invokeFailure: CoreFailure?
    var output = ""

    var operations: [String] {
        lock.lock()
        defer { lock.unlock() }
        return log
    }

    private func classJSON(_ name: String, compiled: Bool = true, error: Bool = false) -> JSONValue {
        .obj(
            JSONMember("name", .string(name)),
            JSONMember("kind", .string("class")),
            JSONMember("superclass", .string("java.lang.Object")),
            JSONMember("path", .string("\(name).java")),
            JSONMember("compiled", .bool(compiled)),
            JSONMember("error", .bool(error)),
            JSONMember("fields", .array([])),
            JSONMember("methods", .array([]))
        )
    }

    private func benchJSON() -> [JSONValue] {
        [.obj(
            JSONMember("handle", .int(1)),
            JSONMember("name", .string("counter1")),
            JSONMember("class", .string("Counter")),
            JSONMember("fields", .obj(
                JSONMember("count", .obj(JSONMember("int", .int(0))))
            ))
        )]
    }

    func handle(_ request: JSONValue) throws -> JSONValue {
        let op = request["op"]?.stringValue ?? ""
        lock.lock()
        log.append(op)
        lock.unlock()

        switch op {
        case "reset":
            return .obj(JSONMember("reset", .bool(true)))
        case "classes":
            return .obj(JSONMember("classes", .array(classNames.map { classJSON($0) })))
        case "setSource":
            return .obj(JSONMember("saved", .bool(true)), JSONMember("revision", .int(1)))
        case "newClass":
            let name = request["name"]?.stringValue ?? "New"
            return .obj(
                JSONMember("path", .string("\(name).java")),
                JSONMember("text", .string("public class \(name) { }")),
                JSONMember("revision", .int(2))
            )
        case "compile":
            let diagnostics = self.diagnostics
            return .obj(
                JSONMember("success", .bool(compileSucceeds)),
                JSONMember("revision", .int(3)),
                JSONMember("classes", .array(classNames.map {
                    classJSON($0, compiled: compileSucceeds, error: !compileSucceeds)
                })),
                JSONMember("diagnostics", .array(diagnostics.map { diagnostic in
                    .obj(
                        JSONMember("path", .string(diagnostic.path)),
                        JSONMember("line", .int(diagnostic.line)),
                        JSONMember("column", .int(diagnostic.column)),
                        JSONMember("message", .string(diagnostic.message)),
                        JSONMember("severity", .string(diagnostic.severity))
                    )
                })),
                JSONMember("output", .obj(JSONMember("text", .string(output))))
            )
        case "new":
            return .obj(
                JSONMember("handle", .int(1)),
                JSONMember("name", .string("counter1")),
                JSONMember("class", .string(request["class"]?.stringValue ?? "Counter")),
                JSONMember("fields", .obj(
                    JSONMember("count", .obj(JSONMember("int", .int(0))))
                ))
            )
        case "bench":
            return .obj(JSONMember("bench", .array(benchJSON())))
        case "invoke", "invokeStatic":
            if let failure = invokeFailure {
                return .obj(JSONMember("error", .obj(
                    JSONMember("class", .string(failure.className)),
                    JSONMember("message", .string(failure.message)),
                    JSONMember("unsupported", .bool(failure.unsupported))
                )))
            }
            return .obj(JSONMember("value", invokeResult.jsonArgument))
        case "output":
            return .obj(JSONMember("output", .string(output)))
        case "clearOutput", "clearBench":
            return .obj(JSONMember("cleared", .bool(true)))
        case "removeObject":
            return .obj(JSONMember("removed", .bool(true)))
        default:
            return .obj(JSONMember("ok", .bool(true)))
        }
    }
}

@MainActor
final class WorkspaceTests: XCTestCase {

    private struct Fixture {
        let root: URL
        let store: ProjectStore
        let core: FakeCore
        let workspace: Workspace

        func cleanUp() {
            try? FileManager.default.removeItem(at: root)
        }
    }

    private func makeFixture() throws -> Fixture {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("bluejpad-workspace-\(UUID().uuidString)", isDirectory: true)
        let store = ProjectStore(root: root)
        let core = FakeCore()
        let workspace = Workspace(
            api: CoreAPI(client: ScriptedCoreClient(handler: { try core.handle($0) })),
            store: store
        )
        _ = try store.createProject(named: "Demo")
        try store.writeSource("public class Counter { }", path: "Counter.java", in: "Demo")
        return Fixture(root: root, store: store, core: core, workspace: workspace)
    }

    func testOpeningAProjectPushesSourcesAndCompiles() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")

        XCTAssertEqual(fixture.workspace.project, "Demo")
        XCTAssertEqual(fixture.workspace.files.map(\.path), ["Counter.java"])
        XCTAssertEqual(fixture.workspace.classes.map(\.name), ["Counter"])
        XCTAssertTrue(fixture.workspace.connection.isReady)
        XCTAssertTrue(fixture.workspace.lastCompileSucceeded)

        let operations = fixture.core.operations
        XCTAssertEqual(operations.first, "reset")
        XCTAssertTrue(operations.contains("setSource"))
        XCTAssertTrue(operations.contains("compile"))
    }

    func testFailedCompilationSurfacesDiagnosticsWithoutLosingTheProject() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        fixture.core.compileSucceeds = false
        fixture.core.diagnostics = [
            Diagnostic(path: "Counter.java", line: 4, column: 9, message: "';' expected", severity: "error")
        ]
        await fixture.workspace.openProject("Demo")

        XCTAssertFalse(fixture.workspace.lastCompileSucceeded)
        XCTAssertEqual(fixture.workspace.compileErrors.count, 1)
        XCTAssertEqual(fixture.workspace.classes.first?.error, true)
        XCTAssertTrue(fixture.workspace.console.contains { $0.text.contains("Counter.java:4: ';' expected") })
    }

    func testInstantiateAndCallUpdateTheBenchAndConsole() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")
        fixture.core.invokeResult = .int(3)

        let object = await fixture.workspace.instantiate("Counter")
        XCTAssertEqual(object?.name, "counter1")
        XCTAssertEqual(fixture.workspace.bench.map(\.handle), [1])

        let result = await fixture.workspace.call(handle: 1, method: "increment", arguments: [])
        XCTAssertEqual(result, .int(3))
        XCTAssertTrue(fixture.workspace.console.contains { $0.text.contains("counter1.increment() returned 3") })
    }

    func testRuntimeExceptionsAreReportedLikeBlueJ() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")
        fixture.core.invokeFailure = CoreFailure(
            className: "java.lang.ArithmeticException",
            message: "/ by zero",
            unsupported: false
        )

        let result = await fixture.workspace.call(handle: 1, method: "divide", arguments: [])
        XCTAssertNil(result)
        XCTAssertTrue(fixture.workspace.console.contains {
            $0.isError && $0.text.contains("java.lang.ArithmeticException: / by zero")
        })
        XCTAssertTrue(fixture.workspace.connection.isReady,
                      "a student exception must not look like a broken core")
    }

    func testUnsupportedFeaturesGetAProductMessage() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")
        fixture.core.invokeFailure = CoreFailure(
            className: "java.lang.UnsupportedOperationException",
            message: "unsupported long",
            unsupported: true
        )
        _ = await fixture.workspace.call(handle: 1, method: "widen", arguments: [])
        XCTAssertTrue(fixture.workspace.console.contains { $0.text.contains("not supported on iPad yet") })
    }

    func testCreatingAClassWritesTheFileAndSelectsIt() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")
        let created = await fixture.workspace.newClass(named: "Dog")

        XCTAssertEqual(created?.path, "Dog.java")
        let onDisk = try fixture.store.sources(in: "Demo")
        XCTAssertEqual(onDisk.map(\.path), ["Counter.java", "Dog.java"])
        XCTAssertTrue(onDisk[1].text.contains("public class Dog"))
        XCTAssertEqual(fixture.workspace.selectedClass, "Dog")
    }

    func testSavingASourcePersistsItAndMarksTheProjectDirty() async throws {
        let fixture = try makeFixture()
        defer { fixture.cleanUp() }
        await fixture.workspace.openProject("Demo")
        await fixture.workspace.saveSource(path: "Counter.java", text: "public class Counter { int x; }")

        let onDisk = try fixture.store.sources(in: "Demo")
        XCTAssertEqual(onDisk.first?.text, "public class Counter { int x; }")
        XCTAssertTrue(fixture.workspace.needsCompile)
        XCTAssertTrue(fixture.core.operations.contains("setSource"))
    }
}
