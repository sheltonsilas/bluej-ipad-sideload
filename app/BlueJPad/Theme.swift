import BlueJPadKit
import SwiftUI

enum Theme {
    static let accent = Color(red: 0.16, green: 0.42, blue: 0.76)
    static let accentSoft = Color(red: 0.16, green: 0.42, blue: 0.76).opacity(0.12)
    static let canvas = Color(uiColor: .systemGroupedBackground)
    static let panel = Color(uiColor: .secondarySystemGroupedBackground)
    static let card = Color(uiColor: .tertiarySystemGroupedBackground)
    static let error = Color(uiColor: .systemRed)
    static let success = Color(uiColor: .systemGreen)
    static let warning = Color(uiColor: .systemOrange)

    static let mono = Font.system(size: 13, weight: .regular, design: .monospaced)
    static let monoSmall = Font.system(size: 11.5, weight: .regular, design: .monospaced)

    static func token(_ kind: JavaTokenKind) -> UIColor {
        switch kind {
        case .keyword: return .systemPurple
        case .type: return .systemTeal
        case .string: return .systemRed
        case .comment: return .secondaryLabel
        case .number: return .systemBlue
        case .annotation: return .systemBrown
        }
    }

    static func stripe(_ rect: CGRect) -> Path {
        var path = Path()
        let spacing: CGFloat = 9
        var x: CGFloat = rect.minX - rect.height
        while x < rect.maxX {
            path.move(to: CGPoint(x: x, y: rect.maxY))
            path.addLine(to: CGPoint(x: x + rect.height, y: rect.minY))
            x += spacing
        }
        return path
    }
}
