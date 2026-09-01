package dev.learning.fashionagent.ai;

import java.util.List;

public record ClothingCatalogAnalysis(
        String name,
        String summary,
        List<String> styles,
        List<String> occasions,
        List<String> seasons,
        List<String> colors,
        List<String> materials,
        List<String> garments,
        List<String> headAccessories,
        List<String> bodyAccessories,
        String silhouette,
        List<String> suitableBodyCharacteristics,
        List<String> keywords) {

    public ClothingCatalogAnalysis {
        name = text(name, "未命名造型");
        summary = text(summary, "暂无服装摘要");
        styles = values(styles);
        occasions = values(occasions);
        seasons = values(seasons);
        colors = values(colors);
        materials = values(materials);
        garments = values(garments);
        headAccessories = values(headAccessories);
        bodyAccessories = values(bodyAccessories);
        silhouette = text(silhouette, "未识别");
        suitableBodyCharacteristics = values(suitableBodyCharacteristics);
        keywords = values(keywords);
    }

    public String searchText() {
        return String.join("；",
                "名称：" + name,
                "摘要：" + summary,
                "风格：" + String.join("、", styles),
                "场合：" + String.join("、", occasions),
                "季节：" + String.join("、", seasons),
                "颜色：" + String.join("、", colors),
                "材质：" + String.join("、", materials),
                "服装：" + String.join("、", garments),
                "头饰：" + String.join("、", headAccessories),
                "配饰：" + String.join("、", bodyAccessories),
                "版型：" + silhouette,
                "适合人物特征：" + String.join("、", suitableBodyCharacteristics),
                "关键词：" + String.join("、", keywords));
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static List<String> values(List<String> source) {
        return source == null ? List.of() : source.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
