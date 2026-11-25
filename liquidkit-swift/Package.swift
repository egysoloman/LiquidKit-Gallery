// swift-tools-version: 6.0
import Foundation
import PackageDescription

let skipEnabled = ProcessInfo.processInfo.environment["SKIP_BRIDGE"] == "1"

var packageDependencies: [Package.Dependency] = []
var targetDependencies: [Target.Dependency] = []
var targetPlugins: [Target.PluginUsage] = []

if skipEnabled {
    packageDependencies += [
        .package(url: "https://source.skip.tools/skip.git", from: "1.6.0"),
        .package(url: "https://source.skip.tools/skip-ui.git", from: "1.30.0"),
    ]
    targetDependencies += [
        .product(name: "SkipUI", package: "skip-ui"),
    ]
    targetPlugins += [
        .plugin(name: "skipstone", package: "skip"),
    ]
}

let package = Package(
    name: "LiquidKit",
    defaultLocalization: "zh-Hans",
    platforms: [
        .iOS(.v18),
        .macOS(.v15),
    ],
    products: [
        .library(name: "LiquidKit", targets: ["LiquidKit"]),
        .library(name: "LiquidKitDemo", targets: ["LiquidKitDemo"]),
    ],
    dependencies: packageDependencies,
    targets: [
        .target(
            name: "LiquidKit",
            dependencies: targetDependencies,
            exclude: ["Skip"],
            plugins: targetPlugins
        ),
        .target(
            name: "LiquidKitDemo",
            dependencies: ["LiquidKit"]
        ),
        .testTarget(
            name: "LiquidKitTests",
            dependencies: ["LiquidKit", "LiquidKitDemo"]
        ),
    ]
)
