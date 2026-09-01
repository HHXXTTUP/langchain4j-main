# LangChain4j 图片生成入门 Demo

这是一个独立的 Java 17 学习项目：接收提示词，使用 LangChain4j 的 `OpenAiImageModel` 生成图片。
它同时提供 Spring Boot 网页和命令行两种入口，可以在浏览器中展示结果，也可以把图片保存到本地。

它没有加入 LangChain4j 根项目的 Maven 模块列表，避免个人练习代码影响上游项目构建。

## 代码阅读顺序

1. `ImageGenerationApplication`：Spring Boot 网页入口。
2. `ImageGenerationController`：接收 `POST /api/images` 请求。
3. `ImageGenerationService`：校验提示词并调用 `ImageModel`。
4. `ImageModelFactory`：根据环境变量创建 `OpenAiImageModel`。
5. `ImageGenerationResult`：把 Base64 或 URL 转成浏览器可以展示的 `imageSrc`。
6. `ImageGenerationCli`、`GeneratedImageWriter`：命令行生成并保存图片。

## 1. 安装当前源码依赖

第一次运行时，在 LangChain4j 仓库根目录执行：

```powershell
mvn -pl langchain4j-open-ai -am -DskipTests "-Djacoco.skip=true" "-Dmaven.javadoc.skip=true" install
```

`-am` 会同时构建 `langchain4j-open-ai` 依赖的 `langchain4j-core` 和 HTTP Client 等模块。

## 2. 运行离线测试

```powershell
cd learning-examples\image-generation-demo
mvn test
```

这些测试不会调用真实图片接口，也不会产生费用。

## 3. 配置 API Key

只在当前 PowerShell 会话中设置：

```powershell
$env:OPENAI_API_KEY="你的-api-key"
```

不要把 API Key 写进 Java 源码、`pom.xml` 或提交到 Git。

可选配置：

```powershell
$env:OPENAI_IMAGE_MODEL="gpt-image-1"
$env:OPENAI_IMAGE_SIZE="1024x1024"
$env:OPENAI_IMAGE_QUALITY="medium"
$env:OPENAI_IMAGE_OUTPUT_FORMAT="png"
$env:OPENAI_TIMEOUT_SECONDS="120"
$env:IMAGE_OUTPUT_DIR="output"
```

如果使用实现了 OpenAI Images API 的兼容服务，还可以配置：

```powershell
$env:OPENAI_BASE_URL="https://你的服务地址/v1"
```

## 4. 启动网页

```powershell
mvn spring-boot:run
```

浏览器打开 [http://localhost:8080](http://localhost:8080)，输入提示词并点击“生成图片”。

页面向后端发送：

```http
POST /api/images
Content-Type: application/json

{"prompt":"一只正在编写 Java 的机器人"}
```

后端返回 `imageSrc` 和模型可能提供的 `revisedPrompt`。API Key 只存在于后端环境变量，
不会被放进 HTML 或发送到浏览器。

## 5. 使用命令行生成图片

```powershell
mvn exec:java "-Dexec.args=一只戴眼镜的橘猫正在用 Java 开发 AI 助手，扁平插画风格"
```

不传参数时，程序会在控制台中等待输入提示词：

```powershell
mvn exec:java
```

图片默认保存到当前项目的 `output` 目录。真实图片生成接口通常按次计费。

## 调用链

```text
提示词
  -> ImageGenerationController / ImageGenerationCli
  -> ImageModel 接口
  -> OpenAiImageModel
  -> Images API
  -> Image（Base64 或 URL）
  -> 网页展示 / GeneratedImageWriter 保存到本地
```
