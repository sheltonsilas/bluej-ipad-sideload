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

    public var minY: Double { origin.y }
    public var maxY: Double { origin.y + size.height }
    public var minX: Double { origin.x }
    public var maxX: Double { origin.x + size.width }
}

public struct DiagramEdge: Equatable, Sendable {
    public let from: String
    public let to: String

    public init(from: String, to: String) {
        self.from = from
        self.to = to
    }
}

/// Lays the class diagram out in rows by inheritance depth: base classes on top, subclasses
/// below them, siblings side by side. Pure functions so the geometry is testable without a UI.
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
        gap: Double = 20
    ) -> [String: DiagramRect] {
        guard !cards.isEmpty else { return [:] }

        var rows: [Int: [String]] = [:]
        for card in cards {
            rows[inheritanceDepth(of: card, in: cards), default: []].append(card.name)
        }
        for (depth, names) in rows {
            rows[depth] = names.sorted()
        }

        let maxDepth = rows.keys.max() ?? 0
        var result: [String: DiagramRect] = [:]
        let usableHeight = max(0, canvas.height - margin * 2 - node.height)
        let usableWidth = max(0, canvas.width - margin * 2 - node.width)

        for (depth, names) in rows {
            let rowY: Double
            if maxDepth == 0 {
                rowY = (canvas.height - node.height) / 2
            } else {
                rowY = margin + usableHeight * Double(depth) / Double(maxDepth)
            }

            let rowWidth = Double(names.count) * node.width + Double(max(0, names.count - 1)) * gap
            let startX: Double
            if names.count == 1 {
                startX = (canvas.width - node.width) / 2
            } else if rowWidth <= usableWidth + node.width {
                startX = max(margin, (canvas.width - rowWidth) / 2)
            } else {
                startX = margin
            }

            for (index, name) in names.enumerated() {
                let x = names.count == 1
                    ? startX
                    : min(max(margin, startX + Double(index) * (node.width + gap)),
                          max(margin, canvas.width - margin - node.width))
                result[name] = DiagramRect(
                    x: x,
                    y: max(margin, min(rowY, max(margin, canvas.height - margin - node.height))),
                    width: node.width,
                    height: node.height
                )
            }
        }
        return result
    }
}
