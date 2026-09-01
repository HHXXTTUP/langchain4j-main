package dev.learning.fashionagent.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.learning.fashionagent.video.SnapAnyVideoImportService;
import dev.learning.fashionagent.video.VideoCatalogService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class VideoCatalogControllerTest {

    @Test
    void shouldUpdateMultipleVideoSourceFolders() throws Exception {
        VideoCatalogService service = mock(VideoCatalogService.class);
        when(service.selectSourceFolders(List.of("202607", "202608"))).thenReturn(List.of(
                new VideoCatalogService.VideoFolderView("202607", 2, true),
                new VideoCatalogService.VideoFolderView("202608", 3, true)));
        VideoCatalogController controller = new VideoCatalogController(service, mock(SnapAnyVideoImportService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(put("/api/video-catalog/folders/selection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folders\":[\"202607\",\"202608\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].selected").value(true))
                .andExpect(jsonPath("$[1].name").value("202608"));

        verify(service).selectSourceFolders(List.of("202607", "202608"));
    }
}
