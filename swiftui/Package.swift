// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "FoldGlass",
    platforms: [.iOS(.v18)],
    products: [.library(name: "FoldGlass", targets: ["FoldGlass"])],
    targets: [.target(name: "FoldGlass")]
)
