// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "BlueJPadKit",
    platforms: [.iOS(.v17), .macOS(.v13)],
    products: [
        .library(name: "BlueJPadKit", targets: ["BlueJPadKit"])
    ],
    targets: [
        .target(name: "BlueJPadKit"),
        .testTarget(name: "BlueJPadKitTests", dependencies: ["BlueJPadKit"])
    ]
)
