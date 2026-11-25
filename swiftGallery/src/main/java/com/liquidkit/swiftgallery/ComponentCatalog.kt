package com.liquidkit.swiftgallery

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import io.liquidkit.LiquidKitSymbol

enum class ComponentCategory(
    val title: String,
    val englishSubtitle: String,
    val symbol: LiquidKitSymbol,
    val color: Color,
) {
    Ios27AndGlass("iOS 27 与 Liquid Glass", "iOS 27 & Liquid Glass", LiquidKitSymbol.Sparkles, Color(0xff00c7be)),
    ButtonsAndControls("按钮与交互控件", "Buttons & Controls", LiquidKitSymbol.HandTap, Color(0xff007aff)),
    TextAndInput("文本、排版与输入", "Text & Input", LiquidKitSymbol.TextCursor, Color(0xff5856d6)),
    PickersAndSliders("选择器与数值调节", "Pickers & Sliders", LiquidKitSymbol.SliderHorizontal, Color(0xffaf52de)),
    IndicatorsAndStatus("指示器、进度与状态", "Indicators & Status", LiquidKitSymbol.Gauge, Color(0xff30b0c7)),
    LayoutContainers("布局与容器", "Layout & Containers", LiquidKitSymbol.Grid, Color(0xffff9500)),
    ListsAndCollections("列表与集合", "Lists & Collections", LiquidKitSymbol.ListBulletRectangle, Color(0xff34c759)),
    NavigationAndPresentation("导航与弹窗呈现", "Navigation & Presentation", LiquidKitSymbol.SquareOnSquare, Color(0xffff2d55)),
    GraphicsAndMedia("图形、材质与动画", "Graphics, Materials & Motion", LiquidKitSymbol.Palette, Color(0xff00c7be)),
    ChartsVisualization("数据图表 Charts", "Swift Charts Visualization", LiquidKitSymbol.Chart, Color(0xff5856d6)),
    GesturesAndInteraction("手势、拖拽与触感反馈", "Gestures, Drag & Haptics", LiquidKitSymbol.HandDraw, Color(0xffff9500)),
    AdvancedLayoutAndNav("高级布局与富文本", "Advanced Layout & Rich Text", LiquidKitSymbol.Sidebar, Color(0xff007aff)),
    StylingAndEffects("色彩与自定义样式", "Colors, Transforms & Custom Styles", LiquidKitSymbol.Sparkles, Color(0xffaf52de)),
    SystemFrameworksUI("WebView 与地图", "WebKit, MapKit, TipKit & Timeline", LiquidKitSymbol.Globe, Color(0xff30b0c7)),
}

enum class ComponentRoute {
    // 1. iOS 27 & Liquid Glass
    LiquidGlassEffects, GlassButtons, Ios27NewFeatures,
    // 2. Buttons & Controls
    Buttons, MenusAndLinks, Toggles,
    // 3. Text & Input
    TextAndLabel, TextField, TextEditor,
    // 4. Pickers & Sliders
    Picker, DateAndColorPicker, SliderAndStepper,
    // 5. Indicators & Status
    ProgressView, Gauge, StatusEmptyStates,
    // 6. Layout & Containers
    StacksAndGrid, ScrollView, ContainersGrouping,
    // 7. Lists & Collections
    ListStyles, ListInteractions, OutlineAndTable,
    // 8. Navigation & Presentation
    NavigationAndToolbar, SheetsPopovers, AlertsDialogs,
    // 9. Graphics, Materials & Animation
    ShapesGradients, SymbolsImages, MaterialsEffects, AnimationsTransitions,
    // 10. Swift Charts
    BarLineAreaCharts, PieDonutCharts, MathPlotsCharts,
    // 11. Gestures & Haptics
    BasicGestures, ComposedGestures, HapticsFeedback,
    // 12. Advanced Layout & Rich Text
    RichTextEditor, CustomLayoutFlow, GeometryVisualEffects, TabViewAndSplitView,
    // 13. Styling, Transforms & Search
    ColorsAndHierarchy, TransformsAndMasks, CustomStylesShowcase, SearchAndFocus,
    // 14. System Frameworks & Real-time
    NativeWebView, MapKitSwiftUI, TipKitAndMedia, TimelineAndPaths,
}

@Immutable
data class ComponentItem(
    val route: ComponentRoute,
    val title: String,
    val englishName: String,
    val summary: String,
    val category: ComponentCategory,
    val symbol: LiquidKitSymbol,
    val tags: List<String>,
    val badge: String? = null,
)

