# 语芽学习屋 · Taro 微信小程序

这是一个独立项目，不属于根目录的 Maven 聚合工程，也不会修改现有 LangChain4j、`learning-examples` 或 `stock-analyzer` 模块。

## 技术结构

```text
Taro 微信小程序
  -> Spring Boot 学习接口
  -> GLM-4.6V-Flash 生成低龄英语课程和问题
  -> edge-tts 免费生成英文童声、中文女声
  -> Spring Boot 返回 MP3 音频流，小程序播放
```

`edge-tts` 只负责文字转语音，不负责识别孩子的朗读。当前版本使用“我读完啦，给我一颗小星星”按钮完成鼓励流程，录音识别入口会在后续接入 STT 后自动显示。

## 目录

- `src/`：Taro React + TypeScript 源码。
- `dist/`：微信开发者工具加载的编译结果。
- `server/`：独立 Spring Boot 服务。
- 根目录旧版原生小程序文件仅作参考，不参与当前运行。

## 首次安装与构建

```powershell
cd kids-growth-miniapp
npm install --cache .npm-cache
npm run typecheck
npm run build:weapp
```

持续开发：

```powershell
npm run dev:weapp
```

微信开发者工具导入 `kids-growth-miniapp` 目录，`project.config.json` 已将 `miniprogramRoot` 配置为 `dist/`。

## 安装和启动后端

先安装项目隔离的 `edge-tts`：

```powershell
cd kids-growth-miniapp/server
./setup-edge-tts.ps1
```

然后配置 GLM 并启动服务：

```powershell
$env:ZHIPU_API_KEY="你的智谱 Key"
mvn spring-boot:run
```

不再需要 `AZURE_SPEECH_KEY` 或 `AZURE_SPEECH_REGION`。本地默认地址为：

```text
http://127.0.0.1:8090
```

模拟器调试时，在微信开发者工具“详情 -> 本地设置”中启用“不校验合法域名”。真机不能访问电脑自己的 `127.0.0.1`，需要使用电脑局域网 IP 重新构建：

```powershell
$env:TARO_APP_API_BASE_URL="http://192.168.1.100:8090"
npm run build:weapp
```

同时需要允许 Windows 防火墙访问 8090 端口。正式发布必须使用已配置为小程序合法域名的 HTTPS API。

## 当前英语流程

1. 输入中文单词。
2. GLM 生成简单英文、发音提示、短例句和 2 到 3 个问题。
3. `edge-tts` 自动播放英文儿童语音，再播放中文问题语音。
4. 宝宝读出英文后，点击“我读完啦，给我一颗小星星”。
5. 后端使用 `edge-tts` 生成中文鼓励语音并播放。

GLM 临时限流时，后端会自动重试 10 次，每次间隔 500ms；仍不可用时小程序提示“服务开小差了~”。

动画屋当前使用 GLM 生成英文单词，再由小程序本地模板播放苹果、小猫、月亮、太阳、星星、小鸟等轻量动画；未知单词会使用通用闪光模板。后续可将同一接口替换为真正的视频模型。计划数据继续存储在微信本地缓存中。更完整的服务配置见 `server/README.md`。
