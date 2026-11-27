# km — Personal Android Agent

KM 是个人使用的 Android AI Agent。项目基于独立实现的原生 Android 代码，参考了 Operit 的 Agent 分层设计，不是 Operit 官方软件，也不包含 Operit 原样源码。

## 当前版本（0.1.0）

- OpenAI Chat Completions 兼容 API：自定义 endpoint、模型名称和 API Key
- 分区式系统提示词：身份、执行循环、工具协议、设备上下文、权限规则、可用工具、用户自定义提示
- 原生 Function Calling 与 XML 工具协议两种模式
- 工具执行循环：模型调用工具 → 用户授权 → 执行 → 工具结果回填 → 模型继续
- Android 无障碍服务：读取当前界面控件树；点击、长按、滑动、文字点击、输入、返回和回到桌面
- 启动已安装应用
- km 私有工作区：列出、读取、写入和删除文本文件
- 最近聊天记录保存在本机
- 修改屏幕或写入/删除文件的工具默认逐次询问用户

## 首次使用

1. 安装 APK。
2. 打开「模型设置」，填写 API 地址、模型名称和 API Key。
3. 如果要使用手机自动化，点击「启用手机操作」，在 Android 无障碍设置里手动打开 km 服务。
4. 回到 km，输入任务。每次会改变设备状态的操作都需要你确认。

API 地址可以填完整的 `/v1/chat/completions`，也可以填服务根地址或 `/v1`。优先使用 HTTPS。若配置本地局域网 HTTP 模型服务，请仅在可信网络中使用。

## 当前边界

这是第一阶段核心版本，不等同于 Operit 全量复刻。当前尚未实现完整浏览器 Agent、截图视觉模型、Shizuku/Root 通道、终端/PRoot、MCP、ToolPkg/Skill 市场、工作流编辑器、长期图谱记忆、语音和本地模型推理。后续可在此构建基础上逐步添加。

API Key 保存在应用私有偏好设置中，Android 备份关闭；不要把设备 Root 后的本地数据提取风险视为零。无障碍权限非常敏感，KM 不会在没有用户确认的情况下自动点击或输入。

## 自动构建 APK

每次向 `main` 分支提交代码后，GitHub Actions 自动构建。进入仓库 **Actions** 页面，打开最新成功记录，在 Artifacts 中下载 `km-debug`，解压后安装 `app-debug.apk`。

## Reference

- [Operit Android Agent](https://github.com/AAswordman/Operit) — 参考其系统提示词模块化、内部 XML 工具协议/对外原生 Tool Calling 适配、Prompt Hook 分阶段处理，以及 Android 无障碍自动化思路。
- Operit 仓库主代码标注为 LGPL-3.0-only；本仓库当前 KM 核心模块为独立编写，未直接复制 Operit 源码。今后若引入或修改上游代码，需要逐文件核对许可证并遵守 LGPL-3.0 要求。
