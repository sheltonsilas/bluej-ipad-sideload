import Foundation

public enum JavaTokenKind: String, Equatable, Sendable {
    case keyword
    case type
    case string
    case comment
    case number
    case annotation
}

public struct JavaToken: Equatable, Sendable {
    public let range: NSRange
    public let kind: JavaTokenKind

    public init(range: NSRange, kind: JavaTokenKind) {
        self.range = range
        self.kind = kind
    }
}

/// A deliberately small Java tokenizer: enough for keyword, type, literal and comment
/// colouring without shipping a parser. Ranges are UTF-16 offsets, ready for NSAttributedString.
public enum JavaHighlighter {

    private static let keywords: Set<String> = [
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "record", "return", "short", "static", "strictfp", "super", "switch", "synchronized",
        "this", "throw", "throws", "transient", "try", "var", "void", "volatile", "while",
        "true", "false", "null"
    ]

    public static func tokens(in source: String) -> [JavaToken] {
        let text = source as NSString
        let length = text.length
        var tokens: [JavaToken] = []
        var index = 0

        func peek(_ offset: Int) -> unichar {
            let position = index + offset
            return position < length ? text.character(at: position) : 0
        }

        func isIdentifierStart(_ character: unichar) -> Bool {
            (character >= 65 && character <= 90)
                || (character >= 97 && character <= 122)
                || character == 95 || character == 36
                || character > 127
        }

        func isIdentifierBody(_ character: unichar) -> Bool {
            isIdentifierStart(character) || (character >= 48 && character <= 57)
        }

        func isDigit(_ character: unichar) -> Bool {
            character >= 48 && character <= 57
        }

        while index < length {
            let character = text.character(at: index)

            if character == 47 && peek(1) == 47 {
                let start = index
                while index < length && text.character(at: index) != 10 { index += 1 }
                tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .comment))
                continue
            }

            if character == 47 && peek(1) == 42 {
                let start = index
                index += 2
                while index < length {
                    if text.character(at: index) == 42 && index + 1 < length && text.character(at: index + 1) == 47 {
                        index += 2
                        break
                    }
                    index += 1
                }
                tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .comment))
                continue
            }

            if character == 34 || character == 39 {
                let quote = character
                let start = index
                index += 1
                while index < length {
                    let current = text.character(at: index)
                    if current == 92 {
                        index += 2
                        continue
                    }
                    index += 1
                    if current == quote { break }
                }
                tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .string))
                continue
            }

            if character == 64, isIdentifierStart(peek(1)) {
                let start = index
                index += 1
                while index < length && isIdentifierBody(text.character(at: index)) { index += 1 }
                tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .annotation))
                continue
            }

            if isDigit(character) {
                let start = index
                while index < length {
                    let current = text.character(at: index)
                    if isDigit(current) || current == 46 || current == 120 || current == 88
                        || (current >= 65 && current <= 70) || (current >= 97 && current <= 102)
                        || current == 95 {
                        index += 1
                    } else if current == 76 || current == 108 || current == 68 || current == 100 || current == 70 || current == 102 {
                        index += 1
                        break
                    } else {
                        break
                    }
                }
                tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .number))
                continue
            }

            if isIdentifierStart(character) {
                let start = index
                while index < length && isIdentifierBody(text.character(at: index)) { index += 1 }
                let word = text.substring(with: NSRange(location: start, length: index - start))
                if keywords.contains(word) {
                    tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .keyword))
                } else if let first = word.unicodeScalars.first, CharacterSet.uppercaseLetters.contains(first) {
                    tokens.append(JavaToken(range: NSRange(location: start, length: index - start), kind: .type))
                }
                continue
            }

            index += 1
        }
        return tokens
    }
}
