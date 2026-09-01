package dev.learning.fashionagent.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "runninghub.api-key=",
        "fashion.ai.api-key=",
        "spring.datasource.url=jdbc:h2:mem:account-security-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
class AccountSecurityWebTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void ordinaryAccountCanUpdateOwnSecretsAndDirectoriesButSeesMaskedSecrets() throws Exception {
        MockHttpSession admin = login("admin", "qq1184920089");
        String username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String accountId = createAccount(admin, username, Set.of("account-settings"), Instant.now().plusSeconds(3600));
        MockHttpSession user = login(username, "password123");

        String response = mockMvc.perform(put("/api/accounts/me/settings").session(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "qwenKey", "qwen-user-secret",
                                "videoDirectory", "D:/user-videos"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode self = mapper.readTree(response);
        assertEquals("********", self.path("settings").path("qwenKey").asText());
        assertEquals("D:\\user-videos", self.path("settings").path("videoDirectory").asText());
        assertTrue(self.path("username").asText().contains("***"));
        assertFalse(self.hasNonNull("expiresAt"));

        JsonNode accounts = mapper.readTree(mockMvc.perform(get("/api/accounts").session(admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode created = find(accounts, accountId);
        assertEquals("qwen-user-secret", created.path("settings").path("qwenKey").asText());
    }

    @Test
    void ordinaryAccountCannotUseUnassignedMenusOrAdminOperations() throws Exception {
        MockHttpSession admin = login("admin", "qq1184920089");
        String username = "limited-" + UUID.randomUUID().toString().substring(0, 8);
        createAccount(admin, username, Set.of("account-settings"), Instant.now().plusSeconds(3600));
        MockHttpSession user = login(username, "password123");

        mockMvc.perform(get("/api/system/logs").session(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前账号没有该功能权限"));
        mockMvc.perform(post("/api/accounts").session(user)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("forbidden-create", Set.of("account-settings"), Instant.now().plusSeconds(3600))))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingSessionIsRejectedImmediatelyAfterAdministratorExpiresTheAccount() throws Exception {
        MockHttpSession admin = login("admin", "qq1184920089");
        String username = "expiring-" + UUID.randomUUID().toString().substring(0, 8);
        String accountId = createAccount(admin, username, Set.of("account-settings"), Instant.now().plusSeconds(3600));
        MockHttpSession user = login(username, "password123");

        mockMvc.perform(put("/api/accounts/{id}", accountId).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(username, Set.of("account-settings"), Instant.now().minusSeconds(1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accounts").session(user))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("账号已过有效期，请联系管理员"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private String createAccount(MockHttpSession admin, String username, Set<String> menus, Instant expiry) throws Exception {
        String response = mockMvc.perform(post("/api/accounts").session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(username, menus, expiry)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).path("id").asText();
    }

    private String accountJson(String username, Set<String> menus, Instant expiry) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "username", username,
                "password", "password123",
                "administrator", false,
                "enabled", true,
                "expiresAt", expiry.toString(),
                "allowedMenus", menus,
                "settings", Map.of()));
    }

    private static JsonNode find(JsonNode accounts, String id) {
        for (JsonNode account : accounts) if (id.equals(account.path("id").asText())) return account;
        throw new AssertionError("Account not found: " + id);
    }
}
