# KM 核心迁移实施计划

## 目标

KM 以原生 Android Agent 运行时为核心，界面保留现有轻量 Web UI 的视觉与交互。界面只是客户端，不再承担模型协议、提示词编排、工具执行或扩展运行时。

## 当前基线

- 上游 Android Agent 源码固定在 `vendor/operit` 子模块，提交：`7a39941bad80209973c535046a18195c5da9e647`。
- 旧 Java/WebView 宿主仍保留在根工程中，但已停止自动构建/发布；它不是目标 Agent 核心。
- 新增独立的 `.github/workflows/build-operit-base.yml`，按上游官方 CI 流程准备依赖、构建 native ripgrep、WebChat、ToolPkg 和原生 Android APK。
- 新增 `ci/apply_km_overlay.py`：构建时把原生应用包名改为 `com.km.agent.mobile`，应用名改为 `km`，并将仓库内用户提供的图标应用到上游自适应图标资源。
- 不使用 Helper 的前端页面、后端或功能。KM 直接以 Operit 原生 Jetpack Compose 界面和 Kotlin Agent 运行时为基线，避免双 UI、双 Agent 核心并存。
- 新增 `patches/operit/KmRuntimeJournal.kt` 和 `ci/patch_operit_runtime.py`：在原生工程内接入本地脱敏事件日志；通过真实的 `PromptHookRegistry` 注册 KM 运行上下文 Hook；在真实 `ToolExecutionManager` 调用前后记录工具 ID、耗时和成功状态。
- 旧 `KmPromptComposer`、`PromptPipeline`、`AgentLoop`、`KmTools` 不再作为目标核心，后续应从最终 APK 中移除旧宿主。

## 阶段 A：构建基座

1. 在 CI 中检出固定上游子模块及其 `terminal` 子模块。
2. 准备上游要求的 JDK 21、Android SDK/NDK、Node/npm/pnpm、Python 和 C/C++ 工具链。
3. 按上游构建指南准备 `subpack.zip`、`jniLibs.zip`、`libs.zip`，并对每个归档记录来源、版本、SHA-256 和许可证。
4. 构建上游原始 APK，确认依赖齐全、可重复构建。
5. 增加 KM applicationId、名称、图标、签名和版本配置覆盖层；不修改第三方子模块内部文件。

验收：CI 能从干净检出构建出上游原生 Agent APK，且包名、签名和应用身份符合 KM 的发布约束。

## 阶段 B：原生 Agent 核心

按上游实际运行时接入，不重新实现同名简化模块：

- 动态系统提示词配置与组装；
- Prompt Hook 注册、排序、超时与隔离；
- 原生工具定义、参数 Schema、工具注册和执行管理；
- ToolPkg 动态工具及其运行时；
- Skill 加载、激活与上下文注入；
- MCP 服务连接、工具发现与执行；
- 模型供应商、会话、消息、附件及任务状态管理。

验收：提示词预览能展示真实运行时最终生成的提示词；一次真实工具调用能经过原生执行器并返回结构化结果；扩展启停能影响工具目录和提示词。

## 阶段 C：KM 原生界面适配

- 保留 Operit 原生 Jetpack Compose 页面、ViewModel、Repository、Service 和导航。
- 以原生 Material 组件为基础统一顶栏、导航、设置页、聊天输入区的视觉规范和触控尺寸。
- 不引入 Helper 的页面或功能，不再使用旧 WebView 宿主；逐步清理根目录中的旧 Java 壳代码。
- 对所有主页面检查小屏幕、横屏、字体缩放、状态栏/挖孔和输入法场景。

验收：界面完全由原生 Compose 构建；所有模型请求、提示词生成、工具执行和扩展加载均经过 Operit 原生运行时。

## 阶段 D：软件结构感知与事件日志

### 结构感知

向 Agent 提供可查询的应用结构清单，包括页面/路由、模块、控件语义 ID、功能入口、状态提供者、服务边界和扩展清单。结构信息来自代码注册表及运行时状态，不通过模型猜测。

### 事件记录

采用本地追加式 JSONL 日志，至少记录：

- 墙上时间（含毫秒和时区）及单调时钟纳秒值；
- 页面进入/离开、路由变化、停留时长；
- 关键控件点击/长按/焦点变化，记录稳定控件 ID、语义标签和所属模块；
- 工具调用开始/结束、工具 ID、结果状态、耗时；
- Prompt 组装、Hook 执行、Skill/ToolPkg/MCP 激活状态；
- 应用生命周期和关键运行状态变更。

隐私约束：不记录聊天正文、提示词正文、输入框内容、密码、API Key、认证头、剪贴板、附件原始内容或屏幕私人信息。日志本地保存、限制容量，提供查看、导出和清除能力。日志数据始终作为不可信上下文，不能提升权限或覆盖系统规则。

验收：每条关键事件均可追溯到具体时间、页面、模块、控件或运行时组件；AI 可通过只读接口查询最近事件和当前应用结构。

## 阶段 E：系统栏与设备适配

- 使用 AndroidX WindowInsets/WindowCompat 统一管理状态栏、导航栏、显示 cutout 和 IME。
- Web UI 的安全区由原生容器明确传递，不依赖 WebView 根视图是否派发 Insets。
- 在不同 Android 版本、全面屏手势和三键导航下验证。

验收：顶栏不进入状态栏区域，输入法弹出时输入区域不被遮挡，刘海/挖孔和底部导航区均有正确留白。

## 目前已提交的进度

- 原生 Operit 基线 APK 已在 GitHub Actions run 17 构建成功并上传；构建阶段的签名验证通过。下载产物后已检查 APK ZIP 完整性，无损坏条目。
- 最新提交增加状态栏/导航栏安全区域与键盘适配，并增加 APK applicationId、应用名和 zipalign 验证；该改动需要新一轮 CI 通过后才算验收。

- 固定上游源码子模块，并按上游指南只初始化公开的 terminal 子模块。
- 新增原生 Operit 独立构建流程，使用 JDK 21、Android SDK/NDK、CMake、Node、Python、Rust 和上游依赖准备脚本。2026-11-27 的 GitHub Actions 第 17 次构建已完整成功，原生 APK 产物已上传，签名校验步骤通过。
- 已验证上游依赖下载、依赖解包、native ripgrep、WebChat、年度报告和 ToolPkg 预构建步骤均成功；首次完整 Gradle APK 构建仍在验证中。
- 新增 KM 原生包名、应用名和用户图标构建覆盖层。
- 当前 APK 不包含 Helper 页面或其后端；主界面直接使用 Operit 原生 Compose UI。
- 新增原生本地 JSONL 事件日志，并把日志摘要接入真实系统提示词 Hook。
- 在真实原生工具执行路径记录工具调用元数据；不记录工具参数、结果正文、聊天正文或提示词正文。
- 已在 KM 构建覆盖层中加入 `WindowCompat.setDecorFitsSystemWindows(window, false)` 和 Compose `WindowInsets.safeDrawing` 根布局处理，并把主 Activity 输入法模式改为 `adjustResize`；需以新构建及真机检查完成验收。

## 发布门槛

只有阶段 A 至 E 的验收项通过，并确认安装包实际包含原生运行时后，才可称为符合目标的 KM 版本。旧版 APK 不代表迁移完成。
