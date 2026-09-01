package dev.learning.fashionagent.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

interface StoryVideoPlannerAgent {
    @SystemMessage("""
            你是影视分镜规划师。把用户的一段图生视频剧情拆成连续镜头，每个镜头 3-10 秒。
            只输出结构化 JSON，不执行任何外部接口。镜头必须能独立提交给视频生成服务。
            第一个镜头通常使用 TEXT_TO_VIDEO_IMAGE，firstFrameSource=USER_IMAGE 或 NONE；
            后续镜头如果需要保持前后连贯，使用 FIRST_LAST_FRAME，firstFrameSource=PREVIOUS_LAST_FRAME，
            lastFrameSource=GENERATED_LAST_FRAME。只有剧情明确需要用户人物图时才 characterImageRequired=true。
            不要把不存在的图片 URL 写入结果；需要用户选择的图片用 characterImageHint 说明。
            dialogue 只填写剧情中明确出现的对白，没有对白时为空字符串。每个 prompt 要包含主体、动作、场景、镜头运动、光线、连续性约束，避免依赖上一个任务的隐式状态。
            画面环境必须具体到地点、时间、天气、背景层次、道具、色彩和光线，不要只写“在室内”或“在街上”。
            每个镜头如果识别到人物，必须把人物按“图2：人物A”“图3：人物B”标记，并在 characterImageHint 中说明每张图对应谁；prompt 中也必须保留这些图号。
            dialogue 必须按说话人分行，例如“人物A：...；人物B：...”，并在 prompt 中加入对白原文、语气、语速、停顿和情绪。
            镜头时长允许为 1-10 秒，优先使用能完整表达动作和对白的最短时长。
            """)
    Plan plan(@UserMessage String story);

    @Description("连续图生视频镜头计划")
    record Plan(
            @JsonProperty(required = true) @Description("按时间顺序排列的镜头，至少 1 个") List<Shot> shots,
            @JsonProperty(required = true) @Description("给用户的规划说明") String planningNotes) {}

    @Description("一个独立可提交的视频镜头")
    record Shot(
            @JsonProperty(required = true) int sequence,
            @JsonProperty(required = true) int duration,
            @JsonProperty(required = true) String interfaceType,
            @JsonProperty(required = true) String prompt,
            @JsonProperty(required = true) String environment,
            @JsonProperty(required = true) List<String> characters,
            @JsonProperty(required = true) List<DialogueLine> dialogueLines,
            @JsonProperty(required = true) String firstFrameSource,
            @JsonProperty(required = true) String lastFrameSource,
            @JsonProperty(required = true) boolean characterImageRequired,
            @JsonProperty(required = true) String characterImageHint,
            @JsonProperty(required = true) String dialogue) {}

    record DialogueLine(
            @JsonProperty(required = true) String speaker,
            @JsonProperty(required = true) String text,
            @JsonProperty(required = true) String tone) {}
}
