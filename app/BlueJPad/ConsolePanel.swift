import BlueJPadKit
import SwiftUI

struct ConsolePanel: View {
    @ObservedObject var workspace: Workspace
    @State private var input = ""
    @FocusState private var inputFocused: Bool

    private func send() {
        let line = input
        input = ""
        Task { await workspace.submitInput(line) }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Label("Terminal", systemImage: "terminal")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Clear") {
                    Task { await workspace.clearConsole() }
                }
                .font(.caption)
                .buttonStyle(.borderless)
                .accessibilityIdentifier("clearConsoleButton")
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)

            Divider()

            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 1) {
                        ForEach(workspace.console) { entry in
                            Text(entry.text)
                                .font(Theme.monoSmall)
                                .foregroundStyle(entry.isError ? Theme.error : Color.primary)
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        Color.clear
                            .frame(height: 1)
                            .id("console-bottom")
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                }
                .onChange(of: workspace.console.count) { _, _ in
                    withAnimation(.easeOut(duration: 0.12)) {
                        proxy.scrollTo("console-bottom", anchor: .bottom)
                    }
                }
                .onAppear {
                    proxy.scrollTo("console-bottom", anchor: .bottom)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("consoleText")

            if let prompt = workspace.awaitingInput {
                Divider()
                HStack(spacing: 8) {
                    Text("\(prompt.label) needs input")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .accessibilityIdentifier("stdinPrompt")
                    TextField("Type a line", text: $input)
                        .font(Theme.monoSmall)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .submitLabel(.send)
                        .focused($inputFocused)
                        .onSubmit(send)
                        .accessibilityIdentifier("stdinField")
                    Button(action: send) {
                        Image(systemName: "arrow.up.circle.fill")
                    }
                    .buttonStyle(.borderless)
                    .accessibilityLabel("Send input")
                    .accessibilityIdentifier("stdinSendButton")
                    Button("Stop") {
                        Task { await workspace.cancelInput() }
                    }
                    .font(.caption)
                    .buttonStyle(.borderless)
                    .accessibilityIdentifier("stdinCancelButton")
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
            }
        }
        .background(Theme.panel)
        .onChange(of: workspace.awaitingInput) { _, prompt in
            if prompt != nil { inputFocused = true }
        }
    }
}
