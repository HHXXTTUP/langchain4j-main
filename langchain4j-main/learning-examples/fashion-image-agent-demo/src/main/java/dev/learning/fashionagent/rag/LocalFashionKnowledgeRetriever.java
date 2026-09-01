package dev.learning.fashionagent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.learning.FashionLearningRepository;
import dev.learning.fashionagent.learning.LearnedFashionExperience;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalFashionKnowledgeRetriever implements FashionKnowledgeRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFashionKnowledgeRetriever.class);
    private static final String QUERY_PREFIX = "为这个句子生成表示以用于检索相关文章：";

    private final EmbeddingModel embeddingModel;
    private final FashionRagProperties properties;
    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final FashionLearningRepository learningRepository;
    private int indexedSegments;

    public LocalFashionKnowledgeRetriever(
            EmbeddingModel embeddingModel,
            FashionRagProperties properties) {
        this(embeddingModel, properties, null);
    }

    public LocalFashionKnowledgeRetriever(
            EmbeddingModel embeddingModel,
            FashionRagProperties properties,
            FashionLearningRepository learningRepository) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
        this.learningRepository = learningRepository;
    }

    @PostConstruct
    public void initialize() {
        Path knowledgeDirectory = properties.getKnowledgeDirectory().toAbsolutePath().normalize();
        if (!Files.isDirectory(knowledgeDirectory)) {
            throw new IllegalStateException("服装 RAG 知识目录不存在：" + knowledgeDirectory);
        }

        List<Document> documents = loadMarkdownDocuments(knowledgeDirectory);
        if (documents.isEmpty()) {
            throw new IllegalStateException("服装 RAG 知识目录没有可用的 Markdown 文档：" + knowledgeDirectory);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(
                positive(properties.getMaxSegmentSize(), "max-segment-size"),
                nonNegative(properties.getSegmentOverlap(), "segment-overlap"));
        List<TextSegment> segments = splitter.splitAll(documents);
        if (segments.isEmpty()) {
            throw new IllegalStateException("服装 RAG 文档切分后没有产生任何知识片段");
        }

        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        indexedSegments = segments.size();
        int learnedExperiences = loadLearnedExperiences();
        LOGGER.info(
                "服装 RAG 知识库初始化完成 directory={} documents={} segments={} learnedExperiences={} embeddingDimension={}",
                knowledgeDirectory,
                documents.size(),
                indexedSegments,
                learnedExperiences,
                embeddingModel.dimension());
    }

    @Override
    public FashionKnowledgeContext retrieve(String userDescription, FashionReferenceSpec referenceSpec) {
        if (indexedSegments == 0) {
            throw new IllegalStateException("服装 RAG 知识库尚未初始化");
        }
        String query = buildQuery(userDescription, referenceSpec);
        Embedding queryEmbedding = embeddingModel.embed(QUERY_PREFIX + query).content();
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(positive(properties.getMaxResults(), "max-results"))
                        .minScore(score(properties.getMinScore()))
                        .build())
                .matches();
        List<FashionKnowledgeHit> hits = matches.stream()
                .map(this::toHit)
                .toList();
        String promptContext = formatPromptContext(hits, positive(properties.getMaxContextLength(), "max-context-length"));
        String message = hits.isEmpty()
                ? "未检索到达到相似度阈值的服装经验，本次不注入额外规则"
                : "检索到 " + hits.size() + " 条服装经验并注入换装提示词";
        LOGGER.info("服装 RAG 检索完成 query={} hits={} sources={}", query, hits.size(),
                hits.stream().map(FashionKnowledgeHit::source).toList());
        return new FashionKnowledgeContext(true, query, hits, promptContext, message);
    }

    @Override
    public synchronized void addExperience(LearnedFashionExperience experience) {
        TextSegment segment = experienceSegment(experience);
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        indexedSegments++;
        LOGGER.info("成功任务经验已实时加入 RAG experienceId={} sourceJobId={} indexedSegments={}",
                experience.id(), experience.sourceJobId(), indexedSegments);
    }

    int indexedSegments() {
        return indexedSegments;
    }

    private List<Document> loadMarkdownDocuments(Path directory) {
        try (var paths = Files.list(directory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".md"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(FileSystemDocumentLoader::loadDocument)
                    .peek(this::enrichMetadata)
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("读取服装 RAG 知识目录失败：" + directory, exception);
        }
    }

    private int loadLearnedExperiences() {
        if (learningRepository == null) {
            return 0;
        }
        try {
            List<LearnedFashionExperience> experiences = learningRepository.listApprovedExperiences();
            for (LearnedFashionExperience experience : experiences) {
                TextSegment segment = experienceSegment(experience);
                embeddingStore.add(embeddingModel.embed(segment).content(), segment);
                indexedSegments++;
            }
            return experiences.size();
        } catch (RuntimeException exception) {
            LOGGER.warn("本地 H2 学习经验暂未加载，Markdown RAG 仍可正常使用：{}", exception.getMessage());
            return 0;
        }
    }

    private static TextSegment experienceSegment(LearnedFashionExperience experience) {
        Metadata metadata = new Metadata()
                .put("source", "database-experience:" + experience.id())
                .put("title", experience.content().title())
                .put("index", 0)
                .put("sourceJobId", experience.sourceJobId().toString());
        return TextSegment.from(experience.knowledgeText(), metadata);
    }

    private void enrichMetadata(Document document) {
        String source = document.metadata().getString(Document.FILE_NAME);
        document.metadata()
                .put("source", source == null ? "unknown.md" : source)
                .put("title", firstHeading(document.text(), source));
    }

    private FashionKnowledgeHit toHit(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Integer chunkIndex = segment.metadata().getInteger("index");
        return new FashionKnowledgeHit(
                textOrDefault(segment.metadata().getString("source"), "unknown.md"),
                textOrDefault(segment.metadata().getString("title"), "未命名知识"),
                chunkIndex == null ? 0 : chunkIndex,
                roundScore(match.score()),
                segment.text().trim());
    }

    static String buildQuery(String userDescription, FashionReferenceSpec spec) {
        List<String> parts = new ArrayList<>();
        add(parts, "用户描述", userDescription);
        add(parts, "服装摘要", spec.summary());
        add(parts, "服装", String.join("、", spec.garments()));
        add(parts, "颜色", String.join("、", spec.colors()));
        add(parts, "材质", String.join("、", spec.materials()));
        add(parts, "头部和发饰", String.join("、", spec.headAccessories()));
        add(parts, "身体配饰", String.join("、", spec.bodyAccessories()));
        add(parts, "必须迁移", String.join("、", spec.mustTransfer()));
        return String.join("；", parts);
    }

    private static String formatPromptContext(List<FashionKnowledgeHit> hits, int maxLength) {
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder context = new StringBuilder("服装替换经验库检索结果（仅采用与当前参考图一致的规则，不得臆造图中不存在的元素）：\n");
        for (int index = 0; index < hits.size(); index++) {
            FashionKnowledgeHit hit = hits.get(index);
            String item = "%d. [%s | 相似度 %.3f] %s\n".formatted(
                    index + 1, hit.source(), hit.score(), normalizeMarkdown(hit.text()));
            if (context.length() + item.length() > maxLength) {
                int remaining = maxLength - context.length();
                if (remaining > 40) {
                    context.append(item, 0, Math.min(remaining, item.length()));
                }
                break;
            }
            context.append(item);
        }
        return context.toString().trim();
    }

    private static String normalizeMarkdown(String text) {
        return text.replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String firstHeading(String text, String fallback) {
        return text.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).trim())
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(textOrDefault(fallback, "未命名知识"));
    }

    private static void add(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + "：" + value.trim());
        }
    }

    private static int positive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException("fashion.rag." + name + " 必须大于 0");
        }
        return value;
    }

    private static int nonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException("fashion.rag." + name + " 不能小于 0");
        }
        return value;
    }

    private static double score(double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException("fashion.rag.min-score 必须在 0 到 1 之间");
        }
        return value;
    }

    private static double roundScore(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
