package dev.learning.fashionagent.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

interface ModelFashionVisionAgent {

    @SystemMessage("""
            你是服装商品资料分析师。请把服装参考图转换为可检索的结构化目录资料。
            只描述图片中清晰可见的信息；年龄、职业、品牌等无法确认的内容不要猜测。
            风格、场合、季节、版型和适合人物特征用于后续语义选衣，应使用简洁、稳定、可复用的中文标签。
            suitableBodyCharacteristics 只能描述服装版型通常适配的视觉特征，不得对人物进行价值判断。
            输出使用简体中文。
            """)
    ModelClothingCatalogAnalysis analyzeCatalog(
            @UserMessage String instruction,
            @UserMessage ImageContent clothingImage);

    @SystemMessage("""
            你是一名严谨的服装视觉分析师。图片是即将用于 AI 换装的服装参考图。
            识别服装、主色、材质、身体配饰和头部配饰。头部配饰包括帽子、发带、头纱、发簪、发夹和装饰性头饰。
            只描述图片中清晰可见的内容，不确定时不要猜测。
            replacementPrompt 只负责输出对参考图2穿搭的准确补充，不要重复通用换装要求。
            补充内容必须按照从头到脚的顺序，明确描述发型样式、头发颜色、发饰、头饰、上装、下装或连体服、项链、手套、腰部配饰、腿部装饰、鞋袜；
            对每个清晰可见元素说明颜色、材质、纹样、形状、层次和佩戴位置。不确定的细节不要猜测。
            图2人物的姿势、动作、身体比例、画面构图、环境和背景不属于穿搭资料，严禁写入 replacementPrompt。
            输出使用简体中文。
            """)
    ModelFashionAnalysis analyze(
            @UserMessage String instruction,
            @UserMessage ImageContent clothingImage);

    @SystemMessage("""
            你是一名严格的 AI 换装质量检查员。
            图片顺序固定为：第一张人物原图、第二张服装参考图、第三张换装结果图。
            图1是人物脸部身份、身体姿势、肢体动作、身体比例、画面构图、环境和背景的唯一质检基准。
            图2只提供发型、发色、服装、鞋袜和配饰细节，绝不能把图2人物的姿势、动作、身体比例、构图、环境或背景作为目标。
            服装、发型和配饰完整度只比较图2与图3；人物身份、姿势、动作、身体比例、构图、环境和背景保持度只比较图1与图3。
            如果图3模仿了图2人物的姿势、动作、环境或背景，必须视为严重错误并明显降低 identityPreservationScore。
            分数范围必须是 0 到 100。correctionPrompt 必须只针对本次失败项，并要求已正确区域保持不变。
            如果底层换装模型可能无法通过再次提示修复，retryable 应为 false。输出使用简体中文。
            """)
    ModelQualityAnalysis inspect(
            @UserMessage String instruction,
            @UserMessage List<ImageContent> orderedImages);

    @SystemMessage("""
            你是 AI 换装系统的经验工程师。输入是一条综合评分达到可接受标准的换装任务。
            从任务中提取以后遇到相似服装、材质、配饰或场景时仍然适用的经验。
            如果证据中存在差异或遗漏元素，必须把每项遗漏转换成下一次执行前就要注入提示词、并在质检时逐项确认的 reusableRules；
            这些遗漏属于待修复经验，不能描述成已经成功迁移的策略。successfulStrategy 只总结本次确实做对的部分。
            不要把人物姓名、文件路径、任务编号等一次性信息写成规则，也不要声称系统能保证绝对成功。
            reusableRules 必须是明确、可操作的提示词或检查规则；risks 写出仍需警惕的失败点。
            输出使用简体中文。
            """)
    ModelExperienceAnalysis extractExperience(@UserMessage String successfulTaskEvidence);

