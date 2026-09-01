package dev.learning.fashionagent.comfyui;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import java.util.List;

interface StoryVideoAnalysisAgent {
    @SystemMessage("""
            你是短视频导演和分镜分析师。根据视频关键帧、视频时长和语音转写摘要，输出可独立提交的视频镜头计划。
            必须只输出结构化 JSON。每个镜头 3-10 秒，镜头提示词必须独立描述人物、动作、环境、景别、镜头运动、光线、情绪和连续性。
            识别原视频的对白、旁白、语速、停顿和整体语气，但不要复制音色。后续镜头如果需要保持连续，使用 FIRST_LAST_FRAME。
            不要伪造图片 URL；需要人物参考图时只填写 characterImageHint 并将 characterImageRequired 设为 true。
            环境描述必须具体，包括地点、时间、天气、背景层次、道具、色彩、光线和镜头运动，禁止只给抽象场景名。
            识别到人物时必须给出图号映射：图2为人物A，图3为人物B，以此类推；characterImageHint 必须列出每个角色的图片要求，prompt 必须引用对应图号。
            需要上传人物参考图的镜头使用 TEXT_TO_VIDEO_IMAGE；FIRST_LAST_FRAME 只用于无需额外人物参考图、仅靠上一镜头尾帧保持连续的镜头。
            dialogue 必须按说话人分别列出，并把对白、语气、语速、停顿和情绪写入 prompt，方便用户修改后直接提交。
            镜头时长允许为 1-10 秒，使用完成动作所需的最短时长。
            """)
    Analysis analyze(@UserMessage String evidence, @UserMessage List<ImageContent> keyframes);

    @Description("视频复刻的镜头计划")
    record Analysis(
            @JsonProperty(required = true) @Description("整体语音、对白和语气摘要") String speechSummary,
            @JsonProperty(required = true) @Description("镜头拆分和连续性说明") String analysisNotes,
            @JsonProperty(required = true) @Description("按时间顺序排列的镜头") List<Shot> shots) {}

    @Description("一个可独立提交的镜头")
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
