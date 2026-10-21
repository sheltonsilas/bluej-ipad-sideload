import BlueJPadKit
import SwiftUI

struct SidebarView: View {
    @ObservedObject var workspace: Workspace
    @Binding var inspected: BenchObject?
    var onInspect: (BenchObject) -> Void
    var onCallInstance: (BenchObject, ClassMethod) -> Void
    var onCallStatic: (ClassCard, ClassMethod) -> Void
    var onCreateObject: (ClassCard) -> Void
    var onOpenEditor: (ClassCard) -> Void
    var onDelete: (ClassCard) -> Void

    private var selectedCard: ClassCard? {
        guard let name = workspace.selectedClass else { return nil }
        return workspace.classes.first { $0.name == name }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                classesSection
                benchSection
                if let object = inspected {
                    InspectorView(object: object) { inspected = nil }
                }
                if let card = selectedCard {
                    ClassDetailView(
                        card: card,
                        onCreateObject: { onCreateObject(card) },
                        onOpenEditor: { onOpenEditor(card) },
                        onDelete: { onDelete(card) },
                        onCallStatic: { method in onCallStatic(card, method) }
                    )
                }
            }
            .padding(14)
        }
    }

    private var classesSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Classes")
                    .font(.headline)
                Spacer()
                Text("\(workspace.classes.count)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            if workspace.classes.isEmpty {
                Text("Compile the project to list its classes here.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 6)
            } else {
                ForEach(workspace.classes) { card in
                    Button {
                        workspace.selectedClass = card.name
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: card.error
                                  ? "exclamationmark.triangle.fill"
                                  : (card.compiled ? "square.fill" : "square.dashed"))
                                .font(.caption2)
                                .foregroundStyle(card.error
                                                 ? Theme.error
                                                 : (card.compiled ? Theme.accent : Color.secondary))
                            Text(card.simpleName)
                                .font(.subheadline)
                                .lineLimit(1)
                            Spacer()
                            if workspace.selectedClass == card.name {
                                Image(systemName: "checkmark")
                                    .font(.caption2.weight(.semibold))
                                    .foregroundStyle(Theme.accent)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("classRow-\(card.name)")
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
                    }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("classList")
    }

    private var benchSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Object Bench")
                    .font(.headline)
                Spacer()
                if !workspace.bench.isEmpty {
                    Button("Clear") {
                        Task { await workspace.resetBench() }
                    }
                    .font(.caption)
                    .buttonStyle(.borderless)
                }
            }

            if workspace.bench.isEmpty {
                Text("Create an object from a class to put it on the bench.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .padding(.vertical, 6)
            } else {
                ForEach(workspace.bench) { object in
                    BenchCardView(
                        object: object,
                        methods: instanceMethods(for: object),
                        onInspect: { onInspect(object) },
                        onMethod: { method in onCallInstance(object, method) },
                        onRemove: { Task { await workspace.removeObject(handle: object.handle) } }
                    )
                    .onTapGesture { onInspect(object) }
                }
            }
        }
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("benchPanel")
    }

    private func instanceMethods(for object: BenchObject) -> [ClassMethod] {
        guard let card = workspace.classes.first(where: { $0.name == object.className }) else { return [] }
        return card.methods.filter { !$0.isStatic && $0.name != card.simpleName }
    }
}

struct BenchCardView: View {
    let object: BenchObject
    let methods: [ClassMethod]
    let onInspect: () -> Void
    let onMethod: (ClassMethod) -> Void
    let onRemove: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(object.name)
                        .font(.subheadline.weight(.semibold))
                    Text(object.simpleClassName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Menu {
                    if methods.isEmpty {
                        Text("No methods")
                    }
                    ForEach(methods) { method in
                        Button(method.signature) { onMethod(method) }
                            .accessibilityIdentifier("method-\(method.name)")
                    }
                    Divider()
                    Button("Remove from Bench", role: .destructive, action: onRemove)
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.body)
                }
                .accessibilityIdentifier("methods-\(object.name)")
            }

            HStack(spacing: 10) {
                Text(summary)
                    .font(Theme.monoSmall)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                Spacer()
                Button("Inspect", action: onInspect)
                    .font(.caption2)
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("inspect-\(object.name)")
            }
        }
        .padding(11)
        .background(RoundedRectangle(cornerRadius: 11).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: 11)
                .strokeBorder(Color.secondary.opacity(0.18), lineWidth: 1)
        )
        .accessibilityIdentifier("bench-\(object.name)")
    }

    private var summary: String {
        if object.fields.isEmpty { return "no fields" }
        return object.fields.prefix(2)
            .map { "\($0.name) = \($0.value.displayText)" }
            .joined(separator: ", ")
    }
}

