# km — Personal Android Agent

KM 是个人使用的 Android AI Agent。采用独立编写的原生 Android 实现，参考 Operit 的 Agent 分层思路，不是 Operit 官方软件，也不包含 Operit 原样源码。

## 当前版本 0.2.0

- 深色 Agent 聊天界面、设备/模型状态卡片和常用任务快捷入口
- 多会话管理：新建、切换、自动命名和最近会话持久化（最多保留 12 个会话，每个会话保存最近 100 条消息）
- 对话文本导出、单会话清空
- 模型连接测试与当前系统提示词预览
- 正在执行任务时可请求停止
- 工具执行记录显示在聊天时间线
- OpenAI Chat Completions 兼容 API：自定义 endpoint、模型名称和 API Key
- 分区式系统提示词：身份、执行循环、工具协议、设备上下文、权限规则、可用工具、用户自定义提示
- 原生 Function Calling 与 XML 工具协议两种模式
- 工具执行循环：模型调用工具 → 用户授权 → 执行 → 工具结果回填 → 模型继续
- Android 无障碍服务：读取当前界面控件树；点击、长按、滑动、文字点击、输入、返回和回到桌面
- 启动已安装应用
- KM 私有工作区：列出、读取、写入和删除文本文件

## 首次使用

1. 安装 APK。
2. 打开「模型设置」，填写 API 地址、模型名称和 API Key。
3. 点击「测试连接」验证服务端配置。
4. 如果要使用手机自动化，点击设备状态卡片，在 Android 无障碍设置里手动启用 km 服务。
5. 返回 KM，输入任务。每次会改变设备状态的操作都需要你确认。

API 地址可以填完整的 `/v1/chat/completions`，也可以填服务根地址或 `/v1`。优先使用 HTTPS。若配置本地局域网 HTTP 模型服务，请仅在可信网络中使用。

## 当前边界

KM 0.2.0 是一个可运行的个人 Agent 基础版本，不等同于 Operit 全量复刻。当前尚未实现完整浏览器 Agent、截图视觉识别、Shizuku/Root 通道、终端/PRoot、MCP、ToolPkg/Skill 插件系统、可视化工作流、长期记忆、语音和本地模型推理。

API Key 保存在应用私有偏好设置中，Android 备份关闭；这不是硬件级密钥保险箱。无障碍权限非常敏感，KM 不会在没有用户确认的情况下执行改变设备状态的动作。

## 自动构建 APK

每次向 `main` 分支提交代码后，GitHub Actions 自动构建。进入仓库 **Actions** 页面，打开最新成功记录，在 Artifacts 中下载 `km-debug`，解压后安装 `app-debug.apk`。

## Reference

- [Operit Android Agent](https://github.com/AAswordman/Operit) — 参考其系统提示词模块化、内部 XML 工具协议/对外原生 Tool Calling 适配、Prompt Hook 分阶段处理，以及 Android 无障碍自动化思路。
- Operit 仓库主代码标注为 LGPL-3.0-only；本仓库 KM 核心模块为独立编写，未直接复制 Operit 源码。今后若移植上游代码或资源，需要逐文件核对许可证并遵守适用义务。
