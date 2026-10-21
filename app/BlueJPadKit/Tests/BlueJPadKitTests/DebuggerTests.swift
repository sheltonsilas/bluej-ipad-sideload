import XCTest
@testable import BlueJPadKit

@MainActor
final class DebuggerTests: XCTestCase {

    private static let pausedJSON = JSONValue.obj(
        JSONMember("status", .string("paused")),
        JSONMember("active", .bool(true)),
        JSONMember("output", .string("")),
        JSONMember("frames", .array([
            .obj(
                JSONMember("class", .string("Counter")),
                JSONMember("method", .string("increment")),
                JSONMember("line", .int(12)),
                JSONMember("locals", .array([
                    .obj(
                        JSONMember("name", .string("amount")),
                        JSONMember("type", .string("int")),
                        JSONMember("value", .obj(JSONMember("int", .int(5))))
                    )
                ])),
                JSONMember("fields", .array([
                    .obj(
                        JSONMember("name", .string("count")),
                        JSONMember("type", .string("field")),
                        JSONMember("value", .obj(JSONMember("int", .int(3))))
                    )
                ]))
            ),
            .obj(
                JSONMember("class", .string("Counter")),
                JSONMember("method", .string("main")),
                JSONMember("line", .int(20)),
                JSONMember("locals", .array([])),
                JSONMember("fields", .array([]))
            )
        ]))
    )

    func testParsesAPausedSnapshot() {
        let snapshot = DebugSnapshot(json: Self.pausedJSON)

        XCTAssertEqual(snapshot.status, .paused)
        XCTAssertTrue(snapshot.isPaused)
        XCTAssertEqual(snapshot.frames.count, 2)
        XCTAssertEqual(snapshot.topFrame?.location, "Counter.increment:12")
        XCTAssertEqual(snapshot.topFrame?.locals.first?.value, .int(5))
        XCTAssertEqual(snapshot.topFrame?.fields.first?.name, "count")
        XCTAssertTrue(snapshot.summary.contains("Paused at Counter.increment:12"))
    }

    func testParsesFailuresAndCompletedRuns() {
        let failed = DebugSnapshot(json: .obj(
            JSONMember("status", .string("failed")),
            JSONMember("active", .bool(false)),
            JSONMember("error", .obj(
                JSONMember("class", .string("java.lang.ArithmeticException")),
                JSONMember("message", .string("/ by zero"))
            ))
        ))
        XCTAssertEqual(failed.status, .failed)
        XCTAssertEqual(failed.errorClass, "java.lang.ArithmeticException")
        XCTAssertTrue(failed.summary.contains("ArithmeticException: / by zero"))

        let finished = DebugSnapshot(json: .obj(
            JSONMember("status", .string("finished")),
            JSONMember("active", .bool(false)),
            JSONMember("value", .obj(JSONMember("int", .int(6))))
        ))
        XCTAssertEqual(finished.value, .int(6))
        XCTAssertTrue(finished.summary.contains("finished: 6"))
    }

    func testStepActionsUseTheProtocolSpelling() {
        XCTAssertEqual(DebugAction.resume.rawValue, "continue")
        XCTAssertEqual(DebugAction.stepOver.rawValue, "stepOver")
        XCTAssertEqual(DebugAction.stepInto.rawValue, "stepInto")
        XCTAssertEqual(DebugAction.stepOut.rawValue, "stepOut")
        XCTAssertEqual(DebugAction.stop.rawValue, "stop")
    }

    func testBreakpointsAreSentAsClassLinePointsAndTheSessionDrivesTheWorkspace() async throws {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("bluejpad-debug-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let store = ProjectStore(root: root)
        _ = try store.createProject(named: "Demo")
        try store.writeSource("public class Counter { }", path: "Counter.java", in: "Demo")

        let lock = NSLock()
        var sent: [JSONValue] = []
        let client = ScriptedCoreClient { request in
            lock.lock()
            sent.append(request)
            lock.unlock()
            switch request["op"]?.stringValue {
            case "classes", "compile":
                return .obj(
                    JSONMember("success", .bool(true)),
                    JSONMember("revision", .int(1)),
                    JSONMember("classes", .array([
                        .obj(
                            JSONMember("name", .string("Counter")),
                            JSONMember("kind", .string("class")),
                            JSONMember("path", .string("Counter.java")),
                            JSONMember("compiled", .bool(true)),
                            JSONMember("error", .bool(false)),
                            JSONMember("fields", .array([])),
                            JSONMember("methods", .array([]))
                        )
                    ])),
                    JSONMember("diagnostics", .array([])),
                    JSONMember("output", .obj(JSONMember("text", .string(""))))
                )
            case "debugBreakpoints":
                return .obj(JSONMember("points", .array(request["points"]?.arrayValue ?? [])))
            case "debugStart":
                return Self.pausedJSON
            case "debugCommand":
                return .obj(
                    JSONMember("status", .string("finished")),
                    JSONMember("active", .bool(false)),
                    JSONMember("frames", .array([])),
                    JSONMember("output", .string(""))
                )
            case "output":
                return .obj(JSONMember("output", .string("")))
            default:
                return .obj(JSONMember("ok", .bool(true)))
            }
        }
        let workspace = Workspace(api: CoreAPI(client: client), store: store)
        await workspace.openProject("Demo")

        workspace.toggleBreakpoint(path: "Counter.java", line: 12)
        XCTAssertEqual(workspace.breakpoints(inPath: "Counter.java"), [12])
        XCTAssertEqual(workspace.debugPoints, ["Counter#12"])

        await workspace.debugMain(className: "Counter")
        XCTAssertTrue(workspace.isPaused)
        XCTAssertEqual(workspace.debugSnapshot?.topFrame?.line, 12)
        XCTAssertTrue(workspace.isDebugging)
        XCTAssertTrue(workspace.console.contains { $0.text.contains("Paused at Counter.increment:12") })

        await workspace.debugStep(.stepOver)
        XCTAssertFalse(workspace.isPaused)
        XCTAssertNil(workspace.debugSnapshot)
        XCTAssertFalse(workspace.isDebugging)
        XCTAssertTrue(workspace.console.contains { $0.text.contains("Debug run finished") })

        // Toggling the same line clears the breakpoint again.
        workspace.toggleBreakpoint(path: "Counter.java", line: 12)
        XCTAssertTrue(workspace.breakpoints(inPath: "Counter.java").isEmpty)

        let ops = lock.withLock { sent.map { $0["op"]?.stringValue ?? "" } }
        XCTAssertTrue(ops.contains("debugBreakpoints"))
        XCTAssertTrue(ops.contains("debugStart"))
        XCTAssertTrue(ops.contains("debugCommand"))
    }
}
