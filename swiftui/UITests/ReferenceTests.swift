import XCTest

final class ReferenceTests: XCTestCase {
    let app = XCUIApplication()
    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launchArguments += ["--comparison", "-AppleLanguages", "(zh-Hans)", "-AppleLocale", "zh_CN"]
        if name.contains("ControlledPress") { app.launchArguments.append("--record-press") }
        app.launch()
    }
    func capture(_ label: String) {
        let shot = XCTAttachment(screenshot: app.screenshot())
        shot.name = label
        shot.lifetime = .keepAlways
        add(shot)
        print("FOLD_FRAME", label, Date().timeIntervalSince1970)
    }
    func row(_ name: String) -> XCUIElement { app.buttons["row." + name].firstMatch }

    func testContextLiftAndBorderlessMenu() {
        let entry = row("影像目录")
        XCTAssertTrue(entry.waitForExistence(timeout: 8))
        let point = entry.coordinate(withNormalizedOffset: CGVector(dx: 0.32, dy: 0.5))
        capture("row-rest")
        print("FOLD_PROBE row", Date().timeIntervalSince1970, entry.frame, point.screenPoint)
        point.press(forDuration: 1.15)
        let open = app.buttons["打开"].firstMatch
        XCTAssertTrue(open.waitForExistence(timeout: 4))
        capture("row-active")
        open.tap()
        XCTAssertTrue(app.staticTexts["影像目录"].firstMatch.waitForExistence(timeout: 4) || app.navigationBars["影像目录"].exists)
        app.terminate()
        app.launch()
        let sort = app.buttons["sort"]
        capture("borderless-rest")
        print("FOLD_PROBE sort", Date().timeIntervalSince1970, sort.frame)
        sort.press(forDuration: 0.85)
        XCTAssertTrue(app.buttons["修改时间"].waitForExistence(timeout: 4))
        capture("borderless-active")
        app.buttons["修改时间"].tap()
        XCTAssertTrue(row("影像目录").exists)
    }

    func testAlertPresentations() {
        app.buttons["新建文件夹"].tap()
        let field = app.alerts.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 4))
        capture("alert-input")
        app.alerts.buttons["取消"].tap()
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        let info = app.buttons["查看信息"].firstMatch
        let settingsList = app.collectionViews.firstMatch
        for _ in 0..<5 where !info.isHittable { settingsList.swipeUp() }
        info.tap()
        XCTAssertTrue(app.alerts["折页组件"].waitForExistence(timeout: 4))
        capture("alert-single")
        app.alerts.buttons["完成"].tap()
        for _ in 0..<5 where !app.buttons["全部组件"].isHittable { settingsList.swipeDown() }
        app.buttons["全部组件"].tap()
        app.buttons["family.导航与弹层"].tap()
        for (entry, title, action, label) in [
            ("提示框", "折页参照", "完成", "message"),
            ("双操作提示", "移除样张？", "取消", "pair"),
            ("三操作提示", "保存更改？", "取消", "stack")
        ] {
            app.buttons[entry].tap()
            let alert = app.alerts[title]
            XCTAssertTrue(alert.waitForExistence(timeout: 4))
            print("FOLD_ALERT", label, alert.frame, alert.buttons.allElementsBoundByIndex.map { [$0.label, String(describing: $0.frame)] })
            capture("alert-" + label)
            alert.buttons[action].tap()
        }
    }

    func testShortPressWhenLeavingAFile() {
        let file = row("馆藏索引.tsv")
        XCTAssertTrue(file.waitForExistence(timeout: 12))
        for duration in [0.04, 0.10, 0.34] {
            file.tap()
            let rail = app.buttons["BackButton"].firstMatch
            let back = rail.exists ? rail : app.navigationBars.buttons.element(boundBy: 0)
            XCTAssertTrue(back.isHittable)
            Thread.sleep(forTimeInterval: 0.9) // Let the incoming navigation bar reach its resting size.
            let bounds = back.frame
            print("BACK_PRESS", duration, Date().timeIntervalSince1970, bounds, app.frame)
            back.press(forDuration: duration)
            XCTAssertTrue(file.waitForExistence(timeout: 4))
        }
    }

    func testMenuAndNavigation() {
        XCTAssertTrue(row("印刷样张").waitForExistence(timeout: 12))
        capture("01-home")
        app.buttons["select"].tap()
        app.buttons["select"].tap()
        app.buttons["sort"].tap()
        XCTAssertTrue(app.buttons["修改时间"].waitForExistence(timeout: 4))
        capture("02-popup")
        app.buttons["修改时间"].tap()
        let item = row("印刷样张")
        item.coordinate(withNormalizedOffset: CGVector(dx: 0.24, dy: 0.5)).press(forDuration: 0.9)
        let open = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "打开")).firstMatch
        XCTAssertTrue(open.waitForExistence(timeout: 8))
        capture("03-context")
        open.tap()
        XCTAssertTrue(row("用纸.csv").waitForExistence(timeout: 4))
        row("用纸.csv").tap()
        let railBack = app.buttons["BackButton"].firstMatch
        let back = railBack.exists ? railBack : app.navigationBars.buttons.element(boundBy: 0)
        back.press(forDuration: 0.8)
        let root = app.buttons["本机文库"].firstMatch
        if root.waitForExistence(timeout: 2) {
            capture("04-history")
            root.tap()
        } else {
            // The Duo side rail pops one level on release in this presentation.
            XCTAssertTrue(railBack.exists && row("用纸.csv").exists)
            capture("04-rail-back-release")
            railBack.tap()
        }
        XCTAssertTrue(row("印刷样张").waitForExistence(timeout: 4))
        app.buttons["options"].tap()
        XCTAssertTrue(app.buttons["显示方式"].waitForExistence(timeout: 4))
        capture("05-toolbar")
    }

    func testSearchAndScroll() {
        row("材质校对").tap()
        let list = app.collectionViews.firstMatch
        list.swipeUp()
        capture("06-scroll")
        let field = app.textFields["search"]
        field.tap()
        field.typeText("12")
        XCTAssertTrue(row("样张 12.txt").waitForExistence(timeout: 4))
        capture("07-search")
    }
    func testNativeTabTapAndHold() {
        XCUIDevice.shared.orientation = .portrait
        let bar = app.tabBars.firstMatch
        let inGallery = !bar.waitForExistence(timeout: 2)
        if inGallery {
            // The unfolded workspace uses a sidebar. The catalogue also hosts the compact tab bar.
            app.buttons["options"].tap()
            app.buttons["组件目录"].tap()
            app.buttons["全部组件"].tap()
            app.buttons["family.导航与弹层"].tap()
            app.buttons["标签页"].tap()
        }
        let recent = bar.buttons["最近"].firstMatch
        let pinned = bar.buttons["固定"].firstMatch
        let browse = bar.buttons["浏览"].firstMatch
        guard browse.waitForExistence(timeout: 8) else {
            capture("tab-presentation")
            XCTFail("Browse is unavailable in the tab bar at \(app.frame.size).")
            return
        }
        recent.tap()
        XCTAssertTrue(app.navigationBars["最近打开"].waitForExistence(timeout: 4))
        capture("tab-tap")
        let center = CGVector(dx: 0.5, dy: 0.5)
        let start = recent.coordinate(withNormalizedOffset: center)
        let end = pinned.coordinate(withNormalizedOffset: center)
        start.press(forDuration: 0.45, thenDragTo: end, withVelocity: .slow, thenHoldForDuration: 0.35)
        XCTAssertTrue(app.navigationBars["固定"].waitForExistence(timeout: 4))
        capture("tab-hold-release")
        browse.tap()
        let content = inGallery ? app.staticTexts["tabSample.browse"] : row("材质校对")
        XCTAssertTrue(content.waitForExistence(timeout: 4))
    }

    func testTabMaterialBands() {
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.导航与弹层"].tap()
        app.buttons["标签页"].tap()
        let bands = app.switches["对照色带"]
        XCTAssertTrue(bands.waitForExistence(timeout: 6))
        bands.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
        XCTAssertEqual(bands.value as? String, "1")
        let bar = app.tabBars.firstMatch
        bar.buttons["最近"].tap()
        XCTAssertTrue(app.staticTexts["tabSample.recent"].exists)
        capture("tab-reference-bands")
        let first = bar.buttons["最近"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let last = bar.buttons["浏览"].coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5))
        let between = first.withOffset(CGVector(dx: (last.screenPoint.x - first.screenPoint.x) * 0.25, dy: 0))
        first.press(forDuration: 0.6, thenDragTo: between, withVelocity: .slow, thenHoldForDuration: 1.4)
        bar.buttons["浏览"].press(forDuration: 1.0)
        XCTAssertTrue(app.staticTexts["tabSample.browse"].exists)
        capture("tab-reference-bands-release")
    }

    func testTabHoldAcrossPlainAndBandedBackgrounds() {
        XCUIDevice.shared.orientation = .portrait
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.导航与弹层"].tap()
        app.buttons["标签页"].tap()
        let bands = app.switches["对照色带"]
        XCTAssertTrue(bands.waitForExistence(timeout: 6))
        let bar = app.tabBars.firstMatch
        let left = bar.buttons["最近"]
        let right = bar.buttons["浏览"]
        let middle = CGVector(dx: 0.5, dy: 0.5)
        for enabled in [false, true] {
            if (bands.value as? String == "1") != enabled {
                bands.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
            }
            right.tap()
            XCTAssertTrue(app.staticTexts["tabSample.browse"].exists)
            let from = right.coordinate(withNormalizedOffset: middle)
            let to = left.coordinate(withNormalizedOffset: middle)
            let stop = from.withOffset(CGVector(dx: (to.screenPoint.x - from.screenPoint.x) * 0.76, dy: 0))
            let label = enabled ? "tab-bands" : "tab-plain"
            capture(label + "-rest")
            print("FOLD_HOLD", label, from.screenPoint, stop.screenPoint, Date().timeIntervalSince1970)
            from.press(forDuration: 0.45, thenDragTo: stop, withVelocity: .slow, thenHoldForDuration: 2.6)
            print("FOLD_UP", label, Date().timeIntervalSince1970)
            XCTAssertTrue(app.staticTexts["tabSample.recent"].waitForExistence(timeout: 3))
        }
    }

    func testValueControlsAndReadOnlyProgress() throws {
        XCUIDevice.shared.orientation = .portrait
        func openFamily(_ name: String) {
            app.buttons["options"].tap()
            app.buttons["组件目录"].tap()
            app.buttons["全部组件"].tap()
            app.buttons["family." + name].tap()
        }
        openFamily("选择与数值")
        let toggle = app.switches["显示预览"]
        let rightEnd = toggle.coordinate(withNormalizedOffset: CGVector(dx: 1, dy: 0.5))
        let thumb = rightEnd.withOffset(CGVector(dx: -20, dy: 0))
        let initial = try XCTUnwrap(toggle.value as? String)
        thumb.press(forDuration: 0.3, thenDragTo: thumb.withOffset(CGVector(dx: -2, dy: 0)),
                    withVelocity: .slow, thenHoldForDuration: 0.2)
        print("FOLD_SWITCH", initial, toggle.value ?? "")
        capture("values-switch-release")

        app.terminate()
        app.launch()
        openFamily("状态与进度")
        let slider = app.sliders["调整进度"]
        XCTAssertTrue(slider.waitForExistence(timeout: 4))
        let readout = app.progressIndicators["读取进度"].firstMatch
        XCTAssertTrue(readout.exists)
        func at(_ x: CGFloat) -> XCUICoordinate {
            slider.coordinate(withNormalizedOffset: CGVector(dx: x, dy: 0.5))
        }
        func logValue(_ label: String) {
            print("FOLD_VALUE", label, slider.value ?? "", readout.value ?? "", slider.frame, readout.frame)
            capture("values-" + label)
        }
        logValue("start")
        at(0.18).press(forDuration: 0.25, thenDragTo: at(0.36), withVelocity: .slow, thenHoldForDuration: 0.5)
        logValue("track-drag")
        at(0.80).tap()
        logValue("track-tap")
        slider.adjust(toNormalizedSliderPosition: 1)
        logValue("maximum")
        let maximum = try XCTUnwrap(readout.value as? String)
        slider.adjust(toNormalizedSliderPosition: 0)
        logValue("minimum")
        XCTAssertNotEqual(try XCTUnwrap(readout.value as? String), maximum)
    }

    func testLongNativeMenu() throws {
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.导航与弹层"].tap()
        let trigger = app.buttons["长菜单"].firstMatch
        trigger.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(
            dx: min(30, trigger.frame.width / 2), dy: trigger.frame.height / 2)).tap()
        capture("long-native-root")
        func menuItem(_ label: String) -> XCUIElement {
            app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", label)).firstMatch
        }
        let branch = menuItem("往期样张")
        XCTAssertTrue(branch.waitForExistence(timeout: 5))
        branch.tap()
        let first = menuItem("往期 01")
        XCTAssertTrue(first.waitForExistence(timeout: 5))
        capture("long-native-menu")
        let last = menuItem("往期 72")
        let x = first.frame.midX
        // Retain the window by index: its first row leaves the accessibility tree during the scroll.
        let menuWindow = try XCTUnwrap(app.windows.allElementsBoundByIndex.first {
            $0.buttons["往期 01"].exists
        })
        let origin = menuWindow.coordinate(withNormalizedOffset: .zero)
        let localX = x - menuWindow.frame.minX
        let from = origin.withOffset(CGVector(dx: localX, dy: menuWindow.frame.height * 0.78))
        let to = origin.withOffset(CGVector(dx: localX, dy: menuWindow.frame.height * 0.28))
        for _ in 0..<20 where !last.isHittable {
            from.press(forDuration: 0.03, thenDragTo: to, withVelocity: .fast, thenHoldForDuration: 0)
        }
        XCTAssertTrue(last.isHittable)
        last.tap()
        XCTAssertTrue(app.staticTexts["往期 72"].waitForExistence(timeout: 4))
        capture("long-native-menu-selection")
    }
    func testMaterialMenuOverColorEdges() throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("此样张使用原生玻璃菜单") }
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.玻璃材质"].tap()
        let menu = app.buttons["material-menu"].firstMatch
        XCTAssertTrue(menu.waitForExistence(timeout: 5))
        if !menu.isHittable { app.swipeUp() }
        capture("material-button")
        // In a Form the accessibility button can span the full sample row.
        // The glass capsule is centered horizontally at the top of that row.
        menu.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.21)).tap()
        let grid = app.descendants(matching: .any).matching(NSPredicate(format: "label == %@", "网格")).firstMatch
        XCTAssertTrue(grid.waitForExistence(timeout: 5))
        capture("material-open-menu")
        grid.tap()
        XCTAssertTrue(menu.isHittable)
    }
    func testCapsulePairActionsAndSpacing() throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("此样张使用原生玻璃容器") }
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.按压与回弹"].tap()
        for name in ["短胶囊", "长胶囊"] {
            app.segmentedControls.buttons[name].tap()
            let sample = app.buttons["probe." + name]
            XCTAssertTrue(sample.isHittable)
            sample.press(forDuration: 0.35)
        }
        app.segmentedControls.buttons["相邻"].tap()
        let a = app.buttons["probe.选择"]
        let b = app.buttons["probe.操作"]
        let gap = app.sliders["间距"]
        func dragGap(from fraction: CGFloat, to destination: CGFloat) {
            let from = gap.coordinate(withNormalizedOffset: CGVector(dx: fraction, dy: 0.5))
            let to = gap.coordinate(withNormalizedOffset: CGVector(dx: destination, dy: 0.5))
            from.press(forDuration: 0.15, thenDragTo: to)
        }
        func shownGap() throws -> Int {
            let label = app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", "间距 ")).firstMatch.label
            return try XCTUnwrap(Int(label.split(separator: " ").last.map(String.init) ?? ""))
        }
        dragGap(from: 0.40, to: 0.01)
        let near = try shownGap()
        let nearCenters = b.frame.midX - a.frame.midX
        a.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "选择 ·")).firstMatch.exists)
        b.tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH %@", "操作 ·")).firstMatch.exists)
        dragGap(from: 0.04, to: 0.99)
        let far = try shownGap()
        XCTAssertGreaterThan(far - near, 16)
        XCTAssertEqual((b.frame.midX - a.frame.midX) - nearCenters, CGFloat(far - near), accuracy: 2)
        let delta = b.frame.midY - a.frame.midY
        let stagger = app.switches["错开"]
        stagger.coordinate(withNormalizedOffset: CGVector(dx: 0.90, dy: 0.5)).tap()
        let displaced = NSPredicate { _, _ in
            (stagger.value as? String) == "1" && b.frame.midY - a.frame.midY > delta + 5
        }
        let changed = XCTNSPredicateExpectation(predicate: displaced, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [changed], timeout: 4), .completed)
    }

    func testToolbarReplacementExamples() throws {
        XCUIDevice.shared.orientation = .portrait
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.按钮与链接"].tap()
        app.buttons["工具栏过渡"].tap()
        let actions = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", "切换样例 "))
        for (name, startCount, endCount) in [("圆钮换页", 1, 1), ("双钮到头像", 2, 1), ("双钮到三钮", 2, 3)] {
            app.segmentedControls.buttons[name].tap()
            XCTAssertEqual(actions.count, startCount)
            capture("toolbar-\(name)-start")
            app.buttons["切换工具栏"].tap()
            XCTAssertTrue(app.buttons["返回起始状态"].waitForExistence(timeout: 5))
            XCTAssertEqual(actions.count, endCount)
            // The system can expose the glyph as its AX frame. Compare the glass in the captures.
            capture("toolbar-\(name)-end")
            XCTAssertTrue(actions.firstMatch.isHittable)
            actions.firstMatch.tap()
            XCTAssertTrue(app.buttons["切换工具栏"].waitForExistence(timeout: 5))
        }
    }

    func testControlledPressAndRelease() throws {
        try runControlledPress(roles: ["button", "menu"], distances: [24, 72])
    }

    func testControlledPressLongDistances() throws {
        try runControlledPress(roles: ["button"], distances: [144, 240])
    }

    func testControlledPressLongMenuPulls() throws {
        try runControlledPress(roles: ["menu"], distances: [144, 240], downwardOnly: true)
    }

    private func runControlledPress(roles: [String], distances: [CGFloat], downwardOnly: Bool = false) throws {
        guard #available(iOS 26.0, *) else { throw XCTSkip("需要原生玻璃控件") }
        app.buttons["options"].tap()
        app.buttons["组件目录"].tap()
        app.buttons["全部组件"].tap()
        app.buttons["family.按压与回弹"].tap()
        print("PROBE_SCREEN", app.frame.width, app.frame.height)
        let directions: [(String, CGFloat, CGFloat)] = [
            ("down", 0, 1), ("diagonal", -sqrt(0.5), sqrt(0.5)), ("left", -1, 0)
        ]
        let middle = app.buttons["项目 3"].firstMatch
        for role in roles {
            if role == "menu" { app.buttons["菜单"].firstMatch.tap() }
            let source = app.buttons["probe." + role].firstMatch
            for distance in distances {
                for (direction, ux, uy) in directions where !downwardOnly || direction == "down" {
                    for trial in 1...2 {
                        if role == "menu" && !middle.exists {
                            source.tap()
                            XCTAssertTrue(middle.waitForExistence(timeout: 4))
                        }
                        let target = role == "menu" ? app.buttons["项目 5"].firstMatch : source
                        let frame = target.frame
                        XCTAssertGreaterThan(frame.width, 0)
                        // The lower leading rim lets a pull leave the action rows on release.
                        let unit = role == "menu" ? CGVector(dx: 14 / frame.width, dy: 1 - 7 / frame.height)
                                                  : CGVector(dx: 0.5, dy: 0.5)
                        let start = target.coordinate(withNormalizedOffset: unit)
                        let end = start.withOffset(CGVector(dx: distance * ux, dy: distance * uy))
                        let key = "\(role)-\(Int(distance))-\(direction)-\(trial)"
                        print("PROBE_BEGIN", key, Date().timeIntervalSince1970,
                              frame.minX + unit.dx * frame.width, frame.minY + unit.dy * frame.height,
                              frame.width, frame.height)
                        let contact = app.descendants(matching: .any)["probe.contact"].firstMatch
                        let before = role == "button" ? contact.value as? String : nil
                        let velocity = XCUIGestureVelocity(rawValue: distance / 0.30)
                        start.press(forDuration: 0.12, thenDragTo: end,
                                    withVelocity: velocity, thenHoldForDuration: 0.22)
                        print("PROBE_RETURN", key, Date().timeIntervalSince1970)
                        Thread.sleep(forTimeInterval: 0.78)
                        let present = role == "menu" ? middle.exists : source.exists
                        print("PROBE_END", key, Date().timeIntervalSince1970, present ? "visible" : "dismissed")
                        XCTAssertTrue(present, "松手后应能看到样张控件：\(key)")
                        if role == "button" {
                            XCTAssertNotNil(before)
                            XCTAssertNotEqual(before, contact.value as? String, "触点必须抵达内屏样张")
                        }
                    }
                }
            }
        }
    }

    func testWideSidebarAndDetail() throws {
        try XCTSkipUnless(UIDevice.current.userInterfaceIdiom == .pad, "需要 regular 宽度")
        let device = XCUIDevice.shared
        let original = device.orientation
        defer { device.orientation = original == .unknown ? .portrait : original }
        device.orientation = .landscapeLeft
        XCTAssertTrue(row("路线手记").waitForExistence(timeout: 12))
        let recent = app.buttons["最近"].firstMatch
        if !recent.isHittable {
            app.coordinate(withNormalizedOffset: CGVector(dx: 0.035, dy: 0.055)).tap()
        }
        XCTAssertTrue(recent.waitForExistence(timeout: 8))
        XCTAssertTrue(row("印刷样张").isHittable)
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "wide-sidebar-and-detail"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

}
