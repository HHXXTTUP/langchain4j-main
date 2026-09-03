package dev.learning.fashionagent.director;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Loads only the rules relevant to the selected directing action. */
@Component
public class ShortDramaDirectorPromptLibrary {
    private static final String ROOT = "skills/short-drama-director/";
    private static final Map<ShortDramaDirectorMode, List<String>> REFERENCES = Map.of(
            ShortDramaDirectorMode.FULL_EPISODE, List.of("screenplay-gate-engine.md", "cinematic-dramaturgy-rules.md", "asset-spatial-ledger.md", "quality-gate-review.md"),
            ShortDramaDirectorMode.SCREENPLAY, List.of("screenplay-gate-engine.md", "cinematic-dramaturgy-rules.md"),
            ShortDramaDirectorMode.DIALOGUE_DOCTOR, List.of("dialogue-doctor-7d.md", "dialogue-speed-check.md"),
            ShortDramaDirectorMode.ASSET_BREAKDOWN, List.of("asset-spatial-ledger.md", "cinematic-dramaturgy-rules.md"),
            ShortDramaDirectorMode.STORYBOARD, List.of("cinematic-dramaturgy-rules.md", "camera-transitions-6types.md", "lighting-and-antifake.md"),
            ShortDramaDirectorMode.SPEECH_SPEED, List.of("dialogue-speed-check.md"),
            ShortDramaDirectorMode.VIDEO_PROMPT, List.of("seedance-render-engine.md", "model-adapters.md", "camera-transitions-6types.md"),
            ShortDramaDirectorMode.QUALITY_REVIEW, List.of("quality-gate-review.md", "platform-safety-compliance-guide.md"));

    public String instructions(ShortDramaDirectorMode mode, String tier, String platform, String aspectRatio) {
        StringBuilder prompt = new StringBuilder(read(ROOT + "SKILL.md"));
        for (String file : REFERENCES.get(mode)) {
            prompt.append("\n\n--- 专项规则：").append(file).append(" ---\n").append(read(ROOT + "references/" + file));
        }
        prompt.append("\n\n当前执行模式：").append(mode.label())
                .append("。动作强度：").append(normalize(tier, "R2"))
                .append("。发布平台：").append(normalize(platform, "抖音"))
                .append("。画幅：").append(normalize(aspectRatio, "9:16"))
                .append("。请严格使用以上规则完成任务；不要提及 Skill、规则库或提示词本身。输出中文，结构清晰，可直接交付制作。\n");
        if (mode == ShortDramaDirectorMode.FULL_EPISODE || mode == ShortDramaDirectorMode.SCREENPLAY) {
            prompt.append("\n这是一个可持续创作的剧本项目初始化阶段。只能输出剧本设定，严禁生成任何第一集正文、对白或分段内容。输出必须按以下顶级标题组织：\n")
                    .append("【剧本名称】\n【剧本设定】\n")
                    .append("定位/题材/受众；画幅、画质、视觉风格、色彩、镜头与声音基调；世界观与规则边界；核心冲突和长线剧情大纲（三幕走向）；人物资产清单（仅角色名、性别、角色级别、短锚点、人物关系，不描述外貌服装）；主要场景与空间锚点、光源方向、180度轴线；第一集的剧情目标、冲突卡点和结尾钩子（只写规划，不写正文）。\n")
                    .append("设定要足够完整，可作为后续每一集和视频复刻的统一圣经；不要出现【第1集】标题。\n");
        }
        return prompt.toString();
    }

    public String episodeInstructions(String tier, String platform, String aspectRatio) {
        StringBuilder prompt = new StringBuilder(read(ROOT + "SKILL.md"));
        for (String file : REFERENCES.get(ShortDramaDirectorMode.FULL_EPISODE)) prompt.append("\n\n--- 专项规则：").append(file).append(" ---\n").append(read(ROOT + "references/" + file));
        prompt.append("\n当前执行模式：剧情推演。动作强度：").append(normalize(tier, "R2")).append("。发布平台：").append(normalize(platform, "抖音")).append("。画幅：").append(normalize(aspectRatio, "9:16"))
                .append("。只输出本集故事正文，不重复剧本设定，不输出分镜、镜头、摄影、运镜、时长、画质参数或制作说明；用通俗现代汉语按小说叙事方式写清人物、因果、动作、环境变化、情绪和对白，保证前后对白口吻统一、事件真实连贯，结尾留下明确的剧情钩子。对白只用中文双引号标记，不添加字幕或声音制作要求；不要使用文言文、空泛形容词或重复段落。\n");
        return prompt.toString();
    }

    private static String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String read(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream stream = resource.getInputStream()) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
        catch (IOException e) { throw new IllegalStateException("短剧导演规则资源缺失：" + path, e); }
    }
}
