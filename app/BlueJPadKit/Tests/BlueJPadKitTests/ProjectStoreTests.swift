import XCTest
@testable import BlueJPadKit

final class ProjectStoreTests: XCTestCase {

    private var root: URL!

    override func setUpWithError() throws {
        root = FileManager.default.temporaryDirectory
            .appendingPathComponent("bluejpad-tests-\(UUID().uuidString)", isDirectory: true)
    }

    override func tearDownWithError() throws {
        if let root { try? FileManager.default.removeItem(at: root) }
    }

    func testCreatesProjectsAndReadsSourcesBack() throws {
        let store = ProjectStore(root: root)
        let project = try store.createProject(named: "Shapes")
        XCTAssertEqual(project.name, "Shapes")

        try store.writeSource("public class Square { }", path: "Square.java", in: "Shapes")
        try store.writeSource("package shapes; public class Circle { }", path: "shapes/Circle.java", in: "Shapes")

        let sources = try store.sources(in: "Shapes")
        XCTAssertEqual(sources.map(\.path), ["Square.java", "shapes/Circle.java"])
        XCTAssertEqual(sources[1].text, "package shapes; public class Circle { }")
        XCTAssertEqual(sources[1].packageName, "shapes")

        XCTAssertEqual(try store.projects().map(\.name), ["Shapes"])
    }

    func testRejectsUnsafeNamesAndPaths() throws {
        let store = ProjectStore(root: root)
        XCTAssertThrowsError(try store.createProject(named: "../escape"))
        XCTAssertThrowsError(try store.createProject(named: "  "))
        _ = try store.createProject(named: "Safe")
        XCTAssertThrowsError(try store.createProject(named: "Safe"))
        XCTAssertThrowsError(try store.writeSource("x", path: "../Outside.java", in: "Safe"))
    }

    func testDeletingSourcesAndProjects() throws {
        let store = ProjectStore(root: root)
        _ = try store.createProject(named: "Scratch")
        try store.writeSource("class A { }", path: "A.java", in: "Scratch")
        try store.deleteSource(path: "A.java", in: "Scratch")
        XCTAssertTrue(try store.sources(in: "Scratch").isEmpty)
        try store.deleteProject(named: "Scratch")
        XCTAssertTrue(try store.projects().isEmpty)
    }

    func testSeedsTheWelcomeProjectOnlyOnce() throws {
        let store = ProjectStore(root: root)
        let first = try store.seedWelcomeProjectIfNeeded()
        XCTAssertEqual(first.map(\.name), ["Welcome"])
        let second = try store.seedWelcomeProjectIfNeeded()
        XCTAssertEqual(second.map(\.name), ["Welcome"])

        let sources = try store.sources(in: "Welcome")
        XCTAssertEqual(sources.map(\.path), ["Calculator.java", "Counter.java", "Greeter.java"])
        XCTAssertTrue(sources.allSatisfy { $0.text.contains("public class") })
    }

    func testStarterSourcesDeclareTheExpectedTypes() {
        for file in StarterProject.files {
            XCTAssertTrue(file.text.contains("public class"), file.path)
        }
    }
}
