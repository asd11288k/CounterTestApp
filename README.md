# km — Personal Android Agent

KM 是个人使用的 Android AI Agent。采用独立编写的原生 Android 实现，参考 Operit 的 Agent 分层思想，不是 Operit 官方软件，也不包含 Operit 原样源码。

## 当前版本 0.3.0

### 移动端 UI 与适配
- 深色主题、品牌启动图标和统一对话视觉
- 自动避让 Android 状态栏、刘海/挖孔和底部导航栏
- 软键盘弹出时重新计算可用空间，输入框保持可见
- 快捷操作区域横向滑动，消息气泡按屏幕宽度自适应
- 原生深色弹窗、设置面板和可滚动长表单

### 角色卡
- 内置 KM 助理、技术顾问、写作搭档、角色伙伴
- 自定义角色名称、头像符号、角色定位、开场语和系统设定
- 新建、编辑、删除自定义角色卡
- 每个聊天会话独立记住角色卡选择
- 当前角色设定每次请求动态注入系统提示词

### 插件中心
- 内置手机自动化、私有工作区、任务规划、写作整理插件
- 插件启用/停用、详情查看、自定义插件卸载
- 从手机文件选择器导入 .kmplugin / JSON 插件清单
- 插件可注入专属系统提示词，并只公开其声明的工具
- 模型收到的工具 schema 与 XML 工具目录均按启用状态动态过滤
- 插件安装默认停用，启用前可查看插件说明和权限范围

**安全模型：** 当前版本采用声明式插件包。插件只能调用 KM 已实现、经过权限保护的工具，不会动态加载任意 APK、DEX 或脚本。导入包不能自行取得新的 Android 权限，也不能绕过操作确认。

### 动态系统提示词
每轮模型请求重新拼装：
1. KM 核心身份与任务执行规则
2. 当前会话所选角色卡
3. 当前工具调用协议（原生 Function Calling / XML）
4. 实时 Android 版本、屏幕 dp 尺寸、语言区域、时区和无障碍权限状态
5. 已启用插件的系统提示词
6. 权限、安全和不可信数据边界
7. 由插件启用状态决定的当前工具目录
8. 用户自定义补充提示词

### Agent 能力
- OpenAI Chat Completions 兼容 API：自定义地址、模型和 API Key
- 原生 Function Calling 与 XML 工具协议
- 工具调用循环、单轮工具上限和用户逐次确认
- Android 无障碍：读取可见控件树、点击、长按、滑动、按文本点击、输入、返回和主页
- 启动已安装应用
- KM 私有工作区：列出、读取、写入和删除 UTF-8 文件
- 多会话、会话角色绑定、自动标题、对话导出和停止请求
- 模型连接测试、系统提示词预览和工具执行记录

## 首次使用
1. 安装 km 0.3。
2. 打开顶部 MODEL 卡片，重新填写 API 地址、模型名称和 API Key。
3. 点击「测试连接」。
4. 如需手机自动化，打开设备状态卡片，在 Android 无障碍设置中启用 km。
5. 从对话标题右侧选择角色卡；从「更多」进入插件中心。

API 地址可填写完整 /v1/chat/completions、服务根地址或 /v1。建议优先使用 HTTPS。API Key 保存在应用私有偏好设置中，Android 备份关闭；这不是硬件级密钥保险箱。

## 插件包格式

插件是 UTF-8 JSON 文件，扩展名建议为 .kmplugin 或 .json：

    {
      "apiVersion": 1,
      "id": "example.myplugin",
      "name": "我的插件",
      "version": "1.0.0",
      "description": "插件简介",
      "systemPrompt": "注入到系统提示词中的规则",
      "tools": ["get_screen", "read_file"]
    }

tools 只允许 KM 当前实现的工具：get_screen、tap、long_press、swipe、click_text、set_text、press_back、press_home、open_app、list_workspace、read_file、write_file、delete_file。未知工具会在导入阶段拒绝。导入的插件默认停用。

## 签名与安装说明

0.2.0 使用旧的临时调试签名。它的私钥没有保存在构建环境中，因此无法给已安装的 0.2.0 直接签出兼容更新。0.3.0 改用新的应用 ID com.km.agent.mobile，可以与旧版并存，不要求卸载旧版。后续 0.3.x 起将使用构建缓存中的固定签名身份；工作流在签名缓存丢失时会失败关闭，而不是静默生成新签名。工作流每天刷新缓存访问，并且不会把 keystore 放入公开源码仓库。

0.3.0 需要重新配置一次模型 API。两个不同 Android 包之间无法直接读取对方的私有应用数据。

## 当前边界

KM 0.3.0 是一个持续扩展的个人 Agent，不等同于 Operit 全量复刻。当前未实现任意第三方代码插件、MCP 服务市场、完整浏览器 Agent、截图视觉模型、Shizuku/Root 通道、终端/PRoot、长期记忆数据库、可视化工作流、语音和本地模型推理。

## 自动构建
向 main 分支提交后，GitHub Actions 自动构建并签名。进入仓库 Actions 页面，打开最新成功记录，在 Artifacts 中下载 km-0.3.0-signed，解压即可安装。

## Reference
- Operit Android Agent — https://github.com/AAswordman/Operit
- Operit 仓库主代码标注为 LGPL-3.0-only；本仓库 KM 核心模块为独立编写，未直接复制其源文件。今后若移植上游代码或资源，需要逐文件核对许可证并履行适用义务。
