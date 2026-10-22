import XCTest
@testable import BlueJPadKit

final class CoreValueTests: XCTestCase {

    func testDecodesEveryProtocolShape() throws {
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"int":42}"#)), .int(42))
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"long":9000000000}"#)), .long(9_000_000_000))
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"float":1.25}"#)), .float(1.25))
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"double":2.5}"#)), .double(2.5))
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"string":"hi"}"#)), .string("hi"))
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(#"{"null":true}"#)), .null)
        XCTAssertEqual(CoreValue(json: try JSONValue.parse(
            #"{"array":{"component":"int","items":[{"int":1},{"int":2}]}}"#)),
            .array(component: "int", items: [.int(1), .int(2)]))
        guard case .object(let className, let fields)? = CoreValue(json: try JSONValue.parse(
            #"{"object":"Counter","fields":{"count":{"int":3}}}"#)) else {
            return XCTFail("expected an object value")
        }
        XCTAssertEqual(className, "Counter")
        XCTAssertEqual(fields, [FieldValue(name: "count", value: .int(3))])
    }

    func testEncodesArgumentsForTheCore() {
        XCTAssertEqual(CoreValue.int(5).jsonArgument.encoded(), #"{"int":5}"#)
        XCTAssertEqual(CoreValue.long(9_000_000_000).jsonArgument.encoded(), #"{"long":9000000000}"#)
        XCTAssertEqual(CoreValue.float(1.25).jsonArgument.encoded(), #"{"float":1.25}"#)
        XCTAssertEqual(CoreValue.string("hi").jsonArgument.encoded(), #"{"string":"hi"}"#)
        XCTAssertEqual(CoreValue.reference(3).jsonArgument.encoded(), #"{"ref":3}"#)
        XCTAssertEqual(CoreValue.boolean(true).jsonArgument.encoded(), #"{"bool":true}"#)
        XCTAssertEqual(
            CoreValue.array(component: "int", items: [.int(1), .int(2)]).jsonArgument.encoded(),
            #"{"array":{"component":"int","items":[{"int":1},{"int":2}]}}"#
        )
    }

    func testFormatsValuesTheWayAnInspectorReads() {
        XCTAssertEqual(CoreValue.int(7).displayText, "7")
        XCTAssertEqual(CoreValue.long(9_000_000_000).displayText, "9000000000")
        XCTAssertEqual(CoreValue.float(0.5).displayText, "0.5")
        XCTAssertEqual(CoreValue.float(1.25).resultText, "1.25")
        XCTAssertEqual(CoreValue.double(2.5).displayText, "2.5")
        XCTAssertEqual(CoreValue.double(5).displayText, "5.0")
        XCTAssertEqual(CoreValue.string("hi").displayText, "\"hi\"")
        XCTAssertEqual(CoreValue.string("hi").resultText, "hi")
        XCTAssertEqual(CoreValue.null.displayText, "null")
        XCTAssertEqual(CoreValue.array(component: "int", items: [.int(1), .int(2)]).displayText, "int[2]")
    }

    func testParsesTypedParameters() throws {
        XCTAssertEqual(try CoreValue.argument(text: " 12 ", javaType: "int"), .int(12))
        XCTAssertEqual(try CoreValue.argument(text: "9000000000L", javaType: "long"), .long(9_000_000_000))
        XCTAssertEqual(try CoreValue.argument(text: "2.5f", javaType: "float"), .float(2.5))
        XCTAssertEqual(try CoreValue.argument(text: "2.5", javaType: "double"), .double(2.5))
        XCTAssertEqual(try CoreValue.argument(text: "true", javaType: "boolean"), .boolean(true))
        XCTAssertEqual(try CoreValue.argument(text: "Ada", javaType: "java.lang.String"), .string("Ada"))
        XCTAssertEqual(try CoreValue.argument(text: "x", javaType: "char"), .int(120))
        XCTAssertThrowsError(try CoreValue.argument(text: "twelve", javaType: "int"))
        XCTAssertThrowsError(try CoreValue.argument(text: "twelve", javaType: "long"))
        XCTAssertThrowsError(try CoreValue.argument(text: "half", javaType: "float"))
        XCTAssertTrue(CoreValue.float(1).isNumeric)
        XCTAssertTrue(CoreValue.long(1).isNumeric)
        XCTAssertThrowsError(try CoreValue.argument(text: "maybe", javaType: "boolean"))
        XCTAssertThrowsError(try CoreValue.argument(text: "x", javaType: "java.util.ArrayList"))
    }
}
