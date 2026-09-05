package dev.learning.fashionagent.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.learning.fashionagent.service.QwenVideoScriptService;
import dev.learning.fashionagent.service.QwenVideoScriptService.QwenVideoScriptView;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class QwenVideoScriptControllerTest {

    @Test
    void shouldAcceptUploadedVideoForScriptAnalysis() throws Exception {
        QwenVideoScriptService service = mock(QwenVideoScriptService.class);
        UUID id = UUID.randomUUID();
        when(service.create(any(MultipartFile.class), org.mockito.ArgumentMatchers.eq(true))).thenReturn(new QwenVideoScriptView(
                id, "本地上传", "demo.mp4", "ANALYZING", "正在调用千问分析脚本", null, null,
                Instant.now(), Instant.now()));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new QwenVideoScriptController(service)).build();

        mockMvc.perform(multipart("/api/qwen-video-scripts")
                        .file(new MockMultipartFile("video", "demo.mp4", "video/mp4", "video".getBytes(StandardCharsets.UTF_8)))
                        .param("parse", "true"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.sourceFileName").value("demo.mp4"));

        verify(service).create(any(MultipartFile.class), org.mockito.ArgumentMatchers.eq(true));
    }
}
