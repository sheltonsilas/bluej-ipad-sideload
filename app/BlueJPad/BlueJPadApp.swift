import BlueJPadKit
import SwiftUI

@main
@MainActor
struct BlueJPadApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView(workspace: model.workspace)
                .task { await model.start() }
        }
    }
}

@MainActor
final class AppModel: ObservableObject {
    let workspace: Workspace
    let coreURL: URL

    init() {
        let environment = ProcessInfo.processInfo.environment
        let configured = environment["BLUEJ_CORE_URL"] ?? "http://127.0.0.1:8899"
        let url = URL(string: configured) ?? URL(string: "http://127.0.0.1:8899")!
        self.coreURL = url
        self.workspace = Workspace(
            api: CoreAPI(client: HTTPCoreClient(baseURL: url)),
            store: ProjectStore(root: ProjectStore.defaultRoot())
        )
    }

    func start() async {
        await workspace.start()
        let environment = ProcessInfo.processInfo.environment
        if let project = environment["BLUEJ_OPEN_PROJECT"], !project.isEmpty,
           workspace.projects.contains(where: { $0.name == project }) {
            await workspace.openProject(project)
        }
    }
}
