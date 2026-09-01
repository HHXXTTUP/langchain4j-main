package dev.learning.fashionagent.comfyui;

import dev.learning.fashionagent.ai.FashionAiCallExecutor;
import dev.learning.fashionagent.ai.FashionAiConfiguration;
import dev.learning.fashionagent.ai.FashionAiProperties;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class StoryVideoPlannerService {
    private final FashionAiCallExecutor callExecutor;
    private final FashionAiProperties properties;
    private final ConcurrentHashMap<String, StoryVideoPlannerAgent> agents = new ConcurrentHashMap<>();

    public StoryVideoPlannerService(FashionAiProperties properties) {
        this.properties = properties;
        this.callExecutor = new FashionAiCallExecutor(properties);
    }

    public StoryVideoPlan preview(String story) {
        StoryVideoPlannerAgent agent = agent();
        if (story == null || story.isBlank()) throw new IllegalArgumentException("请输入需要拆分的剧情");
        if (story.length() > 12000) throw new IllegalArgumentException("剧情内容不能超过 12000 个字符");
        StoryVideoPlannerAgent.Plan plan = callExecutor.execute("剧情镜头拆分", () -> agent.plan(story.trim()));
        if (plan == null || plan.shots() == null || plan.shots().isEmpty()) {
            throw new IllegalStateException("模型没有返回可用的镜头计划");
        }
        List<StoryVideoPlan.Shot> normalized = new ArrayList<>();
        int sequence = 1;
        for (StoryVideoPlannerAgent.Shot shot : plan.shots()) {
            if (shot == null) continue;
            int duration = Math.max(1, Math.min(10, shot.duration()));
            String interfaceType = normalizeInterfaceType(shot.interfaceType(), sequence);
            String first = normalizeFrameSource(shot.firstFrameSource(), sequence == 1 ? "USER_IMAGE" : "PREVIOUS_LAST_FRAME");
            String last = normalizeFrameSource(shot.lastFrameSource(), "GENERATED_LAST_FRAME");
            String environment = defaultText(shot.environment());
            List<String> characters = shot.characters() == null ? List.of() : shot.characters().stream().filter(v -> v != null && !v.isBlank()).toList();
            if (!characters.isEmpty()) interfaceType = "TEXT_TO_VIDEO_IMAGE";
            List<StoryVideoPlan.DialogueLine> dialogueLines = shot.dialogueLines() == null ? List.of() : shot.dialogueLines().stream()
                    .filter(v -> v != null && v.text() != null && !v.text().isBlank())
                    .map(v -> new StoryVideoPlan.DialogueLine(defaultText(v.speaker()), v.text().trim(), defaultText(v.tone())))
                    .toList();
            String dialogue = defaultText(shot.dialogue());
            if (dialogue.isBlank() && !dialogueLines.isEmpty()) {
                dialogue = dialogueLines.stream().map(v -> v.speaker() + "：" + v.text() + (v.tone().isBlank() ? "" : "（" + v.tone() + "）"))
                        .reduce((a, b) -> a + "；" + b).orElse("");
            }
            String prompt = requireText(shot.prompt(), "镜头提示词不能为空");
            prompt = enrichPrompt(prompt, environment, characters, dialogue);
            normalized.add(new StoryVideoPlan.Shot(
                    sequence++, duration, interfaceType,
                    prompt, environment, characters, dialogueLines,
                    first, last, shot.characterImageRequired() || !characters.isEmpty(),
                    defaultText(shot.characterImageHint()), dialogue));
        }
        return new StoryVideoPlan(normalized, defaultText(plan.planningNotes()));
    }

    private StoryVideoPlannerAgent agent() {
        if (!properties.isModelConfigured()) throw new IllegalStateException("剧情拆分需要在账号配置中填写智谱 GLM API Key");
        String key = properties.getBaseUrl() + "|" + properties.getModelName() + "|" + properties.getApiKey();
        return agents.computeIfAbsent(key, ignored -> AiServices.builder(StoryVideoPlannerAgent.class)
                .chatModel(FashionAiConfiguration.createModel(properties)).build());
    }

    private static String normalizeInterfaceType(String value, int sequence) {
        if ("FIRST_LAST_FRAME".equalsIgnoreCase(value) || (sequence > 1 && value == null)) return "FIRST_LAST_FRAME";
        return "TEXT_TO_VIDEO_IMAGE";
    }

    private static String normalizeFrameSource(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalStateException(message);
        return value.trim();
    }

    private static String defaultText(String value) { return value == null ? "" : value.trim(); }

    private static String enrichPrompt(String prompt, String environment, List<String> characters, String dialogue) {
        StringBuilder result = new StringBuilder(prompt.trim());
        if (!environment.isBlank() && !prompt.contains(environment)) result.append("\n环境细节：").append(environment);
        if (!characters.isEmpty()) result.append("\n人物参考图映射：").append(String.join("；", characters));
        if (!dialogue.isBlank()) result.append("\n对白与语气：").append(dialogue);
        return result.toString().trim();
    }
}
