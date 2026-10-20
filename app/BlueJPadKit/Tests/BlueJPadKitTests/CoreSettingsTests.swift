import XCTest
@testable import BlueJPadKit

final class CoreSettingsTests: XCTestCase {

    private var suite: UserDefaults!
    private var suiteName: String!

    override func setUpWithError() throws {
        suiteName = "bluejpad-settings-\(UUID().uuidString)"
        suite = UserDefaults(suiteName: suiteName)
    }

    override func tearDownWithError() throws {
        suite.removePersistentDomain(forName: suiteName)
    }

    func testFallsBackToLoopback() {
        let settings = CoreSettings(defaults: suite, environment: [:])
        XCTAssertEqual(settings.coreURL.absoluteString, "http://127.0.0.1:8899")
        XCTAssertFalse(settings.isEnvironmentLocked)
    }

    func testSavedAddressWinsAndSurvivesANewInstance() {
        let settings = CoreSettings(defaults: suite, environment: [:])
        XCTAssertTrue(settings.save("192.168.1.20:8899"))
        XCTAssertEqual(CoreSettings(defaults: suite, environment: [:]).coreURL.absoluteString,
                       "http://192.168.1.20:8899")
    }

    func testEnvironmentBeatsTheSavedValue() {
        _ = CoreSettings(defaults: suite, environment: [:]).save("http://10.0.0.5:9000")
        let settings = CoreSettings(defaults: suite, environment: ["BLUEJ_CORE_URL": "http://127.0.0.1:7777"])
        XCTAssertEqual(settings.coreURL.absoluteString, "http://127.0.0.1:7777")
        XCTAssertTrue(settings.isEnvironmentLocked)
    }

    func testRejectsEmptyAndHostlessValues() {
        XCTAssertNil(CoreSettings.normalized("   "))
        XCTAssertNil(CoreSettings.normalized("http://"))
        XCTAssertFalse(CoreSettings(defaults: suite, environment: [:]).save(""))
    }
}
