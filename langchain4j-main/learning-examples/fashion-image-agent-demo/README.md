# Fashion Image Agent Demo

## Standalone ComfyUI video generation

The `Video Generation` menu submits a prompt and 1-9 reference images to the
AutoDL ComfyUI workflow. Configure the secret in the application process:

```text
COMFYUI_VIDEO_TOKEN=<AutoDL workflow token>
COMFYUI_VIDEO_WORKFLOW_ID=minimax_h3_lightx2v_v5
```

Generated videos and uploaded inputs are stored below
`generated/comfyui-video/<job-id>/`; the history page reads its snapshots from
the embedded H2 database. The token is never sent to the browser or written into job snapshots.

这是一个面向 Java 开发者的 AI 应用学习项目。用户输入人物描述后，LangChain4j 使用 GLM 扩写提示词，RunningHub 人物应用生成一张人物底图；系统从本地 H2 服装资料中按语义选择本地服装参考图，使用 GLM 理解服装和头饰，再从本地换装经验库执行 RAG 检索，最后调用 RunningHub 换装应用。人物图和换装图都会由多模态模型质检，必要时生成纠正提示词并受控重试；通过质检的任务还会提取可复用经验并实时更新 RAG。

项目重点不是把所有类都命名为 Agent，而是学习怎样把大模型放在它真正擅长的语义判断环节，同时让 Java 代码控制成本、副作用和流程边界。

## 核心架构

```text
用户人物描述
  -> PortraitPromptEnhancer
     -> LangChain4j 将简短描述扩写为 PortraitPromptSpec
     -> 补充成年女性样貌、身材姿态、环境、光线、构图和风格
     -> 强制合并可配置的人物固定约束，避免模型扩写时遗漏业务要求
  -> Agent1BeautyCreator
     -> 使用扩写后的提示词调用 RunningHub 人物生成应用
     -> 805/RHAuditException 内容审核失败时交回 LangChain4j 执行安全重写并有界重试
     -> 下载并保存 portrait-attempt-1.*
     -> 居中裁切并高质量缩放为 1080x1920 的标准竖版人物底图
  -> PortraitQualityInspector
     -> LangChain4j 检查图片正常性、人体结构和提示词一致性
     -> 未通过时生成纠正提示词并受控重新生成人物图
     -> 通过后归档为 original.*
  -> Agent2ClothingPicker
     -> 从本地 H2 读取由 GLM 生成的服装结构化资料
     -> 本地 BGE 根据人物扩写提示词执行 Top-K 语义选衣
     -> 尚未生成资料时明确记录原因并随机回退
  -> FashionReferenceAnalyzer
     ->RAG 检索分析服装图
     -> FashionReferenceSpec 结构化结果
     -> 固定身份、姿态和背景保护前缀 + AI 从头到脚的图2穿搭分析
  -> FashionKnowledgeRetriever
     -> 将用户描述和 FashionReferenceSpec 组合成检索 Query
     -> BGE-small-zh 本地 Embedding + InMemoryEmbeddingStore 相似度检索
     -> 召回身份、发饰、材质、背景或失败修复经验并注入换装提示词
  -> Agent3OutfitStylist
     -> 人物图和服装图各上传一次
     -> RunningHub 换装生成 outfit-attempt-1.*
  -> OutfitQualityInspector
     -> 对比人物原图、服装参考图、换装结果图
     -> OutfitQualityReport 结构化质检报告
  -> Java 质量门与重试策略
     -> 仅当 AI 已质检、未通过、可修复、未超次数时重试
     -> 选择质量最好的候选图，而不是固定选择最后一张
  -> Agent4ResultPresenter
     -> 归档最终 outfit.* 并返回页面
  -> FashionExperienceLearningService
     -> 仅接纳已执行并通过视觉质检的成功任务
     -> GLM 提取可复用规则，写入本地 H2 并实时加入当前 RAG 索引
```

