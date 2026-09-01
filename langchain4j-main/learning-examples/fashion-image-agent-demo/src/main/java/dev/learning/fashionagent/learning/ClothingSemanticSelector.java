package dev.learning.fashionagent.learning;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.learning.fashionagent.service.ClothingCatalog;
import dev.learning.fashionagent.selection.AssetType;
import dev.learning.fashionagent.selection.BalancedAssetSelectionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class ClothingSemanticSelector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClothingSemanticSelector.class);
    private static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";
    private static final String SEMANTIC_MATCH_RULE =
            "将人物扩写描述与服装资料的名称、摘要、风格、场合、季节、颜色、材质、服装单品、"
                    + "头部配饰、身体配饰、版型、适合人物特征和关键词生成 BGE 向量；"
                    + "先从全部服装中选择历史使用次数最少的一组并避开刚使用的素材，"
                    + "再在该组内选择余弦相似度最高的服装";

    private final FashionLearningRepository repository;
    private final ClothingCatalog catalog;
    private final EmbeddingModel embeddingModel;
    private final BalancedAssetSelectionService balancedSelection;

    public ClothingSemanticSelector(
            FashionLearningRepository repository,
            ClothingCatalog catalog,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            BalancedAssetSelectionService balancedSelection) {
        this.repository = repository;
        this.catalog = catalog;
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.balancedSelection = balancedSelection;
    }

    public Selection select(String portraitDescription) {
        if (embeddingModel == null) {
            return random("RAG 已关闭，使用随机服装");
        }
        List<ClothingProfile> profiles;
        try {
            profiles = repository.listClothingProfiles().stream()
                    .filter(profile -> Files.isRegularFile(profile.imagePath()))
                    .toList();
        } catch (RuntimeException exception) {
            LOGGER.warn("读取服装结构化资料失败，暂时随机选衣：{}", exception.getMessage());
            return random("服装资料表不可用，使用随机服装");
        }
        if (profiles.isEmpty()) {
            return random("尚未生成服装结构化资料，使用随机服装");
        }

        List<Embedding> embeddings = embeddingModel.embedAll(
                profiles.stream()
                        .map(ClothingProfile::searchText)
                        .map(TextSegment::from)
                        .toList()).content();
        InMemoryEmbeddingStore<ClothingProfile> store = new InMemoryEmbeddingStore<>();
        store.addAll(embeddings, profiles);
        Embedding query = embeddingModel.embed(QUERY_PREFIX + portraitDescription).content();
        List<EmbeddingMatch<ClothingProfile>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .maxResults(profiles.size())
                        .build())
                .matches();
        if (matches.isEmpty()) {
            return random("语义检索没有返回候选，使用随机服装");
        }
        Map<Path, EmbeddingMatch<ClothingProfile>> matchesByPath = matches.stream()
                .collect(Collectors.toMap(
                        match -> normalized(match.embedded().imagePath()),
                        Function.identity(),
                        (first, ignored) -> first));
        BalancedAssetSelectionService.Selection balanced = balancedSelection.select(
                AssetType.CLOTHING,
                matches.stream()
                        .map(match -> new BalancedAssetSelectionService.Candidate(
                                match.embedded().imagePath(),
                                match.score()))
                        .toList());
        EmbeddingMatch<ClothingProfile> selectedMatch = matchesByPath.get(normalized(balanced.path()));
        if (selectedMatch == null) {
            throw new IllegalStateException("均衡选择返回了不在服装语义候选中的图片：" + balanced.path());
        }
        ClothingProfile selected = selectedMatch.embedded();
        double matchPercentage = percentage(selectedMatch.score());
        List<Candidate> candidates = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(selectedMatch),
                        matches.stream().filter(match -> match != selectedMatch))
                .limit(Math.min(3, matches.size()))
                .map(match -> new Candidate(
                        match.embedded().analysis().name(),
                        match.embedded().fileName(),
                        match.score(),
                        percentage(match.score())))
                .toList();
        String reason = "根据人物提示词语义检索选择“" + selected.analysis().name()
                + "”，匹配度 " + String.format("%.1f%%", matchPercentage)
                + "；该素材选择前已使用 " + balanced.useCountBefore() + " 次，本次后为 "
                + balanced.useCountAfter() + " 次"
                + "；规则：" + SEMANTIC_MATCH_RULE;
        LOGGER.info("服装语义均衡检索完成 selected={} score={} useCountBefore={} candidates={}",
                selected.fileName(), selectedMatch.score(), balanced.useCountBefore(),
                matches.stream().map(match -> match.embedded().fileName()).toList());
        return new Selection(
                selected.imagePath(),
                selected.analysis().name(),
                reason,
                true,
                selectedMatch.score(),
                matchPercentage,
                SEMANTIC_MATCH_RULE,
                candidates);
    }

    private Selection random(String reason) {
        BalancedAssetSelectionService.Selection balanced = balancedSelection.select(
                AssetType.CLOTHING,
                catalog.images().stream()
                        .map(BalancedAssetSelectionService.Candidate::of)
                        .toList());
        Path image = balanced.path();
        String rule = "未执行语义匹配；按历史使用次数从少到多轮转本地服装，并避免连续选择同一张图片；"
                + "生成服装结构化资料并启用 Embedding 模型后，会在均衡轮转基础上增加语义匹配";
        return new Selection(
                image,
                image.getFileName().toString(),
                reason + "；该素材选择前已使用 " + balanced.useCountBefore() + " 次，本次后为 "
                        + balanced.useCountAfter() + " 次；规则：" + rule,
                false,
                0,
                0,
                rule,
                List.of());
    }

    private static double percentage(double score) {
        return Math.round(Math.max(0, Math.min(1, score)) * 1000.0) / 10.0;
    }

    private static Path normalized(Path path) {
        return path.toAbsolutePath().normalize();
    }

    public record Selection(
            Path image,
            String clothingName,
            String reason,
            boolean semantic,
            double score,
            double matchPercentage,
            String rule,
            List<Candidate> candidates) {

        public Selection {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public Selection withImage(Path archivedImage) {
            return new Selection(
                    archivedImage,
                    clothingName,
                    reason,
                    semantic,
                    score,
                    matchPercentage,
                    rule,
                    candidates);
        }
    }

    public record Candidate(String clothingName, String fileName, double score, double matchPercentage) {}
}
