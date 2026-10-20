import BlueJPadKit
import SwiftUI

struct CoreSettingsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var address: String
    @State private var saved = false
    @State private var error: String?
    private let settings = CoreSettings()

    init() {
        _address = State(initialValue: CoreSettings().coreURL.absoluteString)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("http://192.168.1.20:8899", text: $address)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .font(Theme.mono)
                        .accessibilityIdentifier("coreAddressField")

                    if settings.isEnvironmentLocked {
                        Text("BLUEJ_CORE_URL is set for this launch, so the saved address is ignored.")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    if saved {
                        Label("Saved. Reopen the app to connect there.", systemImage: "checkmark.circle.fill")
                            .font(.caption)
                            .foregroundStyle(Theme.success)
                    }
                    if let error {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(Theme.error)
                    }
                } header: {
                    Text("Java core address")
                } footer: {
                    Text("Run core-server on a Mac with `java -jar core-server.jar --port 8899` and enter its address here. On the simulator, loopback works as is.")
                }
            }
            .navigationTitle("Java Core")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        if settings.save(address) {
                            saved = true
                            error = nil
                        } else {
                            error = "Enter an address such as 192.168.1.20:8899."
                        }
                    }
                    .accessibilityIdentifier("saveCoreAddressButton")
                }
            }
        }
        .presentationDetents([.medium])
    }
}
