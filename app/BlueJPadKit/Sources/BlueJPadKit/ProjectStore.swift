import Foundation

public struct ProjectSummary: Identifiable, Hashable, Sendable {
    public let name: String
    public let url: URL

    public var id: String { name }

    public init(name: String, url: URL) {
        self.name = name
        self.url = url
    }
}

public enum ProjectStoreError: Error, LocalizedError, Equatable {
    case invalidName
    case alreadyExists(String)
    case notFound(String)
    case unsafePath(String)

    public var errorDescription: String? {
        switch self {
        case .invalidName: return "Use letters, numbers, spaces, dashes or underscores for the project name."
        case .alreadyExists(let name): return "A project called \(name) already exists."
        case .notFound(let name): return "No project called \(name)."
        case .unsafePath(let path): return "The path \(path) leaves the project folder."
        }
    }
}

/// Projects live as plain folders of `.java` files in the app's Documents directory, with a
/// tiny `project.json` for metadata. Nothing is stored in an opaque database, so a project can
/// be copied out with the Files app.
public struct ProjectStore: Sendable {
    public static let metadataFile = "project.json"
    public let root: URL

    public init(root: URL) {
        self.root = root
    }

    public static func defaultRoot() -> URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("BlueJ Projects", isDirectory: true)
    }

    public static func sanitizedName(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, trimmed.count <= 60 else { return nil }
        let allowed = CharacterSet.alphanumerics.union(CharacterSet(charactersIn: " -_"))
        guard trimmed.unicodeScalars.allSatisfy({ allowed.contains($0) }) else { return nil }
        guard !trimmed.hasPrefix(".") else { return nil }
        return trimmed
    }

    public func ensureRoot() throws {
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }

    public func projects() throws -> [ProjectSummary] {
        try ensureRoot()
        let contents = try FileManager.default.contentsOfDirectory(
            at: root,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: [.skipsHiddenFiles]
        )
        return contents.compactMap { url -> ProjectSummary? in
            guard (try? url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory) == true else { return nil }
            let name = url.lastPathComponent
            return ProjectSummary(name: name, url: url)
        }
        .sorted { $0.name.localizedStandardCompare($1.name) == .orderedAscending }
    }

    public func projectURL(_ name: String) -> URL {
        root.appendingPathComponent(name, isDirectory: true)
    }

    @discardableResult
    public func createProject(named raw: String) throws -> ProjectSummary {
        guard let name = Self.sanitizedName(raw) else { throw ProjectStoreError.invalidName }
        try ensureRoot()
        let url = projectURL(name)
        guard !FileManager.default.fileExists(atPath: url.path) else {
            throw ProjectStoreError.alreadyExists(name)
        }
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        let metadata = """
        {
          "name": "\(name)",
          "format": 1,
          "created": "\(ISO8601DateFormatter().string(from: Date()))"
        }
        """
        try metadata.write(to: url.appendingPathComponent(Self.metadataFile), atomically: true, encoding: .utf8)
        return ProjectSummary(name: name, url: url)
    }

    public func deleteProject(named name: String) throws {
        let url = projectURL(name)
        guard FileManager.default.fileExists(atPath: url.path) else {
            throw ProjectStoreError.notFound(name)
        }
        try FileManager.default.removeItem(at: url)
    }

    public func sources(in name: String) throws -> [SourceFile] {
        let base = projectURL(name)
        guard FileManager.default.fileExists(atPath: base.path) else {
            throw ProjectStoreError.notFound(name)
        }
        guard let walker = FileManager.default.enumerator(
            at: base,
            includingPropertiesForKeys: [.isRegularFileKey],
            options: [.skipsHiddenFiles]
        ) else { return [] }
        var files: [SourceFile] = []
        for case let url as URL in walker {
            guard url.pathExtension == "java" else { continue }
            let relative = url.path.replacingOccurrences(of: base.path + "/", with: "")
            let text = (try? String(contentsOf: url, encoding: .utf8)) ?? ""
            files.append(SourceFile(path: relative, text: text))
        }
        return files.sorted { $0.path.localizedStandardCompare($1.path) == .orderedAscending }
    }

    public func writeSource(_ text: String, path: String, in name: String) throws {
        let url = try resolve(path, in: name)
        try FileManager.default.createDirectory(
            at: url.deletingLastPathComponent(),
            withIntermediateDirectories: true
        )
        try text.write(to: url, atomically: true, encoding: .utf8)
    }

    public func deleteSource(path: String, in name: String) throws {
        let url = try resolve(path, in: name)
        if FileManager.default.fileExists(atPath: url.path) {
            try FileManager.default.removeItem(at: url)
        }
    }

    private func resolve(_ path: String, in name: String) throws -> URL {
        let normalized = path.replacingOccurrences(of: "\\", with: "/")
        guard !normalized.hasPrefix("/"), !normalized.contains("..") else {
            throw ProjectStoreError.unsafePath(path)
        }
        return projectURL(name).appendingPathComponent(normalized)
    }

    /// Creates the read-only sample project the first time the app runs.
    @discardableResult
    public func seedWelcomeProjectIfNeeded() throws -> [ProjectSummary] {
        let existing = try projects()
        guard !existing.contains(where: { $0.name == StarterProject.name }) else { return existing }
        let project = try createProject(named: StarterProject.name)
        for file in StarterProject.files {
            try writeSource(file.text, path: file.path, in: project.name)
        }
        return try projects()
    }
}

public enum StarterProject {
    public static let name = "Welcome"

    public static let counter = """
    /**
     * A counter that the object bench can drive one increment at a time.
     */
    public class Counter
    {
        private int count;
        private String label;

        public Counter()
        {
            count = 0;
            label = "clicks";
        }

        public Counter(int start)
        {
            count = start;
            label = "clicks";
        }

        public void increment()
        {
            count = count + 1;
        }

        public void incrementBy(int amount)
        {
            count = count + amount;
        }

        public void reset()
        {
            count = 0;
        }

        public int getCount()
        {
            return count;
        }

        public String describe()
        {
            return label + ": " + count;
        }
    }

    """

    public static let greeter = """
    /**
     * Builds greetings, and shows loops and string methods.
     */
    public class Greeter
    {
        private String name;

        public Greeter(String newName)
        {
            name = newName;
        }

        public String greet()
        {
            return "Hello, " + name + "!";
        }

        public String shout()
        {
            return greet().toUpperCase();
        }

        public String countdown(int from)
        {
            String result = "";
            int i = from;
            while (i > 0)
            {
                result = result + i + " ";
                i = i - 1;
            }
            return result + "liftoff";
        }

        public String initials(String first, String last)
        {
            return "" + first.charAt(0) + last.charAt(0);
        }
    }

    """

    public static let calculator = """
    /**
     * Static methods: call these from the class itself, no object needed.
     */
    public class Calculator
    {
        public static int add(int a, int b)
        {
            return a + b;
        }

        public static double average(int a, int b)
        {
            return (a + b) / 2.0;
        }

        public static int sumTo(int limit)
        {
            int total = 0;
            for (int i = 1; i <= limit; i++)
            {
                total = total + i;
            }
            return total;
        }

        public static int safeDivide(int a, int b)
        {
            try
            {
                return a / b;
            }
            catch (ArithmeticException problem)
            {
                return 0;
            }
        }
    }

    """

    public static let files: [(path: String, text: String)] = [
        ("Counter.java", counter),
        ("Greeter.java", greeter),
        ("Calculator.java", calculator)
    ]
}
