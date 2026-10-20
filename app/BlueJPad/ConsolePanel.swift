import BlueJPadKit
import SwiftUI

struct ConsolePanel: View {
    @ObservedObject var workspace: Workspace

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
            .accessibilityIdentifier("consoleText")
        }
        .background(Theme.panel)
    }
}
