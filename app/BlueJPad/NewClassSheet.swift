import BlueJPadKit
import SwiftUI

struct NewClassSheet: View {
    @ObservedObject var workspace: Workspace
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var kind = "class"
    @State private var message: String?
    @State private var isCreating = false

    private let kinds = ["class", "interface", "enum"]

    var body: some View {
        NavigationStack {
            Form {
                Section("Name") {
                    TextField("Counter", text: $name)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .font(Theme.mono)
                        .accessibilityIdentifier("classNameField")
                }

                Section("Kind") {
                    Picker("Kind", selection: $kind) {
                        ForEach(kinds, id: \.self) { value in
                            Text(value.capitalized).tag(value)
                        }
                    }
                    .pickerStyle(.segmented)
                }

                if let message {
                    Section {
                        Text(message)
                            .font(.footnote)
                            .foregroundStyle(Theme.error)
                    }
                }

                Section {
                    Text("A standard BlueJ class body is created, so the class compiles before you write a line.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("New Class")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        create()
                    } label: {
                        if isCreating {
                            ProgressView()
                        } else {
                            Text("Create")
                        }
                    }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || isCreating)
                    .accessibilityIdentifier("createClassButton")
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func create() {
        isCreating = true
        message = nil
        let requested = name.trimmingCharacters(in: .whitespaces)
        let requestedKind = kind
        Task {
            if let file = await workspace.newClass(named: requested, kind: requestedKind) {
                isCreating = false
                workspace.editingFile = file
                dismiss()
            } else {
                message = workspace.console.last(where: \.isError)?.text ?? "Could not create the class."
                isCreating = false
            }
        }
    }
}
