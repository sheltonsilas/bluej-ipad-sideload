import XCTest
@testable import BlueJPadKit

final class ModelAndLayoutTests: XCTestCase {

    private func card(_ name: String, superclass: String? = nil, kind: String = "class") -> ClassCard {
        ClassCard(name: name, kind: kind, superclass: superclass)
    }

    func testDecodesTheClassCardShapeTheCoreSends() throws {
        let json = try JSONValue.parse("""
        {"name":"Counter","kind":"class","superclass":"java.lang.Object","path":"Counter.java",
         "compiled":true,"error":false,
         "fields":[{"name":"count","type":"int","static":false}],
         "methods":[{"name":"increment","parameters":[],"returns":"void","static":false},
                    {"name":"add","parameters":["int","int"],"returns":"int","static":true}]}
        """)
        let decoded = ClassCard(json: json)
        XCTAssertEqual(decoded.name, "Counter")
        XCTAssertEqual(decoded.simpleName, "Counter")
        XCTAssertTrue(decoded.compiled)
        XCTAssertNil(decoded.projectSuperclass, "java.lang.Object is not drawn")
        XCTAssertEqual(decoded.fields.map(\.name), ["count"])
        XCTAssertEqual(decoded.methods[1].signature, "add(int, int)")
    }

    func testDecodesBenchObjectsInFieldOrder() throws {
        let json = try JSONValue.parse("""
        {"handle":2,"name":"counter2","class":"Counter",
         "fields":{"count":{"int":4},"label":{"string":"clicks"}}}
        """)
        let object = BenchObject(json: json)
        XCTAssertEqual(object.handle, 2)
        XCTAssertEqual(object.name, "counter2")
        XCTAssertEqual(object.simpleClassName, "Counter")
        XCTAssertEqual(object.fields.map(\.name), ["count", "label"])
        XCTAssertEqual(object.fields.first?.value, .int(4))
    }

    func testInheritanceDepthAndEdges() {
        let cards = [
            card("Dog", superclass: "Animal"),
            card("Animal"),
            card("Cat", superclass: "Animal"),
            card("Puppy", superclass: "Dog")
        ]
        XCTAssertEqual(ClassDiagramLayout.inheritanceDepth(of: cards[0], in: cards), 1)
        XCTAssertEqual(ClassDiagramLayout.inheritanceDepth(of: cards[3], in: cards), 2)

        let edges = ClassDiagramLayout.edges(for: cards)
        XCTAssertEqual(Set(edges), [DiagramEdge(from: "Dog", to: "Animal"),
                                    DiagramEdge(from: "Cat", to: "Animal"),
                                    DiagramEdge(from: "Puppy", to: "Dog")])
    }

    func testLayoutStacksSubclassesBelowTheirSuperclass() throws {
        let cards = [
            card("Dog", superclass: "Animal"),
            card("Animal"),
            card("Cat", superclass: "Animal")
        ]
        let frames = ClassDiagramLayout.layout(
            cards: cards,
            canvas: DiagramSize(width: 800, height: 600),
            node: DiagramSize(width: 160, height: 90)
        )
        let animal = try XCTUnwrap(frames["Animal"])
        let dog = try XCTUnwrap(frames["Dog"])
        let cat = try XCTUnwrap(frames["Cat"])

        XCTAssertLessThan(animal.center.y, dog.center.y)
        XCTAssertEqual(dog.center.y, cat.center.y, accuracy: 0.001)
        XCTAssertNotEqual(dog.center.x, cat.center.x)
        for frame in frames.values {
            XCTAssertGreaterThanOrEqual(frame.minX, 0)
            XCTAssertGreaterThanOrEqual(frame.minY, 0)
            XCTAssertLessThanOrEqual(frame.maxX, 800)
            XCTAssertLessThanOrEqual(frame.maxY, 600)
        }
    }

    func testLayoutKeepsEveryClassVisible() {
        let cards = (1...12).map { card("Class\($0)") }
        let frames = ClassDiagramLayout.layout(
            cards: cards,
            canvas: DiagramSize(width: 1024, height: 700),
            node: DiagramSize(width: 150, height: 80)
        )
        XCTAssertEqual(frames.count, 12)
        XCTAssertEqual(Set(frames.values.map(\.center.y)).count, 1)
    }

    func testHighlightingFindsKeywordsTypesStringsAndComments() {
        let source = """
        // a note
        public class Dog extends Animal {
            String name = "Rex";
            int legs = 4;
        }
        """
        let tokens = JavaHighlighter.tokens(in: source)
        let ns = source as NSString
        let kinds = tokens.map { (ns.substring(with: $0.range), $0.kind) }
        XCTAssertTrue(kinds.contains { $0.0 == "public" && $0.1 == .keyword })
        XCTAssertTrue(kinds.contains { $0.0 == "class" && $0.1 == .keyword })
        XCTAssertTrue(kinds.contains { $0.0 == "Dog" && $0.1 == .type })
        XCTAssertTrue(kinds.contains { $0.0 == "\"Rex\"" && $0.1 == .string })
        XCTAssertTrue(kinds.contains { $0.0 == "4" && $0.1 == .number })
        XCTAssertTrue(kinds.contains { $0.0 == "// a note" && $0.1 == .comment })
    }

    func testHighlightingHandlesAnnotationsAndEscapes() {
        let source = #"@Override public String x() { return "a\"b"; }"#
        let tokens = JavaHighlighter.tokens(in: source)
        let ns = source as NSString
        let texts = tokens.map { ns.substring(with: $0.range) }
        XCTAssertTrue(texts.contains("@Override"))
        XCTAssertTrue(texts.contains(#""a\"b""#))
    }
}
