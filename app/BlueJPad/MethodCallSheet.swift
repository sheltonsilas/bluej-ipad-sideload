import BlueJPadKit
import SwiftUI

struct MethodCallSheet: View {
    let target: CallTarget
    @Environment(\.dismiss) private var dismiss
    @State private var values: [String]
    @State private var validationMessage: String?
    @State private var isRunning = false

    init(target: CallTarget) {
        self.target = target
        _values = State(initialValue: Array(repeating: "", count: target.parameters.count))
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    ForEach(Array(target.parameters.enumerated()), id: \.offset) { pair in
                        HStack {
                            Text(CoreValue.simpleName(pair.element))
                                .font(Theme.mono)
                                .foregroundStyle(.secondary)
                                .frame(width: 70, alignment: .leading)
                            TextField("value", text: $values[pair.offset])
                                .font(Theme.mono)
                                .keyboardType(.numbersAndPunctuation)
                                .autocorrectionDisabled()
                                .textInputAutocapitalization(.never)
                                .accessibilityIdentifier("param-\(pair.offset)")
                        }
                    }
                } header: {
                    Text("Parameters")
                } footer: {
                    Text("Integers, doubles, booleans, chars and Strings are accepted.")
                }

                if let validationMessage {
                    Section {
                        Text(validationMessage)
                            .foregroundStyle(Theme.error)
                            .font(.footnote)
                    }
                }
            }
            .navigationTitle(target.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        submit()
                    } label: {
                        if isRunning {
                            ProgressView()
                        } else {
                            Text("Call")
                        }
                    }
                    .disabled(isRunning)
                    .accessibilityIdentifier("callButton")
                }
            }
            .safeAreaInset(edge: .top) {
                Text(target.detail)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18)
                    .padding(.top, 6)
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func submit() {
        validationMessage = nil
        do {
            var arguments: [CoreValue] = []
            for (index, type) in target.parameters.enumerated() {
                arguments.append(try CoreValue.argument(text: values[index], javaType: type))
            }
            isRunning = true
            let perform = target.perform
            Task {
                await perform(arguments)
                isRunning = false
                dismiss()
            }
        } catch {
            validationMessage = error.localizedDescription
        }
    }
}
