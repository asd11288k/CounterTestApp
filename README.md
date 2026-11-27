# km — Helper UI + Operit-inspired Android Agent

km 是一个面向个人使用的 Android Agent。新版本保留 helper 的前端页面与启动图标，在 helper 的 WebView 外壳内逐步重建并迁移 Operit 风格的 Agent 运行架构。

## 当前基础迁移版本 1.0.0

- helper 原版 HTML/CSS/JS 前端。
- Android WebView 宿主与仅监听 127.0.0.1 的本地 HTTP 网关。
- 多智能体、多会话、模型渠道、OpenAI/Gemini/Anthropic 协议。
- 图片与文本附件、网页搜索/抓取、文件沙箱和时间工具。
- 技能管理、提示词预览与历史留档。
- PromptPipeline：分层系统提示词、可注册 Hook 阶段；真实请求、提示词预览和提示词历史共用相同分段来源。
- XML 工具调用协议与插件工具白名单。
- PluginRegistry：声明式插件包清单、启停、工具白名单和提示词注入。
- EventJournal：记录关键按钮点击、页面进入/离开、页面停留时长和应用生命周期。时间戳包含毫秒，另记录单调时钟值用于耗时。日志只记录控件元数据，不记录聊天正文、提示词、密钥、输入框值或附件内容。

## 与 Operit 的关系

参考 [Operit](https://github.com/AAswordman/Operit) 的动态系统提示词、Prompt Hook、工具执行、包系统和 Skill 分层设计。当前提交是 KM 的独立实现与迁移基础，没有把 Operit 的源文件或提示词原文直接复制进来。

Operit 上游仓库标注 LGPL-3.0-only。后续若直接移植其代码或资源，将逐文件遵守相应许可证、版权声明和源码提供义务。

## 迁移中，尚未完成

此版本**不是 Operit 全量移植**。完整 ToolPkg 运行时与市场、MCP 本地/远程服务、Operit 格式 Skill 文件夹/ZIP 导入、浏览器 Agent、文件绑定/多模态媒体处理、完整无障碍工具覆盖、Shizuku/Root、终端/PRoot、工作流、长期记忆和角色体系仍需逐步迁移并通过真机测试。未接入能力不会在提示词中宣称为可用。

## 安装包

应用显示名称：km  
Android applicationId：`com.km.agent.mobile`  
版本：1.0.0（versionCode 4）

applicationId 与原 KM 0.3 系列一致，构建工作流沿用稳定签名配置，目标是支持覆盖更新。首次迁移后的数据结构发生变化，旧版专有会话数据并不会自动转换为 helper 的智能体/会话存储。

## 自动构建

向 main 分支提交后，GitHub Actions 自动构建 APK。进入 Actions 页面，打开最新成功记录，在 Artifacts 下载 `km-1.0.0-signed` 并解压安装。

## 参考项目

- helper：https://github.com/asd11288k/helper
- Operit：https://github.com/AAswordman/Operit
