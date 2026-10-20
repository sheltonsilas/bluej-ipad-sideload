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

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HighlightedCodeEditor(text: $text)
                    .background(Theme.panel)

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
        if view.text != text {
            view.text = text
        }
        context.coordinator.apply(to: view)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(text: $text)
    }

    final class Coordinator: NSObject, UITextViewDelegate {
        private let text: Binding<String>

        init(text: Binding<String>) {
            self.text = text
        }

        func textViewDidChange(_ textView: UITextView) {
            text.wrappedValue = textView.text
            apply(to: textView)
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
