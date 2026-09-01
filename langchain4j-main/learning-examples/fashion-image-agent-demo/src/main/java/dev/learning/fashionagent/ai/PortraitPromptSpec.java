package dev.learning.fashionagent.ai;

public record PortraitPromptSpec(
        AnalysisMode mode,
        String originalDescription,
        String appearance,
        String bodyAndPose,
        String environment,
        String lighting,
        String composition,
        String visualStyle,
        String generationPrompt) {

    public PortraitPromptSpec {
        mode = mode == null ? AnalysisMode.RULE_BASED_FALLBACK : mode;
        originalDescription = text(originalDescription, "成年女性");
        appearance = text(appearance, "自然协调的成年女性面部与发型");
        bodyAndPose = text(bodyAndPose, "自然站立，全身构图，四肢清晰无遮挡");
        environment = text(environment, "简洁且具有空间层次的环境");
        lighting = text(lighting, "柔和自然光");
        composition = text(composition, "人物居中，全身完整进入画面");
        visualStyle = text(visualStyle, "写实人像摄影");
        generationPrompt = text(generationPrompt, fallbackPrompt(originalDescription));
    }

    public boolean aiEnhanced() {
        return mode == AnalysisMode.MULTIMODAL_AI;
    }

    public static PortraitPromptSpec fallback(String description, String reason) {
        return new PortraitPromptSpec(
                AnalysisMode.RULE_BASED_FALLBACK,
                description,
                reason,
                "自然站立，全身构图，四肢清晰无遮挡",
                "简洁且具有空间层次的环境",
                "柔和自然光",
                "人物居中，全身完整进入画面，适合后续服装替换",
                "写实人像摄影",
                fallbackPrompt(description));
    }

    private static String fallbackPrompt(String description) {
        return "一位明确年满20岁的成年女性，" + text(description, "自然美丽的成年女性")
                + "。自然协调的面部和身体比例，自然站立，四肢清晰无遮挡，全身完整进入画面；"
                + "环境具有真实空间层次，柔和自然光，写实高质量人像摄影，画面清晰，适合后续服装替换。";
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