## AI 与普通代码的边界

| 环节 | 实现方式 | 原因 |
| --- | --- | --- |
| 图片内容理解 | LangChain4j AI Service + 多模态模型 | 服装、材质、头饰属于非结构化视觉语义 |
| 简短人物描述扩写 | LangChain4j AI Service + 结构化输出 | 环境、光线、构图和人物细节需要语义补全 |
| 人物图片生成 | RunningHub AI 应用 | 文生图属于生成工作流能力，GLM-4.6V-Flash 本身只理解图片 |
| 人物底图检查 | 多模态模型 + Java 质量门 | 同时验证图片正常性和提示词一致性 |
| 结构化输出 | Java record + JSON 模式 | 避免业务代码解析不稳定的自然语言 |
| 换装经验检索 | LangChain4j 文档切分 + 本地 Embedding Store | 只注入与当前服装有关的项目经验，并保留来源和相似度 |
| 服装语义选择 | GLM 目录资料 + BGE Embedding | 把人物描述与服装风格、场合、颜色和版型建立可解释关联 |
| 成功经验学习 | GLM 结构化提取 + H2 + 实时 Embedding | 只从通过质检的任务学习，避免失败结果污染知识库 |
| 图片上传、下载、归档 | 普通 Java Service | 操作确定、可测试，不需要 LLM |
| 最大尝试次数 | Java 配置与循环 | 外部生成有费用，不能让模型无限自主调用 |
| 是否通过 | Java 质量门 | 模型负责评分，业务规则负责决策 |
| 最终候选选择 | Java 确定性比较器 | 防止后一次生成比前一次更差却被采用 |
| API 状态与页面展示 | Spring Boot + 原生 Web | 与 AI 推理分离，便于观察整个过程 |

这里暂时不使用 Supervisor Agent。换装 API 有费用和外部副作用，确定性工作流更容易限制次数、复现问题和控制成本。等流程稳定后，再把“是否调用其他工具”之类的低风险决策逐步交给 Agentic 能力。

这里仍不使用对话 Memory，因为当前任务彼此独立。RAG 与 Memory 解决的问题不同：RAG 查询的是可维护、可追溯的换装知识；Memory 保存的是某个用户或会话的历史偏好。服装参考图仍由多模态模型直接分析，RAG 不替代视觉理解，只给视觉结果补充项目经验。

## LangChain4j 学习点

### 1. AI Service

`ModelPortraitAgent` 和 `ModelFashionVisionAgent` 是两个声明式 AI Service 接口。`@SystemMessage` 固定角色和判断规则，`@UserMessage` 传入任务说明及图片。`AiServices.builder(...)` 在运行时生成实现。两个接口共享同一个 `ChatModel`，但拥有独立的领域职责和结构化输出。

这和调用普通 REST API 的区别是：调用方使用的是具有业务语义的 Java 接口，而不是手工拼接智谱请求 JSON。

### 2. 多模态消息

项目使用 `ImageContent` 把图片交给支持视觉的 Chat Model：

- 服装分析输入一张服装参考图。
- 人物质检输入一张人物候选图，并结合扩写后的生成规格判断。
- 质量检查按固定顺序输入人物原图、服装图、结果图。
- 图片会缩放到最长边不超过 `max-image-dimension`，转为 JPEG 后 Base64 编码。

缩放不会提升模型能力，但可以降低视觉 Token、上传体积、超时概率和调用成本。

### 3. 结构化输出

模型不直接返回一段自由文本，而是返回：

- `PortraitPromptSpec`：原始描述、样貌、身材姿态、环境、光线、构图、风格和最终生成提示词。
- `PortraitQualityReport`：技术可用性、综合分、提示词一致性、人体结构、画质、具体问题和纠正提示词。
- `FashionReferenceSpec`：服装、颜色、材质、头饰、身体配饰、必须迁移元素、换装提示词。
- `OutfitQualityReport`：综合、服装、头饰、身份分数，差异、遗漏元素、是否可重试和纠正提示词。
- `ClothingCatalogAnalysis`：服装名称、风格、场合、季节、颜色、材质、版型、适配特征和检索关键词。
- `FashionExperienceDraft`：成功策略、适用场景、可复用规则、风险与关键词。

