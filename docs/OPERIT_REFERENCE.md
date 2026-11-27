# KM 开发说明与上游参考

KM 的 Android Agent 核心以固定提交的 Operit 原生 Kotlin 工程为基础，源码作为 Git 子模块保存在 `vendor/operit`。KM 只在构建阶段应用产品身份、图标和本地扩展补丁，不直接改写上游子模块历史。

上游固定基线：`7a39941bad80209973c535046a18195c5da9e647`。

上游关键架构包括：

- `SystemPromptConfig` 与 `SystemToolPrompts`：动态系统提示词和工具提示词生成；
- `PromptHookRegistry`：提示词输入、历史、系统提示词、工具提示词和 finalize 等阶段的 Hook；
- `ToolExecutionManager`：原生工具权限、并行/串行执行及结果聚合；
- `SkillManager`、`ToolPkgManager` 和 MCP runtime：扩展加载与执行。

KM 的构建覆盖层位于 `ci/apply_km_overlay.py`，原生运行时补丁位于 `ci/patch_operit_runtime.py` 和 `patches/operit/`。覆盖层不应复制或重写 Operit 的 Agent 核心。

Operit 仓库标注 GNU LGPL v3（LGPL-3.0-only）。KM 若分发包含上游代码的 APK，必须随分发物提供适用的许可证、版权声明和对应源代码/修改说明，并遵守 LGPL 对修改及链接形式的要求。非法律意见。
