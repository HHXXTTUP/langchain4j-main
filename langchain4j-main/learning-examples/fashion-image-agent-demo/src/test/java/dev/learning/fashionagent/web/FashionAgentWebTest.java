package dev.learning.fashionagent.web;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.learning.fashionagent.ai.AnalysisMode;
import dev.learning.fashionagent.ai.FashionReferenceSpec;
import dev.learning.fashionagent.rag.FashionKnowledgeContext;
import dev.learning.fashionagent.rag.FashionKnowledgeRetriever;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest(properties = {
        "runninghub.api-key=",
        "fashion.ai.api-key=",
        "spring.datasource.url=jdbc:h2:mem:fashion-web-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "runninghub.clothing-directory=target/test-empty-clothing"
})
@AutoConfigureMockMvc
class FashionAgentWebTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    FashionKnowledgeRetriever knowledgeRetriever;

    MockHttpSession session;

    @BeforeEach
    void login() throws Exception {
        session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"qq1184920089\"}"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void shouldServeStudioPage() throws Exception {
        mockMvc.perform(get("/index.html").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("阿睿工作台")))
                .andExpect(content().string(containsString("AI多媒体平台")))
                .andExpect(content().string(containsString("工作台")))
                .andExpect(content().string(containsString("任务列表")))
                .andExpect(content().string(containsString("图片生成")))
                .andExpect(content().string(containsString("视频生成")))
                .andExpect(content().string(containsString("资料库")))
                .andExpect(content().string(containsString("菜单配置")))
                .andExpect(content().string(containsString("本地视频资料")))
                .andExpect(content().string(containsString("人物质检")))
                .andExpect(content().string(containsString("视频质检")))
                .andExpect(content().string(containsString("异常与运行日志")))
                .andExpect(content().string(containsString("完整运行链路")))
                .andExpect(content().string(containsString("result-card-template")));
    }

    @Test
    void shouldRejectBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/generations").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("描述词不能为空"));
    }

    @Test
    void shouldReportSystemNotReadyWithoutKeyAndClothingImages() throws Exception {
        mockMvc.perform(get("/api/system/readiness").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.apiKeyConfigured").value(false));
    }

    @Test
    void shouldExposeRuntimeLogsOnWebApi() throws Exception {
        mockMvc.perform(get("/api/system/logs?lines=100").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN));
    }

    @Test
    void shouldExposeLocalVideoCatalog() throws Exception {
        mockMvc.perform(get("/api/video-catalog").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void shouldRetrieveKnowledgeWithTheRealLocalChineseEmbeddingModel() {
        FashionReferenceSpec referenceSpec = new FashionReferenceSpec(
                AnalysisMode.MULTIMODAL_AI,
                "红色发带是必须还原的头部配饰",
                List.of(),
                List.of("红色"),
                List.of(),
                List.of("红色发带"),
                List.of(),
                List.of("红色发带"),
                "迁移红色发带");

        FashionKnowledgeContext context = knowledgeRetriever.retrieve("修复换装后遗漏的红色发带", referenceSpec);

        assertTrue(context.enabled());
        assertTrue(context.hasEvidence());
        assertTrue(context.hits().stream()
                .anyMatch(hit -> hit.source().equals("hair-and-accessories.md")));
    }
}