换装提示词采用“固定前缀 + AI 视觉分析补充”的模板。固定前缀负责要求图1人物长相、姿态、动作和背景不变，并要求迁移图2的发型、发色和全部配饰；GLM 只补充图2从头到脚可见的颜色、材质、纹样、层次和佩戴位置。每次质检后的纠正重试也会重新把固定前缀放在最前面，避免身份与背景保护要求被长提示词截断。

GLM 使用智谱支持的 `json_object` 模式保证响应是 JSON。由于智谱兼容接口当前不是严格 JSON Schema 模式，LangChain4j 会根据 Java record 自动把字段、类型和说明追加到提示词，再把模型返回的 JSON 映射回 record。业务层仍然可以直接读取字段，不需要字符串切割。

### 4. 受控反思循环

这个项目现在实现了三类有限状态的 Reflection Loop：

```text
人物提示词 -> RunningHub 内容审核拒绝 -> AI 安全重写提示词 -> 重新提交人物生成
人物提示词扩写 -> 人物生成 -> 人物检查 -> 纠正提示词 -> 重新生成人物
服装理解 -> 换装生成 -> 换装检查 -> 纠正提示词 -> 重新换装
```

人物内容审核的安全重写最多执行 `portrait-audit-max-retries` 次，人物视觉质检循环最多执行 `max-portrait-attempts` 次，换装循环最多执行 `max-outfit-attempts` 次。内容审核失败发生在图片产出前，因此只增加 RunningHub 提交次数，不增加人物候选图数量；视觉质检发生在图片产出后，才会产生新的 `PortraitAttempt`。这种错误分类能避免把平台审核失败误记成图片质量失败。

安全重写不会把 RunningHub 返回的违规词列表拼回生成提示词，而是让 LangChain4j 从用户原始正常意图重新规划，并由 Java 强制追加一份只包含正向描述的公开展示用人物约束。纠正提示词则把上一轮图片问题放在最前面，同时保留原始约束。即使原提示词很长，也优先保留本轮纠正信息。

自动重试必须同时满足：

```text
evaluated = true
passed = false
retryable = true
attemptNumber < maxOutfitAttempts
```

模型请求失败时任务立即停止，不会把规则提示词冒充成 AI 结果，也不会继续产生下一步图片生成费用。

### 5. 质量门

模型负责观察图片并给分，`OutfitQualityGate` 使用 Java 配置决定是否通过：

人物图先由 `PortraitQualityGate` 检查：

- 图片技术上正常可用。
- 综合分达到人物通过线。
- 与扩写提示词保持一致。
- 面部、手指、四肢和身体比例正常。
- 清晰度、曝光和构图达到画质线。

人物图未通过时不会进入服装选择。AI 已启用但人物质检调用失败时同样停止流程，防止未经验证的图片继续消耗换装费用。

换装结果再由 `OutfitQualityGate` 检查：

- 综合分达到 `quality-pass-score`。
- 服装匹配达到 `clothing-match-pass-score`。
- 人物身份保持达到 `identity-pass-score`。
- 参考图存在头饰时，头饰匹配达到 `head-accessory-pass-score`。

这是 AI 应用中很重要的分工：模型输出概率性的判断，确定性的业务规则决定后续副作用。

### 6. 失败边界与可观测性

没有配置智谱 Key，或 LangChain4j 的提示词扩写、服装分析调用失败时，任务会在当前阶段停止。系统不会使用固定提示词继续，也不会显示虚假的 AI 分析结果。人物图或换装图已经生成后，如果对应的视觉质检模型调用在 15 次繁忙重试后仍然失败，则保存一份 `evaluated=false` 的报告，页面和步骤日志明确显示“已跳过质检”，然后继续下一阶段。取消操作仍会立即中断，不会被当成质检异常放行。

