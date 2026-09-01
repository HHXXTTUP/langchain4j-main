package dev.learning.fashionagent.ai;

import java.util.List;

public record FashionReferenceSpec(
        AnalysisMode mode,
        String summary,
        List<String> garments,
        List<String> colors,
        List<String> materials,
        List<String> headAccessories,
        List<String> bodyAccessories,
        List<String> mustTransfer,
        String replacementPrompt) {

    private static final String FIXED_REPLACEMENT_PREFIX = """
            让图1穿上图2的衣服。图1是人物脸部身份、身体姿势、肢体动作、身体比例、画面构图、环境和背景的唯一基准；必须严格保留图1女生的脸部五官、脸型、眼神样貌、站立姿势、肢体动作、构图、环境和背景完全不变。图2只用于提供发型、发色、全身服饰、鞋袜和配饰细节；禁止复制或参考图2人物的姿势、动作、身体比例、构图、环境和背景。完整复刻图2的发型样式、全套服装、头饰、项链、手套、腿部装饰细节。要特别注意：
            1. 头发颜色和发饰一定要和上传的参考图一致！
            2. 结果图背景、环境和构图只能与图1保持一致，绝不能采用图2的背景！背景按上传底图一定不要改变！
            3. 人物的长相一定要按原图一样不要改变！
            4. 人物姿势和肢体动作只能与图1保持一致，绝不能模仿图2人物！
            """.trim();
    private static final String ANALYSIS_HEADING = "对图2穿搭的视觉分析补充：";

    public FashionReferenceSpec {
        mode = mode == null ? AnalysisMode.RULE_BASED_FALLBACK : mode;
        summary = textOrDefault(summary, "未获得服装视觉摘要");
        garments = safeList(garments);
        colors = safeList(colors);
        materials = safeList(materials);
        headAccessories = safeList(headAccessories);
        bodyAccessories = safeList(bodyAccessories);
        mustTransfer = safeList(mustTransfer);
        replacementPrompt = composeReplacementPrompt(replacementPrompt);
    }

    public boolean aiAnalyzed() {
        return mode == AnalysisMode.MULTIMODAL_AI;
    }

    public static FashionReferenceSpec fallback(String reason) {
        return new FashionReferenceSpec(
                AnalysisMode.RULE_BASED_FALLBACK,
                reason,
                List.of(),
                List.of(),
                List.of(),
                List.of("服装参考图中可见的帽子、发带、头纱、发簪等头部配饰"),
                List.of(),
                List.of("完整服装", "所有可见头部配饰", "与服装配套的身体配饰"),
                defaultReplacementPrompt());
    }

    public static String defaultReplacementPrompt() {
        return composeReplacementPrompt(null);
    }

    public static String fixedReplacementPrefix() {
        return FIXED_REPLACEMENT_PREFIX;
    }

    public static String composeReplacementPrompt(String analysisDetails) {
        String details = textOrDefault(analysisDetails, """
                按照图2从头到脚完整迁移清晰可见的发型、发色、服装、鞋袜和所有配饰。
                头部配饰包括帽子、发带、头纱、发簪、发夹和发饰，必须保持颜色、材质、形状和佩戴位置一致。
                不要遗漏图2中清晰可见的装饰，也不要增加参考图中不存在的元素。
                """.trim());
        if (details.startsWith(FIXED_REPLACEMENT_PREFIX)) {
            return details;
        }
        return FIXED_REPLACEMENT_PREFIX + "\n\n" + ANALYSIS_HEADING + "\n" + details;
    }

    public String analysisSupplement() {
        if (!replacementPrompt.startsWith(FIXED_REPLACEMENT_PREFIX)) {
            return replacementPrompt;
        }
        String remainder = replacementPrompt.substring(FIXED_REPLACEMENT_PREFIX.length()).trim();
        if (remainder.startsWith(ANALYSIS_HEADING)) {
            return remainder.substring(ANALYSIS_HEADING.length()).trim();
        }
        return remainder;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
