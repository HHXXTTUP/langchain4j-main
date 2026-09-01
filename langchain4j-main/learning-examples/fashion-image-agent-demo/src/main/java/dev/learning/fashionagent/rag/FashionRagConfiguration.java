package dev.learning.fashionagent.rag;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallzhv15q.BgeSmallZhV15QuantizedEmbeddingModel;
import dev.learning.fashionagent.learning.FashionLearningRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FashionRagConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "fashion.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
    EmbeddingModel fashionEmbeddingModel() {
        return new BgeSmallZhV15QuantizedEmbeddingModel();
    }

    @Bean
    @ConditionalOnProperty(prefix = "fashion.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
    FashionKnowledgeRetriever localFashionKnowledgeRetriever(
            EmbeddingModel fashionEmbeddingModel,
            FashionRagProperties properties,
            FashionLearningRepository learningRepository) {
        return new LocalFashionKnowledgeRetriever(fashionEmbeddingModel, properties, learningRepository);
    }

    @Bean
    @ConditionalOnMissingBean(FashionKnowledgeRetriever.class)
    FashionKnowledgeRetriever disabledFashionKnowledgeRetriever() {
        return (userDescription, referenceSpec) ->
                FashionKnowledgeContext.disabled("服装 RAG 已通过配置关闭，本次不注入知识库规则");
    }
}
