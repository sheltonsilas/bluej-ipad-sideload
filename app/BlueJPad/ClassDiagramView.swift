import BlueJPadKit
import SwiftUI

struct ClassDiagramView: View {
    @ObservedObject var workspace: Workspace
    var onOpenEditor: (ClassCard) -> Void
    var onCreateObject: (ClassCard) -> Void

    private let nodeSize = DiagramSize(width: 176, height: 116)

    var body: some View {
        GeometryReader { proxy in
            let canvas = DiagramSize(width: proxy.size.width, height: proxy.size.height)
            let frames = ClassDiagramLayout.layout(cards: workspace.classes, canvas: canvas, node: nodeSize)
            ZStack(alignment: .topLeading) {
                Theme.canvas

                Canvas { context, _ in
                    for edge in ClassDiagramLayout.edges(for: workspace.classes) {
                        guard let from = frames[edge.from], let to = frames[edge.to] else { continue }
                        let start = CGPoint(x: from.center.x, y: from.minY)
                        let end = CGPoint(x: to.center.x, y: to.maxY)
                        var path = Path()
                        path.move(to: start)
                        let midY = (start.y + end.y) / 2
                        path.addCurve(
                            to: end,
                            control1: CGPoint(x: start.x, y: midY),
                            control2: CGPoint(x: end.x, y: midY)
                        )
                        context.stroke(path, with: .color(Theme.accent.opacity(0.45)), lineWidth: 1.6)

                        var head = Path()
                        head.move(to: CGPoint(x: end.x, y: end.y))
                        head.addLine(to: CGPoint(x: end.x - 6, y: end.y + 10))
                        head.addLine(to: CGPoint(x: end.x + 6, y: end.y + 10))
                        head.closeSubpath()
                        context.fill(head, with: .color(Theme.accent.opacity(0.55)))
                    }
                }

                ForEach(workspace.classes) { card in
                    if let frame = frames[card.name] {
                        ClassBoxView(card: card, isSelected: workspace.selectedClass == card.name)
                            .frame(width: frame.size.width, height: frame.size.height)
                            .position(x: frame.center.x, y: frame.center.y)
                            .onTapGesture(count: 2) { onOpenEditor(card) }
                            .onTapGesture { workspace.selectedClass = card.name }
                            .contextMenu {
                                Button {
                                    onOpenEditor(card)
                                } label: {
                                    Label("Open Editor", systemImage: "square.and.pencil")
                                }
                                Button {
                                    onCreateObject(card)
                                } label: {
                                    Label("Create Object", systemImage: "plus.circle")
                                }
                                Divider()
                                Button {
                                    workspace.selectedClass = card.name
                                    Task { await workspace.compile() }
                                } label: {
                                    Label("Compile Project", systemImage: "hammer")
                                }
                            }
                    }
                }

                if workspace.classes.isEmpty {
                    emptyState
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }

                statusBadge
                    .padding(14)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
            }
        }
        .accessibilityIdentifier("classDiagram")
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Image(systemName: "square.on.square.dashed")
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(.tertiary)
            Text(workspace.files.isEmpty ? "No classes yet" : "Nothing compiled yet")
                .font(.headline)
            Text(workspace.files.isEmpty
                 ? "Tap New Class to add your first Java class."
                 : "Tap Compile to build the project and draw its classes.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 300)
        }
        .foregroundStyle(.secondary)
    }

    private var statusBadge: some View {
        HStack(spacing: 8) {
            Circle()
                .fill(workspace.connection.isReady ? Theme.success : Theme.error)
                .frame(width: 7, height: 7)
            Text(workspace.status)
                .font(.caption)
                .foregroundStyle(.secondary)
            if workspace.needsCompile {
                Text("· unsaved changes")
                    .font(.caption)
                    .foregroundStyle(Theme.warning)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Capsule().fill(Theme.panel.opacity(0.9)))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("workspaceStatus")
    }
}

struct ClassBoxView: View {
    let card: ClassCard
    let isSelected: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color(uiColor: .secondarySystemBackground))

            if !card.compiled && !card.error {
                Canvas { context, size in
                    let rect = CGRect(origin: .zero, size: size)
                    context.clip(to: Path(roundedRect: rect, cornerRadius: 10))
                    context.stroke(
                        Theme.stripe(rect),
                        with: .color(Color.secondary.opacity(0.22)),
                        lineWidth: 1
                    )
                }
            }

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text(card.kind.uppercased())
                        .font(.system(size: 8.5, weight: .bold))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 1.5)
                        .background(Capsule().fill(Theme.accentSoft))
                        .foregroundStyle(Theme.accent)
                    Spacer()
                    if card.error {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .font(.caption2)
                            .foregroundStyle(Theme.error)
                    } else if !card.compiled {
                        Image(systemName: "hourglass")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }

                Text(card.simpleName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)

                if let parent = card.projectSuperclass {
                    Text("extends \(CoreValue.simpleName(parent))")
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else {
                    Text(card.error ? "has compile errors" : (card.compiled ? "compiled" : "not compiled"))
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Spacer(minLength: 0)

                HStack(spacing: 8) {
                    Label("\(card.fields.count)", systemImage: "square.split.2x1")
                    Label("\(card.methods.count)", systemImage: "function")
                    Spacer()
                }
                .font(.system(size: 9))
                .foregroundStyle(.secondary)
            }
            .padding(9)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .strokeBorder(
                    isSelected ? Theme.accent : (card.error ? Theme.error : Color.secondary.opacity(0.35)),
                    lineWidth: isSelected ? 2.5 : 1
                )
        )
        .contentShape(RoundedRectangle(cornerRadius: 10))
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(card.kind) \(card.simpleName)")
        .accessibilityIdentifier("class-\(card.name)")
    }
}
