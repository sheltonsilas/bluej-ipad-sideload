import Foundation

/// Where the app finds the Java core. The environment variable wins (CI and UI tests), then
/// the value saved from the Settings sheet, then loopback for the simulator.
public struct CoreSettings {
    public static let defaultsKey = "BlueJCoreURL"
    public static let fallbackURL = URL(string: "http://127.0.0.1:8899")!
    public static let environmentKey = "BLUEJ_CORE_URL"

    private let defaults: UserDefaults
    private let environment: [String: String]

    public init(
        defaults: UserDefaults = .standard,
        environment: [String: String] = ProcessInfo.processInfo.environment
    ) {
        self.defaults = defaults
        self.environment = environment
    }

    public var coreURL: URL {
        if let value = environment[Self.environmentKey], let url = Self.normalized(value) {
            return url
        }
        if let value = defaults.string(forKey: Self.defaultsKey), let url = Self.normalized(value) {
            return url
        }
        return Self.fallbackURL
    }

    public var isEnvironmentLocked: Bool {
        environment[Self.environmentKey] != nil
    }

    public func save(_ text: String) -> Bool {
        guard let url = Self.normalized(text) else { return false }
        defaults.set(url.absoluteString, forKey: Self.defaultsKey)
        return true
    }

    public static func normalized(_ text: String) -> URL? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        let withScheme = trimmed.contains("://") ? trimmed : "http://" + trimmed
        guard let url = URL(string: withScheme), let host = url.host, !host.isEmpty else { return nil }
        return url
    }
}
