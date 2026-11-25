import XCTest
import SwiftUI
@testable import LiquidKit
@testable import LiquidKitDemo

final class LiquidKitTests: XCTestCase {
    func testRGBAClampingAndPresets() {
        let clamped = LiquidRGBA(red: 1.5, green: -0.2, blue: 0.5, alpha: 2.0)
        XCTAssertEqual(clamped.red, 1.0, accuracy: 1e-6)
        XCTAssertEqual(clamped.green, 0.0, accuracy: 1e-6)
        XCTAssertEqual(clamped.blue, 0.5, accuracy: 1e-6)
        XCTAssertEqual(clamped.alpha, 1.0, accuracy: 1e-6)
        XCTAssertEqual(LiquidRGBA.foldTeal.red, 0.19, accuracy: 1e-6)
    }

    func testSpringTokensMatchCrossPlatformDefaults() {
        XCTAssertEqual(LiquidSpringToken.snappy.response, 0.25, accuracy: 1e-6)
        XCTAssertEqual(LiquidSpringToken.snappy.dampingFraction, 0.85, accuracy: 1e-6)
        XCTAssertEqual(LiquidSpringToken.smooth.dampingFraction, 1.0, accuracy: 1e-6)
        XCTAssertEqual(LiquidSpringToken.bouncy.dampingFraction, 0.70, accuracy: 1e-6)
        let bounded = LiquidSpringToken(response: 0.0, dampingFraction: 3.0)
        XCTAssertEqual(bounded.response, 0.05, accuracy: 1e-6)
        XCTAssertEqual(bounded.dampingFraction, 1.0, accuracy: 1e-6)
    }

    func testDateValueClampsCalendarComponents() {
        let date = LiquidDateValue(year: 2026, month: 19, day: 0)
        XCTAssertEqual(date.year, 2026)
        XCTAssertEqual(date.month, 12)
        XCTAssertEqual(date.day, 1)

        let valid = LiquidDateValue(year: 2026, month: 11, day: 24)
        let roundTrip = LiquidDateValue.from(valid.foundationDate)
        XCTAssertEqual(roundTrip, valid)
    }

    func testModelsAndButtonStylesCoverFullSwiftUISurface() {
        XCTAssertEqual(LiquidButtonStyleKind.allCases.count, 6)
        XCTAssertEqual(LiquidControlSizeKind.allCases.count, 5)
        XCTAssertEqual(LiquidButtonBorderShapeKind.allCases, [.capsule, .circle, .roundedRectangle])
        XCTAssertEqual(LiquidGlassVariant.allCases, [.regular, .clear, .identity])
        XCTAssertEqual(LiquidTransitionKind.allCases, [.crossFade, .zoom, .slide])
        XCTAssertEqual(LiquidSafeAreaEdgeKind.allCases, [.top, .bottom])

        let tab = LiquidTabItem(id: "browse", title: "浏览", systemImage: "folder.fill")
        XCTAssertEqual(tab.id, "browse")

        let palette = LiquidPaletteOptionItem(id: "teal", label: "青绿", color: .foldTeal)
        XCTAssertEqual(palette.id, "teal")

        let tabOpt = LiquidTabOptionItem(id: "all", label: "全部", systemImage: "square.grid.2x2")
        XCTAssertEqual(tabOpt.label, "全部")

        let entry = LiquidMenuEntry(id: "delete", title: "删除", systemImage: "trash", destructive: true)
        XCTAssertTrue(entry.destructive)
        XCTAssertTrue(entry.enabled)
    }

    @MainActor
    func testDemoViewConstructsCleanly() {
        let demo = LiquidKitDemoView()
        XCTAssertNotNil(demo.body)
    }

    @MainActor
    func testGalleryAndModalWrappersConstructCleanly() {
        let mesh = LiquidMeshGradientView(
            width: 2,
            height: 2,
            colors: [
                .accentBlue,
                .foldTeal,
                LiquidRGBA(red: 0.6, green: 0.3, blue: 0.9),
                LiquidRGBA(red: 1.0, green: 0.5, blue: 0.2),
            ]
        )
        XCTAssertEqual(mesh.width, 2)
        XCTAssertEqual(mesh.height, 2)
        XCTAssertNotNil(mesh.body)

        let outline = LiquidOutlineGroupView(items: [
            LiquidOutlineItem(id: "root", title: "组件库", systemImage: "folder"),
            LiquidOutlineItem(id: "child", title: "Liquid Glass", parentId: "root", systemImage: "sparkles"),
        ])
        XCTAssertEqual(outline.items.count, 2)
        XCTAssertNotNil(outline.body)

        let table = LiquidTableView(headers: ["组件", "状态"], rows: [["Toggle", "完成"], ["Slider", "完成"]])
        XCTAssertEqual(table.rows.count, 2)
        XCTAssertNotNil(table.body)

        let bar = LiquidBarChartView(points: [
            LiquidChartPoint(label: "Q1", value: 42, color: .accentBlue),
            LiquidChartPoint(label: "Q2", value: 68, color: .foldTeal),
        ])
        XCTAssertNotNil(bar.body)

        let line = LiquidLineAreaChartView(labels: ["Mon", "Tue", "Wed"], values: [10, 24, 18], ruleValue: 20)
        XCTAssertNotNil(line.body)

        let pie = LiquidPieDonutChartView(slices: [
            LiquidPieChartSlice(label: "UI", value: 60, color: .accentBlue),
            LiquidPieChartSlice(label: "Shader", value: 40, color: .foldTeal),
        ])
        XCTAssertNotNil(pie.body)

        let math = LiquidMathPlotChartView(frequency: 1.5)
        XCTAssertNotNil(math.body)

        let flow = LiquidFlowLayoutView { Text("A"); Text("B") }
        XCTAssertNotNil(flow.body)

        let redacted = Text("Skeleton").liquidRedacted(active: true)
        XCTAssertNotNil(redacted)

        let accessory = LiquidTabViewBottomAccessoryView { Text("Now Playing") }
        XCTAssertNotNil(accessory.body)
    }
}
