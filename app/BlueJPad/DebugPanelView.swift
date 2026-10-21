import BlueJPadKit
import SwiftUI

/// Shown in the sidebar while the debugger is paused: where execution stopped, the variables
/// the student can see, and the step controls.
struct DebugPanelView: View {
    @ObservedObject var workspace: Workspace
    let snapshot: DebugSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 6) {
                Image(systemName: "ladybug.fill")
                    .foregroundStyle(Theme.accent)
                Text("Debugger")
                    .font(.headline)
                Spacer()
                if workspace.isDebugBusy {
                    ProgressView().controlSize(.small)
                }
                Button {
                    Task { await workspace.debugStep(.stop) }
                } label: {
                    Image(systemName: "stop.fill")
                        .foregroundStyle(Theme.error)
                }
                .buttonStyle(.borderless)
                .accessibilityIdentifier("debugStopButton")
            }

            if let frame = snapshot.topFrame {
                VStack(alignment: .leading, spacing: 2) {
                    Text(frame.title)
                        .font(Theme.mono)
                    Text("line \(frame.line)")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                variableList(title: "Variables", variables: frame.locals, empty: "No local variables.")
                if !frame.fields.isEmpty {
                    variableList(title: "Fields", variables: frame.fields, empty: "")
                }

                if snapshot.frames.count > 1 {
                    Text("Call stack")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.secondary)
                    ForEach(snapshot.frames.dropFirst()) { caller in
                        Text(caller.location)
                            .font(Theme.monoSmall)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            HStack(spacing: 6) {
                ForEach([DebugAction.resume, .stepOver, .stepInto, .stepOut]) { action in
                    Button {
                        Task { await workspace.debugStep(action) }
                    } label: {
                        Image(systemName: action.symbol)
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .disabled(workspace.isDebugBusy)
                    .accessibilityLabel(action.title)
                    .accessibilityIdentifier("debug-\(action.rawValue)")
                }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 11).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: 11)
                .strokeBorder(Theme.accent.opacity(0.45), lineWidth: 1)
        )
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("debugPanel")
    }

    private func variableList(title: String, variables: [DebugVariable], empty: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            if variables.isEmpty && !empty.isEmpty {
                Text(empty)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            ForEach(variables) { variable in
                HStack(spacing: 6) {
                    Text(variable.name)
                        .font(Theme.monoSmall)
                    Text(variable.displayType)
                        .font(.system(size: 9))
                        .foregroundStyle(.tertiary)
                    Spacer()
                    Text(variable.value.displayText)
                        .font(Theme.monoSmall)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
                .accessibilityIdentifier("debugVar-\(variable.name)")
            }
        }
    }
}