每次任务的模型分析、实际提示词、每轮图片和质检报告都会写入本地。页面底部同时提供结构化步骤日志和应用原始日志，使提示词、阶段转换和模型决策可以复盘。

## RAG 学习与实现

现在项目有两个职责不同的检索链路。第一个决定“人物适合从目录中选择哪套服装”，第二个决定“选定服装后应该采用哪些换装经验”。

服装目录检索：

```text
页面点击“生成本地资料”
  -> 异步遍历 clothing/ 中的图片
  -> GLM 多模态分析为 ClothingCatalogAnalysis
  -> 按图片 SHA-256 幂等写入本地 H2 的 clothing_profile

人物扩写提示词
  -> BGE 生成 Query 向量
  -> 对 clothing_profile.search_text 生成向量并召回 Top K
  -> 选择最高相似度服装，步骤日志保留名称、分数和候选数量
```

换装经验检索：

```text
knowledge/fashion/*.md
  -> FileSystemDocumentLoader 加载文档和来源元数据
  -> DocumentSplitters.recursive 按段落、句子和字符切分 Chunk
  -> BgeSmallZhV15QuantizedEmbeddingModel 把 Chunk 转成 512 维向量
  -> InMemoryEmbeddingStore<TextSegment> 在启动时建立本地索引

用户描述 + FashionReferenceSpec
  -> 结构化检索 Query
  -> 同一个 BGE 模型生成 Query 向量
  -> 余弦相似度召回 Top K，并应用 minScore 阈值
  -> FashionKnowledgeContext 保存来源、Chunk、相似度和原文
  -> FashionRagPromptAugmenter 注入 RunningHub 换装提示词
```

### 为什么放在服装分析之后

服装选择发生在人物提示词扩写之后，因为扩写结果已经包含环境、气质、构图和人物视觉特征。换装规则检索仍放在服装分析之后：`FashionReferenceSpec` 已经把选中图片转成服装、颜色、材质、头饰、身体配饰和必须迁移元素，此时查询换装经验的语义最完整。

### 任务完成后的经验更新

`FashionExperienceLearningService` 先检查 `OutfitQualityReport`。只有 `evaluated=true` 且 `passed=true` 才调用 GLM 提取经验。经验以来源任务唯一的方式写入 `fashion_learned_experience`，随后立即嵌入当前内存索引；应用重启时会把 Markdown 和 H2 已批准经验一起重建索引。未质检、质检失败、重复任务或提取异常都不会污染知识库，也不会把已经成功的业务任务改成失败。

### Embedding 模型与聊天模型的区别

GLM 负责理解图片和生成结构化语义，BGE 只负责把文本映射到向量空间。Embedding 不生成答案，也不需要 `ZHIPU_API_KEY`。本项目使用量化中文 BGE 在 Java 进程内推理，所以检索不会消耗 GLM 配额，不受免费模型繁忙影响。

### Chunk 为什么不能过大或过小

Chunk 太大时，身份、材质、背景等主题混在同一个向量里，召回虽容易命中，但上下文不够精准。Chunk 太小时，规则会失去条件和修复建议。当前默认每段最多 450 个字符、重叠 60 个字符，适合这批短规则文档；它不是通用最佳值，应根据知识文档长度和召回测试调整。

### Top K 与相似度阈值

`max-results=3` 控制最多注入三段知识，`min-score=0.55` 过滤相关度不足的片段。Top K 太大会让提示词变长并产生规则冲突；阈值太高可能零召回，太低会注入无关经验。页面步骤日志会显示每条来源和分数，调参时不要只看最终图片，也要先看召回是否合理。

### 为什么保留来源和原文

