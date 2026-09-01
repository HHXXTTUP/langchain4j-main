# 微信云托管部署说明

本服务通过 `wx.cloud.callContainer` 的微信私有链路接入小程序，不依赖云托管测试域名或微信“服务器合法域名”。耗时的 GLM 和 TTS 操作使用任务提交与轮询，避免超过 `callContainer` 单次 15 秒等待上限；MP3 通过 ArrayBuffer 返回给小程序播放。

## 1. 创建云托管环境

1. 登录 [微信云托管控制台](https://cloud.weixin.qq.com/cloudrun)。
2. 使用小程序管理员微信扫码，选择当前小程序 AppID。
3. 开通云开发和云托管环境，地域选择上海。
4. 记录环境 ID，后续排查日志时会用到。

试用额度和计费规则以控制台实时展示为准。不要在未确认预算提醒的情况下开启高规格或多个常驻实例。

## 2. 创建服务

1. 进入“云托管 -> 服务管理”，选择“新建服务”。
2. 服务名称填写 `kids-growth-api`。
3. 部署方式选择“Dockerfile/本地代码”，不要选择 Java 构建、Maven 构建或仅上传 `pom.xml` 的方式。
4. 选择本项目的 `server` 文件夹作为构建根目录，确保该目录中能直接看到 `Dockerfile`、`pom.xml` 和 `src`。
5. 当前控制台优先使用文件夹上传，不要把 `server` 文件夹再压缩一层上传；`Dockerfile` 必须位于上传目录根部。
6. 服务端口填写 `8080`。
7. 健康检查路径填写 `/health`，请求方式选择 `GET`。

建议首次使用 1 核 CPU、1 GiB 内存。Java、Python 和 edge-tts 同时运行时，512 MiB 容易因内存不足重启。

## 3. 配置环境变量

在服务的“版本配置”或“环境变量”中填写：

| 名称 | 必填 | 建议值 |
| --- | --- | --- |
| `ZHIPU_API_KEY` | 是 | 智谱开放平台 API Key |
| `SERVER_PORT` | 是 | `8080` |
| `EDGE_TTS_PYTHON` | 是 | `python3` |
| `CHILD_AI_MODEL` | 否 | `glm-4.6v-flash` |
| `EDGE_TTS_ENGLISH_VOICE` | 否 | `en-US-AnaNeural` |
| `EDGE_TTS_CHINESE_VOICE` | 否 | `zh-CN-XiaoyiNeural` |
| `EDGE_TTS_RATE` | 否 | `-8%` |
| `EDGE_TTS_PITCH` | 否 | `+4Hz` |

不要配置小程序 AppSecret。当前后端不需要 AppSecret，也绝不能把 GLM Key 写入 Dockerfile、Git 或小程序代码。

## 4. 部署和验证

1. 创建版本并开始构建，等待“构建成功”和“部署成功”。
2. 如果健康检查失败，先确认服务端口为 `8080`，再查看版本日志。
3. 部署验证期间可以暂时开启公网访问，并在浏览器访问 `<测试公网域名>/health`，应返回：

```json
{"service":"kids-growth-learning-server","status":"UP"}
```

4. 访问 `<测试公网域名>/api/learning/readiness`，确认 `aiReady` 和 `ttsReady` 都为 `true`。
5. 小程序真机验证通过后，在“服务设置 -> 网络访问”中关闭公网访问，只保留微信私有链路。

## 5. 配置小程序私有调用

当前小程序生产配置为：

```text
环境 ID：prod-d8g1em4boece7c8ea
服务名称：kids-growth-api
```

环境 ID 和服务名位于 `config/index.ts`，不属于密钥。使用 `callContainer` 时不需要配置 `request`、`downloadFile` 或 `uploadFile` 合法域名。微信小程序基础库最低版本应不低于 `2.23.0`。

## 6. 构建小程序正式版本

先重新部署本目录的后端代码，再在 `kids-growth-miniapp` 目录打开 PowerShell：

```powershell
npm run typecheck
npm run build:weapp
```

构建后确认产物包含云托管环境和服务名：

```powershell
rg "prod-d8g1em4boece7c8ea|kids-growth-api|callContainer" dist
```

然后在微信开发者工具中点击“编译”和“预览”，使用真机测试全部流程。

需要连接本机 `8090` 调试时，在构建前临时设置：

```powershell
$env:TARO_APP_USE_CLOUD_CONTAINER="false"
npm run dev:weapp
```

## 7. 上线前设置

- 最大实例数必须暂时设置为 `1`。异步任务、学习会话和 MP3 仍保存在单个容器内存中，多实例会导致轮询请求找不到任务。
- 调试阶段最小实例数可以设为 `0`，送审期间建议设为 `1`，避免冷启动导致审核超时。
- 设置费用预算和告警，试用额度不是永久免费的。
- 当前学习会话和 MP3 在容器内存中保存，实例重启后会消失；这不影响单次课程，但不适合长期学习记录。
- `edge-tts` 是非官方服务，正式商业上线前应准备可切换的官方 TTS 服务。