object ComponentCatalog {
    val allItems: List<ComponentItem> = listOf(
        // 1. iOS 27 & Liquid Glass
        ComponentItem(
            ComponentRoute.LiquidGlassEffects,
            "Liquid Glass 玻璃效果",
            ".glassEffect & GlassEffectContainer",
            "演示 regular/clear/identity、tint 着色、interactive 交互反馈及容器融合动画。",
            ComponentCategory.Ios27AndGlass,
            LiquidKitSymbol.Drop,
            listOf("glassEffect", "Glass", "GlassEffectContainer", "glassEffectID", "Liquid Glass"),
            "Liquid Glass",
        ),
        ComponentItem(
            ComponentRoute.GlassButtons,
            "Glass 按钮样式",
            ".buttonStyle(.glass / .glassProminent)",
            "展示原生玻璃按钮、高亮玻璃按钮、着色变体、圆形图标按钮与不同控件尺寸。",
            ComponentCategory.Ios27AndGlass,
            LiquidKitSymbol.Sparkles,
            listOf("glass", "glassProminent", "ButtonStyle", "controlSize"),
            "Liquid Glass",
        ),
        ComponentItem(
            ComponentRoute.Ios27NewFeatures,
            "iOS 27 新特性集合",
            "TabsPickerStyle / Reorderable / CrossFade",
            "集中展示 iOS 27 的 .tabs 选择器、reorderContainer 拖拽重排、crossFade 转场、SliderTick 与 safeAreaBar。",
            ComponentCategory.Ios27AndGlass,
            LiquidKitSymbol.SquareOnSquare,
            listOf("iOS 27", "tabs", "reorderable", "reorderContainer", "crossFade", "SliderTick", "safeAreaBar"),
            "iOS 27",
        ),

        // 2. Buttons & Controls
        ComponentItem(
            ComponentRoute.Buttons,
            "Button 按钮与样式",
            "Button / ButtonStyle / ButtonRole",
            "涵盖 automatic、plain、bordered、borderedProminent、glass 等全部样式及 Role/Shape/Size 变体。",
            ComponentCategory.ButtonsAndControls,
            LiquidKitSymbol.HandTap,
            listOf("Button", "ButtonStyle", "ButtonRole", "destructive", "buttonBorderShape", "controlSize"),
        ),
        ComponentItem(
            ComponentRoute.MenusAndLinks,
            "Menu、Link 与 ControlGroup",
            "Menu / Link / ShareLink / ControlGroup",
            "包含下拉菜单、主操作触发菜单、子菜单嵌套、菜单内 Picker、外部链接、系统分享与分组按钮。",
            ComponentCategory.ButtonsAndControls,
            LiquidKitSymbol.More,
            listOf("Menu", "Link", "ShareLink", "ControlGroup", "palette"),
        ),
        ComponentItem(
            ComponentRoute.Toggles,
            "Toggle 开关与样式",
            "Toggle / ToggleStyle",
            "包含 switch 与 button 两种样式、自定义 Tint 着色、多状态标签及批量状态联动。",
            ComponentCategory.ButtonsAndControls,
            LiquidKitSymbol.SliderHorizontal,
            listOf("Toggle", "switch", "button", "tint"),
        ),

        // 3. Text & Input
        ComponentItem(
            ComponentRoute.TextAndLabel,
            "Text 与 Label 排版",
            "Text / Font / AttributedString / Label",
            "Dynamic Type 字体层级、字重与字体设计、Markdown 渲染、数值过渡动画及 Label 多种显示模式。",
            ComponentCategory.TextAndInput,
            LiquidKitSymbol.TextCursor,
            listOf("Text", "Label", "Font", "Markdown", "AttributedString"),
        ),
        ComponentItem(
            ComponentRoute.TextField,
            "TextField 与 SecureField",
            "TextField / SecureField / FocusState",
            "文本输入框样式、密码可见切换、多行输入、焦点管理与提交按钮样式。",
            ComponentCategory.TextAndInput,
            LiquidKitSymbol.Rename,
            listOf("TextField", "SecureField", "FocusState", "roundedBorder", "plain"),
        ),
        ComponentItem(
            ComponentRoute.TextEditor,
            "TextEditor 多行文本",
            "TextEditor / scrollContentBackground",
            "长文本编辑、占位符覆盖、字数统计、隐藏默认背景并自定义玻璃或卡片样式。",
            ComponentCategory.TextAndInput,
            LiquidKitSymbol.Document,
            listOf("TextEditor", "scrollContentBackground"),
        ),

        // 4. Pickers & Sliders
        ComponentItem(
            ComponentRoute.Picker,
            "Picker 选择器全样式",
            "Picker / PickerStyle",
            "对比 menu、segmented、wheel、inline、palette 以及 iOS 27 .tabs 全部样式。",
            ComponentCategory.PickersAndSliders,
            LiquidKitSymbol.CheckCircle,
            listOf("Picker", "menu", "segmented", "wheel", "inline", "palette", "tabs"),
            "iOS 27",
        ),
        ComponentItem(
            ComponentRoute.DateAndColorPicker,
            "DatePicker 与 ColorPicker",
            "DatePicker / MultiDatePicker / ColorPicker",
            "日期选择器（compact/graphical/wheel）、多日期日历选择与支持透明度的原生取色器。",
            ComponentCategory.PickersAndSliders,
            LiquidKitSymbol.Calendar,
            listOf("DatePicker", "MultiDatePicker", "ColorPicker", "graphical", "wheel", "compact"),
        ),
        ComponentItem(
            ComponentRoute.SliderAndStepper,
            "Slider 与 Stepper",
            "Slider / SliderTick / Stepper",
            "连续/步进滑块、带 SliderTick 刻度与 neutralValue 中性点的 iOS 26+ 滑块及增减步进器。",
            ComponentCategory.PickersAndSliders,
            LiquidKitSymbol.SliderHorizontal,
            listOf("Slider", "SliderTick", "Stepper", "neutralValue"),
            "iOS 26+",
        ),

        // 5. Indicators & Status
        ComponentItem(
            ComponentRoute.ProgressView,
            "ProgressView 进度条",
            "ProgressView / ProgressViewStyle",
            "不确定旋转加载、线性确定进度条、可交互进度联动与自定义环形进度条样式。",
            ComponentCategory.IndicatorsAndStatus,
            LiquidKitSymbol.Reload,
            listOf("ProgressView", "linear", "circular", "ProgressViewStyle"),
        ),
        ComponentItem(
            ComponentRoute.Gauge,
            "Gauge 仪表盘",
            "Gauge / GaugeStyle",
            "展示 linearCapacity、accessoryLinear、accessoryCircular 与 accessoryCircularCapacity 渐变仪表。",
            ComponentCategory.IndicatorsAndStatus,
            LiquidKitSymbol.Gauge,
            listOf("Gauge", "linearCapacity", "accessoryCircular", "accessoryCircularCapacity"),
        ),
        ComponentItem(
            ComponentRoute.StatusEmptyStates,
            "空状态、占位与徽标",
            "ContentUnavailableView / redacted / badge",
            "系统级空状态/搜索无结果视图、骨架屏占位渲染（redacted）及列表行角标 badge。",
            ComponentCategory.IndicatorsAndStatus,
            LiquidKitSymbol.Folder,
            listOf("ContentUnavailableView", "redacted", "placeholder", "badge"),
        ),

        // 6. Layout & Containers
        ComponentItem(
            ComponentRoute.StacksAndGrid,
            "Stack、Grid 与响应式布局",
            "VStack / HStack / Grid / LazyVGrid / ViewThatFits",
            "堆叠对齐与间距实时调节、二维 Grid、自适应网格与响应式布局切换。",
            ComponentCategory.LayoutContainers,
            LiquidKitSymbol.Grid,
            listOf("VStack", "HStack", "ZStack", "Grid", "LazyVGrid", "ViewThatFits"),
        ),
        ComponentItem(
            ComponentRoute.ScrollView,
            "ScrollView 滚动与分页",
            "ScrollView / scrollTargetBehavior / scrollTransition",
            "水平分页轮播、视图对齐吸附、滚动缩放过渡动画与编程式跳转。",
            ComponentCategory.LayoutContainers,
            LiquidKitSymbol.List,
            listOf("ScrollView", "paging", "scrollTransition", "ScrollViewReader"),
        ),
        ComponentItem(
            ComponentRoute.ContainersGrouping,
            "GroupBox、Form 与容器",
            "GroupBox / DisclosureGroup / Form / LabeledContent",
            "分组卡片容器、可折叠树形组、结构化表单、标签值行组件及分割线。",
            ComponentCategory.LayoutContainers,
            LiquidKitSymbol.MenuCard,
            listOf("GroupBox", "DisclosureGroup", "Form", "LabeledContent"),
        ),

        // 7. Lists & Collections
        ComponentItem(
            ComponentRoute.ListStyles,
            "List 样式与 Section",
            "List / ListStyle / Section",
            "实时切换 insetGrouped、plain、cards 列表样式及可折叠分组。",
            ComponentCategory.ListsAndCollections,
            LiquidKitSymbol.List,
            listOf("List", "insetGrouped", "plain", "cards", "Section"),
        ),
        ComponentItem(
            ComponentRoute.ListInteractions,
            "List 交互、滑动与多选",
            "swipeActions / refreshable / EditButton",
            "左右滑动操作、删除/置顶、编辑模式多选与列表行定制。",
            ComponentCategory.ListsAndCollections,
            LiquidKitSymbol.HandDraw,
            listOf("swipeActions", "refreshable", "onDelete", "selection"),
        ),
        ComponentItem(
            ComponentRoute.OutlineAndTable,
            "OutlineGroup 与 Table",
            "OutlineGroup / List(children:) / Table",
            "树形层级目录展开与支持列排序的多列数据表格 Table。",
            ComponentCategory.ListsAndCollections,
            LiquidKitSymbol.ListBulletRectangle,
            listOf("OutlineGroup", "Table", "TableColumn", "SortComparator"),
        ),

        // 8. Navigation & Presentation
        ComponentItem(
            ComponentRoute.NavigationAndToolbar,
            "NavigationStack 与 Toolbar",
            "NavigationStack / ToolbarItem / VisibilityPriority",
            "编程式路径导航、工具栏液态玻璃替换、crossFade 与 zoom 转场。",
            ComponentCategory.NavigationAndPresentation,
            LiquidKitSymbol.Forward,
            listOf("NavigationStack", "ToolbarItem", "crossFade", "zoom"),
            "iOS 27",
        ),
        ComponentItem(
            ComponentRoute.SheetsPopovers,
            "Sheet、Popover 与 Inspector",
            "sheet / presentationDetents / popover / inspector",
            "多高度半屏抽屉、拖动条控制、气泡浮层及自适应侧边 Inspector。",
            ComponentCategory.NavigationAndPresentation,
            LiquidKitSymbol.SquareOnSquare,
            listOf("sheet", "presentationDetents", "popover", "inspector"),
        ),
        ComponentItem(
            ComponentRoute.AlertsDialogs,
            "Alert、Confirmation 与菜单",
            "alert / confirmationDialog / contextMenu",
            "普通与破坏性警告框、带输入框弹窗、底部操作确认列表与长按上下文菜单。",
            ComponentCategory.NavigationAndPresentation,
            LiquidKitSymbol.Bell,
            listOf("alert", "confirmationDialog", "contextMenu"),
        ),

        // 9. Graphics, Materials & Animation
        ComponentItem(
            ComponentRoute.ShapesGradients,
            "Shape、Gradient 与 Canvas",
            "Shape / MeshGradient / Canvas",
            "内置几何形状与不对称圆角、线性/径向渐变、多点 MeshGradient 与高性能 Canvas 绘图。",
            ComponentCategory.GraphicsAndMedia,
            LiquidKitSymbol.SquareOnCircle,
            listOf("Shape", "LinearGradient", "MeshGradient", "Canvas"),
        ),
        ComponentItem(
            ComponentRoute.SymbolsImages,
            "SF Symbols、图片与相册",
            "Image / symbolEffect / AsyncImage",
            "LiquidSymbol 矢量图标集、脉冲/弹跳/旋转动态效果与多尺寸预览。",
            ComponentCategory.GraphicsAndMedia,
            LiquidKitSymbol.Camera,
            listOf("SF Symbols", "symbolEffect", "LiquidSymbol"),
        ),
        ComponentItem(
            ComponentRoute.MaterialsEffects,
            "Material 材质与视觉特效",
            "Material / glassEffect / blur / blendMode",
            "系统多档液态玻璃透明度调节与 Regular / Clear / Identity 材质实时对比。",
            ComponentCategory.GraphicsAndMedia,
            LiquidKitSymbol.CircleHalfFilled,
            listOf("Material", "glassEffect", "blur", "opacity"),
        ),
        ComponentItem(
            ComponentRoute.AnimationsTransitions,
            "动画与过渡转场",
            "Spring / PhaseAnimator / Transition",
            "SwiftUI 弹簧物理预设（snappy / smooth / bouncy）、多阶段循环动画与视图过渡转场。",
            ComponentCategory.GraphicsAndMedia,
            LiquidKitSymbol.Sparkles,
            listOf("Spring", "Animation", "PhaseAnimator", "Transition"),
        ),

        // 10. Swift Charts
        ComponentItem(
            ComponentRoute.BarLineAreaCharts,
            "柱状图、折线图与面积图",
            "BarMark / LineMark / AreaMark / RuleMark",
            "支持分组柱状图/横向条形图切换、平滑曲线、渐变面积、目标参考线及手指划选提示。",
            ComponentCategory.ChartsVisualization,
            LiquidKitSymbol.Chart,
            listOf("Charts", "BarMark", "LineMark", "AreaMark", "RuleMark"),
        ),
        ComponentItem(
            ComponentRoute.PieDonutCharts,
            "饼图、环形图与中心指标",
            "SectorMark / chartBackground",
            "实现饼图与甜甜圈环形图动态切换，支持内径比例、扇区间隙与中心数据插槽。",
            ComponentCategory.ChartsVisualization,
            LiquidKitSymbol.CircleHalfFilled,
            listOf("Charts", "SectorMark", "Donut", "Pie"),
        ),
        ComponentItem(
            ComponentRoute.MathPlotsCharts,
            "函数绘图与横向滚动图表",
            "LinePlot / sin & cos / Frequency",
            "实时绘制 sin / cos 数学曲线并可交互调节频率参数。",
            ComponentCategory.ChartsVisualization,
            LiquidKitSymbol.Chart,
            listOf("Charts", "LinePlot", "sin", "cos"),
        ),

        // 11. Gestures & Haptics
        ComponentItem(
            ComponentRoute.BasicGestures,
            "基础手势：点击、拖拽与缩放",
            "Tap / SpatialTap / LongPress / Drag / Magnify",
            "演示单击、坐标落点、长按蓄力、拖拽回弹与缩放旋转。",
            ComponentCategory.GesturesAndInteraction,
            LiquidKitSymbol.HandTap,
            listOf("TapGesture", "SpatialTap", "DragGesture", "Magnify"),
        ),
        ComponentItem(
            ComponentRoute.ComposedGestures,
            "组合手势与原生拖放",
            "simultaneous / sequenced / draggable / dropDestination",
            "演示组合手势与跨容器拖拽投递状态传输。",
            ComponentCategory.GesturesAndInteraction,
            LiquidKitSymbol.HandDraw,
            listOf("draggable", "dropDestination", "composed"),
        ),
        ComponentItem(
            ComponentRoute.HapticsFeedback,
            "SensoryFeedback 触感反馈",
            ".sensoryFeedback(_:trigger:)",
            "接入 Android 原生触觉引擎映射 impact / selection / success / warning / error 触感。",
            ComponentCategory.GesturesAndInteraction,
            LiquidKitSymbol.Bell,
            listOf("sensoryFeedback", "haptics", "impact", "selection"),
            "iOS 26+",
        ),

        // 12. Advanced Layout & Rich Text
        ComponentItem(
            ComponentRoute.RichTextEditor,
            "富文本 AttributedString 编辑器",
            "TextEditor / AttributedString Formatting",
            "实时切换粗体、斜体、下划线与前景色并即时渲染 AnnotatedString 富文本预览。",
            ComponentCategory.AdvancedLayoutAndNav,
            LiquidKitSymbol.TextCursor,
            listOf("AttributedString", "RichText", "TextEditor"),
            "iOS 26+",
        ),
        ComponentItem(
            ComponentRoute.CustomLayoutFlow,
            "自定义 Layout 协议 (流式布局)",
            "Layout / FlowLayout / LiquidFlowLayout",
            "完整实现支持自动换行、动态间距调节与标签增删的流式布局。",
            ComponentCategory.AdvancedLayoutAndNav,
            LiquidKitSymbol.Grid,
            listOf("Layout", "FlowLayout", "LiquidFlowLayout"),
        ),
        ComponentItem(
            ComponentRoute.GeometryVisualEffects,
            "Geometry 与 visualEffect 视差",
            "onGeometryChange / visualEffect / parallax",
            "实时读取尺寸几何坐标与滚动驱动视差卡片缩放旋转效果。",
            ComponentCategory.AdvancedLayoutAndNav,
            LiquidKitSymbol.SquareOnCircle,
            listOf("onGeometryChange", "visualEffect", "parallax"),
        ),
        ComponentItem(
            ComponentRoute.TabViewAndSplitView,
            "TabView 变体与分栏 SplitView",
            "TabView / tabViewBottomAccessory / NavigationSplitView",
            "液态玻璃底栏、独立搜索球、底部附属播放条与两栏响应式 NavigationSplitView。",
            ComponentCategory.AdvancedLayoutAndNav,
            LiquidKitSymbol.Sidebar,
            listOf("TabView", "tabViewBottomAccessory", "NavigationSplitView"),
            "iOS 27",
        ),

        // 13. Styling, Transforms & Search
        ComponentItem(
            ComponentRoute.ColorsAndHierarchy,
            "语义颜色、Color.mix 与层级",
            "Color.mix / HierarchicalShapeStyle / colorScheme",
            "交互式色彩混合 lerp、五级层级透明度及深浅色卡片对比。",
            ComponentCategory.StylingAndEffects,
            LiquidKitSymbol.Palette,
            listOf("Color", "mix", "Hierarchical", "colorScheme"),
        ),
        ComponentItem(
            ComponentRoute.TransformsAndMasks,
            "3D 旋转、遮罩与图像滤镜",
            "rotation3DEffect / graphicsLayer / ColorMatrix",
            "三维透视 cameraDistance 旋转、渐变遮罩与实时亮度/饱和度调节。",
            ComponentCategory.StylingAndEffects,
            LiquidKitSymbol.SquareOnCircle,
            listOf("rotation3DEffect", "mask", "saturation", "brightness"),
        ),
        ComponentItem(
            ComponentRoute.CustomStylesShowcase,
            "自定义样式协议大全",
            "ButtonStyle / ToggleStyle / GroupBoxStyle",
            "演示自定义可复用按压按钮样式、复选开关与主题分组卡片。",
            ComponentCategory.StylingAndEffects,
            LiquidKitSymbol.SliderHorizontal,
            listOf("ButtonStyle", "ToggleStyle", "GroupBoxStyle"),
        ),
        ComponentItem(
            ComponentRoute.SearchAndFocus,
            "Searchable、作用域与焦点管理",
            "searchable / searchScopes / FocusState",
            "液态玻璃搜索栏、Scope 分类胶囊筛选与实时关键词建议补全。",
            ComponentCategory.StylingAndEffects,
            LiquidKitSymbol.Search,
            listOf("searchable", "searchScopes", "FocusState"),
            "iOS 26+",
        ),

        // 14. System Frameworks & Real-time
        ComponentItem(
            ComponentRoute.NativeWebView,
            "原生 WebView 与本地 HTML",
            "WebView / AndroidView(android.webkit.WebView)",
            "内嵌原生 Android WebView，支持本地 HTML 实时注入、加载网页与前进后退导航。",
            ComponentCategory.SystemFrameworksUI,
            LiquidKitSymbol.Globe,
            listOf("WebView", "WebKit", "HTML"),
            "iOS 26+",
        ),
        ComponentItem(
            ComponentRoute.MapKitSwiftUI,
            "地图标注与地理围栏画布",
            "Map / Marker / Annotation / MapCircle",
            "交互式矢量地图画布，支持视角切换、地标 Marker、围栏圆圈与样式切换。",
            ComponentCategory.SystemFrameworksUI,
            LiquidKitSymbol.Map,
            listOf("MapKit", "Map", "Marker", "MapCircle"),
        ),
        ComponentItem(
            ComponentRoute.TipKitAndMedia,
            "TipKit 引导提示与媒体控制",
            "TipView / LiquidTipCard / Media Player",
            "系统级新功能引导卡片（可关闭/重置）及模拟视频播放进度控制条。",
            ComponentCategory.SystemFrameworksUI,
            LiquidKitSymbol.Lightbulb,
            listOf("TipKit", "TipView", "Media"),
        ),
        ComponentItem(
            ComponentRoute.TimelineAndPaths,
            "TimelineView 与自定义 Path",
            "TimelineView / Live Clock / Shape.trim",
            "实时帧驱动模拟指针时钟、倒计时与可动画星形 Path 描边绘制。",
            ComponentCategory.SystemFrameworksUI,
            LiquidKitSymbol.Clock,
            listOf("TimelineView", "Path", "trim", "Clock"),
        ),
    )

    fun items(inCategory: ComponentCategory): List<ComponentItem> =
        allItems.filter { it.category == inCategory }

    fun item(forRoute: ComponentRoute): ComponentItem? =
        allItems.firstOrNull { it.route == forRoute }
}