最终提示词不是黑盒拼接。`FashionKnowledgeHit` 保存 `source`、`title`、`chunkIndex`、`score` 和 `text`，每个任务还会输出查询和完整上下文。看到错误换装时，可以区分是视觉分析错误、知识召回错误、提示词组合错误，还是 RunningHub 本身能力不足。

### 当前边界和后续演进

当前 `InMemoryEmbeddingStore` 适合少量学习文档：应用重启时重新索引，结构简单且便于断点调试。知识量扩大后可以保持 `FashionKnowledgeRetriever` 接口不变，把存储替换为 PGVector、Milvus 或其他持久化向量库。H2 继续保存任务和检索审计事件，不承担高维向量相似度查询。

## 本地产物

把 `.png`、`.jpg`、`.jpeg` 或 `.webp` 服装图放入：

```text
learning-examples/fashion-image-agent-demo/clothing/
```

每个任务会创建独立目录：

```text
generated/jobs/{jobId}/
  portrait-prompt-spec.json
  portrait-generation-prompt-1.txt
  portrait-attempt-1.*
  portrait-quality-report-1.json
  portrait-generation-prompt-2.txt # 人物重试时
  portrait-attempt-2.*             # 人物重试时
  portrait-quality-report-2.json   # 人物重试时
  original.*
  clothing.*
  fashion-analysis.json
  fashion-rag-query.txt
  fashion-rag-context.json
  replacement-prompt-1.txt
  outfit-attempt-1.*
  quality-report-1.json
  replacement-prompt-2.txt       # 发生重试时
  outfit-attempt-2.*             # 发生重试时
  quality-report-2.json          # 发生重试时
  outfit.*                       # 最终采用的候选图
```

`outfit.*` 不一定等于最后一次尝试。系统优先选择通过质量门的图片；都未通过时，比较各候选的最低关键项分数和综合分，避免为了修复头饰而采用一张身份破坏更严重的结果。

## 配置

在当前 PowerShell 会话中设置密钥：

```powershell
$env:RUNNINGHUB_API_KEY="你的 RunningHub API Key"
$env:ZHIPU_API_KEY="你的智谱开放平台 API Key"
```

不要把真实 Key 写入前端或提交到仓库。浏览器中的 JavaScript 对用户完全可见，服务端环境变量才是合适的存放位置。

主要配置位于 `src/main/resources/application.yml`：

