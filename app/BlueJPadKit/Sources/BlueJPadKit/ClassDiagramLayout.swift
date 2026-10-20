import Foundation

public struct DiagramPoint: Equatable, Sendable {
    public var x: Double
    public var y: Double

    public init(x: Double, y: Double) {
        self.x = x
        self.y = y
    }
}

public struct DiagramSize: Equatable, Sendable {
    public var width: Double
    public var height: Double

    public init(width: Double, height: Double) {
        self.width = width
        self.height = height
    }
}

public struct DiagramRect: Equatable, Sendable {
    public var origin: DiagramPoint
    public var size: DiagramSize

    public init(x: Double, y: Double, width: Double, height: Double) {
        self.origin = DiagramPoint(x: x, y: y)
        self.size = DiagramSize(width: width, height: height)
    }

    public var center: DiagramPoint {
        DiagramPoint(x: origin.x + size.width / 2, y: origin.y + size.height / 2)
    }

    public var minX: Double { origin.x }
    public var minY: Double { origin.y }
    public var maxX: Double { origin.x + size.width }
    public var maxY: Double { origin.y + size.height }

    public func intersects(_ other: DiagramRect) -> Bool {
        minX < other.maxX && other.minX < maxX && minY < other.maxY && other.minY < maxY
    }
}

public struct DiagramEdge: Hashable, Sendable {
    public let from: String
    public let to: String

    public init(from: String, to: String) {
        self.from = from
        self.to = to
    }
}

/// Lays the class diagram out in rows by inheritance depth: base classes above their
/// subclasses, siblings side by side, wrapping when the canvas is too narrow for one row.
/// Pure functions so the geometry is testable without a UI.
public enum ClassDiagramLayout {

    public static func inheritanceDepth(of card: ClassCard, in cards: [ClassCard]) -> Int {
        var byName: [String: ClassCard] = [:]
        for card in cards { byName[card.name] = card }
        var depth = 0
        var current = card
        var visited: Set<String> = [card.name]
        while let parentName = current.projectSuperclass, let parent = byName[parentName] {
            if visited.contains(parentName) { break }
            visited.insert(parentName)
            depth += 1
            current = parent
            if depth > 20 { break }
        }
        return depth
    }

    public static func edges(for cards: [ClassCard]) -> [DiagramEdge] {
        let names = Set(cards.map(\.name))
        return cards.compactMap { card in
            guard let parent = card.projectSuperclass, names.contains(parent) else { return nil }
            return DiagramEdge(from: card.name, to: parent)
        }
    }

    public static func layout(
        cards: [ClassCard],
        canvas: DiagramSize,
        node: DiagramSize,
        margin: Double = 24,
        gap: Double = 20,
        rowGap: Double = 34
    ) -> [String: DiagramRect] {
        guard !cards.isEmpty else { return [:] }

        var rows: [Int: [String]] = [:]
        for card in cards {
            rows[inheritanceDepth(of: card, in: cards), default: []].append(card.name)
        }

        let usableWidth = max(node.width, canvas.width - margin * 2)
        // How many boxes fit side by side; at least one, even on a very narrow canvas.
        let perRow = max(1, Int((usableWidth + gap) / (node.width + gap)))
        var rects: [String: DiagramRect] = [:]
        var rowIndex = 0

        for depth in rows.keys.sorted() {
            let names = (rows[depth] ?? []).sorted()
            var start = 0
            while start < names.count {
                let chunk = Array(names[start..<min(start + perRow, names.count)])
                let rowWidth = Double(chunk.count) * node.width + Double(max(0, chunk.count - 1)) * gap
                let startX = margin + max(0, (usableWidth - rowWidth) / 2)
                let y = margin + Double(rowIndex) * (node.height + rowGap)
                for (index, name) in chunk.enumerated() {
                    rects[name] = DiagramRect(
                        x: startX + Double(index) * (node.width + gap),
                        y: y,
                        width: node.width,
                        height: node.height
                    )
                }
                rowIndex += 1
                start += chunk.count
            }
        }
        return rects
    }
}