struct ClassDetailView: View {
    let card: ClassCard
    let onCreateObject: () -> Void
    let onOpenEditor: () -> Void
    let onDelete: () -> Void
    let onCallStatic: (ClassMethod) -> Void

    private var staticMethods: [ClassMethod] { card.methods.filter(\.isStatic) }
    private var instanceMethods: [ClassMethod] {
        card.methods.filter { !$0.isStatic && $0.name != card.simpleName }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text(card.simpleName)
                        .font(.headline)
                    Text(card.kind)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(role: .destructive, action: onDelete) {
                    Image(systemName: "trash")
                }
                .buttonStyle(.borderless)
            }

            HStack(spacing: 8) {
                Button(action: onCreateObject) {
                    Label("Create Object", systemImage: "plus.circle.fill")
                }
                .buttonStyle(.borderedProminent)
                .font(.caption)
                .disabled(!card.compiled || card.error)
                .accessibilityIdentifier("instantiateButton")

                Button(action: onOpenEditor) {
                    Label("Edit", systemImage: "square.and.pencil")
                }
                .buttonStyle(.bordered)
                .font(.caption)
                .accessibilityIdentifier("openEditorButton")
            }

            if !staticMethods.isEmpty {
                Text("Static methods")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                ForEach(staticMethods) { method in
                    Button {
                        onCallStatic(method)
                    } label: {
                        Text(method.signature)
                            .font(Theme.monoSmall)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("static-\(method.name)")
                }
            }

            if !instanceMethods.isEmpty {
                Text("Instance methods")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                ForEach(instanceMethods) { method in
                    Text(method.signature)
                        .font(Theme.monoSmall)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 11).fill(Theme.card))
        .accessibilityIdentifier("classDetail")
    }
}

struct InspectorView: View {
    let object: BenchObject
    let onClose: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Inspector")
                    .font(.headline)
                Spacer()
                Button(action: onClose) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.borderless)
            }
            Text("\(object.name) : \(object.simpleClassName)")
                .font(Theme.mono)
                .foregroundStyle(.secondary)

            if object.fields.isEmpty {
                Text("No instance fields.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ForEach(object.fields) { field in
                FieldRow(field: field)
            }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 11).fill(Theme.card))
        .overlay(
            RoundedRectangle(cornerRadius: 11)
                .strokeBorder(Theme.accent.opacity(0.35), lineWidth: 1)
        )
        .accessibilityIdentifier("inspector")
    }
}

struct FieldRow: View {
    let field: FieldValue
    @State private var expanded = false

    private var isExpandable: Bool {
        switch field.value {
        case .object, .array: return true
        default: return false
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack(spacing: 8) {
                Text(field.name)
                    .font(Theme.mono)
                Spacer()
                if isExpandable {
                    Button {
                        expanded.toggle()
                    } label: {
                        Image(systemName: expanded ? "chevron.down" : "chevron.right")
                            .font(.caption2)
                    }
                    .buttonStyle(.borderless)
                }
                Text(field.value.displayText)
                    .font(Theme.mono)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.trailing)
            }
            .accessibilityIdentifier("field-\(field.name)")

            if expanded {
                switch field.value {
                case .object(_, let fields):
                    ForEach(fields) { child in
                        HStack {
                            Text(child.name).font(Theme.monoSmall)
                            Spacer()
                            Text(child.value.displayText)
                                .font(Theme.monoSmall)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.leading, 14)
                    }
                case .array(_, let items):
                    ForEach(Array(items.enumerated()), id: \.offset) { pair in
                        HStack {
                            Text("[\(pair.offset)]").font(Theme.monoSmall).foregroundStyle(.secondary)
                            Spacer()
                            Text(pair.element.displayText)
                                .font(Theme.monoSmall)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.leading, 14)
                    }
                default:
                    EmptyView()
                }
            }
        }
    }
}
