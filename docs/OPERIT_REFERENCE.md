# KM 开发说明与上游参考

KM 的 Agent 能力参考了 [AAswordman/Operit](https://github.com/AAswordman/Operit) 的公开架构和功能说明，尤其是：

- 分区化系统提示词及运行时工具清单生成；
- 内部工具调用协议与 Provider 原生 Function Calling 之间的适配；
- Prompt 输入、系统提示词、工具提示词和最终请求准备等阶段化扩展点；
- Android Accessibility Service 作为屏幕读取与界面动作通道。

Operit 仓库标注 GNU LGPL v3（LGPL-3.0-only）。KM 当前实现为独立编写的简化实现，没有直接复制 Operit 源文件或原文提示词。后续如移植 Operit 代码、依赖或资源，应单独检查对应目录许可证、保留版权与许可声明，并遵循适用的 LGPL 义务。非法律意见。
