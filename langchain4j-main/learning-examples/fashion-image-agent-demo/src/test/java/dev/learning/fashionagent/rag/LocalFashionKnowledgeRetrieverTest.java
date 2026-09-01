package dev.learning.fashionagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.learning.fashionagent.ai.AnalysisMode;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LocalFashionKnowledgeRetrieverTest {

    @Test
    void shouldLoadChunkEmbedAndRetrieveHairAccessoryKnowledge() throws IOException {
        Path tempDirectory = Path.of("target", "test-rag-knowledge", UUID.randomUUID().toString());
        Files.createDirectories(tempDirectory);
        Files.writeString(tempDirectory.resolve("hair.md"), """
                # 发型与配饰迁移规则

                红色发带、发簪和头纱必须描述颜色、形状、材质与佩戴位置，不能只写替换头饰。
                """);
        Files.writeString(tempDirectory.resolve("material.md"), """
                # 面料规则

                丝绸需要柔和光泽，薄纱需要半透明层次，皮革需要平滑反光。
                """);
        FashionRagProperties properties = properties(tempDirectory);
        properties.setMinScore(0.8);
        properties.setMaxResults(1);
        LocalFashionKnowledgeRetriever retriever =
                new LocalFashionKnowledgeRetriever(new KeywordEmbeddingModel(), properties);
        retriever.initialize();

        FashionKnowledgeContext context = retriever.retrieve("保留红色发带", hairReference());

        assertEquals(2, retriever.indexedSegments());
        assertEquals(1, context.hits().size());
        assertEquals("hair.md", context.hits().get(0).source());
        assertTrue(context.hits().get(0).score() >= 0.8);
        assertTrue(context.promptContext().contains("红色发带"));
        assertTrue(context.query().contains("头部和发饰"));
    }

    @Test
    void shouldKeepFixedBusinessPrefixWhenRagContextIsInjected() {
        FashionKnowledgeContext context = new FashionKnowledgeContext(
                true,
                "发饰",
                List.of(new FashionKnowledgeHit("hair.md", "发饰", 0, 0.9, "发带规则")),
                "检索规则：发带必须保持佩戴位置",
                "检索完成");

        String prompt = FashionRagPromptAugmenter.augment(hairReference(), context);

        assertTrue(prompt.startsWith(FashionReferenceSpec.fixedReplacementPrefix()));
        assertTrue(prompt.contains("发带必须保持佩戴位置"));
        assertTrue(prompt.contains("迁移红色发带"));
    }

    @Test
    void shouldBuildAQueryFromStructuredVisualAnalysis() {
        String query = LocalFashionKnowledgeRetriever.buildQuery("现代艺术馆中的人物", hairReference());

        assertTrue(query.contains("用户描述：现代艺术馆中的人物"));
        assertTrue(query.contains("服装：红色连衣裙"));
        assertTrue(query.contains("材质：丝绸"));
        assertTrue(query.contains("头部和发饰：红色发带"));
    }

    private static FashionRagProperties properties(Path directory) {
        FashionRagProperties properties = new FashionRagProperties();
        properties.setKnowledgeDirectory(directory);
        properties.setMaxSegmentSize(1000);
        properties.setSegmentOverlap(0);
        properties.setMaxContextLength(600);
        return properties;
    }

    private static FashionReferenceSpec hairReference() {
        return new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红色丝绸连衣裙搭配红色发带",
                List.of("红色连衣裙"),
                List.of("红色"),
                List.of("丝绸"),
                List.of("红色发带"),
                List.of(),
                List.of("红色连衣裙", "红色发带"),
                "迁移红色发带并保持佩戴位置");
    }

    private static final class KeywordEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            return Response.from(segments.stream()
                    .map(TextSegment::text)
                    .map(KeywordEmbeddingModel::embedding)
                    .toList());
        }

        private static Embedding embedding(String text) {
            float[] vector = new float[5];
            if (containsAny(text, "发带", "发饰", "发簪", "头纱", "头部")) {
                vector[0] = 1;
            }
            if (containsAny(text, "丝绸", "薄纱", "皮革", "材质")) {
                vector[1] = 1;
            }
            if (containsAny(text, "身份", "五官", "脸型")) {
                vector[2] = 1;
            }
            if (containsAny(text, "背景", "姿态", "构图")) {
                vector[3] = 1;
            }
            if (vector[0] == 0 && vector[1] == 0 && vector[2] == 0 && vector[3] == 0) {
                vector[4] = 1;
            }
            return Embedding.from(vector);
        }

        private static boolean containsAny(String text, String... keywords) {
            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    return true;
                }
            }
            return false;
        }
    }
}
