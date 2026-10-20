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

    private func element(_ identifier: String) -> XCUIElement {
        app.descendants(matching: .any)[identifier]
    }

    func testProjectBrowserCompilesRunsAndInspects() throws {
        launch()
        let welcome = element("project-Welcome")
        XCTAssertTrue(welcome.waitForExistence(timeout: 30), "the Welcome project should be listed")
        attach("01-project-browser")

        welcome.tap()
        XCTAssertTrue(app.navigationBars["Welcome"].waitForExistence(timeout: 20), "the project should open")
        attach("02-project-opened")

        let counter = app.staticTexts["Counter"]
        XCTAssertTrue(counter.waitForExistence(timeout: 45),
                      "compiling on open should draw the class diagram\n\(app.debugDescription)")
        attach("02-class-diagram")

        // The three Welcome classes must not overlap in the diagram.
        let calculator = app.staticTexts["Calculator"]
        let greeter = app.staticTexts["Greeter"]
        if calculator.exists && greeter.exists {
            XCTAssertFalse(counter.frame.intersects(calculator.frame), "Counter overlaps Calculator")
            XCTAssertFalse(counter.frame.intersects(greeter.frame), "Counter overlaps Greeter")
        }

        counter.tap()
        let create = element("instantiateButton")
        XCTAssertTrue(create.waitForExistence(timeout: 10))
        create.tap()

        let benchCard = app.staticTexts["counter1"]
        XCTAssertTrue(benchCard.waitForExistence(timeout: 20), "creating an object should put it on the bench")
        attach("03-object-bench")

        element("inspect-counter1").tap()
        XCTAssertTrue(app.staticTexts["Inspector"].waitForExistence(timeout: 10), "the inspector should open")
        attach("04-inspector")

        element("methods-counter1").tap()
        let increment = app.buttons["method-increment"]
        XCTAssertTrue(increment.waitForExistence(timeout: 10), "the bench menu should list instance methods")
        increment.tap()

        let returned = app.staticTexts.containing(
            NSPredicate(format: "label CONTAINS %@", "returned 1")
        ).firstMatch
        XCTAssertTrue(returned.waitForExistence(timeout: 20), "increment() should return 1 and log it")
        attach("05-method-called")

        // A runtime exception is reported in the terminal, not as a crash.
        element("methods-counter1").tap()
        let incrementBy = app.buttons["method-incrementBy"]
        if incrementBy.waitForExistence(timeout: 5) {
            incrementBy.tap()
            let field = app.textFields["param-0"]
            XCTAssertTrue(field.waitForExistence(timeout: 10))
            field.tap()
            field.typeText("5")
            app.buttons["callButton"].tap()
            let five = app.staticTexts.containing(
                NSPredicate(format: "label CONTAINS %@", "returned 6")
            ).firstMatch
            XCTAssertTrue(five.waitForExistence(timeout: 20))
        }
        attach("06-terminal")
    }

    func testCreateAClassEditItAndCompile() throws {
        launch(openingProject: "Welcome")
        XCTAssertTrue(app.staticTexts["Counter"].waitForExistence(timeout: 45),
                      "the Welcome project should open and compile\n\(app.debugDescription)")

        element("newClassButton").tap()
        let name = element("classNameField")
        XCTAssertTrue(name.waitForExistence(timeout: 10))
        name.tap()
        name.typeText("Dog")
        attach("07-new-class")
        element("createClassButton").tap()

        // Creating a class opens its editor with the standard BlueJ body.
        let editor = app.textViews["codeEditor"]
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