    @Description("服装图片的可检索目录资料")
    record ModelClothingCatalogAnalysis(
            @JsonProperty(required = true) @Description("简短、可辨识的造型名称") String name,
            @JsonProperty(required = true) @Description("完整但简洁的服装造型摘要") String summary,
            @JsonProperty(required = true) @Description("风格标签，如通勤、复古、甜酷") List<String> styles,
            @JsonProperty(required = true) @Description("适用场合标签") List<String> occasions,
            @JsonProperty(required = true) @Description("适用季节标签") List<String> seasons,
            @JsonProperty(required = true) @Description("主要颜色") List<String> colors,
            @JsonProperty(required = true) @Description("可见材质") List<String> materials,
            @JsonProperty(required = true) @Description("主要服装单品") List<String> garments,
            @JsonProperty(required = true) @Description("发饰、帽子等头部配饰") List<String> headAccessories,
            @JsonProperty(required = true) @Description("项链、手套、腰带等身体配饰") List<String> bodyAccessories,
            @JsonProperty(required = true) @Description("服装轮廓和版型") String silhouette,
            @JsonProperty(required = true) @Description("这套版型通常适配的人物视觉特征")
                    List<String> suitableBodyCharacteristics,
            @JsonProperty(required = true) @Description("用于检索的补充关键词") List<String> keywords) {}

    @Description("可接受换装任务中提取出的成功策略和遗漏修复经验")
    record ModelExperienceAnalysis(
            @JsonProperty(required = true) @Description("经验标题") String title,
            @JsonProperty(required = true) @Description("经验适用的服装、配饰或场景") String scenario,
            @JsonProperty(required = true) @Description("本次成功的核心策略") String successfulStrategy,
            @JsonProperty(required = true) @Description("可直接复用的规则") List<String> reusableRules,
            @JsonProperty(required = true) @Description("相似任务仍需检查的风险") List<String> risks,
            @JsonProperty(required = true) @Description("检索关键词") List<String> keywords) {}

    @Description("服装参考图的结构化视觉分析")
    record ModelFashionAnalysis(
            @JsonProperty(required = true) @Description("一段简洁准确的服装和配饰摘要") String summary,
            @JsonProperty(required = true) @Description("所有清晰可见的主要服装") List<String> garments,
            @JsonProperty(required = true) @Description("主要颜色") List<String> colors,
            @JsonProperty(required = true) @Description("可以从图片判断的材质") List<String> materials,
            @JsonProperty(required = true) @Description("帽子、发带、头纱、发簪等头部配饰；没有则为空列表")
                    List<String> headAccessories,
            @JsonProperty(required = true) @Description("腰带、项链、手套等身体配饰；没有则为空列表")
                    List<String> bodyAccessories,
            @JsonProperty(required = true) @Description("换装时必须迁移的具体元素") List<String> mustTransfer,
            @JsonProperty(required = true) @Description("只描述参考图2具体发型、发色、服装和配饰细节的中文提示词")
                    String replacementPrompt) {}

    @Description("换装结果的结构化质量报告")
    record ModelQualityAnalysis(
            @JsonProperty(required = true) @Description("整体效果分数，0到100") int overallScore,
            @JsonProperty(required = true) @Description("服装匹配分数，0到100") int clothingMatchScore,
            @JsonProperty(required = true) @Description("头部配饰匹配分数，0到100；参考图无头饰时填100")
                    int headAccessoryMatchScore,
            @JsonProperty(required = true) @Description("人物身份、面部、姿态和背景保持分数，0到100")
                    int identityPreservationScore,
            @JsonProperty(required = true) @Description("再生成一次是否可能修复问题") boolean retryable,
            @JsonProperty(required = true) @Description("一段简洁质检结论") String summary,
            @JsonProperty(required = true) @Description("结果图与目标之间的重要差异") List<String> differences,
            @JsonProperty(required = true) @Description("参考图存在但结果图遗漏的元素") List<String> missingElements,
            @JsonProperty(required = true) @Description("下一次换装使用的针对性纠正提示词；无需重试时为空字符串")
                    String correctionPrompt) {}
}
