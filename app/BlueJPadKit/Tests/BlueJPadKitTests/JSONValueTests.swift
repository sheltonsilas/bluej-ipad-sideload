import XCTest
@testable import BlueJPadKit

final class JSONValueTests: XCTestCase {

    func testParsesObjectsInInsertionOrder() throws {
        let value = try JSONValue.parse(#"{"b":1,"a":"two","nested":{"x":[true,null,2.5]}}"#)
        XCTAssertEqual(value.members?.map(\.key), ["b", "a", "nested"])
        XCTAssertEqual(value["b"]?.intValue, 1)
        XCTAssertEqual(value["a"]?.stringValue, "two")
        XCTAssertEqual(value["nested"]?["x"]?.arrayValue?.count, 3)
        XCTAssertEqual(value["nested"]?["x"]?.arrayValue?[2].doubleValue, 2.5)
    }

    func testRoundTripsEscapedText() throws {
        let original = JSONValue.obj(
            JSONMember("text", .string("line1\n\"quoted\"\ttab\\slash \u{00E9}")),
            JSONMember("empty", .string(""))
        )
        XCTAssertEqual(try JSONValue.parse(original.encoded()), original)
    }

    func testSerialisesRequestsTheCoreUnderstands() throws {
        let request = JSONValue.obj(
            JSONMember("op", .string("invoke")),
            JSONMember("handle", .int(2)),
            JSONMember("args", .array([.obj(JSONMember("int", .int(5)))]))
        )
        XCTAssertEqual(request.encoded(),
                       #"{"op":"invoke","handle":2,"args":[{"int":5}]}"#)
    }

    func testRejectsTrailingDataAndBrokenInput() {
        XCTAssertThrowsError(try JSONValue.parse(#"{"a":1} extra"#))
        XCTAssertThrowsError(try JSONValue.parse("{\"a\":}"))
        XCTAssertThrowsError(try JSONValue.parse("{\"a\":1"))
    }

    func testUnicodeEscapesBecomeCharacters() throws {
        let value = try JSONValue.parse(#"{"s":"\u00e9\u0041"}"#)
        XCTAssertEqual(value["s"]?.stringValue, "\u{00E9}A")
    }
}
