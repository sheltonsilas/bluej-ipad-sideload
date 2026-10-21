import BlueJPadKit
import SwiftUI

struct CallTarget: Identifiable {
    let id = UUID()
    let title: String
    let detail: String
    let parameters: [String]
    let perform: @MainActor ([CoreValue]) async -> Void
}

struct WorkspaceView: View {
    @ObservedObject var workspace: Workspace
    @State private var showConsole = true
    @State private var showingNewClass = false
    @State private var callTarget: CallTarget?
    @State private var inspected: BenchObject?
    @State private var pendingDelete: ClassCard?

    var body: some View {
        VStack(spacing: 0) {
            if !workspace.compileErrors.isEmpty {
                errorStrip
                Divider()
            }
            HStack(spacing: 0) {
                ClassDiagramView(
                    workspace: workspace,
                    onOpenEditor: openEditor,
                    onCreateObject: createObject
                )
                Divider()
                SidebarView(
                    workspace: workspace,
                    inspected: $inspected,
                    onInspect: { object in Task { inspected = await workspace.inspect(handle: object.handle) } },
                    onCallInstance: callMethod,
                    onCallStatic: callStatic,
                    onCreateObject: createObject,
                    onOpenEditor: openEditor,
                    onDelete: { pendingDelete = $0 }
                )
                .frame(width: 342)
                .background(Theme.panel)
            }
            if showConsole {
                Divider()
                ConsolePanel(workspace: workspace)
                    .frame(height: 190)
            }
        }
        .background(Theme.canvas)
        .navigationTitle(workspace.project ?? "BlueJ")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { toolbarContent }
        .sheet(isPresented: $showingNewClass) {
            NewClassSheet(workspace: workspace)
        }
        .sheet(item: $callTarget) { target in
            MethodCallSheet(target: target)
        }
        .sheet(item: $workspace.editingFile) { file in
            CodeEditorView(workspace: workspace, file: file)
        }
        .confirmationDialog(
            "Delete \(pendingDelete?.simpleName ?? "class")?",
            isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            ),
            titleVisibility: .visible
        ) {
            Button("Delete Source File", role: .destructive) {
                if let card = pendingDelete {
                    Task { await workspace.deleteSource(path: card.path) }
                }
                pendingDelete = nil
            }
            Button("Cancel", role: .cancel) { pendingDelete = nil }
        } message: {
            Text("The class and its .java file are removed from the project.")
        }
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .navigationBarLeading) {
            Button {
                workspace.closeProject()
            } label: {
                Label("Projects", systemImage: "chevron.left")
            }
            .disabled(workspace.isDebugging)
            .accessibilityIdentifier("backToProjectsButton")
        }
        ToolbarItemGroup(placement: .primaryAction) {
            Button {
                showingNewClass = true
            } label: {
                Label("New Class", systemImage: "plus.square.on.square")
            }
            .disabled(workspace.isBusy || workspace.isDebugging)
            .accessibilityIdentifier("newClassButton")

            Button {
                Task { await workspace.compile() }
            } label: {
                HStack(spacing: 6) {
                    if workspace.isBusy {
                        ProgressView().controlSize(.small)
                    } else {
                        Image(systemName: "hammer.fill")
                    }
                    Text("Compile")
                }
            }
            .disabled(workspace.isBusy || workspace.isDebugging)
            .accessibilityIdentifier("compileButton")

            Button {
                showConsole.toggle()
            } label: {
                Label("Console", systemImage: "terminal")
            }
            .accessibilityIdentifier("consoleToggle")
        }
    }

    private var errorStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(Theme.error)
                ForEach(workspace.compileErrors) { diagnostic in
                    Button {
                        if let file = workspace.files.first(where: { $0.path == diagnostic.path }) {
                            workspace.editingFile = file
                        }
                    } label: {
                        Text("\(diagnostic.fileName):\(diagnostic.line) \(diagnostic.message)")
                            .font(Theme.monoSmall)
                            .lineLimit(1)
                    }
                    .buttonStyle(.bordered)
                    .tint(Theme.error)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
        }
        .background(Theme.error.opacity(0.08))
        .accessibilityIdentifier("compileErrors")
    }

    private func openEditor(_ card: ClassCard) {
        guard let file = workspace.source(forClassName: card.name) else { return }
        workspace.editingFile = file
    }

    private func createObject(_ card: ClassCard) {
        guard card.compiled, !card.error else {
            Task { await workspace.compile() }
            return
        }
        let constructors = card.methods.filter { $0.name == card.simpleName }
        if let parameterised = constructors.first(where: { !$0.parameters.isEmpty }),
           !constructors.contains(where: { $0.parameters.isEmpty }) {
            callTarget = CallTarget(
                title: "New \(card.simpleName)",
                detail: "Constructor parameters",
                parameters: parameterised.parameters
            ) { arguments in
                await workspace.instantiate(card.name, arguments: arguments)
            }
        } else {
            Task { await workspace.instantiate(card.name) }
        }
    }

    private func callMethod(_ object: BenchObject, _ method: ClassMethod) {
        if method.parameters.isEmpty {
            Task { await workspace.call(handle: object.handle, method: method.name, arguments: []) }
        } else {
            callTarget = CallTarget(
                title: method.name,
                detail: "\(object.name) : \(object.simpleClassName)",
                parameters: method.parameters
            ) { arguments in
                await workspace.call(handle: object.handle, method: method.name, arguments: arguments)
            }
        }
    }

    private func callStatic(_ card: ClassCard, _ method: ClassMethod) {
        if method.parameters.isEmpty {
            Task { await workspace.callStatic(className: card.name, method: method.name, arguments: []) }
        } else {
            callTarget = CallTarget(
                title: method.name,
                detail: "\(card.simpleName) (static)",
                parameters: method.parameters
            ) { arguments in
                await workspace.callStatic(className: card.name, method: method.name, arguments: arguments)
            }
        }
    }
}