| 配置 | 默认值 | 用途 |
| --- | ---: | --- |
| `fashion.rag.enabled` | `true` | 是否启用本地服装经验 RAG |
| `fashion.rag.knowledge-directory` | `knowledge/fashion` | Markdown 知识库目录 |
| `fashion.rag.max-results` | `3` | 单次检索最多召回的 Chunk 数 |
| `fashion.rag.min-score` | `0.55` | 最低相关度阈值，范围 0 到 1 |
| `fashion.rag.max-segment-size` | `450` | 单个 Chunk 最大字符数 |
| `fashion.rag.segment-overlap` | `60` | 相邻 Chunk 重叠字符数 |
| `fashion.rag.max-context-length` | `1200` | 注入换装提示词的检索上下文上限 |
| `fashion.ai.enabled` | `true` | 是否允许启用多模态分析 |
| `fashion.ai.model-name` | `glm-4.6v-flash` | 免费的提示词扩写与视觉分析模型 |
| `fashion.ai.portrait-preset` | 亚洲面孔、20到30岁等 | 强制合并到 GLM 扩写结果中的人物业务约束 |
| `fashion.ai.portrait-output-width` | `1080` | 下载后人物底图的标准宽度 |
| `fashion.ai.portrait-output-height` | `1920` | 下载后人物底图的标准高度 |
| `runninghub.beauty-app-id` | `2066795888403640322` | RunningHub 人物生成应用 ID |
| `runninghub.outfit-app-id` | `2062480340836511746` | RunningHub 换装应用 ID |
| `fashion.ai.base-url` | `https://open.bigmodel.cn/api/paas/v4` | 智谱 OpenAI 兼容 API 地址 |
| `fashion.ai.proxy-host` | 空 | 可选 HTTP 代理地址；国内通常直接连接 |
| `fashion.ai.proxy-port` | `0` | 可选 HTTP 代理端口 |
| `fashion.ai.timeout` | `90s` | 单次模型调用超时 |
| `fashion.ai.busy-max-attempts` | `15` | 模型繁忙或限流时包含首次请求在内的最大调用次数 |
| `fashion.ai.busy-retry-interval` | `10s` | 模型繁忙后的再次调用间隔 |
| `fashion.ai.max-image-dimension` | `1536` | 发给模型的图片最长边 |
| `fashion.ai.max-portrait-attempts` | `2` | 人物底图最多生成次数 |
| `fashion.ai.portrait-audit-max-retries` | `2` | RunningHub 内容审核失败后的 AI 安全提示词重写次数 |
| `fashion.ai.portrait-quality-pass-score` | `75` | 人物综合通过线 |
| `fashion.ai.portrait-prompt-alignment-pass-score` | `70` | 人物图与提示词一致性通过线 |
| `fashion.ai.portrait-anatomy-pass-score` | `70` | 人体结构通过线 |
| `fashion.ai.portrait-image-quality-pass-score` | `70` | 人物图画质通过线 |
| `fashion.ai.max-outfit-attempts` | `2` | 换装最多生成次数 |
| `fashion.ai.quality-pass-score` | `75` | 综合通过线 |
| `fashion.ai.clothing-match-pass-score` | `75` | 服装匹配通过线 |
| `fashion.ai.head-accessory-pass-score` | `70` | 存在头饰时的通过线 |
| `fashion.ai.identity-pass-score` | `70` | 人物身份保持通过线 |

RunningHub Key 和智谱 Key 都是完整链路的必需配置。RunningHub 负责人物生成和换装，`GLM-4.6V-Flash` 负责提示词扩写与双重视觉质检；缺少任意一个 Key 时页面会显示未就绪，并禁止创建生成任务。

免费模型高峰期可能返回“当前访问量过大”。项目会在每次真实请求前记录 `第 N/15 次调用开始`，只对 429、503 和明确的模型繁忙提示等待 10 秒后重试。401 和参数错误会立即失败；点击页面停止按钮会中断 10 秒等待。可以通过 `GLM_BUSY_MAX_ATTEMPTS` 和 `GLM_BUSY_RETRY_INTERVAL` 调整策略。

## 启动与测试

### IntelliJ IDEA 源码调试

当前工程已经包含 `FashionAgentApplication` Spring Boot 启动项，并使用 `fashion-image-agent-demo` 模块。运行前在 IntelliJ 的 `Run | Edit Configurations` 中检查：

| 项目 | 值 |
| --- | --- |
| JDK | 17 |
| Main class | `dev.learning.fashionagent.FashionAgentApplication` |
| Module | `fashion-image-agent-demo` |
| Working directory | `$PROJECT_DIR$/learning-examples/fashion-image-agent-demo` |
| Environment variables | `RUNNINGHUB_API_KEY=...;ZHIPU_API_KEY=...` |

在 `FashionAgentPipeline.run()`、`LangChain4jPortraitAiService.enhancePrompt()` 或 `GenerationJobService.execute()` 左侧设置断点，然后使用 Debug 启动 `FashionAgentApplication`。浏览器提交任务后，请求会进入这些源码断点。

任务、账号、权限和学习经验默认保存在 `data/fashion-agent.mv.db`，无需安装数据库服务。应用首次启动会自动执行 `schema-h2.sql` 并创建默认管理员账号。

