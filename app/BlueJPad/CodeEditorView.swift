import BlueJPadKit
import SwiftUI
import UIKit

struct CodeEditorView: View {
    @ObservedObject var workspace: Workspace
    let file: SourceFile
    @Environment(\.dismiss) private var dismiss
    @State private var text: String
    @State private var isSaving = false

    init(workspace: Workspace, file: SourceFile) {
        self.workspace = workspace
        self.file = file
        _text = State(initialValue: file.text)
    }

    private var fileDiagnostics: [Diagnostic] {
        workspace.diagnostics.filter { $0.path == file.path }
    }

    private var breakpointLines: Set<Int> {
        workspace.breakpoints(inPath: file.path)
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HighlightedCodeEditor(
                    text: $text,
                    breakpointLines: breakpointLines,
                    onToggleBreakpoint: { line in
                        workspace.toggleBreakpoint(path: file.path, line: line)
                    }
                )
                .background(Theme.panel)

                if !breakpointLines.isEmpty {
                    breakpointStrip
                    Divider()
                }

                if !fileDiagnostics.isEmpty {
                    Divider()
                    ScrollView(.vertical, showsIndicators: true) {
                        VStack(alignment: .leading, spacing: 4) {
                            ForEach(fileDiagnostics) { diagnostic in
                                Text("Line \(diagnostic.line): \(diagnostic.message)")
                                    .font(Theme.monoSmall)
                                    .foregroundStyle(diagnostic.isError ? Theme.error : Theme.warning)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                    }
                    .frame(maxHeight: 110)
                    .background(Theme.error.opacity(0.06))
                }
            }
            .navigationTitle(file.fileName)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        save()
                    } label: {
                        if isSaving {
                            ProgressView()
                        } else {
                            Text("Save")
                        }
                    }
                    .disabled(isSaving)
                    .accessibilityIdentifier("saveButton")
                }
            }
        }
    }

    private var breakpointStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Label("Breakpoints", systemImage: "circle.fill")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(Theme.error)
                ForEach(breakpointLines.sorted(), id: \.self) { line in
                    Button {
                        workspace.toggleBreakpoint(path: file.path, line: line)
                    } label: {
                        HStack(spacing: 3) {
                            Text("\(line)")
                                .font(Theme.monoSmall)
                            Image(systemName: "xmark")
                                .font(.system(size: 8, weight: .bold))
                        }
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.error)
                    .accessibilityIdentifier("removeBreakpoint-\(line)")
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("breakpointStrip")
    }

    private func save() {
        isSaving = true
        let contents = text
        Task {
            await workspace.saveSource(path: file.path, text: contents)
            isSaving = false
            dismiss()
        }
    }
}

struct HighlightedCodeEditor: UIViewRepresentable {
    @Binding var text: String
    var breakpointLines: Set<Int> = []
    var onToggleBreakpoint: ((Int) -> Void)?

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView()
        view.delegate = context.coordinator
        view.font = UIFont.monospacedSystemFont(ofSize: 14, weight: .regular)
        view.autocorrectionType = .no
        view.autocapitalizationType = .none
        view.smartQuotesType = .no
        view.smartDashesType = .no
        view.smartInsertDeleteType = .no
        view.spellCheckingType = .no
        view.keyboardType = .asciiCapable
        view.textContainerInset = UIEdgeInsets(top: 14, left: 10, bottom: 40, right: 10)
        view.backgroundColor = .clear
        view.text = text
        view.accessibilityIdentifier = "codeEditor"
        context.coordinator.apply(to: view)
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        context.coordinator.breakpointLines = breakpointLines
        context.coordinator.onToggleBreakpoint = onToggleBreakpoint
        if view.text != text {
            view.text = text
        }
        context.coordinator.apply(to: view)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text, breakpointLines: breakpointLines, onToggleBreakpoint: onToggleBreakpoint)
    }

    @MainActor
    final class Coordinator: NSObject, UITextViewDelegate {
        private let text: Binding<String>
        var breakpointLines: Set<Int>
        var onToggleBreakpoint: ((Int) -> Void)?

        init(text: Binding<String>, breakpointLines: Set<Int>, onToggleBreakpoint: ((Int) -> Void)?) {
            self.text = text
            self.breakpointLines = breakpointLines
            self.onToggleBreakpoint = onToggleBreakpoint
        }

        func textViewDidChange(_ textView: UITextView) {
            text.wrappedValue = textView.text
            apply(to: textView)
        }

        /// Adds "Toggle Breakpoint" to the selection menu, which is how BlueJ lets you set a
        /// breakpoint by line without a separate gutter.
        func textView(_ textView: UITextView, editMenuForTextIn range: NSRange,
                      suggestedActions: [UIMenuElement]) -> UIMenu? {
            guard let onToggleBreakpoint else { return nil }
            let line = CodeEditorGeometry.lineNumber(at: range.location, in: textView.text ?? "")
            let toggle = UIAction(title: "Toggle Breakpoint (line \(line))",
                                  image: UIImage(systemName: "circle.dashed")) { _ in
                onToggleBreakpoint(line)
            }
            return UIMenu(children: suggestedActions + [toggle])
        }

        func apply(to textView: UITextView) {
            guard let string = textView.text else { return }
            let selection = textView.selectedRange
            let offset = textView.contentOffset
            let font = UIFont.monospacedSystemFont(ofSize: 14, weight: .regular)
            let attributed = NSMutableAttributedString(string: string, attributes: [
                .font: font,
                .foregroundColor: UIColor.label
            ])
            for line in breakpointLines.sorted() {
                guard let range = CodeEditorGeometry.range(ofLine: line, in: string),
                      NSMaxRange(range) <= attributed.length else { continue }
                attributed.addAttribute(.backgroundColor,
                                        value: UIColor.systemRed.withAlphaComponent(0.14),
                                        range: range)
            }
            for token in JavaHighlighter.tokens(in: string) {
                guard NSMaxRange(token.range) <= attributed.length else { continue }
                attributed.addAttribute(.foregroundColor, value: Theme.token(token.kind), range: token.range)
            }
            textView.attributedText = attributed
            let location = min(selection.location, attributed.length)
            let length = min(selection.length, attributed.length - location)
            textView.selectedRange = NSRange(location: location, length: length)
            textView.contentOffset = offset
            textView.typingAttributes = [
                .font: font,
                .foregroundColor: UIColor.label
            ]
        }
    }
}

/// Line arithmetic shared by the breakpoint menu and the breakpoint highlighting.
enum CodeEditorGeometry {
    static func lineNumber(at location: Int, in text: String) -> Int {
        let ns = text as NSString
        let clamped = max(0, min(location, ns.length))
        let prefix = ns.substring(to: clamped)
        return prefix.components(separatedBy: "\n").count
    }

    /// The character range of a one-based line, including its newline.
    static func range(ofLine line: Int, in text: String) -> NSRange? {
        guard line > 0 else { return nil }
        let ns = text as NSString
        var index = 1
        var location = 0
        while location <= ns.length {
            let range = ns.lineRange(for: NSRange(location: location, length: 0))
            if index == line { return range }
            if range.length == 0 { return nil }
            location = range.location + range.length
            index += 1
        }
        return nil
    }
}
