import XCTest

final class AppFlowUITests: XCTestCase {

    private var app: XCUIApplication!

    override func setUpWithError() throws {
        continueAfterFailure = false
        app = XCUIApplication()
        if let url = ProcessInfo.processInfo.environment["BLUEJ_CORE_URL"] {
            app.launchEnvironment["BLUEJ_CORE_URL"] = url
        }
    }

    private func launch(openingProject project: String? = nil) {
        if let project {
            app.launchEnvironment["BLUEJ_OPEN_PROJECT"] = project
        }
        app.launch()
    }

    private func attach(_ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    /// Identifiers can legitimately match more than one element (a toolbar button inside a
    /// sheet also shows up in its presentation host), so always resolve to the first match.
    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: identifier).firstMatch
    }

    func testProjectBrowserCompilesRunsAndInspects() throws {
        launch()
        let welcome = element("project-Welcome")
        XCTAssertTrue(welcome.waitForExistence(timeout: 30), "the Welcome project should be listed")
        attach("01-project-browser")

        welcome.tap()
        XCTAssertTrue(app.navigationBars["Welcome"].waitForExistence(timeout: 20), "the project should open")

        let counterRow = element("classRow-Counter")
        XCTAssertTrue(counterRow.waitForExistence(timeout: 45),
                      "compiling on open should list the classes\n\(app.debugDescription)")
        attach("02-class-diagram")

        // The Welcome classes are drawn in the diagram and must not overlap.
        let counterBox = element("class-Counter")
        let calculatorBox = element("class-Calculator")
        let greeterBox = element("class-Greeter")
        XCTAssertTrue(counterBox.exists, "the diagram should draw Counter")
        if calculatorBox.exists && greeterBox.exists {
            XCTAssertFalse(counterBox.frame.intersects(calculatorBox.frame), "Counter overlaps Calculator")
            XCTAssertFalse(counterBox.frame.intersects(greeterBox.frame), "Counter overlaps Greeter")
        }

        counterRow.tap()
        let create = element("instantiateButton")
        XCTAssertTrue(create.waitForExistence(timeout: 10), "selecting a class should offer Create Object")
        create.tap()

        XCTAssertTrue(element("bench-counter1").waitForExistence(timeout: 20),
                      "creating an object should put it on the bench")
        attach("03-object-bench")

        element("inspect-counter1").tap()
        XCTAssertTrue(element("inspector").waitForExistence(timeout: 10), "the inspector should open")
        attach("04-inspector")

        element("methods-counter1").tap()
        let increment = app.buttons["method-increment"]
        XCTAssertTrue(increment.waitForExistence(timeout: 10), "the bench menu should list instance methods")
        increment.tap()

        let returned = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS %@", "counter1.increment() finished")
        ).firstMatch
        XCTAssertTrue(returned.waitForExistence(timeout: 20), "increment() should run and log it")
        attach("05-method-called")

        element("methods-counter1").tap()
        let incrementBy = app.buttons["method-incrementBy"]
        if incrementBy.waitForExistence(timeout: 5) {
            incrementBy.tap()
            let field = app.textFields["param-0"]
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            field.tap()
            field.typeText("5")
            element("callButton").tap()
            let six = app.staticTexts.containing(
                NSPredicate(format: "label CONTAINS %@", "count = 6")
            ).firstMatch
            XCTAssertTrue(six.waitForExistence(timeout: 20), "incrementBy(5) should leave the count at 6")
        }
        attach("06-terminal")
    }

    func testDebuggingRunsMainAndStepsThroughIt() throws {
        launch(openingProject: "Welcome")
        XCTAssertTrue(element("classRow-Calculator").waitForExistence(timeout: 45),
                      "the Welcome project should compile")

        element("classRow-Calculator").tap()
        let debug = element("debugButton")
        XCTAssertTrue(debug.waitForExistence(timeout: 10),
                      "a class with main should offer Debug main\n\(app.debugDescription)")
        debug.tap()

        XCTAssertTrue(element("debugPanel").waitForExistence(timeout: 30),
                      "debugging should pause on the first line of main\n\(app.debugDescription)")
        XCTAssertTrue(app.staticTexts.containing(
            NSPredicate(format: "label BEGINSWITH %@", "line ")
        ).firstMatch.exists, "the panel should show the current line")
        attach("11-debugger-paused")

        element("debug-stepOver").tap()
        XCTAssertTrue(element("debugPanel").waitForExistence(timeout: 30),
                      "stepping should pause again")

        element("debug-continue").tap()
        let finished = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS %@", "Debug run finished")
        ).firstMatch
        XCTAssertTrue(finished.waitForExistence(timeout: 30), "continuing should finish the run")
        attach("12-debug-finished")
    }

    func testCreateAClassEditItAndCompile() throws {
        launch(openingProject: "Welcome")
        XCTAssertTrue(element("classRow-Counter").waitForExistence(timeout: 45),
                      "the Welcome project should open and compile\n\(app.debugDescription)")

        element("newClassButton").tap()
        let name = element("classNameField")
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        name.typeText("Dog")
        attach("07-new-class")
        element("createClassButton").tap()

        // Creating a class opens its editor with the standard BlueJ body.
        let editor = element("codeEditor")
        XCTAssertTrue(editor.waitForExistence(timeout: 20), "the new class should open in the editor")
        attach("08-editor")

        editor.tap()
        editor.typeText("\n// a comment added by the UI test\n")
        element("saveButton").tap()

        let dog = element("class-Dog")
        XCTAssertTrue(dog.waitForExistence(timeout: 20), "the new class should appear in the diagram")
        attach("09-after-save")

        element("compileButton").tap()
        let compiled = app.staticTexts.containing(
            NSPredicate(format: "label BEGINSWITH %@", "Compiled")
        ).firstMatch
        XCTAssertTrue(compiled.waitForExistence(timeout: 30), "compiling should report how many classes built")
        attach("10-compiled")
    }
}
