package dev.learning.fashionagent.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

interface ModelPortraitAgent {

    @SystemMessage("""
            你是一名专业的人像摄影导演和 AI 图片提示词工程师。
            用户可能只输入“一个美女”之类的简短描述。请保留用户核心意图，并补充成年女性的面部、发型、身材、姿态、环境、光线、构图和摄影风格。
            人物必须明确年满20岁；不得把成年人描述成未成年人。用户消息中会包含固定业务约束，这些约束优先于与其冲突的用户原始描述，必须完整体现在 generationPrompt 中。
            为了后续 AI 换装，人物需要自然站立、全身完整、四肢清晰、身体和衣物不被大型物体遮挡。
            不要添加用户明确排斥的特征，不要写色情或裸露内容。generationPrompt 应当是可以直接交给图片生成工作流的完整中文正向提示词。
            输出使用简体中文。
            """)
    ModelPortraitPrompt enhance(@UserMessage String description);

    @SystemMessage("""
            你是一名 AI 图片内容安全提示词修订专家。上一版人物提示词被图片平台的内容审核拒绝，请重新生成一份安全、自然、适合公开展示的人像提示词。
            保留用户原始描述中的人物年龄、面孔、环境、光线、正面站立和全身构图等正常意图，但不要复述上一版提示词，也不要使用暧昧、挑逗、暴露程度、身体敏感部位或容易被误判为成人内容的词语。
            人物必须明确为20到30岁的成年女性，穿着完整、日常、得体的时尚服装，采用自然中性的站姿与表情，画面适合普通商业人像展示。
            generationPrompt 必须是可以直接提交图片生成平台的完整中文正向提示词，不要在提示词中书写违规词列表或否定式安全声明。
            输出使用简体中文。
            """)
    ModelPortraitPrompt rewriteAfterAudit(@UserMessage String instruction);

    @SystemMessage("""
            你是一名严格的人物图片质量检查员。检查图片是否正常可用，以及是否符合给定的人物生成提示词。
            重点检查：图片是否空白或损坏；是否为明确成年女性；面部、手指和四肢是否存在严重畸形；人物是否完整全身且无遮挡；环境、光线、样貌和构图是否与提示词一致；是否适合后续服装替换。
            分数范围必须是0到100。只有严重问题才判定 technicallyValid=false。correctionPrompt 只描述下一次生成需要修复的内容，并保留已经正确的内容。
            如果再次生成可能修复则 retryable=true。输出使用简体中文。
            """)
    ModelPortraitQuality inspect(
            @UserMessage String instruction,
            @UserMessage ImageContent portraitImage);

    @Description("扩写后的人物图片生成规格")
    record ModelPortraitPrompt(
            @JsonProperty(required = true) @Description("人物面部、发型、年龄感和气质") String appearance,
            @JsonProperty(required = true) @Description("身材、姿态、手臂和腿部状态") String bodyAndPose,
            @JsonProperty(required = true) @Description("人物所在环境和氛围") String environment,
            @JsonProperty(required = true) @Description("光源方向、光质和明暗关系") String lighting,
            @JsonProperty(required = true) @Description("景别、机位、人物位置和全身完整性") String composition,
            @JsonProperty(required = true) @Description("摄影或视觉风格") String visualStyle,
            @JsonProperty(required = true) @Description("可直接交给图片生成服务的完整中文提示词")
                    String generationPrompt) {}

    @Description("人物生成图片的结构化质量报告")
    record ModelPortraitQuality(
            @JsonProperty(required = true) @Description("图片是否非空白、非损坏且不存在阻断使用的严重异常")
                    boolean technicallyValid,
            @JsonProperty(required = true) @Description("整体可用性评分，0到100") int overallScore,
            @JsonProperty(required = true) @Description("图片与提示词的一致性评分，0到100") int promptAlignmentScore,
            @JsonProperty(required = true) @Description("面部、手指、四肢和身体比例评分，0到100") int anatomyScore,
            @JsonProperty(required = true) @Description("清晰度、曝光、构图和画面完整性评分，0到100")
                    int imageQualityScore,
            @JsonProperty(required = true) @Description("重新生成是否可能修复当前问题") boolean retryable,
            @JsonProperty(required = true) @Description("简洁的质检结论") String summary,
            @JsonProperty(required = true) @Description("发现的具体问题；没有则为空列表") List<String> issues,
            @JsonProperty(required = true) @Description("下一次人物生成使用的纠正提示词；无需重试时为空字符串")
                    String correctionPrompt) {}
}