### Maven 命令行运行

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
$env:RUNNINGHUB_API_KEY="你的 RunningHub API Key"
$env:ZHIPU_API_KEY="你的智谱开放平台 API Key"
mvn test
mvn spring-boot:run
```

浏览器打开 [http://localhost:8088](http://localhost:8088)。

常规测试不会调用外部模型服务。真实 GLM 多模态测试需要显式启用，并提供一张本地服装图片：

```powershell
$env:FASHION_AI_LIVE_TEST="true"
mvn "-Dfashion.ai.live-image=clothing/你的服装图片.jpg" -Dtest=FashionVisionLiveTest test
```

真实 RunningHub 上传测试也需要单独的环境开关，避免普通构建产生外部调用和费用。

## Web API

创建异步任务：

```http
POST /api/generations
Content-Type: application/json

{
  "prompt": "站在现代艺术馆中的成年女性，全身构图，柔和自然光"
}
```

响应为 `202 Accepted`，页面轮询：

```http
GET /api/generations/{jobId}
```

停止仍在排队或运行的任务：

```http
POST /api/generations/{jobId}/cancel
```

使用原描述词创建一个全新的执行任务：

```http
POST /api/generations/{jobId}/restart
```

重新启动不会恢复已取消的 Java 线程，而是创建新的任务 ID 并从提示词扩写阶段重新执行。旧任务会继续保留在历史记录中。

查询该任务的完整步骤事件：

```http
GET /api/generations/{jobId}/events
```

每轮候选图：

```http
GET /api/generations/{jobId}/attempts/{attemptNumber}/image
```

每轮人物候选图：

```http
GET /api/generations/{jobId}/portrait-attempts/{attemptNumber}/image
```

系统状态与日志：

```http
GET /api/system/readiness
GET /api/system/logs?lines=800
```

## 本地 H2 历史记录与账号配置

应用使用文件型 H2 数据库，不依赖 MySQL。表结构由 `src/main/resources/schema-h2.sql` 自动初始化，默认连接参数如下：

| 配置 | 默认值 | 环境变量 |
| --- | --- | --- |
| URL | `jdbc:h2:file:./data/fashion-agent;MODE=MySQL...` | `DB_URL` |
| 用户名 | `sa` | `DB_USERNAME` |
| 密码 | 空 | `DB_PASSWORD` |

核心业务表各自承担不同职责，`app_account` 和 `app_account_setting` 额外保存账号、菜单权限、有效期以及加密后的模型配置：

| 表 | 职责 |
| --- | --- |
| `generation_job` | 当前任务快照、状态、图片路径和完整 `JobView` JSON |
| `generation_step_event` | 追加式审计日志，保存每一步的阶段、说明和结构化结果 |
| `portrait_attempt` | 每轮人物图片、实际提示词、质量报告和是否入选 |
| `outfit_attempt` | 每轮换装图片、换装提示词、质量报告和是否入选 |
| `clothing_profile` | 本地服装图片哈希、GLM 结构化资料和语义检索文本 |
| `fashion_learned_experience` | 来源任务唯一的成功经验、证据分数和 RAG 知识文本 |

这里使用了“写模型 + 读模型”的思路：明细表适合审计和分析，`snapshot_json` 适合网页快速恢复完整任务。数据库位于应用目录的 `data` 文件夹，打包迁移时无需部署外部数据库。

## 任务停止机制

页面日志旁和运行中的历史任务行都提供停止按钮。服务端为每个任务保存独立 `FutureTask`：

1. 先把任务原子地标记为 `CANCELLED`。
2. 调用 `FutureTask.cancel(true)` 向执行线程发送中断信号。
3. 每个 Pipeline Observer 回调再次检查取消状态。
4. 后续成功或失败回调不能覆盖已经取消的状态。

停止的是本地 Agent 流程和后续轮询。RunningHub 如果已经接受远程请求，远程侧仍可能完成本次生成，但本地不会继续下载、质检或执行下一阶段；这是本地线程取消与远程任务取消的边界。

## 外部图像服务映射

人物生成使用 RunningHub AI 应用接口：

| 配置 | Value |
| --- | --- |
| Submit Endpoint | `POST /openapi/v2/run/ai-app/2066795888403640322` |
| Query Endpoint | `POST /openapi/v2/query` |
| Width Node | `156 / value / 1080` |
| Height Node | `157 / value / 1920` |
| Prompt Node | `67 / text` |
| Prompt Value | LangChain4j 扩写或纠正后的人物生成提示词 |
| Local Output | 居中裁切和缩放后的 `1080x1920` PNG |

固定人物约束会先作为高优先级说明交给 GLM，再由 Java 代码追加到最终 `generationPrompt`。这是“模型软约束 + 程序硬约束”的组合：GLM 负责把要求自然地融入摄影描述，Java 负责保证亚洲面孔、年龄、得体着装、正面站姿和 9:16 构图不会因为模型偶然遗漏而丢失。默认约束只使用正向、适合公开展示的描述，避免审核器忽略否定语义而误命中高风险关键词。需要调整风格时可以设置 `FASHION_PORTRAIT_PRESET`，无需修改 Java 代码。

服装替换继续使用 RunningHub 节点映射：

| Node | Field | Value |
| --- | --- | --- |
| `107` | `image` | 上传后的人物图 `fileName` |
| `285` | `image` | 上传后的服装图 `fileName` |
| `223` | `value` | LangChain4j 生成或纠正后的换装提示词 |

人物图和服装图只上传一次。后续重试复用 RunningHub 文件名，只重新提交换装任务，减少上传时间和失败面。

## 常见问题

### GLM 调用返回 401、TLS 或 Connection reset

`401` 表示当前 Java 进程读取到的 `ZHIPU_API_KEY` 无效或没有读取到它。TLS、Connection reset 则表示 Java 进程到智谱接口的网络连接失败，不是提示词或 JSON 映射错误。调用失败会写入步骤日志和应用日志，并立即停止当前任务。

排查顺序：

1. 确认 IntelliJ 启动配置包含 `ZHIPU_API_KEY=...`，修改后必须重新启动应用。
2. 确认当前 Java 进程能访问 `open.bigmodel.cn`。
3. 国内网络默认不需要代理；只有明确使用代理时才配置 `ZHIPU_PROXY_HOST` 和 `ZHIPU_PROXY_PORT`。
4. 使用 live test 单独验证，避免同时经过 RunningHub 流程干扰判断。

### 头饰仍无法被换上

多模态分析和提示词只能告诉换装模型“要做什么”。如果 RunningHub 工作流的遮罩、ControlNet 或底层模型只处理身体服装区域，提示词无法突破工具能力边界。此时质检报告会保留问题并停止无意义重试，下一步应调整换装工作流的头部遮罩和模型节点。

## 推荐学习顺序

1. 先读 `FashionAgentPipeline`，理解整个确定性状态流。
2. 读 `ModelPortraitAgent`，理解文本扩写与图片质检如何使用两个不同方法。
3. 对比 `PortraitPromptSpec` 和 `PortraitQualityReport`，理解生成前规格与生成后证据的区别。
4. 再读 `ModelFashionVisionAgent`，理解多张图片按固定语义顺序输入模型。
5. 阅读两个 QualityGate 和流程测试，理解为什么最终决策不能完全交给模型。
6. 修改一项人物或换装分数阈值，观察页面质检结果与重试行为。
7. 修改结构化字段，例如增加妆容或表情匹配，并贯通模型、领域对象、页面和测试。
8. 在 `knowledge/fashion` 新增一条具体规则，执行任务后从 `RAG_RETRIEVAL` 步骤观察它是否被召回。
9. 分别调整 `min-score` 和 `max-results`，比较“零召回、精准召回、过度召回”对最终提示词的影响。
10. 知识量扩大后把 `InMemoryEmbeddingStore` 替换为持久化向量库，再加入用户偏好的对话 Memory 或更高层 Agentic 编排。
