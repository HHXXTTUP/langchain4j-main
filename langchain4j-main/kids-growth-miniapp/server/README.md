# 儿童英语学习服务

独立 Spring Boot 服务，负责 GLM 儿童英语课程、`edge-tts` 中英文语音合成和 MP3 音频流。`edge-tts` 免费且不需要 Azure Speech Key；GLM Key 只从服务端环境变量读取。

微信云托管部署请参阅 [CLOUD_DEPLOYMENT.md](CLOUD_DEPLOYMENT.md)。项目已包含多阶段 `Dockerfile`，云端镜像会自动安装 Java 17、Python 3 和 `edge-tts`。

## 安装 edge-tts

Windows PowerShell：

```powershell
cd kids-growth-miniapp/server
./setup-edge-tts.ps1
```

如果系统没有 `python` 命令，可以把 Python 可执行文件传给脚本：

```powershell
./setup-edge-tts.ps1 -Python "C:\path\to\python.exe"
```

脚本会创建隔离环境 `server/.venv-edge-tts`。Spring Boot 默认自动使用这个环境，不需要配置 Azure Key。

## 环境变量

必需配置：

```powershell
$env:ZHIPU_API_KEY="在智谱开放平台创建的 Key"
```

可选配置：

```powershell
$env:CHILD_AI_MODEL="glm-4.6v-flash"
$env:EDGE_TTS_PYTHON="C:\path\to\python.exe"
$env:EDGE_TTS_ENGLISH_VOICE="en-US-AnaNeural"
$env:EDGE_TTS_CHINESE_VOICE="zh-CN-XiaoyiNeural"
$env:EDGE_TTS_RATE="-8%"
$env:EDGE_TTS_PITCH="+4Hz"
$env:SERVER_PORT="8090"
```

`en-US-AnaNeural` 用作英文儿童声线，`zh-CN-XiaoyiNeural` 用作活泼的年轻中文女声。

如果 GLM 返回限流（例如智谱错误码 `1305`），服务会在首次调用后最多重试 10 次，每次间隔 500ms。仍然失败时接口返回“服务开小差了~”，小程序会显示对应弹框。

## 启动

在 `server` 目录执行：

```powershell
mvn spring-boot:run
```

就绪检查：

```text
GET http://127.0.0.1:8090/api/learning/readiness
```

响应中的 `ttsReady` 表示 `edge-tts` 是否可用，`sttReady` 表示语音识别是否可用。当前版本只替换了文本转语音，`sttReady` 固定为 `false`。

## 接口

- `POST /api/tasks/learning/sessions`：提交异步课程任务。
- `POST /api/tasks/learning/sessions/{id}/questions/next`：提交异步问题任务。
- `POST /api/tasks/learning/sessions/{id}/praise`：提交异步鼓励语音任务。
- `POST /api/tasks/animation/scenes`：提交异步动画场景任务。
- `GET /api/tasks/{taskId}`：查询任务状态与结果。
- `POST /api/learning/sessions`：生成英文课程与第一轮语音。
- `GET /api/learning/sessions/{id}/audio/{key}`：返回 `audio/mpeg`。
- `POST /api/learning/sessions/{id}/questions/next`：生成下一道互动题语音。
- `POST /api/learning/sessions/{id}/praise`：生成鼓励语音。
- `POST /api/learning/sessions/{id}/attempts`：为后续 STT 服务保留，当前不可用。
- `POST /api/animation/scenes`：让 GLM 生成单词，再返回小程序本地动画模板描述。

学习会话和生成的 MP3 保存在服务内存中，一小时后清理，服务重启后消失。`edge-tts` 生成时使用的临时 MP3 会在读入内存后立即删除。

`edge-tts` 是非官方接口，没有可用性承诺，适合当前开发和验证阶段。正式商业上线应准备一个官方 TTS 服务作为可切换的备用提供方。
