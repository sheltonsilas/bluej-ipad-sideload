import BlueJPadKit
import SwiftUI

struct RootView: View {
    @ObservedObject var workspace: Workspace

    var body: some View {
        NavigationStack {
            Group {
                if workspace.project == nil {
                    ProjectBrowserView(workspace: workspace)
                } else {
                    WorkspaceView(workspace: workspace)
                }
            }
        }
    }
}

struct ProjectBrowserView: View {
    @ObservedObject var workspace: Workspace
    @State private var showingNewProject = false
    @State private var showingSettings = false
    @State private var newProjectName = ""
    @State private var isWorking = false

    var body: some View {
        List {
            Section {
                ForEach(workspace.projects) { project in
                    Button {
                        Task { await workspace.openProject(project.name) }
                    } label: {
                        HStack(spacing: 14) {
                            Image(systemName: "folder.fill")
                                .font(.title2)
                                .foregroundStyle(Theme.accent)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(project.name)
                                    .font(.headline)
                                    .foregroundStyle(.primary)
                                Text("Java project")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.footnote.weight(.semibold))
                                .foregroundStyle(.tertiary)
                        }
                        .padding(.vertical, 4)
                    }
                    .accessibilityIdentifier("project-\(project.name)")
                    .swipeActions {
                        Button(role: .destructive) {
                            workspace.deleteProject(named: project.name)
                        } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            } header: {
                Text("Projects")
            } footer: {
                Text("Projects are folders of .java files in Documents/BlueJ Projects.")
            }

            connectionSection
        }
        .navigationTitle("BlueJ")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    newProjectName = ""
                    showingNewProject = true
                } label: {
                    Label("New Project", systemImage: "plus")
                }
                .accessibilityIdentifier("newProjectButton")
            }
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showingSettings = true
                } label: {
                    Label("Core Settings", systemImage: "gearshape")
                }
                .accessibilityIdentifier("coreSettingsButton")
            }
        }
        .sheet(isPresented: $showingSettings) {
            CoreSettingsSheet()
        }
        .alert("New Project", isPresented: $showingNewProject) {
            TextField("Project name", text: $newProjectName)
                .accessibilityIdentifier("projectNameField")
            Button("Create") {
                let name = newProjectName
                Task {
                    isWorking = true
                    await workspace.createProject(named: name)
                    isWorking = false
                }
            }
            .accessibilityIdentifier("createProjectButton")
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("A new folder will hold the project's Java files.")
        }
    }

    private var connectionSection: some View {
        Section {
            HStack(spacing: 10) {
                Circle()
                    .fill(workspace.connection.isReady ? Theme.success : Theme.error)
                    .frame(width: 9, height: 9)
                VStack(alignment: .leading, spacing: 2) {
                    Text(workspace.connection.isReady ? "Java core connected" : "Java core not reachable")
                        .font(.subheadline.weight(.medium))
                    Text(workspace.connection.label)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
                Spacer()
                if isWorking {
                    ProgressView()
                } else {
                    Button("Retry") {
                        Task {
                            isWorking = true
                            await workspace.start()
                            isWorking = false
                        }
                    }
                    .buttonStyle(.bordered)
                }
            }
            .padding(.vertical, 2)
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("coreStatus")
        } header: {
            Text("Runtime")
        } footer: {
            Text("The app compiles and runs Java on the core over this connection.")
        }
    }
}
